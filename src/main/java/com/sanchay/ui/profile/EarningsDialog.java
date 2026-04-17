package com.sanchay.ui.profile;

import com.sanchay.model.*;
import com.sanchay.service.DataStore;
import com.sanchay.service.MoneyFormatter;
import com.sanchay.ui.UiUtils;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;

/**
 * Dialog for configuring earnings for a family member.
 * Supports multiple income sources (EarningSource), each creating its own
 * recurring INCOME schedule. Each source gets its own tab; a "+ Income" tab
 * appends new sources.
 *
 * SIMPLE sources: gross amount entered, net = gross × (1 − tax%). Schedule uses net amount.
 * SALARY sources: annual inputs entered, monthly net computed via PF + TDS deductions.
 *
 * On Save: all tabs are validated and their EarningSource objects are persisted.
 */
public class EarningsDialog extends Dialog<Boolean> {

    private final FamilyMember member;
    private final DataStore    ds = DataStore.getInstance();

    private final List<EarningSource>          workingSources   = new ArrayList<>();
    private final Map<String, EarningFormPanel> panels          = new LinkedHashMap<>();
    private final Map<String, TextField>        sourceNameFields = new LinkedHashMap<>();
    private final List<EarningSource>           pendingDeletes  = new ArrayList<>();
    private Boolean                             pendingResult   = null;

