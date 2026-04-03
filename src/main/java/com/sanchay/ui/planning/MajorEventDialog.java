package com.sanchay.ui.planning;

import com.sanchay.model.Category;
import com.sanchay.model.MajorEvent;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Dialog for adding or editing a Major Event on the Financial Planning screen.
 * In edit mode a Delete button is shown; deletion is confirmed via {@link DeleteMajorEventDialog}.
 *
 * Usage:
 * <pre>
 *   MajorEventDialog dlg = new MajorEventDialog(existingOrNull);
 *   if (dlg.show() == MajorEventDialog.Outcome.SAVED)   { use dlg.getResult(); }
 *   if (dlg.show() == MajorEventDialog.Outcome.DELETED) { remove the event; }
 * </pre>
 */
class MajorEventDialog {

    enum Outcome { SAVED, DELETED, CANCELLED }

    private Outcome    outcome = Outcome.CANCELLED;
    private MajorEvent result;

    private final Dialog<Void>       dlg;
    private final TextField          nameFld;
    private final ComboBox<String>   typeCb;
    private final ComboBox<String>   freqCb;
    private final TextField          amtFld;
    private final ComboBox<Category> catCb;
    private final ComboBox<Category> subCatCb;
    private final DatePicker         startPicker;
    private final DatePicker         endPicker;
    private final HBox               freqRow;
    private final HBox               endRow;

    MajorEventDialog(MajorEvent existing) {
        boolean isEdit = existing != null;

        dlg = new Dialog<>();
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(520);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg,
                isEdit ? "✎" : "+",
                isEdit ? "Edit Major Event" : "Add Major Event");

        // ── Form fields ───────────────────────────────────────────────────────

        nameFld = new TextField(isEdit ? existing.getName() : "");
        nameFld.setPromptText("e.g. Children's Education");
        nameFld.setMaxWidth(Double.MAX_VALUE);

        typeCb = new ComboBox<>();
        typeCb.getItems().addAll("One Time", "Recurring");
        typeCb.setValue(isEdit && existing.getType() == MajorEvent.EventType.RECURRING
                ? "Recurring" : "One Time");
        typeCb.setMaxWidth(Double.MAX_VALUE);

        freqCb = new ComboBox<>();
        freqCb.getItems().addAll("Monthly", "Quarterly", "Yearly");
        if (isEdit && existing.getFrequency() != null) {
            freqCb.setValue(switch (existing.getFrequency()) {
                case MONTHLY   -> "Monthly";
                case QUARTERLY -> "Quarterly";
                case YEARLY    -> "Yearly";
            });
        } else {
            freqCb.setValue("Monthly");
        }
        freqCb.setMaxWidth(Double.MAX_VALUE);

        amtFld = new TextField(isEdit && existing.getAmountPaise() > 0
                ? formatRupees(existing.getAmountPaise()) : "");
        amtFld.setPromptText("e.g. 5,00,000");
        amtFld.setMaxWidth(Double.MAX_VALUE);

        // ── Category / sub-category ───────────────────────────────────────────

        DataStore ds = DataStore.getInstance();
        List<Category> topCats = ds.getCategories().stream()
                .filter(c -> c.getType() == Category.CategoryType.EXPENSE)
                .filter(c -> c.getParentId() == null)
                .filter(Category::isActive)
                .collect(Collectors.toList());

        catCb = new ComboBox<>();
        catCb.getItems().addAll(topCats);
        catCb.setPromptText("Select category");
        catCb.setMaxWidth(Double.MAX_VALUE);
        catCb.setConverter(categoryConverter());

        subCatCb = new ComboBox<>();
        subCatCb.setPromptText("None");
        subCatCb.setMaxWidth(Double.MAX_VALUE);
        subCatCb.setConverter(categoryConverter());
        subCatCb.setDisable(true);

        // Pre-populate category in edit mode; triggers sub-cat population via listener
        if (isEdit && existing.getCategoryId() != null) {
            topCats.stream()
                    .filter(c -> c.getId().equals(existing.getCategoryId()))
                    .findFirst()
                    .ifPresent(catCb::setValue);
        }

