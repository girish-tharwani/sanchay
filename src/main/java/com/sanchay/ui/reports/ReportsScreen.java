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
        monthPicker.setValue(monthPicker.getItems().get(0));  // default: current month

        Label fyLabel = new Label("FY:");
        fyLabel.getStyleClass().add("form-label");
        ComboBox<String> fyPicker = new ComboBox<>();
        fyPicker.getItems().addAll(buildFYOptions());
        fyPicker.setPromptText("Select FY…");
        fyPicker.setPrefWidth(140);

        controls.getChildren().addAll(monthLabel, monthPicker, fyLabel, fyPicker);
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
            LocalDate trendAnchor;

            if (fyVal != null && !fyVal.isEmpty()) {
                LocalDate[] r = parseFYRange(fyVal);
                from = r[0]; to = r[1];
                label = fyVal;
                trendAnchor = null;   // no single-month highlight in FY mode
            } else if (mIdx >= 0 && mIdx < summaryMonths.size()) {
                LocalDate m = summaryMonths.get(mIdx);
                from = m.withDayOfMonth(1);
                to   = m.withDayOfMonth(m.lengthOfMonth());
                label = m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
                trendAnchor = m;
            } else {
                return;
            }

            // 12-month trend window: the 12 months that end at 'to'
            List<LocalDate> trendMonths = new ArrayList<>();
            LocalDate cursor = to.withDayOfMonth(1).minusMonths(11);
            for (int i = 0; i < 12; i++) { trendMonths.add(cursor); cursor = cursor.plusMonths(1); }

            dynamicContent.getChildren().setAll(
                    buildSummaryCategoryBars(from, to, label),
                    buildSummaryTrend(trendMonths, trendAnchor),
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

        refresh.run();   // initial render

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.setStyle("-fx-background-color: #F5F6FA; -fx-background: #F5F6FA;");
        return sp;
    }

    private VBox buildSummaryCategoryBars(LocalDate from, LocalDate to, String label) {
        Map<String, Long> byCategory = DataStore.getInstance().getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE
                          && !t.getDate().isBefore(from) && !t.getDate().isAfter(to))
                .collect(Collectors.groupingBy(
                        t -> DataStore.getInstance().getCategoryName(t.getCategoryId()),
                        Collectors.summingLong(Transaction::getAmountPaise)));

        long total = byCategory.values().stream().mapToLong(Long::longValue).sum();
        return buildCategoryBarChart("Expenses by Category — " + label,
                "No expense transactions for this period.", byCategory, total, true);
    }

    /**
     * 12-month trend. trendMonths = the 12 months to display (in order).
     * highlightMonth = the single month to colour blue (null = all bars the same grey-blue).
     */
    private VBox buildSummaryTrend(List<LocalDate> trendMonths, LocalDate highlightMonth) {
        VBox section = new VBox(10);
        Label heading = new Label("12-Month Expense Trend (Bank + Credit Card)");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");

        HBox chart = new HBox(6);
        chart.getStyleClass().add("card");
        chart.setPadding(new Insets(16));
        chart.setAlignment(Pos.BOTTOM_CENTER);

        long maxPaise = 1L;
        List<Long> totals = new ArrayList<>();
        for (LocalDate m : trendMonths) {
            long sum = DataStore.getInstance().getTransactions().stream()
                    .filter(t -> t.getType() == Transaction.Type.EXPENSE
                              && t.getDate().getMonth() == m.getMonth()
                              && t.getDate().getYear()  == m.getYear())
                    .mapToLong(Transaction::getAmountPaise).sum();
            totals.add(sum);
            if (sum > maxPaise) maxPaise = sum;
        }
        for (int i = 0; i < 12; i++) {
            LocalDate m = trendMonths.get(i);
            boolean highlighted = highlightMonth != null
                    && m.getMonth() == highlightMonth.getMonth()
                    && m.getYear()  == highlightMonth.getYear();
            int barH = (int) Math.max(4, ((double) totals.get(i) / maxPaise) * 120);
            VBox col = new VBox(4); col.setAlignment(Pos.BOTTOM_CENTER);
            Rectangle bar = new Rectangle(28, barH, Color.web(highlighted ? "#2E75B6" : "#A8C7E8"));
            bar.setArcWidth(4); bar.setArcHeight(4);
            Label lbl = new Label(m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            lbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #9E9E9E;");
            col.getChildren().addAll(bar, lbl);
            chart.getChildren().add(col);
        }
        section.getChildren().addAll(heading, chart);
        return section;
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

    // ── Shared bar chart builder ────────────────────────────────────────────

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
                String colour = BAR_COLOURS[ci++ % BAR_COLOURS.length];
                HBox barRow = new HBox(10);
                barRow.setAlignment(Pos.CENTER_LEFT);
                Label cat = new Label(e.getKey());
                cat.setMinWidth(160);
                cat.setStyle("-fx-font-size: 12px;");
                StackPane bar = new StackPane();
                bar.setMinHeight(18);
                bar.setMaxHeight(18);
                Rectangle bg = new Rectangle(300, 18, Color.web("#F0F4F8"));
                bg.setArcWidth(6); bg.setArcHeight(6);
                Rectangle fill = new Rectangle(Math.max(4, pct * 3), 18, Color.web(colour));
                fill.setArcWidth(6); fill.setArcHeight(6);
                StackPane.setAlignment(fill, Pos.CENTER_LEFT);
                bar.getChildren().addAll(bg, fill);
                Label pctLbl = new Label(String.format("%.1f%%  ₹%,.0f", pct, e.getValue() / 100.0));
                pctLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #595959;");
                barRow.getChildren().addAll(cat, bar, pctLbl);
                bars.getChildren().add(barRow);
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
