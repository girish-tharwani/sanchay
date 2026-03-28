# Feature 3: Recording of Recurring Deposits (RD)

## Context
Investment transactions to a Recurring Deposit account currently have a free-text "RD Reference No" field and also ask for Interest Rate and Maturity Date — all manually entered. The feature requires the RD reference number to be a **dropdown populated from existing recurring schedules** linked to the selected account, and Interest Rate / Maturity Date to be removed from the transaction dialog (those belong on the schedule, not the transaction).

## What's already in place
- `InvestmentType.RECURRING_DEPOSIT` enum value — exists
- `AddEditRecurringDialog` — already has conditional RD fields (RD Ref, Rate, Maturity) when To Account is RD type
- RD ref stored in `RecurringTransaction.notes` as `"RD Ref: <value>"`
- `TransactionDialog` — has `invRdRefFld` (TextField), `invRdRateFld`, `invRdMaturityPicker` for RD type

## What changes

### Gap 1 — RD ref is free-text; should be a dropdown
Replace `invRdRefFld` (TextField) with `invRdRefCb` (editable ComboBox) populated from recurring schedules for the selected account.

### Gap 2 — Interest Rate and Maturity Date shown on transaction dialog
Remove those two rows from the RECURRING_DEPOSIT dynamic panel in TransactionDialog (they live on the schedule).

### Gap 3 — No validation if no RD schedules exist
Add a validation error at save time if no RD refs are available.

---

## Files to modify

| File | Change |
|------|--------|
| `src/main/java/com/sanchay/service/DataStore.java` | Add `getRdRefsForAccount(String accountId)` |
| `src/main/java/com/sanchay/ui/transactions/TransactionDialog.java` | Replace TextField with ComboBox; remove Rate/Maturity; update save/load |

---

## Implementation steps

### Step 1 — `DataStore.java`
Add after `getDistinctScheduleDescriptions()`:

```java
/**
 * Returns distinct RD reference numbers from recurring schedules
 * whose toAccountId matches the given account, sorted alphabetically.
 */
public List<String> getRdRefsForAccount(String accountId) {
    return recurring.stream()
            .filter(r -> accountId.equals(r.getToAccountId()))
            .map(r -> {
                String notes = r.getNotes();
                if (notes == null) return null;
                for (String line : notes.split("\n"))
                    if (line.startsWith("RD Ref: "))
                        return line.substring("RD Ref: ".length()).trim();
                return null;
            })
            .filter(ref -> ref != null && !ref.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.toList());
}
```

### Step 2 — `TransactionDialog.java`

**2a. Field declaration (line 89)**
Change:
```java
private TextField invRdRefFld,  invRdRateFld;
private DatePicker invRdMaturityPicker;
```
To:
```java
private ComboBox<String> invRdRefCb;
private TextField invRdRateFld;
private DatePicker invRdMaturityPicker;
```
(`invRdRateFld` and `invRdMaturityPicker` remain declared but are no longer assigned for RD — save code is null-checked so this is safe.)

**2b. Dynamic panel — RECURRING_DEPOSIT case (lines 1190–1198)**
Replace:
```java
case RECURRING_DEPOSIT -> {
    invRdRefFld         = tf("optional");
    invRdRateFld        = tf("e.g. 6.5");
    invRdMaturityPicker = new DatePicker();
    UiUtils.styleOnShow(invRdMaturityPicker);
    dynRow(g, 0, "RD Reference No",    invRdRefFld);
    dynRow(g, 1, "Interest Rate (%)",   invRdRateFld);
    dynRow(g, 2, "Maturity Date",       invRdMaturityPicker);
}
```
With:
```java
case RECURRING_DEPOSIT -> {
    invRdRefCb = new ComboBox<>();
    invRdRefCb.setEditable(true);
    invRdRefCb.setMaxWidth(Double.MAX_VALUE);
    invRdRefCb.setPromptText("Select RD reference");
    InvestmentAccount rdAcc = invDestCb.getValue();
    if (rdAcc != null)
        invRdRefCb.getItems().addAll(ds.getRdRefsForAccount(rdAcc.getId()));
    dynRow(g, 0, "RD Reference No*", invRdRefCb);
}
```

**2c. Save — RECURRING_DEPOSIT case (lines 710–719)**
Replace:
```java
case RECURRING_DEPOSIT -> {
    StringBuilder sb = new StringBuilder();
    appendNote(sb, "RD Ref",        invRdRefFld);
    appendNote(sb, "Interest Rate",  invRdRateFld);
    if (invRdMaturityPicker != null && invRdMaturityPicker.getValue() != null)
        sb.append("Maturity Date: ").append(
                invRdMaturityPicker.getValue().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("\n");
    if (userNotes != null) sb.append("Notes: ").append(userNotes);
    t.setNotes(sb.toString().stripTrailing());
}
```
With:
```java
case RECURRING_DEPOSIT -> {
    String rdRef = invRdRefCb != null ? invRdRefCb.getEditor().getText().trim() : null;
    if (rdRef == null || rdRef.isBlank()) {
        List<String> refs = ds.getRdRefsForAccount(dest.getId());
        if (refs.isEmpty())
            throw new IllegalArgumentException(
                "No RD schedules found for this account. Please create a recurring schedule first.");
        throw new IllegalArgumentException("Please select an RD reference number.");
    }
    StringBuilder sb = new StringBuilder();
    appendNote(sb, "RD Ref", rdRef);
    if (userNotes != null) sb.append("Notes: ").append(userNotes);
    t.setNotes(sb.toString().stripTrailing());
}
```

**2d. Load — RECURRING_DEPOSIT case (lines 981–987)**
Replace:
```java
case RECURRING_DEPOSIT -> {
    setText(invRdRefFld,  parseNote(t.getNotes(), "RD Ref"));
    setText(invRdRateFld,  parseNote(t.getNotes(), "Interest Rate"));
    setDateFromNote(invRdMaturityPicker, parseNote(t.getNotes(), "Maturity Date"));
    String userNotes = parseNote(t.getNotes(), "Notes");
    sharedNotes.setText(userNotes != null ? userNotes : "");
}
```
With:
```java
case RECURRING_DEPOSIT -> {
    if (invRdRefCb != null) {
        String rdRef = parseNote(t.getNotes(), "RD Ref");
        if (rdRef != null) invRdRefCb.getEditor().setText(rdRef);
    }
    String userNotes = parseNote(t.getNotes(), "Notes");
    sharedNotes.setText(userNotes != null ? userNotes : "");
}
```

**2e. Add a String overload for appendNote (line ~1342)**
```java
private void appendNote(StringBuilder sb, String key, String val) {
    if (val != null && !val.isBlank())
        sb.append(key).append(": ").append(val.trim()).append("\n");
}
```

---

## Verification
1. `bash build.sh compile` — clean
2. Create a Recurring Deposit investment account
3. Create a recurring schedule: type=Investment, To Account=the RD account, fill RD Reference No
4. Open New Transaction → type=Investment → select the RD account → "RD Reference No*" dropdown shows the schedule's RD ref; Interest Rate and Maturity Date rows absent
5. Select the ref → save → transaction saved with RD Ref in notes
6. Edit the transaction → RD ref pre-selected in dropdown
7. Try saving without selecting a ref → appropriate error shown
8. Try with an RD account that has no schedules → "No RD schedules found" error
