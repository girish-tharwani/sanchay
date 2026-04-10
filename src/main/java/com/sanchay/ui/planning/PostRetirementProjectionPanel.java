package com.sanchay.ui.planning;

import com.sanchay.model.PlanParameters;
import com.sanchay.service.FinancialPlanningCalculator;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;

class PostRetirementProjectionPanel {

    // ── Union type for the table rows ─────────────────────────────────────────

    /** Either a phase-separator header or a real data row. */
    private abstract static class TableEntry {}

    private static final class PhaseHeaderEntry extends TableEntry {
        final String text;
        PhaseHeaderEntry(String text) { this.text = text; }
    }

    private static final class DataEntry extends TableEntry {
        final FinancialPlanningCalculator.PostRetirementRow row;
        DataEntry(FinancialPlanningCalculator.PostRetirementRow row) { this.row = row; }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private enum ProjectionMode { MINIMUM, ACTUAL }

    private final FinancialPlanningCalculator planningCalculator;
    private final LongFunction<String> moneyFormatter;

    private VBox root;
    private TableView<TableEntry> table;
    private Label minimumProjectionLbl;
    private Label actualProjectionLbl;
    private ProjectionMode projectionMode = ProjectionMode.MINIMUM;

    private PlanParameters params;
    private LocalDate selfDob;
    private long forecastedCorpusPaise;

    PostRetirementProjectionPanel(FinancialPlanningCalculator planningCalculator,
                                  LongFunction<String> moneyFormatter) {
        this.planningCalculator = planningCalculator;
        this.moneyFormatter = moneyFormatter;
    }

    Region build() {
        minimumProjectionLbl = new Label("Minimum Corpus Projection");
        minimumProjectionLbl.getStyleClass().addAll("text-section-title", "fp-projection-mode-label");
        minimumProjectionLbl.setPrefWidth(210);
        minimumProjectionLbl.setMinWidth(210);
        minimumProjectionLbl.setAlignment(Pos.CENTER_RIGHT);

        CheckBox projectionModeSwitch = new CheckBox();
        projectionModeSwitch.getStyleClass().add("fp-projection-switch");
        projectionModeSwitch.setSelected(false);
        projectionModeSwitch.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            projectionMode = isSelected ? ProjectionMode.ACTUAL : ProjectionMode.MINIMUM;
            updateProjectionModeHeader();
            refresh();
        });

        actualProjectionLbl = new Label("Actual Corpus Projection");
        actualProjectionLbl.getStyleClass().addAll("text-section-title", "fp-projection-mode-label");
        actualProjectionLbl.setPrefWidth(190);
        actualProjectionLbl.setMinWidth(190);
        actualProjectionLbl.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(10, minimumProjectionLbl, projectionModeSwitch, actualProjectionLbl);
        header.setAlignment(Pos.CENTER_LEFT);
        updateProjectionModeHeader();

        table = buildCashFlowTable();

