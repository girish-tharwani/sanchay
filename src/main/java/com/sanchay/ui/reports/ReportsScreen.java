package com.sanchay.ui.reports;

import com.sanchay.service.DataStore;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/**
 * Reports module — two tabs.
 * Each tab is managed by its own class; this screen is a thin coordinator.
 */
public class ReportsScreen {

    private final StackPane view;
    private final ExpenseReportTab summaryTab;
    private final ExpenseTrendTab  trendTab;
    private final CashFlowForecastTab cashFlowTab;

    public ReportsScreen() {
        summaryTab  = new ExpenseReportTab();
        trendTab    = new ExpenseTrendTab();
        cashFlowTab = new CashFlowForecastTab();

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab t1 = new Tab("Expense Report");
        t1.setContent(summaryTab.getView());

        Tab t2 = new Tab("Expense Trend");
        t2.setContent(trendTab.getView());

        Tab t3 = new Tab("Cash Flow Forecast");
        t3.setContent(cashFlowTab.getView());

        tabPane.getTabs().addAll(t1, t2, t3);

        VBox panel = new VBox(0);
        panel.getStyleClass().add("main-panel");
        panel.setPadding(new Insets(24, 24, 0, 24));
        Label title = new Label("Reports");
        title.getStyleClass().add("screen-title");
        title.setPadding(new Insets(0, 0, 16, 0));
        panel.getChildren().addAll(title, tabPane);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        view = new StackPane(panel);
    }

    public Node getView() { return view; }

    /** Re-queries DataStore and redraws all tabs. Called by MainWindow on every navigation. */
    public void refresh() {
        summaryTab.refresh();
        trendTab.refresh();
        cashFlowTab.refresh();
    }
}
