# Sanchay — User Guide

**Build Agami | Version v1.0.0 | April 2026 | Windows 11+**

Sanchay is a personal finance app for Indian households. It runs entirely on your Windows PC — no internet connection, no account, no cloud. All your data is stored as plain files in a folder you choose.

---

## Quick Start Guide

### What Sanchay Does

| Feature | Summary |
|---|---|
| **Accounts** | Track bank accounts, credit cards, loans, and investments |
| **Transactions** | Record income, expenses, transfers, CC payments, EMIs, and investments |
| **Import** | Import CSV statements from your bank; auto-matches against existing records |
| **Recurring** | Manage EMI schedules, SIPs, salary — get reminders for what's due |
| **Reports** | Monthly and yearly expense breakdown by category |
| **Categories** | Customise expense and income categories to your household |
| **Profile** | Track family members and their income |

### First Launch

1. Run `Sanchay.exe`.
2. The **First Run Wizard** opens. Click **Browse** and choose (or create) a folder where your financial data will be stored — for example `D:\PersonalFinance`. You can put this folder inside OneDrive or Google Drive for automatic backup.
3. Click **Get Started**. The main window opens.

### Adding Your First Account

1. Go to **Accounts** (sidebar).
2. Click **+ Add** next to the account type you want (Bank, Credit Card, Loan, or Investment).
3. Fill in the name, type, and opening balance, then click **Save**.

### Adding a Transaction

1. Click the **+** button (floating, bottom-right of every screen).
2. Select the transaction type (Expense, Income, Transfer, etc.).
3. Fill in the date, description, amount, and account, then click **Save**.

### Importing a Bank Statement

1. Go to **Accounts**, then click **≡** on your bank account to open its transactions.
2. Click **Import CSV** and select your downloaded bank statement.
3. Map the columns (date, debit, credit, description) — Sanchay remembers this for next time.
4. Review the imported rows. Auto-categorized rows show a **?** badge; click the badge to accept the suggestion.

### Reviewing What's Due

The **Dashboard** shows all recurring transactions due within the next 7 days. Click **✓** to record one or **≫** to skip it. The **Recurring** screen shows all schedules.

---

## Detailed Guide

### Navigation

The left sidebar is always visible and contains:

```
Dashboard    — Household snapshot
Accounts     — All financial accounts
Recurring    — Schedules and reminders
Reports      — Spending analysis
Categories   — Manage categories
─────────────
Profile      — Family members and earnings
Settings     — Data folder, date format, backups
Help (?)     — Getting-started guide
```

Click the **+** button (floating, bottom-right) from any screen to add a new transaction. The sidebar also acts as the window drag handle — click and drag anywhere on it to move the window.

---

### Dashboard

The dashboard is your home screen. It shows:

**Summary cards (top row)**

| Card | What it shows |
|---|---|
| Net Worth | Total assets minus liabilities |
| Bank Balance | Combined balance of all active bank accounts |
| Monthly Expenses | All expense transactions in the current month |
| Monthly Income | All income transactions in the current month |

**Credit card & loan row**

Shows outstanding balance and available credit for each active credit card, and the count of active loans.

**Pending recurring transactions**

Lists every scheduled transaction that is due within the next 7 days or is overdue. For each item:
- Click **✓** to record it (opens a confirmation dialog where you can adjust the amount and date).
- Click **≫** to skip this occurrence and move the schedule to the next due date.

**Recent transactions**

The last 10 transactions recorded across all accounts.

---

### Accounts

#### Account Types

| Type | Sub-types | What it tracks |
|---|---|---|
| **Bank** | Savings, Current | Running balance |
| **Credit Card** | — | Outstanding balance, available credit |
| **Loan** | Home, Vehicle, Personal | Outstanding principal |
| **Investment** | Mutual Fund, Equity, Bond, FD, RD | Total invested amount |

#### Account Groups

Accounts are grouped by type. Each group has a **▸/▾ toggle** — click the group header to collapse or expand it. The collapsed/expanded state is remembered across restarts. Closed accounts are hidden by default; tick **Show Closed** to reveal them.

#### Adding an Account

Click **+ Add** on a group header and fill in the details. Fields vary by type:

