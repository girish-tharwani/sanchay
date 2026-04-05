package com.sanchay.ui.help;

/**
 * Converts the Markdown subset used in USER-GUIDE.md to a styled HTML document.
 * Handles: h1–h4, hr, tables, ordered lists (with nested sub-lists), unordered lists,
 * blockquotes, fenced code blocks, inline code, bold, italic, and links.
 */
class MarkdownConverter {

    // ── Public entry point ────────────────────────────────────────────────────

    static String toHtml(String markdown) {
        String[] lines = markdown.split("\n");
        StringBuilder body = new StringBuilder();
        State state = State.NORMAL;
        boolean inSubUl = false;  // nested <ul> inside an <ol> <li> (used for TOC sub-items)

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();

            // ── Fenced code block ─────────────────────────────────────────────
            if (state == State.IN_PRE) {
                if (line.startsWith("```")) {
                    body.append("</code></pre>\n");
                    state = State.NORMAL;
                } else {
                    body.append(escHtml(line)).append("\n");
                }
                continue;
            }
            if (line.startsWith("```")) {
                inSubUl = closeStructures(body, state, inSubUl);
                state = State.NORMAL;
                body.append("<pre><code>");
                state = State.IN_PRE;
                continue;
            }

            // ── Blank line ────────────────────────────────────────────────────
            if (line.isBlank()) {
                if (inSubUl) { body.append("</ul>\n"); inSubUl = false; }
                if (state == State.IN_UL)    { body.append("</ul>\n"); state = State.NORMAL; }
                else if (state == State.IN_OL)   { body.append("</li>\n</ol>\n"); state = State.NORMAL; }
                else if (state == State.IN_TABLE) { body.append("</tbody></table>\n"); state = State.NORMAL; }
                continue;
            }

            // ── Table separator row (|---|---| — skip it) ─────────────────────
            if (line.matches("^\\|[-|: ]+\\|$")) {
                continue;
            }

            // ── Table data / header row ───────────────────────────────────────
            if (line.startsWith("|") && line.endsWith("|")) {
                if (state != State.IN_TABLE) {
                    inSubUl = closeStructures(body, state, inSubUl);
                    state = State.NORMAL;
                    body.append("<table>\n<thead><tr>");
                    for (String cell : splitRow(line))
                        body.append("<th>").append(inline(cell.strip())).append("</th>");
                    body.append("</tr></thead>\n<tbody>\n");
                    state = State.IN_TABLE;
                } else {
                    body.append("<tr>");
                    for (String cell : splitRow(line))
                        body.append("<td>").append(inline(cell.strip())).append("</td>");
                    body.append("</tr>\n");
                }
                continue;
            }

            // Close table if we were in one and hit a non-table line
            if (state == State.IN_TABLE) {
                body.append("</tbody></table>\n");
                state = State.NORMAL;
            }

            // ── Horizontal rule ───────────────────────────────────────────────
            if (line.matches("^---+$")) {
                inSubUl = closeStructures(body, state, inSubUl);
                state = State.NORMAL;
                body.append("<hr/>\n");
                continue;
            }

            // ── Headings ──────────────────────────────────────────────────────
            if (line.startsWith("#### ")) {
                inSubUl = closeStructures(body, state, inSubUl); state = State.NORMAL;
                String t = line.substring(5);
                body.append("<h4 id=\"").append(anchor(t)).append("\">").append(inline(t)).append("</h4>\n");
                continue;
            }
            if (line.startsWith("### ")) {
                inSubUl = closeStructures(body, state, inSubUl); state = State.NORMAL;
                String t = line.substring(4);
                body.append("<h3 id=\"").append(anchor(t)).append("\">").append(inline(t)).append("</h3>\n");
                continue;
            }
            if (line.startsWith("## ")) {
                inSubUl = closeStructures(body, state, inSubUl); state = State.NORMAL;
                String t = line.substring(3);
                body.append("<h2 id=\"").append(anchor(t)).append("\">").append(inline(t)).append("</h2>\n");
                continue;
            }
            if (line.startsWith("# ")) {
                inSubUl = closeStructures(body, state, inSubUl); state = State.NORMAL;
                String t = line.substring(2);
                body.append("<h1 id=\"").append(anchor(t)).append("\">").append(inline(t)).append("</h1>\n");
                continue;
            }

            // ── Blockquote ────────────────────────────────────────────────────
            if (line.startsWith("> ")) {
                inSubUl = closeStructures(body, state, inSubUl); state = State.NORMAL;
                body.append("<blockquote>").append(inline(line.substring(2))).append("</blockquote>\n");
                continue;
            }

            // ── Ordered list item ─────────────────────────────────────────────
            if (line.matches("^\\d+\\. .+")) {
                if (inSubUl) { body.append("</ul>\n"); inSubUl = false; }
                if (state == State.IN_OL) {
                    // Close the previous open <li>
                    body.append("</li>\n");
                } else {
                    inSubUl = closeStructures(body, state, false);
                    state = State.NORMAL;
                    body.append("<ol>\n");
                    state = State.IN_OL;
                }
                String text = line.replaceFirst("^\\d+\\. ", "");
                body.append("<li>").append(inline(text)); // <li> left open for possible sub-items
                continue;
            }

            // ── Unordered list item ───────────────────────────────────────────
            if (line.matches("^\\s*-\\s.+")) {
                int dashPos = line.indexOf("- ");
                int indent = dashPos;
                String text = line.substring(dashPos + 2);

                if (state == State.IN_OL && indent >= 3) {
                    // Sub-item under the current <ol><li>
                    if (!inSubUl) { body.append("\n<ul>\n"); inSubUl = true; }
                    body.append("<li>").append(inline(text)).append("</li>\n");
                } else {
                    // Top-level <ul> item
                    if (inSubUl) { body.append("</ul>\n"); inSubUl = false; }
                    if (state == State.IN_OL) { body.append("</li>\n</ol>\n"); state = State.NORMAL; }
                    if (state != State.IN_UL) { body.append("<ul>\n"); state = State.IN_UL; }
                    body.append("<li>").append(inline(text)).append("</li>\n");
                }
                continue;
            }

            // ── Paragraph ─────────────────────────────────────────────────────
            inSubUl = closeStructures(body, state, inSubUl);
            state = State.NORMAL;
            body.append("<p>").append(inline(line)).append("</p>\n");
        }

