package com.sanchay.ui.categories;

import com.sanchay.model.Category;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.SingleInputDialog;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.transactions.TransactionDialog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Categories screen.
 *
 * Sub-category transaction filtering uses a mutable ArrayList (not List.of()) because
 * many transactions have a null subCategoryId, and List.of().contains(null) throws NPE.
 */
public class CategoriesScreen {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

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
                javafx.scene.paint.Color.web("#3db89a"));
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

        Button expandBtn = new Button(subCats.isEmpty() ? "  " : "▶");
        expandBtn.getStyleClass().add("btn-icon");
        expandBtn.setMinWidth(24);
        if (subCats.isEmpty()) expandBtn.setDisable(true);
        expandBtn.setOnAction(e -> {
            expanded[0] = !expanded[0];
            subCatContainer.setVisible(expanded[0]);
            subCatContainer.setManaged(expanded[0]);
            expandBtn.setText(expanded[0] ? "▼" : "▶");
        });

        Label nameLbl = new Label(cat.getName());
        nameLbl.setMinWidth(180);
        // Inline required: active/inactive text colour and style are runtime data
        nameLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;"
                + (cat.isActive() ? " -fx-text-fill: -text-label;" : " -fx-text-fill: -text-hint; -fx-font-style: italic;"));

        Label statusLbl = new Label(cat.isActive() ? "Active" : "Inactive");
        statusLbl.getStyleClass().add(cat.isActive() ? "status-active" : "status-closed");

        Label subCountLbl = new Label(subCats.size() + " sub-categor" + (subCats.size() == 1 ? "y" : "ies"));
        subCountLbl.getStyleClass().add("text-hint");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button showTxnBtn = new Button("☰");
        showTxnBtn.getStyleClass().add("btn-icon");
        showTxnBtn.setTooltip(new Tooltip("Transactions"));
        showTxnBtn.setOnAction(e -> showTransactionsForCategory(cat));

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
                showReassignDialog(cat, type, usageCount, groupSection, heading,
                        "Reassign Transactions",
                        () -> buildCategoryGroup(groupSection, type, heading)));

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
            // Inline required: active/inactive text colour and style are runtime data
            nameLbl.setStyle("-fx-font-size: 12px;"
                    + (sub.isActive() ? " -fx-text-fill: -text-label;" : " -fx-text-fill: -text-hint; -fx-font-style: italic;"));

            Label statusLbl = new Label(sub.isActive() ? "Active" : "Inactive");
            statusLbl.getStyleClass().add(sub.isActive() ? "status-active" : "status-closed");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Button showTxnBtn = new Button("☰");
            showTxnBtn.getStyleClass().add("btn-icon");
            showTxnBtn.setTooltip(new Tooltip("Transactions"));
            showTxnBtn.setOnAction(e -> showTransactionsForCategory(sub));

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
                    showMoveSubCategoryDialog(sub, parent, type, groupSection, heading));

            MenuItem reassignItem = new MenuItem("Reassign Transactions →");
            reassignItem.setDisable(usage == 0);
            reassignItem.setOnAction(e ->
                    showReassignDialog(sub, type, usage, groupSection, heading,
                            "Reassign Transactions",
                            () -> buildCategoryGroup(groupSection, type, heading)));

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

    // ── Show Transactions dialog ───────────────────────────────────────────────

    private void showTransactionsForCategory(Category cat) {
        DataStore ds = DataStore.getInstance();

        List<String> catIds;
        if (cat.getParentId() == null) {
            List<String> subIds = ds.getCategories().stream()
                    .filter(c -> cat.getId().equals(c.getParentId()))
                    .map(Category::getId)
                    .toList();
            catIds = new ArrayList<>();
            catIds.add(cat.getId());
            catIds.addAll(subIds);
        } else {
            catIds = new ArrayList<>();
            catIds.add(cat.getId());
        }

        List<Transaction> txns = ds.getTransactions().stream()
                .filter(t -> {
                    String tCatId    = t.getClassification() != null ? t.getClassification().getCategoryId() : null;
                    String tSubCatId = t.getClassification() != null ? t.getClassification().getSubCategoryId() : null;
                    return catIds.contains(tCatId) || (tSubCatId != null && catIds.contains(tSubCatId));
                })
                .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                .toList();

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Transactions — " + cat.getName());
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(700);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "#", "Transactions — " + cat.getName());
        dlg.getDialogPane().setPrefHeight(500);

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));

        Label countLbl = new Label(txns.size() + " transaction" + (txns.size() == 1 ? "" : "s") + " in '" + cat.getName() + "'");
        countLbl.getStyleClass().add("text-section-title");

        if (txns.isEmpty()) {
            Label none = new Label("No transactions found for this category.");
            none.getStyleClass().add("text-empty");
            content.getChildren().addAll(countLbl, none);
        } else {
            TableView<Transaction> table = new TableView<>();
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            table.setPrefHeight(380);

            TableColumn<Transaction, String> dateCol = new TableColumn<>("DATE");
            dateCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().getDate().format(DATE_FMT)));
            dateCol.setPrefWidth(100);

            TableColumn<Transaction, String> descCol = new TableColumn<>("DESCRIPTION");
            descCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().getDescription()));
            descCol.setPrefWidth(200);
            descCol.setCellFactory(tc -> {
                TableCell<Transaction, String> cell = new TableCell<>() {
                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty); setText(empty || item == null ? null : item);
                    }
                };
                cell.getStyleClass().add("cell-desc");
                return cell;
            });

            TableColumn<Transaction, Void> typeCol = new TableColumn<>("TYPE");
            typeCol.setPrefWidth(90);
            typeCol.setCellFactory(tc -> new TableCell<>() {
                @Override protected void updateItem(Void item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                    setGraphic(UiUtils.typeBadge(getTableRow().getItem().getType()));
                }
            });

            TableColumn<Transaction, String> accountCol = new TableColumn<>("ACCOUNT");
            accountCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            ds.getAccountName(c.getValue().getFromAccountId())));
            accountCol.setPrefWidth(130);

            TableColumn<Transaction, String> subCatCol = new TableColumn<>("SUB-CATEGORY");
            subCatCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            ds.getCategoryName(c.getValue().getClassification() != null
                                    ? c.getValue().getClassification().getSubCategoryId() : null)));
            subCatCol.setPrefWidth(120);

            TableColumn<Transaction, String> amtCol = new TableColumn<>("AMOUNT");
            amtCol.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                        c.getValue().getTypedSignedAmountInr()));
            amtCol.setPrefWidth(100);
            amtCol.setCellFactory(tc -> {
                TableCell<Transaction, String> cell = new TableCell<>() {
                    @Override protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty); setText(empty || item == null ? null : item);
                    }
                };
                cell.getStyleClass().add("cell-amt");
                return cell;
            });

            final List<String> finalCatIds = catIds;
            Runnable refreshTable = () -> {
                List<Transaction> updated = ds.getTransactions().stream()
                        .filter(tx -> {
                            String txCatId    = tx.getClassification() != null ? tx.getClassification().getCategoryId() : null;
                            String txSubCatId = tx.getClassification() != null ? tx.getClassification().getSubCategoryId() : null;
                            return finalCatIds.contains(txCatId) || (txSubCatId != null && finalCatIds.contains(txSubCatId));
                        })
                        .sorted((a, b) -> b.getDate().compareTo(a.getDate()))
                        .toList();
                table.getItems().setAll(updated);
            };
            table.setRowFactory(tv -> {
                TableRow<Transaction> row = new TableRow<>();
                row.setOnMouseClicked(e -> {
                    if (e.getClickCount() == 2 && !row.isEmpty()) {
                        new TransactionDialog(row.getItem()).showAndWait().ifPresent(saved -> {
                            refreshTable.run();
                            Platform.runLater(() -> restoreCatSelection(table, saved.getId()));
                        });
                    }
                });
                return row;
            });
            table.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    Transaction sel = table.getSelectionModel().getSelectedItem();
                    if (sel != null) {
                        new TransactionDialog(sel).showAndWait().ifPresent(saved -> {
                            refreshTable.run();
                            Platform.runLater(() -> restoreCatSelection(table, saved.getId()));
                        });
                    }
                }
            });

            table.getColumns().addAll(dateCol, descCol, typeCol, accountCol, subCatCol, amtCol);
            table.getItems().addAll(txns);

            content.getChildren().addAll(countLbl, table, UiUtils.hintLabel("Double-click a row to edit"));
        }

        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("scroll-transparent");
        dlg.getDialogPane().setContent(sp);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }

    private static void restoreCatSelection(TableView<Transaction> table, String id) {
        for (int i = 0; i < table.getItems().size(); i++) {
            if (table.getItems().get(i).getId().equals(id)) {
                table.getSelectionModel().select(i);
                table.scrollTo(i);
                table.requestFocus();
                return;
            }
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
            showReassignDialog(cat, type, directUsage, groupSection, heading,
                    "Reassign & Delete", () -> {
                        subCats.forEach(sc -> ds.deleteCategory(sc.getId()));
                        ds.deleteCategory(cat.getId());
                        buildCategoryGroup(groupSection, type, heading);
                    });
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

        showReassignDialog(sub, type, usage, groupSection, heading,
                "Reassign & Delete", () -> {
                    ds.deleteCategory(sub.getId());
                    buildCategoryGroup(groupSection, type, heading);
                });
    }

    // ── Reassign transactions dialog ──────────────────────────────────────────

    /**
     * Shows a dialog to pick a replacement category/sub-category and bulk-reassign
     * all transactions from {@code source}.
     *
     * @param confirmLabel  Button label — "Reassign & Delete" when called from a
     *                      delete flow, "Reassign Transactions" for standalone use.
     * @param onComplete    Called after a successful reassignment. For delete flows
     *                      this should also call ds.deleteCategory(); for standalone
     *                      reassign it just rebuilds the group.
     */
    private void showReassignDialog(Category source, Category.CategoryType type,
                                     long usageCount, VBox groupSection, String heading,
                                     String confirmLabel, Runnable onComplete) {
        DataStore ds = DataStore.getInstance();

        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Reassign Transactions");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(440);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "→", "Reassign Transactions",
                "Reassign " + usageCount + " transaction"
                + (usageCount == 1 ? "" : "s") + " from '" + source.getName() + "' to:");

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(16));
        ColumnConstraints c1 = new ColumnConstraints(130);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

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

    // ── Move sub-category dialog ──────────────────────────────────────────────

    private void showMoveSubCategoryDialog(Category sub, Category currentParent,
                                            Category.CategoryType type,
                                            VBox groupSection, String heading) {
        DataStore ds = DataStore.getInstance();

        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Move Sub-category");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(400);
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "→", "Move Sub-category",
                "Move '" + sub.getName() + "' to a different parent category:");

        GridPane g = new GridPane();
        g.setHgap(12); g.setVgap(10);
        g.setPadding(new Insets(16));
        ColumnConstraints c1 = new ColumnConstraints(130);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

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

        dlg.getDialogPane().setContent(g);

        ButtonType confirmBtn = new ButtonType("Move", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(confirmBtn, ButtonType.CANCEL);

        Button confirmNode = (Button) dlg.getDialogPane().lookupButton(confirmBtn);
        confirmNode.setDisable(true);
        parentCb.valueProperty().addListener((obs, o, n) -> confirmNode.setDisable(n == null));

        dlg.setResultConverter(bt -> bt == confirmBtn);
        dlg.showAndWait().ifPresent(confirmed -> {
            if (!confirmed) return;
            ds.moveSubCategoryParent(sub.getId(), parentCb.getValue().getId());
            buildCategoryGroup(groupSection, type, heading);
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }

}
