package com.sanchay.ui.reports;

import com.sanchay.model.Category;
import com.sanchay.model.Transaction;
import javafx.scene.control.CustomMenuItem;
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

class ExpenseReportTab {

    private final ScrollPane view;
    private Runnable refresh;
    private ComboBox<String> fyPicker;
    private Label fyLabel;
    private MenuButton catMenu;
    /** Category names the user has explicitly hidden. Empty = show all. */
    private final Set<String> hiddenCategories = new HashSet<>();

    ExpenseReportTab() {
        view = buildView();
    }

    Node getView() { return view; }

    void refresh() {
        updateYearPicker();
        rebuildCategoryMenu();
        if (refresh != null) refresh.run();
    }

    void updateYearPicker() {
        boolean isIndianFY = "Indian Financial Year".equals(DataStore.getInstance().getYearFormat());
        List<String> options = isIndianFY ? buildFYOptions() : buildCYOptions();
        if (fyPicker != null) {
            String prev = fyPicker.getValue();
            fyPicker.getItems().setAll(options);
            fyPicker.setPromptText(isIndianFY ? "Select FY…" : "Select Year…");
            fyLabel.setText(isIndianFY ? "FY" : "YEAR");
            if (prev != null && fyPicker.getItems().contains(prev))
                fyPicker.setValue(prev);
        }
    }

    // ── Category menu ─────────────────────────────────────────────────────────

    private void rebuildCategoryMenu() {
        if (catMenu == null) return;
        List<Category> cats = DataStore.getInstance().getExpenseCategories();
        catMenu.getItems().clear();
        for (Category cat : cats) {
            CheckBox cb = new CheckBox(cat.getName());
            cb.setSelected(!hiddenCategories.contains(cat.getName()));
            cb.getStyleClass().add("acsel-account-cb");
            cb.setOnAction(e -> {
                if (cb.isSelected()) hiddenCategories.remove(cat.getName());
                else hiddenCategories.add(cat.getName());
                syncCatMenuLabel();
                if (refresh != null) refresh.run();
            });
            // hideOnClick=false keeps the menu open after each toggle
            CustomMenuItem item = new CustomMenuItem(cb, false);
            catMenu.getItems().add(item);
        }
        syncCatMenuLabel();
    }

    private void syncCatMenuLabel() {
        long total   = catMenu.getItems().size();
        long checked = catMenu.getItems().stream()
                .filter(i -> i instanceof CustomMenuItem ci
                          && ci.getContent() instanceof CheckBox cb
                          && cb.isSelected())
                .count();
        catMenu.setText(checked == total ? "All Categories"
                : checked + " of " + total + " categories");
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    private ScrollPane buildView() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(16, 0, 24, 0));

        // ── Controls ──────────────────────────────────────────────────────────
        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label monthLabel = new Label("MONTH");
        monthLabel.getStyleClass().add("filter-label");
        ComboBox<String> monthPicker = new ComboBox<>();
        monthPicker.getStyleClass().add("filter-field");
        List<LocalDate> months = new ArrayList<>();
        LocalDate now = LocalDate.now();
        for (int i = 0; i < 12; i++) {
            LocalDate m = now.minusMonths(i);
            months.add(m);
            monthPicker.getItems().add(
                    m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear());
        }
        // No default month — FY is the default filter

        fyLabel = new Label("FY");
        fyLabel.getStyleClass().add("filter-label");
        fyPicker = new ComboBox<>();
        fyPicker.getStyleClass().add("filter-field");
        fyPicker.getItems().addAll(buildFYOptions());
        fyPicker.setPromptText("Select FY…");
        fyPicker.setPrefWidth(140);
        // Default: current FY
        String defaultFY = currentFYLabel();
        if (fyPicker.getItems().contains(defaultFY))      fyPicker.setValue(defaultFY);
        else if (!fyPicker.getItems().isEmpty())           fyPicker.setValue(fyPicker.getItems().get(0));

        catMenu = new MenuButton("All Categories");
        catMenu.getStyleClass().add("filter-field");
        catMenu.setPrefWidth(160);

        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().add("btn-secondary");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button downloadBtn = new Button("⬇ Download CSV");
        downloadBtn.getStyleClass().add("btn-gold");

        controls.getChildren().addAll(
                fyLabel, fyPicker, monthLabel, monthPicker, catMenu, clearBtn, spacer, downloadBtn);
        root.getChildren().add(controls);

        // ── Show sub-categories checkbox — lives above the transaction table ──
        CheckBox showSubCat = new CheckBox("Show sub-categories");
        showSubCat.getStyleClass().add("text-hint");

