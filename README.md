# Personal Finance Tracking and Management Application
## Product Requirements Specification
**Updated:** March 2026 | **Platform:** Windows 11+ | **Currency:** INR

---

## Table of Contents
1. [Purpose and Scope](#1-purpose-and-scope)
2. [Technology Stack](#2-technology-stack)
3. [Application Structure and Navigation](#3-application-structure-and-navigation)
4. [Dashboard](#4-dashboard)
5. [Accounts Module](#5-accounts-module)
6. [Transactions Module](#6-transactions-module)
7. [Recurring Transactions](#7-recurring-transactions)
8. [Categories](#8-categories)
9. [Reports Module](#9-reports-module)
10. [Data Management](#10-data-management)
11. [Settings](#11-settings)
12. [Profile](#12-profile)
13. [Out of Scope — Initial Version](#13-out-of-scope--initial-version)
14. [UI and Visual Design Standards](#14-ui-and-visual-design-standards)

---

## 1. Purpose and Scope

This document specifies the requirements for a desktop personal finance management application for Windows. The application is designed for personal and family use — providing a single, shared view of all household finances including bank accounts, credit cards, loans, investments, and day-to-day transactions.

**Key design principles:**
- **Self-contained:** no database server or cloud service required
- **Portable:** packaged as a single executable (.exe) for easy sharing with friends and family
- **India-first:** all monetary values in INR; financial year follows the Indian tax year (April–March)
- **Family view:** one application instance serves the entire household; joint accounts are supported; transactions can optionally be tagged to a family member

---

## 2. Technology Stack

### 2.1 Language and Runtime

**Selected:** Java with JavaFX for the UI layer.

The application is packaged using **jpackage** (bundled with JDK 14+) which produces a self-contained Windows installer or a single-folder executable that includes the JVM — no Java installation required on the target machine.

**Packaging approach:**
- Use `jpackage --type exe` to produce a Windows installer, or `--type app-image` for a portable folder
- Bundle the JRE using `--runtime-image` so end users need no prior Java installation
- Target JDK 17 LTS or JDK 21 LTS for long-term support

### 2.2 Data Storage

All application data is stored as plain **JSON files** in a single user-designated folder. There is no dependency on any database engine.

**File layout:**
```
%APPDATA%\sanchay\
  app-config.json       # Pointer to data folder; written on first run

<data-folder>/
  accounts.json         # All account definitions
  transactions.json     # All recorded transactions
  recurring.json        # Recurring transaction schedules
  categories.json       # Expense and income categories (incchanluding sub-categories)
  members.json          # Family members (name, relationship, earning flag)
  settings.json         # App preferences and metadata
  import_mappings.json  # CSV column-mapping per account (one record per account)
```

**Design rules:**
- Each JSON file is an array of objects; one record per array element
- All monetary values stored as integers in **paise** (1 INR = 100 paise) to avoid floating-point errors
- Dates stored as ISO 8601 strings: `YYYY-MM-DD`
- Each record carries a system-generated UUID as its primary key
- Files are human-readable and can be version-controlled or backed up by simply copying the folder

> **Note:** The application displays the active data folder path in Settings and provides a one-click backup option.

### 2.3 App Configuration File

A lightweight configuration file at `%APPDATA%\sanchay\app-config.json` stores only the pointer to the user's chosen data folder. This is separate from `settings.json` which lives inside the data folder.

```json
{
  "dataFolderPath": "C:\\Users\\Girish\\Documents\\PersonalFinance",
  "appVersion": "0.1.0-SNAPSHOT"
}
```

This file is created during the First-Run Wizard (§3.0) and updated whenever the user relocates the data folder from Settings.

---

## 3. Application Structure and Navigation

### 3.0 First-Run Wizard

On the very first launch (i.e., `app-config.json` does not exist, or exists but points to a non-existent folder), the application displays a **First-Run Wizard** instead of the main shell.

**Folder selection page:**
- Displays the application name, a short explanatory message, and a **Browse…** button that opens the OS folder picker
- A read-only text field shows the selected path; the **Get Started** button is disabled until a folder is chosen
- When the selected folder already contains data files (`accounts.json`, `transactions.json`, or `categories.json`), a status label confirms "Existing data found" and the button label changes to **Open Existing Data** — preferences dialog is skipped
- When the selected folder is empty or new, a status label confirms "New folder" — clicking **Get Started** opens the **Preferences dialog** (see below) before closing the wizard

**Preferences dialog (fresh setups only):**
- A modal dialog prompting for: **Currency** (INR), **Year Format** (Indian Financial Year / Calendar Year), and **Date Format** (DD/MM/YYYY / YYYY-MM-DD)
- If the dialog is dismissed without confirming, defaults are used
- Selected preferences are written to `settings.json` immediately after data is first loaded

**Recovery mode** (folder missing):
- If `app-config.json` exists but the recorded path no longer exists on disk, the wizard opens in recovery mode with a warning showing the missing path
- The user can point to the relocated folder (existing data auto-detected) or choose a new empty folder (preferences dialog shown)

### 3.1 Overall Layout

The main window is a single resizable frame divided into three permanent zones that are always visible regardless of which module is active:

```
┌─────────────────────────────────────────────────────────────┐
│  TOP BAR: App title                                         │
├──────────────┬──────────────────────────────────────────────┤
│              │                                              │
│   SIDEBAR    │            MAIN PANEL                       │
│              │                                              │
│  Dashboard   │   Context-sensitive content for the         │
│  Accounts    │   selected module                           │
│  Recurring   │                                              │
│  Reports     │                                              │
│  Categories  │                                              │
│              │                                              │
│  ──────────  │                                              │
│  Profile     │                                              │
│  Settings    │                                   [ + ]      │
└──────────────┴──────────────────────────────────────────────┘
```

| Zone | Description |
|------|-------------|
| **Top bar** | App name only; no other controls |
| **Left sidebar** | Fixed-width navigation with icon + label for each module; Profile and Settings pinned to the bottom |
| **Main panel** | Full content area for the active module; scrollable as needed |
| **Floating '+' button** | Persistent button anchored to the bottom-right corner of the main panel |

### 3.2 Left Sidebar

The sidebar is **fixed width** (not collapsible) and displays each navigation item as an icon paired with a text label. The currently active module is highlighted.

**Navigation items (top to bottom):**

| Position | Item | Icon (suggested) | Navigates to |
|----------|------|-----------------|--------------|
| 1 | Dashboard | Home / house | Dashboard screen |
| 2 | Accounts | Bank / wallet | Accounts list screen |
| 3 | Recurring | Repeat / cycle | Recurring schedules screen |
| 4 | Reports | Bar chart | Reports screen |
| 5 | Categories | Tag / label | Categories screen |
| — | *(spacer)* | — | — |
| Bottom (1) | Profile | Person / silhouette | Profile screen |
| Bottom (2) | Settings | Gear | Settings screen |

> **Note:** There is no standalone Transactions screen. All transaction history is accessed per account via the **Transactions** button on each account card. The Recurring screen is the central place to manage all recurring schedules. The Dashboard pending widget links directly to the Recurring screen.

### 3.3 Top Bar

The top bar is a slim, fixed-height bar running the full width of the window above the sidebar and main panel.

| Element | Position | Behaviour |
|---------|----------|-----------|
| Application name / logo | Left | Static; non-interactive |

The top bar contains no other controls. Date filtering is handled independently within each screen (Reports screen, Account Transaction History). The quick-add button lives in the main panel, not the top bar.

### 3.4 Floating Quick-Add Button

A circular **'+'** button is permanently anchored to the **bottom-right corner** of the main panel. It is visible on every screen.

**Behaviour on click:**
- Opens a **New Transaction** modal dialog. A **Type** dropdown at the top selects from seven types: **Expense · Income · Transfer · Refund · Investment · CC Payment · Loan Payment**. Type-specific fields swap in below the shared Date / Description / Amount row; shared fields are always visible.
- The user fills in the form and clicks **Save** to post the transaction, or **Cancel** to discard

---

## 4. Dashboard

The dashboard is the home screen shown on application launch. It provides a household-level financial snapshot without requiring navigation into individual modules. All figures are read-only computed summaries. Clicking any widget navigates to the relevant detailed view.

**Summary widgets (top row):**
- Total Bank Balance — computed running balance across all active bank accounts (opening balance ± all transactions)
- Credit Card Balance — total outstanding across all active credit cards (sum of expenses charged minus payments made)
- Total Investments — sum of invested amounts across all investment accounts (opening balance + all investment transactions)
- Total Loan Outstanding — sum of outstanding principal across all active loan accounts (opening outstanding minus all Loan Payment and Transfer transactions made to each loan account)

**Pending Recurring Transactions widget:**
- Lists all recurring transaction instances that are due within the next 7 days (or overdue)
- Each row shows: transaction type tag (fixed-width), description, amount, due date, and two action buttons: **Record** and **Skip**
- Clicking **Record** opens a confirmation dialog (see §7.2)
- Clicking **Skip** advances the schedule without posting a transaction
- Clicking the widget heading navigates to the Recurring screen

> **Note:** The Recent Transactions widget shows the 10 most recent transactions across all accounts. Full transaction history is accessed per account from the Accounts screen.

---

## 5. Accounts Module

The Accounts module allows the user to set up and manage all financial accounts used by the household. Accounts are grouped into four categories: Bank Accounts, Credit Card Accounts, Loan Accounts, and Investment Accounts.

**Common behaviour across all account types:**
- Each account has a unique user-defined name and an optional **Description** (one line; shown on the account tile and details screen)
- All accounts carry a non-editable **Currency** field fixed to INR
- Accounts support a **Joint / Add-on Account** flag; when enabled, a second account holder name field is shown
- Accounts can be marked as **Active** or **Closed** (or type-specific closed states: Blocked/Cancelled for credit cards, Settled for loans, Redeemed for investments) via the Status dropdown in the Edit dialog
- Closed accounts are hidden by default; checking **"Show Closed"** in the group header reveals them on the Accounts screen
- Closed accounts are excluded from all transaction entry dropdowns and dashboard calculations
- Deleting an account is not permitted if it has associated transactions; the user must close it instead

**Each account card on the list screen shows two action buttons:**

| Button | Behaviour |
|--------|-----------|
| **Details** | Opens the account detail / edit screen showing all account fields in an editable form. Changes are saved on confirmation. A **Back button** is always visible at the top-left of the Details screen; pressing it returns the user to the Accounts list screen. |
| **Transactions** | Opens the account transaction history screen for that specific account — a filterable, searchable list of all transactions affecting that account, with a running balance. |

### Account Transaction History Screen

Accessible via the **Transactions** button on any account card. Shows all transactions for that account.

**Features:**
- **Back button** — always visible at the top-left of this screen; pressing it returns the user to the Accounts list screen. This is the only navigation out of this screen; the sidebar is not sufficient on its own because the account list rebuilds on each navigation.
- Running balance column showing balance after each transaction
- **Sub-category column** — displayed after the Category column; shows the sub-category name where one has been assigned, or a dash otherwise
- Search by description / notes (free text)
- Filter by amount range
- Filter by category
- Filter by date range (defaults to current financial year)
- Filter by transaction type (Expense / Income / Transfer / Investment / CC Payment / Loan Payment)
- Sortable by date, amount, or category
- Export to CSV

> New transactions are added via the floating **'+'** button, not from within this screen.

---

### 5.1 Bank Accounts

#### 5.1.1 Savings Account

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Account Name | Text | User-defined label (e.g., "HDFC Primary Savings") |
| Description | Text | Optional one-line description |
| Sub-Type | Dropdown | Savings / Current |
| Currency | Text (read-only) | Fixed to INR |
| Status | Dropdown | Active / Closed |
| Account Number | Text | Display masked after entry (show last 4 digits only) |
| Bank Name | Text | Name of the bank |
| Branch Name | Text | Optional |
| Opening Date | Date (DD/MM/YYYY) | Date the account was opened |
| Opening Balance | Currency (INR) | Balance at the time of first entry into this app |
| Account Holder | Text | Primary account holder name |
| Joint Account | Boolean | Toggle; reveals second holder name field if enabled |
| Second Holder Name | Text | Visible only when Joint Account is enabled |
| IFSC Code | Text | Optional |
| Notes | Text (multi-line) | Optional |

#### 5.1.2 Current Account

Same fields as Savings Account. No additional fields required.

---

### 5.2 Credit Card Accounts

Credit cards are modelled as liability accounts. Spending on a credit card is recorded as an Expense against the card. Paying the credit card bill is recorded as a separate **Credit Card Payment** transaction (see Section 6.5) that moves money from a bank account to the card, reducing the outstanding balance.

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Account Name | Text | e.g., "HDFC Regalia" |
| Card Number | Text | Last 4 digits only |
| Bank Name | Text | |
| Card Network | Dropdown | Visa / Mastercard / Amex / RuPay |
| Credit Limit | Currency (INR) | |
| Payment Due Day | Integer (1–28) | Day of month bill payment is due |
| Opening Balance | Currency (INR) | Outstanding at time of first entry |
| Add-on Card | Boolean | Toggle; reveals add-on holder name field |
| Add-on Holder Name | Text | Visible only when Add-on Card is enabled |
| Status | Dropdown | Active / Blocked / Cancelled |
| Notes | Text (multi-line) | Optional |

> On saving a credit card account, the application automatically creates a **recurring transaction** (type: CC Payment, amount: blank/variable, frequency: Monthly) as a payment reminder. The amount is left blank since it varies each month — the user adjusts it when recording. See Section 7.

---

### 5.3 Loan Accounts

#### 5.3.1 Common Loan Fields (Home / Vehicle / Personal)

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Account Name | Text | e.g., "ICICI Home Loan" |
| Loan Account Number | Text | Reference number from the lender |
| Bank / Lender Name | Text | |
| Loan Type | Read-only | Home / Vehicle / Personal |
| Opening Date | Date | Date the loan account was first entered into the app (required for amortization schedule) |
| Disbursement Date | Date | Date loan amount was received |
| Loan Amount | Currency (INR) | Total sanctioned amount |
| Interest Rate | Decimal % | Current annual rate; can be overwritten when rate changes |
| Loan Tenure (Months) | Integer | Total repayment period |
| EMI Amount | Currency (INR) | Monthly instalment amount |
| EMI Due Date | Integer (1–28) | Day of month on which EMI is due |
| Outstanding Principal | Currency (INR) | Opening outstanding balance at time of first entry; displayed outstanding is computed dynamically as this value minus all Loan Payment (and Transfer) transactions made to the loan account |
| Joint Account | Boolean | Toggle; reveals co-applicant name field |
| Co-applicant Name | Text | Visible only when Joint Account is enabled |
| Status | Dropdown | Active / Closed / Settled |
| Notes | Text (multi-line) | Optional |

> The Loan Account Add/Edit dialog correctly shows the Joint Account toggle and Co-applicant Name field.

**Interest rate changes:** When a floating rate changes, the user simply overwrites the Interest Rate field. The previous rate is not retained . Rate change history is a future enhancement.

> On saving a loan account, the application automatically creates a **recurring transaction** (type: Expense, category: EMI / Loan Repayment, frequency: Monthly) for the EMI amount from the user's chosen bank account. See Section 7.

#### 5.3.2 Vehicle Loan — Additional Fields

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Vehicle Registration No. | Text | Optional |
| Vehicle Description | Text | e.g., "Maruti Swift 2023" |

> **Future enhancement:** Auto-generate full amortization schedule (monthly breakdown of principal and interest) from loan parameters.

---

### 5.4 Investment Accounts

Investment accounts use a **bucket model**: one account represents a broad investment vehicle (e.g., "Mutual Funds — Equity", "Direct Equity — Zerodha", ICICI Fixed Deposits, ICICI Recurring Deposits). Individual SIPs, purchases, and redemptions are recorded as transactions against the bucket account. There is no separate sub-account per SIP scheme or per FD etc.

#### 5.4.1 Investment Account Fields

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Account Name | Text | User-defined bucket label (e.g., "All Mutual Funds") |
| Description | Text | Optional one-line description; shown on the account tile and details screen |
| Investment Type | Dropdown | Mutual Funds / Equities / Debt Bonds / Fixed Deposits / Recurring Deposits / Provident Fund |
| Currency | Text (read-only) | Fixed to INR |
| Account Number | Text | Folio or account reference number from the institution |
| Institution Name | Text | e.g., "Zerodha", "Groww", "HDFC AMC" |
| Opening Date | Date | Date the account or folio was opened |
| Invested Amount | Currency (INR) | Computed as: opening balance entered at account creation + sum of all Investment transactions posted to this account |
| Status | Dropdown | Active / Closed / Redeemed |
| Notes | Text (multi-line) | Optional |

**Default accounts seeded on first run:**

| Name | Type | Description |
|------|------|-------------|
| All Equities | Equities | This is where you record all your equity transactions. |
| All Mutual Funds | Mutual Funds | This is where you record all your mutual funds transactions. |
| All Bonds | Debt Bonds | This is where you record all your bonds transactions. |

> **Future enhancement:** Track current market value / NAV for investment accounts.

#### 5.4.2 SIP as Recurring Transactions

Each SIP running within a bucket account is modelled as a **recurring investment transaction**:

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Description | Text | e.g., "HDFC Flexi Cap Fund – SIP" |
| Amount | Currency (INR) | Monthly SIP instalment |
| From Account | Dropdown | Source bank account |
| To Investment Account | Dropdown | Destination investment bucket |
| Frequency | Dropdown | Monthly / Quarterly |
| SIP Date | Integer (1–28) | Day of month for deduction |
| Start Date | Date | First SIP date |
| End Date | Date | Optional; leave blank for open-ended SIPs |

> See Section 7 (Recurring Transactions) for how recurring schedules are managed and how pending instances are surfaced to the user.

#### 5.4.3 Fixed Deposit (FD) as Recurring Transactions

Each FD running within a bucket account is modelled as a **recurring investment transaction**:

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Account Name | Text | e.g., "SBI FD – June 2024" |
| FD Reference Number | Text | Reference number from the bank |
| Bank Name | Text | |
| Opening Date | Date | Date of FD creation |
| Principal Amount | Currency (INR) | Amount deposited |
| Interest Rate | Decimal % | Annual interest rate (e.g., 6.5) |
| Maturity Date | Date | Date when FD matures |
| Maturity Amount | Currency (INR) | Expected amount on maturity (manual entry) |
| Interest Payout | Dropdown | Cumulative / Monthly / Quarterly / Annually |
| Joint Account | Boolean | Toggle; reveals second holder name field |
| Second Holder Name | Text | Visible only when Joint Account is enabled |
| Status | Dropdown | Active / Matured / Renewed / Broken |
| Notes | Text (multi-line) | Optional |

#### 5.4.4 Recurring Deposit (RD) as Recurring Transactions

Each RD running within a bucket account is modelled as a **recurring investment transaction**:

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Account Name | Text | |
| RD Reference Number | Text | |
| Bank Name | Text | |
| Opening Date | Date | |
| Monthly Instalment | Currency (INR) | Amount deposited each month |
| Tenure (Months) | Integer | Total duration |
| Interest Rate | Decimal % | Annual interest rate |
| Maturity Date | Date | Auto-calculated: opening date + tenure months |
| Maturity Amount | Currency (INR) | Expected amount on maturity |
| Opening Balance | Currency (INR) | Total already deposited before the app was set up (for accounts that pre-date app entry) |
| Linked Bank Account | Dropdown | Bank account from which instalments are debited |
| Joint Account | Boolean | Toggle |
| Second Holder Name | Text | Visible only when Joint Account is enabled |
| Status | Dropdown | Active / Matured / Closed |
| Notes | Text (multi-line) | Optional |

> On saving an RD account, the application automatically creates a **recurring transaction** (type: Transfer, frequency: Monthly) for the instalment amount from the linked bank account into the RD account. See Section 7.

---

## 6. Transactions Module

The Transactions module is where the user records all money movements. There are seven transaction types: **Expense, Income, Transfer, Refund, Investment, Credit Card Payment,** and **Loan Payment**. All transactions are entered via a unified dialog with a **Type** dropdown; type-specific fields swap in below the shared Date / Description / Amount row. The same dialog is used for both new transactions and editing existing ones (double-click a row in any transaction table to edit). All transactions carry a financial year tag derived from their date.

**Common behaviour across all transaction types:**
- Every transaction has a system-generated UUID
- Transactions can be edited or deleted after entry
- All amounts stored in paise; displayed in INR
- Transactions support an optional **Family Member** tag (free text; e.g., "Rahul", "Priya") for household attribution
- Filterable by date range, account, type, category, and family member
- Free-text search across description and notes fields

**New Transaction dialog — field label readability:**
All field label text in the New Transaction dialog (and any other modal dialog) must be rendered in a colour that is legible against the dialog's background. On a grey dialog background, white field labels are not permitted — labels must use a dark text colour (e.g., `#1A1A2E` or the application's standard dark foreground colour) to ensure sufficient contrast.

### 6.1 Expense

Records money spent from a bank account **or a credit card**.

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Date | Date | Date of the expense |
| Description | Text | e.g., "Electricity Bill – March" |
| Category | Dropdown | From user-defined expense categories |
| Sub-category | Dropdown | Optional; child of selected category; populated based on selected category |
| Amount | Currency (INR) | |
| Paid From Account | Dropdown | Active bank accounts **and active credit cards** |
| Payment Mode | Dropdown | UPI / Net Banking / Debit Card / Credit Card / Cash / Cheque / Auto-debit |
| Family Member | Text | Optional; household attribution |
| Reference / UTR No. | Text | Optional |
| Notes | Text (multi-line) | Optional |

> When a credit card is selected in **Paid From Account**, Payment Mode auto-selects **Credit Card** and the transaction increases the outstanding balance on that card.

### 6.2 Income

Records money received into a bank account.

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Date | Date | Date income was received |
| Description | Text | e.g., "Monthly Salary – March 2026" |
| Income Type | Dropdown | Salary / Interest / Dividend / Rental / Freelance / Gift / Other |
| Category | Dropdown | From user-defined income categories |
| Sub-category | Dropdown | Optional; child of selected category; populated based on selected category |
| Amount | Currency (INR) | |
| Credited To Account | Dropdown | Active bank account |
| Source | Text | Optional; e.g., employer name |
| Family Member | Text | Optional |
| Notes | Text (multi-line) | Optional |

### 6.3 Transfer

Records movement of funds between accounts. Typically bank-to-bank (e.g., transferring to an emergency fund or a joint account). For repaying a loan EMI, prefer the dedicated **Loan Payment** type (§6.7).

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Date | Date | |
| Description | Text | e.g., "Transfer to Emergency Fund" |
| Amount | Currency (INR) | |
| From Account | Dropdown | Source bank account |
| To Account | Dropdown | Active bank accounts **and active loan accounts** |
| Category | Dropdown | Optional; from expense categories (e.g., EMI / Loan Repayment) |
| Sub-category | Dropdown | Optional; child of selected category; populated when category is selected |
| Notes | Text (multi-line) | Optional |

### 6.4 Refund

Records money returned to the user — e.g., an online shopping return, insurance claim reimbursement, or cashback credited to an account. The amount is credited back to a bank account or credit card.

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Date | Date | Date the refund was received |
| Description | Text | e.g., "Amazon Return — Order #12345" |
| Amount | Currency (INR) | |
| Refunded Into | Dropdown | Active bank accounts and active credit cards |
| Category | Dropdown | Original expense category the refund relates to |
| Sub-category | Dropdown | Optional; child of selected category |
| Payment Mode | Dropdown | UPI / Net Banking / Debit Card / Credit Card / Cash / Cheque / Auto-debit |
| Family Member | Text | Optional |
| Reference / UTR No. | Text | Optional |
| Notes | Text (multi-line) | Optional |

### 6.5 Investment Transaction

Records money moved from a bank account into an investment bucket account. The form dynamically adapts based on the selected investment account's type to capture type-specific transaction details.

**Base fields (all investment types):**

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Date | Date | |
| Description | Text | e.g., "HDFC Flexi Cap Fund SIP – March 2026" |
| Amount (₹) | Currency (INR) | Amount being invested |
| From Account | Dropdown | Source bank account |
| To Investment Account | Dropdown | Destination investment bucket (active investment accounts only); type-specific additional fields appear automatically once an account is selected |
| Notes | Text (multi-line) | Optional |

**Additional fields — Mutual Funds, Equity, Debt Bonds:**

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Scheme / Script Name | Text | Optional; specific fund name or stock ticker within the bucket (e.g., "HDFC Flexi Cap Fund", "RELIANCE") |
| Units / NAV | Decimal | Optional; number of units purchased or NAV at time of purchase |

**Additional fields — Fixed Deposit (FD):**

| Field | Type / Format | Notes |
|-------|--------------|-------|
| FD Reference Number | Text | Optional; reference number from the bank |
| Interest Rate (%) | Decimal | Annual interest rate for this FD |
| Maturity Date | Date | Date when FD matures |
| Maturity Amount (₹) | Currency (INR) | Expected maturity amount (manual entry) |

**Additional fields — Recurring Deposit (RD):**

| Field | Type / Format | Notes |
|-------|--------------|-------|
| RD Reference Number | Text | Optional; reference number from the bank |
| Interest Rate (%) | Decimal | Annual interest rate for this RD |
| Maturity Date | Date | Date when RD matures |

**Behaviour:**
- When the user selects a **To Investment Account**, the type-specific additional fields appear immediately below; they replace whatever fields were shown for the previously selected type
- All additional fields are optional — saving with only the base fields populated is valid
- FD/RD-specific fields are stored in the transaction's notes field in a structured format until a dedicated FD/RD transaction sub-model is introduced

### 6.6 Credit Card Payment

Records the payment of a credit card bill from a bank account. This reduces the outstanding balance on the credit card and reduces the bank account balance by the same amount. It does not affect net worth (the liability and the asset reduce equally).

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Date | Date | Date the payment was made |
| Description | Text | e.g., "HDFC Regalia Bill Payment – March 2026" |
| Amount | Currency (INR) | Amount paid; can be minimum due, full outstanding, or any amount in between |
| Paid From Account | Dropdown | Source bank account |
| Credit Card | Dropdown | The credit card account being paid |
| Payment Mode | Dropdown | NEFT / IMPS / UPI / Auto-debit |
| Reference / UTR No. | Text | Optional |
| Notes | Text (multi-line) | Optional |

> A Credit Card Payment does **not** count as an Expense — it is a settlement of an existing liability, not new spending. The original credit card expenses have already been recorded and categorised individually.

### 6.7 Loan Payment

Records a repayment made from a bank account towards a loan (home, vehicle, or personal). This reduces both the bank account balance and the outstanding loan principal. It is semantically distinct from a Transfer — the **To Account** dropdown is restricted to active loan accounts only, making it impossible to accidentally route a payment to a bank account.

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Date | Date | Date the payment was made |
| Description | Text | e.g., "ICICI Home Loan EMI – March 2026" |
| Amount | Currency (INR) | Amount paid toward the loan |
| From Bank Account | Dropdown | Source bank account |
| Loan Account | Dropdown | Active loan accounts only |
| Category | Dropdown | Optional; from expense categories (e.g., EMI / Loan Repayment) |
| Sub-category | Dropdown | Optional; child of selected category |
| Payment Mode | Dropdown | UPI / Net Banking / NEFT / IMPS / Auto-debit / etc. |
| Reference / UTR No. | Text | Optional |
| Notes | Text (multi-line) | Optional |

> **Balance impact:** The bank account balance decreases by the payment amount. The outstanding loan principal decreases by the same amount. Net worth is unchanged (liability and asset reduce equally).

> **Backward compatibility:** Existing Transfer transactions that were previously used to record loan repayments continue to be counted correctly in loan outstanding calculations.

---

## 7. Recurring Transactions

Recurring transactions allow the user to define a scheduled transaction that repeats at a set frequency. They are used for SIP instalments, RD instalments, loan EMIs, credit card bill payments, rent, salaries, and any other predictable money movement.

### 7.1 Recurring Transaction Fields

| Field | Type / Format | Notes |
|-------|--------------|-------|
| Description | Text | e.g., "HDFC Home Loan EMI" |
| Transaction Type | Dropdown | Expense / Income / Transfer / Investment / CC Payment |
| Category | Dropdown | From expense or income categories (shown when relevant to transaction type) |
| Sub-category | Dropdown | Optional; child of selected category |
| Amount | Currency (INR) | Leave blank for variable-amount schedules (e.g., CC bill) |
| From Account | Dropdown | Source bank account |
| To Account | Dropdown | Shown for Transfer (bank accounts + loan accounts), Investment (investment accounts), and CC Payment (credit card accounts) types only; hidden for Expense and Income |
| Frequency | Dropdown | Monthly / Quarterly / Annually / Alternate Year |
| Due Day of Month | Integer (1–28) | Day each instance falls due |
| Start Date | Date | Date the schedule begins |
| End Date | Date | Optional; leave blank for open-ended schedules |
| Status | Dropdown | Active / Paused / Completed |

**Additional fields for Investment type** (shown dynamically based on selected investment account type):

| Investment Account Type | Additional Fields |
|------------------------|-------------------|
| Mutual Funds / Equity / Debt Bonds | Scheme / Script Name (optional), Units / NAV (optional) |
| Fixed Deposit | FD Reference No (optional), Interest Rate %, Maturity Date, Maturity Amount (optional) |
| Recurring Deposit | RD Reference No (optional), Interest Rate %, Maturity Date, Opening Balance (optional), Maturity Amount (optional) |

> Investment-specific additional fields are stored as dedicated typed fields on the recurring schedule record (not in the `notes` field).

**All Schedules table columns:**

| Column | Notes |
|--------|-------|
| Description | Schedule name |
| Type | Transaction type |
| Frequency | Monthly / Quarterly / Annually / Alternate Year |
| Amount | Fixed amount or "Variable" |
| Next Due | Next calculated due date |
| Status | Active / Paused / Completed |
| Category | Linked expense or income category |
| Sub-category | Linked sub-category, if set; dash otherwise |
| Actions | ⏸ / ▶ icon button to pause or resume; double-click the row to edit |

### 7.2 Recording a Pending Recurring Transaction

When a recurring transaction is due, it appears in the **Pending Recurring Transactions** list on the Dashboard and on the Recurring screen. The user records an instance by clicking **Record**.

**Record dialog fields:**

| Field | Notes |
|-------|-------|
| Transaction Date | Date picker; defaults to today; editable |
| Amount | Pre-filled from the schedule; editable for variable-amount schedules |
| Paid From Account | Pre-filled from the schedule; editable for this occurrence only |

When the user confirms:
- A new `Transaction` record is created and saved to `transactions.json`
- The transaction carries all fields from the recurring schedule, including **category ID and sub-category ID**
- The recurring schedule's `lastRecordedDate` is updated
- The pending item **immediately disappears** from the pending list without requiring screen navigation

> The sub-category from the recurring schedule is always copied to the posted transaction.

- The user may also **Skip** an occurrence (e.g., if an EMI was already auto-debited and recorded separately)
- Recurring transactions do **not** post automatically — user confirmation is always required

### 7.3 Auto-created Recurring Transactions by Account Type

| Account Type | Auto-created Recurring | Type | Amount | Due Day |
|---|---|---|---|---|
| Recurring Deposit | RD instalment transfer | Transfer | Monthly instalment | User-specified |
| Home / Vehicle / Personal Loan | EMI payment reminder | Expense | EMI amount | EMI due date |
| Credit Card | Bill payment reminder | CC Payment | Blank (varies monthly) | Payment due date |

**Earnings-linked schedules** (auto-created from Profile → Earnings Configuration):

| Trigger | Auto-created Recurring | Type | Amount | Notes |
|---|---|---|---|---|
| Structured Salary saved | Salary income deposit | Income | Net In-hand | Into the configured salary bank account |
| Structured Salary saved (PF account linked) | PF deposit | Investment | (24% + VPF%) × Basic+DA | Directly into PF account; no source bank account |

Both earnings-linked schedules are paused/resumed together whenever the member's Earning checkbox is toggled.

---

## 8. Categories

### 8.1 Default Expense Categories and Sub-categories

| Category | Default Sub-categories |
|----------|----------------------|
| Housing | Rent, Maintenance, Society Charges, Property Tax, Home Insurance |
| Utilities | Electricity, Water, Gas, Internet, Mobile Recharge, DTH / Cable |
| Groceries | Supermarket, Vegetables & Fruits, Dairy, Dry Groceries |
| Food & Dining | Restaurants, Food Delivery, Coffee & Tea, Snacks |
| Transport | Fuel, Parking, Cab / Auto, Public Transport, Vehicle Maintenance |
| Healthcare | Doctor / Physician, Medicines / Prescriptions, Lab Tests, Dental, Eyecare, Hospital, Health Insurance |
| Education | School Fees, Tuition, Books & Stationery, Online Courses, Exam Fees |
| Shopping | Clothing & Apparel, Electronics, Home Goods, Personal Care |
| Entertainment | Movies & Events, OTT Subscriptions, Gaming, Hobbies |
| EMI / Loan Repayment | *(auto-linked when EMI recurring transactions are recorded; no sub-categories)* |
| Taxes | Advance Tax, TDS, Professional Tax, GST |
| Miscellaneous | Any expense not fitting other categories |

> **Note:** Credit Card Payment is **not** an expense category. It is a separate transaction type (Section 6.5) and does not appear in expense category reports.

### 8.2 Default Income Categories

| Category | Notes |
|----------|-------|
| Salary | Primary employment income |
| Interest | Savings account interest, FD interest |
| Dividend | Stock dividends, mutual fund payouts |
| Rental Income | Rent received from property |
| Freelance / Business | Consulting, self-employment income |
| Gift / Bonus | One-time receipts |
| Miscellaneous | Other income |

Income categories support sub-categories. Sub-categories can be added via the Categories screen (§8.3) and are available in the Income transaction form.

### 8.3 Categories Screen

The **Categories** screen is a dedicated top-level screen accessible from the left sidebar. It replaces and supersedes the "Open Category Manager" link that previously appeared in Settings.

**Display:**
- All categories listed in two groups: **Expense Categories** and **Income Categories**
- Each category row shows: name, type, active/inactive status
- Inactive categories are shown in a visually muted style but remain in the list
- Each category can be expanded to show its sub-categories (if any)

**Actions available on each category row:**
- **Rename** — opens an inline or dialog input to change the category name; the new name is reflected immediately in all transaction displays
- **Deactivate / Reactivate** — toggles the category's active flag; deactivated categories are hidden from transaction entry dropdowns but are preserved in historical transaction records and reports
- **Delete** — only permitted if the category has **zero** associated transactions (across all sub-categories too); if transactions exist the delete button is disabled or shows an explanatory tooltip
- **Show Transactions** — opens a modal or inline panel listing **all transactions across all accounts** that use this category (or any of its sub-categories), sorted by date descending; filterable by date range

**Adding a new category:**
- An **"+ Add Category"** button at the top of each group opens a small dialog asking for: name, type (Expense or Income)
- After saving, the new category appears immediately in the list and is available in all transaction entry dropdowns

### 8.4 Sub-categories

Sub-categories allow finer-grained expense tracking within a parent category. They are optional — a transaction may be assigned a category without specifying a sub-category.

**Data model:**
- Each sub-category belongs to exactly one parent category
- Sub-categories are supported under both **Expense** and **Income** categories
- A sub-category inherits the active/inactive status of its parent; if a parent category is deactivated, all its sub-categories are also hidden from entry dropdowns
- Sub-categories have their own active/inactive flag independently of the parent

**Sub-category management (within the Categories screen):**
- Expanding a category row reveals its sub-categories
- Each sub-category row supports: **Rename**, **Deactivate / Reactivate**, **Delete** (only if zero transactions use it), and **Show Transactions**
- The **Show Transactions** button on a sub-category row correctly filters transactions by that sub-category's ID. Transactions that have the sub-category's ID in either `categoryId` or `subCategoryId` are included
- An **"+ Add Sub-category"** button within an expanded category opens a dialog with a single field: sub-category name (parent is implicit)

**Transaction entry behaviour:**
- When a user selects a category in the Expense transaction form, the Sub-category dropdown is populated with that category's active sub-categories
- Sub-category selection is optional — leaving it blank is valid
- If no sub-categories exist for the selected category, the Sub-category dropdown is hidden or shown as disabled

**Seeded sub-categories on first run:**
The default sub-categories listed in §8.1 are automatically seeded into `categories.json` on first write, alongside their parent categories.

---

## 9. Reports Module

### 9.1 Monthly Expense Summary

The primary report. Displays a breakdown of all expenses for a selected period **across bank accounts and credit cards combined**.

**Date pickers:**

The Monthly Expense Summary provides two independent date pickers that allow flexible period selection:

| Picker | Type | Behaviour |
|--------|------|-----------|
| **Financial Year** | Dropdown | Lists available financial years (e.g., "FY 2025-26", "FY 2024-25"); selecting a year aggregates all expense data for the full April–March period |
| **Month** | Dropdown | Lists the last 24 months, each qualified with its year (e.g., "Mar 2026", "Feb 2026", "Jan 2026", "Dec 2025", "Nov 2025" …); selecting a month overrides the FY picker and shows data for that specific month only |

- Both pickers are independent; the user may use either one at a time
- Changing either picker immediately refreshes **all** report components below — summary totals, category table, bar chart, trend line, and transaction list — without requiring any additional user action
- The default state on first opening the screen is the current month selected in the Month picker
- The FY picker defaults to the current financial year

**Components:**
- Total expenses for the selected period (bank account expenses + credit card expenses combined)
- Expenses grouped by category — table showing category, transaction count, and total amount; includes expenses paid from any bank account **or** any credit card
- Bar chart: category-wise expense breakdown for the selected period (bank + credit card combined)
- Trend line: monthly total expenses for the last 12 months (bank + credit card combined); always shown regardless of the picker state
- Transaction list: **all** expense transactions for the period — both bank account expenses and credit card expenses — sortable by date, amount, or category; includes a Sub-category column
- Account column in transaction list indicates whether each expense was paid from a bank account or which credit card

### 9.2 Credit Card Expense Report

A dedicated report for understanding credit card spending, kept as a separate tab from the Monthly Expense Summary.

**Date pickers:**

The same two-picker approach (Financial Year and Month, as described in §9.1) applies to the Credit Card report. Both pickers immediately refresh all components when changed.

**Components:**
- Card selector — dropdown to choose a specific card or view all cards combined. **Changing this selector must immediately refresh all report components**
- Total spent on card(s) for the selected period
- Outstanding balance as of end of selected period
- Expenses grouped by category — showing what the card was used for
- Bar chart: category-wise breakdown of card spending
- Payments made during the selected period — list of CC Payment transactions
- Transaction list: all expenses charged to the selected card(s) for the period

### 9.3 Account Statement View

Available from within each account's detail screen. Shows a chronological list of all transactions affecting that account with a running balance. Filterable by date range. Exportable to CSV.

For credit card accounts the running balance reflects the **outstanding amount owed** — increasing with expenses, decreasing with payments.

### 9.4 Future Reports (Planned)

- Net worth over time
- Investment portfolio performance and current market valuation
- Loan amortization and repayment progress tracker
- Annual income vs expense summary aligned to Indian tax year
- Family member-wise expense breakdown
- Sub-category drill-down within category reports

---

## 10. Data Management

### 10.1 Persistence Model

#### 10.1.1 Startup Sequence

Every time the application launches, it follows this decision tree before showing any UI:

```
1. Resolve config file path:
       %APPDATA%\sanchay\app-config.json

2. Does app-config.json exist?
       NO  → Show First-Run Wizard (§3.0)
       YES → Read dataFolderPath from it

3. Does the dataFolderPath folder exist on disk?
       NO  → Show First-Run Wizard with "folder not found" message
       YES → Proceed

4. Read data files from dataFolderPath:
       accounts.json, transactions.json, recurring.json,
       categories.json, members.json, settings.json

5. Any file missing?
       YES → Treat as empty (initialise with defaults); do not block launch
       NO  → Parse and load all records into memory

6. Store dataFolderPath in DataStore so Settings screen can display it correctly

7. Show main application shell → Dashboard
```

> Step 6 is explicitly required. The DataStore must have the resolved data folder path set before any screen is constructed so that the Settings screen displays the correct path.

#### 10.1.2 Write Strategy — Save on Every Mutation

The application uses a **save-on-mutation** strategy: every time the user creates, edits, or deletes a record (account, transaction, recurring schedule, category, setting), the relevant JSON file is rewritten to disk immediately. There is no explicit "Save" button anywhere in the UI.

This means:
- Data is never lost if the app is closed unexpectedly (only the in-progress dialog would be lost, not already-confirmed records)
- Each JSON file is always in a consistent state on disk

**File write procedure for each mutation:**
1. Serialise the full in-memory list for that data type to JSON
2. Write to a temporary file in the same data folder (e.g. `accounts.json.tmp`)
3. Atomically rename `.tmp` → `.json` (replaces the old file)
4. Delete `.tmp` if the rename fails (do not leave partial files)

#### 10.1.3 Default Content on First Write

When a data file is written for the first time (i.e., the file did not exist before), the following defaults are seeded:

| File | Default content |
|------|----------------|
| `categories.json` | All 12 default expense categories with their sub-categories (§8.1) and 7 default income categories (§8.2) |
| `settings.json` | Theme = Light; date format = DD/MM/YYYY |
| `members.json` | Empty array |
| `accounts.json` | Three default investment accounts: All Equities (Equities), All Mutual Funds (Mutual Funds), All Bonds (Debt Bonds). If the file is corrupt on load, it is replaced with the same defaults. |
| `transactions.json` | Empty array |
| `recurring.json` | Empty array |
| `import_mappings.json` | Empty array |

#### 10.1.4 Data Folder Relocation (from Settings)

The user can switch the active data folder at any time from the Settings screen. No restart is required — the app reloads in-session immediately.

When the user selects a new folder:

1. The app presents a folder picker
2. The user selects the new folder
3. `app-config.json` is updated with the new path (no files are moved — the app switches, not migrates)
4. If the new folder is empty, the **Preferences dialog** is shown to set currency, year format, and date format before loading
5. DataStore is reset and all data is reloaded from the new folder
6. All cached screens are discarded and the app navigates to Dashboard

> **Data migration:** Moving files to the new folder must be done manually outside the app. The app only updates the pointer — it does not copy or move JSON files between folders.

#### 10.1.5 Members Data Schema

Family members are stored as structured objects in **`members.json`**. The file is a **flat JSON array** — there is no wrapper object.

```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "name": "Girish",
    "relationship": "SELF",
    "earning": true
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440002",
    "name": "Priya",
    "relationship": "SPOUSE",
    "earning": false
  },
  {
    "id": "550e8400-e29b-41d4-a716-446655440003",
    "name": "Arjun",
    "relationship": "CHILD",
    "earning": false
  }
]
```

| Field | Type | Notes |
|-------|------|-------|
| `id` | UUID string | System-generated; used as the stable primary key for edit and remove operations |
| `name` | String | Display name; must be unique (case-insensitive) within the household |
| `relationship` | Enum string | One of: `SELF`, `SPOUSE`, `CHILD`, `SIBLING`, `PARENT`, `OTHER` |
| `earning` | Boolean | Whether this member has their own income; used for future reporting features |

---

## 11. Settings

| Setting | Type | Notes |
|---------|------|-------|
| Data Folder Path | Directory picker | Displays current configured path; selecting a new folder reloads the app in-session immediately |
| Currency | Dropdown | INR (currently the only option) |
| Year Format | Dropdown | Indian Financial Year (Apr–Mar) / Calendar Year (Jan–Dec); affects financial year labels and date range filters throughout the app |
| Date Format | Dropdown | DD/MM/YYYY (default) / YYYY-MM-DD |
| Backup | Button | Triggers one-click backup of the entire data folder to a timestamped ZIP file |
| About / Version | Read-only | App version, data schema version, platform |

> **Note:** Family Members is managed on the **Profile** screen (§12). Category Manager is on the **Categories** screen (§8.3). Each screen (Reports, Account Transaction History) manages its own date filter independently.

---

## 12. Profile

The **Profile** screen is a dedicated top-level screen accessible from the left sidebar (pinned near the bottom, above Settings). It houses user and household identity information. More sections will be added here in future versions.

### 12.1 Family Members

The Family Members section in Profile is a **fully functional** editable list that records the name, relationship, and earning status of each household member.

**Display:**

The family members list is presented as a table with the following columns:

| Column | Notes |
|--------|-------|
| Name | Member's name |
| Relationship | One of: Self, Spouse, Child, Sibling, Parent, Other |
| DOB | Date of birth formatted as `dd MMM yyyy`; shows `—` if not set |
| Earning? | Checkbox; checking/unchecking inline toggles the earning flag and pauses or resumes all linked schedules immediately |
| Monthly In-hand | Computed monthly net in-hand for configured earning members; "Not configured" for earning members with no earnings entered; "—" for non-earning members |
| ₹ | Visible only for earning members; opens the Earnings Configuration dialog |
| Actions | Remove button per row; double-click to edit |

**Add Member dialog fields:**

| Field | Type | Notes |
|-------|------|-------|
| Name | Text | Required; must be non-empty and unique (case-insensitive) |
| Relationship | Dropdown | Self / Spouse / Child / Sibling / Parent / Other |
| Date of Birth | Date picker | Optional |
| Earning | Checkbox | Indicates whether this member has their own income; unchecked by default |

**Actions:**
- **Add Member** — opens the Add Member dialog; on confirmation the new member appears immediately; if Earning is checked, the Earnings Configuration dialog opens automatically
- **Edit** (double-click) — opens the same dialog pre-filled; toggling Earning off pauses the linked recurring schedule; toggling it on resumes it (or opens Earnings Configuration if not yet set up)
- **Remove** — asks for confirmation; removes the member; the member's name is **not** removed from existing transactions
- **₹ button** — visible only for earning members; opens the Earnings Configuration dialog to create or update the earnings details at any time

**Earnings Configuration dialog** (opened via ₹ button or on first Earning=true save):

*Simple tab* — Amount (₹), Frequency, Into Account, Day of Month, Schedule Description, Income Category. Creates one monthly/periodic INCOME recurring schedule.

*Structured Salary tab* — Basic+DA, HRA, Other Allowances, Estimated Tax Rate %, VPF %, Into Account, Day of Month, Schedule Description, Income Category, PF Account. A live breakdown panel shows:

| Row | Calculation |
|-----|-------------|
| Gross Monthly | Basic+DA + HRA + Other Allowances |
| Employee PF (12% + VPF) | (12% + VPF%) × Basic+DA |
| Estimated Monthly TDS | (Gross − 12% of Basic+DA) × Tax Rate% — VPF is not tax-exempt |
| **Net In-hand** | Gross − Employee PF − TDS |
| Employer EPF | (12% of Basic+DA) − ₹1,250 |
| EPS | ₹1,250 (fixed cap) |
| Total Employer Cost | Gross + (12% of Basic+DA) |
| Monthly PF Deposit | (24% + VPF%) × Basic+DA — total credited to PF account |

The Net In-hand figure becomes the INCOME recurring schedule amount. If a PF account is selected, a second INVESTMENT recurring schedule is created for the Monthly PF Deposit amount, deposited directly into the PF account (no source bank account). Both schedules share the same day of month. The employer contribution rows are informational only.

**Validation:**
- Member names must be non-empty
- Duplicate names are not permitted (case-insensitive check)

---

## 13. Out of Scope — Initial Version

The following features are explicitly deferred to future versions:

- Cloud sync or multi-device access
- Bank statement import via PDF or OFX format
- FD / RD maturity alerts and push notifications
- Loan amortization schedule auto-generation
- Investment current market value / NAV lookup
- Budget planning and budget vs actuals tracking
- ITR-relevant summaries and 80C tracking
- Mobile application
- Multi-user login with per-user access control
- Interest rate change history on floating-rate loans
- Credit card reward points tracking
- Credit card statement PDF import
- Moving sub-categories between parent categories

---

## 14. UI and Visual Design Standards

This section captures binding visual design rules that apply across the entire application. These rules take precedence over any implicit or conventional defaults in the UI framework.

### 14.1 Colour Use on Dashboard Tiles

- All monetary amount values displayed on dashboard summary tiles must use a **single, consistent colour** throughout — either the application's standard foreground/text colour or one designated accent colour applied uniformly.
- It is **not permitted** to assign different colours to amounts on different tiles (e.g., green for income, red for loans, blue for bank balance). Such per-tile colour coding creates visual noise and is explicitly disallowed.
- Status-based colour (e.g., red for a negative net worth) is permitted only when the colour conveys a meaningful state change on a single value, not as a general tile-differentiation scheme.

### 14.2 Pending Recurring Transactions Widget

- The background of the Pending Recurring Transactions widget (on the Dashboard and within the Recurring module) must be **subtle and low-contrast** — for example, a light grey (`#F5F5F5` in light theme) or a very desaturated surface colour consistent with the application theme.
- Saturated, bright, or otherwise visually loud background colours are explicitly disallowed for this widget.

### 14.3 Transaction Type Tag Alignment in Pending Recurring List

- Each row in the Pending Recurring list displays a transaction type tag (e.g., "Expense", "Income", "Transfer", "Investment", "CC Payment") followed by the transaction description.
- Tags must be rendered at a **fixed minimum width** sufficient to accommodate the longest possible tag text ("CC Payment" in the current set). This can be achieved via a fixed-width label, a badge with `min-width`, or equivalent layout constraint.
- The transaction description text must begin at the same horizontal position on every row, producing a clean left-aligned column of descriptions regardless of how many characters the preceding tag contains.

### 14.4 Table Row Selection and Hover

- When a row is selected in any transaction table (Account Transaction History, Reports transaction list, etc.), the selected row's background and text colours must maintain **sufficient contrast** for comfortable reading.
- A light blue row background combined with white text is **not permitted** — this combination fails readability at typical monitor brightness settings.
- Acceptable approaches include:
  - A medium-to-dark selection background (e.g., the theme's primary colour at full or near-full opacity) with **white or light text**, or
  - A light selection background (e.g., a pale tint of the primary colour) with **dark text** (not white).
- **Row hover highlight is not used.** No background colour change is applied when the mouse hovers over any table row (selected or unselected). The hover highlight was removed as it was visually distracting during scrolling.
- The chosen selection style must be consistent across all tables in the application.

### 14.5 First-Run Wizard — Text Must Not Be Clipped

All text in the First-Run Wizard — including the welcome message, instructional prompts, and confirmation text — must be fully visible without truncation or clipping.

**Required behaviour:**
- Text labels and message areas must use **word wrap** and must not truncate content with ellipsis (`...`) or cut off at a container boundary.
- The welcome message container must be sized with a minimum height that fits the full message at the application's default window size.
- If the message is long, the container may scroll, but it must never silently hide content.
- This rule applies to the wizard's folder selection page and to the Preferences dialog.

### 14.6 Dialog Field Label Text Colour

Field labels inside modal dialogs (New Transaction, Edit Transaction, Record Recurring, and any other dialog) must use a **dark, readable text colour** regardless of the dialog's background colour.

**Required behaviour:**
- On a grey or white dialog background, field labels must use a dark colour (e.g., `#1A1A2E`, `#333333`, or the application's standard dark foreground) — white or near-white label text is not permitted on light backgrounds.
- This applies to all form field labels throughout the application, not only the New Transaction dialog.
