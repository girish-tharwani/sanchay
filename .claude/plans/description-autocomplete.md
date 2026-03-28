# Feature 1: Description Autocomplete

## Context
The Description field on New/Edit Transaction and New/Edit Schedule dialogs is a plain TextField with no suggestions. Users have to retype common descriptions every time. Autocomplete will offer inline completions (greyed suffix, selected) drawn from previously stored descriptions.

---

## Requirements
- Inline suggestion: as user types, the first starts-with match is shown as a highlighted suffix
- Typing replaces the selection; Backspace removes only the highlighted suffix → natural flow
- Transaction dialog → pool from past transaction descriptions only
- Schedule dialog → pool from past recurring schedule descriptions only
- Case-insensitive prefix match

---

## Files to modify

| File | Change |
|------|--------|
| `src/main/java/com/sanchay/service/DataStore.java` | Add `getDistinctTransactionDescriptions()` and `getDistinctScheduleDescriptions()` |
| `src/main/java/com/sanchay/ui/UiUtils.java` | Add `wireDescriptionAutocomplete(TextField, List<String>)` |
| `src/main/java/com/sanchay/ui/transactions/TransactionDialog.java` | Wire autocomplete on `sharedDesc` |
| `src/main/java/com/sanchay/ui/recurring/AddEditRecurringDialog.java` | Wire autocomplete on `descFld` |

---

## Implementation steps

### Step 1 — `DataStore.java`
Add two methods after the existing `getRecurring()` area:

```java
/** Distinct non-blank transaction descriptions, sorted alphabetically. */
public List<String> getDistinctTransactionDescriptions() {
    return transactions.stream()
            .map(Transaction::getDescription)
            .filter(d -> d != null && !d.isBlank())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
}

/** Distinct non-blank recurring schedule descriptions, sorted alphabetically. */
public List<String> getDistinctScheduleDescriptions() {
    return recurring.stream()
            .map(RecurringTransaction::getDescription)
            .filter(d -> d != null && !d.isBlank())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());
}
```

### Step 2 — `UiUtils.java`
Add a new static method after `wireAutoComplete()` (after line 285):

```java
/**
 * Wires inline starts-with autocomplete on a plain TextField.
 * As the user types, the first matching suggestion is completed inline
 * with the suffix selected — further typing replaces it naturally.
 */
public static void wireDescriptionAutocomplete(TextField field, List<String> suggestions) {
    boolean[] suppress = {false};
    field.textProperty().addListener((obs, old, text) -> {
        if (suppress[0]) return;
        if (text == null || text.isEmpty()) return;
        // Only complete on forward typing (text grew)
        if (old != null && text.length() <= old.length()) return;
        String lower = text.toLowerCase();
        suggestions.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .findFirst()
                .ifPresent(match -> {
                    suppress[0] = true;
                    field.setText(match);
                    field.selectRange(text.length(), match.length());
                    suppress[0] = false;
                });
    });
}
```

### Step 3 — `TransactionDialog.java`
After `sharedDesc` is initialized (line ~115, where `setPromptText` is called), add one line:

```java
UiUtils.wireDescriptionAutocomplete(sharedDesc, ds.getDistinctTransactionDescriptions());
```

`ds` is already the `DataStore` instance available in the constructor.

### Step 4 — `AddEditRecurringDialog.java`
After `descFld` is initialized (line ~42, after `setMaxWidth`), add one line:

```java
UiUtils.wireDescriptionAutocomplete(descFld, DataStore.getInstance().getDistinctScheduleDescriptions());
```

`DataStore` is already imported in this class.

---

## Verification
1. `bash build.sh compile` — clean build
2. Open New Transaction dialog, type a prefix of a past description → inline suffix appears highlighted
3. Continue typing → replaces the highlighted portion
4. Backspace → removes only the highlighted suffix, not user-typed text
5. Open New Schedule dialog, type → suggestions come only from past schedules, not transactions
6. New user with no data → no crash, no suggestion shown
