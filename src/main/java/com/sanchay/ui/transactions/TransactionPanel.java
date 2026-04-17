package com.sanchay.ui.transactions;

import com.sanchay.model.Account;
import com.sanchay.model.Category;
import com.sanchay.model.Transaction;
import javafx.scene.Node;
import javafx.scene.control.ComboBox;

import java.util.List;

/** Contract for all type-specific transaction panel classes. */
interface TransactionPanel {

    /** Pre-built UI node (constructed once in the panel constructor). */
    Node getNode();

    /** Validate fields and persist. Throws {@link IllegalArgumentException} on validation failure. */
    Transaction save();

    /** Populate panel fields from an existing transaction. */
    void prefill(Transaction t);

    /** Primary category combo for auto-suggest routing (null if panel has no category). */
    default ComboBox<Category> getCatCombo() { return null; }

    /** Category master list for auto-suggest reordering (null if panel has no category). */
    default List<Category> getCatMaster() { return null; }

    /** Sub-category combo for auto-suggest routing (null if panel has no sub-category). */
    default ComboBox<Category> getSubCatCombo() { return null; }

    /**
     * Pre-populate account fields when a context account is known.
     * Called when the user opens the dialog from an account's transaction view,
     * or when the type combo changes while a context account is set.
     */
    default void applyContextAccount(Account acc, boolean isSource) {}

    /**
     * Move focus to the first empty required field in this panel.
     * Returns {@code true} if a field was focused, {@code false} if all fields are filled
     * (caller should then move focus to the shared Notes field).
     */
    default boolean focusFirstEmpty() { return false; }
}
