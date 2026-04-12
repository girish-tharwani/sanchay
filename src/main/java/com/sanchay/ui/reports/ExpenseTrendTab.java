package com.sanchay.ui.reports;

import com.sanchay.model.Category;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.service.ReportPrefsService;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Expense Trend tab — shows net expenses (EXPENSE minus REFUND) by category and
 * sub-category across the current year and up to 4 past years, one column per year.
 * Categories sorted alphabetically; sub-categories indented below each parent.
 * Zero amounts shown as blank. A Total row summarises all visible categories.
 */
class ExpenseTrendTab {

    private static final double AMT_COL_WIDTH = 130;

    private final ScrollPane view;
    private final ReportPrefsService reportPrefs;
    private ComboBox<String> pastYearsPicker;
    private MenuButton catMenu;
    private VBox tableContainer;

    /** Category names the user has explicitly hidden. Empty = show all. */
    private final Set<String> hiddenCategories = new HashSet<>();

    ExpenseTrendTab() {
        reportPrefs = new ReportPrefsService(DataStore.getInstance().getDataFolderPath());
        hiddenCategories.addAll(reportPrefs.getExpenseTrendHidden());
        view = buildView();
    }

    Node getView() { return view; }

    void refresh() {
        rebuildCategoryMenu();
        rebuildTable();
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
                reportPrefs.saveExpenseTrendHidden(hiddenCategories);
                syncCatMenuLabel();
                rebuildTable();
            });
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

    // ── View ──────────────────────────────────────────────────────────────────

    private ScrollPane buildView() {
        VBox root = new VBox(20);
        root.setPadding(new Insets(24));

        // Controls row
        HBox controls = new HBox(12);
        controls.setAlignment(Pos.CENTER_LEFT);

        Label pastLbl = new Label("Past Years:");
        pastLbl.getStyleClass().add("form-label");

        pastYearsPicker = new ComboBox<>();
        pastYearsPicker.getItems().addAll("1 year", "2 years", "3 years", "4 years");
        pastYearsPicker.setValue("1 year");
        pastYearsPicker.setPrefWidth(120);
        pastYearsPicker.setOnAction(e -> rebuildTable());

        catMenu = new MenuButton("All Categories");
        catMenu.getStyleClass().add("menu-button-as-combo");
        catMenu.setPrefWidth(160);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button downloadBtn = new Button("⬇ Download CSV");
        downloadBtn.getStyleClass().add("btn-gold");
        downloadBtn.setOnAction(e -> exportCsv(downloadBtn));

        controls.getChildren().addAll(pastLbl, pastYearsPicker, catMenu, spacer, downloadBtn);

        tableContainer = new VBox(0);
        tableContainer.getStyleClass().add("table-card");

        root.getChildren().addAll(controls, tableContainer);

        rebuildCategoryMenu();
        rebuildTable();

        ScrollPane sp = new ScrollPane(root);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("scroll-page-bg");
        return sp;
    }

    // ── Table rebuild ─────────────────────────────────────────────────────────

    private void rebuildTable() {
        if (tableContainer == null) return;
        tableContainer.getChildren().clear();

        boolean isFY      = isIndianFY();
        int     totalCols = pastYearsCount() + 1; // current + past

        List<LocalDate[]> periods    = buildPeriods(isFY, totalCols);
        List<String>      yearLabels = periods.stream()
                .map(p -> isFY ? fyLabel(p) : cyLabel(p))
                .collect(Collectors.toList());

        DataStore ds   = DataStore.getInstance();
        Map<String, Map<String, long[]>> data = computeAmounts(ds, periods, totalCols);

        List<Category> cats = ds.getExpenseCategories().stream()
                .filter(c -> !hiddenCategories.contains(c.getName()))
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        // Build a single GridPane for the whole table — guarantees column alignment
        GridPane grid = new GridPane();
        grid.setMaxWidth(Double.MAX_VALUE);

        // Column constraints: col 0 grows, cols 1..N are fixed 130px right-aligned
        ColumnConstraints labelCol = new ColumnConstraints();
        labelCol.setHgrow(Priority.ALWAYS);
        labelCol.setFillWidth(true);
        grid.getColumnConstraints().add(labelCol);
        for (int i = 0; i < totalCols; i++) {
            ColumnConstraints amtCol = new ColumnConstraints(AMT_COL_WIDTH, AMT_COL_WIDTH, AMT_COL_WIDTH);
            amtCol.setHalignment(HPos.RIGHT);
            grid.getColumnConstraints().add(amtCol);
        }

        int row = 0;

        // Header row
        row = addHeaderRow(grid, yearLabels, row);

        // Teal separator
        row = addSeparator(grid, row);

        boolean hasData = false;
        long[] grandTotals = new long[totalCols];

        for (Category cat : cats) {
            Map<String, long[]> catData = data.get(cat.getId());
            if (catData == null) continue;

            long[] catTotals = new long[totalCols];
            for (long[] arr : catData.values())
                for (int i = 0; i < totalCols; i++) catTotals[i] += arr[i];

            if (!anyNonZero(catTotals)) continue;
            hasData = true;

            for (int i = 0; i < totalCols; i++) grandTotals[i] += catTotals[i];

            row = addCategoryRow(grid, cat.getName(), catTotals, row);

            List<Category> subCats = ds.getCategories().stream()
                    .filter(c -> cat.getId().equals(c.getParentId()) && c.isActive())
                    .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());

            for (Category sub : subCats) {
                long[] subAmts = catData.get(sub.getId());
                if (subAmts == null || !anyNonZero(subAmts)) continue;
                row = addSubCatRow(grid, sub.getName(), subAmts, row);
            }
        }

        if (!hasData) {
            Label none = new Label("No expense transactions found for the selected period.");
            none.getStyleClass().add("text-empty");
            none.setPadding(new Insets(16));
            GridPane.setColumnSpan(none, GridPane.REMAINING);
            grid.add(none, 0, row);
        } else {
            // Total row
            row = addSeparator(grid, row);
            addTotalRow(grid, grandTotals, row);
        }

        tableContainer.getChildren().add(grid);
    }

    // ── Row builders (GridPane-based) ─────────────────────────────────────────

    private int addHeaderRow(GridPane grid, List<String> yearLabels, int row) {
        Label catLbl = new Label("CATEGORY / SUB-CATEGORY");
        catLbl.getStyleClass().add("filter-label");
        GridPane.setMargin(catLbl, new Insets(10, 14, 10, 14));
        grid.add(catLbl, 0, row);

        for (int i = 0; i < yearLabels.size(); i++) {
            Label yrLbl = new Label(yearLabels.get(i));
            yrLbl.getStyleClass().add("filter-label");
            yrLbl.setAlignment(Pos.CENTER_RIGHT);
            yrLbl.setMaxWidth(Double.MAX_VALUE);
            GridPane.setMargin(yrLbl, new Insets(10, 14, 10, 0));
            grid.add(yrLbl, i + 1, row);
        }
        return row + 1;
    }

    private int addSeparator(GridPane grid, int row) {
        Region sep = new Region();
        sep.getStyleClass().add("sep-teal");
        sep.setMaxWidth(Double.MAX_VALUE);
        GridPane.setColumnSpan(sep, GridPane.REMAINING);
        grid.add(sep, 0, row);
        return row + 1;
    }

    private int addCategoryRow(GridPane grid, String name, long[] amounts, int row) {
        Region bg = new Region();
        bg.setMaxWidth(Double.MAX_VALUE);
        bg.setMaxHeight(Double.MAX_VALUE);
        bg.setStyle("-fx-background-color: rgba(42,138,122,0.07);");
        GridPane.setColumnSpan(bg, GridPane.REMAINING);
        grid.add(bg, 0, row);

        Label nameLbl = new Label(name);
        // Inline required: bold + brand-dark for category-level rows (data-driven weight)
        nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: -brand-dark; -fx-font-size: 12px;");
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        GridPane.setMargin(nameLbl, new Insets(8, 14, 8, 14));
        grid.add(nameLbl, 0, row);

        for (int i = 0; i < amounts.length; i++) {
            Label amtLbl = amountLabel(amounts[i], true);
            GridPane.setMargin(amtLbl, new Insets(8, 14, 8, 0));
            grid.add(amtLbl, i + 1, row);
        }
        return row + 1;
    }

    private int addSubCatRow(GridPane grid, String name, long[] amounts, int row) {
        Region border = new Region();
        border.setMaxWidth(Double.MAX_VALUE);
        border.setMaxHeight(Double.MAX_VALUE);
        border.setStyle("-fx-border-color: transparent transparent rgba(42,138,122,0.12) transparent; " +
                        "-fx-border-width: 0 0 1px 0;");
        GridPane.setColumnSpan(border, GridPane.REMAINING);
        grid.add(border, 0, row);

        HBox nameCell = new HBox(0);
        Region indent = new Region();
        indent.setMinWidth(16);
        indent.setMaxWidth(16);
        Label nameLbl = new Label(name);
        nameLbl.getStyleClass().add("text-body-muted");
        HBox.setHgrow(nameLbl, Priority.ALWAYS);
        nameCell.getChildren().addAll(indent, nameLbl);
        nameCell.setMaxWidth(Double.MAX_VALUE);
        GridPane.setMargin(nameCell, new Insets(6, 14, 6, 14));
        grid.add(nameCell, 0, row);

        for (int i = 0; i < amounts.length; i++) {
            Label amtLbl = amountLabel(amounts[i], false);
            GridPane.setMargin(amtLbl, new Insets(6, 14, 6, 0));
            grid.add(amtLbl, i + 1, row);
        }
        return row + 1;
    }

    private void addTotalRow(GridPane grid, long[] amounts, int row) {
        Region bg = new Region();
        bg.setMaxWidth(Double.MAX_VALUE);
        bg.setMaxHeight(Double.MAX_VALUE);
        bg.setStyle("-fx-background-color: rgba(42,138,122,0.13);");
        GridPane.setColumnSpan(bg, GridPane.REMAINING);
        grid.add(bg, 0, row);

        Label nameLbl = new Label("TOTAL");
        // Inline required: bold + brand-dark, slightly larger for the summary total row
        nameLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: -brand-dark; -fx-font-size: 13px;");
        nameLbl.setMaxWidth(Double.MAX_VALUE);
        GridPane.setMargin(nameLbl, new Insets(10, 14, 10, 14));
        grid.add(nameLbl, 0, row);

        for (int i = 0; i < amounts.length; i++) {
            Label amtLbl = amountLabel(amounts[i], true);
            // Inline required: slightly larger font for total row to match the label weight
            amtLbl.setStyle(amtLbl.getStyle() + " -fx-font-size: 13px;");
            GridPane.setMargin(amtLbl, new Insets(10, 14, 10, 0));
            grid.add(amtLbl, i + 1, row);
        }
    }

    private Label amountLabel(long paise, boolean bold) {
        Label lbl = new Label(paise == 0 ? "" : MoneyFormatter.format(paise));
        lbl.setAlignment(Pos.CENTER_RIGHT);
        lbl.setMaxWidth(Double.MAX_VALUE);
        lbl.getStyleClass().add("text-form-value");
        // Inline required: alignment override + optional bold + error colour for negative amounts
        String style = "-fx-text-alignment: right;";
        if (bold && paise != 0) style += " -fx-font-weight: bold;";
        if (paise < 0)          style += " -fx-text-fill: -color-error;";
        lbl.setStyle(style);
        return lbl;
    }

    // ── Data computation ──────────────────────────────────────────────────────

    /**
     * Builds: catId → (subCatId or "" for no sub-category) → long[totalCols] net paise.
     * Net = EXPENSE amounts − REFUND amounts.
     */
    private Map<String, Map<String, long[]>> computeAmounts(DataStore ds,
                                                             List<LocalDate[]> periods,
                                                             int totalCols) {
        Map<String, Map<String, long[]>> result = new HashMap<>();
        for (int yi = 0; yi < totalCols; yi++) {
            LocalDate from = periods.get(yi)[0];
            LocalDate to   = periods.get(yi)[1];
            final int idx  = yi;
            ds.getTransactions().stream()
                    .filter(t -> (t.getType() == Transaction.Type.EXPENSE
                               || t.getType() == Transaction.Type.REFUND)
                            && !t.getDate().isBefore(from) && !t.getDate().isAfter(to)
                            && t.getClassification() != null
                            && t.getClassification().getCategoryId() != null)
                    .forEach(t -> {
                        String catId    = t.getClassification().getCategoryId();
                        String subCatId = t.getClassification().getSubCategoryId();
                        if (subCatId == null) subCatId = "";
                        long sign = t.getType() == Transaction.Type.EXPENSE ? 1L : -1L;
                        result.computeIfAbsent(catId, k -> new HashMap<>())
                              .computeIfAbsent(subCatId, k -> new long[totalCols])[idx]
                              += sign * t.getAmountPaise();
                    });
        }
        return result;
    }

    // ── CSV export ────────────────────────────────────────────────────────────

    private void exportCsv(Node owner) {
        boolean isFY      = isIndianFY();
        int     totalCols = pastYearsCount() + 1;
        List<LocalDate[]> periods    = buildPeriods(isFY, totalCols);
        List<String>      yearLabels = periods.stream()
                .map(p -> isFY ? fyLabel(p) : cyLabel(p))
                .collect(Collectors.toList());

        DataStore ds   = DataStore.getInstance();
        Map<String, Map<String, long[]>> data = computeAmounts(ds, periods, totalCols);
        List<Category> cats = ds.getExpenseCategories().stream()
                .filter(c -> !hiddenCategories.contains(c.getName()))
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Expense Trend");
        fc.setInitialFileName("expense-trend.csv");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File file = fc.showSaveDialog(owner.getScene().getWindow());
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(file, StandardCharsets.UTF_8)) {
            StringBuilder hdr = new StringBuilder("Category,Sub-Category");
            yearLabels.forEach(y -> hdr.append(",").append(escapeCsv(y)));
            pw.println(hdr);

            long[] grandTotals = new long[totalCols];

            for (Category cat : cats) {
                Map<String, long[]> catData = data.get(cat.getId());
                if (catData == null) continue;

                long[] catTotals = new long[totalCols];
                for (long[] arr : catData.values())
                    for (int i = 0; i < totalCols; i++) catTotals[i] += arr[i];
                if (!anyNonZero(catTotals)) continue;

                for (int i = 0; i < totalCols; i++) grandTotals[i] += catTotals[i];

                pw.println(buildCsvRow(escapeCsv(cat.getName()), "", catTotals, totalCols));

                List<Category> subCats = ds.getCategories().stream()
                        .filter(c -> cat.getId().equals(c.getParentId()) && c.isActive())
                        .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                        .collect(Collectors.toList());
                for (Category sub : subCats) {
                    long[] subAmts = catData.get(sub.getId());
                    if (subAmts == null || !anyNonZero(subAmts)) continue;
                    pw.println(buildCsvRow(escapeCsv(cat.getName()),
                                           escapeCsv(sub.getName()), subAmts, totalCols));
                }
            }

            // Total row
            pw.println(buildCsvRow("TOTAL", "", grandTotals, totalCols));

        } catch (Exception ex) {
            new Alert(Alert.AlertType.ERROR, "Failed to save file: " + ex.getMessage())
                    .showAndWait();
        }
    }

    private String buildCsvRow(String cat, String sub, long[] amounts, int totalCols) {
        StringBuilder sb = new StringBuilder(cat).append(",").append(sub);
        for (int i = 0; i < totalCols; i++)
            sb.append(",").append(amounts[i] == 0 ? "" :
                    String.format("%.2f", amounts[i] / 100.0));
        return sb.toString();
    }

    // ── Year helpers ──────────────────────────────────────────────────────────

    private boolean isIndianFY() {
        return "Indian Financial Year".equals(DataStore.getInstance().getYearFormat());
    }

    private int pastYearsCount() {
        if (pastYearsPicker == null || pastYearsPicker.getValue() == null) return 1;
        return Integer.parseInt(pastYearsPicker.getValue().split(" ")[0]);
    }

    private List<LocalDate[]> buildPeriods(boolean isFY, int totalCols) {
        List<LocalDate[]> periods = new ArrayList<>();
        for (int i = 0; i < totalCols; i++)
            periods.add(isFY ? fyRange(i) : cyRange(i));
        return periods;
    }

    /** Index 0 = current FY, 1 = previous, etc. */
    private LocalDate[] fyRange(int index) {
        LocalDate today = LocalDate.now();
        int startYear   = today.getMonthValue() >= 4 ? today.getYear() : today.getYear() - 1;
        startYear -= index;
        return new LocalDate[]{LocalDate.of(startYear, 4, 1),
                               LocalDate.of(startYear + 1, 3, 31)};
    }

    /** Index 0 = current calendar year, 1 = previous, etc. */
    private LocalDate[] cyRange(int index) {
        int year = LocalDate.now().getYear() - index;
        return new LocalDate[]{LocalDate.of(year, 1, 1),
                               LocalDate.of(year, 12, 31)};
    }

    private String fyLabel(LocalDate[] range) {
        int s = range[0].getYear();
        return "FY " + s + "-" + String.valueOf(s + 1).substring(2);
    }

    private String cyLabel(LocalDate[] range) {
        return String.valueOf(range[0].getYear());
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private boolean anyNonZero(long[] arr) {
        for (long v : arr) if (v != 0) return true;
        return false;
    }

    private static String escapeCsv(String s) {
        if (s == null || s.isEmpty()) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n"))
            return "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }
}
