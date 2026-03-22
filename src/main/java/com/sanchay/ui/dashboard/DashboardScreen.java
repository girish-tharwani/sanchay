package com.sanchay.ui.dashboard;

import com.sanchay.model.RecurringTransaction;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.ui.MainWindow;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Dashboard screen. */
public class DashboardScreen {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final MainWindow mainWindow;
    private final boolean showWelcomeBanner;
    private boolean bannerDismissed = false;
    private ScrollPane view;

    /** Held as a field so pending items can be refreshed in-place after Record/Skip. */
    private VBox pendingContainer;

    public DashboardScreen(MainWindow mainWindow, boolean showWelcomeBanner) {
        this.mainWindow = mainWindow;
        this.showWelcomeBanner = showWelcomeBanner;
        buildView();
    }

    public Node getView() { return view; }
    public void refresh() { buildView(); }

    private void buildView() {
        VBox content = new VBox(20);
        content.getStyleClass().add("main-panel");
        content.setPadding(new Insets(24));

        Label title = new Label("Dashboard");
        title.getStyleClass().add("screen-title");
        content.getChildren().add(title);

        if (showWelcomeBanner && !bannerDismissed) {
            content.getChildren().add(buildGetStartedCard());
        }

        content.getChildren().addAll(
                buildSummaryRow(),
                buildCreditCardRow(),
                buildPendingCard(),
                buildRecentTransactions()
        );

        view = new ScrollPane(content);
        view.setFitToWidth(true);
        view.setStyle("-fx-background-color: #F5F6FA; -fx-background: #F5F6FA;");
    }

    // ── Get Started card (shown on genuine first run) ─────────────────────────

    private VBox buildGetStartedCard() {
        VBox card = new VBox(16);
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setStyle("-fx-background-color: #EAF4FB; -fx-border-color: #1F4E79; "
                + "-fx-border-radius: 8; -fx-background-radius: 8; -fx-border-width: 1.5;");

        // Heading row
        HBox heading = new HBox(10);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🚀");
        icon.setStyle("-fx-font-size: 20px;");
        Label title = new Label("Welcome — let's get you set up");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1F4E79;");
        Region headSpacer = new Region();
        HBox.setHgrow(headSpacer, Priority.ALWAYS);
        Button dismiss = new Button("Got it — I'll start now");
        dismiss.setStyle("-fx-background-color: #1ABC9C; -fx-text-fill: white; "
                + "-fx-font-size: 12px; -fx-font-weight: bold; "
                + "-fx-padding: 6 16; -fx-background-radius: 5; -fx-cursor: hand;");
        dismiss.setOnAction(e -> {
            bannerDismissed = true;
            if (card.getParent() instanceof VBox p) p.getChildren().remove(card);
        });
        heading.getChildren().addAll(icon, title, headSpacer, dismiss);

        // Intro
        Label intro = new Label(
                "Before you start recording transactions, complete these three steps in order:");
        intro.setStyle("-fx-font-size: 13px; -fx-text-fill: #2C3E50;");
        intro.setWrapText(true);

        // Steps
        VBox steps = new VBox(10);
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

        card.getChildren().addAll(heading, intro, steps);
        return card;
    }

    // ── Summary row ───────────────────────────────────────────────────────────

    private HBox buildSummaryRow() {
        DataStore ds = DataStore.getInstance();
        HBox row = new HBox(16);
        row.setFillHeight(true);
        row.getChildren().addAll(
                summaryCard("Net Worth",        fmtInr(ds.getNetWorthPaise())),
                summaryCard("Bank Balance",     fmtInr(ds.getTotalBankBalancePaise())),
                summaryCard("Monthly Expenses", fmtInr(ds.getMonthlyExpensesPaise())),
                summaryCard("Monthly Income",   fmtInr(ds.getMonthlyIncomePaise()))
        );
        return row;
    }

    private VBox buildCreditCardRow() {
        DataStore ds = DataStore.getInstance();
        long ccOutstanding = ds.getTotalCreditCardOutstandingPaise();
        long loanCount     = ds.getActiveLoanAccounts().size();

        HBox row = new HBox(16);
        row.getChildren().addAll(
                summaryCard("Credit Card Balance", fmtInr(ccOutstanding)),
                summaryCard("Active Loans",        String.valueOf(loanCount))
        );
        return new VBox(row);
    }

    private VBox summaryCard(String label, String value) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMinWidth(150);

        Label lbl = new Label(label);
        lbl.getStyleClass().add("card-title");

        Label val = new Label(value);
        val.getStyleClass().add("card-value");

