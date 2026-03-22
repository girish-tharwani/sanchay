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
    private boolean isFirstRun;

    public MainWindow(boolean isFirstRun) {
        this.isFirstRun = isFirstRun;
    }

    public void show(Stage stage) {
        root = new BorderPane();
        root.setTop(buildTopBar());
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

        navigateTo("Dashboard");
    }

    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, 20, 0, 20));

        Label title = new Label("💰 Sanchay");
        title.getStyleClass().add("top-bar-title");

        bar.getChildren().add(title);
        return bar;
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

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

        Button profileBtn  = buildNavButton("Profile",  "👤  Profile");
        Button settingsBtn = buildNavButton("Settings", "⚙️  Settings");

        Button helpBtn = new Button("❓  Help");
        helpBtn.getStyleClass().add("sidebar-item");
        helpBtn.setMaxWidth(Double.MAX_VALUE);
        helpBtn.setOnAction(e -> new HelpDialog(root.getScene().getWindow()).show());
        VBox.setMargin(helpBtn, new Insets(0, 0, 12, 0));

        sidebar.getChildren().addAll(topItems, spacer, profileBtn, settingsBtn, helpBtn);
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
        dlg.showAndWait().ifPresent(t -> {
            if (postTransactionCallback != null) postTransactionCallback.run();
            else refreshCurrentScreen();
        });
    }

    // ── Record / Skip recurring ───────────────────────────────────────────────

    /**
     * Record confirmation dialog — invoked from both DashboardScreen and RecurringScreen.
     * Contains: Transaction Date (defaults today), Amount (pre-filled), Paid From Account.
     * After recording, onComplete is called so the caller can refresh its pending list.
     */
    public void recordRecurring(RecurringTransaction r, Runnable onComplete) {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Record — " + r.getDescription());
        dlg.setHeaderText("Confirm details for this occurrence:");
        dlg.getDialogPane().setPrefWidth(420);

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(12);
        g.setPadding(new Insets(16));
        ColumnConstraints c1 = new ColumnConstraints(140);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        int row = 0;

        addDialogLabel(g, "Transaction:", r.getDescription(), row++);
        addDialogLabel(g, "Type:",        r.getTransactionType().name().replace("_", " "), row++);

        DatePicker datePicker = new DatePicker(LocalDate.now());
        datePicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(datePicker);
        addDialogField(g, "Date:", datePicker, row++);

        TextField amtFld = new TextField(
                r.getAmountPaise() > 0
                        ? String.format("%.2f", r.getAmountPaise() / 100.0)
                        : "");
        amtFld.setPromptText("Enter amount");
        amtFld.setMaxWidth(Double.MAX_VALUE);
        addDialogField(g, "Amount (₹):", amtFld, row++);

        ComboBox<Account> accountCb = new ComboBox<>();
        DataStore ds = DataStore.getInstance();
        boolean showAccount = r.getTransactionType() == Transaction.Type.EXPENSE
                || r.getTransactionType() == Transaction.Type.CC_PAYMENT
                || r.getTransactionType() == Transaction.Type.TRANSFER
                || r.getTransactionType() == Transaction.Type.INVESTMENT;
        if (showAccount) {
            ds.getAccounts().stream()
                    .filter(a -> a.isActive() && !(a instanceof CreditCardAccount))
                    .forEach(accountCb.getItems()::add);
            if (r.getFromAccountId() != null) {
                accountCb.getItems().stream()
                        .filter(a -> a.getId().equals(r.getFromAccountId()))
                        .findFirst().ifPresent(accountCb::setValue);
            }
            if (accountCb.getValue() == null && !accountCb.getItems().isEmpty())
                accountCb.setValue(accountCb.getItems().get(0));
            accountCb.setMaxWidth(Double.MAX_VALUE);
            addDialogField(g, "Paid From:", accountCb, row++);
        }

        dlg.getDialogPane().setContent(g);
        ButtonType recordBtn = new ButtonType("Record", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(recordBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> bt == recordBtn);
        dlg.showAndWait().ifPresent(confirmed -> {
            if (!confirmed) return;
            String amtRaw = amtFld.getText().trim().replace(",", "").replace("₹", "");
            try {
                double v = Double.parseDouble(amtRaw);
                if (v <= 0) throw new NumberFormatException();
                long paise = Math.round(v * 100);
                LocalDate txDate = datePicker.getValue() != null ? datePicker.getValue() : LocalDate.now();

                Transaction t = new Transaction(r.getTransactionType(), txDate, r.getDescription(), paise);
                if (showAccount && accountCb.getValue() != null)
                    t.setFromAccountId(accountCb.getValue().getId());
                else
                    t.setFromAccountId(r.getFromAccountId());
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
            } catch (NumberFormatException ex) {
                Alert err = new Alert(Alert.AlertType.ERROR, "Enter a valid positive amount.");
                err.showAndWait();
            }
        });
    }

    /** Convenience overload for callers that don't need a completion callback. */
    public void recordRecurring(RecurringTransaction r) {
        recordRecurring(r, null);
    }

    public void skipRecurring(RecurringTransaction r, Runnable onComplete) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Skip Occurrence");
        confirm.setHeaderText("Skip '" + r.getDescription() + "'?");
        confirm.setContentText(
                "Use this when the transaction was already recorded separately.\n"
                        + "The schedule will advance to the next due date.");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            r.markRecorded(LocalDate.now());
            DataStore.getInstance().saveRecurringNow();
            if (onComplete != null) onComplete.run();
            refreshDashboard();
        });
    }

    // ── Dialog helpers ────────────────────────────────────────────────────────

    private void addDialogLabel(GridPane g, String labelText, String value, int row) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-weight: bold;");
        Label val = new Label(value);
        val.setStyle("-fx-text-fill: #595959;");
        g.add(lbl, 0, row);
        g.add(val, 1, row);
    }

    private void addDialogField(GridPane g, String labelText, javafx.scene.Node field, int row) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-weight: bold;");
        g.add(lbl, 0, row);
        g.add(field, 1, row);
    }
}