package com.financeapp.ui.profile;

import com.financeapp.model.FamilyMember;
import com.financeapp.model.RecurringTransaction;
import com.financeapp.service.DataStore;
import com.financeapp.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Profile screen — family members and earnings configuration. */
public class ProfileScreen {

    private ScrollPane view;

    public ProfileScreen() { buildView(); }

    public Node getView() { return view; }

    public void refresh() { buildView(); }

    private void buildView() {
        VBox content = new VBox(24);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(24));

        Label title = new Label("Profile");
        title.getStyleClass().add("screen-title");

        content.getChildren().addAll(
                title,
                buildSection("👨‍👩‍👧  Family Members", buildFamilyMembersPanel())
        );

        view = new ScrollPane(content);
        view.setFitToWidth(true);
        view.setStyle("-fx-background-color: #F5F6FA; -fx-background: #F5F6FA;");
    }

    // ── Section wrapper ───────────────────────────────────────────────────────

    private VBox buildSection(String heading, Node body) {
        VBox section = new VBox(12);
        Label h = new Label(heading);
        h.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.getChildren().add(body);
        section.getChildren().addAll(h, card);
        return section;
    }

    // ── Family Members ────────────────────────────────────────────────────────

    private VBox buildFamilyMembersPanel() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(4));

        Label desc = new Label(
                "Family members can be tagged to transactions for household attribution. "
                + "Names are available in the 'Tag to family member' field when recording transactions.");
        desc.setWrapText(true);
        desc.setStyle("-fx-text-fill: #595959; -fx-font-size: 12px;");
        box.getChildren().add(desc);

        refreshFamilyList(box);
        return box;
    }

    private void refreshFamilyList(VBox box) {
        // Remove everything except the description label (first child)
        while (box.getChildren().size() > 1)
            box.getChildren().remove(1);

        DataStore ds = DataStore.getInstance();
        List<FamilyMember> members = ds.getFamilyMembers();

        // ── Table ─────────────────────────────────────────────────────────────
        TableView<FamilyMember> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(Math.max(120, members.size() * 40 + 35));
        table.setPlaceholder(new Label("No family members added yet."));
        table.getItems().addAll(members);

        TableColumn<FamilyMember, String> nameCol = new TableColumn<>("Name");
        nameCol.setMinWidth(160);
        nameCol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));

        TableColumn<FamilyMember, String> dobCol = new TableColumn<>("Date of Birth");
        dobCol.setMinWidth(110);
        dobCol.setCellValueFactory(d -> {
            LocalDate dob = d.getValue().getDateOfBirth();
            String text = dob != null ? dob.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) : "—";
            return new javafx.beans.property.SimpleStringProperty(text);
        });

        TableColumn<FamilyMember, String> relCol = new TableColumn<>("Relationship");
        relCol.setMinWidth(120);
        relCol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        formatRelationship(d.getValue().getRelationship())));

        // Earning? checkbox column — toggles earning flag and handles schedule lifecycle
        TableColumn<FamilyMember, Void> earningChkCol = new TableColumn<>("Earning?");
        earningChkCol.setMinWidth(72);
        earningChkCol.setMaxWidth(72);
        earningChkCol.setCellFactory(tc -> new TableCell<>() {
            private final CheckBox chk = new CheckBox();
            {
                chk.setOnAction(e -> {
                    FamilyMember m = getTableRow().getItem();
                    if (m == null) return;
                    boolean wasEarning = m.isEarning();
                    boolean nowEarning = chk.isSelected();
                    m.setEarning(nowEarning);
                    ds.updateFamilyMember(m);
                    if (wasEarning && !nowEarning) {
                        setScheduleStatus(ds, m.getRecurringScheduleId(),
                                RecurringTransaction.Status.PAUSED);
                        setScheduleStatus(ds, m.getPfScheduleId(),
                                RecurringTransaction.Status.PAUSED);
                        ds.saveRecurringNow();
                    } else if (!wasEarning && nowEarning) {
                        RecurringTransaction sched =
                                ds.findRecurringById(m.getRecurringScheduleId());
                        if (sched != null) {
                            setScheduleStatus(ds, m.getRecurringScheduleId(),
                                    RecurringTransaction.Status.ACTIVE);
                            setScheduleStatus(ds, m.getPfScheduleId(),
                                    RecurringTransaction.Status.ACTIVE);
                            ds.saveRecurringNow();
                        }
                        // Earnings details are configured via the ₹ button — not launched here
                    }
                    refreshFamilyList(box);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                chk.setSelected(getTableRow().getItem().isEarning());
                setGraphic(chk);
            }
        });

        TableColumn<FamilyMember, String> earnCol = new TableColumn<>("Monthly In-hand");
        earnCol.setMinWidth(130);
        earnCol.setCellValueFactory(d -> {
            FamilyMember m = d.getValue();
            String text;
            if (!m.isEarning()) {
                text = "—";
            } else if (m.getEarningType() == null) {
                text = "Not configured";
            } else {
                long paise = m.computeInHandPaise();
                text = paise > 0 ? String.format("₹%,.0f", paise / 100.0) : "—";
            }
            return new javafx.beans.property.SimpleStringProperty(text);
        });

        // Double-click row to edit
        table.setRowFactory(tv -> {
            TableRow<FamilyMember> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openMemberForm(row.getItem());
                    refreshFamilyList(box);
                }
            });
            return row;
        });

        TableColumn<FamilyMember, Void> actionsCol = new TableColumn<>("");
        actionsCol.setMinWidth(36);
        actionsCol.setMaxWidth(36);
        actionsCol.setCellFactory(tc -> new TableCell<>() {
            private final Button removeBtn = new Button("×");
            {
                removeBtn.setStyle(
                        "-fx-background-color: #F5DADA; -fx-text-fill: #A93226; "
                        + "-fx-font-size: 12px; -fx-font-weight: bold; "
                        + "-fx-padding: 1 6; -fx-background-radius: 3; -fx-cursor: hand;");
                removeBtn.setTooltip(new Tooltip("Remove member"));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                FamilyMember m = getTableRow().getItem();
                removeBtn.setOnAction(e -> {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
                    confirm.setTitle("Remove Member");
                    confirm.setHeaderText("Remove '" + m.getName() + "'?");
                    confirm.setContentText(
                            "This removes the member from the list. "
                            + "Existing transactions tagged to this member are not affected.");
                    confirm.showAndWait().filter(b -> b == ButtonType.OK)
                            .ifPresent(b -> {
                                ds.removeFamilyMember(m.getId());
                                refreshFamilyList(box);
                            });
                });
                setGraphic(removeBtn);
            }
        });

        // ₹ Earnings button — visible only for earning members
        TableColumn<FamilyMember, Void> earningsCol = new TableColumn<>("");
        earningsCol.setMinWidth(36);
        earningsCol.setMaxWidth(36);
        earningsCol.setCellFactory(tc -> new TableCell<>() {
            private final Button btn = new Button("₹");
            {
                btn.setStyle("-fx-background-color: #E8F5E9; -fx-text-fill: #1B5E20; "
                        + "-fx-font-size: 12px; -fx-font-weight: bold; "
                        + "-fx-padding: 1 5; -fx-background-radius: 3; -fx-cursor: hand;");
                btn.setTooltip(new Tooltip("Configure earnings"));
                btn.setOnAction(e -> {
                    FamilyMember m = getTableRow().getItem();
                    if (m == null) return;
                    new EarningsDialog(m).showAndWait();
                    refreshFamilyList(box);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                setGraphic(getTableRow().getItem().isEarning() ? btn : null);
            }
        });

        table.getColumns().addAll(nameCol, dobCol, relCol, earningChkCol, earnCol, earningsCol, actionsCol);
        box.getChildren().add(table);
        box.getChildren().add(UiUtils.hintLabel("Double-click a row to edit"));

        // ── Add button ────────────────────────────────────────────────────────
        Button addBtn = new Button("+ Add Member");
        addBtn.getStyleClass().add("btn-secondary");
        addBtn.setOnAction(e -> {
            openMemberForm(null);
            refreshFamilyList(box);
        });
        box.getChildren().add(addBtn);
    }

    // ── Add / Edit dialog ─────────────────────────────────────────────────────

    private void openMemberForm(FamilyMember existing) {
        boolean isNew = (existing == null);
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle(isNew ? "Add Family Member" : "Edit Family Member");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(380);

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(12);
        g.setPadding(new Insets(16));
        ColumnConstraints c1 = new ColumnConstraints(120);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(c1, c2);

        // Name
        TextField nameFld = new TextField(isNew ? "" : existing.getName());
        nameFld.setPromptText("e.g. Rahul");
        nameFld.setMaxWidth(Double.MAX_VALUE);

        // Date of Birth
        DatePicker dobPicker = new DatePicker();
        dobPicker.setPromptText("dd/MM/yyyy");
        dobPicker.setMaxWidth(Double.MAX_VALUE);
        UiUtils.applySmartDateConverter(dobPicker);
        if (!isNew && existing.getDateOfBirth() != null) dobPicker.setValue(existing.getDateOfBirth());

        // Relationship
        ComboBox<FamilyMember.Relationship> relCb = new ComboBox<>();
        relCb.getItems().addAll(FamilyMember.Relationship.values());
        relCb.setValue(isNew ? defaultRelationship() : existing.getRelationship());
        relCb.setMaxWidth(Double.MAX_VALUE);
        relCb.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(FamilyMember.Relationship item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatRelationship(item));
            }
        });
        relCb.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(FamilyMember.Relationship item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatRelationship(item));
            }
        });

        // Earning checkbox
        CheckBox earningCb = new CheckBox();
        earningCb.setSelected(!isNew && existing.isEarning());

        formRow(g, 0, "Name*",        nameFld);
        formRow(g, 1, "Date of Birth", dobPicker);
        formRow(g, 2, "Relationship", relCb);
        formRow(g, 3, "Earning",      earningCb);

        dlg.getDialogPane().setContent(g);

        ButtonType saveBtn = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);

        boolean wasEarning = !isNew && existing.isEarning();

        dlg.setResultConverter(bt -> {
            if (bt != saveBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { alert("Validation", "Name is required."); return null; }

            DataStore ds = DataStore.getInstance();
            boolean nowEarning = earningCb.isSelected();
            FamilyMember saved;

            if (isNew) {
                FamilyMember m = new FamilyMember(name, relCb.getValue(), nowEarning);
                m.setDateOfBirth(dobPicker.getValue());
                if (!ds.addFamilyMember(m)) {
                    alert("Add Failed", "The name '" + name + "' already exists.");
                    return null;
                }
                saved = m;
            } else {
                existing.setName(name);
                existing.setRelationship(relCb.getValue());
                existing.setEarning(nowEarning);
                existing.setDateOfBirth(dobPicker.getValue());
                if (!ds.updateFamilyMember(existing)) {
                    alert("Update Failed", "The name '" + name + "' is already taken.");
                    return null;
                }
                saved = existing;
            }

            // ── Earning lifecycle ─────────────────────────────────────────────
            if (wasEarning && !nowEarning) {
                setScheduleStatus(ds, saved.getRecurringScheduleId(),
                        RecurringTransaction.Status.PAUSED);
                setScheduleStatus(ds, saved.getPfScheduleId(),
                        RecurringTransaction.Status.PAUSED);
                ds.saveRecurringNow();
            } else if (!wasEarning && nowEarning) {
                RecurringTransaction sched =
                        ds.findRecurringById(saved.getRecurringScheduleId());
                if (sched != null) {
                    setScheduleStatus(ds, saved.getRecurringScheduleId(),
                            RecurringTransaction.Status.ACTIVE);
                    setScheduleStatus(ds, saved.getPfScheduleId(),
                            RecurringTransaction.Status.ACTIVE);
                    ds.saveRecurringNow();
                }
                // Earnings details are configured via the ₹ button — not launched here
            }

            return null;
        });

        dlg.showAndWait();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static FamilyMember.Relationship defaultRelationship() {
        List<FamilyMember> existing = DataStore.getInstance().getFamilyMembers();
        boolean hasSelf   = existing.stream().anyMatch(m -> m.getRelationship() == FamilyMember.Relationship.SELF);
        boolean hasSpouse  = existing.stream().anyMatch(m -> m.getRelationship() == FamilyMember.Relationship.SPOUSE);
        if (!hasSelf)   return FamilyMember.Relationship.SELF;
        if (!hasSpouse) return FamilyMember.Relationship.SPOUSE;
        return FamilyMember.Relationship.CHILD;
    }

    private static void setScheduleStatus(DataStore ds, String scheduleId,
                                          RecurringTransaction.Status status) {
        RecurringTransaction s = ds.findRecurringById(scheduleId);
        if (s != null) s.setStatus(status);
    }

    private static String formatRelationship(FamilyMember.Relationship r) {
        if (r == null) return "—";
        return switch (r) {
            case SELF    -> "Self";
            case SPOUSE  -> "Spouse";
            case CHILD   -> "Child";
            case PARENT  -> "Parent";
            case SIBLING -> "Sibling";
            case OTHER   -> "Other";
        };
    }

    private void formRow(GridPane g, int rowIdx, String labelText, Node control) {
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #1A1A2E; -fx-font-size: 12px;");
        lbl.setMinWidth(115);
        g.add(lbl,     0, rowIdx);
        g.add(control, 1, rowIdx);
        GridPane.setFillWidth(control, true);
    }

    private void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}
