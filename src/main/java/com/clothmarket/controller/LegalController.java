package com.clothmarket.controller;

import com.clothmarket.service.LegalPages;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * Public pages Google Play and users need: /api/legal/privacy, /api/legal/terms,
 * /api/legal/delete-account. Templates live in resources/legal/. They are served
 * only when the operator has configured their real details (LEGAL_* variables);
 * until then the endpoint answers 503 rather than publish placeholder text.
 * The wording itself MUST be reviewed by a lawyer before launch.
 */
@RestController
@RequestMapping("/legal")
public class LegalController {

    private static final Set<String> PAGES = Set.of("privacy", "terms", "delete-account");

    @Value("${legal.company-name:}") private String companyName;
    @Value("${legal.support-email:}") private String supportEmail;
    @Value("${legal.grievance-officer-name:}") private String grievanceName;
    @Value("${legal.grievance-officer-email:}") private String grievanceEmail;
    @Value("${legal.address:}") private String address;
    @Value("${legal.effective-date:}") private String effectiveDate;
    @Value("${legal.terms-version:1}") private String version;

    @GetMapping(value = "/{page}", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public ResponseEntity<String> page(@PathVariable String page) throws IOException {
        if (!PAGES.contains(page)) {
            return ResponseEntity.notFound().build();
        }
        String template = new String(new ClassPathResource("legal/" + page + ".html")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        String html = LegalPages.render(template, Map.of(
                "COMPANY_NAME", companyName,
                "SUPPORT_EMAIL", supportEmail,
                "GRIEVANCE_OFFICER_NAME", grievanceName,
                "GRIEVANCE_OFFICER_EMAIL", grievanceEmail,
                "POSTAL_ADDRESS", address,
                "EFFECTIVE_DATE", effectiveDate,
                "VERSION", version));
        if (html == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("This page is not configured yet.");
        }
        return ResponseEntity.ok(html);
    }
}
