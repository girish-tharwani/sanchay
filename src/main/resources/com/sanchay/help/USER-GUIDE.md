# Sanchay — User Guide

This guide walks through every feature in Sanchay from first launch to advanced use. Each section is written as a step-by-step workflow you can follow directly in the app.

---

## Table of Contents

1. [First Launch & Setup](#1-first-launch--setup)
2. [The Main Window](#2-the-main-window)
3. [Setting Up Your Profile](#3-setting-up-your-profile)
4. [Managing Accounts](#4-managing-accounts)
   - [Bank Account](#41-bank-account)
   - [Credit Card](#42-credit-card)
   - [Loan Account](#43-loan-account)
   - [Investment Account](#44-investment-account)
5. [Recording Transactions](#5-recording-transactions)
   - [Expense](#51-expense)
   - [Income](#52-income)
   - [Transfer](#53-transfer)
   - [Credit Card Payment](#54-credit-card-payment)
   - [Loan Payment](#55-loan-payment)
   - [Investment](#56-investment)
   - [Redeem](#57-redeem)
   - [Refund](#58-refund)
6. [Recurring Schedules](#6-recurring-schedules)
   - [Creating a Schedule](#61-creating-a-schedule)
   - [Recording & Skipping Due Items](#62-recording--skipping-due-items)
7. [Importing Bank Statements](#7-importing-bank-statements)
   - [Preparing Your Statement](#71-preparing-your-statement)
   - [First-Time Column Mapping](#72-first-time-column-mapping)
   - [Reviewing Import Results](#73-reviewing-import-results)
   - [Handling Ambiguous Matches](#74-handling-ambiguous-matches)
8. [Categories](#8-categories)
9. [Dashboard](#9-dashboard)
10. [Reports & Cash Flow Forecast](#10-reports--cash-flow-forecast)
11. [Financial Planning](#11-financial-planning)
    - [Plan Parameters](#111-plan-parameters)
    - [Major Events](#112-major-events)
12. [Settings](#12-settings)
    - [Changing Your Data Folder](#121-changing-your-data-folder)
    - [Creating a Backup](#122-creating-a-backup)
13. [Tips & Common Workflows](#13-tips--common-workflows)

---

## 1. First Launch & Setup

When you open Sanchay for the first time, the setup wizard guides you through two steps.

### Step 1 — Choose a data folder

Your data folder is where Sanchay stores all your financial records as plain JSON files. Choose a location you control:

- **Local folder** (e.g. `C:\Users\You\Documents\sanchay-data`) — fast, works offline, no sync
- **Cloud-synced folder** (e.g. inside OneDrive, Google Drive, or Dropbox) — accessible from multiple machines, automatically backed up by the cloud service
- **External drive** — useful for keeping finances separate from your main machine

You can change this location at any time from Settings without losing data.

### Step 2 — Preferences

| Setting | Options |
|---|---|
| Date Format | DD/MM/YYYY · YYYY-MM-DD |
| Year Format | Indian Financial Year (Apr–Mar) · Calendar Year (Jan–Dec) |
| Currency | INR |

Click **Finish**. The main window opens.

---

## 2. The Main Window

The window has three zones:

- **Sidebar** (left) — navigation between screens; collapsible
- **Main panel** (centre/right) — the active screen
- **Floating Action Button / FAB** (bottom-right, `+`) — opens the Add Transaction dialog for the currently viewed account; always available

The window is undecorated (no OS title bar). Use the **−**, **□**, and **×** buttons in the top-right corner to minimise, maximise, or close. You can drag the top bar to move the window.

**Navigation items:**

| Item | What it does |
|---|---|
| Dashboard | At-a-glance summary, pending recurring transactions, recent activity |
| Accounts | All accounts grouped by type; click an account to view its transactions |
| Recurring | Manage all recurring schedules |
| Reports | Cash flow forecast and projection charts |
| Financial Plan | Retirement projections, corpus breakdown, major events |
| Categories | Manage expense and income category trees |
| Profile | Family members and their earnings |
| Settings | Data folder, display preferences, backup |
| Help | In-app reference |

---

## 3. Setting Up Your Profile

Before adding accounts, set up your family members. At minimum, add yourself — the app uses your date of birth for age-related calculations in the Financial Planning screen.

### Add a family member

1. Go to **Profile**.
2. Click **+ Add Member**.
3. Fill in:
   - **Name** (required)
   - **Date of Birth** — needed for retirement age calculations; add at least for "Self"
   - **Relationship** — Self, Spouse, Child, Parent, Sibling, or Other
   - **Earning** — tick this if the member has income to track
4. Click **Save**.

Add one member with Relationship = **Self** first; several features use this record as the reference point for "current age."

### Configure earnings for a member

Once a member is marked as Earning, an **₹** button appears on their row. Click it to open the Earnings dialog.

Each income source is a tab. Click **+ Income** to add one. You'll be asked for:
- **Source Name** — a label for the tab (e.g. "Main Job", "Freelance")
- **Type** — Simple Income or Structured Salary

#### Simple Income

For freelance, rental, pension, or any non-salaried income:

| Field | Notes |
|---|---|
| Description | Pre-filled; used as the recurring transaction label |
| Amount (gross) | Monthly/quarterly/etc. gross amount |
| Frequency | Monthly · Quarterly · Half Yearly · Annually · Alternate Year |
| Estimated Tax Rate | Optional; used to compute net amount hint |
| Into Account | Which bank account the money arrives in |
| Day of Month | 1–28; when the income typically lands |
| Category | Optional income category |

The net amount is shown as a hint: *gross × (1 − tax rate)*.

#### Structured Salary

For salaried employees. Sanchay calculates the full in-hand breakdown:

| Field | Notes |
|---|---|
| Basic + DA (annual) | Required |
| HRA (annual) | Optional |
| Other Allowances (annual) | Optional |
| Estimated Tax Rate (%) | For TDS calculation |
| VPF (%) | Voluntary PF above mandatory 12% |
| To Account | Bank account where salary is credited |
| Day of Month | Salary credit date |
| Category | Optional income category |
| PF Account | Select an existing Provident Fund investment account, or click **+ Add PF Account** to create one inline |
| Gratuity | Tick to include in breakdown |

The right panel updates live showing: Gross Monthly, Employee PF, TDS, **Net In-hand**, Employer EPF, EPS, and Gratuity per year of service.

Click **Save** when done. Sanchay creates the corresponding recurring income schedule automatically.

---

## 4. Managing Accounts

Go to **Accounts** and click **+ Add Account** (or the `+` button in the relevant group header). Select the account type.

### 4.1 Bank Account

| Field | Notes |
|---|---|
| Name | Display name (e.g. "HDFC Primary Savings") |
| Description | Optional note |
| Bank | Bank name |
| Account Holder | Your name or a family member |
| Account Number | Optional; stored for reference only |
| Sub-Type | Savings · Current |
| Status | Active · Closed |
| Opening Date | Date the account was opened; defaults to today |
| Opening Balance | Balance on the opening date |
| Joint Account | Tick to add a Second Holder |

### 4.2 Credit Card

| Field | Notes |
|---|---|
| Name | Display name (e.g. "HDFC Regalia") |
| Issuer | Bank/issuer name |
| Card Holder | Family member |
| Card Number | Optional; last 4 digits is enough |
| Credit Limit | Total approved limit |
| Status | Active · Blocked · Cancelled |
| Billing Date | Statement generation date (1–28) |
| Payment Due (days) | Days after billing date the payment is due; default 20 |
| Add-on Card | Tick to add an Add-on Card Holder |

### 4.3 Loan Account

| Field | Notes |
|---|---|
| Name | Display name (e.g. "ICICI Home Loan") |
| Loan Type | Home Loan · Vehicle Loan · Personal Loan |
| Status | Active · Closed · Settled |
| Lender | Bank/NBFC name |
| Account No. | Loan account number |
| Loan Amount | Original sanctioned amount |
| Interest Rate | Annual rate %; used to generate amortization schedule |
| Tenure (months) | Total loan tenure |
| EMI Amount | Monthly EMI; auto-calculated from rate + tenure, or enter manually |
| EMI Due Day | Day of month EMI is debited (1–28) |
| Opening Balance | Outstanding principal as of the opening date |
| Opening Date | Date of first entry into Sanchay |
| Joint Account | Tick to add a Co-applicant |

After saving, Sanchay generates the full **amortization schedule** and creates a monthly Loan Payment recurring schedule automatically.

#### Updating the interest rate later

When your bank revises the rate, edit the loan account and change the **Interest Rate** field. A prompt asks for the **Effective From** date. The schedule is regenerated from that point forward, and you can choose whether to **Reduce Tenure** or **Reduce EMI**.

To view the full schedule, open the account and click the **Schedule** button.

### 4.4 Investment Account

| Field | Notes |
|---|---|
| Name | Display name (e.g. "HDFC Flexi Cap Fund") |
| Investment Type | Mutual Funds · Equities · Debt Bonds · Fixed Deposits · Recurring Deposits · Provident Fund |
| Status | Active · Closed · Redeemed |
| Account Number | Folio number or account ID |
| Opening Invested Amount | Amount invested at opening |

Investment accounts track both book value (from transactions) and market value (from manual snapshots). To record a market value snapshot, open the account and click **Market Values**.

---

## 5. Recording Transactions

Click the **+** FAB at the bottom-right of any screen, or open an account and click **+ Add** in the toolbar. The transaction type defaults to the context of the current screen.

All transaction types share these fields:

| Field | Notes |
|---|---|
| Type | Determines which fields appear below |
| Date | Defaults to today |
| Description | Required; autocomplete suggests previous descriptions |
| Amount (₹) | Required; always enter as a positive number |
| Notes | Optional free-text memo |

### 5.1 Expense

Money leaving a bank or credit card account.

| Field | Notes |
|---|---|
| From Account | Bank or credit card |
| Category / Sub-category | Optional; sub-category appears after selecting a parent category |
| Payment Mode | Cash · Card · Cheque · UPI · Bank Transfer · Crypto · Other |
| Family Member | Optional; attribute the spend to a specific person |
| Ref / UTR No | Optional reference number |

### 5.2 Income

Money arriving in a bank account.

| Field | Notes |
|---|---|
| To Account | Bank accounts only |
| Category / Sub-category | Optional |
| Family Member | Optional |

### 5.3 Transfer

Movement between two of your bank accounts (e.g. sweeping funds from salary account to savings).

| Field | Notes |
|---|---|
| From Account | Bank accounts |
| To Account | Bank accounts (must differ from From) |
| Category / Sub-category | Optional |

### 5.4 Credit Card Payment

Paying your credit card bill from a bank account. This clears the outstanding balance on the card.

| Field | Notes |
|---|---|
| From Account | Bank account the payment comes from |
| To Account | Credit card being paid |

The amount should be the full or minimum payment amount.

### 5.5 Loan Payment

Recording an EMI payment.

| Field | Notes |
|---|---|
| From Account | Bank or credit card account |
| To Account | Loan account |
| Principal (₹) | Pre-filled from the amortization schedule if available |
| Interest (₹) | Auto-calculated as Amount − Principal; shown read-only |
| Payment Mode | Optional |
| Ref / UTR No | Optional |

If the pre-filled principal looks wrong (e.g. after a prepayment), edit it directly. The interest label updates live.

### 5.6 Investment

Deploying funds from a bank account into an investment account.

| Field | Notes |
|---|---|
| From Account | Bank account |
| To Account | Investment account |
| (Type-specific fields) | See below |

**Additional fields by investment type:**

| Type | Extra Fields |
|---|---|
| Mutual Funds / Equities / Debt Bonds | Scheme / Script name; Units / NAV |
| Fixed Deposit | FD Reference No; Interest Rate (%); Maturity Date; Maturity Amount |
| Recurring Deposit | RD Reference No (required); Interest Rate (%); Maturity Date; Opening Balance; Maturity Amount |
| Provident Fund | No extra fields |

For Fixed Deposits, a live preview shows: Principal, Annual Interest, Total Interest, and Tenor.

### 5.7 Redeem

Withdrawing from an investment account back to a bank account.

| Field | Notes |
|---|---|
| From Account | Investment account |
| To Account | Bank account |
| Reference No. | For Fixed Deposit accounts, select the specific FD reference |
| Principal (₹) | Original invested amount being returned |
| Gain / Loss | Auto-calculated (Amount − Principal); shown in green (gain) or red (loss) |
| Category | Automatically switches between Income categories (gain) and Expense categories (loss) |

Sanchay records two transactions: the main redemption plus a GAIN or LOSE transaction for the difference.

### 5.8 Refund

Money returned to your account that offsets a previous expense — a refund, cashback, or reversal.

| Field | Notes |
|---|---|
| To Account | Bank or credit card |
| Category / Sub-category | Should match the original expense category so reports offset correctly |
| Payment Mode | Optional |
| Family Member | Optional |

---

## 6. Recurring Schedules

Recurring schedules automate regular transactions like salary, EMI, SIP, rent, and subscriptions. When due, they appear on the Dashboard for one-click recording.

### 6.1 Creating a Schedule

Go to **Recurring** → click **+ Add Recurring**.

| Field | Notes |
|---|---|
| Description | Label for the schedule (e.g. "Netflix Subscription") |
| Type | Expense · Income · Transfer · Investment · CC Payment · Loan Payment |
| Frequency | Monthly · Quarterly · Half Yearly · Annually · Alternate Year |
| Due Day of Month | 1–28 |
| Start Date | First occurrence date |
| Amount (₹) | Leave blank for variable-amount reminders (e.g. credit card bill) |
| No. of Payments | Optional cap; e.g. "24" for a 2-year SIP; a hint shows the expected last payment date |
| From Account | Source account |
| To Account | Visible for Transfer, Investment, CC Payment, Loan Payment types |
| Category / Sub-category | Optional |
| Auto-record after N days | If enabled, Sanchay records this automatically N days after the due date passes without manual action |

**Investment schedules** have additional fields depending on the investment type (same fields as the Investment transaction form).

Click **Save**. The schedule appears in the Recurring screen grouped by status.

### 6.2 Recording & Skipping Due Items

The **Dashboard** shows all overdue and today-due recurring items.

**To record a due item:**
1. Click the **✓** button on the Dashboard card, or go to **Recurring** and click **Record** on the row.
2. The Record dialog opens with the date defaulting to today and the amount pre-filled (if fixed).
3. Adjust the date or amount if needed and click **Record**.

The schedule advances to the next due date.

**To skip a due item** (when you already recorded it separately, or it simply didn't occur):
1. Click the **≫** button on the Dashboard card or the **Skip** button in Recurring.
2. Read the confirmation: *"The schedule will advance to the next due date."*
3. Click **Skip**.

**To pause a schedule temporarily:** Open the schedule, click the menu (⋮), and select **Pause**. It will not appear as pending until you reactivate it.

---

## 7. Importing Bank Statements

Sanchay can import your bank or credit card statement from a copied CSV/text extract, matching imported rows against transactions you've already entered manually.

### 7.1 Preparing Your Statement

1. Log in to your bank's internet banking portal.
2. Download or view the transaction history for the desired period.
3. **Copy** the table (rows and columns) to your clipboard — most portals let you select all rows and Ctrl+C, or provide a "Download CSV" option you can then open and copy from.

Sanchay accepts:
- Comma-separated (CSV) text
- Tab-delimited text
- Either can be pasted directly from clipboard or loaded via file dialog

### 7.2 First-Time Column Mapping

The first time you import from a specific bank, Sanchay doesn't know which columns contain the date, description, and amount. The **Import Mapping** dialog opens automatically.

**Detected columns** are shown as teal chips at the top.

| Field | What to set |
|---|---|
| Date column | The column containing transaction dates |
| Description column | The column containing narration / description |
| Amount type | **Single column** (one amount with +/−) or **Separate Debit/Credit columns** |
| Amount column | If single; the column containing the amount |
| Debit column | If separate; the column for money out (may be blank for credit rows) |
| Credit column | If separate; the column for money in (may be blank for debit rows) |
| Date format | Pick from the dropdown or type your own (e.g. `dd/MM/yyyy`); auto-detected when you select the date column |

The **Import** button stays disabled until Date, Description, and Amount are all mapped. Once you click **Import**, the mapping is saved for this bank — future imports from the same bank skip this dialog.

### 7.3 Reviewing Import Results

After import, Sanchay shows a summary:

| Outcome | What it means |
|---|---|
| **Added** | New transaction created; not matched to any existing record |
| **Reconciled** | CSV row matched and merged with an existing manual transaction |
| **Skipped (duplicate)** | Row already exists in the database (same date + amount + description hash) |
| **Pending review** | Ambiguous matches that need your decision |

**Auto-categorization:** For rows with no manual match, Sanchay applies your Category Rules and Type Rules. Matched rows are marked `AUTO_CATEGORIZED` and shown with an amber indicator in the Transactions screen — click them to confirm or correct the category.

### 7.4 Handling Ambiguous Matches

If a CSV row matches more than one existing transaction, or a single manual transaction is claimed by multiple CSV rows, the **Ambiguous Match** dialog opens.

For each contested item, you see the CSV row on one side and the candidate manual transactions on the other. Choose:
- **Reconcile with this** — merge the CSV row into the selected manual transaction
- **Add as new** — create a new transaction and leave the manual ones untouched
- **Skip** — discard this CSV row entirely

For pending recurring schedule matches, a separate **Recurring Match** dialog shows the CSV row alongside the matching schedule occurrence. Confirm to reconcile, or add as new.

---

## 8. Categories

Categories classify your transactions for reporting. They are hierarchical: one level of parent → sub-category. Expense and income categories are managed separately.

Go to **Categories**.

### Add a category

1. Click **+ Add** under Expense or Income.
2. Type the category name (e.g. "Food & Dining") and press Enter or Save.

### Add a sub-category

1. Find the parent category row and click the **⋮** menu.
2. Select **Add Sub-category**.
3. Type the sub-category name (e.g. "Restaurants") and save.

### Other category actions (⋮ menu)

| Action | Notes |
|---|---|
| Rename | Changes the display name everywhere |
| Add Sub-category | Creates a child under this category |
| Reassign Transactions | Bulk-moves all transactions in this category to a different one; only enabled when transactions exist |
| Move to Category | (Sub-categories only) Moves sub-category under a different parent |
| Deactivate / Reactivate | Inactive categories are hidden from dropdowns but retain their transactions |
| Delete | Requires reassigning transactions first; shown in red |

### View transactions in a category

Click the **☰** button on any category or sub-category row to open a table of all transactions using it. Double-click any row to edit that transaction.

---

## 9. Dashboard

The Dashboard is your home screen. It shows:

**Summary cards** (top row):
- **Net Worth** — total across all active accounts
- **Bank Balance** — sum of all active bank accounts
- **Monthly Expenses** — current month's spending
- **Monthly Income** — current month's income

**Credit Card & Loans row:**
- **Credit Card Balance** — total outstanding across all cards (highlighted in red when non-zero)
- **Active Loans** — total outstanding principal

**Pending Recurring Transactions** — every overdue or due-today schedule. Use the **✓** and **≫** buttons to record or skip without leaving the Dashboard.

**Recent Transactions** — the last 10 transactions across all accounts.

If you haven't set up any accounts yet, a **Get Started** banner guides you through the first steps: Profile → Accounts → Categories.

---

## 10. Reports & Cash Flow Forecast

Go to **Reports** → **Cash Flow Forecast** tab.

The forecast projects account balances month by month. Select a date range and click **Generate Forecast**.

**What the forecast includes:**

- Scheduled recurring income and expenses over the period
- FD and RD maturity events (principal + interest credited on maturity date)
- AI-analysed expense patterns with seasonality factors (uses the history window set in Settings; default 12 months)
- Confidence-scored forecasts per expense sub-category

**Reading the forecast:**

- The main chart shows total balance (all eligible accounts combined) over time
- You can switch to individual account view to see each account's trajectory separately
- The panel below the chart shows projected total income and total expenses for the period
- Any **warnings** (e.g. "EMI not set up for loan account X") are listed; these indicate the forecast may be incomplete

> **Note:** Equity, Mutual Fund, and Provident Fund accounts are excluded from balance projections because their value is market-linked and cannot be reliably forecast.

**Forecast overrides:**

For specific months where you know the forecast will be wrong (e.g. a holiday month with higher spending), you can add a **ForecastOverride** for a given account and month. Overrides let you set an exact value or exclude a month entirely.

---

## 11. Financial Planning

Go to **Financial Plan**. This screen models your long-term wealth trajectory toward retirement.

The **KPI cards** at the top show:
- Current Age (from your "Self" member's date of birth)
- Years to Retirement / Years in Retirement
- Retirement Age (from plan parameters)
- Projected Future Earnings and Forecasted Corpus

### 11.1 Plan Parameters

Click **Edit Parameters** (or click directly on any field — they auto-save on focus loss).

| Parameter | Default | Notes |
|---|---|---|
| Retirement Age | 60 | Or set a Retirement Date directly |
| Life Expectancy | 80 | Used for post-retirement sustainability |
| Pre-Retirement Tax % | 30% | Applied to earnings before retirement |
| Post-Retirement Tax % | 20% | Applied to retirement withdrawals |
| Rate of Return — Equities | 12% | Annual |
| Rate of Return — Mutual Funds | 10% | Annual |
| Rate of Return — PF | 8.1% | Annual |
| Rate of Return — Post-Retire | 7% | Conservative rate for retirement corpus |
| Inflation | 6% | Annual; affects cost of living projections |
| Monthly Cost of Living | ₹1,50,000 | Target monthly spend in retirement |
| Employment Start Date | — | Used for gratuity calculation |
| Monthly SIP — MF | ₹10,000 | Ongoing SIP contribution |
| Monthly SIP — Equity | ₹5,000 | Ongoing SIP contribution |

All changes are saved immediately to `plan_params.json`.

### 11.2 Major Events

Major events are significant one-time or recurring expenses you want to plan for: children's education, a wedding, property purchase, overseas trip, medical fund, etc.

**Add a major event:**

1. Click **+ Add Event** in the Major Events card.
2. Fill in:

| Field | Notes |
|---|---|
| Event Name | Descriptive label (e.g. "Daughter's Wedding") |
| Type | One Time · Recurring |
| Recurrence | Monthly · Quarterly · Yearly — visible only for Recurring type |
| Forecasted Amount | Your estimate of the cost |
| Category | Expense category to link against actual transactions |
| Sub-category | Optional |
| Start Date | When you expect this expense |
| End Date | For recurring events; defaults to your retirement date |

3. Click **Save**.

As you record transactions that match the event's category over time, the **Actual** column updates automatically — so you can track forecast vs. reality.

**Edit or delete:** Double-click an event row, or select it and click **Edit**. In edit mode, a **Delete** button (red) appears.

---

## 12. Settings

Go to **Settings**.

### 12.1 Changing Your Data Folder

Under **Data**, click the folder path field and select a new folder. The app reloads immediately with all data from the new location.

Use cases:
- Move from local disk to a cloud-synced folder for automatic backup
- Switch to an external drive
- Share a data folder between two machines on the same OneDrive/Google Drive

> The old folder is not deleted or modified. Only `%APPDATA%\sanchay\app-config.json` is updated to point to the new location.

### 12.2 Appearance Settings

| Setting | Options | Effect |
|---|---|---|
| Date Format | DD/MM/YYYY · YYYY-MM-DD | How dates appear throughout the app |
| Year Format | Indian Financial Year · Calendar Year | Affects year grouping in reports |
| Expense Forecast Window | 3 · 6 · 12 · 18 · 24 months | How many months of history the cash flow forecast analyses for patterns |

Changes take effect immediately.

### 12.3 Creating a Backup

Under **Backup**, click **Create Backup**. Sanchay creates a timestamped ZIP archive of your entire data folder:

```
Sanchay_data_backup_20240315_143022.zip
```

The ZIP is saved alongside your data folder. Store it on a separate drive or email it to yourself for off-site protection.

> Backups capture all JSON data files but not the app config (`%APPDATA%\sanchay\app-config.json`). If you move to a new machine, copy the ZIP and then set the data folder path in Settings after first run.

---

## 13. Tips & Common Workflows

### Setting up a new bank account with existing history

1. Add the bank account with **Opening Balance** = your balance on a convenient cut-off date (e.g. start of current financial year).
2. Set **Opening Date** to that cut-off date.
3. Import your bank statement for the period from that date to today. This catches up all transactions without manual entry.

### Tracking a new Fixed Deposit

1. Add an Investment account with type **Fixed Deposits**.
2. Record an **Investment** transaction from your bank account to the new FD account.
3. Fill in the FD Reference No, Interest Rate, Maturity Date, and Maturity Amount.
4. The cash flow forecast will automatically credit the maturity amount on the maturity date.

### Handling a credit card bill payment

Every month:
1. Your credit card bill arrives (statement balance shown on the Accounts screen).
2. Pay via net banking.
3. Record a **CC Payment** transaction from your bank account to the credit card for the amount paid.

If you want a reminder, set up a **Recurring** schedule of type CC Payment with a variable amount (leave Amount blank) due a few days before the payment due date.

### Reconciling imported transactions with manual ones

If you record a transaction manually and then import a statement that includes the same transaction:
- Sanchay will automatically match and reconcile them if the date, amount, and description are close enough.
- The reconciled transaction keeps your manual category/notes and gets updated with the bank's date and import hash.
- If the match is ambiguous (e.g. two similar transactions on the same day), the Ambiguous Match dialog lets you decide.

### Keeping investments up to date

Investment account balances shown in Accounts are **book value** (total amount invested minus redeemed). To track current market value:

1. Open the investment account.
2. Click **Market Values**.
3. Add a snapshot with today's date and current NAV / market value.

The difference between book value and latest market value indicates unrealised gain or loss.

### Pausing a subscription without deleting the schedule

If a service is temporarily paused (gym during travel, streaming service on hold):
1. Go to **Recurring**.
2. Find the schedule and open its menu (⋮).
3. Select **Pause**.

The schedule stops appearing as pending until you **Reactivate** it. Your history of past recordings is preserved.

### Moving to a new computer

1. Create a **Backup** from Settings → it saves a ZIP of your data folder.
2. Copy the ZIP to the new machine and extract it to your preferred location.
3. Install Sanchay on the new machine.
4. On first run, when the wizard asks for a data folder, point it to the extracted folder.

All your accounts, transactions, and settings will be available immediately.

---
