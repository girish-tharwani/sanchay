package com.sanchay.ui.transactions;

import com.google.gson.Gson;
import com.sanchay.model.Account;
import com.sanchay.model.BankAccount;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.service.PersistenceService;
import com.sanchay.ui.MainWindow;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;

/**
 * Smoke test for {@link TransactionsScreen}.
 *
 * <h2>What it tests</h2>
 * <ul>
 *   <li><b>Core controls visible</b> — verifies that the screen title, search field, date-range
 *       pickers, type-filter combo, pending-only checkbox, transaction table, and export button
 *       are all rendered and visible on startup.</li>
 *   <li><b>Real data loads into the table</b> — asserts the transaction table is non-empty for
 *       the randomly selected account, confirming that {@link PersistenceService} wired up
 *       correctly and {@link TransactionsScreen} populated the table from the loaded data.</li>
 * </ul>
 *
 * <h2>Test data</h2>
 * Reads {@code test-config.json} at the project root (Maven working directory).
 * That file must contain a {@code testDataFolderPath} pointing to a Sanchay data folder with
 * at least {@code accounts.json}, {@code transactions.json}, and {@code categories.json}.
 * The test fails immediately with a descriptive error if any of those files are absent —
 * there is no fallback to generated or seeded data.
 *
 * <h2>Account selection</h2>
 * On each run a bank account ({@link BankAccount}) that has at least one transaction is chosen
 * at random from the loaded data. If no bank accounts qualify, any account type with transactions
 * is used. This randomisation ensures different accounts are exercised across runs.
 *
 * <h2>How to run</h2>
 * <pre>
 *   mvn test -Dtest=TransactionsScreenSmokeTest
 * </pre>
 * A live JavaFX window is opened briefly — a display must be available (fine on a dev machine).
 */
@Tag("smoke")
@Tag("transactions")
class TransactionsScreenSmokeTest extends ApplicationTest {

    private Account account;

    /** Reads test-config.json from the project root and returns the testDataFolderPath. */
    private static String resolveTestDataFolder() throws Exception {
        // Locate test-config.json relative to the working directory (project root when running via Maven)
        Path configPath = Paths.get("test-config.json");
        if (!Files.exists(configPath)) {
            throw new IllegalStateException("test-config.json not found at: " + configPath.toAbsolutePath());
        }
        try (Reader r = Files.newBufferedReader(configPath)) {
            TestConfig cfg = new Gson().fromJson(r, TestConfig.class);
            if (cfg == null || cfg.testDataFolderPath == null || cfg.testDataFolderPath.isBlank()) {
                throw new IllegalStateException("test-config.json must contain a non-empty 'testDataFolderPath'");
            }
            return cfg.testDataFolderPath;
        }
    }

    private void setUpFixture() throws Exception {
        String dataFolder = resolveTestDataFolder();

        DataStore store = DataStore.getInstance();
        store.reset();

        // Verify the data folder actually contains the expected files before loading
        Path folder = Paths.get(dataFolder);
        String[] required = {"accounts.json", "transactions.json", "categories.json"};
        List<String> missing = new java.util.ArrayList<>();
        for (String f : required) {
            if (!Files.exists(folder.resolve(f))) missing.add(f);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Test data folder '" + dataFolder + "' is missing required files: " + missing
                    + "\nUpdate testDataFolderPath in test-config.json to point to a valid data folder.");
        }

        // Load all data from the real data folder (read-only; no writes performed here)
        PersistenceService ps = new PersistenceService(dataFolder);
        ps.loadAll(store);

        // Pick a random bank account that has at least one transaction
        List<Account> accounts = store.getAccounts();
        List<Transaction> allTxns = store.getTransactions();

        List<Account> candidates = accounts.stream()
                .filter(a -> a instanceof BankAccount)
                .filter(a -> allTxns.stream().anyMatch(t ->
                        a.getId().equals(t.getFromAccountId()) || a.getId().equals(t.getToAccountId())))
                .toList();

        if (candidates.isEmpty()) {
            // Fall back to any account with transactions
            candidates = accounts.stream()
                    .filter(a -> allTxns.stream().anyMatch(t ->
                            a.getId().equals(t.getFromAccountId()) || a.getId().equals(t.getToAccountId())))
                    .toList();
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No accounts with transactions found in test data folder: " + dataFolder);
        }

        account = candidates.get(new Random().nextInt(candidates.size()));
    }

    @Override
    public void start(Stage stage) throws Exception {
        setUpFixture();
        TransactionsScreen screen = new TransactionsScreen(new MainWindow(false), account);
        stage.setScene(new Scene((Parent) screen.getView(), 1280, 800));
        stage.show();
    }

    @Test
    void transactionsScreen_rendersCoreControls() {
        verifyThat("#txn-screen-title", isVisible());
        verifyThat("#txn-search-field", isVisible());
        verifyThat("#txn-from-date-picker", isVisible());
        verifyThat("#txn-to-date-picker", isVisible());
        verifyThat("#txn-type-filter-combo", isVisible());
        verifyThat("#txn-pending-only-checkbox", isVisible());
        verifyThat("#txn-table", isVisible());
        verifyThat("#txn-export-button", isVisible());
    }

    @Test
    void transactionsScreen_loadsRowsFromRealData() {
        assertNotNull(account, "A test account must have been selected");

        @SuppressWarnings("unchecked")
        TableView<Transaction> table = lookup("#txn-table").queryAs(TableView.class);
        assertFalse(table.getItems().isEmpty(),
                "Expected at least one transaction for account: " + account.getName());
    }

    // ── JSON config DTO ──────────────────────────────────────────────────────
    private static class TestConfig {
        String testDataFolderPath;
    }
}