        // Close any open structures at end-of-file
        if (inSubUl) body.append("</ul>\n");
        if (state == State.IN_UL)    body.append("</ul>\n");
        else if (state == State.IN_OL)   body.append("</li>\n</ol>\n");
        else if (state == State.IN_TABLE) body.append("</tbody></table>\n");

        return wrapHtml(body.toString());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Closes the current list or table structure; returns the new inSubUl value (always false). */
    private static boolean closeStructures(StringBuilder sb, State state, boolean inSubUl) {
        if (inSubUl) sb.append("</ul>\n");
        if (state == State.IN_UL)    sb.append("</ul>\n");
        else if (state == State.IN_OL)   sb.append("</li>\n</ol>\n");
        else if (state == State.IN_TABLE) sb.append("</tbody></table>\n");
        return false;
    }

    /** Splits a Markdown table row on | while ignoring leading and trailing pipes. */
    private static String[] splitRow(String line) {
        return line.substring(1, line.length() - 1).split("\\|");
    }

    /** Applies inline Markdown formatting (code, bold, italic, links) to a single text span. */
    private static String inline(String text) {
        text = escHtml(text);
        // Inline code — process before bold/italic to avoid double-processing
        text = text.replaceAll("`([^`]+)`", "<code>$1</code>");
        // Bold (**text**)
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        // Italic (*text*) — negative lookaround avoids matching inside **bold**
        text = text.replaceAll("(?<![*])\\*(?![*])(.+?)(?<![*])\\*(?![*])", "<em>$1</em>");
        // Links [text](url)
        text = text.replaceAll("\\[([^\\]]+)]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        return text;
    }

    /**
     * Generates a GitHub-style anchor ID from heading text.
     * Rules: lowercase, strip inline markup, keep only [a-z0-9 \-], replace spaces with '-'.
     * Example: "1. First Launch & Setup" → "1-first-launch--setup"
     */
    private static String anchor(String text) {
        return text
                .replaceAll("\\*\\*(.+?)\\*\\*", "$1")   // strip bold
                .replaceAll("\\*(.+?)\\*", "$1")           // strip italic
                .replaceAll("`(.+?)`", "$1")               // strip inline code
                .toLowerCase()
                .replaceAll("[^a-z0-9 \\-]", "")          // keep only alphanumeric, space, hyphen
                .replaceAll(" ", "-");
    }

    private static String escHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String wrapHtml(String body) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>"
                + HTML_CSS + "</style></head><body>" + body + ANCHOR_SCRIPT + "</body></html>";
    }

