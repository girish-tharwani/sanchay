package com.sanchay.ui;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.service.PersistenceService;
import com.sanchay.ui.accounts.AccountsScreen;
import com.sanchay.ui.categories.CategoriesScreen;
import com.sanchay.ui.help.HelpScreen;
import com.sanchay.ui.dashboard.DashboardScreen;
import com.sanchay.ui.profile.ProfileScreen;
import com.sanchay.ui.recurring.RecordRecurringDialog;
import com.sanchay.ui.recurring.RecurringScreen;
import com.sanchay.ui.recurring.SkipRecurringDialog;
import com.sanchay.ui.planning.FinancialPlanningScreen;
import com.sanchay.ui.reports.ReportsScreen;
import com.sanchay.ui.settings.SettingsScreen;
import com.sanchay.ui.transactions.TransactionDialog;
import com.sanchay.ui.transactions.TransactionsScreen;
import com.sanchay.ui.wizard.PreferencesSetupDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

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
    private FinancialPlanningScreen financialPlanningScreen;
    private TransactionsScreen transactionsScreen;
    private HelpScreen helpScreen;
    private double restoredX;
    private double restoredY;
    private double restoredWidth;
    private double restoredHeight;
    private boolean customMaximized = false;

    // AccountsScreen is NOT cached — always rebuilt on navigation so that
    // navigating back via the sidebar always shows the account list, not a
    // stale transactions sub-view.
    private String  currentScreen            = "";
    private Account currentTransactionAccount = null;

    // Per-navigation context: FAB state written by screens; navigation callbacks read by screens.
    private NavigationContext navCtx = new NavigationContext(null, null, null, null);

    // Remembered across the session so repeated CSV exports start in the same folder.
    private String lastAccountExportDir = null;

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
        maxBtn.setOnAction(e -> toggleMaximize(stage));
        closeBtn.setOnAction(e -> stage.close());
        stage.maximizedProperty().addListener((obs, wasMax, isMax) -> {
            customMaximized = isMax;
            maxBtn.setText(isMax ? "❐" : "□");
        });

        HBox wcBar = new HBox(1, minBtn, maxBtn, closeBtn);
        wcBar.setPadding(new Insets(4, 4, 0, 0));
        wcBar.setMaxWidth(Region.USE_PREF_SIZE); // prevent StackPane from stretching to full width
        wcBar.setPickOnBounds(false);

        // Outer root: BorderPane content + window controls overlay
        StackPane outerRoot = new StackPane(root, wcBar);
        StackPane.setAlignment(wcBar, Pos.TOP_RIGHT);

        Scene scene = new Scene(outerRoot, 1180, 750);
        for (String f : new String[]{
                "/com/sanchay/css/theme.css",
                "/com/sanchay/css/components.css",
                "/com/sanchay/css/layout.css",
                "/com/sanchay/css/reports.css",
                "/com/sanchay/css/planning.css",
                "/com/sanchay/css/screens/help.css"}) {
            scene.getStylesheets().add(getClass().getResource(f).toExternalForm());
        }

        addResizeSupport(scene, stage);

        stage.setTitle("Sanchay — Personal Finance");
        stage.setScene(scene);
        stage.setMinWidth(1185);
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
        Label rupee = new Label(MoneyFormatter.symbol());
        rupee.getStyleClass().add("logo-rupee");
        logoCircle.getChildren().add(rupee);

        VBox brandText = new VBox(1);
        Label appName = new Label("Sanchay");
        appName.getStyleClass().add("logo-app-name");
        Label tagline = new Label("Personal Finance");
        tagline.getStyleClass().add("logo-tagline");
        brandText.getChildren().addAll(appName, tagline);

        HBox logoSection = new HBox(10, logoCircle, brandText);
        logoSection.setAlignment(Pos.CENTER_LEFT);
        logoSection.setPadding(new Insets(20, 16, 18, 16));

        // ── Drag-to-move (entire sidebar acts as the drag handle) ─────────────
        double[] dragOffset = {0, 0};
        sidebar.setOnMousePressed(e -> {
            dragOffset[0] = stage.getX() - e.getScreenX();
            dragOffset[1] = stage.getY() - e.getScreenY();
        });
        sidebar.setOnMouseDragged(e -> {
            if (!isWindowMaximized(stage)) {
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
                buildNavButton("Reports",         "📊  Reports"),
                buildNavButton("FinancialPlan",   "🌐  Financial Plan"),
                buildNavButton("Categories",      "🗂️  Categories")
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
        helpBtn.setOnAction(e -> navigateTo("Help"));
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
        // Fresh context on every navigation; AccountsScreen re-populates it when needed.
        navCtx = new NavigationContext(null, null, null, null);
        currentScreen = screen;
        currentTransactionAccount = null;
        updateSidebarHighlight(screen);

        javafx.scene.Node content = switch (screen) {
            case "Dashboard" -> {
                if (dashboardScreen == null) dashboardScreen = new DashboardScreen(this, isFirstRun);
                else dashboardScreen.refresh();
                isFirstRun = false;
                yield dashboardScreen.getView();
            }
            case "Accounts" -> {
                navCtx = new NavigationContext(this::navigateToTransactions, null, null, null);
                AccountsScreen accountsScreen = new AccountsScreen(navCtx);
                yield accountsScreen.getView();
            }
            case "Recurring" -> {
                if (recurringScreen == null) recurringScreen = new RecurringScreen(this);
                recurringScreen.refresh();
                yield recurringScreen.getView();
            }
            case "Reports" -> {
                if (reportsScreen == null) reportsScreen = new ReportsScreen();
                reportsScreen.refresh();
                yield reportsScreen.getView();
            }
            case "FinancialPlan" -> {
                if (financialPlanningScreen == null)
                    financialPlanningScreen = new FinancialPlanningScreen(() -> navigateTo("Profile"));
                else
                    financialPlanningScreen.refresh();
                yield financialPlanningScreen.getView();
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
                if (settingsScreen == null) settingsScreen = new SettingsScreen(this::reloadDataFolder);
                else settingsScreen.refresh();
                yield settingsScreen.getView();
            }
            case "Help" -> {
                if (helpScreen == null) helpScreen = new HelpScreen();
                yield helpScreen.getView();
            }
            default -> new Label("Screen not found: " + screen);
        };

        javafx.scene.Node fab = mainPanelWrapper.getChildren()
                .get(mainPanelWrapper.getChildren().size() - 1);
        mainPanelWrapper.getChildren().clear();
        mainPanelWrapper.getChildren().addAll(content, fab);
    }

    public void navigateToRecurring() { navigateTo("Recurring"); }

    public void navigateToTransactions(Account account) {
        currentScreen = "Transactions";
        currentTransactionAccount = account;
        updateSidebarHighlight("Accounts"); // Highlight Accounts since Transactions is a sub-view

        navCtx = new NavigationContext(
                null,
                () -> navigateTo("Accounts"),
                () -> lastAccountExportDir,
                dir -> lastAccountExportDir = dir);
        transactionsScreen = new TransactionsScreen(account, navCtx);
        javafx.scene.Node content = transactionsScreen.getView();

        javafx.scene.Node fab = mainPanelWrapper.getChildren()
                .get(mainPanelWrapper.getChildren().size() - 1);
        mainPanelWrapper.getChildren().clear();
        mainPanelWrapper.getChildren().addAll(content, fab);
    }

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

    private void refreshCurrentScreen() {
        if ("Transactions".equals(currentScreen) && currentTransactionAccount != null)
            navigateToTransactions(currentTransactionAccount);
        else
            navigateTo(currentScreen);
    }

    // ── FAB — New Transaction ─────────────────────────────────────────────────

    private void openNewTransactionDialog() {
        TransactionDialog dlg = new TransactionDialog();
        if (navCtx.getContextAccount() != null) dlg.setContextAccount(navCtx.getContextAccount());
        dlg.showAndWait().ifPresent(t -> {
            Runnable cb = navCtx.getOnTransactionSaved();
            if (cb != null) cb.run();
            else refreshCurrentScreen();
        });
    }

    // ── Record / Skip recurring ───────────────────────────────────────────────

    /** Invoked from both DashboardScreen and RecurringScreen. */
    public void recordRecurring(RecurringTransaction r, Runnable onComplete) {
        new RecordRecurringDialog(r).show(onComplete, this::refreshDashboard);
    }

    /** Convenience overload for callers that don't need a completion callback. */
    public void recordRecurring(RecurringTransaction r) {
        recordRecurring(r, null);
    }

    public void skipRecurring(RecurringTransaction r, Runnable onComplete) {
        new SkipRecurringDialog(r).show(onComplete, this::refreshDashboard);
    }

    // ── Data folder reload (no restart required) ──────────────────────────────

    /**
     * Switches the app to a new data folder in-session.
     * Resets DataStore, loads from newPath, applies optional first-run preferences,
     * discards all cached screens, and navigates to Dashboard.
     */
    public void reloadDataFolder(String newPath, PreferencesSetupDialog.Result prefs) {
        DataStore store = DataStore.getInstance();
        store.reset();

        PersistenceService persistence = new PersistenceService(newPath);
        store.setPersistence(persistence);
        store.setDataFolderPath(newPath);
        persistence.loadAll(store);

        if (prefs != null) {
            store.setCurrencyInternal(prefs.currency());
            store.setYearFormatInternal(prefs.yearFormat());
            store.setDateFormatInternal(prefs.dateFormat());
            persistence.saveSettings(store);
        }

        dashboardScreen  = null;
        recurringScreen  = null;
        reportsScreen    = null;
        settingsScreen   = null;
        profileScreen    = null;
        categoriesScreen = null;
        financialPlanningScreen = null;
        transactionsScreen = null;
        helpScreen       = null;
        isFirstRun       = false;

        navigateTo("Dashboard");
    }

    // ── Window resize support (UNDECORATED stage has no native resize handles) ─

    private static final int RESIZE_MARGIN = 6;

    private boolean isWindowMaximized(Stage stage) {
        return customMaximized || stage.isMaximized();
    }

    private void toggleMaximize(Stage stage) {
        if (isWindowMaximized(stage)) {
            restoreWindow(stage);
        } else {
            maximizeToVisualBounds(stage);
        }
    }

    private void maximizeToVisualBounds(Stage stage) {
        restoredX = stage.getX();
        restoredY = stage.getY();
        restoredWidth = stage.getWidth();
        restoredHeight = stage.getHeight();

        Rectangle2D currentBounds = new Rectangle2D(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        Screen screen = Screen.getScreensForRectangle(currentBounds).stream()
                .findFirst()
                .orElse(Screen.getPrimary());
        Rectangle2D visualBounds = screen.getVisualBounds();

        customMaximized = true;
        stage.setMaximized(false);
        stage.setX(visualBounds.getMinX());
        stage.setY(visualBounds.getMinY());
        stage.setWidth(visualBounds.getWidth());
        stage.setHeight(visualBounds.getHeight());
    }

    private void restoreWindow(Stage stage) {
        customMaximized = false;
        stage.setMaximized(false);
        if (restoredWidth > 0 && restoredHeight > 0) {
            stage.setX(restoredX);
            stage.setY(restoredY);
            stage.setWidth(restoredWidth);
            stage.setHeight(restoredHeight);
        }
    }

    private void addResizeSupport(Scene scene, Stage stage) {
        double[] resizeStart = new double[6]; // screenX, screenY, stageX, stageY, stageW, stageH
        boolean[] resizing   = {false};
        Cursor[]  activeCursor = {Cursor.DEFAULT};

        // Use addEventFilter (capture phase) so these fire even when a child node
        // consumes the event — required for edges overlapping the content area.
        scene.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            if (isWindowMaximized(stage)) { scene.setCursor(Cursor.DEFAULT); return; }
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
            if (isWindowMaximized(stage) || c == Cursor.DEFAULT) { resizing[0] = false; return; }
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
            if (!resizing[0] || isWindowMaximized(stage)) return;
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

