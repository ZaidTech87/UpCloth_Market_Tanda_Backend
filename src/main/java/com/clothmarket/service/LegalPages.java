package com.clothmarket.service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills the {{PLACEHOLDER}} values of the legal page templates with the operator's
 * real details (HTML-escaped). If ANY placeholder is left unresolved the page is not
 * served, so a template with "{{COMPANY_NAME}}" can never go live by accident.
 */
public final class LegalPages {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Z_]+)}}");

    private LegalPages() {}

    /** @return rendered HTML, or null if a placeholder has no (non-blank) value. */
    public static String render(String template, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            if (v == null || v.isBlank()) {
                return null;
            }
            m.appendReplacement(out, Matcher.quoteReplacement(escape(v.trim())));
        }
        m.appendTail(out);
        return out.toString();
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
