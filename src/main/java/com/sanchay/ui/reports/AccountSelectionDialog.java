package com.sanchay.ui.reports;

import com.sanchay.model.*;
import com.sanchay.ui.UiUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Modal dialog for choosing which accounts appear in the detailed cash-flow
 * chart.  Accounts are presented in four labelled groups with tri-state group
 * header checkboxes.  A hard cap of {@value #MAX} accounts is enforced.
 */
class AccountSelectionDialog {

    static final int MAX = 10;

    private record AccountGroup(String name, List<Account> accounts) {}

    /**
     * Shows the dialog and returns the new selection, or {@code Optional.empty()}
     * when the user cancelled.
     *
     * @param allAccounts accounts currently in the projection (already filtered)
     * @param current     previously persisted selection; {@code null}/empty → treat as "all selected"
     */
    static Optional<Set<String>> show(List<Account> allAccounts, Set<String> current) {
        if (allAccounts == null || allAccounts.isEmpty()) return Optional.empty();

        // Resolve working selection — intersect saved IDs with currently projected accounts
        Set<String> allIds = allAccounts.stream()
                .map(Account::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> workingSel = (current == null || current.isEmpty())
                ? new LinkedHashSet<>(allIds)
                : allIds.stream()
                        .filter(current::contains)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        // Enforce hard cap on initial load
        if (workingSel.size() > MAX) {
            workingSel = workingSel.stream().limit(MAX)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        final Set<String> sel = workingSel;

        List<AccountGroup> groups = buildGroups(allAccounts);

        // ── Dialog shell ──────────────────────────────────────────────────────
        Dialog<Set<String>> dlg = new Dialog<>();
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "📊", "Choose Accounts",
                "Select up to " + MAX + " accounts to show in the detailed chart");
        dlg.getDialogPane().setPrefWidth(420);

        // Counter badge (top-right of the list)
        Label counterLbl = new Label();
        counterLbl.getStyleClass().add("acsel-counter");
        syncCounter(counterLbl, sel.size());

        // ── Build rows ────────────────────────────────────────────────────────
        List<CheckBox>        allAccCbs = new ArrayList<>();
        Map<CheckBox, Account> cbToAcc  = new IdentityHashMap<>();

        VBox listBox = new VBox(14);
        listBox.setPadding(new Insets(4, 4, 4, 4));

        for (AccountGroup group : groups) {
            if (group.accounts().isEmpty()) continue;

            List<CheckBox> accCbs = new ArrayList<>();

            // Account checkboxes (built before group so handler can reference them)
            for (Account acc : group.accounts()) {
                CheckBox cb = new CheckBox(acc.getName());
                cb.setSelected(sel.contains(acc.getId()));
                cb.getStyleClass().add("acsel-account-cb");
                accCbs.add(cb);
                allAccCbs.add(cb);
                cbToAcc.put(cb, acc);
            }

            // Group header checkbox (tri-state)
            CheckBox groupCb = new CheckBox(group.name());
            groupCb.getStyleClass().add("acsel-group-cb");
            syncGroupState(groupCb, group.accounts(), sel);

            // Wire account checkbox clicks
            for (int i = 0; i < accCbs.size(); i++) {
                CheckBox cb  = accCbs.get(i);
                Account  acc = group.accounts().get(i);
                cb.setOnAction(ev -> {
                    if (cb.isSelected()) sel.add(acc.getId());
                    else                 sel.remove(acc.getId());
                    syncGroupState(groupCb, group.accounts(), sel);
                    syncCounter(counterLbl, sel.size());
                    syncDisabled(allAccCbs, cbToAcc, sel);
                });
            }

            // Wire group checkbox click
            groupCb.setOnAction(ev -> {
                boolean allChk = group.accounts().stream().allMatch(a -> sel.contains(a.getId()));
                if (allChk) {
                    // Deselect all in this group
                    for (int i = 0; i < group.accounts().size(); i++) {
                        sel.remove(group.accounts().get(i).getId());
                        accCbs.get(i).setSelected(false);
                    }
                } else {
                    // Select as many as the remaining headroom allows
                    int room  = MAX - sel.size();
                    int added = 0;
                    for (int i = 0; i < group.accounts().size(); i++) {
                        Account a = group.accounts().get(i);
                        if (!sel.contains(a.getId()) && added < room) {
                            sel.add(a.getId());
                            accCbs.get(i).setSelected(true);
                            added++;
                        }
                    }
                }
                syncGroupState(groupCb, group.accounts(), sel);
                syncCounter(counterLbl, sel.size());
                syncDisabled(allAccCbs, cbToAcc, sel);
            });

            // Indented account rows under the group header
            VBox accBox = new VBox(5);
            accBox.setPadding(new Insets(2, 0, 0, 26));
            accCbs.forEach(cb -> accBox.getChildren().add(cb));

            listBox.getChildren().add(new VBox(6, groupCb, accBox));
        }

        syncDisabled(allAccCbs, cbToAcc, sel); // initial disabled state

        // ── Scroll + content layout ───────────────────────────────────────────
        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.min(340,
                allAccounts.size() * 29 + groups.size() * 38 + 24));
        scroll.getStyleClass().add("edge-to-edge");

        HBox counterRow = new HBox(counterLbl);
        counterRow.setAlignment(Pos.CENTER_RIGHT);
        counterRow.setPadding(new Insets(0, 2, 6, 0));

        VBox content = new VBox(0, counterRow, scroll);
        content.setPadding(new Insets(10, 16, 4, 16));
        dlg.getDialogPane().setContent(content);

        ButtonType applyBt  = new ButtonType("Apply Selection", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBt = new ButtonType("Cancel",          ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().addAll(applyBt, cancelBt);

        dlg.setResultConverter(bt -> bt == applyBt ? new LinkedHashSet<>(sel) : null);

        return dlg.showAndWait().filter(Objects::nonNull);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<AccountGroup> buildGroups(List<Account> accounts) {
        List<Account> banks   = accounts.stream().filter(a -> a instanceof BankAccount).toList();
        List<Account> cards   = accounts.stream().filter(a -> a instanceof CreditCardAccount).toList();
        List<Account> loans   = accounts.stream().filter(a -> a instanceof LoanAccount).toList();
        List<Account> invests = accounts.stream().filter(a -> a instanceof InvestmentAccount).toList();
        List<AccountGroup> out = new ArrayList<>();
        if (!banks.isEmpty())   out.add(new AccountGroup("Bank Accounts", banks));
        if (!cards.isEmpty())   out.add(new AccountGroup("Credit Cards",  cards));
        if (!loans.isEmpty())   out.add(new AccountGroup("Loans",         loans));
        if (!invests.isEmpty()) out.add(new AccountGroup("Investments",   invests));
        return out;
    }

    /** Sets the group checkbox to selected / indeterminate / unchecked based on how many accounts in the group are selected. */
    private static void syncGroupState(CheckBox groupCb, List<Account> accs, Set<String> sel) {
        long n = accs.stream().filter(a -> sel.contains(a.getId())).count();
        if (n == 0) {
            groupCb.setIndeterminate(false);
            groupCb.setSelected(false);
        } else if (n == accs.size()) {
            groupCb.setIndeterminate(false);
            groupCb.setSelected(true);
        } else {
            groupCb.setSelected(false);
            groupCb.setIndeterminate(true);
        }
    }

    private static void syncCounter(Label lbl, int count) {
        lbl.setText(count + " / " + MAX + " selected");
        if (count >= MAX) {
            if (!lbl.getStyleClass().contains("acsel-counter-full"))
                lbl.getStyleClass().add("acsel-counter-full");
        } else {
            lbl.getStyleClass().remove("acsel-counter-full");
        }
    }

    /** Disables unselected checkboxes when the cap is reached; re-enables them when headroom opens. */
    private static void syncDisabled(List<CheckBox> allCbs,
                                     Map<CheckBox, Account> cbToAcc,
                                     Set<String> sel) {
        boolean atMax = sel.size() >= MAX;
        for (CheckBox cb : allCbs) {
            Account acc = cbToAcc.get(cb);
            cb.setDisable(atMax && !sel.contains(acc.getId()));
        }
    }
}
