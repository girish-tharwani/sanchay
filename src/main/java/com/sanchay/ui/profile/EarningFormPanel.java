package com.sanchay.ui.profile;

import com.sanchay.model.EarningSource;
import javafx.scene.Node;

/** Common interface for per-income-type form panels inside EarningsDialog. */
interface EarningFormPanel {
    Node buildPanel();
    void collectValues(EarningSource src); // throws IllegalArgumentException on validation failure
}
