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
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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
        stage.initStyle(StageStyle.UNDECORATED);

        root = new BorderPane();
        root.setLeft(buildSidebar(stage));
        root.setCenter(buildMainPanelWrapper());

        // Window controls — floating overlay at the true top-right corner of the window
        Button minBtn   = new Button("–");
        Button maxBtn   = new Button("□");
        Button closeBtn = new Button("✕");
        minBtn.getStyleClass().add("wc-btn");
        maxBtn.getStyleClass().add("wc-btn");
        closeBtn.getStyleClass().addAll("wc-btn", "wc-btn-close");
        minBtn.setOnAction(e -> stage.setIconified(true));
        maxBtn.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        closeBtn.setOnAction(e -> stage.close());
        stage.maximizedProperty().addListener((obs, wasMax, isMax) ->
                maxBtn.setText(isMax ? "❐" : "□"));

        HBox wcBar = new HBox(1, minBtn, maxBtn, closeBtn);
        wcBar.setPadding(new Insets(4, 4, 0, 0));
        wcBar.setMaxWidth(Region.USE_PREF_SIZE); // prevent StackPane from stretching to full width
        wcBar.setPickOnBounds(false);

        // Outer root: BorderPane content + window controls overlay
        StackPane outerRoot = new StackPane(root, wcBar);
        StackPane.setAlignment(wcBar, Pos.TOP_RIGHT);

        Scene scene = new Scene(outerRoot, 1200, 750);
        scene.getStylesheets().add(
                getClass().getResource("/com/sanchay/css/app.css").toExternalForm());

        addResizeSupport(scene, stage);

        stage.setTitle("Sanchay — Personal Finance");
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();

        DataStore.getInstance().autoRecordPending();
        navigateTo("Dashboard");
    }

    private VBox buildSidebar(Stage stage) {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");

        // ── Brand / logo section ─────────────────────────────────────────────
        StackPane logoCircle = new StackPane();
        logoCircle.getStyleClass().add("logo-circle");
        Label rupee = new Label("₹");
        rupee.getStyleClass().add("logo-rupee");
        logoCircle.getChildren().add(rupee);

        VBox brandText = new VBox(1);
        Label appName = new Label("Sanchay");
        appName.getStyleClass().add("logo-app-name");
        Label tagline = new Label("Personal Finance");
        tagline.getStyleClass().add("logo-tagline");
        brandText.getChildren().addAll(appName, tagline);

        // ── Drag-to-move (logo section acts as the title bar) ─────────────────
        double[] dragOffset = {0, 0};

        HBox logoSection = new HBox(10, logoCircle, brandText);
        logoSection.setAlignment(Pos.CENTER_LEFT);
        logoSection.setPadding(new Insets(20, 16, 18, 16));

        logoSection.setOnMousePressed(e -> {
            dragOffset[0] = stage.getX() - e.getScreenX();
            dragOffset[1] = stage.getY() - e.getScreenY();
        });
        logoSection.setOnMouseDragged(e -> {
            if (!stage.isMaximized()) {
                stage.setX(e.getScreenX() + dragOffset[0]);
                stage.setY(e.getScreenY() + dragOffset[1]);
            }
        });

        // ── Divider ──────────────────────────────────────────────────────────
        Region divider = new Region();
        divider.getStyleClass().add("sidebar-divider");
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
        divider2.getStyleClass().add("sidebar-divider");
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
        subtitle.getStyleClass().add("dialog-subtitle");
        subtitle.setWrapText(true);

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(12, 14, 12, 14));
        g.getStyleClass().add("info-box");
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
        iconLbl.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--gold");
        VBox textBlock = new VBox(4);
        Label headline = new Label("Skip \u2018" + r.getDescription() + "\u2019?");
        headline.getStyleClass().add("text-step-title");
        headline.setWrapText(true);
        Label subLbl = new Label(
                "Use this when the transaction was already recorded separately. "
                + "The schedule will advance to the next due date.");
        subLbl.getStyleClass().add("dialog-subtitle");
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
        iconLbl.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--error");
        Label msgLbl = new Label(message);
        msgLbl.getStyleClass().add("text-body-muted");
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
        val.getStyleClass().add("text-form-value");
        g.add(lbl, 0, row);
        g.add(val, 1, row);
    }

    private void addDialogField(GridPane g, String labelText, javafx.scene.Node field, int row) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        g.add(lbl, 0, row);
        g.add(field, 1, row);
    }

    // ── Window resize support (UNDECORATED stage has no native resize handles) ─

    private static final int RESIZE_MARGIN = 6;

    private void addResizeSupport(Scene scene, Stage stage) {
        double[] resizeStart = new double[6]; // screenX, screenY, stageX, stageY, stageW, stageH
        boolean[] resizing   = {false};
        Cursor[]  activeCursor = {Cursor.DEFAULT};

        // Use addEventFilter (capture phase) so these fire even when a child node
        // consumes the event — required for edges overlapping the content area.
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (stage.isMaximized()) { scene.setCursor(Cursor.DEFAULT); return; }
            double x = e.getSceneX(), y = e.getSceneY();
            double w = scene.getWidth(),  h = scene.getHeight();
            boolean left  = x < RESIZE_MARGIN,  right = x > w - RESIZE_MARGIN;
            boolean top   = y < RESIZE_MARGIN,   bot   = y > h - RESIZE_MARGIN;
            Cursor c = Cursor.DEFAULT;
            if      (left && top)   c = Cursor.NW_RESIZE;
            else if (right && top)  c = Cursor.NE_RESIZE;
            else if (left && bot)   c = Cursor.SW_RESIZE;
            else if (right && bot)  c = Cursor.SE_RESIZE;
            else if (left)          c = Cursor.W_RESIZE;
            else if (right)         c = Cursor.E_RESIZE;
            else if (top)           c = Cursor.N_RESIZE;
            else if (bot)           c = Cursor.S_RESIZE;
            scene.setCursor(c);
        });

        scene.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            Cursor c = scene.getCursor();
            if (stage.isMaximized() || c == Cursor.DEFAULT) { resizing[0] = false; return; }
            resizing[0]     = true;
            activeCursor[0] = c;
            resizeStart[0]  = e.getScreenX();
            resizeStart[1]  = e.getScreenY();
            resizeStart[2]  = stage.getX();
            resizeStart[3]  = stage.getY();
            resizeStart[4]  = stage.getWidth();
            resizeStart[5]  = stage.getHeight();
        });

        scene.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!resizing[0] || stage.isMaximized()) return;
            double dx = e.getScreenX() - resizeStart[0];
            double dy = e.getScreenY() - resizeStart[1];
            Cursor c  = activeCursor[0];
            double newX = resizeStart[2], newY = resizeStart[3];
            double newW = resizeStart[4], newH = resizeStart[5];
            if (c == Cursor.E_RESIZE  || c == Cursor.NE_RESIZE || c == Cursor.SE_RESIZE)
                newW = Math.max(stage.getMinWidth(),  resizeStart[4] + dx);
            if (c == Cursor.W_RESIZE  || c == Cursor.NW_RESIZE || c == Cursor.SW_RESIZE) {
                newW = Math.max(stage.getMinWidth(),  resizeStart[4] - dx);
                newX = resizeStart[2] + (resizeStart[4] - newW);
            }
            if (c == Cursor.S_RESIZE  || c == Cursor.SE_RESIZE || c == Cursor.SW_RESIZE)
                newH = Math.max(stage.getMinHeight(), resizeStart[5] + dy);
            if (c == Cursor.N_RESIZE  || c == Cursor.NE_RESIZE || c == Cursor.NW_RESIZE) {
                newH = Math.max(stage.getMinHeight(), resizeStart[5] - dy);
                newY = resizeStart[3] + (resizeStart[5] - newH);
            }
            stage.setX(newX); stage.setY(newY);
            stage.setWidth(newW); stage.setHeight(newH);
        });

        scene.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> resizing[0] = false);
    }
}