package com.sanchay.ui.transactions;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.ImportService;
import com.sanchay.ui.UiUtils;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Orchestrates the full clipboard-paste and CSV-file import flow for a single account. */
class ImportOrchestrator {

    private final Account account;

    ImportOrchestrator(Account account) {
        this.account = account;
    }

    void doImportCsv(Runnable refreshTable) {
        DataStore ds = DataStore.getInstance();

        FileChooser fc = new FileChooser();
        fc.setTitle("Import Bank / CC Statement");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        ImportMapping prior = ImportService.findMapping(account.getId(), ds.getImportMappings());
        if (prior != null && prior.getLastImportPath() != null) {
            File lastDir = new File(prior.getLastImportPath());
            if (lastDir.isDirectory()) fc.setInitialDirectory(lastDir);
        }
        File file = fc.showOpenDialog(null);
        if (file == null) return;

        List<String[]> rows;
        try { rows = ImportService.parseCsv(file); }
        catch (IOException ex) { info("Import Failed", "Could not read file:\n" + ex.getMessage()); return; }

        if (rows.isEmpty()) { info("Import Failed", "The file is empty."); return; }

        String importDir = file.getParentFile() != null ? file.getParentFile().getAbsolutePath() : null;
        doImportRows(rows, importDir, refreshTable);
    }

    /**
     * Core import flow shared by file import and clipboard paste.
     * {@code importDir} is the directory of the source file; pass {@code null} when importing
     * from the clipboard.
     */
    void doImportRows(List<String[]> rows, String importDir, Runnable refreshTable) {
        DataStore ds = DataStore.getInstance();

        if (!ImportService.isLikelyHeader(rows.get(0))) {
            info("Import Rejected",
                    "The first row does not look like a header row.\n"
                    + "Please ensure the data has column headers in the first row.");
            return;
        }

        ImportMapping saved = ImportService.findMapping(account.getId(), ds.getImportMappings());
        String snapshot    = String.join(",", rows.get(0));
        boolean snapshotOk = saved != null && snapshot.equals(saved.getHeaderSnapshot());

        String[] sampleRow = rows.size() > 1 ? rows.get(1) : null;
        ImportMappingDialog dlg = new ImportMappingDialog(account, rows.get(0),
                snapshotOk ? saved : null, sampleRow);
        Optional<ImportMapping> mappingOpt = dlg.showAndWait();
        if (mappingOpt.isEmpty() || mappingOpt.get() == null) return;

        ImportMapping mapping = mappingOpt.get();
        mapping.setHeaderSnapshot(snapshot);
        if (importDir != null) mapping.setLastImportPath(importDir);
        ds.saveOrUpdateImportMapping(mapping);

        ImportService.ImportResult result = ImportService.executeImport(rows, mapping, account, ds);

        // Resolve ambiguous matches.
        // After reconciling one CSV row, its chosen manual becomes RECONCILED.
        // For subsequent ambiguous rows, filter out already-reconciled candidates:
        //   – 0 still-MANUAL candidates → all taken by earlier rows → add as new automatically
        //   – 1+ still-MANUAL candidates → show dialog with only the available ones
        for (ImportService.AmbiguousMatch am : result.ambiguous) {
            List<Transaction> available = am.candidates.stream()
                    .filter(c -> c.getSourceIndicator() == Transaction.SourceIndicator.MANUAL)
                    .collect(Collectors.toList());

            if (available.isEmpty()) {
                boolean categorized = ds.suggestCategoryForDescription(
                        am.imported.getDescription(), am.imported.getType())
                        .map(rule -> {
                            Transaction.Classification cl = new Transaction.Classification();
                            cl.setCategoryId(rule.getCategoryId());
                            cl.setSubCategoryId(rule.getSubCategoryId());
                            am.imported.setClassification(cl);
                            return true;
                        }).orElse(false);
                am.imported.setSourceIndicator(categorized
                        ? Transaction.SourceIndicator.AUTO_CATEGORIZED
                        : Transaction.SourceIndicator.IMPORTED);
                ds.addTransactionInternal(am.imported);
                result.newCount++;
                continue;
            }

            AmbiguousMatchDialog amd = new AmbiguousMatchDialog(am.imported, available);
            Optional<Transaction> choice = amd.showAndWait();
            if (choice.isPresent()) {
                Transaction chosen = choice.get();
                if (chosen != null) {
                    ImportService.reconcile(am.imported, chosen, ds);
                    result.reconciledCount++;
                } else {
                    // "Add as New" was clicked
                    boolean categorized = ds.suggestCategoryForDescription(
                            am.imported.getDescription(), am.imported.getType())
                            .map(rule -> {
                                Transaction.Classification cl = new Transaction.Classification();
                                cl.setCategoryId(rule.getCategoryId());
                                cl.setSubCategoryId(rule.getSubCategoryId());
                                am.imported.setClassification(cl);
                                return true;
                            }).orElse(false);
                    am.imported.setSourceIndicator(categorized
                            ? Transaction.SourceIndicator.AUTO_CATEGORIZED
                            : Transaction.SourceIndicator.IMPORTED);
                    ds.addTransactionInternal(am.imported);
                    result.newCount++;
                }
            }
            // CANCEL on ambiguous dialog → skip this entry silently
        }
        if (!result.ambiguous.isEmpty()) ds.saveTransactionsNow();

        // Resolve recurring matches — show RecurringMatchDialog for each.
        for (ImportService.RecurringMatch rm : result.recurringMatches) {
            RecurringMatchDialog rmd = new RecurringMatchDialog(rm.imported, rm.candidates);
            Optional<RecurringTransaction> choice = rmd.showAndWait();
            if (choice.isEmpty()) continue;  // cancelled — skip silently

            RecurringTransaction chosen = choice.get();
            if (chosen == RecurringMatchDialog.ADD_AS_NEW) {
                boolean categorized = ds.suggestCategoryForDescription(
                        rm.imported.getDescription(), rm.imported.getType())
                        .map(rule -> {
                            Transaction.Classification cl = new Transaction.Classification();
                            cl.setCategoryId(rule.getCategoryId());
                            cl.setSubCategoryId(rule.getSubCategoryId());
                            rm.imported.setClassification(cl);
                            return true;
                        }).orElse(false);
                rm.imported.setSourceIndicator(categorized
                        ? Transaction.SourceIndicator.AUTO_CATEGORIZED
                        : Transaction.SourceIndicator.IMPORTED);
                ds.addTransactionInternal(rm.imported);
                ds.saveTransactionsNow();
                result.newCount++;
            } else {
                ImportService.reconcileWithRecurring(rm.imported, chosen, ds);
                result.recurringReconciledCount++;
            }
        }

        refreshTable.run();
        new ImportCompleteDialog(result).show();
    }

    private void info(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        UiUtils.initDialog(a, title, "i", 400);
        Label content = new Label(msg);
        content.setId("txn-info-dialog-message");
        content.setWrapText(true);
        content.getStyleClass().add("dialog-body-text");
        a.getDialogPane().setContent(content);
        a.showAndWait();
    }
}