    public EarningsDialog(FamilyMember member) {
        this.member = member;
        UiUtils.initDialog(this, "Earnings — " + member.getName(), MoneyFormatter.symbol(), 950);
        getDialogPane().setMinHeight(720);

        TabPane tabs = buildTabPane();
        getDialogPane().setContent(tabs);

        ButtonType saveBtn = UiUtils.addSaveCancel(getDialogPane());

        Platform.runLater(() -> {
            Button btn = (Button) getDialogPane().lookupButton(saveBtn);
            if (btn != null) btn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                Boolean result = saveAll(tabs);
                if (result == null) {
                    ev.consume();
                } else {
                    pendingResult = result;
                }
            });
        });

        setResultConverter(bt -> bt == saveBtn ? pendingResult : null);
    }

    // ── Tab pane construction ─────────────────────────────────────────────────

    private TabPane buildTabPane() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        List<EarningSource> existing = member.getEarningSources();
        if (existing == null || existing.isEmpty()) {
            EarningSource blank = new EarningSource();
            blank.setSourceName("Income - Structured");
            blank.setType(FamilyMember.EarningType.SALARY);
            existing = List.of(blank);
        }
        for (EarningSource src : existing) {
            workingSources.add(src);
            tabs.getTabs().add(buildSourceTab(src, tabs));
        }

        Tab addTab = new Tab("+ Income (Simple / Structured)");
        addTab.setClosable(false);
        tabs.getTabs().add(addTab);

        tabs.getSelectionModel().selectedItemProperty().addListener((obs, prev, curr) -> {
            if (curr == addTab) {
                Platform.runLater(() -> {
                    if (prev != null) tabs.getSelectionModel().select(prev);
                });
                showAddSourceDialog(tabs, addTab);
            }
        });

        return tabs;
    }

    private Tab buildSourceTab(EarningSource src, TabPane tabs) {
        Tab tab = new Tab(src.getSourceName() != null ? src.getSourceName() : "Income");
        tab.setClosable(false);

        TextField sourceNameFld = tf(src.getSourceName() != null ? src.getSourceName() : "");
        sourceNameFld.setPromptText("e.g. Main Job, Freelance, Rental...");
        sourceNameFld.textProperty().addListener((o, ov, nv) ->
                tab.setText(nv.isBlank() ? "Income" : nv));
        sourceNameFields.put(src.getId(), sourceNameFld);

        GridPane nameGrid = UiUtils.buildFormGrid(160);
        nameGrid.setPadding(new Insets(12, 16, 8, 16));
        row(nameGrid, 0, "Source Name*", sourceNameFld);

        EarningFormPanel panel = src.getType() == FamilyMember.EarningType.SALARY
                ? new SalaryEarningsPanel(src, member, ds)
                : new SimpleEarningsPanel(src, member, ds);
        panels.put(src.getId(), panel);

        Node formContent = panel.buildPanel();

        Button removeBtn = new Button("Remove this source");
        removeBtn.getStyleClass().add("btn-secondary");
        removeBtn.setOnAction(e -> removeSource(src, tab, tabs));
        HBox removeRow = new HBox(removeBtn);
        removeRow.setPadding(new Insets(6, 16, 10, 16));

        VBox wrapper = new VBox(0, nameGrid, new Separator(), formContent, removeRow);

        ScrollPane scroll = new ScrollPane(wrapper);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-transparent");
        tab.setContent(scroll);
        return tab;
    }

    private void showAddSourceDialog(TabPane tabs, Tab addTab) {
        Dialog<EarningSource> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Add Income Source", "+", 380);

        GridPane g = UiUtils.buildFormGrid(130);
        g.setVgap(12);
        g.setPadding(new Insets(16));

        TextField nameFld = tf("");
        nameFld.setPromptText("e.g. Main Job, Freelance...");

        ComboBox<String> typeCb = new ComboBox<>();
        typeCb.getItems().addAll("Simple Income", "Structured Salary");
        typeCb.setValue("Simple Income");
        typeCb.setMaxWidth(Double.MAX_VALUE);

        row(g, 0, "Source Name*", nameFld);
        row(g, 1, "Type*", typeCb);

        dlg.getDialogPane().setContent(g);
        ButtonType addBtn = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(addBtn, ButtonType.CANCEL);

        dlg.setResultConverter(bt -> {
            if (bt != addBtn) return null;
            String name = nameFld.getText().trim();
            if (name.isEmpty()) { showError("Source Name is required."); return null; }
            EarningSource src = new EarningSource();
            src.setSourceName(name);
            boolean isSalary = "Structured Salary".equals(typeCb.getValue());
            src.setType(isSalary ? FamilyMember.EarningType.SALARY : FamilyMember.EarningType.SIMPLE);
            if (!isSalary) src.setSimpleFrequency(RecurringTransaction.Frequency.MONTHLY.name());
            return src;
        });

        dlg.showAndWait().ifPresent(src -> {
            workingSources.add(src);
            int insertIdx = tabs.getTabs().indexOf(addTab);
            Tab newTab = buildSourceTab(src, tabs);
            tabs.getTabs().add(insertIdx, newTab);
            tabs.getSelectionModel().select(newTab);
        });
    }

    private void removeSource(EarningSource src, Tab tab, TabPane tabs) {
        if (workingSources.size() == 1) {
            showError("At least one income source is required.");
            return;
        }
        if (src.getRecurringScheduleId() != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Remove Income Source");
            confirm.setHeaderText(null);
            confirm.setContentText(
                    "This will also delete the linked recurring income schedule. Continue?");
            UiUtils.applyStylesheet(confirm);
            if (confirm.showAndWait().filter(r -> r == ButtonType.OK).isEmpty()) return;
            pendingDeletes.add(src);
        }
        workingSources.remove(src);
        panels.remove(src.getId());
        sourceNameFields.remove(src.getId());
        tabs.getTabs().remove(tab);
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    private Boolean saveAll(TabPane tabs) {
        try {
            for (EarningSource src : pendingDeletes) {
                if (src.getRecurringScheduleId() != null) ds.deleteRecurring(src.getRecurringScheduleId());
                if (src.getPfScheduleId() != null)        ds.deleteRecurring(src.getPfScheduleId());
                if (src.getEsppScheduleId() != null)      ds.deleteRecurring(src.getEsppScheduleId());
            }

            List<EarningSource> savedSources = new ArrayList<>();
            for (EarningSource src : workingSources) {
                TextField nameFld = sourceNameFields.get(src.getId());
                if (nameFld == null) continue;

                String name = nameFld.getText().trim();
                if (name.isEmpty())
                    throw new IllegalArgumentException("Source Name is required for all income sources.");
                src.setSourceName(name);

                EarningFormPanel panel = panels.get(src.getId());
                panel.collectValues(src);

                InvestmentAccount pfAcct   = panel instanceof SalaryEarningsPanel s ? s.getSelectedPfAccount()   : null;
                InvestmentAccount esppAcct = panel instanceof SalaryEarningsPanel s ? s.getSelectedEsppAccount() : null;
                EarningScheduleBuilder.build(src, ds, member.getName(), pfAcct, esppAcct);

                savedSources.add(src);
            }

            member.setEarningSources(savedSources);
            ds.updateFamilyMember(member);
            return Boolean.TRUE;

        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return null;
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private TextField tf(String value) {
        TextField f = new TextField(value);
        f.setMaxWidth(Double.MAX_VALUE);
        return f;
    }

    private void row(GridPane g, int rowIdx, String labelText, Node control) {
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("text-form-value");
        lbl.setMinWidth(155);
        g.add(lbl, 0, rowIdx);
        g.add(control, 1, rowIdx);
        GridPane.setFillWidth(control, true);
    }

    private void showError(String msg) {
        Dialog<Void> dlg = new Dialog<>();
        UiUtils.initDialog(dlg, "Validation Error", "⚠", 380);

        HBox iconRow = new HBox(14);
        iconRow.setAlignment(Pos.CENTER_LEFT);
        iconRow.setPadding(new Insets(16));
        Label iconLbl = new Label("⚠");
        iconLbl.getStyleClass().addAll("dialog-icon-box-lg", "dialog-icon-box-lg--error");
        Label msgLbl = new Label(msg);
        msgLbl.getStyleClass().add("form-label");
        msgLbl.setWrapText(true);
        msgLbl.setMaxWidth(280);
        iconRow.getChildren().addAll(iconLbl, msgLbl);

        dlg.getDialogPane().setContent(iconRow);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dlg.showAndWait();
    }
}
