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
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Dashboard screen — matches UI-Reimagined/dashboard.html */
public class DashboardScreen {

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter HEADER_FMT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

    // Accent stripe colours for stat cards
    private static final String STRIPE_TEAL  = "#3db89a";
    private static final String STRIPE_GOLD  = "#f0a500";
    private static final String STRIPE_RED   = "#e05555";

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
        content.setPadding(new Insets(28, 28, 28, 28));

        // ── Page header: title + date ─────────────────────────────────────────
        Label title = new Label("Dashboard");
        title.getStyleClass().add("screen-title");

        Label dateLabel = new Label(LocalDate.now().format(HEADER_FMT));
        dateLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #7aa4b0;");

        VBox titleGroup = new VBox(2, title, dateLabel);
        content.getChildren().add(titleGroup);

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
        view.setStyle("-fx-background-color: #eef4f5; -fx-background: #eef4f5;");
    }

    // ── Get Started card ──────────────────────────────────────────────────────

    private VBox buildGetStartedCard() {
        VBox card = new VBox(16);
        card.setPadding(new Insets(20, 24, 20, 24));
        card.setStyle(
                "-fx-background-color: #3db89a, white; " +
                "-fx-background-insets: 0, 0 0 0 4; " +
                "-fx-background-radius: 12, 12; " +
                "-fx-border-color: rgba(42,138,122,0.25); " +
                "-fx-border-radius: 12; -fx-border-width: 1;");

        HBox heading = new HBox(10);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🚀");
        icon.setStyle("-fx-font-size: 20px;");
        Label titleLbl = new Label("Welcome — let's get you set up");
        titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #0f3d4a;");
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
        intro.setStyle("-fx-font-size: 13px; -fx-text-fill: #4a7a88;");
        intro.setWrapText(true);

        VBox steps = new VBox(10);
        steps.getChildren().addAll(
                UiUtils.buildStep("1", "Add family members",
                        "Go to Profile → + Add Member. Add everyone in your household."),
                UiUtils.buildStep("2", "Add your bank accounts",
                        "Go to Accounts → Bank Accounts → + Add. Add the accounts where "
                        + "salaries and income are deposited."),
                UiUtils.buildStep("3", "Complete earnings details",
                        "Return to Profile and click the ₹ button next to each earning member.")
        );

        card.getChildren().addAll(heading, intro, steps);
        return card;
    }

    // ── Summary stat cards ────────────────────────────────────────────────────

    private HBox buildSummaryRow() {
        DataStore ds = DataStore.getInstance();
        HBox row = new HBox(14);
        row.setFillHeight(true);
        row.getChildren().addAll(
                summaryCard("Net Worth",        fmtInr(ds.getNetWorthPaise()),        STRIPE_GOLD, "#0f3d4a"),
                summaryCard("Bank Balance",     fmtInr(ds.getTotalBankBalancePaise()), STRIPE_TEAL, "#0f3d4a"),
                summaryCard("Monthly Expenses", fmtInr(ds.getMonthlyExpensesPaise()),  STRIPE_TEAL, "#0f3d4a"),
                summaryCard("Monthly Income",   fmtInr(ds.getMonthlyIncomePaise()),    STRIPE_TEAL, "#0f3d4a")
        );
        return row;
    }

    private HBox buildCreditCardRow() {
        DataStore ds = DataStore.getInstance();
        long ccOutstanding = ds.getTotalCreditCardOutstandingPaise();
        long loanCount     = ds.getActiveLoanAccounts().size();

        String ccStripe = ccOutstanding > 0 ? STRIPE_RED : STRIPE_TEAL;
        String ccColor  = ccOutstanding > 0 ? "#c0392b" : "#0f3d4a";
        String ccValue  = ccOutstanding > 0
                ? "₹−" + String.format("%,.2f", ccOutstanding / 100.0)
                : fmtInr(0);

        HBox row = new HBox(14);
        row.getChildren().addAll(
                summaryCard("Credit Card Balance", ccValue,                  ccStripe, ccColor),
                summaryCard("Active Loans",        String.valueOf(loanCount), STRIPE_TEAL, "#0f3d4a")
        );
        return row;
    }

    /**
     * Stat card with a 3 px left accent stripe.
     * stripeColor — hex colour for the left stripe
     * valueColor  — hex colour for the amount label
     */
    private VBox summaryCard(String label, String value, String stripeColor, String valueColor) {
        VBox card = new VBox(8);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMinWidth(140);
        card.setStyle(
                "-fx-background-color: " + stripeColor + ", white; " +
                "-fx-background-insets: 0, 0 0 0 3; " +
                "-fx-background-radius: 12, 12; " +
                "-fx-border-color: rgba(42,138,122,0.18); " +
                "-fx-border-radius: 12; " +
                "-fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(15,61,74,0.08), 12, 0, 0, 2); " +
                "-fx-padding: 18 18 18 20;");

        Label lbl = new Label(label.toUpperCase());
        lbl.setStyle(
                "-fx-text-fill: #7aa4b0; -fx-font-size: 10.5px; " +
                "-fx-font-weight: 700;");

        Label val = new Label(value);
        val.setStyle(
                "-fx-text-fill: " + valueColor + "; " +
                "-fx-font-size: 20px; -fx-font-weight: 700;");

        card.getChildren().addAll(lbl, val);
        return card;
    }

    // ── Pending recurring card ────────────────────────────────────────────────

    private VBox buildPendingCard() {
        VBox card = new VBox(0);
        card.setStyle(
                "-fx-background-color: white; " +
                "-fx-background-radius: 14; " +
                "-fx-border-color: rgba(42,138,122,0.18); " +
                "-fx-border-radius: 14; -fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(15,61,74,0.08), 12, 0, 0, 2);");

        // Section header
        HBox header = sectionHeader("Pending Recurring Transactions", STRIPE_TEAL, true);
        header.setPadding(new Insets(16, 20, 14, 20));

        // Separator
        Region sep = new Region();
        sep.setStyle("-fx-background-color: rgba(42,138,122,0.12); -fx-pref-height: 1; -fx-max-height: 1;");
        sep.setMaxWidth(Double.MAX_VALUE);

        // Items container — no background; rows self-separate via bottom border
        pendingContainer = new VBox(0);
        pendingContainer.setPadding(new Insets(0, 20, 4, 20));
        buildPendingItems(pendingContainer);

        card.getChildren().addAll(header, sep, pendingContainer);
        return card;
    }

    private void buildPendingItems(VBox container) {
        container.getChildren().clear();
        List<RecurringTransaction> pending = DataStore.getInstance().getPendingRecurring();

        if (pending.isEmpty()) {
            Label none = new Label("✅  No pending transactions. You're all caught up!");
            none.setStyle("-fx-text-fill: #27AE60; -fx-padding: 12 0;");
            container.getChildren().add(none);
        } else {
            for (int i = 0; i < pending.size(); i++) {
                HBox item = buildPendingItem(pending.get(i), container);
                // Last item: remove the bottom border
                if (i == pending.size() - 1) {
                    item.setStyle(item.getStyle() +
                            " -fx-border-color: transparent; -fx-border-width: 0;");
                }
                container.getChildren().add(item);
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
        desc.setStyle("-fx-font-size: 13.5px; -fx-font-weight: 600; -fx-text-fill: #0f3d4a;");
        Label due = new Label("Due: " + (r.getNextDueDate() != null
                ? r.getNextDueDate().format(DATE_FMT) : "—"));
        due.setStyle("-fx-font-size: 12px; -fx-text-fill: #7aa4b0;");
        VBox details = new VBox(2, desc, due);
        HBox.setHgrow(details, Priority.ALWAYS);

        Label amount = new Label(r.getAmountInr());
        amount.setStyle("-fx-font-size: 14px; -fx-font-weight: 700; -fx-text-fill: #0f3d4a;");

        Button record = new Button("✓");
        record.setStyle(
                "-fx-background-color: #e8f0f2; -fx-text-fill: #7aa4b0; " +
                "-fx-font-size: 12px; " +
                "-fx-min-width: 28; -fx-max-width: 28; -fx-min-height: 28; -fx-max-height: 28; " +
                "-fx-background-radius: 7; -fx-border-color: rgba(42,138,122,0.30); -fx-border-radius: 7; " +
                "-fx-border-width: 1; -fx-cursor: hand; -fx-padding: 0;");
        record.setTooltip(new Tooltip("Record"));
        record.setOnAction(e -> mainWindow.recordRecurring(r, () -> buildPendingItems(container)));

        Button skip = new Button("≫");
        skip.setStyle(
                "-fx-background-color: #e8f0f2; -fx-text-fill: #7aa4b0; " +
                "-fx-font-size: 12px; " +
                "-fx-min-width: 28; -fx-max-width: 28; -fx-min-height: 28; -fx-max-height: 28; " +
                "-fx-background-radius: 7; -fx-border-color: rgba(42,138,122,0.30); -fx-border-radius: 7; " +
                "-fx-border-width: 1; -fx-cursor: hand; -fx-padding: 0;");
        skip.setTooltip(new Tooltip("Skip"));
        skip.setOnAction(e -> mainWindow.skipRecurring(r, () -> buildPendingItems(container)));

        item.getChildren().addAll(typeBadge, details, amount, record, skip);
        return item;
    }

    // ── Recent transactions ───────────────────────────────────────────────────

    private VBox buildRecentTransactions() {
        VBox card = new VBox(0);
        card.setStyle(
                "-fx-background-color: white; " +
                "-fx-background-radius: 14; " +
                "-fx-border-color: rgba(42,138,122,0.18); " +
                "-fx-border-radius: 14; -fx-border-width: 1; " +
                "-fx-effect: dropshadow(gaussian, rgba(15,61,74,0.08), 12, 0, 0, 2);");

        HBox header = sectionHeader("Recent Transactions", STRIPE_GOLD, false);
        header.setPadding(new Insets(16, 20, 14, 20));

        Region sep = new Region();
        sep.setStyle("-fx-background-color: rgba(42,138,122,0.12); -fx-pref-height: 1; -fx-max-height: 1;");
        sep.setMaxWidth(Double.MAX_VALUE);

        VBox rows = new VBox(0);
        rows.setPadding(new Insets(0, 20, 4, 20));

        List<Transaction> recent = DataStore.getInstance().getRecentTransactions(10);
        if (recent.isEmpty()) {
            Label none = new Label("No transactions yet. Use the + button to record your first transaction.");
            none.setStyle("-fx-text-fill: #7aa4b0; -fx-font-style: italic; -fx-padding: 12 0;");
            rows.getChildren().add(none);
        } else {
            for (int i = 0; i < recent.size(); i++) {
                Transaction t = recent.get(i);
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);

                // Bottom border on all but the last row
                String borderStyle = (i < recent.size() - 1)
                        ? "-fx-border-color: transparent transparent rgba(42,138,122,0.18) transparent; " +
                          "-fx-border-width: 0 0 1px 0;"
                        : "";
                row.setStyle("-fx-padding: 10 0;" + borderStyle);

                Label typeBadge = new Label(UiUtils.badgeText(t.getType()));
                typeBadge.getStyleClass().add(UiUtils.badgeStyle(t.getType()));
                typeBadge.setMinWidth(90);

                Label dateL = new Label(t.getDate().format(DATE_FMT));
                dateL.setStyle("-fx-font-size: 12px; -fx-text-fill: #7aa4b0;");
                dateL.setMinWidth(90);

                Label descL = new Label(t.getDescription());
                descL.setStyle("-fx-font-size: 13px; -fx-text-fill: #4a7a88;");
                descL.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(descL, Priority.ALWAYS);

                Label amtL = new Label(t.getAmountInr());
                amtL.setStyle("-fx-font-size: 13px; -fx-font-weight: 700; -fx-text-fill: #0f3d4a; -fx-alignment: center-right;");
                amtL.setMinWidth(110);
                amtL.setMaxWidth(110);

                row.getChildren().addAll(typeBadge, dateL, descL, amtL);
                rows.getChildren().add(row);
            }
        }

        card.getChildren().addAll(header, sep, rows);
        return card;
    }

    // ── Shared section header builder ─────────────────────────────────────────

    /**
     * Builds a section header: [coloured dot] [UPPERCASE TITLE] ... [View All →]
     * dotColor — hex colour of the small circle indicator
     * showViewAll — whether to show the "View All →" hyperlink
     */
    private HBox sectionHeader(String title, String dotColor, boolean showViewAll) {
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);

        Circle dot = new Circle(4);
        dot.setStyle("-fx-fill: " + dotColor + ";");

        Label titleLbl = new Label(title.toUpperCase());
        titleLbl.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: 700; -fx-text-fill: #4a7a88;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(dot, titleLbl, spacer);

        if (showViewAll) {
            Hyperlink viewAll = new Hyperlink("View All →");
            viewAll.setStyle(
                    "-fx-font-size: 12px; -fx-font-weight: 600; " +
                    "-fx-text-fill: #2a8a7a; -fx-border-color: transparent;");
            viewAll.setOnAction(e -> mainWindow.navigateToRecurring());
            header.getChildren().add(viewAll);
        }

        return header;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String fmtInr(long paise) {
        return String.format("₹%,.2f", paise / 100.0);
    }
}