        card.getChildren().addAll(lbl, val);
        return card;
    }

    // ── Pending recurring card ────────────────────────────────────────────────

    private VBox buildPendingCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label heading = new Label("⏰  Pending Recurring Transactions");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Hyperlink viewAll = new Hyperlink("View All →");
        viewAll.setOnAction(e -> mainWindow.navigateToRecurring());
        header.getChildren().addAll(heading, spacer, viewAll);

        pendingContainer = new VBox(8);
        pendingContainer.setStyle(
                "-fx-background-color: #F5F5F5; -fx-background-radius: 6; -fx-padding: 8;");
        buildPendingItems(pendingContainer);

        card.getChildren().addAll(header, pendingContainer);
        return card;
    }

    private void buildPendingItems(VBox container) {
        container.getChildren().clear();
        List<RecurringTransaction> pending = DataStore.getInstance().getPendingRecurring();

        if (pending.isEmpty()) {
            Label none = new Label("✅  No pending transactions. You're all caught up!");
            none.setStyle("-fx-text-fill: #27AE60; -fx-padding: 4 0;");
            container.getChildren().add(none);
        } else {
            for (RecurringTransaction r : pending) {
                container.getChildren().add(buildPendingItem(r, container));
            }
        }
    }

    private HBox buildPendingItem(RecurringTransaction r, VBox container) {
        HBox item = new HBox(12);
        item.getStyleClass().add("pending-item");
        item.setAlignment(Pos.CENTER_LEFT);

        Label typeBadge = new Label(UiUtils.badgeText(r.getTransactionType()));
        typeBadge.getStyleClass().add(UiUtils.badgeStyle(r.getTransactionType()));

        Label desc = new Label(r.getDescription());
        desc.setStyle("-fx-font-weight: bold;");
        Label due = new Label("Due: " + (r.getNextDueDate() != null
                ? r.getNextDueDate().format(DATE_FMT) : "—"));
        due.setStyle("-fx-text-fill: #595959; -fx-font-size: 12px;");
        VBox details = new VBox(2, desc, due);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label amount = new Label(r.getAmountInr());
        amount.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");

        Button record = new Button("Record");
        record.getStyleClass().add("btn-primary");
        record.setStyle("-fx-font-size: 11px; -fx-padding: 4 12;");
        record.setOnAction(e -> mainWindow.recordRecurring(r, () -> buildPendingItems(container)));

        Button skip = new Button("Skip");
        skip.getStyleClass().add("btn-secondary");
        skip.setStyle("-fx-font-size: 11px; -fx-padding: 4 12;");
        skip.setOnAction(e -> mainWindow.skipRecurring(r, () -> buildPendingItems(container)));

        item.getChildren().addAll(typeBadge, details, spacer, amount, record, skip);
        return item;
    }

    // ── Recent transactions ───────────────────────────────────────────────────

    private VBox buildRecentTransactions() {
        VBox section = new VBox(10);
        section.getStyleClass().add("card");

        Label heading = new Label("Recent Transactions");
        heading.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1F4E79;");
        section.getChildren().add(heading);

        List<Transaction> recent = DataStore.getInstance().getRecentTransactions(10);
        if (recent.isEmpty()) {
            Label none = new Label("No transactions yet. Use the + button to record your first transaction.");
            none.setStyle("-fx-text-fill: #9E9E9E; -fx-font-style: italic;");
            section.getChildren().add(none);
            return section;
        }

        for (Transaction t : recent) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(4, 0, 4, 0));

            Label typeBadge = new Label(UiUtils.badgeText(t.getType()));
            typeBadge.getStyleClass().add(UiUtils.badgeStyle(t.getType()));

            Label dateL = new Label(t.getDate().format(DATE_FMT));
            dateL.setStyle("-fx-font-size: 12px; -fx-text-fill: #595959;");
            dateL.setMinWidth(100);

            Label descL = new Label(t.getDescription());
            descL.setStyle("-fx-font-size: 12px;");
            descL.setMinWidth(500);
            HBox.setHgrow(descL, Priority.ALWAYS);

            Label amtL = new Label(t.getAmountInr());
            amtL.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1F4E79;");
            amtL.setMinWidth(100);
            amtL.setPrefWidth(100);
            amtL.setAlignment(Pos.CENTER_RIGHT);
            HBox.setHgrow(amtL, Priority.NEVER);

            row.getChildren().addAll(typeBadge, dateL, descL, amtL);
            section.getChildren().add(row);
        }
        return section;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fmtInr(long paise) {
        return String.format("₹%,.2f", paise / 100.0);
    }

}