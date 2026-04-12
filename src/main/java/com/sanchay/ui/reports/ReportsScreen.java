package com.sanchay.ui.reports;

import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reports module — two tabs.
 *
 * FY options are derived dynamically from the years present in transaction data.
 * Month and FY pickers are mutually exclusive — selecting one clears the other.
 * Default: month = current month, FY = blank.
 * When FY is selected, all content shows data for the full April–March year.
 */
public class ReportsScreen {

    private StackPane view;
    private Runnable summaryRefresh;
    private CashFlowForecastTab cashFlowTab;
    private ComboBox<String> summaryFyPicker;
    private Label summaryFyLabel;

    public ReportsScreen() { buildView(); }

    public Node getView() { return view; }

    /** Re-queries DataStore and redraws all tabs. Called by MainWindow on every navigation. */
    public void refresh() {
        updateYearPickers();
        if (summaryRefresh != null) summaryRefresh.run();
        if (cashFlowTab    != null) cashFlowTab.refresh();
    }

    private void updateYearPickers() {
        boolean isIndianFY = "Indian Financial Year".equals(DataStore.getInstance().getYearFormat());
        List<String> options = isIndianFY ? buildFYOptions() : buildCYOptions();
        String labelText  = isIndianFY ? "FY" : "YEAR";
        String promptText = isIndianFY ? "Select FY…" : "Select Year…";
        if (summaryFyPicker != null) {
            summaryFyPicker.getItems().setAll(options);
            summaryFyPicker.setPromptText(promptText);
            summaryFyLabel.setText(labelText);
        }
    }

    private void buildView() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab summaryTab = new Tab("Monthly Expense Summary");
        summaryTab.setContent(buildMonthlySummaryTab());

        cashFlowTab = new CashFlowForecastTab();
        Tab forecastTab = new Tab("Cash Flow Forecast");
        forecastTab.setContent(cashFlowTab.getView());
        tabPane.getTabs().addAll(summaryTab, forecastTab);

        VBox panel = new VBox(0);
        panel.getStyleClass().add("main-panel");
        panel.setPadding(new Insets(24, 24, 0, 24));
        Label title = new Label("Reports");
        title.getStyleClass().add("screen-title");
        title.setPadding(new Insets(0, 0, 16, 0));
        panel.getChildren().addAll(title, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        view = new StackPane(panel);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TAB 1 — Monthly Expense Summary
    // ═══════════════════════════════════════════════════════════════════════

    private ScrollPane buildMonthlySummaryTab() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(16, 0, 24, 0));

