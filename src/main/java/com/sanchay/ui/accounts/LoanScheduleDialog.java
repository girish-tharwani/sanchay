package com.sanchay.ui.accounts;

import com.sanchay.model.AmortizationEntry;
import com.sanchay.model.LoanAccount;
import com.sanchay.model.RecurringTransaction;
import com.sanchay.model.Transaction;
import com.sanchay.service.AmortizationService;
import com.sanchay.service.DataStore;
//import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import com.sanchay.ui.recurring.AddEditRecurringDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.*;
import javafx.stage.Modality;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Modal dialog showing the full amortization schedule for a loan account.
 * Columns: Payment Date | Opening Balance | EMI | Principal Repayment | Interest
 * Principal Repayment is editable; on commit the schedule is recalculated forward.
 */
public class LoanScheduleDialog {

    private final LoanAccount loan;

    public LoanScheduleDialog(LoanAccount loan) {
        this.loan = loan;
    }

    public void show() {
        DataStore ds = DataStore.getInstance();

        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Repayment Schedule — " + loan.getName(), "≡", 820);
        dlg.getDialogPane().setPrefHeight(640);
        dlg.initModality(Modality.APPLICATION_MODAL);

        // ── Table ──────────────────────────────────────────────────────────────
        TableView<AmortizationEntry> table = new TableView<>();
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        TableColumn<AmortizationEntry, String> colDate = new TableColumn<>("Payment Date");
        colDate.setCellValueFactory(r -> {
            LocalDate d = r.getValue().getPaymentDate();
            String formatted = d != null
                    ? d.format(ds.getDateFormatter())
                    : "";
            return new SimpleStringProperty(formatted);
        });
        colDate.setPrefWidth(120);

        TableColumn<AmortizationEntry, String> colOpening = new TableColumn<>("Opening Balance");
        colOpening.setCellValueFactory(r ->
                new SimpleStringProperty(String.format("%.0f", r.getValue().getOpeningBalancePaise() / 100.0)));
        colOpening.setPrefWidth(145);

        TableColumn<AmortizationEntry, String> colEmi = new TableColumn<>("EMI");
        colEmi.setCellValueFactory(r ->
                new SimpleStringProperty(String.format("%.0f", r.getValue().getEmiAmountPaise() / 100.0)));
        colEmi.setPrefWidth(120);

        TableColumn<AmortizationEntry, String> colPrincipal = new TableColumn<>("Principal");
        colPrincipal.setCellValueFactory(r ->
                new SimpleStringProperty(String.format("%.0f", r.getValue().getPrincipalRepaymentPaise() / 100.0)));
        colPrincipal.setPrefWidth(130);
        colPrincipal.setEditable(true);
        colPrincipal.setCellFactory(TextFieldTableCell.forTableColumn());
        colPrincipal.setOnEditCommit(event -> {
            AmortizationEntry entry = event.getRowValue();
            try {
                long newPrincipal = Math.round(Double.parseDouble(event.getNewValue().replace(",", "")) * 100);
                if (newPrincipal < 0) { table.refresh(); return; }

                List<AmortizationEntry> entries = new ArrayList<>(ds.getSchedule(loan.getId()));
                int idx = entries.indexOf(entry);
                if (idx < 0) { table.refresh(); return; }

                // Apply override on this entry
                AmortizationEntry modified = entries.get(idx);
                modified.setPrincipalRepaymentPaise(newPrincipal);
                modified.setInterestPaymentPaise(modified.getEmiAmountPaise() - newPrincipal);
                modified.setManualOverride(true);

                // Recalculate subsequent entries
                List<AmortizationEntry> updated = AmortizationService.recalculateFrom(entries, idx + 1, loan);
                ds.saveSchedule(loan.getId(), updated);
                table.getItems().setAll(updated);
            } catch (NumberFormatException e) {
                table.refresh();
            }
        });

        TableColumn<AmortizationEntry, String> colInterest = new TableColumn<>("Interest");
        colInterest.setCellValueFactory(r ->
                new SimpleStringProperty(String.format("%.0f", r.getValue().getInterestPaymentPaise() / 100.0)));
        colInterest.setPrefWidth(120);

        table.getColumns().addAll(colDate, colOpening, colEmi, colPrincipal, colInterest);

        // Style past rows differently from future rows
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(AmortizationEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                getStyleClass().removeAll("row-past", "row-override");
                if (!empty && entry != null) {
                    if (entry.getPaymentDate() != null && entry.getPaymentDate().isBefore(LocalDate.now())) {
                        getStyleClass().add("row-past");
                    }
                    if (entry.isManualOverride()) {
                        getStyleClass().add("row-override");
                    }
                }
            }
        });

        // Load schedule — regenerate if missing, empty, or corrupt
        List<AmortizationEntry> schedule = ds.getSchedule(loan.getId());
        if (schedule.isEmpty()) {
            try {
                schedule = AmortizationService.generateSchedule(loan);
                ds.saveSchedule(loan.getId(), schedule);
            } catch (Exception ex) {
                schedule = List.of();
                Label errLabel = new Label("Could not generate schedule: " + ex.getMessage()
                        + "\nPlease check that the loan has a valid opening date, tenure, rate and EMI.");
                errLabel.getStyleClass().add("text-error");
                errLabel.setWrapText(true);
                dlg.getDialogPane().setContent(errLabel);
                dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
                dlg.showAndWait();
                return;
            }
        }
        table.getItems().setAll(schedule);

        // ── Summary label ──────────────────────────────────────────────────────
        Label hint = new Label("Click a Principal cell to edit. Changed rows are highlighted. " +
                "All subsequent rows are recalculated automatically.");
        hint.getStyleClass().add("text-hint");
        hint.setWrapText(true);

        // ── Setup Payments button row ──────────────────────────────────────────
        RecurringTransaction existingSchedule = ds.findLoanPaymentSchedule(loan.getId());
        HBox buttonRow = new HBox(10);
        buttonRow.setAlignment(Pos.CENTER_LEFT);

        if (existingSchedule == null) {
            Button setupBtn = new Button("Setup Payments");
            setupBtn.getStyleClass().add("btn-gold");
            setupBtn.setOnAction(e -> {
                List<AmortizationEntry> currentSchedule = ds.getSchedule(loan.getId());
                LocalDate today = LocalDate.now();
                LocalDate startDate = currentSchedule.stream()
                        .map(AmortizationEntry::getPaymentDate)
                        .filter(d -> d != null && !d.isBefore(today))
                        .findFirst().orElse(today);
                long remainingPayments = currentSchedule.stream()
                        .filter(entry -> entry.getPaymentDate() != null && !entry.getPaymentDate().isBefore(today))
                        .count();
                RecurringTransaction defaults = new RecurringTransaction(
                        "EMI - " + loan.getName(),
                        Transaction.Type.LOAN_PAYMENT,
                        RecurringTransaction.Frequency.MONTHLY,
                        loan.getEmiDueDay(),
                        startDate,
                        loan.getEmiAmountPaise());
                defaults.setToAccountId(loan.getId());
                if (remainingPayments > 0) defaults.setNumberOfPayments((int) remainingPayments);
                AddEditRecurringDialog.showWithDefaults(defaults);
            });
            buttonRow.getChildren().add(setupBtn);
        } else {
            Button setupBtn = new Button("Setup Payments");
            setupBtn.getStyleClass().add("btn-gold");
            setupBtn.setDisable(true);
            Label alreadyLbl = new Label("Payment schedule already set up.");
            alreadyLbl.getStyleClass().add("text-hint");
            Hyperlink editLink = new Hyperlink("Edit");
            editLink.setOnAction(e -> AddEditRecurringDialog.show(existingSchedule));
            buttonRow.getChildren().addAll(setupBtn, alreadyLbl, editLink);
        }

        VBox content = new VBox(10, table, buttonRow, hint);
        content.setPadding(new Insets(16));
        VBox.setVgrow(table, Priority.ALWAYS);

        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dlg.showAndWait();
    }

}
