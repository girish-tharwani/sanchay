package com.sanchay.ui.categories;

import com.sanchay.model.Category;
import com.sanchay.service.DataStore;
import com.sanchay.ui.common.SingleInputDialog;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Comparator;
import java.util.List;

/**
 * Categories screen — manages expense and income categories and sub-categories.
 * Inline dialogs have been extracted to dedicated classes in this package:
 * {@link CategoryTransactionsDialog}, {@link ReassignCategoryDialog}, {@link MoveSubCategoryDialog}.
 */
public class CategoriesScreen {

    private ScrollPane view;

    public CategoriesScreen() { buildView(); }

    public Node getView() { return view; }

    public void refresh() { buildView(); }

    private void buildView() {
        VBox content = new VBox(24);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(24));

        Label title = new Label("Categories");
        title.getStyleClass().add("screen-title");

        Label subtitle = new Label(
                "Manage expense and income categories and sub-categories. "
                        + "Deactivated categories are hidden from transaction entry but preserved in history.");
        subtitle.setWrapText(true);
        subtitle.getStyleClass().add("text-hint");

        VBox expSection = new VBox(8);
        VBox incSection = new VBox(8);
        buildCategoryGroup(expSection, Category.CategoryType.EXPENSE, "📂  Expense Categories");
        buildCategoryGroup(incSection, Category.CategoryType.INCOME,  "💰  Income Categories");

        content.getChildren().addAll(title, subtitle, expSection, incSection);