        catCb.valueProperty().addListener((obs, old, cat) ->
                populateSubCats(cat, ds, isEdit ? existing.getSubCategoryId() : null));
        if (catCb.getValue() != null) {
            populateSubCats(catCb.getValue(), ds, isEdit ? existing.getSubCategoryId() : null);
        }

        // ── Start date ────────────────────────────────────────────────────────

        startPicker = new DatePicker();
        startPicker.setMaxWidth(Double.MAX_VALUE);
        startPicker.setPromptText("dd/mm/yyyy (optional)");
        UiUtils.applySmartDateConverter(startPicker);
        UiUtils.styleOnShow(startPicker);
        if (isEdit && existing.getStartDate() != null) {
            try { startPicker.setValue(LocalDate.parse(existing.getStartDate())); }
            catch (Exception ignored) {}
        }

        // ── End date (recurring only) ─────────────────────────────────────────

        endPicker = new DatePicker();
        endPicker.setMaxWidth(Double.MAX_VALUE);
        endPicker.setPromptText("dd/mm/yyyy (defaults to retirement date)");
        UiUtils.applySmartDateConverter(endPicker);
        UiUtils.styleOnShow(endPicker);
        if (isEdit && existing.getEndDate() != null) {
            try { endPicker.setValue(LocalDate.parse(existing.getEndDate())); }
            catch (Exception ignored) {}
        }

        // ── Layout ────────────────────────────────────────────────────────────

        freqRow = formRow("Recurrence", freqCb);
        endRow   = formRow("End Date", endPicker);

        boolean startRecurring = "Recurring".equals(typeCb.getValue());
        setRecurringRowsVisible(startRecurring);

        typeCb.valueProperty().addListener((obs, o, n) -> {
            boolean recurring = "Recurring".equals(n);
            setRecurringRowsVisible(recurring);
            // Resize dialog to fit or shed the extra rows
            Platform.runLater(() -> {
                if (dlg.getDialogPane().getScene() != null
                        && dlg.getDialogPane().getScene().getWindow() instanceof Stage s) {
                    s.sizeToScene();
                }
            });
        });

        VBox form = new VBox(10,
                formRow("Event Name *", nameFld),
                formRow("Type", typeCb),
                freqRow,
                formRow("Forecasted Amount *", amtFld),
                formRow("Category *", catCb),
                formRow("Sub-category", subCatCb),
                formRow("Start Date", startPicker),
                endRow
        );
        form.setPadding(new Insets(16, 16, 8, 16));
        dlg.getDialogPane().setContent(form);

