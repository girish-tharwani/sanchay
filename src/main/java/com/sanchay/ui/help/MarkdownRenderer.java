package com.sanchay.ui.help;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts a Markdown string into a tree of styled JavaFX nodes.
 *
 * Supported constructs: H1–H4 headings, paragraphs with inline bold and code,
 * bullet and numbered lists (one level of nesting), blockquotes, horizontal
 * rules, and tables rendered as GridPanes.
 */
class MarkdownRenderer {

    static VBox render(String markdown) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(24, 28, 28, 28));
        String[] lines = markdown.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];

            // H4
            if (line.startsWith("#### ")) {
                root.getChildren().add(headingLabel(line.substring(5), "guide-h4"));
                i++; continue;
            }
            // H3
            if (line.startsWith("### ")) {
                root.getChildren().add(headingLabel(line.substring(4), "guide-h3"));
                i++; continue;
            }
            // H2
            if (line.startsWith("## ")) {
                root.getChildren().add(headingLabel(line.substring(3), "guide-h2"));
                i++; continue;
            }
            // H1
            if (line.startsWith("# ")) {
                root.getChildren().add(headingLabel(line.substring(2), "guide-h1"));
                i++; continue;
            }
            // Horizontal rule
            if (line.startsWith("---")) {
                Separator sep = new Separator();
                sep.getStyleClass().add("separator");
                sep.setPrefHeight(0.5);
                root.getChildren().add(sep);
                i++; continue;
            }
            // Blockquote
            if (line.startsWith("> ")) {
                root.getChildren().add(buildBlockquote(line.substring(2)));
                i++; continue;
            }
            // Table — collect all consecutive | lines
            if (line.startsWith("|")) {
                List<String> tableLines = new ArrayList<>();
                while (i < lines.length && lines[i].startsWith("|")) {
                    tableLines.add(lines[i]);
                    i++;
                }
                root.getChildren().add(buildTable(tableLines));
                continue;
            }
            // List items — collect consecutive list lines
            if (isListLine(line)) {
                List<String> listLines = new ArrayList<>();
                while (i < lines.length && isListLine(lines[i])) {
                    listLines.add(lines[i]);
                    i++;
                }
                root.getChildren().addAll(buildListItems(listLines));
                continue;
            }
            // Blank line
            if (line.isBlank()) {
                i++; continue;
            }
            // Paragraph — accumulate consecutive non-blank, non-block lines
            List<String> paraLines = new ArrayList<>();
            while (i < lines.length && !lines[i].isBlank() && !isBlockStart(lines[i])) {
                paraLines.add(lines[i]);
                i++;
            }
            if (!paraLines.isEmpty()) {
                Node para = inlineFlow(String.join(" ", paraLines));
                applyParagraphStyle(para);
                root.getChildren().add(para);
            }
        }

        return root;
    }

    // ── Block builders ────────────────────────────────────────────────────────

    private static Label headingLabel(String text, String styleClass) {
        Label lbl = new Label(stripLinks(text));
        lbl.getStyleClass().add(styleClass);
        lbl.setWrapText(true);
        lbl.setMaxWidth(Double.MAX_VALUE);
        return lbl;
    }

    private static VBox buildBlockquote(String text) {
        Node content = inlineFlow(text);
        applyInlineWidth(content);
        VBox box = new VBox(content);
        box.getStyleClass().add("guide-blockquote");
        box.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private static GridPane buildTable(List<String> lines) {
        List<String[]> rows = new ArrayList<>();
        for (String line : lines) {
            String[] cells = splitTableRow(line);
            if (!isSeparatorRow(cells)) rows.add(cells);
        }
        if (rows.isEmpty()) return new GridPane();

        int colCount = rows.stream().mapToInt(r -> r.length).max().orElse(1);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("guide-table");

        for (int c = 0; c < colCount; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setHgrow(Priority.ALWAYS);
            cc.setFillWidth(true);
            grid.getColumnConstraints().add(cc);
        }

        boolean header = true;
        int rowIdx = 0;
        for (String[] cells : rows) {
            for (int c = 0; c < colCount; c++) {
                String text = c < cells.length ? cells[c].trim() : "";
                Label cell = new Label(stripInlineMarkup(text));
                cell.setWrapText(true);
                cell.setMaxWidth(Double.MAX_VALUE);
                cell.getStyleClass().add(header ? "guide-th" : "guide-td");
                grid.add(cell, c, rowIdx);
            }
            header = false;
            rowIdx++;
        }
        return grid;
    }

    private static List<Node> buildListItems(List<String> lines) {
        List<Node> result = new ArrayList<>();
        for (String line : lines) {
            boolean nested = line.startsWith("   ") || line.startsWith("\t");
            String trimmed = line.stripLeading();

            String bullet;
            String content;
            if (trimmed.startsWith("- ")) {
                bullet = "•";
                content = trimmed.substring(2);
            } else {
                int dot = trimmed.indexOf(". ");
                bullet = dot > 0 ? trimmed.substring(0, dot + 1) : "•";
                content = dot > 0 ? trimmed.substring(dot + 2) : trimmed;
            }

            Label bulletLbl = new Label(bullet);
            bulletLbl.getStyleClass().add("guide-list-bullet");
            bulletLbl.setMinWidth(16);

            Node contentNode = inlineFlow(content);
            applyInlineWidth(contentNode);

            HBox row = new HBox(6, bulletLbl, contentNode);
            row.getStyleClass().add("guide-list-item");
            row.setAlignment(Pos.TOP_LEFT);
            row.setPadding(new Insets(0, 0, 0, nested ? 24 : 8));
            if (contentNode instanceof Region r) HBox.setHgrow(r, Priority.ALWAYS);

            result.add(row);
        }
        return result;
    }

    // ── Inline parser ─────────────────────────────────────────────────────────

    /**
     * Returns a Label (plain text) or TextFlow (bold/code spans) for the given text.
     */
    private static Node inlineFlow(String text) {
        text = stripLinks(text);

        if (!text.contains("**") && !text.contains("`")) {
            Label lbl = new Label(text);
            lbl.getStyleClass().add("guide-text");
            return lbl;
        }

        TextFlow flow = new TextFlow();
        Pattern p = Pattern.compile("\\*\\*(.+?)\\*\\*|`([^`]+)`");
        Matcher m = p.matcher(text);
        int last = 0;
        while (m.find()) {
            if (m.start() > last) flow.getChildren().add(plainText(text.substring(last, m.start())));
            if (m.group(1) != null) {
                Text bold = new Text(m.group(1));
                bold.getStyleClass().add("guide-bold");
                flow.getChildren().add(bold);
            } else {
                Text code = new Text(m.group(2));
                code.getStyleClass().add("guide-inline-code");
                flow.getChildren().add(code);
            }
            last = m.end();
        }
        if (last < text.length()) flow.getChildren().add(plainText(text.substring(last)));
        return flow;
    }

    private static Text plainText(String s) {
        Text t = new Text(s);
        t.getStyleClass().add("guide-text");
        return t;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void applyParagraphStyle(Node para) {
        if (para instanceof Label l) {
            l.setWrapText(true);
            l.setMaxWidth(Double.MAX_VALUE);
        } else if (para instanceof TextFlow tf) {
            tf.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static void applyInlineWidth(Node node) {
        if (node instanceof Label l) { l.setWrapText(true); l.setMaxWidth(Double.MAX_VALUE); }
        else if (node instanceof TextFlow tf) { tf.setMaxWidth(Double.MAX_VALUE); }
    }

    private static boolean isListLine(String line) {
        if (line == null || line.isBlank()) return false;
        String t = line.stripLeading();
        if (t.startsWith("- ")) return true;
        return t.length() > 2 && Character.isDigit(t.charAt(0)) && t.contains(". ");
    }

    private static boolean isBlockStart(String line) {
        return line.startsWith("#")
            || line.startsWith("---")
            || line.startsWith("> ")
            || line.startsWith("|")
            || isListLine(line);
    }

    private static String[] splitTableRow(String line) {
        String s = line.trim();
        if (s.startsWith("|")) s = s.substring(1);
        if (s.endsWith("|")) s = s.substring(0, s.length() - 1);
        return s.split("\\|", -1);
    }

    private static boolean isSeparatorRow(String[] cells) {
        for (String cell : cells) {
            if (!cell.trim().matches("[-: ]+")) return false;
        }
        return cells.length > 0;
    }

    private static String stripLinks(String text) {
        return text.replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1");
    }

    /** Strips **bold** and `code` markup to plain text — used for table cells and headings. */
    private static String stripInlineMarkup(String text) {
        text = stripLinks(text);
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("`([^`]+)`", "$1");
        return text.trim();
    }
}
