# Plan: Cash Flow Forecast — Feature 10

## Overview
Add a third tab "Cash Flow Forecast" to ReportsScreen. Shows a multi-series line chart of
projected account balances over time (monthly data points). Accounts included: all active
bank accounts, all active credit card accounts, and active investment accounts of type
FIXED_DEPOSIT, RECURRING_DEPOSIT, and DEBT_BONDS.

---

## Files to create / modify

| Action | File |
|--------|------|
| **NEW** | `src/main/java/com/sanchay/service/CashFlowProjectionService.java` |
| **NEW** | `src/main/java/com/sanchay/ui/reports/CashFlowForecastTab.java` |
| **MODIFY** | `src/main/java/com/sanchay/ui/reports/ReportsScreen.java` |
| **MODIFY** | `src/main/resources/com/sanchay/css/app.css` |

---

## Step 1 — CSS additions (`app.css`)

### 1a. Add chart series color tokens to `.root`
```css
/* Cash flow chart — series palette */
-chart-color-total:    -brand-accent;   /* gold — total line */
-chart-color-s1:       #2a8a7a;
-chart-color-s2:       #3db89a;
-chart-color-s3:       #16a34a;
-chart-color-s4:       #e05555;
-chart-color-s5:       #7c3aed;
-chart-color-s6:       #0f3d4a;
-chart-color-s7:       #f59e0b;
```

### 1b. Chart series line styling
Scope rules under `.cash-flow-chart` to avoid colliding with any future charts.
The Total series is always `series0`; account series follow in order.

```css
/* Total series — gold, thicker */
.cash-flow-chart .chart-series-line.series0 { -fx-stroke: -chart-color-total; -fx-stroke-width: 3px; }
.cash-flow-chart .default-color0.chart-line-symbol { -fx-background-color: -chart-color-total; }

/* Individual account series */
.cash-flow-chart .chart-series-line.series1  { -fx-stroke: -chart-color-s1; -fx-stroke-width: 2px; }
.cash-flow-chart .chart-series-line.series2  { -fx-stroke: -chart-color-s2; -fx-stroke-width: 2px; }
.cash-flow-chart .chart-series-line.series3  { -fx-stroke: -chart-color-s3; -fx-stroke-width: 2px; }
.cash-flow-chart .chart-series-line.series4  { -fx-stroke: -chart-color-s4; -fx-stroke-width: 2px; }
.cash-flow-chart .chart-series-line.series5  { -fx-stroke: -chart-color-s5; -fx-stroke-width: 2px; }
.cash-flow-chart .chart-series-line.series6  { -fx-stroke: -chart-color-s6; -fx-stroke-width: 2px; }
.cash-flow-chart .chart-series-line.series7  { -fx-stroke: -chart-color-s7; -fx-stroke-width: 2px; }

/* Hide data point symbols (clean line, hover-only) */
.cash-flow-chart .chart-line-symbol { -fx-background-radius: 3px; -fx-padding: 3px; visibility: hidden; }
.cash-flow-chart .chart-line-symbol:hover { visibility: visible; }

/* Chart plot background */
.cash-flow-chart .chart-plot-background { -fx-background-color: -surface-card; }
.cash-flow-chart .chart-vertical-grid-lines { -fx-stroke: rgba(42,138,122,0.08); }
.cash-flow-chart .chart-horizontal-grid-lines { -fx-stroke: rgba(42,138,122,0.1); }
.cash-flow-chart .axis { -fx-tick-label-fill: -text-muted; -fx-font-size: 11px; }
```

### 1c. Summary strip
```css
.cash-flow-summary-strip {
    -fx-background-color: rgba(42,138,122,0.07);
    -fx-border-color: rgba(42,138,122,0.18);
    -fx-border-radius: 8px;
    -fx-background-radius: 8px;
    -fx-padding: 8 14 8 14;
    -fx-font-size: 12px;
    -fx-font-weight: bold;
    -fx-text-fill: -brand-mid;
}
```

### 1d. Stat cards
```css
.cash-flow-stat-card {
    -fx-background-color: -surface-card;
    -fx-border-color: rgba(42,138,122,0.15);
    -fx-border-radius: 12px;
    -fx-background-radius: 12px;
    -fx-padding: 16 18 16 22;
    -fx-effect: dropshadow(gaussian, rgba(15,61,74,0.07), 8, 0, 0, 2);
}
.cash-flow-stat-label {
    -fx-font-size: 10px;
    -fx-font-weight: bold;
    -fx-text-fill: -text-muted;
}
.cash-flow-stat-value {
    -fx-font-size: 17px;
    -fx-font-weight: bold;
    -fx-text-fill: -text-primary;
}
.cash-flow-stat-value-pos { -fx-text-fill: -color-success; }
.cash-flow-stat-value-neg { -fx-text-fill: -color-error; }
.cash-flow-stat-sub {
    -fx-font-size: 10px;
    -fx-text-fill: -text-muted;
}
```

