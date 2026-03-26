package com.sanchay.ui;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.ui.accounts.AccountsScreen;
import com.sanchay.ui.categories.CategoriesScreen;
import com.sanchay.ui.dashboard.DashboardScreen;
import com.sanchay.ui.profile.ProfileScreen;
import com.sanchay.ui.recurring.RecurringScreen;
import com.sanchay.ui.reports.ReportsScreen;
import com.sanchay.ui.settings.SettingsScreen;
import com.sanchay.ui.transactions.TransactionDialog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.*;

/** Three-zone shell: Top Bar | Sidebar | Main Panel + FAB. */
public class MainWindow {

    private BorderPane root;
    private StackPane  mainPanelWrapper;

    private DashboardScreen  dashboardScreen;
    private RecurringScreen  recurringScreen;
    private ReportsScreen    reportsScreen;
    private SettingsScreen   settingsScreen;
    private ProfileScreen    profileScreen;
    private CategoriesScreen categoriesScreen;

    // AccountsScreen is NOT cached — always rebuilt on navigation so that
    // navigating back via the sidebar always shows the account list, not a
    // stale transactions sub-view.
    private String currentScreen = "";

    // Set by AccountsScreen when a transaction list is visible, so the FAB
    // refreshes the table in place rather than rebuilding the whole screen.
    private Runnable postTransactionCallback = null;
    public void setPostTransactionCallback(Runnable cb) { this.postTransactionCallback = cb; }

    // Set by AccountsScreen when viewing a specific account's transactions,
    // so the FAB pre-populates that account in the new-transaction dialog.
    private Account transactionContextAccount = null;
    public void setTransactionContextAccount(Account acc) { this.transactionContextAccount = acc; }
    private boolean isFirstRun;

    public MainWindow(boolean isFirstRun) {
        this.isFirstRun = isFirstRun;
    }

    public void show(Stage stage) {
        root = new BorderPane();
        root.setLeft(buildSidebar());
        root.setCenter(buildMainPanelWrapper());

        Scene scene = new Scene(root, 1200, 750);
        scene.getStylesheets().add(
                getClass().getResource("/com/sanchay/css/app.css").toExternalForm());

        stage.setTitle("Sanchay — Personal Finance");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();

        DataStore.getInstance().autoRecordPending();
        navigateTo("Dashboard");
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        // ── Brand / logo section ─────────────────────────────────────────────
        StackPane logoCircle = new StackPane();
        logoCircle.setStyle(
                "-fx-background-color: #f0a500; -fx-background-radius: 18;" +
                "-fx-min-width: 36; -fx-min-height: 36;" +
                "-fx-max-width: 36; -fx-max-height: 36;" +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 2);");
        Label rupee = new Label("₹");
        rupee.setStyle("-fx-text-fill: white; -fx-font-size: 18px; -fx-font-weight: bold;");
        logoCircle.getChildren().add(rupee);

        VBox brandText = new VBox(1);
        Label appName = new Label("Sanchay");
        appName.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");
        Label tagline = new Label("Personal Finance");
        tagline.setStyle("-fx-text-fill: rgba(255,255,255,0.60); -fx-font-size: 10px;");
        brandText.getChildren().addAll(appName, tagline);

        HBox logoSection = new HBox(10, logoCircle, brandText);
        logoSection.setAlignment(Pos.CENTER_LEFT);
        logoSection.setPadding(new Insets(20, 16, 18, 16));

        // ── Divider ──────────────────────────────────────────────────────────
        Region divider = new Region();
        divider.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-pref-height: 1; -fx-max-height: 1;");
        divider.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(divider, new Insets(0, 0, 6, 0));

        // ── Nav items ────────────────────────────────────────────────────────
        VBox topItems = new VBox(2);
        topItems.getChildren().addAll(
                buildNavButton("Dashboard",  "🏠  Dashboard"),
                buildNavButton("Accounts",   "🏦  Accounts"),
                buildNavButton("Recurring",  "🔁  Recurring"),
                buildNavButton("Reports",    "📊  Reports"),
                buildNavButton("Categories", "🗂️  Categories")
        );

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // ── Bottom items ─────────────────────────────────────────────────────
        Region divider2 = new Region();
        divider2.setStyle("-fx-background-color: rgba(255,255,255,0.12); -fx-pref-height: 1; -fx-max-height: 1;");
        divider2.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(divider2, new Insets(6, 0, 6, 0));

        Button profileBtn  = buildNavButton("Profile",  "👤  Profile");
        Button settingsBtn = buildNavButton("Settings", "⚙️  Settings");

        Button helpBtn = new Button("❓  Help");
        helpBtn.getStyleClass().add("sidebar-item");
        helpBtn.setMaxWidth(Double.MAX_VALUE);
        helpBtn.setOnAction(e -> new HelpDialog(root.getScene().getWindow()).show());
        VBox.setMargin(helpBtn, new Insets(0, 0, 12, 0));

        sidebar.getChildren().addAll(
                logoSection, divider,
                topItems,
                spacer,
                divider2, profileBtn, settingsBtn, helpBtn);
        return sidebar;
    }

