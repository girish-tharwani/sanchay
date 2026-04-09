package com.sanchay.ui.help;

import com.sanchay.service.AppConfig;
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
 * Help &amp; Support screen — About card + full rendered user guide.
 *
 * Layout note: WebView must NOT be placed inside a ScrollPane. JavaFX's ScrollPane
 * applies an internal CacheFilter to its viewport, and compositing a WebView through
 * that cache pipeline crashes the WebKit renderer (RTTexture NPE in JavaFX 21).
 * The guide section therefore uses no outer ScrollPane; the WebView grows to fill
 * the remaining screen height and handles scrolling internally via its own scrollbar.
 */
public class HelpScreen {

    private VBox view;

    public HelpScreen() {
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

        view.getChildren().add(buildAboutSection());

        VBox guideSection = buildGuideSection();
        VBox.setVgrow(guideSection, Priority.ALWAYS);
        view.getChildren().add(guideSection);
    }

    // ── Quick Start dialog ────────────────────────────────────────────────────

    private void showQuickStartDialog() {
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Quick Start", "🚀", 520);

        Label intro = new Label(
                "Before you start recording transactions, complete these three steps in order:");
        intro.setPadding(new Insets(0, 0, 0, 12));
        intro.getStyleClass().add("text-body-muted");
        intro.setWrapText(true);

        VBox content = new VBox(14, intro, UiUtils.buildGetStartedSteps());
        content.setPadding(new Insets(12, 6, 0, 0));
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
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
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        Button quickStartBtn = new Button("Quick Start");
        quickStartBtn.getStyleClass().add("btn-gold");
        quickStartBtn.setOnAction(e -> showQuickStartDialog());
        labelRow.getChildren().addAll(dot, heading, headerSpacer, quickStartBtn);

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
