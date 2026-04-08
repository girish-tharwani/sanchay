package com.sanchay.ui.planning;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;

/**
 * Reusable planning section card with a stable content body for refreshable rows.
 */
final class PlanningSectionCard {

    private final VBox root;
    private final VBox body;

    PlanningSectionCard(String title, String dotColor) {
        Circle dot = new Circle(4);
        dot.setStyle("-fx-fill: " + dotColor + ";");

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("text-section-title");

        HBox header = new HBox(8, dot, titleLbl);
        header.setAlignment(Pos.CENTER_LEFT);

        Region divider = new Region();
        divider.getStyleClass().add("content-divider");
        divider.setMaxWidth(Double.MAX_VALUE);

        body = new VBox(0);

        root = new VBox(0, header, divider, body);
        root.getStyleClass().add("card");
        VBox.setMargin(header, new Insets(0, 0, 8, 0));
        VBox.setMargin(divider, new Insets(0, 0, 10, 0));
        VBox.setVgrow(body, Priority.NEVER);
    }

    VBox getRoot() {
        return root;
    }

    VBox getBody() {
        return body;
    }
}
