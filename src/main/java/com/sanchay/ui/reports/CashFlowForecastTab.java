package com.sanchay.ui.reports;

import com.sanchay.service.CashFlowProjectionService;
import com.sanchay.service.DataStore;
import com.sanchay.service.ForecastStateService;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.StageStyle;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Third tab in ReportsScreen: projects account balances as a multi-series
 * line chart over a user-selected time horizon.
 */
public class CashFlowForecastTab {

    // ── Table row models ──────────────────────────────────────────────────────

    public record CashFlowTableRow(
            String    month,
            String    scheduleName,
            String    fromAccount,
            String    toAccount,
            String    amount,
            long      amountPaise,
            YearMonth yearMonth
    ) {}

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMM yy", java.util.Locale.ENGLISH);

    // ── Fields ────────────────────────────────────────────────────────────────

    private final DataStore                  ds            = DataStore.getInstance();
    private final CashFlowProjectionService  projService   = new CashFlowProjectionService();
    private final ForecastStateService       forecastState;
    private final ForecastChartBuilder       chartBuilder;
    private final ForecastOverridesPanel     overridesPanel;

    private final ScrollPane view;

    private ComboBox<String>            periodPicker;
    private ToggleButton                detailToggle;
    private Button                      chooseAccountsBtn;
    private Label                       summaryStrip;
    private Label                       warningBar;
    private TableView<ForecastTableRow> forecastTable;
    private ComboBox<String>            monthFilterCombo;
    private ComboBox<String>            categoryFilterCombo;
    private TextField                   forecastSearchField;
    private Label                       forecastTotalValue;
    private List<ForecastTableRow>      allForecastRows      = new ArrayList<>();

    // Second table — recurring cash flows
    private TableView<CashFlowTableRow> cashFlowTable;
    private ComboBox<String>            cashFlowMonthFilter;
    private ComboBox<String>            cashFlowFromFilter;
    private ComboBox<String>            cashFlowToFilter;
    private TextField                   cashFlowSearchField;
    private List<CashFlowTableRow>      allCashFlowRows      = new ArrayList<>();

    private boolean                     refreshing           = false;
    private boolean                     showDetailedAccounts = false;
    private CashFlowProjectionService.ProjectionResult lastProjectionResult;

    // ── Construction ─────────────────────────────────────────────────────────

    public CashFlowForecastTab() {
        forecastState  = new ForecastStateService(ds.getDataFolderPath());
        chartBuilder   = new ForecastChartBuilder(forecastState);
        overridesPanel = new ForecastOverridesPanel(forecastState, this::refresh);
        view = buildView();
        refresh();
    }

    public Node getView() { return view; }

    // ── Layout ────────────────────────────────────────────────────────────────

