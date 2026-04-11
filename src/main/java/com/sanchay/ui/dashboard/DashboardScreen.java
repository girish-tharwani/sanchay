package com.sanchay.ui.dashboard;

import com.sanchay.model.InvestmentAccount;
import com.sanchay.model.MarketValueEntry;
import com.sanchay.model.RecurringTransaction;
import com.sanchay.model.Transaction;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.MainWindow;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Dashboard screen. */
public class DashboardScreen {

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter HEADER_FMT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);

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
        dateLabel.getStyleClass().add("dialog-subtitle");

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
        view.getStyleClass().add("scroll-page-bg");
    }

    // ── Get Started card ──────────────────────────────────────────────────────

    private VBox buildGetStartedCard() {
        VBox card = new VBox(16);
        card.getStyleClass().add("card-welcome");

        HBox heading = new HBox(10);
        heading.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("🚀");
        // Inline required: single-use emoji size, no matching CSS utility class
        icon.setStyle("-fx-font-size: 20px;");
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

    // ── Summary stat cards ────────────────────────────────────────────────────

    private HBox buildSummaryRow() {
        DataStore ds = DataStore.getInstance();
        HBox row = new HBox(14);
        row.setFillHeight(true);
        row.getChildren().addAll(
                summaryCard("Net Worth",        UiUtils.formatCorpusDisplay(computeCurrentCorpusPaise()),      "-brand-accent"),
                summaryCard("Bank Balance",     UiUtils.formatCorpusDisplay(ds.getTotalBankBalancePaise()),    "-brand-light"),
                summaryCard("Monthly Expenses", UiUtils.formatCorpusDisplay(computeAvgMonthlyExpensesPaise()), "-brand-light"),
                summaryCard("Monthly Income",   UiUtils.formatCorpusDisplay(computeAvgMonthlyIncomePaise()),   "-brand-light")
        );
        return row;
    }

    private HBox buildCreditCardRow() {
        DataStore ds = DataStore.getInstance();
        long ccOutstanding   = ds.getTotalCreditCardOutstandingPaise();
        long loanOutstanding = ds.getActiveLoanAccounts().stream()
                .mapToLong(ds::getLoanOutstandingPaise).sum();

        String ccStripe   = ccOutstanding   > 0 ? "#e05555" : "-brand-light";
        String loanStripe = loanOutstanding > 0 ? "#e05555" : "-brand-light";

        String ccValue   = ccOutstanding > 0
                ? MoneyFormatter.symbol() + MoneyFormatter.format(ccOutstanding).substring(MoneyFormatter.symbol().length())
                : MoneyFormatter.format(0);
        String loanValue = loanOutstanding > 0
                ? MoneyFormatter.symbol() + MoneyFormatter.format(loanOutstanding).substring(MoneyFormatter.symbol().length())
                : MoneyFormatter.format(0);

        HBox row = new HBox(14);
        row.getChildren().addAll(
                summaryCard("Credit Card Balance",    ccValue,   ccStripe),
                summaryCard("Outstanding Loan Amount", loanValue, loanStripe)
        );
        return row;
    }

    /**
     * Stat card with a 3 px left accent stripe.
     * stripeColor — hex/token for the left stripe (runtime-computed, stays inline).
     * All static styles (border, shadow, padding, radius) live in .card-summary CSS class.
     * All amount values use uniform brand-dark colour per CLAUDE.md — no per-tile colouring.
     */
    private VBox summaryCard(String label, String value, String stripeColor) {
        VBox card = new VBox(8);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMinWidth(140);
        card.getStyleClass().add("card-summary");
        // Inline required: stripe colour is data-driven (teal / gold / red per card type)
        card.setStyle(
                "-fx-background-color: " + stripeColor + ", white; " +
                "-fx-background-insets: 0, 0 0 0 3;");

        Label lbl = new Label(label.toUpperCase());
        lbl.getStyleClass().add("card-title");

        Label val = new Label(value);
        val.getStyleClass().add("card-value");

        card.getChildren().addAll(lbl, val);
        return card;
    }

    // ── Pending recurring card ────────────────────────────────────────────────

    private VBox buildPendingCard() {
        VBox card = new VBox(0);
        card.getStyleClass().add("table-card");

        // Section header
        HBox header = sectionHeader("Pending Recurring Transactions", "-brand-light", true);
        header.setPadding(new Insets(16, 20, 14, 20));

        // Separator
        Region sep = new Region();
        sep.getStyleClass().add("sep-teal");
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
            none.getStyleClass().add("text-empty");
            none.setPadding(new Insets(12, 0, 12, 0));
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
        desc.getStyleClass().add("text-step-title");
        Label due = new Label("Due: " + (r.getNextDueDate() != null
                ? r.getNextDueDate().format(DATE_FMT) : "—"));
        due.getStyleClass().add("dialog-subtitle");
        VBox details = new VBox(2, desc, due);
        HBox.setHgrow(details, Priority.ALWAYS);

        Label amount = new Label(r.getAmountInr());
        amount.getStyleClass().add("text-step-title");

        Button record = new Button("✓");
        record.getStyleClass().add("btn-action-sm");
        record.setTooltip(new Tooltip("Record"));
        record.setOnAction(e -> mainWindow.recordRecurring(r, () -> buildPendingItems(container)));

        Button skip = new Button("≫");
        skip.getStyleClass().add("btn-action-sm");
        skip.setTooltip(new Tooltip("Skip"));
        skip.setOnAction(e -> mainWindow.skipRecurring(r, () -> buildPendingItems(container)));

        item.getChildren().addAll(typeBadge, details, amount, record, skip);
        return item;
    }

    // ── Recent transactions ───────────────────────────────────────────────────

    private VBox buildRecentTransactions() {
        VBox card = new VBox(0);
        card.getStyleClass().add("table-card");

        HBox header = sectionHeader("Recent Transactions", "-brand-accent", false);
        header.setPadding(new Insets(16, 20, 14, 20));

        Region sep = new Region();
        sep.getStyleClass().add("sep-teal");
        sep.setMaxWidth(Double.MAX_VALUE);

        VBox rows = new VBox(0);
        rows.setPadding(new Insets(0, 20, 4, 20));

        List<Transaction> recent = DataStore.getInstance().getRecentTransactions(10);
        if (recent.isEmpty()) {
            Label none = new Label("No transactions yet. Use the + button to record your first transaction.");
            none.getStyleClass().add("text-empty");
            none.setPadding(new Insets(12, 0, 12, 0));
            rows.getChildren().add(none);
        } else {
            for (int i = 0; i < recent.size(); i++) {
                Transaction t = recent.get(i);
                HBox row = new HBox(12);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(10, 0, 10, 0));

                // Bottom border on all but the last row (runtime-computed state, stays inline)
                if (i < recent.size() - 1) {
                    row.setStyle("-fx-border-color: transparent transparent rgba(42,138,122,0.18) transparent; " +
                                 "-fx-border-width: 0 0 1px 0;");
                }

                Label typeBadge = new Label(UiUtils.badgeText(t.getType()));
                typeBadge.getStyleClass().add(UiUtils.badgeStyle(t.getType()));
                typeBadge.setMinWidth(90);

                Label dateL = new Label(t.getDate().format(DATE_FMT));
                dateL.getStyleClass().add("dialog-subtitle");
                dateL.setMinWidth(90);

                Label descL = new Label(t.getDescription());
                descL.getStyleClass().add("text-body-muted");
                descL.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(descL, Priority.ALWAYS);

                Label amtL = new Label(t.getAmountInr());
                amtL.getStyleClass().add("stat-value");
                // Inline required: alignment + text-fill not covered by stat-value class
                amtL.setStyle("-fx-text-fill: -brand-dark; -fx-alignment: center-right;");
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
        // Inline required: Shape.fill cannot be set via style class; colour is data-driven
        dot.setStyle("-fx-fill: " + dotColor + ";");

        Label titleLbl = new Label(title.toUpperCase());
        titleLbl.getStyleClass().add("section-group-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        header.getChildren().addAll(dot, titleLbl, spacer);

        if (showViewAll) {
            Hyperlink viewAll = new Hyperlink("View All →");
            viewAll.getStyleClass().add("link-teal");
            viewAll.setOnAction(e -> mainWindow.navigateToRecurring());
            header.getChildren().add(viewAll);
        }

        return header;
    }

    // ── Computation helpers ───────────────────────────────────────────────────

    /**
     * Corpus value using the same method as the "Current Corpus" tile on the
     * Financial Planning screen: bank (net of CC) + equity/MF at 90% market value
     * (or invested amount if no market value recorded) + bonds/FD/RD/PF at cost.
     * Amounts rounded down to the nearest ₹10,000 per bucket.
     */
    private long computeCurrentCorpusPaise() {
        DataStore ds    = DataStore.getInstance();
        LocalDate today = LocalDate.now();
        final long ROUND = 1_000_000L; // 10,000 rupees in paise

        long bank = Math.floorDiv(
                ds.getTotalBankBalancePaise() - ds.getTotalCreditCardOutstandingPaise(), ROUND) * ROUND;

        long equity = 0, mf = 0, bonds = 0, fd = 0, rd = 0, pf = 0;
        for (InvestmentAccount ia : ds.getInvestmentAccounts()) {
            if (ia.getInvestmentStatus() == InvestmentAccount.InvestmentStatus.REDEEMED) continue;
            long value;
            switch (ia.getInvestmentType()) {
                case EQUITY -> {
                    MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
                    value = mv != null ? (long) (mv.getMarketValuePaise() * 0.9)
                                      : ds.getInvestedPaiseAsOf(ia, today);
                    equity += Math.floorDiv(value, ROUND) * ROUND;
                }
                case MUTUAL_FUNDS -> {
                    MarketValueEntry mv = ds.getLatestMarketValue(ia.getId());
                    value = mv != null ? (long) (mv.getMarketValuePaise() * 0.9)
                                      : ds.getInvestedPaiseAsOf(ia, today);
                    mf += Math.floorDiv(value, ROUND) * ROUND;
                }
                case DEBT_BONDS        -> bonds += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND) * ROUND;
                case FIXED_DEPOSIT     -> fd    += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND) * ROUND;
                case RECURRING_DEPOSIT -> rd    += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND) * ROUND;
                case PROVIDENT_FUND    -> pf    += Math.floorDiv(ds.getInvestedPaiseAsOf(ia, today), ROUND) * ROUND;
            }
        }
        return bank + equity + mf + bonds + fd + rd + pf;
    }

    /**
     * Average monthly expenses over up to the last 12 complete months (current month excluded).
     * Denominator = months between the earliest transaction and the end of last month, capped at 12.
     */
    private long computeAvgMonthlyExpensesPaise() {
        DataStore ds = DataStore.getInstance();
        YearMonth prevMonth   = YearMonth.from(LocalDate.now()).minusMonths(1);
        YearMonth windowStart = prevMonth.minusMonths(11);

        YearMonth effectiveStart = ds.getTransactions().stream()
                .map(t -> YearMonth.from(t.getDate()))
                .min(Comparator.naturalOrder())
                .map(earliest -> earliest.isAfter(windowStart) ? earliest : windowStart)
                .orElse(null);
        if (effectiveStart == null || effectiveStart.isAfter(prevMonth)) return 0;

        long months = ChronoUnit.MONTHS.between(effectiveStart, prevMonth) + 1;

        long expenses = ds.getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.EXPENSE && inWindow(t.getDate(), effectiveStart, prevMonth))
                .mapToLong(Transaction::getAmountPaise).sum();
        long refunds = ds.getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.REFUND && inWindow(t.getDate(), effectiveStart, prevMonth))
                .mapToLong(Transaction::getAmountPaise).sum();
        return Math.max(0, (expenses - refunds) / months);
    }

    /**
     * Average monthly income over up to the last 12 complete months (current month excluded).
     * Denominator = months between the earliest transaction and the end of last month, capped at 12.
     */
    private long computeAvgMonthlyIncomePaise() {
        DataStore ds = DataStore.getInstance();
        YearMonth prevMonth   = YearMonth.from(LocalDate.now()).minusMonths(1);
        YearMonth windowStart = prevMonth.minusMonths(11);

        YearMonth effectiveStart = ds.getTransactions().stream()
                .map(t -> YearMonth.from(t.getDate()))
                .min(Comparator.naturalOrder())
                .map(earliest -> earliest.isAfter(windowStart) ? earliest : windowStart)
                .orElse(null);
        if (effectiveStart == null || effectiveStart.isAfter(prevMonth)) return 0;

        long months = ChronoUnit.MONTHS.between(effectiveStart, prevMonth) + 1;

        long income = ds.getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.INCOME && inWindow(t.getDate(), effectiveStart, prevMonth))
                .mapToLong(Transaction::getAmountPaise).sum();
        long gain = ds.getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.GAIN && inWindow(t.getDate(), effectiveStart, prevMonth))
                .mapToLong(Transaction::getAmountPaise).sum();
        long lose = ds.getTransactions().stream()
                .filter(t -> t.getType() == Transaction.Type.LOSE && inWindow(t.getDate(), effectiveStart, prevMonth))
                .mapToLong(Transaction::getAmountPaise).sum();
        return Math.max(0, (income + gain - lose) / months);
    }

    private static boolean inWindow(LocalDate date, YearMonth start, YearMonth end) {
        YearMonth ym = YearMonth.from(date);
        return !ym.isBefore(start) && !ym.isAfter(end);
    }

}
