package com.sanchay.ui.transactions;

import com.google.gson.Gson;
import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.PersistenceService;
import com.sanchay.ui.MainWindow;
import javafx.application.Platform;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.*;
import org.testfx.framework.junit5.ApplicationTest;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Full integration test for {@link TransactionsScreen} across all account types.
 *
 * <h2>What it covers</h2>
 * <ol>
 *   <li><b>Per account type — BankAccount, CreditCardAccount, LoanAccount, InvestmentAccount:</b>
 *     <ul>
 *       <li>Navigates to the account's {@link TransactionsScreen} and verifies the table is
 *           non-empty (real transactions loaded from test data).</li>
 *       <li>Clicks the FAB (floating "+") button and asserts that context-specific fields are
 *           pre-populated: the correct account is pre-selected and the transaction type is set
 *           to the type appropriate for the account context.</li>
 *       <li>For BankAccount and CreditCardAccount: fills in description (containing the test
 *           run ID and a timestamp for traceability) and amount, saves the transaction, then
 *           re-navigates to the account to verify the new row appears in the table.</li>
 *       <li>For LoanAccount and InvestmentAccount: verifies the pre-population and then cancels
 *           the dialog to avoid triggering complex downstream dialogs (amortization sync,
 *           investment schedule preview, etc.).</li>
 *     </ul>
 *   </li>
 *   <li><b>Two-account transactions — TRANSFER and CC_PAYMENT:</b>
 *     <ul>
 *       <li>TRANSFER: adds a transfer from a primary bank account to a secondary bank account,
 *           then verifies the transaction appears in both accounts' tables.</li>
 *       <li>CC_PAYMENT: adds a payment from a bank account to a credit card account, then
 *           verifies the transaction appears in both accounts' tables.</li>
 *       <li>Both two-account tests skip gracefully if the required second account is not
 *           present in the test data.</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <h2>Pre-population assertions per account type</h2>
 * <ul>
 *   <li><b>BankAccount</b>: all transaction types available; type defaults to EXPENSE;
 *       {@code #txn-expense-account-combo} pre-set to the bank account.</li>
 *   <li><b>CreditCardAccount</b>: types limited to EXPENSE / REFUND / CC_PAYMENT; defaults to
 *       EXPENSE; {@code #txn-expense-account-combo} pre-set to the CC account.</li>
 *   <li><b>LoanAccount</b>: type forced to LOAN_PAYMENT only;
 *       {@code #txn-loan-payment-to-account-combo} pre-set to the loan account.</li>
 *   <li><b>InvestmentAccount</b>: types limited to INVESTMENT / REDEEM; defaults to INVESTMENT;
 *       {@code #txn-investment-destination-account-combo} pre-set to the investment account.</li>
 * </ul>
 *
 * <h2>Test data</h2>
 * Reads {@code test-config.json} at the project root to find the data folder. Requires
 * {@code accounts.json}, {@code transactions.json}, and {@code categories.json} to exist there.
 * At minimum one BankAccount with transactions must be present; all other account types are
 * exercised only when present and silently skipped otherwise.
 *
 * <h2>Traceability</h2>
 * Every transaction added by this test includes a unique run ID (8-character UUID prefix) and
 * a wall-clock timestamp in its description, e.g.:
 * {@code "Test EXPENSE RUN=a1b2c3d4 14:32:05"}.
 * This makes it easy to find and clean up test-added rows in the data file.
 *
 * <h2>How to run</h2>
 * <pre>
 *   mvn test -Dtest=TransactionsScreenFullTest
 * </pre>
 * A live JavaFX window is opened — a display must be available.
 */
@Tag("full")
@Tag("transactions")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TransactionsScreenFullTest extends ApplicationTest {

    // Unique prefix for all transactions added during this run; use it to find/clean up test data.
    private static final String RUN_ID = UUID.randomUUID().toString().substring(0, 8);

    // Created once for the whole class run; all tests read/write from this single copy.
    private static Path runFolder;

    private MainWindow mainWindow;

    // One account per type, loaded from real test data. cc/loan/investment may be null.
    private Account bank1;         // primary bank account (must exist)
    private Account bank2;         // secondary bank for transfer test (may equal bank1)
    private Account cc;            // credit card account (null → CC tests skipped)
    private Account loan;          // loan account       (null → Loan tests skipped)
    private Account investment;    // investment account  (null → Investment tests skipped)

    // ── Setup ─────────────────────────────────────────────────────────────────

    private static TestConfig readConfig() throws Exception {
        Path configPath = Paths.get("test-config.json");
        if (!Files.exists(configPath))
            throw new IllegalStateException("test-config.json not found at: " + configPath.toAbsolutePath());
        try (Reader r = Files.newBufferedReader(configPath)) {
            TestConfig cfg = new Gson().fromJson(r, TestConfig.class);
            if (cfg == null || cfg.testDataFolderPath == null || cfg.testDataFolderPath.isBlank())
                throw new IllegalStateException("test-config.json must contain a non-empty 'testDataFolderPath'");
            if (cfg.testFolder == null || cfg.testFolder.isBlank())
                throw new IllegalStateException("test-config.json must contain a non-empty 'testFolder'");
            return cfg;
        }
    }

    /**
     * Copies the source test-data folder into testFolder under a name that includes
     * the run ID and a timestamp, e.g. {@code run_a1b2c3d4_20260410_113045}.
     * Returns the path to the copy.
     */
    private static Path createRunFolder(Path source, Path testFolder) throws Exception {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path dest = testFolder.resolve("run_" + RUN_ID + "_" + ts);
        Files.createDirectories(dest);
        try (var stream = Files.walk(source)) {
            for (Path src : (Iterable<Path>) stream::iterator) {
                Path target = dest.resolve(source.relativize(src));
                if (Files.isDirectory(src)) Files.createDirectories(target);
                else Files.copy(src, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        System.out.println("Test run folder: " + dest);
        return dest;
    }

    private void loadFixture() throws Exception {
        TestConfig cfg = readConfig();

        Path source = Paths.get(cfg.testDataFolderPath);
        List<String> missing = new java.util.ArrayList<>();
        for (String f : new String[]{"accounts.json", "transactions.json", "categories.json"})
            if (!Files.exists(source.resolve(f))) missing.add(f);
        if (!missing.isEmpty())
            throw new IllegalStateException(
                    "Test data folder '" + source + "' is missing: " + missing
                    + "\nUpdate testDataFolderPath in test-config.json.");

        // Create an isolated copy once for the whole class run; reuse it for every subsequent start()
        if (runFolder == null)
            runFolder = createRunFolder(source, Paths.get(cfg.testFolder));

        DataStore store = DataStore.getInstance();
        store.reset();
        PersistenceService ps = new PersistenceService(runFolder.toString());
        ps.loadAll(store);
        store.setPersistence(ps);  // wire up so every save goes to the run folder

        List<Account>     accounts = store.getAccounts();
        List<Transaction> txns     = store.getTransactions();

        // Banks with at least one transaction
        List<Account> banks = accounts.stream()
                .filter(a -> a instanceof BankAccount)
                .filter(a -> txns.stream().anyMatch(t ->
                        a.getId().equals(t.getFromAccountId()) || a.getId().equals(t.getToAccountId())))
                .toList();
        if (banks.isEmpty())
            throw new IllegalStateException("No BankAccount with transactions found in: " + source);

        bank1 = banks.get(0);
        bank2 = banks.size() > 1 ? banks.get(1) : banks.get(0);

        cc = accounts.stream()
                .filter(a -> a instanceof CreditCardAccount)
                .filter(a -> txns.stream().anyMatch(t ->
                        a.getId().equals(t.getFromAccountId()) || a.getId().equals(t.getToAccountId())))
                .findFirst().orElse(null);

        loan = accounts.stream()
                .filter(a -> a instanceof LoanAccount)
                .filter(a -> txns.stream().anyMatch(t ->
                        a.getId().equals(t.getFromAccountId()) || a.getId().equals(t.getToAccountId())))
                .findFirst().orElse(null);

        investment = accounts.stream()
                .filter(a -> a instanceof InvestmentAccount)
                .filter(a -> txns.stream().anyMatch(t ->
                        a.getId().equals(t.getFromAccountId()) || a.getId().equals(t.getToAccountId())))
                .findFirst().orElse(null);
    }

    @Override
    public void start(Stage stage) throws Exception {
        loadFixture();
        mainWindow = new MainWindow(false);
        mainWindow.show(stage);
        // show(stage) ends on Dashboard; move directly to bank1 transactions
        mainWindow.navigateToTransactions(bank1);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Navigates to the given account's TransactionsScreen on the FX thread. */
    private void goTo(Account acc) {
        interact(() -> mainWindow.navigateToTransactions(acc));
    }

    /** Returns the transaction table currently in the scene. */
    @SuppressWarnings("unchecked")
    private TableView<Transaction> txnTable() {
        return lookup("#txn-table").queryAs(TableView.class);
    }

    /**
     * Clicks the FAB and blocks until the TransactionDialog pane is present in the scene graph.
     * Necessary because showAndWait() opens the dialog in a separate Stage; the FX event
     * that fires the button action completes asynchronously and the dialog may not be
     * fully shown by the time clickOn() returns.
     */
    private void openFab() {
        clickOn("#fab");
        // Window.getWindows() must be read on the FX thread.
        // After showAndWait() starts its nested event loop, Platform.runLater tasks are still
        // processed by that loop — so we use a CountDownLatch to synchronize with the FX thread
        // directly, bypassing TestFX's interact() machinery entirely.
        for (int i = 0; i < 50; i++) {
            sleep(200);
            AtomicBoolean hasDialog = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                hasDialog.set(Window.getWindows().size() > 1);
                latch.countDown();
            });
            try {
                latch.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (hasDialog.get()) return;
        }
        fail("TransactionDialog did not open within 10 seconds after clicking FAB");
    }

    /** Builds a traceable description string for a newly added test transaction. */
    private String testDesc(String type) {
        String ts = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        return "Test " + type + " RUN=" + RUN_ID + " " + ts;
    }

    // ── 1. BANK ACCOUNT ───────────────────────────────────────────────────────

    @Test @Order(1)
    void bank_transactionsLoadedInTable() {
        goTo(bank1);
        assertFalse(txnTable().getItems().isEmpty(),
                "BankAccount '" + bank1.getName() + "' should have existing transactions");
    }

    @Test @Order(2)
    void bank_fabPrePopulatesBankAccount_addExpense_appearsInTable() {
        goTo(bank1);
        int before = txnTable().getItems().size();
        assumeFalse(before == 0, "Skipping FAB test — bank account has no transactions to confirm table reload");

        // Open the FAB dialog
        openFab();

        // BankAccount context: all types available; EXPENSE should be the active type.
        // The expense account combo must be pre-set to bank1.
        @SuppressWarnings("unchecked")
        ComboBox<Account> acctCb = lookup("#txn-expense-account-combo").queryAs(ComboBox.class);
        assertNotNull(acctCb.getValue(),
                "Expense account combo should be pre-populated when FAB opened from a bank account");
        assertEquals(bank1.getId(), acctCb.getValue().getId(),
                "Pre-populated account should be the bank account the screen was opened for");

        // Fill and save
        String desc = testDesc("EXPENSE");
        clickOn("#txn-description-field");
        write(desc);
        clickOn("#txn-amount-field");
        write("100");
        clickOn("#txn-save-button");

        // Re-navigate to get a fresh table and verify the new row is present
        goTo(bank1);
        assertTrue(txnTable().getItems().size() > before,
                "Table row count should increase after adding an expense");
        assertTrue(txnTable().getItems().stream().anyMatch(t -> desc.equals(t.getDescription())),
                "Newly added expense should be findable by description: " + desc);
    }

    // ── 2. CREDIT CARD ────────────────────────────────────────────────────────

    @Test @Order(3)
    void creditCard_transactionsLoadedInTable() {
        assumeTrue(cc != null, "No CreditCardAccount with transactions in test data — skipping");
        goTo(cc);
        assertFalse(txnTable().getItems().isEmpty(),
                "CreditCardAccount '" + cc.getName() + "' should have existing transactions");
    }

    @Test @Order(4)
    void creditCard_fabPrePopulatesCCAccount_addExpense_appearsInTable() {
        assumeTrue(cc != null, "No CreditCardAccount in test data — skipping");
        goTo(cc);
        int before = txnTable().getItems().size();

        openFab();

        // CC context: type = EXPENSE (first allowed); expense account combo = CC account.
        @SuppressWarnings("unchecked")
        ComboBox<Transaction.Type> typeCb = lookup("#txn-type-combo").queryAs(ComboBox.class);
        assertEquals(Transaction.Type.EXPENSE, typeCb.getValue(),
                "Default type for CC context should be EXPENSE");
        assertTrue(typeCb.getItems().contains(Transaction.Type.CC_PAYMENT),
                "CC_PAYMENT should be available for CC accounts");
        assertFalse(typeCb.getItems().contains(Transaction.Type.INCOME),
                "INCOME should not be available for CC accounts");

        @SuppressWarnings("unchecked")
        ComboBox<Account> acctCb = lookup("#txn-expense-account-combo").queryAs(ComboBox.class);
        assertNotNull(acctCb.getValue(),
                "Expense account combo should be pre-populated from CC context");
        assertEquals(cc.getId(), acctCb.getValue().getId(),
                "Pre-populated account should be the CC account");

        String desc = testDesc("CC-EXPENSE");
        clickOn("#txn-description-field");
        write(desc);
        clickOn("#txn-amount-field");
        write("500");
        clickOn("#txn-save-button");

        goTo(cc);
        assertTrue(txnTable().getItems().size() > before,
                "Table row count should increase after adding a CC expense");
        assertTrue(txnTable().getItems().stream().anyMatch(t -> desc.equals(t.getDescription())),
                "Newly added CC expense should appear in table: " + desc);
    }

    // ── 3. LOAN ACCOUNT ───────────────────────────────────────────────────────

    @Test @Order(5)
    void loanAccount_transactionsLoadedInTable() {
        assumeTrue(loan != null, "No LoanAccount with transactions in test data — skipping");
        goTo(loan);
        assertFalse(txnTable().getItems().isEmpty(),
                "LoanAccount '" + loan.getName() + "' should have existing transactions");
    }

    @Test @Order(6)
    void loanAccount_fabForcesLoanPaymentType_loanAccountPrePopulated() {
        assumeTrue(loan != null, "No LoanAccount in test data — skipping");
        goTo(loan);

        openFab();

        // Loan context: only LOAN_PAYMENT available; loan account pre-set in toCb.
        @SuppressWarnings("unchecked")
        ComboBox<Transaction.Type> typeCb = lookup("#txn-type-combo").queryAs(ComboBox.class);
        assertEquals(Transaction.Type.LOAN_PAYMENT, typeCb.getValue(),
                "Type should be forced to LOAN_PAYMENT for loan account context");
        assertEquals(1, typeCb.getItems().size(),
                "Only LOAN_PAYMENT should be available in the type combo for a loan account");

        @SuppressWarnings("unchecked")
        ComboBox<Account> toCb = lookup("#txn-loan-payment-to-account-combo").queryAs(ComboBox.class);
        assertNotNull(toCb.getValue(),
                "Loan account combo should be pre-populated from loan context");
        assertEquals(loan.getId(), toCb.getValue().getId(),
                "Pre-populated loan account should match the account the screen was opened for");

        // Cancel — saving would trigger the amortization sync confirmation dialog
        clickOn("#txn-cancel-button");
    }

    // ── 4. INVESTMENT ACCOUNT ─────────────────────────────────────────────────

    @Test @Order(7)
    void investmentAccount_transactionsLoadedInTable() {
        assumeTrue(investment != null, "No InvestmentAccount with transactions in test data — skipping");
        goTo(investment);
        assertFalse(txnTable().getItems().isEmpty(),
                "InvestmentAccount '" + investment.getName() + "' should have existing transactions");
    }

    @Test @Order(8)
    void investmentAccount_fabForcesInvestmentType_destAccountPrePopulated() {
        assumeTrue(investment != null, "No InvestmentAccount in test data — skipping");
        goTo(investment);

        openFab();

        // Investment context: INVESTMENT / REDEEM available; defaults to INVESTMENT; destCb pre-set.
        @SuppressWarnings("unchecked")
        ComboBox<Transaction.Type> typeCb = lookup("#txn-type-combo").queryAs(ComboBox.class);
        assertEquals(Transaction.Type.INVESTMENT, typeCb.getValue(),
                "Type should default to INVESTMENT for investment account context");
        assertTrue(typeCb.getItems().contains(Transaction.Type.REDEEM),
                "REDEEM should also be available for investment accounts");
        assertFalse(typeCb.getItems().contains(Transaction.Type.EXPENSE),
                "EXPENSE should not be available for investment accounts");

        @SuppressWarnings("unchecked")
        ComboBox<Account> destCb = lookup("#txn-investment-destination-account-combo").queryAs(ComboBox.class);
        assertNotNull(destCb.getValue(),
                "Investment destination combo should be pre-populated from investment context");
        assertEquals(investment.getId(), destCb.getValue().getId(),
                "Pre-populated investment account should match the account the screen was opened for");

        // Cancel — investment saving has complex type-specific validation and preview dialogs
        clickOn("#txn-cancel-button");
    }

    // ── 5. TRANSFER — visible in both bank accounts ───────────────────────────

    @Test @Order(9)
    void transfer_addedViaFAB_visibleInBothBankAccounts() {
        assumeTrue(!bank1.getId().equals(bank2.getId()),
                "Need two distinct BankAccounts for transfer test — only one found, skipping");

        goTo(bank1);
        openFab();

        // Switch type to TRANSFER
        @SuppressWarnings("unchecked")
        ComboBox<Transaction.Type> typeCb = lookup("#txn-type-combo").queryAs(ComboBox.class);
        interact(() -> typeCb.setValue(Transaction.Type.TRANSFER));

        // Set from = bank1, to = bank2
        @SuppressWarnings("unchecked")
        ComboBox<Account> fromCb = lookup("#txn-transfer-from-account-combo").queryAs(ComboBox.class);
        @SuppressWarnings("unchecked")
        ComboBox<Account> toCb = lookup("#txn-transfer-to-account-combo").queryAs(ComboBox.class);
        interact(() -> {
            fromCb.setValue(fromCb.getItems().stream()
                    .filter(a -> a.getId().equals(bank1.getId())).findFirst().orElse(null));
            toCb.setValue(toCb.getItems().stream()
                    .filter(a -> a.getId().equals(bank2.getId())).findFirst().orElse(null));
        });

        assertNotNull(fromCb.getValue(), "Transfer 'From' account should be set");
        assertNotNull(toCb.getValue(),   "Transfer 'To' account should be set");

        String desc = testDesc("TRANSFER");
        clickOn("#txn-description-field");
        write(desc);
        clickOn("#txn-amount-field");
        write("1000");
        clickOn("#txn-save-button");

        // Verify in source bank
        goTo(bank1);
        assertTrue(txnTable().getItems().stream().anyMatch(t -> desc.equals(t.getDescription())),
                "Transfer should appear in source bank account '" + bank1.getName() + "'");

        // Verify in destination bank
        goTo(bank2);
        assertTrue(txnTable().getItems().stream().anyMatch(t -> desc.equals(t.getDescription())),
                "Transfer should appear in destination bank account '" + bank2.getName() + "'");
    }

    // ── 6. CC PAYMENT — visible in bank and credit card ───────────────────────

    @Test @Order(10)
    void ccPayment_addedViaFAB_visibleInBankAndCreditCard() {
        assumeTrue(cc != null, "No CreditCardAccount in test data — skipping CC payment test");

        goTo(bank1);
        openFab();

        // Switch type to CC_PAYMENT (available because bank1 has all types)
        @SuppressWarnings("unchecked")
        ComboBox<Transaction.Type> typeCb = lookup("#txn-type-combo").queryAs(ComboBox.class);
        interact(() -> typeCb.setValue(Transaction.Type.CC_PAYMENT));

        // Set from = bank1 (paying bank), to = cc (card being paid)
        @SuppressWarnings("unchecked")
        ComboBox<Account> bankCb = lookup("#txn-cc-payment-from-account-combo").queryAs(ComboBox.class);
        @SuppressWarnings("unchecked")
        ComboBox<Account> cardCb = lookup("#txn-cc-payment-to-account-combo").queryAs(ComboBox.class);
        interact(() -> {
            bankCb.setValue(bankCb.getItems().stream()
                    .filter(a -> a.getId().equals(bank1.getId())).findFirst().orElse(null));
            cardCb.setValue(cardCb.getItems().stream()
                    .filter(a -> a.getId().equals(cc.getId())).findFirst().orElse(null));
        });

        assertNotNull(bankCb.getValue(), "CC payment 'From' bank should be set");
        assertNotNull(cardCb.getValue(), "CC payment 'To' card should be set");

        String desc = testDesc("CC-PAYMENT");
        clickOn("#txn-description-field");
        write(desc);
        clickOn("#txn-amount-field");
        write("2000");
        clickOn("#txn-save-button");

        // Verify in bank (debit side)
        goTo(bank1);
        assertTrue(txnTable().getItems().stream().anyMatch(t -> desc.equals(t.getDescription())),
                "CC payment should appear in bank account '" + bank1.getName() + "'");

        // Verify in credit card (credit side)
        goTo(cc);
        assertTrue(txnTable().getItems().stream().anyMatch(t -> desc.equals(t.getDescription())),
                "CC payment should appear in credit card account '" + cc.getName() + "'");
    }

    // ── JSON config DTO ───────────────────────────────────────────────────────
    private static class TestConfig {
        String testFolder;
        String testDataFolderPath;
    }
}
