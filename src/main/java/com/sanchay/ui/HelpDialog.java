package com.sanchay.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextFlow;
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

        // Steps with dividers
        VBox steps = buildSteps();

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

    private VBox buildSteps() {
        VBox steps = new VBox(0);

        steps.getChildren().addAll(
                stepRow("1", "Add family members",
                        UiUtils.stepDescFlow(
                                "Go to ", UiUtils.navHint("Profile"), arrow(),
                                UiUtils.navHint("+ Add Member"),
                                ". Add everyone in your household. Don't mark anyone as an "
                                + "earning member yet — you'll do that in step 3.")),
                divider(),
                stepRow("2", "Add your bank accounts",
                        UiUtils.stepDescFlow(
                                "Go to ", UiUtils.navHint("Accounts"), arrow(),
                                UiUtils.navHint("Bank Accounts"), arrow(),
                                UiUtils.navHint("+ Add"),
                                ". Add the accounts where salaries and income are deposited. "
                                + "You can add credit cards and loans later.")),
                divider(),
                stepRow("3", "Complete earnings details",
                        UiUtils.stepDescFlow(
                                "Return to ", UiUtils.navHint("Profile"),
                                " and click the ", UiUtils.navHint("₹"),
                                " button next to each earning member. Mark them as earning "
                                + "and fill in their salary and income details."))
        );

        return steps;
    }

    private HBox stepRow(String number, String title, TextFlow desc) {
        HBox row = UiUtils.buildStep(number, title, desc);
        row.setPadding(new Insets(16, 0, 16, 0));
        return row;
    }

    private Region divider() {
        Region sep = new Region();
        sep.getStyleClass().add("content-divider");
        return sep;
    }

    private javafx.scene.text.Text arrow() {
        javafx.scene.text.Text t = new javafx.scene.text.Text(" → ");
        // Text nodes don't support style classes for -fx-fill; inline style required.
        t.setStyle("-fx-fill: -text-muted; -fx-font-size: 11px;");
        return t;
    }
}
