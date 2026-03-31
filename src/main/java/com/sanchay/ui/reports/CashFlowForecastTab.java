package com.sanchay.ui.reports;

import com.sanchay.model.Account;
import com.sanchay.service.CashFlowProjectionService;
import com.sanchay.service.DataStore;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Third tab in ReportsScreen: projects account balances as a multi-series
 * line chart over a user-selected time horizon.
 */
public class CashFlowForecastTab {

    // Data class for forecast table rows
    public record ForecastTableRow(String month, String category, String subCategory, String amount, String method) {}

    // Series swatch hex colors — index 0 = Total (gold), 1–7 = individual accounts.
    // Inline required: colours are runtime-assigned to legend swatch rectangles from this array.
    private static final String[] SERIES_COLORS = {
        "#f0a500",  // 0 — Total (gold)
        "#2a8a7a",  // 1
        "#3db89a",  // 2
        "#16a34a",  // 3
        "#e05555",  // 4
        "#7c3aed",  // 5
        "#0f3d4a",  // 6
        "#f59e0b",  // 7
    };

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMM yy", Locale.ENGLISH);

    private final DataStore ds = DataStore.getInstance();
    private final CashFlowProjectionService projService = new CashFlowProjectionService();

    private final ScrollPane view;
    private ComboBox<String>           periodPicker;
    private LineChart<String, Number>  chart;
    private HBox                       legendBox;
    private Label                      summaryStrip;
    private Label                      warningBar;
    private Label                      statBalance;
    private Label                      statIncome;
    private Label                      statExpense;
    private Label                      statNet;
    private TableView<ForecastTableRow> forecastTable;
    private boolean                    refreshing = false;

    public CashFlowForecastTab() {
        view = buildView();
        refresh();
    }

    public Node getView() { return view; }

    // ── Layout ────────────────────────────────────────────────────────────────

