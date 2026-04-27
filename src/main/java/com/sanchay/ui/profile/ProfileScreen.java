package com.sanchay.ui.profile;

import com.sanchay.model.EarningSource;
import com.sanchay.model.FamilyMember;
import com.sanchay.model.RecurringTransaction;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
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
        view.getStyleClass().add("scroll-page-bg");
    }

    // ── Section wrapper ───────────────────────────────────────────────────────

    private VBox buildSection(String heading, Node body) {
        VBox section = new VBox(8);
        HBox labelRow = new HBox(8);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.shape.Circle dot = new javafx.scene.shape.Circle(4,
                javafx.scene.paint.Color.web(UiUtils.HEX_BRAND_LIGHT));
        Label h = new Label(heading.replaceAll("^[^\\w]*", "").toUpperCase());
        h.getStyleClass().add("section-group-label");
        labelRow.getChildren().addAll(dot, h);
        VBox card = new VBox(12);
        card.getStyleClass().add("table-card");
        card.setPadding(new Insets(16));
        card.getChildren().add(body);
        section.getChildren().addAll(labelRow, card);
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
        desc.getStyleClass().add("text-body-muted");
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
        table.setPrefHeight(Math.max(120, members.size() * 40 + 50));
        table.setPlaceholder(new Label("No family members added yet."));
        table.getItems().addAll(members);

        TableColumn<FamilyMember, String> nameCol = new TableColumn<>("NAME");
        nameCol.setMinWidth(200);
        nameCol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        nameCol.setCellFactory(col -> new TableCell<>() {
            { getStyleClass().add("cell-desc"); }
            @Override protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) { setGraphic(null); return; }
                // Avatar circle with initials
                String initials = getInitials(name);
                Circle bg = new Circle(16, avatarColor(name));
                Label initLbl = new Label(initials);
                initLbl.getStyleClass().add("avatar-initial");
                javafx.scene.layout.StackPane avatar = new javafx.scene.layout.StackPane(bg, initLbl);
                Label nameLbl = new Label(name);
                nameLbl.getStyleClass().add("text-step-title");
                HBox cell = new HBox(10, avatar, nameLbl);
                cell.setAlignment(Pos.CENTER_LEFT);
                setGraphic(cell);
                setText(null);
            }
        });

        TableColumn<FamilyMember, String> dobCol = new TableColumn<>("DATE OF BIRTH");
        dobCol.setMinWidth(110);
        dobCol.setCellValueFactory(d -> {
            LocalDate dob = d.getValue().getDateOfBirth();
            String text = dob != null ? dob.format(DataStore.getInstance().getDateFormatter()) : "—";
            return new javafx.beans.property.SimpleStringProperty(text);
        });

        TableColumn<FamilyMember, String> relCol = new TableColumn<>("RELATIONSHIP");
        relCol.setMinWidth(120);
        relCol.setCellValueFactory(d ->
                new javafx.beans.property.SimpleStringProperty(
                        formatRelationship(d.getValue().getRelationship())));

        // Earning? checkbox column — toggles earning flag and handles schedule lifecycle
        TableColumn<FamilyMember, Void> earningChkCol = new TableColumn<>("EARNING?");
        earningChkCol.setMinWidth(72);
        earningChkCol.setMaxWidth(90);
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
                        pauseOrResumeAllSchedules(ds, m, RecurringTransaction.Status.PAUSED);
                    } else if (!wasEarning && nowEarning) {
                        pauseOrResumeAllSchedules(ds, m, RecurringTransaction.Status.ACTIVE);
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

        TableColumn<FamilyMember, String> earnCol = new TableColumn<>("MONTHLY IN-HAND");
        earnCol.setMinWidth(130);
        earnCol.setCellValueFactory(d -> {
            FamilyMember m = d.getValue();
            String text;
            if (!m.isEarning()) {
                text = "—";
            } else if (!m.hasEarningsConfigured()) {
                text = "Not configured";
            } else {
                long paise = m.computeInHandPaise();
                text = paise > 0 ? MoneyFormatter.formatNoDecimal(paise) : "—";
            }
            return new javafx.beans.property.SimpleStringProperty(text);
        });
        earnCol.setCellFactory(tc -> {
            TableCell<FamilyMember, String> cell = new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty); setText(empty || item == null ? null : item);
                }
            };
            cell.getStyleClass().add("cell-amt");
            return cell;
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
                removeBtn.getStyleClass().add("btn-row-remove");
                removeBtn.setMinSize(22, 22); removeBtn.setMaxSize(22, 22);
                removeBtn.setTooltip(new Tooltip("Remove member"));
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) { setGraphic(null); return; }
                FamilyMember m = getTableRow().getItem();
                removeBtn.setOnAction(e -> {
                    if (new RemoveMemberDialog(m).showAndWait()) {
                        ds.removeFamilyMember(m.getId());
                        refreshFamilyList(box);
                    }
                });
                setGraphic(removeBtn);
            }
        });

        // ₹ Earnings button — visible only for earning members
        TableColumn<FamilyMember, Void> earningsCol = new TableColumn<>("");
        earningsCol.setMinWidth(36);
        earningsCol.setMaxWidth(36);
        earningsCol.setCellFactory(tc -> new TableCell<>() {
            private final Button btn = new Button(MoneyFormatter.symbol());
            {
                btn.getStyleClass().add("btn-row-add");
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
        addBtn.getStyleClass().add("btn-gold");
        addBtn.setOnAction(e -> {
            openMemberForm(null);
            refreshFamilyList(box);
        });
        box.getChildren().add(addBtn);
    }

    // ── Add / Edit dialog ─────────────────────────────────────────────────────

    private void openMemberForm(FamilyMember existing) {
        boolean isNew = (existing == null);
        String dlgTitle = isNew ? "Add Family Member" : "Edit Family Member";
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, dlgTitle, isNew ? "+" : "✎", 380);

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
        DatePicker dobPicker = UiUtils.createDatePicker(null);
        dobPicker.setPromptText("dd/MM/yyyy");
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
                pauseOrResumeAllSchedules(ds, saved, RecurringTransaction.Status.PAUSED);
            } else if (!wasEarning && nowEarning) {
                pauseOrResumeAllSchedules(ds, saved, RecurringTransaction.Status.ACTIVE);
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

    private static void pauseOrResumeAllSchedules(DataStore ds, FamilyMember m,
                                                   RecurringTransaction.Status status) {
        if (!m.hasEarningsConfigured()) return;
        for (EarningSource src : m.getEarningSources()) {
            setScheduleStatus(ds, src.getRecurringScheduleId(), status);
            setScheduleStatus(ds, src.getPfScheduleId(), status);
            setScheduleStatus(ds, src.getEsppScheduleId(), status);
        }
        ds.saveRecurringNow();
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
        lbl.getStyleClass().add("form-label");
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

    // ── Avatar helpers ────────────────────────────────────────────────────────

    private static String getInitials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (String.valueOf(parts[0].charAt(0)) + parts[parts.length - 1].charAt(0)).toUpperCase();
    }

    private static Color avatarColor(String name) {
        // Shape.fill cannot be set via style class; deterministic colour from name hash stays inline
        String[] colors = { "#4169a1", "#3db89a", "#10745b", "#4a7a88", "#dda132",
                             "#5b7fa6", "#7b5ea7", "#0f3d4a", "#b2db1c", "#ee4083" };
        int hash = name.toLowerCase().chars().reduce(0, (acc, c) -> acc * 31 + c);
        return Color.web(colors[Math.abs(hash) % colors.length]);
    }
}
