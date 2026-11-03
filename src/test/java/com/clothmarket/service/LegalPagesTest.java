package com.clothmarket.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LegalPagesTest {

    @Test
    void fillsAndEscapesValues() {
        String html = LegalPages.render("<p>{{COMPANY_NAME}} / {{SUPPORT_EMAIL}}</p>",
                Map.of("COMPANY_NAME", "A & B <Ltd>", "SUPPORT_EMAIL", "help@example.com"));
        assertEquals("<p>A &amp; B &lt;Ltd&gt; / help@example.com</p>", html);
    }

    @Test
    void refusesToRenderWhenAnyPlaceholderIsMissingOrBlank() {
        assertNull(LegalPages.render("{{COMPANY_NAME}} {{SUPPORT_EMAIL}}", Map.of("COMPANY_NAME", "X")));
        assertNull(LegalPages.render("{{COMPANY_NAME}}", Map.of("COMPANY_NAME", "  ")));
    }

    @Test
    void dollarSignsAndBackslashesInValuesAreLiteral() {
        assertEquals("a$1\\b", LegalPages.render("{{VERSION}}", Map.of("VERSION", "a$1\\b")));
    }
}