    private ScrollPane buildView() {
        VBox root = new VBox(16);
        root.setPadding(new Insets(24));

        // Filter row
        periodPicker = new ComboBox<>();
        periodPicker.setPrefWidth(210);
        populatePeriodPicker();
        periodPicker.setValue("Next 12 Months");
        periodPicker.valueProperty().addListener((obs, o, n) -> { if (n != null && !refreshing) refresh(); });

        Label periodLabel = new Label("Time Period:");
        periodLabel.getStyleClass().add("form-label");
        HBox filterRow = new HBox(10, periodLabel, periodPicker);
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
        summaryStrip.getStyleClass().add("cash-flow-summary-strip");
        summaryStrip.setMaxWidth(Double.MAX_VALUE);

        // Chart
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis   yAxis = buildYAxis();
        chart = new LineChart<>(xAxis, yAxis);
        chart.getStyleClass().add("cash-flow-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(420);

        legendBox = new HBox(16);
        legendBox.setPadding(new Insets(4, 12, 8, 12));
        legendBox.setAlignment(Pos.CENTER_LEFT);

        VBox chartCard = new VBox(4, chart, legendBox);
        chartCard.getStyleClass().add("card-wrapper");

        // Stat card value labels
        statBalance = new Label();
        statBalance.getStyleClass().add("cash-flow-stat-value");
        statIncome = new Label();
        statIncome.getStyleClass().addAll("cash-flow-stat-value", "cash-flow-stat-value-pos");
        statExpense = new Label();
        statExpense.getStyleClass().addAll("cash-flow-stat-value", "cash-flow-stat-value-neg");
        statNet = new Label();
        statNet.getStyleClass().add("cash-flow-stat-value");

        HBox statsRow = new HBox(12,
                buildStatCard("Projected Balance",        statBalance),
                buildStatCard("Total Projected Income",   statIncome),
                buildStatCard("Total Projected Expenses", statExpense),
                buildStatCard("Net Cash Flow",            statNet));
        for (Node n : statsRow.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // Table view
        forecastTable = new TableView<>();
        forecastTable.getStyleClass().add("forecast-table");
        forecastTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ForecastTableRow, String> monthCol = new TableColumn<>("Month");
        monthCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().month()));
        monthCol.setPrefWidth(120);

        TableColumn<ForecastTableRow, String> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().category()));
        categoryCol.setPrefWidth(120);

        TableColumn<ForecastTableRow, String> subCategoryCol = new TableColumn<>("Sub-Category");
        subCategoryCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().subCategory()));
        subCategoryCol.setPrefWidth(120);

        TableColumn<ForecastTableRow, String> amountCol = new TableColumn<>("Amount");
        amountCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().amount()));
        amountCol.setPrefWidth(120);

        TableColumn<ForecastTableRow, String> methodCol = new TableColumn<>("Method");
        methodCol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().method()));
        methodCol.setPrefWidth(120);

        forecastTable.getColumns().addAll(monthCol, categoryCol, subCategoryCol, amountCol, methodCol);

        // Add all to root
        root.getChildren().addAll(filterRow, warningBar, summaryStrip, chartCard, statsRow, forecastTable);

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setFitToHeight(false);
        sp.getStyleClass().add("edge-to-edge");
        return sp;
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

    private VBox buildStatCard(String label, Label valueLabel) {
        Label lbl = new Label(label.toUpperCase());
        lbl.getStyleClass().add("cash-flow-stat-label");
        VBox card = new VBox(4, lbl, valueLabel);
        card.getStyleClass().add("cash-flow-stat-card");
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private Node buildLegendEntry(String name, String hexColor) {
        Rectangle swatch = new Rectangle(24, 10);
        // Inline required: colour is runtime data — dynamically assigned from SERIES_COLORS array
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
                projService.compute(startDate, endDate);

        // Summary strip
        summaryStrip.setText("Cash flow forecast: " + selected
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

        // Rebuild chart series
        chart.getData().clear();

        // Series 0: Total (always added first so CSS .series0 applies)
        XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
        totalSeries.setName("Total");
        for (CashFlowProjectionService.ProjectionPoint p : result.totalSeries()) {
            totalSeries.getData().add(
                    new XYChart.Data<>(p.date().format(MONTH_FMT), p.balancePaise() / 100.0));
        }
        chart.getData().add(totalSeries);

        // Series 1..N: individual accounts
        List<Account> accounts = result.accounts();
        for (Account acc : accounts) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(acc.getName());
            List<CashFlowProjectionService.ProjectionPoint> pts =
                    result.accountSeries().get(acc.getId());
            if (pts != null) {
                for (CashFlowProjectionService.ProjectionPoint p : pts) {
                    series.getData().add(
                            new XYChart.Data<>(p.date().format(MONTH_FMT), p.balancePaise() / 100.0));
                }
            }
            chart.getData().add(series);
        }

        // Rebuild legend
        legendBox.getChildren().clear();
        legendBox.getChildren().add(buildLegendEntry("Total of Accounts", SERIES_COLORS[0]));
        for (int i = 0; i < accounts.size(); i++) {
            String color = SERIES_COLORS[(i + 1) % SERIES_COLORS.length];
            legendBox.getChildren().add(buildLegendEntry(accounts.get(i).getName(), color));
        }

        // Stat cards
        long lastTotal = result.totalSeries().isEmpty() ? 0
                : result.totalSeries().get(result.totalSeries().size() - 1).balancePaise();
        long income  = result.totalProjectedIncomePaise();
        long expense = result.totalProjectedExpensesPaise();
        long net     = income - expense;

        statBalance.setText(formatPaise(lastTotal));
        applyPosNeg(statBalance, lastTotal);

        statIncome.setText(formatPaise(income));
        statExpense.setText(formatPaise(expense));

        statNet.setText(formatPaise(net));
        applyPosNeg(statNet, net);

        // Update table
        updateForecastTable(result.forecastedExpenses());
    }

    private void updateForecastTable(List<CashFlowProjectionService.ForecastedExpense> forecasts) {
        forecastTable.getItems().clear();
        for (CashFlowProjectionService.ForecastedExpense fe : forecasts) {
            String month = fe.month().format(MONTH_FMT);
            String category = getCategoryName(fe.categoryId());
            String subCategory = getCategoryName(fe.subCategoryId());
            String amount = formatPaise(fe.amountPaise());
            String method = fe.method();
            forecastTable.getItems().add(new ForecastTableRow(month, category, subCategory, amount, method));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void populatePeriodPicker() {
        boolean isIndianFY = "Indian Financial Year".equals(ds.getYearFormat());
        String  fyLabel    = isIndianFY ? "This Financial Year" : "This Calendar Year";
        String  current    = periodPicker.getValue();
        periodPicker.getItems().setAll(
                "Next 6 Months", "Next 12 Months", "Next 24 Months", fyLabel);
        if (current != null && periodPicker.getItems().contains(current)) {
            periodPicker.setValue(current);
        } else if (current != null) {
            // FY/CY label changed — keep the intent but update the label
            boolean wasFyCy = current.startsWith("This ");
            periodPicker.setValue(wasFyCy ? fyLabel : "Next 12 Months");
        }
    }

    private LocalDate[] computePeriodRange(String selected, LocalDate today) {
        return switch (selected) {
            case "Next 6 Months"  -> new LocalDate[]{ today, today.plusMonths(6) };
            case "Next 24 Months" -> new LocalDate[]{ today, today.plusMonths(24) };
            case "This Financial Year" -> {
                int fyStartYear = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
                yield new LocalDate[]{ today, LocalDate.of(fyStartYear + 1, 3, 31) };
            }
            case "This Calendar Year" ->
                    new LocalDate[]{ today, LocalDate.of(today.getYear(), 12, 31) };
            default -> new LocalDate[]{ today, today.plusMonths(12) };
        };
    }

    private String formatPaise(long paise) {
        long   abs    = Math.abs(paise);
        double absR   = abs / 100.0;
        String formatted;
        if      (absR >= 1_00_00_000) formatted = String.format("%.2fCr", absR / 1_00_00_000.0);
        else if (absR >= 1_00_000)    formatted = String.format("%.2fL",  absR / 1_00_000.0);
        else if (absR >= 1_000)       formatted = String.format("%.1fK",  absR / 1_000.0);
        else                          formatted = String.format("%.0f",    absR);
        return (paise < 0 ? "(₹" : "₹") + formatted + (paise < 0 ? ")" : "");
    }

    private void applyPosNeg(Label lbl, long paise) {
        lbl.getStyleClass().removeAll("cash-flow-stat-value-pos", "cash-flow-stat-value-neg");
        lbl.getStyleClass().add(paise >= 0 ? "cash-flow-stat-value-pos" : "cash-flow-stat-value-neg");
    }

    private String getCategoryName(String categoryId) {
        if (categoryId == null) return "";
        return ds.getCategories().stream()
                .filter(c -> c.getId().equals(categoryId))
                .map(com.sanchay.model.Category::getName)
                .findFirst()
                .orElse("Unknown");
    }
}
