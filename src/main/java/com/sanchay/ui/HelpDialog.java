package com.sanchay.ui;

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
        dlg.getDialogPane().setPrefWidth(560);
        dlg.getDialogPane().setPrefHeight(480);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dlg.getDialogPane().setContent(buildContent());
        dlg.showAndWait();
    }

    private ScrollPane buildContent() {
        VBox root = new VBox(24);
        root.setPadding(new Insets(8, 4, 8, 4));

        root.getChildren().add(buildGetStartedSection());
        root.getChildren().add(buildAboutRow());

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        return scroll;
    }

    // ── About ─────────────────────────────────────────────────────────────────

    private HBox buildAboutRow() {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 0, 0, 0));
        row.setStyle("-fx-border-color: #E0E0E0; -fx-border-width: 1 0 0 0;");

        Button aboutBtn = new Button("About Sanchay…");
        aboutBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #1F4E79; "
                + "-fx-font-size: 12px; -fx-cursor: hand; -fx-underline: true; -fx-padding: 4 0;");
        aboutBtn.setOnAction(e -> {
            SplashScreen about = new SplashScreen();
            about.show();
            about.closeAndThen(null);
        });

        row.getChildren().add(aboutBtn);
        return row;
    }

    // ── Get Started section ───────────────────────────────────────────────────

    private VBox buildGetStartedSection() {
        VBox section = new VBox(16);

        Label heading = new Label("🚀  Getting started");
        heading.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #1F4E79;");

        Label intro = new Label(
                "Before you start recording transactions, complete these three steps in order:");
        intro.setStyle("-fx-font-size: 13px; -fx-text-fill: #2C3E50;");
        intro.setWrapText(true);

        VBox steps = new VBox(12);
        steps.getChildren().addAll(
                UiUtils.buildStep("1", "Add family members",
                        "Go to Profile → + Add Member. Add everyone in your household. "
                        + "Don't mark anyone as an earning member yet — you'll do that in step 3."),
                UiUtils.buildStep("2", "Add your bank accounts",
                        "Go to Accounts → Bank Accounts → + Add. Add the accounts where "
                        + "salaries and income are deposited. You can add credit cards and loans later."),
                UiUtils.buildStep("3", "Complete earnings details",
                        "Return to Profile and click the ₹ button next to each earning member. "
                        + "Mark them as earning and fill in their salary and income details.")
        );

        section.getChildren().addAll(heading, intro, steps);
        return section;
    }

}