        // ── Controls ─────────────────────────────────────────────────────────
        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label monthLabel = new Label("MONTH");
        monthLabel.getStyleClass().add("filter-label");
        ComboBox<String> monthPicker = new ComboBox<>();
        monthPicker.getStyleClass().add("filter-field");
        List<LocalDate> summaryMonths = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = 0; i < 12; i++) {
            LocalDate m = now.minusMonths(i);
            summaryMonths.add(m);
            monthPicker.getItems().add(
                    m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear());
        }
        monthPicker.setValue(monthPicker.getItems().get(0));

        summaryFyLabel = new Label("FY");
        summaryFyLabel.getStyleClass().add("filter-label");
        summaryFyPicker = new ComboBox<>();
        summaryFyPicker.getStyleClass().add("filter-field");
        summaryFyPicker.getItems().addAll(buildFYOptions());
        summaryFyPicker.setPromptText("Select FY…");
        summaryFyPicker.setPrefWidth(140);
        ComboBox<String> fyPicker = summaryFyPicker;

        CheckBox showSubCat = new CheckBox("Show sub-categories");
        showSubCat.getStyleClass().add("text-hint");

        Region ctrlSpacer = new Region();
        HBox.setHgrow(ctrlSpacer, Priority.ALWAYS);
        Button downloadBtn = new Button("⬇ Download CSV");
        downloadBtn.getStyleClass().add("btn-gold");

        controls.getChildren().addAll(monthLabel, monthPicker, summaryFyLabel, summaryFyPicker, showSubCat, ctrlSpacer, downloadBtn);
        root.getChildren().add(controls);

        // ── Dynamic content ───────────────────────────────────────────────────
        VBox dynamicContent = new VBox(20);
        root.getChildren().add(dynamicContent);

        // ── Mutual-exclusion + refresh ────────────────────────────────────────
        boolean[] updating = {false};

        summaryRefresh = () -> {
            String fyVal = fyPicker.getValue();
            int mIdx = monthPicker.getSelectionModel().getSelectedIndex();

            LocalDate from, to;
            String label;

            if (fyVal != null && !fyVal.isEmpty()) {
                LocalDate[] r = parseFYRange(fyVal);
                from = r[0]; to = r[1];
                label = fyVal;
            } else if (mIdx >= 0 && mIdx < summaryMonths.size()) {
                LocalDate m = summaryMonths.get(mIdx);
                from = m.withDayOfMonth(1);
                to   = m.withDayOfMonth(m.lengthOfMonth());
                label = m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
            } else {
                return;
            }

            Map<String, Map<String, Long>> data = computeTwoLevelCatData(from, to);
            long total = data.values().stream()
                    .flatMap(inner -> inner.values().stream())
                    .mapToLong(Long::longValue).sum();

            dynamicContent.getChildren().setAll(
                    buildExpByCatSection(data, total, label, showSubCat.isSelected()),
                    buildSummaryExpenseTable(from, to, label)
            );
        };

        fyPicker.setOnAction(e -> {
            if (updating[0]) return;
            String v = fyPicker.getValue();
            if (v != null && !v.isEmpty()) {
                updating[0] = true;
                monthPicker.getSelectionModel().clearSelection();
                updating[0] = false;
            }
            summaryRefresh.run();
        });

        monthPicker.setOnAction(e -> {
            if (updating[0]) return;
            if (monthPicker.getValue() != null) {
                updating[0] = true;
                fyPicker.getSelectionModel().clearSelection();
                updating[0] = false;
            }
            summaryRefresh.run();
        });

        showSubCat.selectedProperty().addListener((obs, o, n) -> summaryRefresh.run());

        // Download button re-derives data from current picker state on demand
        downloadBtn.setOnAction(e -> {
            String fyVal = fyPicker.getValue();
            int mIdx = monthPicker.getSelectionModel().getSelectedIndex();
            LocalDate from, to;
            String label;
            if (fyVal != null && !fyVal.isEmpty()) {
                LocalDate[] r = parseFYRange(fyVal);
                from = r[0]; to = r[1];
                label = fyVal;
            } else if (mIdx >= 0 && mIdx < summaryMonths.size()) {
                LocalDate m = summaryMonths.get(mIdx);
                from = m.withDayOfMonth(1);
                to   = m.withDayOfMonth(m.lengthOfMonth());
                label = m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
            } else return;
            Map<String, Map<String, Long>> data = computeTwoLevelCatData(from, to);
            long total = data.values().stream()
                    .flatMap(inner -> inner.values().stream())
                    .mapToLong(Long::longValue).sum();
            exportExpByCatCsv(data, total, label, showSubCat.isSelected(), downloadBtn);
        });

        summaryRefresh.run();   // initial render

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("scroll-page-bg");
        return sp;
    }

    /**
     * Computes two-level expense data: category → (sub-category or "" for unassigned → total paise).
     * "" key means the transaction had no sub-category assigned.
     */
    private Map<String, Map<String, Long>> computeTwoLevelCatData(LocalDate from, LocalDate to) {
        DataStore ds = DataStore.getInstance();
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        ds.getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE
                          && !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .forEach(t -> {
                    String rCatId    = t.getClassification() != null ? t.getClassification().getCategoryId() : null;
                    String rSubCatId = t.getClassification() != null ? t.getClassification().getSubCategoryId() : null;
                    String cat = ds.getCategoryName(rCatId);
                    if ("—".equals(cat)) cat = "(Uncategorized)";
                    String subCat = rSubCatId != null ? ds.getCategoryName(rSubCatId) : "";
                    result.computeIfAbsent(cat, k -> new LinkedHashMap<>())
                          .merge(subCat, t.getAmountPaise(), Long::sum);
                });
        return result;
    }

    /** Builds the Expenses by Category section in either flat or two-level mode. */
    private VBox buildExpByCatSection(Map<String, Map<String, Long>> data, long grandTotal,
                                      String label, boolean showSubCat) {
        if (showSubCat) {
            return buildTwoLevelCategoryBars(data, grandTotal, label);
        }
        // Flat mode: collapse inner maps to category totals
        Map<String, Long> flat = new LinkedHashMap<>();
        data.forEach((cat, subMap) ->
                flat.put(cat, subMap.values().stream().mapToLong(Long::longValue).sum()));
        return buildCategoryBarChart("Expenses by Category — " + label,
                "No expense transactions for this period.", flat, grandTotal, true);
    }

    /**
     * Two-level bar chart.
     * - Categories with all transactions at category level (no sub-cat) → single flat bar row.
     * - Mixed or fully sub-categorised → bold category header showing total, then indented
     *   sub-category bars. Transactions with no sub-cat appear as "(Unassigned)".
     */
    private VBox buildTwoLevelCategoryBars(Map<String, Map<String, Long>> data,
                                            long grandTotal, String label) {
        VBox section = new VBox(10);
        Label heading = new Label("Expenses by Category — " + label);
        heading.getStyleClass().add("text-section-title");
        Label totalLbl = new Label("Total: " + MoneyFormatter.format(grandTotal));
        totalLbl.getStyleClass().add("section-group-label");

        VBox bars = new VBox(6);
        bars.getStyleClass().add("card");
        bars.setPadding(new Insets(16));

        if (data.isEmpty()) {
            bars.getChildren().add(new Label("No expense transactions for this period."));
        } else {
            int ci = 0;
            // Sort categories by their total descending
            List<Map.Entry<String, Map<String, Long>>> sorted = data.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Map<String, Long>>>comparingLong(
                            e -> e.getValue().values().stream().mapToLong(Long::longValue).sum())
                            .reversed())
                    .collect(Collectors.toList());

            for (Map.Entry<String, Map<String, Long>> catEntry : sorted) {
                String catName      = catEntry.getKey();
                Map<String, Long> subMap = catEntry.getValue();
                long catTotal       = subMap.values().stream().mapToLong(Long::longValue).sum();
                double catPct       = grandTotal > 0 ? catTotal * 100.0 / grandTotal : 0;
                String colour       = UiUtils.CHART_PALETTE[ci++ % UiUtils.CHART_PALETTE.length];
                boolean noSubCats   = subMap.size() == 1 && subMap.containsKey("");

                if (noSubCats) {
                    // Every transaction in this category has no sub-category → flat single row
                    bars.getChildren().add(buildBarRow(catName, catTotal, catPct, colour, 0));
                } else {
                    // Category header: bold label + total, no bar
                    HBox catHeader = new HBox(10);
                    catHeader.setAlignment(Pos.CENTER_LEFT);
                    catHeader.setPadding(new Insets(6, 0, 2, 0));
                    Label catLbl = new Label(catName);
                    catLbl.setMinWidth(160);
                    catLbl.getStyleClass().add("text-section-title");
                    Label catTotalLbl = new Label(
                            String.format("%.1f%%  ", catPct) + MoneyFormatter.formatNoDecimal(catTotal));
                    catTotalLbl.getStyleClass().add("text-hint");
                    catHeader.getChildren().addAll(catLbl, catTotalLbl);
                    bars.getChildren().add(catHeader);

                    // Sub-category bars sorted by amount desc; "" key → "(Unassigned)"
                    subMap.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .forEach(sub -> {
                                String subName = sub.getKey().isEmpty()
                                        ? "(Unassigned)" : sub.getKey();
                                double subPct = grandTotal > 0
                                        ? sub.getValue() * 100.0 / grandTotal : 0;
                                bars.getChildren().add(
                                        buildBarRow(subName, sub.getValue(), subPct, colour, 20));
                            });
                }
            }
        }
        section.getChildren().addAll(heading, totalLbl, bars);
        return section;
    }

    /** Single horizontal bar row. indent=0 for top-level, indent=20 for sub-category rows. */
    private HBox buildBarRow(String name, long amountPaise, double pct, String colour, int indent) {
        HBox barRow = new HBox(10);
        barRow.setAlignment(Pos.CENTER_LEFT);
        if (indent > 0) barRow.setPadding(new Insets(0, 0, 0, indent));
        Label nameLbl = new Label(indent > 0 ? "└ " + name : name);
        nameLbl.setMinWidth(Math.max(140, 160 - indent));
        nameLbl.getStyleClass().add("text-body-muted");
        StackPane bar = new StackPane();
        bar.setMinHeight(18); bar.setMaxHeight(18);
        Rectangle bg   = new Rectangle(300, 10, Color.web("#eef4f5"));
        bg.setArcWidth(4); bg.setArcHeight(4);
        Rectangle fill = new Rectangle(Math.max(4, pct * 3), 10, Color.web(colour));
        fill.setArcWidth(4); fill.setArcHeight(4);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.getChildren().addAll(bg, fill);
        bar.setMinHeight(10); bar.setMaxHeight(10);
        Label pctLbl = new Label(String.format("%.1f%%", pct));
        pctLbl.getStyleClass().add("text-hint");
        pctLbl.setMinWidth(40);
        Label amtLbl = new Label(MoneyFormatter.formatNoDecimal(amountPaise));
        amtLbl.getStyleClass().add("text-form-value");
        amtLbl.setMinWidth(80);
        barRow.getChildren().addAll(nameLbl, bar, pctLbl, amtLbl);
        return barRow;
    }

    /** Exports expense-by-category data as CSV, respecting the current sub-category toggle. */
    private void exportExpByCatCsv(Map<String, Map<String, Long>> data, long grandTotal,
                                    String label, boolean showSubCat, Node owner) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Expense Report");
        fc.setInitialFileName("expenses-" + label.replace(" ", "-").replace("/", "-") + ".csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = fc.showSaveDialog(owner.getScene().getWindow());
        if (file == null) return;

        Comparator<Map.Entry<String, Map<String, Long>>> byCatTotal =
                Comparator.<Map.Entry<String, Map<String, Long>>>comparingLong(
                        e -> e.getValue().values().stream().mapToLong(Long::longValue).sum())
                        .reversed();

        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            if (showSubCat) {
                pw.println("Category,Sub-Category,Amount (INR),Percentage");
                data.entrySet().stream().sorted(byCatTotal).forEach(catEntry ->
                        catEntry.getValue().entrySet().stream()
                                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                                .forEach(sub -> {
                                    double pct = grandTotal > 0
                                            ? sub.getValue() * 100.0 / grandTotal : 0;
                                    pw.printf("%s,%s,%.2f,%.1f%n",
                                            escapeCsv(catEntry.getKey()),
                                            escapeCsv(sub.getKey()),
                                            sub.getValue() / 100.0, pct);
                                }));
            } else {
                pw.println("Category,Amount (INR),Percentage");
                data.entrySet().stream().sorted(byCatTotal).forEach(catEntry -> {
                    long amt = catEntry.getValue().values().stream().mapToLong(Long::longValue).sum();
                    double pct = grandTotal > 0 ? amt * 100.0 / grandTotal : 0;
                    pw.printf("%s,%.2f,%.1f%n", escapeCsv(catEntry.getKey()), amt / 100.0, pct);
                });
            }
        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Failed to save file: " + ex.getMessage())
                    .showAndWait();
        }
    }

    private static String escapeCsv(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    private VBox buildSummaryExpenseTable(LocalDate from, LocalDate to, String label) {
        VBox section = new VBox(10);
        Label heading = new Label("All Expense Transactions — " + label);
        heading.getStyleClass().add("text-section-title");

        List<Transaction> txs = DataStore.getInstance().getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE
                          && !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());

        TableView<Transaction> table = new TableView<>(FXCollections.observableArrayList(txs));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(250);
        TableColumn<Transaction, String> amtCol1 = rCol("Amount", 110, t -> t.getTypedSignedAmountInr(), "cell-amt");
        table.getColumns().addAll(
                rCol("Date",        80,  t -> t.getDate().format(DateTimeFormatter.ofPattern("dd MMM"))),
                rCol("Description", 0,   t -> t.getDescription(), "cell-desc"),
                rCol("Account",     140, t -> DataStore.getInstance().getAccountName(t.getFromAccountId())),
                rCol("Category",    130, t -> DataStore.getInstance().getCategoryName(
                        t.getClassification() != null ? t.getClassification().getCategoryId() : null)),
                amtCol1
        );
        VBox tableCard = new VBox();
        tableCard.getStyleClass().add("table-card");
        tableCard.getChildren().add(table);
        section.getChildren().addAll(heading, tableCard);
        return section;
    }

    // ── Shared flat bar chart builder (used by flat mode of summary tab) ──

    private VBox buildCategoryBarChart(String headingText, String emptyMessage,
                                       Map<String, Long> byCategory, long total,
                                       boolean showTotal) {
        VBox section = new VBox(10);
        Label heading = new Label(headingText);
        heading.getStyleClass().add("text-section-title");
        section.getChildren().add(heading);

        if (showTotal) {
            Label totalLbl = new Label("Total: " + MoneyFormatter.format(total));
            totalLbl.getStyleClass().add("section-group-label");
            section.getChildren().add(totalLbl);
        }

        VBox bars = new VBox(8);
        bars.getStyleClass().add("card");
        bars.setPadding(new Insets(16));
        if (byCategory.isEmpty()) {
            bars.getChildren().add(new Label(emptyMessage));
        } else {
            int ci = 0;
            for (Map.Entry<String, Long> e :
                    byCategory.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .toList()) {
                double pct = total > 0 ? (e.getValue() * 100.0 / total) : 0;
                bars.getChildren().add(
                        buildBarRow(e.getKey(), e.getValue(), pct,
                                UiUtils.CHART_PALETTE[ci++ % UiUtils.CHART_PALETTE.length], 0));
            }
        }
        section.getChildren().add(bars);
        return section;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Derives distinct Indian FY labels from all transaction dates, most recent first. */
    private List<String> buildFYOptions() {
        return DataStore.getInstance().getTransactions().stream()
                .map(t -> {
                    int y = t.getDate().getYear();
                    int m = t.getDate().getMonthValue();
                    int s = (m >= 4) ? y : y - 1;
                    return "FY " + s + "-" + String.valueOf(s + 1).substring(2);
                })
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /** Derives distinct calendar year labels from all transaction dates, most recent first. */
    private List<String> buildCYOptions() {
        return DataStore.getInstance().getTransactions().stream()
                .map(t -> String.valueOf(t.getDate().getYear()))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

    /**
     * Parses a year-range label into a [from, to] date pair.
     * Accepts "FY 2024-25" → { 2024-04-01, 2025-03-31 } (Indian FY)
     * or "2024" → { 2024-01-01, 2024-12-31 } (Calendar Year).
     */
    private LocalDate[] parseFYRange(String label) {
        if (label.startsWith("FY ")) {
            int startYear = Integer.parseInt(label.replace("FY ", "").split("-")[0]);
            return new LocalDate[]{
                    LocalDate.of(startYear, 4, 1),
                    LocalDate.of(startYear + 1, 3, 31)
            };
        } else {
            int year = Integer.parseInt(label);
            return new LocalDate[]{
                    LocalDate.of(year, 1, 1),
                    LocalDate.of(year, 12, 31)
            };
        }
    }

    private TableColumn<Transaction, String> rCol(String title, int width,
            java.util.function.Function<Transaction, String> fn) {
        TableColumn<Transaction, String> col = new TableColumn<>(title.toUpperCase());
        if (width > 0) col.setPrefWidth(width);
        col.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(fn.apply(cd.getValue())));
        return col;
    }

    private TableColumn<Transaction, String> rCol(String title, int width,
            java.util.function.Function<Transaction, String> fn, String cellStyleClass) {
        TableColumn<Transaction, String> col = rCol(title, width, fn);
        col.setCellFactory(tc -> {
            TableCell<Transaction, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item);
                }
            };
            cell.getStyleClass().add(cellStyleClass);
            return cell;
        });
        return col;
    }


}