    private ScrollPane buildView() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));

        // Period picker
        periodPicker = new ComboBox<>();
        periodPicker.setPrefWidth(210);
        populatePeriodPicker();
        periodPicker.setValue("Next 12 Months");
        periodPicker.valueProperty().addListener((obs, o, n) -> { if (n != null && !refreshing) refresh(); });

        Label periodLabel = new Label("Time Period:");
        periodLabel.getStyleClass().add("form-label");

        // Detail toggle — only shown when >5 accounts
        detailToggle = new ToggleButton("Show Details");
        detailToggle.getStyleClass().add("btn-secondary");
        detailToggle.setVisible(false);
        detailToggle.setManaged(false);
        detailToggle.setOnAction(e -> {
            showDetailedAccounts = detailToggle.isSelected();
            detailToggle.setText(showDetailedAccounts ? "Show Summary" : "Show Details");
            updateChooseAccountsVisibility();
            if (!refreshing) refresh();
        });

        // "Choose Accounts" button — shown only while in detail mode
        chooseAccountsBtn = new Button("Choose Accounts");
        chooseAccountsBtn.getStyleClass().add("btn-secondary");
        chooseAccountsBtn.setVisible(false);
        chooseAccountsBtn.setManaged(false);
        chooseAccountsBtn.setOnAction(e -> onChooseAccountsClicked());

        // Regenerate Projections button
        Button regenerateBtn = new Button("Regenerate Projections");
        regenerateBtn.getStyleClass().add("btn-secondary");
        regenerateBtn.setOnAction(e -> onRegenerateClicked());

        HBox filterRow = new HBox(12, periodLabel, periodPicker, detailToggle, chooseAccountsBtn, regenerateBtn);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        // Warning bar — hidden by default
        warningBar = new Label();
        warningBar.getStyleClass().add("cash-flow-warning-bar");
        warningBar.setWrapText(true);
        warningBar.setMaxWidth(Double.MAX_VALUE);
        warningBar.setManaged(false);
        warningBar.setVisible(false);

        // Summary strip
        summaryStrip = new Label();
        summaryStrip.getStyleClass().add("text-section-title");
        summaryStrip.setMaxWidth(Double.MAX_VALUE);

        Label forecastSubtitle = new Label("Excluding market investments");
        forecastSubtitle.getStyleClass().add("dialog-subtitle");

        Node chartCard = chartBuilder.getChartCard();

        // ── Tabbed table area ─────────────────────────────────────────────────

        // Tab 1 — Forecasted Expenses
        HBox tableFilterBar = buildTableFilterBar();
        forecastTable = buildForecastTable();
        VBox expensesTabContent = new VBox(8, tableFilterBar, forecastTable);
        expensesTabContent.setPadding(new Insets(10, 0, 0, 0));
        Tab expensesTab = new Tab("Forecasted Expenses", expensesTabContent);
        expensesTab.setClosable(false);

        // Tab 2 — Recurring Cash Flows
        HBox cashFlowFilterBar = buildCashFlowFilterBar();
        cashFlowTable = buildCashFlowTable();
        VBox cashFlowTabContent = new VBox(8, cashFlowFilterBar, cashFlowTable);
        cashFlowTabContent.setPadding(new Insets(10, 0, 0, 0));
        Tab cashFlowTab = new Tab("Recurring Transactions", cashFlowTabContent);
        cashFlowTab.setClosable(false);

        TabPane tableTabPane = new TabPane(expensesTab, cashFlowTab);
        tableTabPane.getStyleClass().add("forecast-tab-pane");

        VBox summaryBlock = new VBox(2, summaryStrip, forecastSubtitle);
        root.getChildren().addAll(filterRow, warningBar, summaryBlock, chartCard, tableTabPane);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setFitToHeight(false);
        sp.getStyleClass().add("scroll-page-bg");
        return sp;
    }

    private HBox buildTableFilterBar() {
        Label monthLbl = new Label("MONTH");
        monthLbl.getStyleClass().add("filter-label");

        monthFilterCombo = new ComboBox<>();
        monthFilterCombo.setPromptText("All Months");
        monthFilterCombo.getStyleClass().add("filter-field");
        monthFilterCombo.setPrefWidth(120);
        monthFilterCombo.valueProperty().addListener((obs, o, n) -> applyTableFilter());

        Region sep = new Region();
        sep.getStyleClass().add("filter-separator");

        Label catLbl = new Label("CATEGORY");
        catLbl.getStyleClass().add("filter-label");

        categoryFilterCombo = new ComboBox<>();
        categoryFilterCombo.setPromptText("All Categories");
        categoryFilterCombo.getStyleClass().add("filter-field");
        categoryFilterCombo.setPrefWidth(150);
        categoryFilterCombo.valueProperty().addListener((obs, o, n) -> applyTableFilter());

        forecastSearchField = new TextField();
        forecastSearchField.setPromptText("Search description…");
        forecastSearchField.getStyleClass().add("filter-field");
        forecastSearchField.setPrefWidth(180);
        forecastSearchField.textProperty().addListener((obs, o, n) -> applyTableFilter());

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> {
            monthFilterCombo.setValue(null);
            categoryFilterCombo.setValue(null);
            forecastSearchField.clear();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label totalLbl = new Label("Total:");
        totalLbl.getStyleClass().add("filter-label");

        forecastTotalValue = new Label("—");
        forecastTotalValue.getStyleClass().addAll("cash-flow-stat-value", "cash-flow-stat-value-neg");

        HBox bar = new HBox(10, monthLbl, monthFilterCombo, sep, catLbl, categoryFilterCombo,
                forecastSearchField, clearBtn, spacer, totalLbl, forecastTotalValue);
        bar.getStyleClass().add("filter-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private HBox buildCashFlowFilterBar() {
        Label monthLbl = new Label("MONTH");
        monthLbl.getStyleClass().add("filter-label");

        cashFlowMonthFilter = new ComboBox<>();
        cashFlowMonthFilter.setPromptText("All Months");
        cashFlowMonthFilter.getStyleClass().add("filter-field");
        cashFlowMonthFilter.setPrefWidth(120);
        fixPromptText(cashFlowMonthFilter);
        cashFlowMonthFilter.valueProperty().addListener((obs, o, n) -> applyCashFlowFilter());

        Region sep1 = new Region();
        sep1.getStyleClass().add("filter-separator");

        Label fromLbl = new Label("FROM");
        fromLbl.getStyleClass().add("filter-label");

        cashFlowFromFilter = new ComboBox<>();
        cashFlowFromFilter.setPromptText("All Accounts");
        cashFlowFromFilter.getStyleClass().add("filter-field");
        cashFlowFromFilter.setPrefWidth(150);
        fixPromptText(cashFlowFromFilter);
        cashFlowFromFilter.valueProperty().addListener((obs, o, n) -> applyCashFlowFilter());

        Region sep2 = new Region();
        sep2.getStyleClass().add("filter-separator");

        Label toLbl = new Label("TO");
        toLbl.getStyleClass().add("filter-label");

        cashFlowToFilter = new ComboBox<>();
        cashFlowToFilter.setPromptText("All Accounts");
        cashFlowToFilter.getStyleClass().add("filter-field");
        cashFlowToFilter.setPrefWidth(150);
        fixPromptText(cashFlowToFilter);
        cashFlowToFilter.valueProperty().addListener((obs, o, n) -> applyCashFlowFilter());

        cashFlowSearchField = new TextField();
        cashFlowSearchField.setPromptText("Search description…");
        cashFlowSearchField.getStyleClass().add("filter-field");
        cashFlowSearchField.setPrefWidth(180);
        cashFlowSearchField.textProperty().addListener((obs, o, n) -> applyCashFlowFilter());

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("btn-secondary");
        clearBtn.setOnAction(e -> {
            cashFlowMonthFilter.setValue(null);
            cashFlowFromFilter.setValue(null);
            cashFlowToFilter.setValue(null);
            cashFlowSearchField.clear();
        });

        HBox bar = new HBox(10, monthLbl, cashFlowMonthFilter, sep1,
                fromLbl, cashFlowFromFilter, sep2, toLbl, cashFlowToFilter,
                cashFlowSearchField, clearBtn);
        bar.getStyleClass().add("filter-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void applyCashFlowFilter() {
        String selMonth = cashFlowMonthFilter.getValue();
        String selFrom  = cashFlowFromFilter.getValue();
        String selTo    = cashFlowToFilter.getValue();
        String q        = cashFlowSearchField.getText().toLowerCase().strip();
        List<CashFlowTableRow> filtered = allCashFlowRows.stream()
                .filter(r -> selMonth == null || selMonth.equals(r.month()))
                .filter(r -> selFrom  == null || selFrom.equals(r.fromAccount()))
                .filter(r -> selTo    == null || selTo.equals(r.toAccount()))
                .filter(r -> q.isEmpty() || r.scheduleName().toLowerCase().contains(q))
                .collect(Collectors.toList());
        cashFlowTable.getItems().setAll(filtered);
    }

    private TableView<CashFlowTableRow> buildCashFlowTable() {
        TableView<CashFlowTableRow> table = new TableView<>();
        table.getStyleClass().add("forecast-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setEditable(false);
        table.setPlaceholder(new Label("No recurring cash flows in this period."));

        TableColumn<CashFlowTableRow, String> monthCol = new TableColumn<>("MONTH");
        monthCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().month()));
        monthCol.setPrefWidth(100);

        TableColumn<CashFlowTableRow, String> nameCol = new TableColumn<>("SCHEDULE NAME");
        nameCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().scheduleName()));
        nameCol.setPrefWidth(200);

        TableColumn<CashFlowTableRow, String> fromCol = new TableColumn<>("FROM ACCOUNT");
        fromCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().fromAccount()));
        fromCol.setPrefWidth(160);

        TableColumn<CashFlowTableRow, String> toCol = new TableColumn<>("TO ACCOUNT");
        toCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().toAccount()));
        toCol.setPrefWidth(160);

        TableColumn<CashFlowTableRow, String> amountCol = new TableColumn<>("AMOUNT");
        amountCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().amount()));
        amountCol.setPrefWidth(120);

        table.getColumns().addAll(monthCol, nameCol, fromCol, toCol, amountCol);
        return table;
    }

    private void applyTableFilter() {
        String selMonth = monthFilterCombo.getValue();
        String selCat   = categoryFilterCombo.getValue();
        String q        = forecastSearchField.getText().toLowerCase().strip();
        List<ForecastTableRow> filtered = allForecastRows.stream()
                .filter(r -> selMonth == null || selMonth.equals(r.month()))
                .filter(r -> selCat   == null || selCat.equals(r.category()))
                .filter(r -> q.isEmpty()
                        || r.category().toLowerCase().contains(q)
                        || r.subCategory().toLowerCase().contains(q))
                .collect(Collectors.toList());
        forecastTable.getItems().setAll(filtered);

        long totalPaise = filtered.stream()
                .filter(r -> !r.excluded())
                .mapToLong(ForecastTableRow::amountPaise)
                .sum();
        forecastTotalValue.setText(MoneyFormatter.formatTableCompact(totalPaise));
    }

    private TableView<ForecastTableRow> buildForecastTable() {
        TableView<ForecastTableRow> table = new TableView<>();
        table.getStyleClass().add("forecast-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setEditable(false);

        TableColumn<ForecastTableRow, String> monthCol = new TableColumn<>("MONTH");
        monthCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().month()));
        monthCol.setPrefWidth(100);

        TableColumn<ForecastTableRow, String> categoryCol = new TableColumn<>("CATEGORY");
        categoryCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().category()));
        categoryCol.setPrefWidth(140);

        TableColumn<ForecastTableRow, String> subCategoryCol = new TableColumn<>("SUB-CATEGORY");
        subCategoryCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().subCategory()));
        subCategoryCol.setPrefWidth(140);

        TableColumn<ForecastTableRow, String> amountCol = new TableColumn<>("AMOUNT");
        amountCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().amount()));
        amountCol.setPrefWidth(120);
        amountCol.setCellFactory(col -> overridesPanel.buildAmountCell());

        TableColumn<ForecastTableRow, String> methodCol = new TableColumn<>("METHOD");
        methodCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().method()));
        methodCol.setPrefWidth(180);
        methodCol.setCellFactory(col -> overridesPanel.buildExcludedAwareCell());

        TableColumn<ForecastTableRow, Void> actionCol = new TableColumn<>("INCLUDE");
        actionCol.setPrefWidth(80);
        actionCol.setCellFactory(col -> overridesPanel.buildActionCell());

        table.getColumns().addAll(monthCol, categoryCol, subCategoryCol, amountCol, methodCol, actionCol);
        return table;
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    public void refresh() {
        if (refreshing) return;
        refreshing = true;
        try {
            doRefresh();
        } finally {
            refreshing = false;
        }
    }

    private void doRefresh() {
        populatePeriodPicker();

        LocalDate today    = LocalDate.now();
        String    selected = periodPicker.getValue();
        if (selected == null) selected = "Next 12 Months";

        LocalDate[] range     = computePeriodRange(selected, today);
        LocalDate   startDate = range[0];
        LocalDate   endDate   = range[1];

        CashFlowProjectionService.ProjectionResult result =
                projService.compute(startDate, endDate, forecastState.getOverrides());
        lastProjectionResult = result;

        // Summary strip
        summaryStrip.setText("Cash flow forecast —  " + selected
                + "  (" + startDate.format(MONTH_FMT) + " → " + endDate.format(MONTH_FMT) + ")");

        // Warnings
        if (result.warnings().isEmpty()) {
            warningBar.setManaged(false);
            warningBar.setVisible(false);
        } else {
            warningBar.setText("⚠  " + String.join("   |   ", result.warnings()));
            warningBar.setManaged(true);
            warningBar.setVisible(true);
        }

        // Show/hide detail toggle based on account count
        boolean manyAccounts = result.accounts().size() > 5;
        detailToggle.setVisible(manyAccounts);
        detailToggle.setManaged(manyAccounts);
        updateChooseAccountsVisibility();

        // Build chart
        chartBuilder.rebuild(result, showDetailedAccounts);

        // Forecast table
        updateForecastTable(result.forecastedExpenses());

        // Recurring cash flows table
        updateCashFlowTable(result.projectedCashFlows());
    }

    // ── Forecast table ────────────────────────────────────────────────────────

    private void updateForecastTable(List<CashFlowProjectionService.ForecastedExpense> forecasts) {
        allForecastRows = new ArrayList<>();
        for (CashFlowProjectionService.ForecastedExpense fe : forecasts) {
            allForecastRows.add(new ForecastTableRow(
                    fe.month().format(MONTH_FMT),
                    getCategoryName(fe.categoryId()),
                    getCategoryName(fe.subCategoryId()),
                    MoneyFormatter.formatTableCompact(fe.amountPaise()),
                    fe.amountPaise(),
                    fe.excluded() ? "Excluded" : fe.method(),
                    fe.excluded(),
                    fe.categoryId(),
                    fe.subCategoryId(),
                    fe.month()
            ));
        }

        // Repopulate filter combos, preserving current selections where still valid
        String prevMonth = monthFilterCombo.getValue();
        String prevCat   = categoryFilterCombo.getValue();

        List<String> months = allForecastRows.stream()
                .map(ForecastTableRow::month)
                .distinct()
                .collect(Collectors.toList());
        List<String> categories = allForecastRows.stream()
                .map(ForecastTableRow::category)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        monthFilterCombo.getItems().setAll(months);
        monthFilterCombo.setValue(months.contains(prevMonth) ? prevMonth : null);

        categoryFilterCombo.getItems().setAll(categories);
        categoryFilterCombo.setValue(categories.contains(prevCat) ? prevCat : null);

        applyTableFilter();
    }

    private void updateCashFlowTable(List<CashFlowProjectionService.ProjectedCashFlowRow> rows) {
        allCashFlowRows = new ArrayList<>();
        for (CashFlowProjectionService.ProjectedCashFlowRow r : rows) {
            allCashFlowRows.add(new CashFlowTableRow(
                    r.month().format(MONTH_FMT),
                    r.scheduleName(),
                    getAccountName(r.fromAccountId()),
                    getAccountName(r.toAccountId()),
                    MoneyFormatter.formatTableCompact(r.amountPaise()),
                    r.amountPaise(),
                    r.month()
            ));
        }

        String prevMonth = cashFlowMonthFilter.getValue();
        String prevFrom  = cashFlowFromFilter.getValue();
        String prevTo    = cashFlowToFilter.getValue();

        List<String> months = allCashFlowRows.stream()
                .map(CashFlowTableRow::month).distinct().collect(Collectors.toList());
        List<String> fromAccounts = allCashFlowRows.stream()
                .map(CashFlowTableRow::fromAccount).filter(s -> !"—".equals(s))
                .distinct().sorted().collect(Collectors.toList());
        List<String> toAccounts = allCashFlowRows.stream()
                .map(CashFlowTableRow::toAccount).filter(s -> !"—".equals(s))
                .distinct().sorted().collect(Collectors.toList());

        cashFlowMonthFilter.getItems().setAll(months);
        cashFlowMonthFilter.setValue(months.contains(prevMonth) ? prevMonth : null);

        cashFlowFromFilter.getItems().setAll(fromAccounts);
        cashFlowFromFilter.setValue(fromAccounts.contains(prevFrom) ? prevFrom : null);

        cashFlowToFilter.getItems().setAll(toAccounts);
        cashFlowToFilter.setValue(toAccounts.contains(prevTo) ? prevTo : null);

        applyCashFlowFilter();
    }

    private void updateChooseAccountsVisibility() {
        // "Choose Accounts" is only relevant in detail mode AND when there are many accounts
        boolean show = showDetailedAccounts && detailToggle.isManaged();
        chooseAccountsBtn.setVisible(show);
        chooseAccountsBtn.setManaged(show);
        if (show) {
            Set<String> sel = forecastState.getAccountSelection();
            chooseAccountsBtn.setText((sel == null || sel.isEmpty())
                    ? "Choose Accounts"
                    : "Choose Accounts (" + sel.size() + ")");
        }
    }

    private void onChooseAccountsClicked() {
        if (lastProjectionResult == null) return;
        AccountSelectionDialog.show(lastProjectionResult.accounts(),
                        forecastState.getAccountSelection(), forecastState.isShowSum())
                .ifPresent(result -> {
                    forecastState.saveAccountSelection(result.selection());
                    forecastState.saveShowSum(result.showSum());
                    updateChooseAccountsVisibility();
                    refresh();
                });
    }

    private void onRegenerateClicked() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.initStyle(StageStyle.UNDECORATED);
        confirm.setTitle("Regenerate Projections");
        confirm.setHeaderText("Regenerate projections?");
        confirm.setContentText("Are you sure you want to regenerate the projections? "
                + "This will overwrite any manual corrections you have made to the forecasted expenses.");
        UiUtils.applyStylesheet(confirm);

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                forecastState.clearAllOverrides();
                refresh();
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void populatePeriodPicker() {
        boolean isIndianFY = "Indian Financial Year".equals(ds.getYearFormat());
        String  fyLabel    = isIndianFY ? "This Financial Year" : "This Calendar Year";
        String  current    = periodPicker.getValue();
        periodPicker.getItems().setAll(
                "Next 6 Months", "Next 12 Months", "Next 24 Months", "Next 36 Months", fyLabel);
        if (current != null && periodPicker.getItems().contains(current)) {
            periodPicker.setValue(current);
        } else if (current != null) {
            boolean wasFyCy = current.startsWith("This ");
            periodPicker.setValue(wasFyCy ? fyLabel : "Next 12 Months");
        }
    }

    private LocalDate[] computePeriodRange(String selected, LocalDate today) {
        return switch (selected) {
            case "Next 6 Months"  -> new LocalDate[]{ today, today.plusMonths(6) };
            case "Next 24 Months" -> new LocalDate[]{ today, today.plusMonths(24) };
            case "Next 36 Months" -> new LocalDate[]{ today, today.plusMonths(36) };
            case "This Financial Year" -> {
                int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
                yield new LocalDate[]{ today, LocalDate.of(fyStartYear + 1, 3, 31) };
            }
            case "This Calendar Year" ->
                    new LocalDate[]{ today, LocalDate.of(today.getYear(), 12, 31) };
            default -> new LocalDate[]{ today, today.plusMonths(12) };
        };
    }


    private String getCategoryName(String categoryId) {
        if (categoryId == null) return "";
        return ds.getCategories().stream()
                .filter(c -> c.getId().equals(categoryId))
                .map(com.sanchay.model.Category::getName)
                .findFirst()
                .orElse("Unknown");
    }

    /**
     * JavaFX does not reliably restore prompt text on a non-editable ComboBox after
     * setValue(null) once an item has been selected. A custom button cell fixes this.
     */
    private static void fixPromptText(ComboBox<String> combo) {
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText((item == null || empty) ? combo.getPromptText() : item);
            }
        });
    }

    private String getAccountName(String accountId) {
        if (accountId == null) return "—";
        return ds.getAccounts().stream()
                .filter(a -> a.getId().equals(accountId))
                .map(com.sanchay.model.Account::getName)
                .findFirst()
                .orElse("—");
    }
}