        // ── Dynamic content ───────────────────────────────────────────────────
        VBox dynamicContent = new VBox(20);
        root.getChildren().add(dynamicContent);

        // ── Mutual-exclusion + refresh ────────────────────────────────────────
        boolean[] updating = {false};

        refresh = () -> {
            String fyVal = fyPicker.getValue();
            int mIdx = monthPicker.getSelectionModel().getSelectedIndex();

            LocalDate from, to;
            String label;

            if (fyVal != null && !fyVal.isEmpty()) {
                LocalDate[] r = parseFYRange(fyVal);
                from = r[0]; to = r[1];
                label = fyVal;
            } else if (mIdx >= 0 && mIdx < months.size()) {
                LocalDate m = months.get(mIdx);
                from = m.withDayOfMonth(1);
                to   = m.withDayOfMonth(m.lengthOfMonth());
                label = m.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + m.getYear();
            } else {
                dynamicContent.getChildren().clear();
                return;
            }

            Map<String, Map<String, Long>> data = computeTwoLevelCatData(from, to);
            long total = data.values().stream()
                    .flatMap(inner -> inner.values().stream())
                    .mapToLong(Long::longValue).sum();

            dynamicContent.getChildren().setAll(
                    buildExpByCatSection(data, total, label, showSubCat.isSelected(), showSubCat),
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

        clearBtn.setOnAction(e -> {
            updating[0] = true;
            monthPicker.getSelectionModel().clearSelection();
            String fy = currentFYLabel();
            if (fyPicker.getItems().contains(fy))     fyPicker.setValue(fy);
            else if (!fyPicker.getItems().isEmpty())  fyPicker.setValue(fyPicker.getItems().get(0));
            hiddenCategories.clear();
            rebuildCategoryMenu();
            updating[0] = false;
            refresh.run();
        });

        downloadBtn.setOnAction(e -> {
            String fyVal = fyPicker.getValue();
            int mIdx = monthPicker.getSelectionModel().getSelectedIndex();
            LocalDate from, to;
            String label;
            if (fyVal != null && !fyVal.isEmpty()) {
                LocalDate[] r = parseFYRange(fyVal);
                from = r[0]; to = r[1];
                label = fyVal;
            } else if (mIdx >= 0 && mIdx < months.size()) {
                LocalDate m = months.get(mIdx);
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

        rebuildCategoryMenu();
        refresh.run();

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("scroll-page-bg");
        return sp;
    }

    // ── Data ──────────────────────────────────────────────────────────────────

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
                    if (!hiddenCategories.isEmpty() && hiddenCategories.contains(cat)) return;
                    String subCat = rSubCatId != null ? ds.getCategoryName(rSubCatId) : "";
                    result.computeIfAbsent(cat, k -> new LinkedHashMap<>())
                          .merge(subCat, t.getAmountPaise(), Long::sum);
                });
        return result;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    private VBox buildExpByCatSection(Map<String, Map<String, Long>> data, long grandTotal,
                                      String label, boolean showSubCat, CheckBox showSubCatCb) {
        if (showSubCat) {
            return buildTwoLevelCategoryBars(data, grandTotal, label, showSubCatCb);
        }
        Map<String, Long> flat = new LinkedHashMap<>();
        data.forEach((cat, subMap) ->
                flat.put(cat, subMap.values().stream().mapToLong(Long::longValue).sum()));
        return buildCategoryBarChart("Expenses by Category — " + label,
                "No expense transactions for this period.", flat, grandTotal, true, showSubCatCb);
    }

    private VBox buildTwoLevelCategoryBars(Map<String, Map<String, Long>> data,
                                            long grandTotal, String label, CheckBox showSubCatCb) {
        VBox section = new VBox(10);
        Label heading = new Label("Expenses by Category — " + label);
        heading.getStyleClass().add("text-section-title");
        Label totalLbl = new Label("Total: " + MoneyFormatter.format(grandTotal));
        totalLbl.getStyleClass().add("section-group-label");
        Region totalSpacer = new Region();
        HBox.setHgrow(totalSpacer, Priority.ALWAYS);
        HBox totalRow = new HBox(totalLbl, totalSpacer, showSubCatCb);
        totalRow.setAlignment(Pos.CENTER_LEFT);

        VBox bars = new VBox(6);
        bars.getStyleClass().add("card");
        bars.setPadding(new Insets(16));

        if (data.isEmpty()) {
            bars.getChildren().add(new Label("No expense transactions for this period."));
        } else {
            int ci = 0;
            List<Map.Entry<String, Map<String, Long>>> sorted = data.entrySet().stream()
                    .sorted(Comparator.<Map.Entry<String, Map<String, Long>>>comparingLong(
                            e -> e.getValue().values().stream().mapToLong(Long::longValue).sum())
                            .reversed())
                    .collect(Collectors.toList());

            for (Map.Entry<String, Map<String, Long>> catEntry : sorted) {
                String catName           = catEntry.getKey();
                Map<String, Long> subMap = catEntry.getValue();
                long catTotal            = subMap.values().stream().mapToLong(Long::longValue).sum();
                double catPct            = grandTotal > 0 ? catTotal * 100.0 / grandTotal : 0;
                String colour            = UiUtils.CHART_PALETTE[ci++ % UiUtils.CHART_PALETTE.length];
                boolean noSubCats        = subMap.size() == 1 && subMap.containsKey("");

                if (noSubCats) {
                    bars.getChildren().add(buildBarRow(catName, catTotal, catPct, colour, 0));
                } else {
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
        section.getChildren().addAll(heading, totalRow, bars);
        return section;
    }

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

    private VBox buildCategoryBarChart(String headingText, String emptyMessage,
                                       Map<String, Long> byCategory, long total,
                                       boolean showTotal, CheckBox showSubCatCb) {
        VBox section = new VBox(10);
        Label heading = new Label(headingText);
        heading.getStyleClass().add("text-section-title");
        section.getChildren().add(heading);

        if (showTotal) {
            Label totalLbl = new Label("Total: " + MoneyFormatter.format(total));
            totalLbl.getStyleClass().add("section-group-label");
            Region totalSpacer = new Region();
            HBox.setHgrow(totalSpacer, Priority.ALWAYS);
            HBox totalRow = new HBox(totalLbl, totalSpacer, showSubCatCb);
            totalRow.setAlignment(Pos.CENTER_LEFT);
            section.getChildren().add(totalRow);
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

    private VBox buildSummaryExpenseTable(LocalDate from, LocalDate to, String label) {
        VBox section = new VBox(10);
        Label heading = new Label("All Expense Transactions — " + label);
        heading.getStyleClass().add("text-section-title");

        List<Transaction> txs = DataStore.getInstance().getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE
                          && !t.getDate().isBefore(from) && !t.getDate().isAfter(to)
                          && !isCategoryHidden(t))
                .sorted(Comparator.comparing(Transaction::getDate).reversed())
                .collect(Collectors.toList());

        TableView<Transaction> table = new TableView<>(FXCollections.observableArrayList(txs));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(250);
        table.getColumns().addAll(
                rCol("Date",        80,  t -> t.getDate().format(DateTimeFormatter.ofPattern("dd MMM"))),
                rCol("Description", 0,   t -> t.getDescription(), "cell-desc"),
                rCol("Account",     140, t -> DataStore.getInstance().getAccountName(t.getFromAccountId())),
                rCol("Category",    130, t -> DataStore.getInstance().getCategoryName(
                        t.getClassification() != null ? t.getClassification().getCategoryId() : null)),
                rCol("Amount",      110, t -> t.getTypedSignedAmountInr(), "cell-amt")
        );
        VBox tableCard = new VBox();
        tableCard.getStyleClass().add("table-card");
        tableCard.getChildren().add(table);
        section.getChildren().addAll(heading, tableCard);
        return section;
    }

    private boolean isCategoryHidden(Transaction t) {
        if (hiddenCategories.isEmpty()) return false;
        String catId = t.getClassification() != null ? t.getClassification().getCategoryId() : null;
        String catName = DataStore.getInstance().getCategoryName(catId);
        if ("—".equals(catName)) catName = "(Uncategorized)";
        return hiddenCategories.contains(catName);
    }

    // ── Export ────────────────────────────────────────────────────────────────

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

    // ── Year picker helpers ───────────────────────────────────────────────────

    /** Returns the FY/CY label string for the current date. */
    private String currentFYLabel() {
        LocalDate today = LocalDate.now();
        boolean isIndianFY = "Indian Financial Year".equals(DataStore.getInstance().getYearFormat());
        if (isIndianFY) {
            int y = today.getYear(), m = today.getMonthValue();
            int s = (m >= 4) ? y : y - 1;
            return "FY " + s + "-" + String.valueOf(s + 1).substring(2);
        } else {
            return String.valueOf(today.getYear());
        }
    }

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

    private List<String> buildCYOptions() {
        return DataStore.getInstance().getTransactions().stream()
                .map(t -> String.valueOf(t.getDate().getYear()))
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
    }

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

    // ── Table helpers ─────────────────────────────────────────────────────────

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