### 1e. Warning bar (for loans without a recurring payment)
```css
.cash-flow-warning-bar {
    -fx-background-color: -color-warning-bg;
    -fx-border-color: -color-warning;
    -fx-border-radius: 6px;
    -fx-background-radius: 6px;
    -fx-padding: 6 12 6 12;
    -fx-font-size: 11px;
    -fx-text-fill: -color-warning;
}
```

### 1f. Legend swatch
```css
.cash-flow-legend-swatch {
    -fx-min-width: 24px;
    -fx-max-width: 24px;
    -fx-min-height: 10px;
    -fx-max-height: 10px;
    -fx-background-radius: 2px;
}
.cash-flow-legend-label {
    -fx-font-size: 11px;
    -fx-text-fill: -text-secondary;
}
```

---

## Step 2 — `CashFlowProjectionService.java` (new file)

Package: `com.sanchay.service`

### 2a. Inner records / enums

```java
public record ProjectionPoint(LocalDate date, long balancePaise) {}

public record ProjectionResult(
    List<Account> accounts,                          // ordered list of included accounts
    List<ProjectionPoint> totalSeries,               // sum of all account balances per date
    Map<String, List<ProjectionPoint>> accountSeries, // accountId → series
    long totalProjectedIncomePaise,
    long totalProjectedExpensesPaise,
    List<String> warnings
) {}
```

### 2b. `compute(LocalDate startDate, LocalDate endDate)` method

**Step A — Determine included accounts**
```
List<Account> accounts =
    DataStore.getBankAccounts() (active only)
    + DataStore.getCreditCardAccounts() (active only)
    + DataStore.getAllInvestmentAccounts().filter(
          a -> a.getInvestmentType() in {FIXED_DEPOSIT, RECURRING_DEPOSIT, DEBT_BONDS}
             && a.getInvestmentStatus() == ACTIVE
      )
```

**Step B — Compute starting balance per account (as of today)**

For BankAccount:
```
long balance = ba.getOpeningBalancePaise();
for each Transaction t in DataStore.getTransactions():
    if t.getFromAccountId().equals(ba.getId()): balance -= t.getAmountPaise()
    if t.getToAccountId().equals(ba.getId()):   balance += t.getAmountPaise()
```

For CreditCardAccount:
```
long balance = 0;
for each Transaction t:
    if t.getFromAccountId().equals(cc.getId()): balance -= t.getAmountPaise()
    if t.getToAccountId().equals(cc.getId()):   balance += t.getAmountPaise()
// Result is negative when there is outstanding balance (liability)
```

For InvestmentAccount (FD/RD/Bond):
```
long balance = account.getInvestedAmountPaise();
```

**Step C — Build monthly data points**
```
List<LocalDate> monthEndDates = for each month M from YearMonth.from(startDate)
                                    through YearMonth.from(endDate):
                                    M.atEndOfMonth()
```

**Step D — Apply recurring cash flows month by month**

For each month M (as a YearMonth):
1. For each active RecurringTransaction `rt`:
   - Call `getOccurrencesInRange(rt, M.atDay(1), M.atEndOfMonth())`
   - For each occurrence found, apply the delta to the relevant account balance(s)
     per the following rules (only accounts in our included set are affected):

   | rt.type       | fromAccountId delta | toAccountId delta   | Accumulators |
   |---------------|---------------------|---------------------|--------------|
   | INCOME        | —                   | +amountPaise        | +totalIncome |
   | EXPENSE       | -amountPaise        | —                   | +totalExpense |
   | INVESTMENT    | -amountPaise        | +amountPaise        | +totalExpense (outflow from bank) |
   | LOAN_PAYMENT  | -amountPaise        | —                   | +totalExpense |
   | CC_PAYMENT    | -amountPaise        | +amountPaise        | +totalExpense |
   | TRANSFER      | -amountPaise        | +amountPaise        | — |

   Skip types: REDEEM, GAIN, LOSE (unpredictable).

2. Record the snapshot: after all schedule applications for this month, store the
   current balance for each account as a `ProjectionPoint(monthEnd, balance)`.

**Step E — One-time maturity events**

Process these AFTER building the running balance map, but adjust the balance map
on the relevant month-end date.

