package com.sanchay.ui.reports;

import com.sanchay.model.*;
import com.sanchay.service.CashFlowProjectionService;
import com.sanchay.service.DataStore;
import com.sanchay.service.ForecastStateService;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;

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

    public record ForecastTableRow(
            String    month,
            String    category,
            String    subCategory,
            String    amount,
            long      amountPaise,
            String    method,
            boolean   excluded,
            String    categoryId,
            String    subCategoryId,
            YearMonth yearMonth
    ) {}

    public record CashFlowTableRow(
            String    month,
            String    scheduleName,
            String    fromAccount,
            String    toAccount,
            String    amount,
            long      amountPaise,
            YearMonth yearMonth
    ) {}

    // Chart colours are defined in UiUtils.FORECAST_SERIES_COLORS.

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    // ── Fields ────────────────────────────────────────────────────────────────

    private final DataStore                  ds            = DataStore.getInstance();
    private final CashFlowProjectionService  projService   = new CashFlowProjectionService();
    private final ForecastStateService       forecastState;

    private final ScrollPane view;

    private ComboBox<String>            periodPicker;
    private ToggleButton                detailToggle;
    private Button                      chooseAccountsBtn;
    private LineChart<String, Number>   chart;
    private FlowPane                    legendPane;
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
        forecastState = new ForecastStateService(ds.getDataFolderPath());
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

        // Chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = buildYAxis();
        chart = new LineChart<>(xAxis, yAxis);
        chart.getStyleClass().add("cash-flow-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(420);

        legendPane = new FlowPane(8, 8);
        legendPane.setPadding(new Insets(4, 12, 8, 12));

        VBox chartCard = new VBox(4, chart, legendPane);
        chartCard.getStyleClass().add("card-wrapper");

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
        Tab cashFlowTab = new Tab("Recurring Cash Flows", cashFlowTabContent);
        cashFlowTab.setClosable(false);

        TabPane tableTabPane = new TabPane(expensesTab, cashFlowTab);
        tableTabPane.getStyleClass().add("forecast-tab-pane");

        root.getChildren().addAll(filterRow, warningBar, summaryStrip, chartCard, tableTabPane);

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
        amountCol.setCellFactory(col -> new AmountCell());

        TableColumn<ForecastTableRow, String> methodCol = new TableColumn<>("METHOD");
        methodCol.setCellValueFactory(cd -> new SimpleStringProperty(cd.getValue().method()));
        methodCol.setPrefWidth(180);
        methodCol.setCellFactory(col -> buildExcludedAwareCell());

        TableColumn<ForecastTableRow, Void> actionCol = new TableColumn<>("INCLUDE");
        actionCol.setPrefWidth(80);
        actionCol.setCellFactory(col -> new ActionCell());

        table.getColumns().addAll(monthCol, categoryCol, subCategoryCol, amountCol, methodCol, actionCol);
        return table;
    }

    private NumberAxis buildYAxis() {
        NumberAxis yAxis = new NumberAxis();
        yAxis.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number n) {
                double v = n.doubleValue();
                if (Math.abs(v) >= 100_000) return String.format("%.1fL", v / 100_000.0);
                if (Math.abs(v) >= 1_000)   return String.format("%.0fK", v / 1_000.0);
                return String.valueOf((int) v);
            }
            @Override public Number fromString(String s) { return 0; }
        });
        return yAxis;
    }

    private Node buildLegendEntry(String name, String hexColor) {
        Rectangle swatch = new Rectangle(24, 10);
        // Inline required: colour is runtime data — dynamically assigned from colour arrays
        swatch.setFill(Color.web(hexColor));
        swatch.setArcWidth(3);
        swatch.setArcHeight(3);
        Label lbl = new Label(name);
        lbl.getStyleClass().add("cash-flow-legend-label");
        HBox entry = new HBox(6, swatch, lbl);
        entry.setAlignment(Pos.CENTER_LEFT);
        return entry;
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
        rebuildChart(result);

        // Forecast table
        updateForecastTable(result.forecastedExpenses());

        // Recurring cash flows table
        updateCashFlowTable(result.projectedCashFlows());
    }

    // ── Chart building ────────────────────────────────────────────────────────

    private void rebuildChart(CashFlowProjectionService.ProjectionResult result) {
        chart.getData().clear();
        legendPane.getChildren().clear();

        // Summary view always shows the overall total.
        // The persisted "show sum" preference only applies while in detailed view.
        boolean showSum = !showDetailedAccounts || forecastState.isShowSum();
        if (showSum) {
            XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
            totalSeries.setName("Total");
            for (CashFlowProjectionService.ProjectionPoint p : result.totalSeries()) {
                totalSeries.getData().add(
                        new XYChart.Data<>(p.date().format(MONTH_FMT), p.balancePaise() / 100.0));
            }
            chart.getData().add(totalSeries);
            legendPane.getChildren().add(buildLegendEntry("Total of Accounts", UiUtils.FORECAST_SERIES_COLORS[0]));
        }

        List<Account> accounts = result.accounts();
        boolean grouped = accounts.size() > 5 && !showDetailedAccounts;

        if (grouped) {
            buildGroupedSeries(accounts, result.accountSeries());
        } else {
            buildDetailedSeries(accounts, result.accountSeries());
        }

        applyDataPointTooltips();
        applyChartSeriesColors();
    }

    private void buildDetailedSeries(List<Account> accounts,
                                     Map<String, List<CashFlowProjectionService.ProjectionPoint>> accountSeries) {
        Set<String> selection = forecastState.getAccountSelection();
        List<Account> toShow;
        if (selection == null || selection.isEmpty()) {
            toShow = accounts;
        } else {
            toShow = accounts.stream()
                    .filter(a -> selection.contains(a.getId()))
                    .collect(Collectors.toList());
            if (toShow.isEmpty()) toShow = accounts; // safety fallback
        }
        accounts = toShow;

        for (int i = 0; i < accounts.size(); i++) {
            Account acc = accounts.get(i);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(acc.getName());
            List<CashFlowProjectionService.ProjectionPoint> pts = accountSeries.get(acc.getId());
            if (pts != null) {
                for (CashFlowProjectionService.ProjectionPoint p : pts) {
                    series.getData().add(
                            new XYChart.Data<>(p.date().format(MONTH_FMT), p.balancePaise() / 100.0));
                }
            }
            chart.getData().add(series);
            String color = UiUtils.FORECAST_SERIES_COLORS[(i + 1) % UiUtils.FORECAST_SERIES_COLORS.length];
            legendPane.getChildren().add(buildLegendEntry(acc.getName(), color));
        }
    }

    private void buildGroupedSeries(List<Account> accounts,
                                    Map<String, List<CashFlowProjectionService.ProjectionPoint>> accountSeries) {
        // Assign each account to a named group
        Map<String, List<String>> groupToIds = new LinkedHashMap<>();
        groupToIds.put("Bank Accounts", new ArrayList<>());
        groupToIds.put("Credit Cards",  new ArrayList<>());
        groupToIds.put("Investments",   new ArrayList<>());
        groupToIds.put("Loans",         new ArrayList<>());

        for (Account acc : accounts) {
            if      (acc instanceof BankAccount)         groupToIds.get("Bank Accounts").add(acc.getId());
            else if (acc instanceof CreditCardAccount)   groupToIds.get("Credit Cards").add(acc.getId());
            else if (acc instanceof LoanAccount)         groupToIds.get("Loans").add(acc.getId());
            else                                         groupToIds.get("Investments").add(acc.getId());
        }

        // Determine the date spine from the first non-empty series
        List<String> dateLabels = accountSeries.values().stream()
                .filter(pts -> pts != null && !pts.isEmpty())
                .findFirst()
                .map(pts -> pts.stream()
                        .map(p -> p.date().format(MONTH_FMT))
                        .collect(Collectors.toList()))
                .orElse(List.of());

        int n = dateLabels.size();
        int seriesIdx = 1; // 0 is already taken by the Total series

        for (Map.Entry<String, List<String>> entry : groupToIds.entrySet()) {
            String groupName = entry.getKey();
            List<String> ids = entry.getValue();
            if (ids.isEmpty()) continue;

            long[] sums = new long[n];
            for (String id : ids) {
                List<CashFlowProjectionService.ProjectionPoint> pts = accountSeries.get(id);
                if (pts == null) continue;
                for (int i = 0; i < Math.min(n, pts.size()); i++) {
                    sums[i] += pts.get(i).balancePaise();
                }
            }

            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(groupName);
            for (int i = 0; i < n; i++) {
                series.getData().add(new XYChart.Data<>(dateLabels.get(i), sums[i] / 100.0));
            }
            chart.getData().add(series);

            String color = UiUtils.FORECAST_SERIES_COLORS[seriesIdx % UiUtils.FORECAST_SERIES_COLORS.length];
            legendPane.getChildren().add(buildLegendEntry(groupName, color));
            seriesIdx++;
        }
    }

    private void applyChartSeriesColors() {
        // Intentional deviation from the usual CSS-first styling approach:
        // this chart also renders a custom Java-built legend, and keeping the
        // forecast palette in Java gives the legend and chart lines one source
        // of truth instead of duplicating series colours in CSS and code.
        boolean showSumNow = !showDetailedAccounts || forecastState.isShowSum();
        Platform.runLater(() -> {
            for (int i = 0; i < chart.getData().size(); i++) {
                // When showSum is true, index 0 is the Total series (gold, 3 px).
                // When showSum is false, account series start at chart index 0 but
                // should still map to palette index 1+ to match legend colours.
                int colorIdx  = showSumNow ? i : i + 1;
                String color  = UiUtils.FORECAST_SERIES_COLORS[colorIdx % UiUtils.FORECAST_SERIES_COLORS.length];
                boolean isTotal = showSumNow && i == 0;
                String seriesName = chart.getData().get(i).getName();

                Node seriesNode = chart.lookup(".chart-series-line.series" + i);
                if (seriesNode != null) {
                    String width = isTotal ? "3px" : "2px";
                    seriesNode.setStyle("-fx-stroke: " + color + "; -fx-stroke-width: " + width + ";");
                    Tooltip tt = new Tooltip(seriesName);
                    tt.setShowDelay(javafx.util.Duration.millis(80));
                    tt.setHideDelay(javafx.util.Duration.millis(100));
                    tt.getStyleClass().add("cash-flow-line-tooltip");
                    Tooltip.install(seriesNode, tt);
                }

                Node symbolNode = chart.lookup(".default-color" + i + ".chart-line-symbol");
                if (symbolNode != null) {
                    symbolNode.setStyle("-fx-background-color: " + color + ", white;");
                }
            }
        });
    }

    private void applyDataPointTooltips() {
        for (XYChart.Series<String, Number> series : chart.getData()) {
            for (XYChart.Data<String, Number> data : series.getData()) {
                long paise = Math.round(data.getYValue().doubleValue() * 100);
                String text = series.getName() + "\n"
                        + data.getXValue() + ": " + MoneyFormatter.formatTableCompact(paise);
                // nodeProperty listener fires as soon as JavaFX creates the symbol node,
                // which avoids the timing fragility of Platform.runLater approaches.
                if (data.getNode() != null) {
                    attachDataPointTooltip(data.getNode(), text);
                } else {
                    data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                        if (newNode != null) attachDataPointTooltip(newNode, text);
                    });
                }
            }
        }
    }

    private void attachDataPointTooltip(Node node, String text) {
        Tooltip tt = new Tooltip(text);
        tt.setShowDelay(javafx.util.Duration.millis(80));
        tt.setHideDelay(javafx.util.Duration.millis(100));
        tt.getStyleClass().add("cash-flow-line-tooltip");
        Tooltip.install(node, tt);
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

    // ── Custom table cells ────────────────────────────────────────────────────

    /** Cell that applies greyed-out styling to excluded rows. */
    private TableCell<ForecastTableRow, String> buildExcludedAwareCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("forecast-cell-excluded");
                if (empty || item == null) { setText(null); return; }
                setText(item);
                ForecastTableRow row = getTableView().getItems().get(getIndex());
                if (row.excluded()) getStyleClass().add("forecast-cell-excluded");
            }
        };
    }

    /** Amount cell — double-click opens a correction dialog. Excluded rows are not editable. */
    private class AmountCell extends TableCell<ForecastTableRow, String> {
        AmountCell() {
            setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !isEmpty()) {
                    ForecastTableRow row = getTableView().getItems().get(getIndex());
                    if (!row.excluded()) promptAmountCorrection(row);
                }
            });
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().remove("forecast-cell-excluded");
            if (empty || item == null) { setText(null); setTooltip(null); return; }
            setText(item);
            ForecastTableRow row = getTableView().getItems().get(getIndex());
            if (row.excluded()) {
                getStyleClass().add("forecast-cell-excluded");
            } else {
                setTooltip(new Tooltip("Double-click to correct this amount"));
            }
        }
    }

    /** Renders a checkbox — checked means included in the forecast, unchecked means excluded. */
    private class ActionCell extends TableCell<ForecastTableRow, Void> {
        private final CheckBox checkBox = new CheckBox();
        private boolean updating = false;

        ActionCell() {
            checkBox.setOnAction(e -> {
                if (updating) return;
                ForecastTableRow row = getTableView().getItems().get(getIndex());
                boolean acted = checkBox.isSelected() ? promptInclusion(row) : promptExclusion(row);
                if (!acted) {
                    // User cancelled — revert the checkbox to its original position.
                    updating = true;
                    checkBox.setSelected(!checkBox.isSelected());
                    updating = false;
                }
            });
        }

        @Override
        protected void updateItem(Void item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) { setGraphic(null); return; }
            updating = true;
            ForecastTableRow row = getTableView().getItems().get(getIndex());
            checkBox.setSelected(!row.excluded());
            updating = false;
            setGraphic(checkBox);
        }
    }

    // ── Override interaction ──────────────────────────────────────────────────

    private void promptAmountCorrection(ForecastTableRow row) {
        TextInputDialog input = new TextInputDialog(row.amount().replace(MoneyFormatter.symbol(), "").replace(",", "").trim());
        input.setTitle("Correct Forecast Amount");
        input.setHeaderText(row.subCategory() + " — " + row.month());
        input.setContentText("Enter corrected amount (₹):");
        UiUtils.applyStylesheet(input);

        Optional<String> result = input.showAndWait();
        if (result.isEmpty()) return;

        long correctedPaise;
        try {
            correctedPaise = Math.round(Double.parseDouble(result.get().replace(",", "")) * 100);
        } catch (NumberFormatException ex) {
            showError("Invalid amount. Please enter a number (e.g. 5000).");
            return;
        }

        Optional<Boolean> scope = askScope("Apply Correction",
                "Apply this correction to:", row.subCategory(), row.month());
        if (scope.isEmpty()) return;

        YearMonth targetMonth = scope.get() ? null : row.yearMonth();
        forecastState.saveOverride(ForecastOverride.correction(
                row.categoryId(), row.subCategoryId(), targetMonth, correctedPaise));
        refresh();
    }

    private boolean promptExclusion(ForecastTableRow row) {
        Optional<Boolean> scope = askScope("Exclude Sub-Category",
                "Exclude from forecast:", row.subCategory(), row.month());
        if (scope.isEmpty()) return false;

        YearMonth targetMonth = scope.get() ? null : row.yearMonth();
        forecastState.saveOverride(ForecastOverride.exclusion(
                row.categoryId(), row.subCategoryId(), targetMonth));
        refresh();
        return true;
    }

    private boolean promptInclusion(ForecastTableRow row) {
        Optional<Boolean> scope = askScope("Include Sub-Category",
                "Include back in forecast:", row.subCategory(), row.month());
        if (scope.isEmpty()) return false;

        YearMonth targetMonth = scope.get() ? null : row.yearMonth();
        forecastState.saveOverride(ForecastOverride.inclusionMarker(
                row.categoryId(), row.subCategoryId(), targetMonth));
        refresh();
        return true;
    }

    /**
     * Shows a two-choice dialog: "This month only" vs "All future months".
     * Returns Optional.of(true) for "All future months", Optional.of(false) for "This month only",
     * and Optional.empty() if the user cancelled.
     */
    private Optional<Boolean> askScope(String title, String headerPrefix,
                                        String subCatName, String monthLabel) {
        ButtonType thisMonth   = new ButtonType("This month only",    ButtonBar.ButtonData.LEFT);
        ButtonType allMonths   = new ButtonType("All future months",  ButtonBar.ButtonData.RIGHT);
        ButtonType cancel      = new ButtonType("Cancel",             ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert dlg = new Alert(Alert.AlertType.NONE);
        dlg.setTitle(title);
        dlg.setHeaderText(headerPrefix);
        dlg.setContentText(subCatName + "\n\nShould this apply to " + monthLabel
                + " only, or to all future months?");
        dlg.getButtonTypes().setAll(thisMonth, allMonths, cancel);
        dlg.getDialogPane().getStylesheets().addAll(
                forecastTable.getScene().getStylesheets());

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() == cancel) return Optional.empty();
        return Optional.of(result.get() == allMonths);
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
        confirm.getDialogPane().getStylesheets().addAll(
                forecastTable.getScene().getStylesheets());

        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                forecastState.clearAllOverrides();
                refresh();
            }
        });
    }

    private void showError(String message) {
        Alert err = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        err.getDialogPane().getStylesheets().addAll(forecastTable.getScene().getStylesheets());
        err.showAndWait();
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
