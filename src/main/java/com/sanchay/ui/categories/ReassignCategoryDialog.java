package com.sanchay.ui.categories;

import com.sanchay.model.Category;
import com.sanchay.service.DataStore;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Dialog to bulk-reassign all transactions from a source category/sub-category
 * to a different category (and optionally sub-category).
 *
 * After a confirmed reassignment the caller-supplied {@code onComplete} Runnable
 * is invoked, so the caller can perform any follow-up (e.g. delete the source
 * category and rebuild the category list).
 *
 * Usage:
 * <pre>
 *   new ReassignCategoryDialog(source, type, usageCount, "Reassign &amp; Delete",
 *           () -> { ds.deleteCategory(source.getId()); rebuild(); }).show();
 * </pre>
 */
class ReassignCategoryDialog {

    private final Category             source;
    private final Category.CategoryType type;
    private final long                 usageCount;
    private final String               confirmLabel;
    private final Runnable             onComplete;

    ReassignCategoryDialog(Category source,
                           Category.CategoryType type,
                           long usageCount,
                           String confirmLabel,
                           Runnable onComplete) {
        this.source       = source;
        this.type         = type;
        this.usageCount   = usageCount;
        this.confirmLabel = confirmLabel;
        this.onComplete   = onComplete;
    }

    void show() {
        DataStore ds = DataStore.getInstance();

        Dialog<Boolean> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Reassign Transactions", "→", 440,
                "Reassign " + usageCount + " transaction"
                + (usageCount == 1 ? "" : "s") + " from '" + source.getName() + "' to:");

        GridPane g = UiUtils.buildFormGrid(130);
        g.setPadding(new Insets(16));

        // Category picker — all parent categories of same type, excluding source itself
        ComboBox<Category> catCb = new ComboBox<>();
        catCb.setMaxWidth(Double.MAX_VALUE);
        catCb.setPromptText("Select category");
        catCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        catCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        ds.getCategories().stream()
                .filter(c -> c.getType() == type && c.getParentId() == null
                        && !c.getId().equals(source.getId()))
                .forEach(catCb.getItems()::add);

        // Sub-category picker — populated dynamically when category is chosen
        ComboBox<Category> subCatCb = new ComboBox<>();
        subCatCb.setMaxWidth(Double.MAX_VALUE);
        subCatCb.setPromptText("Select sub-category (optional)");
        subCatCb.setVisible(false);
        subCatCb.setManaged(false);
        subCatCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "  └ " + item.getName());
            }
        });
        subCatCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : "  └ " + item.getName());
            }
        });

        catCb.setOnAction(ev -> {
            Category sel = catCb.getValue();
            subCatCb.getItems().clear();
            subCatCb.setValue(null);
            if (sel != null) {
                List<Category> subs = ds.getCategories().stream()
                        .filter(c -> sel.getId().equals(c.getParentId()))
                        .toList();
                if (!subs.isEmpty()) {
                    subCatCb.getItems().addAll(subs);
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

        Label catLbl = new Label("Category:");
        catLbl.getStyleClass().add("text-form-value");
        Label subLbl = new Label("Sub-category:");
        subLbl.getStyleClass().add("text-form-value");
        g.add(catLbl,   0, 0); g.add(catCb,    1, 0); GridPane.setFillWidth(catCb,    true);
        g.add(subLbl,   0, 1); g.add(subCatCb, 1, 1); GridPane.setFillWidth(subCatCb, true);

        dlg.getDialogPane().setContent(g);

        ButtonType confirmBtn = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(confirmBtn, ButtonType.CANCEL);

        Button confirmNode = (Button) dlg.getDialogPane().lookupButton(confirmBtn);
        confirmNode.setDisable(true);
        catCb.valueProperty().addListener((obs, o, n) -> confirmNode.setDisable(n == null));

        dlg.setResultConverter(bt -> bt == confirmBtn);
        dlg.showAndWait().ifPresent(confirmed -> {
            if (!confirmed) return;
            Category newCat = catCb.getValue();
            Category newSub = subCatCb.getValue();
            ds.reassignCategory(source.getId(), newCat.getId(),
                    newSub != null ? newSub.getId() : null);
            onComplete.run();
        });
    }
}
