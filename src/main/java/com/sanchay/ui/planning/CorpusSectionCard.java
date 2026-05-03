package com.sanchay.ui.planning;

import com.sanchay.service.FinancialPlanningCalculator;
import com.sanchay.service.MoneyFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

/** Builds the Current Corpus breakdown card. */
class CorpusSectionCard {

    Region build(FinancialPlanningCalculator.CorpusBreakdown corpus) {
        VBox card = startSectionCard("Current Corpus", "-brand-mid");

        addTableRow(card, "Bank Accounts",      fmt(corpus.bankPaise()),   false, false);
        addTableRow(card, "Equities",           fmt(corpus.equityPaise()), false, false);
        addTableRow(card, "Mutual Funds",       fmt(corpus.mfPaise()),     false, false);
        addTableRow(card, "Bonds",              fmt(corpus.bondsPaise()),  false, false);
        addTableRow(card, "Fixed Deposits",     fmt(corpus.fdPaise()),     false, false);
        addTableRow(card, "Recurring Deposits", fmt(corpus.rdPaise()),     false, false);
        addTableRow(card, "Provident Fund",     fmt(corpus.pfPaise()),     false, false);
        addTableRow(card, "Total Current Corpus",       fmt(corpus.totalPaise()),  true,  false);

        addTableCommentRow(card, "* Bank accounts amount excluding credit card balances");
        addTableCommentRow(card, "* Equities and Mutual Funds valued at 90% of last recorded market value");
        addTableCommentRow(card, "* Bonds, FDs and RDs valued as per their invested amount");
        addTableCommentRow(card, "* Provident Fund as per account balance");

        return card;
    }

    private VBox startSectionCard(String title, String dotColor) {
        Circle dot = new Circle(4);
        // Inline required: Shape.fill cannot be set via CSS class; colour is data-driven
        dot.setStyle("-fx-fill: " + dotColor + ";");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("text-section-title");

        HBox header = new HBox(8, dot, titleLbl);
        header.setAlignment(Pos.CENTER_LEFT);

        Region divider = new Region();
        divider.getStyleClass().add("content-divider");
        divider.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(0, header, divider);
        card.getStyleClass().add("card");
        VBox.setMargin(header,  new Insets(0, 0, 8,  0));
        VBox.setMargin(divider, new Insets(0, 0, 10, 0));
        return card;
    }

    private void addTableRow(VBox parent, String label, String value,
                              boolean total, boolean negative) {
        HBox row = new HBox();
        row.getStyleClass().add(total ? "fp-table-row-total" : "fp-table-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label lblNode = new Label(label);
        lblNode.getStyleClass().add(total ? "fp-table-label-total" : "fp-table-label");
        lblNode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblNode, Priority.ALWAYS);

        Label valNode = new Label(value);
        valNode.getStyleClass().add("fp-table-value");
        if (total)    valNode.getStyleClass().add("fp-table-value-total");
        if (negative) valNode.getStyleClass().add("fp-table-value-negative");
        valNode.setMinWidth(Label.USE_PREF_SIZE);

        row.getChildren().addAll(lblNode, valNode);
        parent.getChildren().add(row);
    }

    private void addTableCommentRow(VBox parent, String label) {
        HBox row = new HBox();
        row.getStyleClass().add("fp-table-row-comment");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        Label lblNode = new Label(label);
        lblNode.getStyleClass().add("fp-table-label-comment");
        lblNode.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(lblNode, Priority.ALWAYS);

        row.getChildren().add(lblNode);
        parent.getChildren().add(row);
    }

    private static String fmt(long paise) {
        return MoneyFormatter.formatNoDecimal(paise);
    }
}