FD and Bond maturity:
```
For each Transaction t where:
    t.getType() == INVESTMENT
    t.getToAccountId() maps to a FD or Bond InvestmentAccount in our set
    t.getInvestmentDetails() != null && t.getInvestmentDetails().getFd() != null
    t.getInvestmentDetails().getFd().getMaturityDate() is within [startDate, endDate]
Do:
    LocalDate matDate = fd.getMaturityDate()
    long matAmt = fd.getMaturityAmountPaise() (use amountPaise if null)
    // On the month containing matDate:
    balance[t.getFromAccountId()] += matAmt      // bank receives principal + interest
    balance[t.getToAccountId()]   = 0             // investment account closes
    totalIncomePaise += (matAmt - t.getAmountPaise())  // only interest portion = income
    // Add to totalIncome (the matAmt is already reflected via principal return, the true
    // income is just interest. But for simplicity, treat the full maturity inflow as income.)
    totalIncomePaise += matAmt
```

RD maturity:
```
For each RecurringTransaction rt where:
    rt.getTransactionType() == INVESTMENT
    rt.getToAccountId() maps to a RD InvestmentAccount in our set
    rt.getMaturityDate() != null && rt.getMaturityDate() within [startDate, endDate]
Do:
    // On the month containing maturityDate:
    balance[rt.getFromAccountId()] += rt.getRdMaturityAmountPaise()
    balance[rt.getToAccountId()]   = 0
    totalIncomePaise += rt.getRdMaturityAmountPaise()
```

Note: maturity events should be applied on the correct month when iterating in Step D.
Integrate maturity events into the month-by-month loop: before recording the snapshot
for month M, check for any FD/RD/Bond maturities whose maturityDate falls in M and
apply them.

**Step F — Loan warning generation**

```
For each active LoanAccount:
    boolean hasSchedule = DataStore.getRecurring().anyMatch(
        rt -> rt.getTransactionType() == LOAN_PAYMENT
           && (rt.getToAccountId().equals(loan.getId()) || rt.getFromAccountId() context)
    )
    if (!hasSchedule):
        warnings.add("EMI for "" + loan.getName() + "" not included — no recurring payment set up")
```

Specifically: look for a RecurringTransaction where type == LOAN_PAYMENT and whose
`toAccountId` matches the loan account id (loan payments are directed TO the loan account).

**Step G — Private helper: `getOccurrencesInRange`**

```java
private List<LocalDate> getOccurrencesInRange(RecurringTransaction rt,
                                               LocalDate rangeStart, LocalDate rangeEnd) {
    if (rt.getStatus() != RecurringTransaction.Status.ACTIVE) return List.of();
    if (rt.getAmountPaise() == 0) return List.of();  // CC reminder — variable, skip

    List<LocalDate> result = new ArrayList<>();
    LocalDate cursor = rt.getStartDate();
    int count = 0;

    while (!cursor.isAfter(rangeEnd)) {
        int day = Math.min(rt.getDueDayOfMonth(), cursor.lengthOfMonth());
        LocalDate occurrence = cursor.withDayOfMonth(day);

        if (!occurrence.isBefore(rangeStart) && !occurrence.isAfter(rangeEnd)) {
            result.add(occurrence);
        }

        count++;
        if (rt.getNumberOfPayments() != null && count >= rt.getNumberOfPayments()) break;
        if (rt.getEndDate() != null && cursor.isAfter(rt.getEndDate())) break;

        cursor = advance(cursor, rt.getFrequency());
    }
    return result;
}

private LocalDate advance(LocalDate from, RecurringTransaction.Frequency freq) {
    return switch (freq) {
        case MONTHLY        -> from.plusMonths(1);
        case QUARTERLY      -> from.plusMonths(3);
        case ANNUALLY       -> from.plusYears(1);
        case ALTERNATE_YEAR -> from.plusYears(2);
    };
}
```

**Step H — Compute summary stats**

After the full projection loop:
- `totalProjectedIncomePaise`: sum of all INCOME recurring amounts applied within the period
  + all FD/RD/Bond maturity amounts landing in the period
- `totalProjectedExpensesPaise`: sum of all EXPENSE + LOAN_PAYMENT recurring amounts applied
  + all INVESTMENT recurring amounts that debit a bank account (money leaving to investments)
- `projectedEndBalancePaise`: sum of all account balances at the last data point
- `netCashFlowPaise` = totalIncome - totalExpenses

---

## Step 3 — `CashFlowForecastTab.java` (new file)

Package: `com.sanchay.ui.reports`