        // ── Buttons ───────────────────────────────────────────────────────────

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);

        if (isEdit) {
            ButtonType deleteType = new ButtonType("Delete", ButtonBar.ButtonData.LEFT);
            dlg.getDialogPane().getButtonTypes().addAll(deleteType, ButtonType.CANCEL, saveType);
            Platform.runLater(() -> {
                Button delBtn = (Button) dlg.getDialogPane().lookupButton(deleteType);
                if (delBtn == null) return;
                delBtn.getStyleClass().add("btn-danger");
                delBtn.addEventFilter(ActionEvent.ACTION, ev -> {
                    ev.consume();
                    boolean confirmed = new DeleteMajorEventDialog(existing.getName()).showAndWait();
                    if (confirmed) {
                        outcome = Outcome.DELETED;
                        Node pane = dlg.getDialogPane();
                        if (pane.getScene() != null && pane.getScene().getWindow() != null)
                            pane.getScene().getWindow().hide();
                    }
                });
            });
        } else {
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, saveType);
        }

        MajorEvent capturedExisting = existing;
        Platform.runLater(() -> {
            Button saveBtn = (Button) dlg.getDialogPane().lookupButton(saveType);
            if (saveBtn == null) return;
            saveBtn.addEventFilter(ActionEvent.ACTION, ev -> {
                MajorEvent validated = validate(capturedExisting);
                if (validated == null) { ev.consume(); return; }
                result  = validated;
                outcome = Outcome.SAVED;
            });
        });

        dlg.setResultConverter(bt -> null);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    Outcome show() {
        dlg.showAndWait();
        return outcome;
    }

    MajorEvent getResult() { return result; }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void setRecurringRowsVisible(boolean visible) {
        freqRow.setVisible(visible);
        freqRow.setManaged(visible);
        endRow.setVisible(visible);
        endRow.setManaged(visible);
    }

    private void populateSubCats(Category cat, DataStore ds, String selectId) {
        subCatCb.getItems().clear();
        subCatCb.setValue(null);
        if (cat == null) { subCatCb.setDisable(true); return; }

        List<Category> subs = ds.getCategories().stream()
                .filter(c -> c.getType() == Category.CategoryType.EXPENSE)
                .filter(c -> cat.getId().equals(c.getParentId()))
                .filter(Category::isActive)
                .collect(Collectors.toList());

        subCatCb.getItems().addAll(subs);
        subCatCb.setDisable(subs.isEmpty());

        if (selectId != null) {
            subs.stream().filter(c -> c.getId().equals(selectId))
                    .findFirst().ifPresent(subCatCb::setValue);
        }
    }

    private MajorEvent validate(MajorEvent existing) {
        if (nameFld.getText() == null || nameFld.getText().isBlank()) {
            alert("Validation Error", "Event Name is required.");
            return null;
        }
        long amtPaise;
        try {
            String raw = amtFld.getText().replace("₹", "").replace(",", "").trim();
            if (raw.isBlank()) throw new NumberFormatException();
            amtPaise = Math.round(Double.parseDouble(raw) * 100);
            if (amtPaise <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            alert("Validation Error", "Forecasted Amount must be a positive number.");
            return null;
        }
        if (catCb.getValue() == null) {
            alert("Validation Error", "Category is required.");
            return null;
        }
        if ("Recurring".equals(typeCb.getValue()) && freqCb.getValue() == null) {
            alert("Validation Error", "Recurrence frequency is required for recurring events.");
            return null;
        }

        MajorEvent ev = existing != null ? existing : new MajorEvent();
        ev.setName(nameFld.getText().trim());
        ev.setType("Recurring".equals(typeCb.getValue())
                ? MajorEvent.EventType.RECURRING : MajorEvent.EventType.ONE_TIME);
        ev.setFrequency(ev.getType() == MajorEvent.EventType.RECURRING
                ? switch (freqCb.getValue()) {
                    case "Monthly"   -> MajorEvent.Frequency.MONTHLY;
                    case "Quarterly" -> MajorEvent.Frequency.QUARTERLY;
                    default          -> MajorEvent.Frequency.YEARLY;
                  }
                : null);
        ev.setAmountPaise(amtPaise);
        ev.setCategoryId(catCb.getValue().getId());
        ev.setSubCategoryId(subCatCb.getValue() != null ? subCatCb.getValue().getId() : null);
        ev.setStartDate(startPicker.getValue() != null ? startPicker.getValue().toString() : null);
        ev.setEndDate(ev.getType() == MajorEvent.EventType.RECURRING && endPicker.getValue() != null
                ? endPicker.getValue().toString() : null);
        return ev;
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private static HBox formRow(String labelText, Node control) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("form-label");
        lbl.setPrefWidth(160);
        lbl.setMinWidth(Label.USE_PREF_SIZE);
        HBox.setHgrow(control, Priority.ALWAYS);
        HBox row = new HBox(12, lbl, control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static StringConverter<Category> categoryConverter() {
        return new StringConverter<>() {
            @Override public String toString(Category c)    { return c == null ? "" : c.getName(); }
            @Override public Category fromString(String s) { return null; }
        };
    }

    private static String formatRupees(long paise) {
        return String.format("₹%,.0f", paise / 100.0);
    }

    private static void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
