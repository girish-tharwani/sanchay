package com.sanchay.ui.reports;

import com.sanchay.model.ForecastOverride;
import com.sanchay.service.ForecastStateService;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.scene.control.*;

import java.time.YearMonth;
import java.util.Optional;

/**
 * Provides override-related table cell factories and dialog interaction for the
 * cash-flow forecast table. Delegates saves to {@link ForecastStateService} and
 * triggers a refresh via the supplied callback.
 */
class ForecastOverridesPanel {

    private final ForecastStateService forecastState;
    private final Runnable             onRefresh;

    ForecastOverridesPanel(ForecastStateService forecastState, Runnable onRefresh) {
        this.forecastState = forecastState;
        this.onRefresh     = onRefresh;
    }

    // ── Cell factories ────────────────────────────────────────────────────────

    /** Cell that applies greyed-out styling to excluded rows. */
    TableCell<ForecastTableRow, String> buildExcludedAwareCell() {
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
    TableCell<ForecastTableRow, String> buildAmountCell() {
        return new AmountCell();
    }

    /** Renders a checkbox — checked means included, unchecked means excluded. */
    TableCell<ForecastTableRow, Void> buildActionCell() {
        return new ActionCell();
    }

    // ── Override dialogs ──────────────────────────────────────────────────────

    void promptAmountCorrection(ForecastTableRow row) {
        TextInputDialog input = new TextInputDialog(
                row.amount().replace(MoneyFormatter.symbol(), "").replace(",", "").trim());
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
        onRefresh.run();
    }

    boolean promptExclusion(ForecastTableRow row) {
        Optional<Boolean> scope = askScope("Exclude Sub-Category",
                "Exclude from forecast:", row.subCategory(), row.month());
        if (scope.isEmpty()) return false;

        YearMonth targetMonth = scope.get() ? null : row.yearMonth();
        forecastState.saveOverride(ForecastOverride.exclusion(
                row.categoryId(), row.subCategoryId(), targetMonth));
        onRefresh.run();
        return true;
    }

    boolean promptInclusion(ForecastTableRow row) {
        Optional<Boolean> scope = askScope("Include Sub-Category",
                "Include back in forecast:", row.subCategory(), row.month());
        if (scope.isEmpty()) return false;

        YearMonth targetMonth = scope.get() ? null : row.yearMonth();
        forecastState.saveOverride(ForecastOverride.inclusionMarker(
                row.categoryId(), row.subCategoryId(), targetMonth));
        onRefresh.run();
        return true;
    }

    /**
     * Shows a two-choice dialog: "This month only" vs "All future months".
     * Returns Optional.of(true) for "All future months", Optional.of(false) for "This month only",
     * and Optional.empty() if the user cancelled.
     */
    private Optional<Boolean> askScope(String title, String headerPrefix,
                                        String subCatName, String monthLabel) {
        ButtonType thisMonth = new ButtonType("This month only",   ButtonBar.ButtonData.LEFT);
        ButtonType allMonths = new ButtonType("All future months", ButtonBar.ButtonData.RIGHT);
        ButtonType cancel    = new ButtonType("Cancel",            ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert dlg = new Alert(Alert.AlertType.NONE);
        dlg.setTitle(title);
        dlg.setHeaderText(headerPrefix);
        dlg.setContentText(subCatName + "\n\nShould this apply to " + monthLabel
                + " only, or to all future months?");
        dlg.getButtonTypes().setAll(thisMonth, allMonths, cancel);
        UiUtils.applyStylesheet(dlg);

        Optional<ButtonType> result = dlg.showAndWait();
        if (result.isEmpty() || result.get() == cancel) return Optional.empty();
        return Optional.of(result.get() == allMonths);
    }

    private void showError(String message) {
        Alert err = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        UiUtils.applyStylesheet(err);
        err.showAndWait();
    }

    // ── Inner cell classes ────────────────────────────────────────────────────

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

    private class ActionCell extends TableCell<ForecastTableRow, Void> {
        private final CheckBox checkBox = new CheckBox();
        private boolean updating = false;

        ActionCell() {
            checkBox.setOnAction(e -> {
                if (updating) return;
                ForecastTableRow row = getTableView().getItems().get(getIndex());
                boolean acted = checkBox.isSelected() ? promptInclusion(row) : promptExclusion(row);
                if (!acted) {
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
}