    private enum State { NORMAL, IN_UL, IN_OL, IN_TABLE, IN_PRE }

    // ── Anchor-click interceptor ──────────────────────────────────────────────
    // loadContent() sets the page base to about:blank, so href="#id" would trigger
    // a full navigation instead of an in-page scroll. This script cancels that and
    // calls scrollIntoView() directly.
    private static final String ANCHOR_SCRIPT = """
            <script>
            document.addEventListener('click', function(e) {
                var el = e.target;
                while (el && el.tagName !== 'A') el = el.parentElement;
                if (el && el.getAttribute('href') && el.getAttribute('href').startsWith('#')) {
                    e.preventDefault();
                    var id = el.getAttribute('href').substring(1);
                    var target = document.getElementById(id);
                    if (target) target.scrollIntoView({behavior: 'smooth', block: 'start'});
                }
            });
            </script>
            """;

    // ── Embedded HTML/CSS — mirrors the app's design tokens ──────────────────

    private static final String HTML_CSS = """
            * { box-sizing: border-box; }
            body {
                font-family: 'Segoe UI', Arial, sans-serif;
                font-size: 13px;
                color: #0f3d4a;
                line-height: 1.75;
                margin: 0;
                padding: 6px 20px 32px 20px;
                background: #ffffff;
            }
            h1 {
                font-size: 20px;
                font-weight: 700;
                color: #0f3d4a;
                margin: 0 0 14px 0;
                padding-bottom: 8px;
                border-bottom: 2px solid #3db89a;
            }
            h2 {
                font-size: 14.5px;
                font-weight: 700;
                color: #2a8a7a;
                margin: 28px 0 8px 0;
                padding: 6px 10px 6px 12px;
                border-left: 3px solid #3db89a;
                background: rgba(42,138,122,0.05);
                border-radius: 0 4px 4px 0;
            }
            h3 {
                font-size: 13.5px;
                font-weight: 700;
                color: #0f3d4a;
                margin: 18px 0 5px 0;
            }
            h4 {
                font-size: 13px;
                font-weight: 700;
                color: #2a8a7a;
                margin: 14px 0 4px 0;
            }
            p { margin: 4px 0 10px 0; }
            hr {
                border: none;
                border-top: 1px solid rgba(42,138,122,0.25);
                margin: 18px 0;
            }
            ul, ol { padding-left: 22px; margin: 4px 0 10px 0; }
            li { margin-bottom: 3px; }
            ul ul, ol ul { margin: 5px 0 3px 0; }
            a { color: #2a8a7a; text-decoration: none; }
            a:hover { text-decoration: underline; }
            code {
                font-family: 'Consolas', 'Courier New', monospace;
                font-size: 11.5px;
                background: rgba(42,138,122,0.09);
                padding: 1px 5px;
                border-radius: 4px;
                color: #0f3d4a;
            }
            pre {
                background: rgba(15,61,74,0.06);
                border-left: 3px solid #3db89a;
                padding: 10px 16px;
                border-radius: 0 6px 6px 0;
                font-size: 11.5px;
                margin: 8px 0 12px 0;
                white-space: pre-wrap;
                word-break: break-all;
            }
            pre code { background: none; padding: 0; border-radius: 0; font-size: inherit; }
            blockquote {
                border-left: 3px solid #f0a500;
                margin: 10px 0;
                padding: 8px 14px;
                background: rgba(240,165,0,0.07);
                border-radius: 0 6px 6px 0;
                color: #5a3a00;
            }
            strong { font-weight: 600; color: #0f3d4a; }
            em { color: #4a7a88; font-style: italic; }
            table {
                width: 100%;
                border-collapse: collapse;
                margin: 8px 0 16px 0;
                font-size: 12.5px;
            }
            th {
                background: rgba(42,138,122,0.1);
                color: #2a8a7a;
                font-weight: 700;
                text-align: left;
                padding: 7px 10px;
                border-bottom: 1.5px solid rgba(42,138,122,0.35);
            }
            td {
                padding: 6px 10px;
                border-bottom: 1px solid rgba(42,138,122,0.1);
                vertical-align: top;
            }
            tr:last-child td { border-bottom: none; }
            """;
}
