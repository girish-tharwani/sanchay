# Feature 2: Accounts Collapse/Expand

## Context
The Accounts screen always shows all account cards for every group (Bank Accounts, Credit Cards, Loan Accounts, Investments). Users want to be able to collapse groups they aren't focused on to reduce visual noise. The collapsed/expanded state should persist across restarts, and the default should be all-collapsed.

---

## Requirements
- Chevron icon (▶ / ▼) inline on the section header row toggles collapse
- Collapsed: header row visible, account cards hidden, "Show Closed" checkbox hidden
- Expanded: full current behaviour
- `+ Add` button always visible in header (even when collapsed)
- Default state: all groups collapsed
- State persists in `settings.json` via the existing `AppSettings` / `DataStore` / `PersistenceService` pipeline

---

## Files to modify

| File | Change |
|------|--------|
| `src/main/java/com/sanchay/service/PersistenceService.java` | Add 4 boolean fields to `AppSettings` inner class; load/save them |
| `src/main/java/com/sanchay/service/DataStore.java` | Add in-memory fields + getter/setter for group collapsed state |
| `src/main/java/com/sanchay/ui/accounts/AccountsScreen.java` | Add chevron to header, hide/show cards on toggle, read/write DataStore |

---

## Implementation steps

### Step 1 — `PersistenceService.AppSettings` (lines 50–53)
Add 4 boolean fields defaulting to `true` (collapsed):
```java
public boolean bankGroupCollapsed       = true;
public boolean ccGroupCollapsed         = true;
public boolean loanGroupCollapsed       = true;
public boolean investmentGroupCollapsed = true;
```

In `loadSettings()` (lines 69–83): after loading `dateFormat`, also call:
```java
store.setGroupCollapsedInternal("bank",       s.bankGroupCollapsed);
store.setGroupCollapsedInternal("cc",         s.ccGroupCollapsed);
store.setGroupCollapsedInternal("loan",       s.loanGroupCollapsed);
store.setGroupCollapsedInternal("investment", s.investmentGroupCollapsed);
```

In `saveSettings()` (lines 274–279): read back from store:
```java
s.bankGroupCollapsed       = store.isGroupCollapsed("bank");
s.ccGroupCollapsed         = store.isGroupCollapsed("cc");
s.loanGroupCollapsed       = store.isGroupCollapsed("loan");
s.investmentGroupCollapsed = store.isGroupCollapsed("investment");
```

### Step 2 — `DataStore.java`
Add a `Map<String, Boolean>` field:
```java
private final Map<String, Boolean> groupCollapsed = new HashMap<>(
    Map.of("bank", true, "cc", true, "loan", true, "investment", true));
```

Add methods:
```java
public boolean isGroupCollapsed(String type) {
    return groupCollapsed.getOrDefault(type, true);
}
public void setGroupCollapsedInternal(String type, boolean val) {
    groupCollapsed.put(type, val);
}
public void setGroupCollapsed(String type, boolean val) {
    groupCollapsed.put(type, val);
    if (persistence != null) persistence.saveSettings(this);
}
```

Also update the `resetState()` method (line ~832) to reset these to `true`.

### Step 3 — `AccountsScreen.java`

**`buildGroup()` changes:**
- Read `DataStore.getInstance().isGroupCollapsed(type)` to get current state
- Add a `Label chevron = new Label(collapsed ? "▶" : "▼")` as **first** child of header HBox
- Style chevron with `.filter-label` class so it matches the heading text
- Make the entire header HBox clickable to toggle:
  ```java
  header.setOnMouseClicked(e -> {
      DataStore.getInstance().setGroupCollapsed(type, !collapsed);
      buildList();
  });
  header.setStyle("-fx-cursor: hand;");
  ```
- When `collapsed == true`:
  - Do **not** add account cards or empty label to the group VBox
  - Do **not** add `showClosedCb` to the header
  - Still add `addBtn` to header
- Header HBox children when collapsed:  `[chevron, dot, label, spacer, addBtn]`
- Header HBox children when expanded:  `[chevron, dot, label, spacer, showClosedCb, addBtn]`

**No structural changes** to `buildList()` — it already rebuilds fully each call.

---

## Verification
1. Run `bash build.sh compile` — must compile clean
2. Launch app (`mvn javafx:run`)
3. Go to Accounts screen — all 4 groups show as collapsed (▶) with no cards visible
4. Click a group header → it expands (▼), cards appear
5. Click again → collapses
6. Restart app → collapsed/expanded state is preserved as set
7. `+ Add` button works from a collapsed group header
8. "Show Closed" checkbox is hidden when collapsed, visible when expanded