### 3a. Fields
```java
private final VBox view;
private ComboBox<String> periodPicker;
private LineChart<String, Number> chart;
private HBox legendBox;
private Label summaryStrip;
private Label warningBar;       // hidden when no warnings
private Label statBalance, statIncome, statExpense, statNet;
```

### 3b. Constructor
Calls `buildView()`, then `refresh()`.

### 3c. `buildView()` layout

```
ScrollPane
└── VBox (padding 24, spacing 16)
    ├── HBox (filter row)
    │   ├── Label "Time Period:" (.form-label)
    │   └── ComboBox<String> periodPicker
    ├── Label warningBar (.cash-flow-warning-bar)  [managed=false when hidden]
    ├── Label summaryStrip (.cash-flow-summary-strip)
    ├── VBox chartCard (.card-wrapper)
    │   ├── Label dateRangeLabel (.text-muted, small font via CSS)
    │   ├── LineChart cashFlowChart (.cash-flow-chart, prefHeight 400)
    │   └── HBox legendBox (spacing 16, padding 12)
    └── HBox statsRow (spacing 12)
        ├── statCard("Projected Balance",    statBalance)  .c-gold
        ├── statCard("Total Projected Income", statIncome) .c-teal
        ├── statCard("Total Projected Expenses", statExpense) .c-error
        └── statCard("Net Cash Flow",        statNet)      .c-purple
```

Period picker items:
- "Next 6 Months"
- "Next 12 Months" ← default
- "Next 24 Months"
- "This Financial Year" or "This Calendar Year" (read `DataStore.getYearFormat()` at refresh time)

When `periodPicker.valueProperty()` changes → call `refresh()`.

### 3d. `refresh()` method

1. Read selected period → compute `(startDate, endDate)`:
   - "Next 6 Months":  startDate=today, endDate=today.plusMonths(6)
   - "Next 12 Months": startDate=today, endDate=today.plusMonths(12)
   - "Next 24 Months": startDate=today, endDate=today.plusMonths(24)
   - "This Financial Year": startDate=today, endDate = April 1 of next FY − 1 day
   - "This Calendar Year": startDate=today, endDate = Dec 31 of current year

2. Update period picker label if FY/CY option text needs to change (year format setting).

3. Call `CashFlowProjectionService.compute(startDate, endDate)` → `ProjectionResult result`.

4. Update summary strip label: "Cash flow forecast for [period description]".

5. Show/hide warning bar. If `result.warnings()` is non-empty, set warning bar text and
   make it visible (managed=true). Otherwise managed=false.

6. Rebuild chart:
   ```
   chart.getData().clear();

   // Series 0 = Total (always first → series0 CSS rule applies)
   XYChart.Series<String, Number> totalSeries = new XYChart.Series<>("Total");
   for ProjectionPoint p : result.totalSeries():
       totalSeries.getData().add(new XYChart.Data<>(formatMonthLabel(p.date()), p.balancePaise() / 100.0));
   chart.getData().add(totalSeries);

   // Series 1..N = individual accounts
   for (int i = 0; i < result.accounts().size(); i++):
       Account acc = result.accounts().get(i);
       XYChart.Series<String, Number> series = new XYChart.Series<>(acc.getName());
       for ProjectionPoint p : result.accountSeries().get(acc.getId()):
           series.getData().add(new XYChart.Data<>(formatMonthLabel(p.date()), p.balancePaise() / 100.0));
       chart.getData().add(series);
   ```

7. Rebuild legend:
   ```
   legendBox.getChildren().clear();
   // Total entry — gold swatch
   legendBox.getChildren().add(buildLegendEntry("Total of Accounts", SERIES_COLORS[0]));
   // One entry per account
   for (int i = 0; i < accounts.size(); i++):
       legendBox.getChildren().add(buildLegendEntry(accounts.get(i).getName(), SERIES_COLORS[i+1]));
   ```

8. Update stat cards:
   ```
   statBalance.setText(formatPaise(lastTotalBalance));
   statBalance.getStyleClass().removeAll("cash-flow-stat-value-pos", "cash-flow-stat-value-neg");
   statBalance.getStyleClass().add(lastTotalBalance >= 0 ? "cash-flow-stat-value-pos" : "cash-flow-stat-value-neg");
   // Same for income (always pos), expense (always neg), net (conditional)
   ```

### 3e. Helper methods

`buildStatCard(String label, String subText, Label valueLabel)` → StackPane/VBox with CSS classes.

`buildLegendEntry(String name, String hexColor)` → HBox with:
  - Rectangle swatch (Inline required: colour is runtime data — series color assigned dynamically)
  - Label with account name (.cash-flow-legend-label)

