package com.sanchay.ui.categories;

import com.sanchay.model.Category;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Dialog to move a sub-category under a different parent category.
 *
 * Returns the chosen new parent {@link Category} via {@link #showAndWait()},
 * or an empty Optional if cancelled. The caller is responsible for calling
 * {@code DataStore.moveSubCategoryParent()} on confirmation.
 *
 * Usage:
 * <pre>
 *   new MoveSubCategoryDialog(sub, currentParent, type).showAndWait()
 *       .ifPresent(newParent -> {
 *           ds.moveSubCategoryParent(sub.getId(), newParent.getId());
 *           rebuild();
 *       });
 * </pre>
 */
class MoveSubCategoryDialog extends Dialog<Category> {

    MoveSubCategoryDialog(Category sub, Category currentParent, Category.CategoryType type) {
        DataStore ds = DataStore.getInstance();

        UiUtils.initDialog(this, "Move Sub-category", "→", 400,
                "Move '" + sub.getName() + "' to a different parent category:");

        GridPane g = UiUtils.buildFormGrid(130);
        g.setPadding(new Insets(16));

        // Parent picker — all parent categories of same type, excluding the current parent
        ComboBox<Category> parentCb = new ComboBox<>();
        parentCb.setMaxWidth(Double.MAX_VALUE);
        parentCb.setPromptText("Select new parent category");
        parentCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        parentCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        ds.getCategories().stream()
                .filter(c -> c.getType() == type && c.getParentId() == null
                        && !c.getId().equals(currentParent.getId()))
                .forEach(parentCb.getItems()::add);

        Label parentLbl = new Label("New Parent:");
        parentLbl.getStyleClass().add("text-form-value");
        g.add(parentLbl, 0, 0); g.add(parentCb, 1, 0); GridPane.setFillWidth(parentCb, true);

        long txnCount = ds.getTransactions().stream()
                .filter(t -> sub.getId().equals(t.getClassification() != null
                        ? t.getClassification().getSubCategoryId() : null))
                .count();
        if (txnCount > 0) {
            Label note = new Label(txnCount + " transaction(s) will have their category updated.");
            note.getStyleClass().add("text-hint");
            g.add(note, 0, 1, 2, 1);
        }

        getDialogPane().setContent(g);

        ButtonType confirmBtn = new ButtonType("Move", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(confirmBtn, ButtonType.CANCEL);

        Button confirmNode = (Button) getDialogPane().lookupButton(confirmBtn);
        confirmNode.setDisable(true);
        parentCb.valueProperty().addListener((obs, o, n) -> confirmNode.setDisable(n == null));

        setResultConverter(bt -> bt == confirmBtn ? parentCb.getValue() : null);
    }
}