        root = new VBox(12, header, table);
        root.getStyleClass().add("card");
        return root;
    }

    void updateInputs(PlanParameters params, LocalDate selfDob, long forecastedCorpusPaise) {
        this.params = params;
        this.selfDob = selfDob;
        this.forecastedCorpusPaise = forecastedCorpusPaise;
        refresh();
    }

    private void refresh() {
        if (table == null || params == null || selfDob == null) return;
        table.getItems().setAll(buildTableItems());
        table.refresh();
    }

    // ── Data helpers ──────────────────────────────────────────────────────────

    private List<TableEntry> buildTableItems() {
        long balance = projectionMode == ProjectionMode.ACTUAL
                ? forecastedCorpusPaise
                : planningCalculator.computeRequiredCorpusPaise(params, selfDob);
        List<FinancialPlanningCalculator.PostRetirementRow> rows =
                planningCalculator.computePostRetirementRows(params, selfDob, balance);

        double inf = params.inflationPct;
        int slowGoAge = FinancialPlanningCalculator.SPENDING_SLOW_GO_AGE;
        int noGoAge   = FinancialPlanningCalculator.SPENDING_NO_GO_AGE;
        double reducedInf = Math.max(0, inf - FinancialPlanningCalculator.SPENDING_SLOW_GO_REDUCTION * 100);

        List<TableEntry> items = new ArrayList<>(rows.size() + 3);
        boolean phase1Hdr = false, phase2Hdr = false, phase3Hdr = false;

        for (FinancialPlanningCalculator.PostRetirementRow row : rows) {
            if (!phase1Hdr && row.age() < slowGoAge) {
                int retireAge = planningCalculator.getRetirementAgeYears(params, selfDob);
                items.add(new PhaseHeaderEntry(
                        "▸  Active Retirement (age " + retireAge + "–" + (slowGoAge - 1) + ")"
                        + "  ·  Full Inflation " + fmtPct(inf)));
                phase1Hdr = true;
            }
            if (!phase2Hdr && row.age() >= slowGoAge && row.age() < noGoAge) {
                items.add(new PhaseHeaderEntry(
                        "▸  Slow-go Years (age " + slowGoAge + "–" + (noGoAge - 1) + ")"
                        + "  ·  Inflation " + fmtPct(reducedInf) + "  (−1.5%)"));
                phase2Hdr = true;
            }
            if (!phase3Hdr && row.age() >= noGoAge) {
                items.add(new PhaseHeaderEntry(
                        "▸  Healthcare Phase (age " + noGoAge + "+)"
                        + "  ·  Full Inflation " + fmtPct(inf)));
                phase3Hdr = true;
            }
            items.add(new DataEntry(row));
        }
        return items;
    }

    private static String fmtPct(double pct) {
        // e.g. 6.0 → "6.0%",  4.5 → "4.5%"
        if (pct == Math.floor(pct)) return (int) pct + ".0%";
        return String.format("%.1f%%", pct);
    }

    // ── Table construction ────────────────────────────────────────────────────

    private TableView<TableEntry> buildCashFlowTable() {
        TableView<TableEntry> tv = new TableView<>();
        tv.getStyleClass().add("forecast-table");
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        tv.setEditable(false);

        // Row factory: overlay a full-width label on phase-header rows.
        // overlay.setManaged(false) — excluded from the row's own layout pass so the
        // normal cell geometry is untouched.  Adding it LAST in getChildren() puts it
        // above the cells in z-order so it paints on top.
        tv.setRowFactory(t -> new TableRow<>() {
            private final Label overlayLabel = new Label();
            private final HBox overlay = new HBox(overlayLabel);

            {
                overlay.getStyleClass().add("phase-header-overlay");
                overlayLabel.getStyleClass().add("phase-header-label");
                overlay.setAlignment(Pos.CENTER_LEFT);
                overlay.setManaged(false);   // must not participate in cell layout
            }

            @Override
            protected void layoutChildren() {
                super.layoutChildren();
                // Stretch overlay to cover the full row after cells are positioned
                if (getChildren().contains(overlay)) {
                    overlay.resizeRelocate(0, 0, getWidth(), getHeight());
                }
            }

            @Override
            protected void updateItem(TableEntry item, boolean empty) {
                super.updateItem(item, empty);
                // Always remove first; re-add LAST so z-order is above cells
                getChildren().remove(overlay);
                getStyleClass().remove("phase-header-row");
                setMouseTransparent(false);

                if (!empty && item instanceof PhaseHeaderEntry hdr) {
                    overlayLabel.setText(hdr.text);
                    getChildren().add(overlay); // last child → on top
                    getStyleClass().add("phase-header-row");
                    setMouseTransparent(true);
                }
            }

            @Override
            public void updateSelected(boolean selected) {
                super.updateSelected(getItem() instanceof PhaseHeaderEntry ? false : selected);
            }
        });

        tv.getColumns().addAll(
                yearCol(), ageCol(), balanceCol(), roiCol(), taxCol(), withdrawalCol(), endingBalanceCol()
        );
        return tv;
    }

    private TableColumn<TableEntry, String> yearCol() {
        TableColumn<TableEntry, String> col = new TableColumn<>("Year");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() instanceof DataEntry d ? String.valueOf(d.row.year()) : ""));
        col.setPrefWidth(70);
        return col;
    }

    private TableColumn<TableEntry, String> ageCol() {
        TableColumn<TableEntry, String> col = new TableColumn<>("Age");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() instanceof DataEntry d ? String.valueOf(d.row.age()) : ""));
        col.setPrefWidth(55);
        return col;
    }

    private TableColumn<TableEntry, String> balanceCol() {
        TableColumn<TableEntry, String> col = new TableColumn<>("Starting Balance");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() instanceof DataEntry d ? moneyFormatter.apply(d.row.startingBalancePaise()) : ""));
        col.setPrefWidth(160);
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("post-retirement-cf-depleted");
                if (empty || item == null || item.isEmpty()) { setText(null); return; }
                setText(item);
                TableEntry entry = getTableView().getItems().get(getIndex());
                if (entry instanceof DataEntry d && d.row.startingDepleted())
                    getStyleClass().add("post-retirement-cf-depleted");
            }
        });
        return col;
    }

    private TableColumn<TableEntry, String> roiCol() {
        TableColumn<TableEntry, String> col = new TableColumn<>("ROI");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() instanceof DataEntry d
                        ? (d.row.startingDepleted() ? "-" : moneyFormatter.apply(d.row.roiPaise()))
                        : ""));
        col.setPrefWidth(130);
        return col;
    }

    private TableColumn<TableEntry, String> taxCol() {
        TableColumn<TableEntry, String> col = new TableColumn<>("Tax");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() instanceof DataEntry d
                        ? (d.row.startingDepleted() ? "-" : moneyFormatter.apply(d.row.taxPaise()))
                        : ""));
        col.setPrefWidth(110);
        return col;
    }

    private TableColumn<TableEntry, String> withdrawalCol() {
        TableColumn<TableEntry, String> col = new TableColumn<>("Withdrawal");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() instanceof DataEntry d ? moneyFormatter.apply(d.row.withdrawalPaise()) : ""));
        col.setPrefWidth(130);
        return col;
    }

    private TableColumn<TableEntry, String> endingBalanceCol() {
        TableColumn<TableEntry, String> col = new TableColumn<>("Ending Balance");
        col.setCellValueFactory(cd -> new SimpleStringProperty(
                cd.getValue() instanceof DataEntry d ? moneyFormatter.apply(d.row.endingBalancePaise()) : ""));
        col.setPrefWidth(160);
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().remove("post-retirement-cf-depleted");
                if (empty || item == null || item.isEmpty()) { setText(null); return; }
                setText(item);
                TableEntry entry = getTableView().getItems().get(getIndex());
                if (entry instanceof DataEntry d && d.row.endingNegative())
                    getStyleClass().add("post-retirement-cf-depleted");
            }
        });
        return col;
    }

    // ── Mode header ───────────────────────────────────────────────────────────

    private void updateProjectionModeHeader() {
        if (minimumProjectionLbl == null || actualProjectionLbl == null) return;
        boolean minimumActive = projectionMode == ProjectionMode.MINIMUM;
        minimumProjectionLbl.getStyleClass().remove("fp-projection-mode-label-active");
        actualProjectionLbl.getStyleClass().remove("fp-projection-mode-label-active");
        minimumProjectionLbl.getStyleClass().remove("fp-projection-mode-label-inactive");
        actualProjectionLbl.getStyleClass().remove("fp-projection-mode-label-inactive");
        minimumProjectionLbl.getStyleClass().add(minimumActive
                ? "fp-projection-mode-label-active"
                : "fp-projection-mode-label-inactive");
        actualProjectionLbl.getStyleClass().add(minimumActive
                ? "fp-projection-mode-label-inactive"
                : "fp-projection-mode-label-active");
    }
}
