package com.sanchay.ui.transactions;

import com.sanchay.model.Account;
import com.sanchay.model.BankAccount;
import com.sanchay.model.Category;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.NavigationContext;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.testfx.api.FxAssert.verifyThat;
import static org.testfx.matcher.base.NodeMatchers.isVisible;

class TransactionsScreenSmokeTest extends ApplicationTest {

    private Account account;

    private void setUpFixture() {
        DataStore store = DataStore.getInstance();
        store.reset();
        store.setDateFormatInternal("DD/MM/YYYY");
        store.setCurrencyInternal("INR");
        store.setYearFormatInternal("Indian Financial Year");

        Category groceries = new Category("Groceries", Category.CategoryType.EXPENSE, null);
        Category salary = new Category("Salary", Category.CategoryType.INCOME, null);
        store.addCategoryInternal(groceries);
        store.addCategoryInternal(salary);

        BankAccount bank = new BankAccount("Test Bank", BankAccount.SubType.SAVINGS);
        bank.setOpeningBalancePaise(250_000);
        store.addAccountInternal(bank);
        account = bank;

        Transaction expense = new Transaction(
                Transaction.Type.EXPENSE,
                LocalDate.of(2026, 4, 2),
                "Weekly groceries",
                12_500);
        expense.setFromAccountId(bank.getId());
        expense.setSourceIndicator(Transaction.SourceIndicator.MANUAL);
        Transaction.Classification expenseClassification = new Transaction.Classification();
        expenseClassification.setCategoryId(groceries.getId());
        expense.setClassification(expenseClassification);
        store.addTransactionInternal(expense);

        Transaction income = new Transaction(
                Transaction.Type.INCOME,
                LocalDate.of(2026, 4, 1),
                "April salary",
                150_000);
        income.setToAccountId(bank.getId());
        income.setSourceIndicator(Transaction.SourceIndicator.MANUAL);
        Transaction.Classification incomeClassification = new Transaction.Classification();
        incomeClassification.setCategoryId(salary.getId());
        income.setClassification(incomeClassification);
        store.addTransactionInternal(income);
    }

    @Override
    public void start(Stage stage) {
        setUpFixture();
        NavigationContext navCtx = new NavigationContext(a -> {}, () -> {}, () -> null, d -> {});
        TransactionsScreen screen = new TransactionsScreen(account, navCtx);
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
    void transactionsScreen_loadsSeededRows() {
        @SuppressWarnings("unchecked")
        TableView<Transaction> table = lookup("#txn-table").queryAs(TableView.class);
        assertEquals(2, table.getItems().size());
    }
}