- **Bank:** Account number, bank name, account holder, IFSC, branch, opening balance.
- **Credit Card:** Card number, issuer, credit limit, billing cycle day, payment due days, cardholder name.
- **Loan:** Lender, loan type, amount, interest rate (% p.a.), tenure (months), EMI amount, EMI due day, disbursement date. Saving a loan account automatically generates an amortization schedule and creates a recurring EMI reminder.
- **Investment:** Investment type, folio/account number.

> **Joint accounts:** Toggle **Joint Account** on any account type and enter the second holder's name.

#### Account Details

Click **ⓘ** on an account card to open its detail and edit screen. All fields are editable here, including status (Active / Closed / Blocked / Settled).

**Loan repayment schedule:** Inside a loan account's detail screen, click **View Repayment Schedule** to see a full amortization table — month by month breakdown of principal, interest, and running balance.

#### Account Transactions

Click **≡** on an account card to open the transaction list for that account.

**Filtering:**
- Date range (defaults to the current financial year, April–March)
- Category and sub-category dropdowns
- Transaction type dropdown
- Amount range
- Free-text search (matches description, notes, reference numbers)
- **Show pending review only** — filters to rows with an **i** or **?** badge (imported rows that haven't been reviewed)

**Sorting:** Click any column header to sort.

**Editing:** Double-click any row to open the transaction for editing.

**Exporting:** Click **⬇ Download CSV** to export the currently filtered transactions. The app remembers the last folder you exported to.

---

### Transactions

#### Transaction Types

| Type | Use for |
|---|---|
| **Expense** | Any spending — groceries, bills, fuel, etc. |
| **Income** | Salary, freelance, rental, dividends, etc. |
| **Transfer** | Moving money between two bank accounts |
| **Refund** | A refund credited back to a bank account or card |
| **Investment** | Investing money from a bank account into an investment account (MF, FD, RD, equity) |
| **CC Payment** | Paying your credit card bill from a bank account |
| **Redeem** | Withdrawing from an investment account back to a bank account |
| **Loan Payment** | Paying an EMI or part-payment against a loan |

#### Adding a Transaction

Click the **+** button from any screen. Select the type, fill in the fields, and click **Save**. The dialog stays open if there are validation errors — fix them and save again.

**Common fields (all types):**
- Date (defaults to today)
- Description (autocomplete shows past descriptions as you type; press Tab to accept the top suggestion)
- Amount (₹)
- Notes (optional)

**Type-specific fields:**

*Expense*
- From Account (bank or credit card)
- Category / Sub-category
- Payment Mode (UPI, Net Banking, Debit Card, Cash, etc.)
- Family Member (autocomplete from your family list)
- Ref / UTR No (optional)

*Income*
- To Account (bank only)
- Category / Sub-category
- Source (e.g. employer name)
- Family Member

*Transfer*
- From Account / To Account (both bank accounts)

*Refund*
- To Account, Category, Payment Mode, Family Member, Ref / UTR No

*Investment — Mutual Fund / Equity*
- From Account (bank) → To Account (investment)
- Scheme name, units purchased, NAV

*Investment — Fixed Deposit*
- From Account → To Account
- FD reference number, interest rate, maturity date, maturity amount

*Investment — Recurring Deposit*
- From Account → To Account (RD account)
- RD Reference No (dropdown from linked schedules — must be set up in Recurring first)

*CC Payment*
- From Account (bank) → To Account (credit card)

*Redeem*
- From Account (investment) → To Account (bank)
- Principal amount (amount of invested capital being withdrawn)
- Gain/Loss is calculated automatically; the category list switches between Income (gain) and Expense (loss) depending on whether you made or lost money

*Loan Payment*
- From Account (bank or CC) → To Account (loan)
- Principal portion (pre-filled from the amortization schedule for that date; interest is calculated automatically)
- Payment Mode, Ref / UTR No

#### Editing a Transaction

Double-click any row in a transaction table to edit it. Note:
- Editing an imported row (**i** badge) resets it to a manually-entered transaction.
- Editing an auto-categorized row (**?** badge) downgrades it to imported status; you can then edit and save.

#### Deleting a Transaction

Select a row and use the delete button. You'll be asked to confirm. For **Redeem** transactions, deleting any one of the three linked records (investment-side, bank-side, and gain/loss) will delete all three — the confirmation dialog will warn you.

#### Understanding the Badges

Badges appear in the source indicator column of transaction tables:

| Badge | Meaning |
|---|---|
| **i** | Imported from CSV; category not yet reviewed |
| **?** | Imported and auto-categorized; click badge to accept the suggestion |
| *(none)* | Manually entered |
| **✓** | Reconciled — matched and merged with an existing transaction during import |

---

### Importing Bank Statements

1. Open an account's transaction list (**≡** button on the account card).
2. Click **Import CSV** and select the CSV file you downloaded from your bank's website.

**Column mapping**

Sanchay detects headers automatically. In the mapping dialog, assign:
- **Date column** (required)
- **Debit column** and **Credit column** — or a single amount column plus a direction column
- **Description column** (required)
- **Reference column** (optional)

Click **Save Mapping** — Sanchay remembers this setup for the same account next time.

**Auto-categorization**

After import, Sanchay applies your category rules (configured in **Categories**) to assign categories automatically. These rows get a **?** badge. Click the **?** to accept the suggestion, or double-click the row to edit the category.

**Reconciliation**

Sanchay tries to match each imported row against existing manually entered transactions (within a ±2-day window, exact amount, similar description). If a match is found, the import row is merged with the existing one and marked **✓** — no duplicate is created.

If an imported row could match more than one existing transaction, an **Ambiguous Match** dialog appears — pick the correct match or add it as a new transaction.

Imported rows are also matched against pending recurring schedule occurrences (±2-day date window, ±5% amount, similar description). A **Recurring Match** dialog confirms the link.

**Deduplication**

Each import row is hashed. Re-importing the same file will silently skip rows already in your data.

---

### Recurring Transactions

The **Recurring** screen manages all scheduled transactions — EMIs, SIPs, salary, rent, etc.

**Pending section (top)**

Shows everything due in the next 7 days or overdue. Record or skip from here, or go to the full list below.

**All Schedules table**

Shows every schedule with its frequency, amount, next due date, and status (Active / Paused / Completed).

- **Double-click** a row to edit the schedule.
- **✓** — Record this occurrence now.
- **‖/▶** — Pause or resume the schedule.
- **×** — Delete the schedule (with confirmation).

#### Adding a Recurring Schedule

Click **+ Add** in the All Schedules section header.

Key fields:
- **Description** (with autocomplete)
- **Type** — same types as one-off transactions
- **Frequency** — Monthly, Quarterly, Annually, or Alternate Year
- **Due Day of Month** — which day each occurrence falls on (1–28; editable directly)
- **Start Date / End Date** — leave End Date blank for open-ended schedules
- **Amount** — leave blank for variable amounts (e.g. CC payment reminders)
- **From / To Account** — as appropriate for the type
- **Auto-record after (days)** — if set, Sanchay will automatically record the transaction N days after the due date without asking. Leave at 0 to always prompt manually.

#### Auto-created Schedules

Sanchay automatically creates recurring schedules when you add certain account types:
- **Loan account** → monthly EMI reminder (amount pre-filled from loan details)
- **Credit card** → monthly payment reminder (amount variable)
- **Recurring Deposit investment account** → monthly instalment transfer

You can find and edit these in the All Schedules table.

#### Recording a Recurring Transaction

Click **✓** on any pending row. The Record dialog pre-fills the amount and accounts from the schedule. You can adjust the amount or date before confirming. After recording, the schedule advances to the next due date.

---

### Reports

The Reports screen has two tabs.

**Monthly Expense Summary**

Select a **month** or a full **financial year** (April–March) from the pickers. The chart shows expenses by category, sorted by amount. Toggle **Show sub-categories** to expand each bar into its constituent sub-categories.

Click **⬇ Download CSV** to export the chart data.

**Credit Card Report**

Spending analysis per credit card — category breakdown, billing date indicators, and payment patterns.

---

### Categories

Categories are used to tag transactions for reporting. There are separate lists for Expense categories and Income categories. Each category can have one level of sub-categories.

#### Adding a Category

Click **+ Add** in either the Expense or Income section. Enter a name and save.

#### Adding a Sub-category

Click **⋮** on a category row and choose **Add Sub-category**.

#### Managing Categories

The **⋮** menu on each category or sub-category row offers:

| Option | What it does |
|---|---|
| Add Sub-category | Adds a child to this category |
| Rename | Renames the category |
| Reassign Transactions | Moves all tagged transactions to a different category |
| Deactivate / Reactivate | Hides/shows in transaction entry dropdowns (historical data is preserved) |
| Delete | Removes the category (only allowed if no transactions are tagged to it) |

#### Category Rules for Auto-import

Category rules tell Sanchay how to auto-categorize imported CSV rows. You configure them in the **Categories** screen. Rules match on keywords in the transaction description — when a match is found, the category (and optionally sub-category) is auto-applied and the row gets a **?** badge.

---

### Profile

The Profile screen tracks family members and their income. This lets you tag transactions to a specific person and understand household earnings.

#### Adding a Family Member

Click **+ Add Member**. Enter the name, date of birth (optional), and relationship (Self, Spouse, Child, Parent, Other).

#### Earnings

Tick **Earning?** for any member who earns income. Then click the **₹** button on that row to configure their earnings:

- **Earning type** — Salary, Freelance, Dividends, Rental, Business, or Other
- Monthly in-hand amount (and optional PF / tax deductions for salary earners)

When an earning member is configured, Sanchay creates a recurring income schedule for them automatically. If you untick **Earning?**, the schedule is paused.

When recording transactions, the **Family Member** field in the transaction dialog offers an autocomplete list of your family members, so each expense or income can be attributed to the right person.

---

### Settings

| Setting | Description |
|---|---|
| **Data Folder** | Shows the current data folder path. Click **Change…** to move your data to a different folder (takes effect on next restart). |
| **Date Format** | Choose between DD/MM/YYYY and YYYY-MM-DD. Takes effect immediately across the whole app. |
| **Backup Now** | Creates a timestamped ZIP of your entire data folder (`PersonalFinance_backup_YYYYMMDD_HHmmss.zip`). You choose where to save it. |

---

### Data and Privacy

All data is stored as plain JSON files in the folder you chose during setup. There is no cloud connection, no telemetry, and no account required.

**Where your data lives:**

| File | Contents |
|---|---|
| `accounts.json` | All account definitions |
| `transactions.json` | All recorded transactions |
| `recurring.json` | Recurring schedules |
| `categories.json` | Expense and income categories |
| `members.json` | Family members |
| `settings.json` | App preferences |
| `import_mappings.json` | CSV column mappings per account |
| `loan_schedules/` | Amortization tables for loan accounts |

The app config (which folder to open) is stored separately at `%APPDATA%\sanchay\app-config.json`.

**Backing up:** Use **Settings → Backup Now** for a one-click ZIP backup, or simply copy the data folder to another location. The files are human-readable and can be put under version control.

**Moving to a new PC:** Copy the data folder to the new machine, install Sanchay, and on first run point the wizard to the copied folder. All your data will appear immediately.

---

### Tips and Notes

- **Financial year** runs April to March (Indian tax year). Date range pickers and report filters default to the current financial year.
- **All amounts are in INR (₹).** Values are stored internally in paise (integer arithmetic) to avoid rounding errors.
- **Autocomplete** is available for descriptions and categories in all transaction and recurring dialogs. Start typing and a dropdown appears; press **Tab** to accept the top match.
- **Account groups are collapsed by default.** Click a group header to expand it and see account cards.
- **Closed accounts** are hidden by default. Tick **Show Closed** inside an expanded group to reveal them.
- **Redeem transactions** create three linked records (investment-side, bank-side, and gain/loss). Deleting any one deletes all three.
- **RD (Recurring Deposit) accounts** require an active recurring schedule with an RD Reference No before you can record investment transactions to them.
- **Loan amortization** is generated automatically when you add a loan. If you change the loan details, the schedule regenerates. The principal portion of each EMI is pre-filled when you record a Loan Payment.
