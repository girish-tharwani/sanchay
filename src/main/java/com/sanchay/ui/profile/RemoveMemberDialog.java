package com.sanchay.ui.profile;

import com.sanchay.model.FamilyMember;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.StageStyle;

/**
 * Styled confirmation dialog for removing a family member.
 * Returns true if the user confirmed, false if cancelled.
 */
class RemoveMemberDialog {

    private final FamilyMember member;

    RemoveMemberDialog(FamilyMember member) {
        this.member = member;
    }

    boolean showAndWait() {
        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Remove Member");
        dlg.setHeaderText(null);
        dlg.getDialogPane().setPrefWidth(400);
        UiUtils.applyStylesheet(dlg);
        dlg.initStyle(StageStyle.UNDECORATED);

        // ── Header ────────────────────────────────────────────────────────────
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header-bar");

        Label iconLbl = new Label("🗑");
        iconLbl.getStyleClass().addAll("dialog-icon-box", "dialog-icon-box--danger");

        Label titleLbl = new Label("Remove Member");
        titleLbl.getStyleClass().add("text-step-title");

        header.getChildren().addAll(iconLbl, titleLbl);
        dlg.getDialogPane().setHeader(header);

        // ── Body ──────────────────────────────────────────────────────────────
        VBox body = new VBox(14);
        body.setPadding(new Insets(20, 22, 4, 22));

        // Warning row: big red icon + headline + sub-text
        HBox warnRow = new HBox(16);
        warnRow.setAlignment(Pos.TOP_LEFT);

        StackPane warnIcon = new StackPane(new Label("⚠"));
        warnIcon.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--error");

        VBox warnText = new VBox(4);
        Label headline = new Label("Remove \"" + member.getName() + "\"?");
        headline.getStyleClass().add("text-step-title");
        Label subtext = new Label("Existing transactions tagged to this member are not affected.");
        subtext.getStyleClass().add("text-body-muted");
        subtext.setWrapText(true);
        warnText.getChildren().addAll(headline, subtext);
        HBox.setHgrow(warnText, Priority.ALWAYS);

        warnRow.getChildren().addAll(warnIcon, warnText);

        // Member detail block (red-tinted card)
        VBox detailBlock = new VBox(3);
        detailBlock.getStyleClass().add("dialog-danger-block");

        Label nameLbl = new Label(member.getName());
        nameLbl.getStyleClass().add("text-step-title");

        String rel = formatRelationship(member.getRelationship());
        if (!rel.isBlank()) {
            Label relLbl = new Label(rel);
            relLbl.getStyleClass().add("text-body-muted");
            detailBlock.getChildren().addAll(nameLbl, relLbl);
        } else {
            detailBlock.getChildren().add(nameLbl);
        }

        body.getChildren().addAll(warnRow, detailBlock);
        dlg.getDialogPane().setContent(body);

        // ── Buttons ───────────────────────────────────────────────────────────
        ButtonType removeBtn = new ButtonType("Remove", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, removeBtn);
        dlg.getDialogPane().lookupButton(removeBtn).getStyleClass().add("btn-danger");

        dlg.setResultConverter(bt -> bt == removeBtn);

        return dlg.showAndWait().orElse(false);
    }

    private static String formatRelationship(FamilyMember.Relationship r) {
        if (r == null) return "";
        return switch (r) {
            case SELF    -> "Self";
            case SPOUSE  -> "Spouse";
            case CHILD   -> "Child";
            case PARENT  -> "Parent";
            case SIBLING -> "Sibling";
            case OTHER   -> "Other";
        };
    }
}
