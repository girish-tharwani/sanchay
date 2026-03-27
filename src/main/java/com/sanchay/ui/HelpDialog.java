package com.sanchay.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

/** Popup dialog showing the "Get Started" setup guide, opened from the Help sidebar button. */
public class HelpDialog {

    private final Window owner;

    public HelpDialog(Window owner) {
        this.owner = owner;
    }

    public void show() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.initOwner(owner);
        dlg.setTitle("Help — Sanchay");
        dlg.getDialogPane().setPrefWidth(460);
        UiUtils.applyStylesheet(dlg);

        // Custom header: icon box + title
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header-bar");
        Label iconBox = new Label("?");
        iconBox.getStyleClass().add("dialog-icon-box");
        Label titleLbl = new Label("Help — Sanchay");
        titleLbl.getStyleClass().add("text-step-title");
        header.getChildren().addAll(iconBox, titleLbl);
        dlg.getDialogPane().setHeader(header);

        // Body
        VBox body = new VBox();
        body.setPadding(new Insets(24, 22, 20, 22));

        // Hero row
        HBox hero = new HBox(10);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(0, 0, 6, 0));
        Label heroIcon = new Label("🚀");
        heroIcon.setStyle("-fx-font-size: 22px;"); // single-use emoji size, no CSS utility class
        Label heroTitle = new Label("Getting started");
        heroTitle.getStyleClass().add("text-heading-lg");
        hero.getChildren().addAll(heroIcon, heroTitle);

        Label intro = new Label("Before you start recording transactions, complete these three steps in order:");
        intro.getStyleClass().add("text-body-muted");
        intro.setWrapText(true);
        intro.setPadding(new Insets(0, 0, 24, 0));

        // Steps
        VBox steps = UiUtils.buildGetStartedSteps();

        body.getChildren().addAll(hero, intro, steps);

        dlg.getDialogPane().setContent(body);

        // Close button; About Sanchay… injected into the button bar on the left
        ButtonType closeType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().add(closeType);

        Platform.runLater(() -> {
            javafx.scene.Node barNode = dlg.getDialogPane().lookup(".button-bar");
            if (barNode instanceof ButtonBar bb) {
                Button aboutBtn = new Button("About Sanchay…");
                aboutBtn.getStyleClass().add("link-teal");
                // Inline required: this button needs underline + 13px to distinguish it
                // from action buttons; the base link-teal class only sets colour/cursor
                aboutBtn.setStyle("-fx-font-size: 13px; -fx-underline: true; -fx-padding: 4 0;");
                aboutBtn.setOnAction(e -> {
                    SplashScreen about = new SplashScreen();
                    about.show();
                    about.closeAndThen(null);
                });
                ButtonBar.setButtonData(aboutBtn, ButtonBar.ButtonData.LEFT);
                bb.getButtons().add(0, aboutBtn);
            }
        });

        dlg.showAndWait();
    }

}