    private Button buildNavButton(String key, String label) {
        Button btn = new Button(label);
        btn.getStyleClass().add("sidebar-item");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> navigateTo(key));
        return btn;
    }

    private StackPane buildMainPanelWrapper() {
        mainPanelWrapper = new StackPane();

        Button fab = new Button("+");
        fab.getStyleClass().add("fab");
        fab.setOnAction(e -> openNewTransactionDialog());
        StackPane.setAlignment(fab, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(fab, new Insets(0, 24, 24, 0));

        mainPanelWrapper.getChildren().addAll(new StackPane(), fab);
        return mainPanelWrapper;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public void navigateTo(String screen) {
        currentScreen = screen;
        updateSidebarHighlight(screen);

        javafx.scene.Node content = switch (screen) {
            case "Dashboard" -> {
                if (dashboardScreen == null) dashboardScreen = new DashboardScreen(this, isFirstRun);
                else dashboardScreen.refresh();
                isFirstRun = false;
                yield dashboardScreen.getView();
            }
            case "Accounts" -> {
                AccountsScreen accountsScreen = new AccountsScreen(this);
                yield accountsScreen.getView();
            }
            case "Recurring" -> {
                if (recurringScreen == null) recurringScreen = new RecurringScreen(this);
                recurringScreen.refresh();
                yield recurringScreen.getView();
            }
            case "Reports" -> {
                if (reportsScreen == null) reportsScreen = new ReportsScreen();
                else reportsScreen.refresh();
                yield reportsScreen.getView();
            }
            case "Categories" -> {
                if (categoriesScreen == null) categoriesScreen = new CategoriesScreen();
                else categoriesScreen.refresh();
                yield categoriesScreen.getView();
            }
            case "Profile" -> {
                if (profileScreen == null) profileScreen = new ProfileScreen();
                else profileScreen.refresh();
                yield profileScreen.getView();
            }
            case "Settings" -> {
                if (settingsScreen == null) settingsScreen = new SettingsScreen();
                yield settingsScreen.getView();
            }
            default -> new Label("Screen not found: " + screen);
        };

        javafx.scene.Node fab = mainPanelWrapper.getChildren()
                .get(mainPanelWrapper.getChildren().size() - 1);
        mainPanelWrapper.getChildren().clear();
        mainPanelWrapper.getChildren().addAll(content, fab);
    }

    public void navigateToRecurring() { navigateTo("Recurring"); }

    public void refreshDashboard() {
        if (dashboardScreen != null) dashboardScreen.refresh();
        if ("Dashboard".equals(currentScreen)) navigateTo("Dashboard");
    }

    private void updateSidebarHighlight(String active) {
        VBox sidebar = (VBox) root.getLeft();
        sidebar.lookupAll(".sidebar-item").forEach(node -> {
            node.getStyleClass().remove("sidebar-item-active");
            if (node instanceof Button btn && btn.getText().contains(active))
                btn.getStyleClass().add("sidebar-item-active");
        });
    }

    private void refreshCurrentScreen() { navigateTo(currentScreen); }

    // ── FAB — New Transaction ─────────────────────────────────────────────────

    private void openNewTransactionDialog() {
        TransactionDialog dlg = new TransactionDialog();
        if (transactionContextAccount != null) dlg.setContextAccount(transactionContextAccount);
        dlg.showAndWait().ifPresent(t -> {
            if (postTransactionCallback != null) postTransactionCallback.run();
            else refreshCurrentScreen();
        });
    }

    // ── Record / Skip recurring ───────────────────────────────────────────────

    /**
     * Record confirmation dialog — invoked from both DashboardScreen and RecurringScreen.
     * Contains: Transaction Date (defaults today), Amount (pre-filled), From Account Account.
     * After recording, onComplete is called so the caller can refresh its pending list.
     */
    public void recordRecurring(RecurringTransaction r, Runnable onComplete) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Record — " + r.getDescription());
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(420);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "↺", "Record — " + r.getDescription());

        Label subtitle = new Label("Confirm the details for this occurrence:");
        subtitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #7aa4b0; -fx-padding: 0 0 4 0;");
        subtitle.setWrapText(true);

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(12, 14, 12, 14));
        g.setStyle(
                "-fx-background-color: #f8fbfc; -fx-background-radius: 8; "
                + "-fx-border-color: rgba(42,138,122,0.15); -fx-border-radius: 8; -fx-border-width: 1;");
        ColumnConstraints c1 = new ColumnConstraints(140);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        int row = 0;

        addDialogLabel(g, "Transaction:", r.getDescription(), row++);
        addDialogField(g, "Type:",        AccountsScreen.typeBadge(r.getTransactionType()), row++);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(datePicker);
        UiUtils.styleOnShow(datePicker);
        addDialogField(g, "Date:", datePicker, row++);

        TextField amtFld = new TextField(
                r.getAmountPaise() > 0
                        ? String.format("%.2f", r.getAmountPaise() / 100.0)
                        : "");
        amtFld.setPromptText("Enter amount");
        amtFld.setMaxWidth(Double.MAX_VALUE);
        addDialogField(g, "Amount (₹):", amtFld, row++);

        DataStore ds = DataStore.getInstance();
        Transaction.Type rType = r.getTransactionType();

        // From Account — shown for all types that debit an account
        ComboBox<Account> fromAccountCb = new ComboBox<>();
        boolean showFromAccount = rType == Transaction.Type.EXPENSE
                || rType == Transaction.Type.CC_PAYMENT
                || rType == Transaction.Type.TRANSFER
                || rType == Transaction.Type.LOAN_PAYMENT
                || rType == Transaction.Type.INVESTMENT;
        if (showFromAccount) {
            ds.getAccounts().stream()
                    .filter(a -> a.isActive() && !(a instanceof CreditCardAccount))
                    .forEach(fromAccountCb.getItems()::add);
            if (r.getFromAccountId() != null)
                fromAccountCb.getItems().stream()
                        .filter(a -> a.getId().equals(r.getFromAccountId()))
                        .findFirst().ifPresent(fromAccountCb::setValue);
            if (fromAccountCb.getValue() == null && !fromAccountCb.getItems().isEmpty())
                fromAccountCb.setValue(fromAccountCb.getItems().get(0));
            fromAccountCb.setMaxWidth(Double.MAX_VALUE);
            addDialogField(g, "From Account:", fromAccountCb, row++);
        }

        // To Account — shown for Income and Transfer
        ComboBox<Account> toAccountCb = new ComboBox<>();
        boolean showToAccount = rType == Transaction.Type.INCOME
                || rType == Transaction.Type.TRANSFER;
        if (showToAccount) {
            ds.getAccounts().stream()
                    .filter(a -> a.isActive() && a instanceof BankAccount)
                    .forEach(toAccountCb.getItems()::add);
            if (r.getToAccountId() != null)
                toAccountCb.getItems().stream()
                        .filter(a -> a.getId().equals(r.getToAccountId()))
                        .findFirst().ifPresent(toAccountCb::setValue);
            if (toAccountCb.getValue() == null && !toAccountCb.getItems().isEmpty())
                toAccountCb.setValue(toAccountCb.getItems().get(0));
            toAccountCb.setMaxWidth(Double.MAX_VALUE);
            addDialogField(g, "To Account:", toAccountCb, row++);
        }

        VBox dialogContent = new VBox(12, subtitle, g);
        dialogContent.setPadding(new Insets(16));
        dlg.getDialogPane().setContent(dialogContent);
        ButtonType recordBtn = new ButtonType("Record", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(recordBtn, ButtonType.CANCEL);

        // Validate before allowing the dialog to close so it stays open on error
        Platform.runLater(() -> {
            javafx.scene.control.Button btn =
                    (javafx.scene.control.Button) dlg.getDialogPane().lookupButton(recordBtn);
            if (btn == null) return;
            btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                String amtRaw = amtFld.getText().trim().replace(",", "").replace("₹", "");
                boolean amtOk = true;
                try { if (Double.parseDouble(amtRaw) <= 0) amtOk = false; }
                catch (NumberFormatException ignored) { amtOk = false; }
                if (!amtOk) {
                    showStyledError("Enter a valid positive amount.");
                    ev.consume();
                    return;
                }
                if (showFromAccount && showToAccount
                        && fromAccountCb.getValue() != null && toAccountCb.getValue() != null
                        && fromAccountCb.getValue().getId().equals(toAccountCb.getValue().getId())) {
                    showStyledError("From and To accounts must differ.");
                    ev.consume();
                }
            });
        });

        dlg.setResultConverter(bt -> bt == recordBtn);
        dlg.showAndWait().ifPresent(confirmed -> {
            if (!confirmed) return;
            String amtRaw = amtFld.getText().trim().replace(",", "").replace("₹", "");
            long paise = Math.round(Double.parseDouble(amtRaw) * 100);
            LocalDate txDate = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();
            Transaction t = new Transaction(rType, txDate, r.getDescription(), paise);
            if (showFromAccount && fromAccountCb.getValue() != null)
                t.setFromAccountId(fromAccountCb.getValue().getId());
            else
                t.setFromAccountId(r.getFromAccountId());
            if (showToAccount && toAccountCb.getValue() != null)
                t.setToAccountId(toAccountCb.getValue().getId());
            else
                t.setToAccountId(r.getToAccountId());
            t.setCategoryId(r.getCategoryId());
            t.setSubCategoryId(r.getSubCategoryId());
            t.setFromRecurring(true);
            t.setRecurringId(r.getId());
            ds.addTransaction(t);
            r.markRecorded(txDate);
            ds.saveRecurringNow();
            if (onComplete != null) onComplete.run();
            refreshDashboard();
        });
    }

    /** Convenience overload for callers that don't need a completion callback. */
    public void recordRecurring(RecurringTransaction r) {
        recordRecurring(r, null);
    }

    public void skipRecurring(RecurringTransaction r, Runnable onComplete) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Skip Occurrence");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(400);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "↷", "Skip Occurrence");

        VBox body = new VBox(14);
        body.setPadding(new Insets(16));

        HBox iconRow = new HBox(14);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        Label iconLbl = new Label("↷");
        iconLbl.setStyle(
                "-fx-background-color: rgba(240,165,0,0.12); "
                + "-fx-border-color: rgba(240,165,0,0.30); "
                + "-fx-border-radius: 50; -fx-background-radius: 50; "
                + "-fx-text-fill: #d4900a; -fx-font-size: 20px; "
                + "-fx-min-width: 42; -fx-min-height: 42; "
                + "-fx-max-width: 42; -fx-max-height: 42; -fx-alignment: center;");
        VBox textBlock = new VBox(4);
        Label headline = new Label("Skip \u2018" + r.getDescription() + "\u2019?");
        headline.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #0f3d4a;");
        headline.setWrapText(true);
        Label subLbl = new Label(
                "Use this when the transaction was already recorded separately. "
                + "The schedule will advance to the next due date.");
        subLbl.setStyle("-fx-font-size: 12.5px; -fx-text-fill: #7aa4b0;");
        subLbl.setWrapText(true);
        subLbl.setMaxWidth(300);
        textBlock.getChildren().addAll(headline, subLbl);
        iconRow.getChildren().addAll(iconLbl, textBlock);
        body.getChildren().add(iconRow);

        dlg.getDialogPane().setContent(body);
        ButtonType skipBtn = new ButtonType("Skip", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, skipBtn);

        dlg.showAndWait().filter(b -> b == skipBtn).ifPresent(b -> {
            r.markRecorded(LocalDate.now());
            DataStore.getInstance().saveRecurringNow();
            if (onComplete != null) onComplete.run();
            refreshDashboard();
        });
    }

    // ── Dialog helpers ────────────────────────────────────────────────────────

    private void showStyledError(String message) {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Validation Error");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(380);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "⚠", "Validation Error");

        VBox body = new VBox(10);
        body.setPadding(new Insets(16));

        HBox iconRow = new HBox(14);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        Label iconLbl = new Label("⚠");
        iconLbl.setStyle(
                "-fx-background-color: rgba(192,57,43,0.10); "
                + "-fx-border-color: rgba(192,57,43,0.25); "
                + "-fx-border-radius: 50; -fx-background-radius: 50; "
                + "-fx-text-fill: #c0392b; -fx-font-size: 18px; "
                + "-fx-min-width: 42; -fx-min-height: 42; "
                + "-fx-max-width: 42; -fx-max-height: 42; -fx-alignment: center;");
        Label msgLbl = new Label(message);
        msgLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #0f3d4a;");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(280);
        iconRow.getChildren().addAll(iconLbl, msgLbl);
        body.getChildren().add(iconRow);

        dlg.getDialogPane().setContent(body);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dlg.showAndWait();
    }

    private void addDialogLabel(GridPane g, String labelText, String value, int row) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #0f3d4a; -fx-font-size: 12px;");
        g.add(lbl, 0, row);
        g.add(val, 1, row);
    }

    private void addDialogField(GridPane g, String labelText, javafx.scene.Node field, int row) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        g.add(lbl, 0, row);
        g.add(field, 1, row);
    }
}