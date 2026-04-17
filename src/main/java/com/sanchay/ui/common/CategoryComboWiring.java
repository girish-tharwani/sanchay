package com.sanchay.ui.common;

import com.sanchay.model.Category;
import com.sanchay.service.DataStore;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;

import java.util.List;

public final class CategoryComboWiring {
    private CategoryComboWiring() {}

    /**
     * Styles subCatCb with "└ name" display and wires the catCb → subCatCb cascade listener.
     * Call before UiUtils.wireAutoComplete() so the combo is styled before autocomplete is attached.
     */
    public static void wire(ComboBox<Category> catCb, ComboBox<Category> subCatCb,
                            List<Category> subMaster, DataStore ds) {
        styleSubCatCombo(subCatCb);
        catCb.valueProperty().addListener((obs, old, sel) -> {
            subCatCb.getItems().clear();
            subCatCb.setValue(null);
            if (subCatCb.isEditable() && subCatCb.getEditor() != null) {
                subCatCb.getEditor().clear();
            }
            subMaster.clear();
            if (sel != null) {
                List<Category> subs = ds.getSubCategories(sel.getId());
                if (!subs.isEmpty()) {
                    subMaster.addAll(subs);
                    subCatCb.getItems().setAll(subs);
                    subCatCb.setVisible(true);
                    subCatCb.setManaged(true);
                } else {
                    subCatCb.setVisible(false);
                    subCatCb.setManaged(false);
                }
            } else {
                subCatCb.setVisible(false);
                subCatCb.setManaged(false);
            }
        });
    }

    /** Applies the "└ name" cell factory and button cell to a sub-category ComboBox. */
    public static void styleSubCatCombo(ComboBox<Category> cb) {
        cb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : "  └ " + c.getName());
                setStyle(empty || c == null ? "" : "-fx-text-fill: -text-label;");
            }
        });
        cb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Category c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : "  └ " + c.getName());
            }
        });
    }
}
