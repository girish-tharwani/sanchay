package com.sanchay.ui.common;

import com.sanchay.ui.SplashScreen;
import com.sanchay.ui.UiUtils;
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
        UiUtils.initDialog(dlg, "Help — Sanchay", "?", 460);

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
                aboutBtn.getStyleClass().addAll("link-teal", "text-link-button");
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
