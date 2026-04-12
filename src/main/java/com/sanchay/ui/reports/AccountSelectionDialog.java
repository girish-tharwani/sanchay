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
 *
 * <p>MF and Equity investment accounts are shown in the Investments group but
 * are permanently disabled — they provide a visual cue that these account types
 * are excluded from cash-flow projection.
 *
 * <p>A "Show sum of all accounts" toggle controls whether the thick gold total
 * line appears on the chart.
 */
class AccountSelectionDialog {

    static final int MAX = 10;

    record Result(Set<String> selection, boolean showSum) {}

    private record AccountGroup(String name, List<Account> accounts) {}

    /**
     * Shows the dialog and returns the new state, or {@code Optional.empty()} when cancelled.
     *
     * @param allAccounts   accounts currently in the projection (already filtered)
     * @param current       previously persisted selection; {@code null}/empty → treat as "all selected"
     * @param currentShowSum previously persisted showSum preference
     */
    static Optional<Result> show(List<Account> allAccounts, Set<String> current, boolean currentShowSum) {
        if (allAccounts == null || allAccounts.isEmpty()) return Optional.empty();

        // Separate selectable accounts from always-disabled ones
        List<Account> selectable = allAccounts.stream()
                .filter(a -> !isViewOnly(a))
                .collect(Collectors.toList());

        // Resolve working selection — only selectable accounts count
        Set<String> allSelectableIds = selectable.stream()
                .map(Account::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> workingSel = (current == null || current.isEmpty())
                ? new LinkedHashSet<>(allSelectableIds)
                : allSelectableIds.stream()
                        .filter(current::contains)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        if (workingSel.size() > MAX) {
            workingSel = workingSel.stream().limit(MAX)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        }
        final Set<String> sel = workingSel;

        List<AccountGroup> groups = buildGroups(allAccounts);

        // ── Dialog shell ──────────────────────────────────────────────────────
        Dialog<Result> dlg = new Dialog<>();
        UiUtils.applyStylesheet(dlg);
        UiUtils.setDialogHeader(dlg, "📊", "Choose Accounts",
                "Select up to " + MAX + " accounts to show in the detailed chart");
        dlg.getDialogPane().setPrefWidth(420);

        // ── Sum toggle (top) ──────────────────────────────────────────────────
        CheckBox showSumCb = new CheckBox("Show sum of all accounts");
        showSumCb.setSelected(currentShowSum);
        showSumCb.getStyleClass().add("acsel-group-cb");
        Separator sep = new Separator();
        sep.setPadding(new Insets(4, 0, 4, 0));

        // Counter badge
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

            List<CheckBox> accCbs      = new ArrayList<>();
            List<Account>  groupSelectable = new ArrayList<>();

            for (Account acc : group.accounts()) {
                CheckBox cb = new CheckBox(acc.getName());
                boolean viewOnly = isViewOnly(acc);
                if (viewOnly) {
                    cb.setSelected(false);
                    cb.setDisable(true);
                    cb.getStyleClass().add("acsel-account-cb");
                } else {
                    cb.setSelected(sel.contains(acc.getId()));
                    cb.getStyleClass().add("acsel-account-cb");
                    allAccCbs.add(cb);
                    cbToAcc.put(cb, acc);
                    groupSelectable.add(acc);
                }
                accCbs.add(cb);
            }

            // Group header checkbox (tri-state, driven only by selectable accounts)
            CheckBox groupCb = new CheckBox(group.name());
            groupCb.getStyleClass().add("acsel-group-cb");
            syncGroupState(groupCb, groupSelectable, sel);

            // Wire selectable account checkbox clicks
            for (Account acc : groupSelectable) {
                CheckBox cb = accCbs.get(group.accounts().indexOf(acc));
                cb.setOnAction(ev -> {
                    if (cb.isSelected()) sel.add(acc.getId());
                    else                 sel.remove(acc.getId());
                    syncGroupState(groupCb, groupSelectable, sel);
                    syncCounter(counterLbl, sel.size());
                    syncDisabled(allAccCbs, cbToAcc, sel);
                });
            }

            // Wire group checkbox click — only affects selectable accounts
            groupCb.setOnAction(ev -> {
                boolean allChk = groupSelectable.stream().allMatch(a -> sel.contains(a.getId()));
                if (allChk) {
                    for (Account a : groupSelectable) {
                        sel.remove(a.getId());
                        CheckBox cb = accCbs.get(group.accounts().indexOf(a));
                        cb.setSelected(false);
                    }
                } else {
                    int room  = MAX - sel.size();
                    int added = 0;
                    for (Account a : groupSelectable) {
                        if (!sel.contains(a.getId()) && added < room) {
                            sel.add(a.getId());
                            accCbs.get(group.accounts().indexOf(a)).setSelected(true);
                            added++;
                        }
                    }
                }
                syncGroupState(groupCb, groupSelectable, sel);
                syncCounter(counterLbl, sel.size());
                syncDisabled(allAccCbs, cbToAcc, sel);
            });

            // Disable group header if there are no selectable accounts in the group
            if (groupSelectable.isEmpty()) groupCb.setDisable(true);

            VBox accBox = new VBox(5);
            accBox.setPadding(new Insets(2, 0, 0, 26));
            accCbs.forEach(cb -> accBox.getChildren().add(cb));

            listBox.getChildren().add(new VBox(6, groupCb, accBox));
        }

        syncDisabled(allAccCbs, cbToAcc, sel);

        // ── Scroll + content layout ───────────────────────────────────────────
        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(Math.min(320,
                allAccounts.size() * 29 + groups.size() * 38 + 24));
        scroll.getStyleClass().add("edge-to-edge");

        HBox counterRow = new HBox(counterLbl);
        counterRow.setAlignment(Pos.CENTER_RIGHT);
        counterRow.setPadding(new Insets(0, 2, 6, 0));

        VBox content = new VBox(8, showSumCb, sep, counterRow, scroll);
        content.setPadding(new Insets(10, 16, 4, 16));
        dlg.getDialogPane().setContent(content);

        ButtonType applyBt  = new ButtonType("Apply Selection", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelBt = new ButtonType("Cancel",          ButtonBar.ButtonData.CANCEL_CLOSE);
        dlg.getDialogPane().getButtonTypes().addAll(applyBt, cancelBt);

        dlg.setResultConverter(bt -> bt == applyBt
                ? new Result(new LinkedHashSet<>(sel), showSumCb.isSelected())
                : null);

        return dlg.showAndWait().filter(Objects::nonNull);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** MF and Equity accounts are shown but cannot be selected — they are excluded from projection. */
    private static boolean isViewOnly(Account acc) {
        if (!(acc instanceof InvestmentAccount inv)) return false;
        return inv.getInvestmentType() == InvestmentAccount.InvestmentType.MUTUAL_FUNDS
            || inv.getInvestmentType() == InvestmentAccount.InvestmentType.EQUITY;
    }

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

    private static void syncGroupState(CheckBox groupCb, List<Account> selectable, Set<String> sel) {
        if (selectable.isEmpty()) {
            groupCb.setIndeterminate(false);
            groupCb.setSelected(false);
            return;
        }
        long n = selectable.stream().filter(a -> sel.contains(a.getId())).count();
        if (n == 0) {
            groupCb.setIndeterminate(false);
            groupCb.setSelected(false);
        } else if (n == selectable.size()) {
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
