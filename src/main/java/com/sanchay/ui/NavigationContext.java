package com.sanchay.ui;

import com.sanchay.model.Account;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Per-navigation shell context: carries FAB state written by screens and navigation callbacks set by MainWindow. */
public final class NavigationContext {

    private Runnable onTransactionSaved;
    private Account  contextAccount;

    private final Consumer<Account> navigateToTransactions;
    private final Runnable          navigateBack;
    private final Supplier<String>  getLastExportDir;
    private final Consumer<String>  setLastExportDir;

    NavigationContext(Consumer<Account> navigateToTransactions,
                      Runnable          navigateBack,
                      Supplier<String>  getLastExportDir,
                      Consumer<String>  setLastExportDir) {
        this.navigateToTransactions = navigateToTransactions;
        this.navigateBack           = navigateBack;
        this.getLastExportDir       = getLastExportDir;
        this.setLastExportDir       = setLastExportDir;
    }

    // Written by screens ───────────────────────────────────────────────────────
    public void setOnTransactionSaved(Runnable cb) { onTransactionSaved = cb; }
    public void setContextAccount(Account acc)     { contextAccount = acc; }

    // Read by MainWindow for FAB ───────────────────────────────────────────────
    Runnable getOnTransactionSaved() { return onTransactionSaved; }
    Account  getContextAccount()     { return contextAccount; }

    // Navigation — called by screens ──────────────────────────────────────────
    public void navigateToTransactions(Account acc) { navigateToTransactions.accept(acc); }
    public void navigateBack()                      { navigateBack.run(); }

    // Export dir — shared session state ───────────────────────────────────────
    public String getLastExportDir()           { return getLastExportDir.get(); }
    public void   setLastExportDir(String dir) { setLastExportDir.accept(dir); }
}
