package com.sanchay.ui.reports;

import com.sanchay.model.CreditCardAccount;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
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

    public ReportsScreen() { buildView(); }

    public Node getView() { return view; }

    private void buildView() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab summaryTab = new Tab("Monthly Expense Summary");
        summaryTab.setContent(buildMonthlySummaryTab());

        Tab ccTab = new Tab("Credit Card Report");
        ccTab.setContent(buildCreditCardTab());

        tabPane.getTabs().addAll(summaryTab, ccTab);

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

        Label monthLabel = new Label("Month:");
        monthLabel.getStyleClass().add("form-label");
        ComboBox<String> monthPicker = new ComboBox<>();
        List<LocalDate> summaryMonths = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = 0; i < 12; i++) {
            LocalDate m = now.minusMonths(i);
            summaryMonths.add(m);
            monthPicker.getItems().add(
                    m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear());
        }
        monthPicker.setValue(monthPicker.getItems().get(0));

        Label fyLabel = new Label("FY:");
        fyLabel.getStyleClass().add("form-label");
        ComboBox<String> fyPicker = new ComboBox<>();
        fyPicker.getItems().addAll(buildFYOptions());
        fyPicker.setPromptText("Select FY…");
        fyPicker.setPrefWidth(140);

        CheckBox showSubCat = new CheckBox("Show sub-categories");
        showSubCat.setStyle("-fx-text-fill: #595959; -fx-font-size: 12px;");

        Button downloadBtn = new Button("⬇ Download CSV");
        downloadBtn.getStyleClass().add("btn-secondary");

        controls.getChildren().addAll(monthLabel, monthPicker, fyLabel, fyPicker, showSubCat, downloadBtn);
        root.getChildren().add(controls);

        // ── Dynamic content ───────────────────────────────────────────────────
        VBox dynamicContent = new VBox(20);
        root.getChildren().add(dynamicContent);

        // ── Mutual-exclusion + refresh ────────────────────────────────────────
        boolean[] updating = {false};

        Runnable refresh = () -> {
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
            refresh.run();
        });

        monthPicker.setOnAction(e -> {
            if (updating[0]) return;
            if (monthPicker.getValue() != null) {
                updating[0] = true;
                fyPicker.getSelectionModel().clearSelection();
                updating[0] = false;
            }
            refresh.run();
        });

        showSubCat.selectedProperty().addListener((obs, o, n) -> refresh.run());

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

        refresh.run();   // initial render

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #F5F6FA; -fx-background: #F5F6FA;");
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
                    String cat = ds.getCategoryName(t.getCategoryId());
                    if ("—".equals(cat)) cat = "(Uncategorized)";
                    String subCat = t.getSubCategoryId() != null
                            ? ds.getCategoryName(t.getSubCategoryId()) : "";
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
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");
        Label totalLbl = new Label("Total: " + String.format("₹%,.2f", grandTotal / 100.0));
        totalLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #595959;");

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
                String colour       = BAR_COLOURS[ci++ % BAR_COLOURS.length];
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
                    catLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1A1A2E;");
                    Label catTotalLbl = new Label(
                            String.format("%.1f%%  ₹%,.0f", catPct, catTotal / 100.0));
                    catTotalLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #595959;");
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
        nameLbl.setStyle("-fx-font-size: 12px;");
        StackPane bar = new StackPane();
        bar.setMinHeight(18); bar.setMaxHeight(18);
        Rectangle bg   = new Rectangle(300, 18, Color.web("#F0F4F8"));
        bg.setArcWidth(6); bg.setArcHeight(6);
        Rectangle fill = new Rectangle(Math.max(4, pct * 3), 18, Color.web(colour));
        fill.setArcWidth(6); fill.setArcHeight(6);
        StackPane.setAlignment(fill, Pos.CENTER_LEFT);
        bar.getChildren().addAll(bg, fill);
        Label pctLbl = new Label(String.format("%.1f%%  ₹%,.0f", pct, amountPaise / 100.0));
        pctLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #595959;");
        barRow.getChildren().addAll(nameLbl, bar, pctLbl);
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
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");

        List<Transaction> txs = DataStore.getInstance().getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE
                          && !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());

        TableView<Transaction> table = new TableView<>(FXCollections.observableArrayList(txs));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(250);
        TableColumn<Transaction, String> amtCol1 = rCol("Amount", 110, t -> t.getTypedSignedAmountInr());
        table.getColumns().addAll(
                rCol("Date",        80,  t -> t.getDate().format(DateTimeFormatter.ofPattern("dd MMM"))),
                rCol("Description", 0,   t -> t.getDescription()),
                rCol("Account",     140, t -> DataStore.getInstance().getAccountName(t.getFromAccountId())),
                rCol("Category",    130, t -> DataStore.getInstance().getCategoryName(t.getCategoryId())),
                amtCol1
        );
        section.getChildren().addAll(heading, table);
        return section;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // TAB 2 — Credit Card Report
    // ═══════════════════════════════════════════════════════════════════════

    private ScrollPane buildCreditCardTab() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(16, 0, 24, 0));

        DataStore ds = DataStore.getInstance();
        List<CreditCardAccount> cards = ds.getCreditCardAccounts();
        LocalDate now = LocalDate.now();

        // ── Controls ─────────────────────────────────────────────────────────
        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label cardLabel = new Label("Card:");
        cardLabel.getStyleClass().add("form-label");
        ComboBox<String> cardPicker = new ComboBox<>();
        cardPicker.getItems().add("All Cards");
        cards.forEach(cc -> cardPicker.getItems().add(cc.getName()));
        cardPicker.setValue("All Cards");

        Label monthLabel = new Label("Month:");
        monthLabel.getStyleClass().add("form-label");
        ComboBox<String> monthPicker = new ComboBox<>();
        List<LocalDate> ccMonths = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate m = now.minusMonths(i);
            ccMonths.add(m);
            monthPicker.getItems().add(
                    m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear());
        }
        monthPicker.setValue(monthPicker.getItems().get(0));

        Label fyLabel = new Label("FY:");
        fyLabel.getStyleClass().add("form-label");
        ComboBox<String> fyPicker = new ComboBox<>();
        fyPicker.getItems().addAll(buildFYOptions());
        fyPicker.setPromptText("Select FY…");
        fyPicker.setPrefWidth(140);

        controls.getChildren().addAll(cardLabel, cardPicker, monthLabel, monthPicker, fyLabel, fyPicker);
        root.getChildren().add(controls);

        // ── Dynamic content ───────────────────────────────────────────────────
        VBox dynamicContent = new VBox(20);
        root.getChildren().add(dynamicContent);

        // ── Mutual-exclusion + refresh ────────────────────────────────────────
        boolean[] updating = {false};

        Runnable refresh = () -> {
            String fyVal = fyPicker.getValue();
            int mIdx = monthPicker.getSelectionModel().getSelectedIndex();

            LocalDate from, to;
            String label;

            if (fyVal != null && !fyVal.isEmpty()) {
                LocalDate[] r = parseFYRange(fyVal);
                from = r[0]; to = r[1];
                label = fyVal;
            } else if (mIdx >= 0 && mIdx < ccMonths.size()) {
                LocalDate m = ccMonths.get(mIdx);
                from = m.withDayOfMonth(1);
                to   = m.withDayOfMonth(m.lengthOfMonth());
                label = m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
            } else {
                return;
            }

            String selectedCard = cardPicker.getValue();
            List<CreditCardAccount> filtered = "All Cards".equals(selectedCard)
                    ? cards
                    : cards.stream().filter(cc -> cc.getName().equals(selectedCard))
                           .collect(Collectors.toList());

            dynamicContent.getChildren().setAll(
                    buildCCSummaryCards(filtered, from, to, label, ds),
                    buildCCCategoryBars(filtered, from, to, label, ds),
                    buildCCTransactionTable(filtered, from, to, label, ds)
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
            refresh.run();
        });

        monthPicker.setOnAction(e -> {
            if (updating[0]) return;
            if (monthPicker.getValue() != null) {
                updating[0] = true;
                fyPicker.getSelectionModel().clearSelection();
                updating[0] = false;
            }
            refresh.run();
        });

        cardPicker.setOnAction(e -> refresh.run());
        refresh.run();

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #F5F6FA; -fx-background: #F5F6FA;");
        return sp;
    }

    private HBox buildCCSummaryCards(List<CreditCardAccount> cards, LocalDate from, LocalDate to,
                                     String periodLabel, DataStore ds) {
        HBox cardRow = new HBox(14);
        for (CreditCardAccount cc : cards) {
            long outstanding = ds.getCreditCardOutstandingPaise(cc.getId());
            long available   = Math.min(cc.getCreditLimitPaise(), cc.getCreditLimitPaise() - outstanding);
            long periodSpend = ds.getTransactions().stream()
                    .filter(t -> t.getType() == Transaction.Type.EXPENSE
                              && cc.getId().equals(t.getFromAccountId())
                              && !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                    .mapToLong(Transaction::getAmountPaise).sum();
            VBox card = new VBox(8); card.getStyleClass().add("card");
            Label name = new Label(cc.getName());
            name.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
            HBox stats = new HBox(16);
            stats.getChildren().addAll(
                    ccStat(periodLabel,   "₹" + fmt(periodSpend),  "#E74C3C"),
                    ccStat("Outstanding", "₹" + fmt(outstanding),  "#E74C3C"),
                    ccStat("Available",   "₹" + fmt(available),    "#27AE60"),
                    ccStat("Issuer",      cc.getBankIssuer(),       "#595959")
            );
            card.getChildren().addAll(name, stats);
            HBox.setHgrow(card, Priority.ALWAYS);
            cardRow.getChildren().add(card);
        }
        return cardRow;
    }

    private VBox buildCCCategoryBars(List<CreditCardAccount> cards, LocalDate from, LocalDate to,
                                     String label, DataStore ds) {
        Set<String> cardIds = cards.stream().map(CreditCardAccount::getId).collect(Collectors.toSet());
        Map<String, Long> byCategory = ds.getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE
                          && cardIds.contains(t.getFromAccountId())
                          && !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .collect(Collectors.groupingBy(
                        t -> ds.getCategoryName(t.getCategoryId()),
                        Collectors.summingLong(Transaction::getAmountPaise)));

        long total = byCategory.values().stream().mapToLong(Long::longValue).sum();
        return buildCategoryBarChart("CC Spending by Category — " + label,
                "No CC expenses for this period.", byCategory, total, false);
    }

    private VBox buildCCTransactionTable(List<CreditCardAccount> cards, LocalDate from, LocalDate to,
                                         String label, DataStore ds) {
        VBox section = new VBox(10);
        Label heading = new Label("Credit Card Transactions — " + label);
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");

        Set<String> cardIds = cards.stream().map(CreditCardAccount::getId).collect(Collectors.toSet());
        List<Transaction> txs = ds.getTransactions().stream()
                .filter(t -> (t.getType() == Transaction.Type.EXPENSE || t.getType() == Transaction.Type.CC_PAYMENT)
                          && (cardIds.contains(t.getFromAccountId()) || cardIds.contains(t.getToAccountId()))
                          && !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());

        TableView<Transaction> table = new TableView<>(FXCollections.observableArrayList(txs));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(220);
        TableColumn<Transaction, String> amtCol2 = rCol("Amount", 110, t -> t.getTypedSignedAmountInr());
        table.getColumns().addAll(
                rCol("Date",        90,  t -> t.getDate().format(DateTimeFormatter.ofPattern("dd MMM"))),
                rCol("Description", 0,   t -> t.getDescription()),
                rCol("Card",        140, t -> ds.getAccountName(
                        t.getType() == Transaction.Type.CC_PAYMENT ? t.getToAccountId() : t.getFromAccountId())),
                rCol("Category",    130, t -> ds.getCategoryName(t.getCategoryId())),
                amtCol2
        );
        section.getChildren().addAll(heading, table);
        return section;
    }

    // ── Shared flat bar chart builder (used by CC tab and flat mode of summary tab) ──

    private static final String[] BAR_COLOURS = {
            "#E74C3C","#E67E22","#F39C12","#8E44AD","#2E75B6",
            "#1ABC9C","#27AE60","#2980B9","#C0392B","#16A085"};

    private VBox buildCategoryBarChart(String headingText, String emptyMessage,
                                       Map<String, Long> byCategory, long total,
                                       boolean showTotal) {
        VBox section = new VBox(10);
        Label heading = new Label(headingText);
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");
        section.getChildren().add(heading);

        if (showTotal) {
            Label totalLbl = new Label("Total: " + String.format("₹%,.2f", total / 100.0));
            totalLbl.setStyle("-fx-font-size: 13px; -fx-text-fill: #595959;");
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
                                BAR_COLOURS[ci++ % BAR_COLOURS.length], 0));
            }
        }
        section.getChildren().add(bars);
        return section;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Derives distinct FY labels from all transaction dates, most recent first. */
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

    /** Parses "FY 2024-25" → { 2024-04-01, 2025-03-31 }. */
    private LocalDate[] parseFYRange(String fyLabel) {
        int startYear = Integer.parseInt(fyLabel.replace("FY ", "").split("-")[0]);
        return new LocalDate[]{
                LocalDate.of(startYear, 4, 1),
                LocalDate.of(startYear + 1, 3, 31)
        };
    }

    private TableColumn<Transaction, String> rCol(String title, int width,
            java.util.function.Function<Transaction, String> fn) {
        TableColumn<Transaction, String> col = new TableColumn<>(title);
        if (width > 0) col.setPrefWidth(width);
        col.setCellValueFactory(cd ->
                new javafx.beans.property.SimpleStringProperty(fn.apply(cd.getValue())));
        return col;
    }

    private VBox ccStat(String label, String value, String color) {
        VBox box = new VBox(2);
        Label lbl = new Label(label); lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #9E9E9E;");
        Label val = new Label(value); val.setStyle(
                "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        box.getChildren().addAll(lbl, val);
        return box;
    }

    private String fmt(long paise) { return String.format("%,.0f", paise / 100.0); }
}
