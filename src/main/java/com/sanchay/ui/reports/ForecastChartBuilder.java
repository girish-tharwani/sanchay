package com.sanchay.ui.reports;

import com.sanchay.model.Account;
import com.sanchay.model.BankAccount;
import com.sanchay.model.CreditCardAccount;
import com.sanchay.model.LoanAccount;
import com.sanchay.service.CashFlowProjectionService;
import com.sanchay.service.DataStore;
import com.sanchay.service.ForecastStateService;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds and updates the cash-flow forecast {@link LineChart} and its custom legend.
 * Owns the chart and legend nodes; callers retrieve them via {@link #getChartCard()}.
 */
class ForecastChartBuilder {

    private static final DateTimeFormatter MONTH_FMT =
            DateTimeFormatter.ofPattern("MMM yy", java.util.Locale.ENGLISH);

    private final DataStore            ds = DataStore.getInstance();
    private final ForecastStateService forecastState;
    private final LineChart<String, Number> chart;
    private final FlowPane legendPane;

    ForecastChartBuilder(ForecastStateService forecastState) {
        this.forecastState = forecastState;

        NumberAxis yAxis = buildYAxis();
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setGapStartAndEnd(false);
        chart = new LineChart<>(xAxis, yAxis);
        chart.getStyleClass().add("cash-flow-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(false);
        chart.setPrefHeight(420);

        legendPane = new FlowPane(8, 8);
        legendPane.setPadding(new Insets(4, 12, 8, 12));
    }

    Node getChartCard() {
        VBox card = new VBox(4, chart, legendPane);
        card.getStyleClass().add("card-wrapper");
        return card;
    }

    LineChart<String, Number> getChart() { return chart; }

    void rebuild(CashFlowProjectionService.ProjectionResult result,
                 boolean showDetailedAccounts,
                 LocalDate forecastStartDate) {
        chart.getData().clear();
        legendPane.getChildren().clear();

        boolean showSum = !showDetailedAccounts || forecastState.isShowSum();
        if (showSum) {
            XYChart.Series<String, Number> totalSeries = new XYChart.Series<>();
            totalSeries.setName("Total");
            for (CashFlowProjectionService.ProjectionPoint p : withTotalOpeningPoint(result, forecastStartDate)) {
                totalSeries.getData().add(
                        new XYChart.Data<>(p.date().format(MONTH_FMT), p.balancePaise() / 100.0));
            }
            chart.getData().add(totalSeries);
            legendPane.getChildren().add(buildLegendEntry("Total of Accounts", UiUtils.FORECAST_SERIES_COLORS[0]));
        }

        List<Account> accounts = result.accounts();
        boolean grouped = accounts.size() > 5 && !showDetailedAccounts;

        if (grouped) {
            buildGroupedSeries(accounts, result.accountSeries(), forecastStartDate);
        } else {
            buildDetailedSeries(accounts, result.accountSeries(), forecastStartDate);
        }

        applyDataPointTooltips();
        applySeriesColors(showSum);
    }

    private void buildDetailedSeries(List<Account> accounts,
                                     Map<String, List<CashFlowProjectionService.ProjectionPoint>> accountSeries,
                                     LocalDate forecastStartDate) {
        Set<String> selection = forecastState.getAccountSelection();
        List<Account> toShow;
        if (selection == null || selection.isEmpty()) {
            toShow = accounts;
        } else {
            toShow = accounts.stream()
                    .filter(a -> selection.contains(a.getId()))
                    .collect(Collectors.toList());
            if (toShow.isEmpty()) toShow = accounts;
        }

        for (int i = 0; i < toShow.size(); i++) {
            Account acc = toShow.get(i);
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(acc.getName());
            List<CashFlowProjectionService.ProjectionPoint> pts =
                    withOpeningPoint(accountSeries.get(acc.getId()), acc, forecastStartDate);
            for (CashFlowProjectionService.ProjectionPoint p : pts) {
                series.getData().add(
                        new XYChart.Data<>(p.date().format(MONTH_FMT), p.balancePaise() / 100.0));
            }
            chart.getData().add(series);
            String color = UiUtils.FORECAST_SERIES_COLORS[(i + 1) % UiUtils.FORECAST_SERIES_COLORS.length];
            legendPane.getChildren().add(buildLegendEntry(acc.getName(), color));
        }
    }

    private void buildGroupedSeries(List<Account> accounts,
                                    Map<String, List<CashFlowProjectionService.ProjectionPoint>> accountSeries,
                                    LocalDate forecastStartDate) {
        Map<String, List<String>> groupToIds = new LinkedHashMap<>();
        groupToIds.put("Bank Accounts", new ArrayList<>());
        groupToIds.put("Credit Cards",  new ArrayList<>());
        groupToIds.put("Investments",   new ArrayList<>());
        groupToIds.put("Loans",         new ArrayList<>());

        for (Account acc : accounts) {
            if      (acc instanceof BankAccount)       groupToIds.get("Bank Accounts").add(acc.getId());
            else if (acc instanceof CreditCardAccount) groupToIds.get("Credit Cards").add(acc.getId());
            else if (acc instanceof LoanAccount)       groupToIds.get("Loans").add(acc.getId());
            else                                       groupToIds.get("Investments").add(acc.getId());
        }

        List<String> dateLabels = accounts.stream()
                .map(acc -> withOpeningPoint(accountSeries.get(acc.getId()), acc, forecastStartDate))
                .filter(pts -> !pts.isEmpty())
                .findFirst()
                .map(pts -> pts.stream()
                        .map(p -> p.date().format(MONTH_FMT))
                        .collect(Collectors.toList()))
                .orElse(List.of());

        int n = dateLabels.size();
        int seriesIdx = 1;

        for (Map.Entry<String, List<String>> entry : groupToIds.entrySet()) {
            String groupName = entry.getKey();
            List<String> ids = entry.getValue();
            if (ids.isEmpty()) continue;

            long[] sums = new long[n];
            for (String id : ids) {
                Account acc = accounts.stream()
                        .filter(a -> a.getId().equals(id))
                        .findFirst()
                        .orElse(null);
                if (acc == null) continue;
                List<CashFlowProjectionService.ProjectionPoint> pts =
                        withOpeningPoint(accountSeries.get(id), acc, forecastStartDate);
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

    private List<CashFlowProjectionService.ProjectionPoint> withTotalOpeningPoint(
            CashFlowProjectionService.ProjectionResult result,
            LocalDate forecastStartDate) {
        LocalDate openingDate = openingPointDate(forecastStartDate);
        long openingTotal = result.accounts().stream()
                .mapToLong(acc -> ds.getForecastStartingBalancePaise(acc, forecastStartDate))
                .sum();

        List<CashFlowProjectionService.ProjectionPoint> points =
                new ArrayList<>(result.totalSeries().size() + 1);
        points.add(new CashFlowProjectionService.ProjectionPoint(openingDate, openingTotal));
        points.addAll(result.totalSeries());
        return points;
    }

    private List<CashFlowProjectionService.ProjectionPoint> withOpeningPoint(
            List<CashFlowProjectionService.ProjectionPoint> points,
            Account account,
            LocalDate forecastStartDate) {
        List<CashFlowProjectionService.ProjectionPoint> chartPoints =
                new ArrayList<>((points == null ? 0 : points.size()) + 1);
        chartPoints.add(new CashFlowProjectionService.ProjectionPoint(
                openingPointDate(forecastStartDate),
                ds.getForecastStartingBalancePaise(account, forecastStartDate)));
        if (points != null) chartPoints.addAll(points);
        return chartPoints;
    }

    private LocalDate openingPointDate(LocalDate forecastStartDate) {
        return YearMonth.from(forecastStartDate).minusMonths(1).atEndOfMonth();
    }

    private void applySeriesColors(boolean showSum) {
        // Intentional deviation from the usual CSS-first styling approach:
        // this chart also renders a custom Java-built legend, and keeping the
        // forecast palette in Java gives the legend and chart lines one source
        // of truth instead of duplicating series colours in CSS and code.
        Platform.runLater(() -> {
            for (int i = 0; i < chart.getData().size(); i++) {
                int colorIdx  = showSum ? i : i + 1;
                String color  = UiUtils.FORECAST_SERIES_COLORS[colorIdx % UiUtils.FORECAST_SERIES_COLORS.length];
                boolean isTotal = showSum && i == 0;
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
                    attachTooltip(data.getNode(), text);
                } else {
                    data.nodeProperty().addListener((obs, oldNode, newNode) -> {
                        if (newNode != null) attachTooltip(newNode, text);
                    });
                }
            }
        }
    }

    private void attachTooltip(Node node, String text) {
        Tooltip tt = new Tooltip(text);
        tt.setShowDelay(javafx.util.Duration.millis(80));
        tt.setHideDelay(javafx.util.Duration.millis(100));
        tt.getStyleClass().add("cash-flow-line-tooltip");
        Tooltip.install(node, tt);
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
}