        view = new ScrollPane(content);
        view.setFitToWidth(true);
        view.getStyleClass().add("scroll-page-bg");
    }

    // ── Category group (Expense or Income) ────────────────────────────────────

    private void buildCategoryGroup(VBox section, Category.CategoryType type, String heading) {
        section.getChildren().clear();
        DataStore ds = DataStore.getInstance();

        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4,
                javafx.scene.paint.Color.web(UiUtils.HEX_BRAND_LIGHT));
        Label h = new Label(heading.replaceAll("^[^\\w]*", "").toUpperCase());
        h.getStyleClass().add("section-group-label");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("btn-gold");
        addBtn.setOnAction(e -> {
            String input = SingleInputDialog.show("Add Category", "Category name*", null, "");
            if (input == null) return;
            String trimmed = input.trim();
            if (trimmed.isEmpty()) { alert("Add Failed", "Name cannot be blank."); return; }
            boolean exists = ds.getCategories().stream()
                    .anyMatch(c -> c.getType() == type
                            && c.getParentId() == null
                            && c.getName().equalsIgnoreCase(trimmed));
            if (exists) { alert("Add Failed", "A category with that name already exists."); return; }
            ds.addCategory(new Category(trimmed, type, null));
            buildCategoryGroup(section, type, heading);
        });
        headerRow.getChildren().addAll(dot, h, sp, addBtn);
        section.getChildren().add(headerRow);

        VBox card = new VBox(0);
        card.getStyleClass().add("table-card");
        card.setPadding(new Insets(0));

        List<Category> parentCats = ds.getCategories().stream()
                .filter(c -> c.getType() == type && c.getParentId() == null)
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (parentCats.isEmpty()) {
            Label none = new Label("No categories yet.");
            none.getStyleClass().add("text-empty");
            none.setPadding(new Insets(12));
            card.getChildren().add(none);
        }

        for (int i = 0; i < parentCats.size(); i++) {
            Category cat = parentCats.get(i);
            boolean isLast = (i == parentCats.size() - 1);
            card.getChildren().add(buildCategoryBlock(cat, section, type, heading, isLast));
        }

        section.getChildren().add(card);
    }

    // ── Single category block (parent row + expandable sub-categories) ────────

    private VBox buildCategoryBlock(Category cat,
                                    VBox groupSection,
                                    Category.CategoryType type,
                                    String heading,
                                    boolean isLast) {
        DataStore ds = DataStore.getInstance();
        VBox block = new VBox(0);

        List<Category> subCats = ds.getCategories().stream()
                .filter(c -> cat.getId().equals(c.getParentId()))
                .sorted(Comparator.comparing(Category::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        // ── Parent row ────────────────────────────────────────────────────────
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(10, 14, 10, 14));
        if (!isLast) row.getStyleClass().add("category-row");

        final boolean[] expanded = {false};
        VBox subCatContainer = new VBox(0);
        subCatContainer.setVisible(false);
        subCatContainer.setManaged(false);

        Label expandBtn = new Label(subCats.isEmpty() ? "" : "▸");
        expandBtn.getStyleClass().addAll("filter-label", "icon-sm");
        expandBtn.setMinWidth(16);
        if (!subCats.isEmpty()) expandBtn.setOnMouseClicked(e -> {
            expanded[0] = !expanded[0];
            subCatContainer.setVisible(expanded[0]);
            subCatContainer.setManaged(expanded[0]);
            expandBtn.setText(expanded[0] ? "▾" : "▸");
        });

        Label nameLbl = new Label(cat.getName());
        nameLbl.setMinWidth(180);
        nameLbl.getStyleClass().addAll("filter-label", cat.isActive() ? "category-name-active" : "category-name-inactive");

        Label statusLbl = new Label(cat.isActive() ? "Active" : "Inactive");
        statusLbl.getStyleClass().add(cat.isActive() ? "status-active" : "status-closed");

        Label subCountLbl = new Label(subCats.size() + " sub-categor" + (subCats.size() == 1 ? "y" : "ies"));
        subCountLbl.getStyleClass().add("text-hint");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button showTxnBtn = new Button("☰");
        showTxnBtn.getStyleClass().add("btn-icon");
        showTxnBtn.setTooltip(new Tooltip("Transactions"));
        showTxnBtn.setOnAction(e -> new CategoryTransactionsDialog(cat).show());

        // ── ⋮ menu ────────────────────────────────────────────────────────────
        long usageCount = ds.getCategoryUsageCount(cat.getId())
                + subCats.stream().mapToLong(sc -> ds.getCategoryUsageCount(sc.getId())).sum();

        MenuItem addSubItem = new MenuItem("Add Sub-category");
        addSubItem.setOnAction(e -> {
            String input = SingleInputDialog.show("Add Sub-category", "Sub-category name*", "Parent: " + cat.getName(), "");
            if (input == null) return;
            String t = input.trim();
            if (t.isEmpty()) { alert("Add Failed", "Name cannot be blank."); return; }
            boolean exists = ds.getCategories().stream()
                    .anyMatch(c -> cat.getId().equals(c.getParentId()) && c.getName().equalsIgnoreCase(t));
            if (exists) { alert("Add Failed", "A sub-category with that name already exists."); return; }
            ds.addCategory(new Category(t, type, cat.getId()));
            buildCategoryGroup(groupSection, type, heading);
        });

        MenuItem renameItem = new MenuItem("Rename");
        renameItem.setOnAction(e -> {
            String input = SingleInputDialog.show("Rename Category", "New name*", null, cat.getName());
            if (input == null) return;
            String t = input.trim();
            if (t.isEmpty()) { alert("Rename Failed", "Name cannot be blank."); return; }
            cat.setName(t);
            ds.saveCategoriesNow();
            buildCategoryGroup(groupSection, type, heading);
        });

        MenuItem reassignItem = new MenuItem("Reassign Transactions →");
        reassignItem.setDisable(usageCount == 0);
        reassignItem.setOnAction(e ->
                new ReassignCategoryDialog(cat, type, usageCount, "Reassign Transactions",
                        () -> buildCategoryGroup(groupSection, type, heading)).show());

        MenuItem toggleItem = new MenuItem(cat.isActive() ? "Deactivate" : "Reactivate");
        toggleItem.setOnAction(e -> {
            cat.setActive(!cat.isActive());
            ds.saveCategoriesNow();
            buildCategoryGroup(groupSection, type, heading);
        });

        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.getStyleClass().add("menu-item-danger");
        deleteItem.setOnAction(e -> handleCategoryDelete(cat, subCats, type, groupSection, heading));

        ContextMenu menu = new ContextMenu();
        menu.getItems().addAll(
                addSubItem, renameItem,
                new SeparatorMenuItem(),
                reassignItem,
                new SeparatorMenuItem(),
                toggleItem,
                new SeparatorMenuItem(),
                deleteItem);

        Button menuBtn = new Button("⋮");
        menuBtn.getStyleClass().add("btn-icon");
        menuBtn.setOnAction(e -> menu.show(menuBtn, Side.BOTTOM, 0, 0));

        row.getChildren().addAll(expandBtn, nameLbl, subCountLbl, spacer,
                statusLbl, showTxnBtn, menuBtn);
        block.getChildren().add(row);

        buildSubCategoryRows(subCatContainer, subCats, cat, groupSection, type, heading, ds);
        block.getChildren().add(subCatContainer);

        return block;
    }

    // ── Sub-category rows ─────────────────────────────────────────────────────

    private void buildSubCategoryRows(VBox container,
                                      List<Category> subCats,
                                      Category parent,
                                      VBox groupSection,
                                      Category.CategoryType type,
                                      String heading,
                                      DataStore ds) {
        container.getChildren().clear();
        for (Category sub : subCats) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 14, 8, 40));
            row.getStyleClass().add("subcategory-row");

            Label indent = new Label("↳");
            indent.getStyleClass().add("text-hint");

            Label nameLbl = new Label(sub.getName());
            nameLbl.setMinWidth(160);
            nameLbl.getStyleClass().addAll("filter-label", sub.isActive() ? "subcategory-name-active" : "subcategory-name-inactive");

            Label statusLbl = new Label(sub.isActive() ? "Active" : "Inactive");
            statusLbl.getStyleClass().add(sub.isActive() ? "status-active" : "status-closed");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button showTxnBtn = new Button("☰");
            showTxnBtn.getStyleClass().add("btn-icon");
            showTxnBtn.setTooltip(new Tooltip("Transactions"));
            showTxnBtn.setOnAction(e -> new CategoryTransactionsDialog(sub).show());

            // ── ⋮ menu ────────────────────────────────────────────────────────
            long usage = ds.getCategoryUsageCount(sub.getId());

            MenuItem renameItem = new MenuItem("Rename");
            renameItem.setOnAction(e -> {
                String input = SingleInputDialog.show("Rename Sub-category", "New name*", null, sub.getName());
                if (input == null) return;
                String t = input.trim();
                if (t.isEmpty()) { alert("Rename Failed", "Name cannot be blank."); return; }
                sub.setName(t);
                ds.saveCategoriesNow();
                buildCategoryGroup(groupSection, type, heading);
            });

            MenuItem moveItem = new MenuItem("Move to Category →");
            moveItem.setOnAction(e ->
                    new MoveSubCategoryDialog(sub, parent, type).showAndWait()
                            .ifPresent(newParent -> {
                                DataStore.getInstance().moveSubCategoryParent(sub.getId(), newParent.getId());
                                buildCategoryGroup(groupSection, type, heading);
                            }));

            MenuItem reassignItem = new MenuItem("Reassign Transactions →");
            reassignItem.setDisable(usage == 0);
            reassignItem.setOnAction(e ->
                    new ReassignCategoryDialog(sub, type, usage, "Reassign Transactions",
                            () -> buildCategoryGroup(groupSection, type, heading)).show());

            MenuItem toggleItem = new MenuItem(sub.isActive() ? "Deactivate" : "Reactivate");
            toggleItem.setOnAction(e -> {
                sub.setActive(!sub.isActive());
                ds.saveCategoriesNow();
                buildCategoryGroup(groupSection, type, heading);
            });

            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.getStyleClass().add("menu-item-danger");
            deleteItem.setOnAction(e -> handleSubCategoryDelete(sub, type, groupSection, heading));

            ContextMenu menu = new ContextMenu();
            menu.getItems().addAll(
                    renameItem, moveItem,
                    new SeparatorMenuItem(),
                    reassignItem,
                    new SeparatorMenuItem(),
                    toggleItem,
                    new SeparatorMenuItem(),
                    deleteItem);

            Button menuBtn = new Button("⋮");
            menuBtn.getStyleClass().add("btn-icon");
            menuBtn.setOnAction(e -> menu.show(menuBtn, Side.BOTTOM, 0, 0));

            row.getChildren().addAll(indent, nameLbl, spacer, statusLbl, showTxnBtn, menuBtn);
            container.getChildren().add(row);
        }
    }

    // ── Delete handlers ───────────────────────────────────────────────────────

    private void handleCategoryDelete(Category cat, List<Category> subCats,
                                       Category.CategoryType type,
                                       VBox groupSection, String heading) {
        DataStore ds = DataStore.getInstance();
        long directUsage = ds.getCategoryUsageCount(cat.getId());
        long subUsage    = subCats.stream().mapToLong(sc -> ds.getCategoryUsageCount(sc.getId())).sum();

        // Case 4 — sub-categories have transactions: must handle them individually first
        if (!subCats.isEmpty() && subUsage > 0) {
            alert("Cannot Delete Yet",
                    subUsage + " transaction(s) are mapped to sub-categories of '" + cat.getName() + "'.\n"
                    + "Reassign or delete each sub-category with transactions first.");
            return;
        }

        // Case 3 — has sub-categories but no transactions anywhere: cascade delete
        if (!subCats.isEmpty() && subUsage == 0 && directUsage == 0) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Category");
            confirm.setHeaderText("Delete '" + cat.getName() + "'?");
            confirm.setContentText("Its " + subCats.size() + " sub-categor"
                    + (subCats.size() == 1 ? "y" : "ies") + " will also be deleted. This cannot be undone.");
            confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                subCats.forEach(sc -> ds.deleteCategory(sc.getId()));
                ds.deleteCategory(cat.getId());
                buildCategoryGroup(groupSection, type, heading);
            });
            return;
        }

        // Case 2 — leaf category (or sub-cats with no transactions) with direct transactions
        if (directUsage > 0) {
            new ReassignCategoryDialog(cat, type, directUsage, "Reassign & Delete", () -> {
                subCats.forEach(sc -> ds.deleteCategory(sc.getId()));
                ds.deleteCategory(cat.getId());
                buildCategoryGroup(groupSection, type, heading);
            }).show();
            return;
        }

        // No transactions, no sub-categories — simple confirmation
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Category");
        confirm.setHeaderText("Delete '" + cat.getName() + "'?");
        confirm.setContentText("This cannot be undone.");
        confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            ds.deleteCategory(cat.getId());
            buildCategoryGroup(groupSection, type, heading);
        });
    }

    private void handleSubCategoryDelete(Category sub, Category.CategoryType type,
                                          VBox groupSection, String heading) {
        DataStore ds = DataStore.getInstance();
        long usage = ds.getCategoryUsageCount(sub.getId());

        if (usage == 0) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Sub-category");
            confirm.setHeaderText("Delete '" + sub.getName() + "'?");
            confirm.setContentText("This cannot be undone.");
            confirm.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
                ds.deleteCategory(sub.getId());
                buildCategoryGroup(groupSection, type, heading);
            });
            return;
        }

        new ReassignCategoryDialog(sub, type, usage, "Reassign & Delete", () -> {
            ds.deleteCategory(sub.getId());
            buildCategoryGroup(groupSection, type, heading);
        }).show();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }

}