`formatMonthLabel(LocalDate d)` → e.g. "Apr 26", "May 26"

`formatPaise(long paise)` → e.g. "₹18,72,000" or "(₹14,28,000)" for negatives

`computePeriodEnd(String selectedPeriod)` → returns `(startDate, endDate)` pair

### 3f. Y-axis tick formatter

The Y-axis shows formatted values. Since `NumberAxis` doesn't natively support custom formatting,
use a `NumberAxis` and set a `tickLabelFormatter`:
```java
NumberAxis yAxis = new NumberAxis();
yAxis.setTickLabelFormatter(new StringConverter<Number>() {
    public String toString(Number n) {
        double v = n.doubleValue();
        if (Math.abs(v) >= 100_000) return String.format("%.1fL", v / 100_000);
        if (Math.abs(v) >= 1_000)   return String.format("%.0fK", v / 1_000);
        return String.valueOf((int) v);
    }
    public Number fromString(String s) { return 0; }
});
```

(Values are in rupees since we divide paise by 100 before adding to chart.)

Note on Y-axis format: use Indian notation — L (lakh) for ≥1,00,000 and K for ≥1,000.

---

## Step 4 — Modify `ReportsScreen.java`

### 4a. Add field
```java
private CashFlowForecastTab cashFlowTab;
```

### 4b. In `buildView()`, add third tab
```java
cashFlowTab = new CashFlowForecastTab();
Tab forecastTab = new Tab("Cash Flow Forecast");
forecastTab.setContent(cashFlowTab.getView());
tabPane.getTabs().addAll(summaryTab, ccTab, forecastTab);
```

### 4c. In `refresh()`
```java
if (cashFlowTab != null) cashFlowTab.refresh();
```

---

## Edge cases and tricky parts

1. **Zero-amount recurring schedules** (e.g. CC payment reminders with `amountPaise == 0`):
   Skip in the projection — they have no fixed amount to project.

2. **Paused or completed recurring schedules**: Skip — check `status == ACTIVE`.

3. **Payment-limited schedules** (e.g. RD with `numberOfPayments` set):
   The `getOccurrencesInRange` helper already stops at `numberOfPayments`.
   The last occurrence may fall after `endDate` and therefore not appear in the projection.

4. **Accounts not in the included set**: If a recurring schedule references an account
   not in our included set (e.g. a Loan account as `fromAccountId`), simply skip the
   delta for that account — don't crash.

5. **FD maturity with null `maturityAmountPaise`**: Fall back to using the transaction's
   `amountPaise` (the original invested amount). The interest gain is then 0 but no crash.

6. **CC outstanding displayed as negative**: The chart Y-axis will show CC lines as negative
   values, which is correct — they represent outstanding liabilities. The legend can note this.

7. **Investment accounts starting at ₹0 invested**: If `investedAmountPaise == 0`, the
   account still appears but as a flat zero line. Consider filtering out zero-balance
   investment accounts from the chart to reduce clutter. Add this filter in Step B of
   the projection service.

8. **Month label collision on X-axis**: For "Next 24 Months" there will be 24 labels.
   JavaFX axis will auto-skip some if needed. No special handling required.

9. **Refresh on tab switch**: `ReportsScreen.refresh()` is called on every navigation,
   which runs all three tab refreshes. The cash flow projection is a pure computation
   with no I/O — it will complete in <100ms for typical data sizes. No lazy loading needed.

10. **Series color cycling**: If there are more than 7 accounts, cycle back through the
    color list (index % SERIES_COLORS.length). The Total series always uses index 0.

---

## Series color constants (in CashFlowForecastTab)

```java
private static final String[] SERIES_COLORS = {
    "#f0a500",   // 0: Total (gold)
    "#2a8a7a",   // 1
    "#3db89a",   // 2
    "#16a34a",   // 3
    "#e05555",   // 4
    "#7c3aed",   // 5
    "#0f3d4a",   // 6
    "#f59e0b",   // 7
};
```

Used only for the legend swatch rectangles (inline: runtime-assigned color).
The chart line colors are controlled by CSS series selectors (.series0, .series1, …).

---

## Out of scope for this implementation

- Account filtering (no multi-select dropdown — all eligible accounts always shown)
- FD/Bond periodic interest payout projection (only full maturity amount on maturityDate)
- Loan EMI projection without a recurring schedule (warning shown instead)
- Equities / Mutual Funds / Provident Fund accounts (excluded per feature spec)
- Historical cash flow (projection starts from today only)
