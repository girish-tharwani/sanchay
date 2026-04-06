package com.sanchay.ui.help;

import com.sanchay.service.AppConfig;
import com.sanchay.ui.MainWindow;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Help &amp; Support screen — dismissable setup banner + full rendered user guide.
 *
 * Layout note: WebView must NOT be placed inside a ScrollPane. JavaFX's ScrollPane
 * applies an internal CacheFilter to its viewport, and compositing a WebView through
 * that cache pipeline crashes the WebKit renderer (RTTexture NPE in JavaFX 21).
 * The guide section therefore uses no outer ScrollPane; the WebView grows to fill
 * the remaining screen height and handles scrolling internally via its own scrollbar.
 */
public class HelpScreen {

    private final MainWindow mainWindow;
    private boolean bannerDismissed = false;
    private VBox view;

    public HelpScreen(MainWindow mainWindow) {
        this.mainWindow = mainWindow;
        buildView();
    }

    public Node getView() { return view; }

    private void buildView() {
        view = new VBox(20);
        view.getStyleClass().add("main-panel");
        view.setPadding(new Insets(28));
        view.setMaxHeight(Double.MAX_VALUE);
        view.setFillWidth(true);

        Label title = new Label("Help & Support");
        title.getStyleClass().add("screen-title");
        view.getChildren().add(title);

        if (!bannerDismissed) {
            view.getChildren().add(buildGetStartedCard());
        }

        view.getChildren().add(buildAboutSection());

        VBox guideSection = buildGuideSection();
        VBox.setVgrow(guideSection, Priority.ALWAYS);
        view.getChildren().add(guideSection);
    }

    // ── Get Started banner ────────────────────────────────────────────────────

    private VBox buildGetStartedCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card-welcome");

        HBox heading = new HBox(10);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🚀");
        icon.getStyleClass().add("help-banner-icon");
        Label titleLbl = new Label("Welcome — let's get you set up");
        titleLbl.getStyleClass().add("text-step-title");
        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        Button dismiss = new Button("Got it — I'll start now");
        dismiss.getStyleClass().add("btn-gold");
        dismiss.setOnAction(e -> {
            bannerDismissed = true;
            if (card.getParent() instanceof VBox p) p.getChildren().remove(card);
        });
        heading.getChildren().addAll(icon, titleLbl, headSpacer, dismiss);

        Label intro = new Label(
                "Before you start recording transactions, complete these three steps in order:");
        intro.getStyleClass().add("text-body-muted");
        intro.setWrapText(true);

        VBox steps = UiUtils.buildGetStartedSteps();

        card.getChildren().addAll(heading, intro, steps);
        return card;
    }

    // ── About ─────────────────────────────────────────────────────────────────

    private VBox buildAboutSection() {
        VBox section = new VBox(8);
        HBox labelRow = new HBox(8);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        Circle dot = new Circle(4, Color.web("#3db89a"));
        Label heading = new Label("ABOUT");
        heading.getStyleClass().add("section-group-label");
        labelRow.getChildren().addAll(dot, heading);

        VBox card = new VBox(12);
        card.getStyleClass().add("table-card");
        card.setPadding(new Insets(16));

        VBox box = new VBox(6);
        addInfo(box, "Application",  "Sanchay - Personal Finance Manager");
        addInfo(box, "Developed by", "Girish Tharwani");
        addInfo(box, "Version",      AppConfig.getAppBuild() + " - " + AppConfig.getAppVersion());
        addInfo(box, "Platform",     "Windows 11+ / JavaFX");
        card.getChildren().add(box);
        section.getChildren().addAll(labelRow, card);
        return section;
    }

    private void addInfo(VBox box, String label, String value) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(label + ":");
        lbl.setMinWidth(130);
        lbl.getStyleClass().add("text-form-value");
        Label val = new Label(value);
        val.getStyleClass().add("text-body-muted");
        row.getChildren().addAll(lbl, val);
        box.getChildren().add(row);
    }

    // ── User guide section ────────────────────────────────────────────────────

    private VBox buildGuideSection() {
        VBox section = new VBox(8);
        VBox.setVgrow(section, Priority.ALWAYS);

        HBox labelRow = new HBox(8);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        Circle dot = new Circle(4, Color.web("#3db89a"));
        Label heading = new Label("USER GUIDE");
        heading.getStyleClass().add("section-group-label");
        labelRow.getChildren().addAll(dot, heading);

        VBox card = new VBox();
        card.getStyleClass().add("help-guide-card");
        VBox.setVgrow(card, Priority.ALWAYS);

        VBox rendered = MarkdownRenderer.render(loadGuideContent());
        ScrollPane scroll = new ScrollPane(rendered);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("guide-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        card.getChildren().add(scroll);
        section.getChildren().addAll(labelRow, card);
        return section;
    }

    private String loadGuideContent() {
        try (InputStream is = getClass().getResourceAsStream("/com/sanchay/help/USER-GUIDE.md")) {
            if (is == null) return "# User guide not found";
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "# Error\n\nCould not load user guide: " + e.getMessage();
        }
    }
}
