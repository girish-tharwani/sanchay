package com.sanchay.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

/**
 * Startup splash screen shown while the application loads data.
 * Stays visible for at least MIN_DISPLAY_MS milliseconds, then fades out.
 */
public class SplashScreen {

    private static final long MIN_DISPLAY_MS = 2000;

    private final Stage stage;
    private long showTimeMs;

    public SplashScreen() {
        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false);
        stage.setScene(buildScene());
        stage.centerOnScreen();
    }

    public void show() {
        showTimeMs = System.currentTimeMillis();
        stage.show();
    }

    /**
     * Waits for the remainder of the minimum display period, plays a fade-out,
     * closes the splash stage, then calls onClosed on the JavaFX Application Thread.
     */
    public void closeAndThen(Runnable onClosed) {
        long elapsed  = System.currentTimeMillis() - showTimeMs;
        long remaining = Math.max(0, MIN_DISPLAY_MS - elapsed);

        PauseTransition wait = new PauseTransition(Duration.millis(remaining));
        wait.setOnFinished(e -> {
            FadeTransition fade = new FadeTransition(Duration.millis(400), stage.getScene().getRoot());
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(f -> {
                stage.close();
                if (onClosed != null) onClosed.run();
            });
            fade.play();
        });
        wait.play();
    }

    // ── Scene ─────────────────────────────────────────────────────────────────

    private Scene buildScene() {
        StackPane root = new StackPane();
        root.setPrefSize(480, 300);

        LinearGradient bg = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0f3d4a")),
                new Stop(1, Color.web("#1a6b5a")));
        root.setBackground(new Background(new BackgroundFill(bg, CornerRadii.EMPTY, Insets.EMPTY)));

        root.getChildren().addAll(buildDecorLayer(), buildContent());
        return new Scene(root, 480, 300);
    }

    // ── Decorative background circles ─────────────────────────────────────────

    private Pane buildDecorLayer() {
        Pane pane = new Pane();
        pane.setPrefSize(480, 300);
        pane.setMouseTransparent(true);

        addCircle(pane, 110, Color.web("#FFFFFF", 0.05), 430,  -30);   // top-right large
        addCircle(pane,  80, Color.web("#3db89a", 0.12), -20,  280);   // bottom-left medium
        addCircle(pane,  45, Color.web("#FFFFFF", 0.07),  55,   28);   // top-left small
        addCircle(pane,  65, Color.web("#3db89a", 0.09), 450,  290);   // bottom-right medium
        addCircle(pane,  30, Color.web("#F0B429", 0.10), 420,  210);   // gold accent right
        addCircle(pane,  20, Color.web("#FFFFFF", 0.08), 160,  270);   // bottom-centre small

        return pane;
    }

    private void addCircle(Pane pane, double radius, Color fill, double cx, double cy) {
        Circle c = new Circle(radius, fill);
        c.setCenterX(cx);
        c.setCenterY(cy);
        pane.getChildren().add(c);
    }

    // ── Main content ──────────────────────────────────────────────────────────

    private VBox buildContent() {
        VBox content = new VBox(8);
        content.setAlignment(Pos.CENTER);

        StackPane coin = buildCoinGraphic();
        VBox.setMargin(coin, new Insets(0, 0, 6, 0));

        Label appName = new Label("Sanchay");
        appName.setStyle("-fx-font-size: 40px; -fx-font-weight: bold; -fx-text-fill: white;");

        Label subtitle = new Label("Personal Finance");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: rgba(255,255,255,0.72);");

        Label tagline = new Label("Your household finances, organised.");
        tagline.setStyle("-fx-font-size: 11px; -fx-text-fill: rgba(255,255,255,0.40);");

        ProgressBar pb = buildProgressBar();
        VBox.setMargin(pb, new Insets(16, 0, 0, 0));

        content.getChildren().addAll(coin, appName, subtitle, tagline, pb);
        return content;
    }

    // ── Coin / rupee graphic ──────────────────────────────────────────────────

    private StackPane buildCoinGraphic() {
        StackPane coin = new StackPane();
        coin.setAlignment(Pos.CENTER);

        Circle glow = new Circle(40, Color.web("#F0B429", 0.18));

        Circle disc = new Circle(30, Color.web("#F0B429", 0.92));
        DropShadow shadow = new DropShadow(18, Color.web("#F0B429", 0.55));
        disc.setEffect(shadow);

        Label rupee = new Label("₹");
        rupee.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: white;");

        coin.getChildren().addAll(glow, disc, rupee);
        return coin;
    }

    // ── Progress bar ──────────────────────────────────────────────────────────

    private ProgressBar buildProgressBar() {
        ProgressBar pb = new ProgressBar(ProgressBar.INDETERMINATE_PROGRESS);
        pb.setPrefWidth(140);
        pb.setPrefHeight(4);
        pb.setStyle(
                "-fx-accent: rgba(255,255,255,0.50);" +
                "-fx-background-color: rgba(255,255,255,0.15);" +
                "-fx-background-radius: 2;" +
                "-fx-padding: 0;");
        return pb;
    }
}
