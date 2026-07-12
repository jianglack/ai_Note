package com.ainote.app.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MemoryPrivacyService {

    private static final Pattern CREDENTIAL_PATTERN = Pattern.compile(
            "(?i)(api[_\\s-]?key\\s*[:=]\\s*\\S+|secret\\s*[:=]\\s*\\S+|password\\s*[:=]\\s*\\S+|"
                    + "passwd\\s*[:=]\\s*\\S+|pwd\\s*[:=]\\s*\\S+|access[_\\s-]?token\\s*[:=]\\s*\\S+|"
                    + "auth[_\\s-]?token\\s*[:=]\\s*\\S+|session[_\\s-]?token\\s*[:=]\\s*\\S+|"
                    + "bearer\\s+[a-z0-9._\\-]+|sk-[a-z0-9_\\-]{16,}|AKIA[0-9A-Z]{16}|"
                    + "-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----)");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern CHINA_MOBILE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?86[-\\s]?)?1[3-9]\\d[-\\s]?\\d{4}[-\\s]?\\d{4}(?!\\d)");
    private static final Pattern US_PHONE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\+?1[-.\\s]?)?\\(?[2-9]\\d{2}\\)?[-.\\s]?[2-9]\\d{2}[-.\\s]?\\d{4}(?!\\d)");
    private static final Pattern CHINA_ID_PATTERN = Pattern.compile(
            "(?<![0-9A-Za-z])\\d{6}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])"
                    + "(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx](?![0-9A-Za-z])");
    private static final Pattern PAYMENT_CARD_CANDIDATE_PATTERN = Pattern.compile(
            "(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)");
    private static final Pattern ADDRESS_LIKE_PATTERN = Pattern.compile(
            "(?i)((?:my|our|home|shipping|billing|mailing|residential|work|office)\\s+address|"
                    + "address\\s*(?:is|:)|住址|家庭地址|公司地址|我的地址|我家地址)"
                    + "[:：]?\\s*.{0,80}?(?:\\d+\\s+\\S+\\s+"
                    + "(?:street|st\\.?|road|rd\\.?|avenue|ave\\.?|lane|ln\\.?|drive|dr\\.?|blvd)|"
                    + "路|街|号|小区|building)");

    private static final List<Rule> SIMPLE_RULES = List.of(
            new Rule("credential", CREDENTIAL_PATTERN, true),
            new Rule("email", EMAIL_PATTERN, true),
            new Rule("phone", CHINA_MOBILE_PATTERN, true),
            new Rule("phone", US_PHONE_PATTERN, true),
            new Rule("china_id", CHINA_ID_PATTERN, true),
            new Rule("address_like", ADDRESS_LIKE_PATTERN, true)
    );

    public MemoryPrivacyScanResult scan(String text) {
        List<RawFinding> rawFindings = findRawFindings(text);
        List<MemoryPrivacyFinding> findings = rawFindings.stream()
                .map(finding -> new MemoryPrivacyFinding(
                        finding.type(),
                        hashForAudit(finding.value()),
                        finding.start(),
                        finding.end(),
                        finding.blocking()))
                .toList();
        boolean safeToStore = findings.stream().noneMatch(MemoryPrivacyFinding::blocking);
        return new MemoryPrivacyScanResult(safeToStore, findings, summarizeFindings(findings));
    }

    public String redact(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        List<RawFinding> findings = findRawFindings(text);
        if (findings.isEmpty()) {
            return text;
        }
        StringBuilder redacted = new StringBuilder();
        int cursor = 0;
        for (RawFinding finding : findings) {
            if (finding.start() < cursor) {
                continue;
            }
            redacted.append(text, cursor, finding.start());
            redacted.append("[REDACTED:").append(finding.type()).append("]");
            cursor = finding.end();
        }
        redacted.append(text.substring(cursor));
        return redacted.toString();
    }

    public Map<String, Integer> countFindings(Collection<String> texts) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if (texts == null) {
            return counts;
        }
        for (String text : texts) {
            for (MemoryPrivacyFinding finding : scan(text).findings()) {
                counts.merge(finding.type(), 1, Integer::sum);
            }
        }
        return counts;
    }

    public String hashForAudit(String value) {
        if (value == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hashed) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private List<RawFinding> findRawFindings(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<RawFinding> findings = new ArrayList<>();
        for (Rule rule : SIMPLE_RULES) {
            Matcher matcher = rule.pattern().matcher(text);
            while (matcher.find()) {
                findings.add(new RawFinding(
                        rule.type(),
                        matcher.start(),
                        matcher.end(),
                        matcher.group(),
                        rule.blocking()));
            }
        }
        Matcher cardMatcher = PAYMENT_CARD_CANDIDATE_PATTERN.matcher(text);
        while (cardMatcher.find()) {
            String candidate = cardMatcher.group();
            String digits = candidate.replaceAll("[^0-9]", "");
            if (digits.length() >= 13 && digits.length() <= 19 && passesLuhn(digits)) {
                findings.add(new RawFinding(
                        "payment_card",
                        cardMatcher.start(),
                        cardMatcher.end(),
                        candidate,
                        true));
            }
        }
        return collapseOverlaps(findings);
    }

    private List<RawFinding> collapseOverlaps(List<RawFinding> findings) {
        return findings.stream()
                .sorted(Comparator
                        .comparingInt(RawFinding::start)
                        .thenComparing((RawFinding finding) -> finding.end() - finding.start(), Comparator.reverseOrder()))
                .collect(ArrayList::new, this::appendIfNotOverlapping, ArrayList::addAll);
    }

    private void appendIfNotOverlapping(List<RawFinding> accepted, RawFinding candidate) {
        if (accepted.isEmpty()) {
            accepted.add(candidate);
            return;
        }
        RawFinding previous = accepted.get(accepted.size() - 1);
        if (candidate.start() >= previous.end()) {
            accepted.add(candidate);
        }
    }

    private boolean passesLuhn(String digits) {
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int value = digits.charAt(i) - '0';
            if (doubleDigit) {
                value *= 2;
                if (value > 9) {
                    value -= 9;
                }
            }
            sum += value;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private Map<String, Integer> summarizeFindings(List<MemoryPrivacyFinding> findings) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MemoryPrivacyFinding finding : findings) {
            counts.merge(finding.type(), 1, Integer::sum);
        }
        return counts;
    }

    private record Rule(String type, Pattern pattern, boolean blocking) {
    }

    private record RawFinding(String type, int start, int end, String value, boolean blocking) {
    }

    public record MemoryPrivacyFinding(
            String type,
            String tokenHash,
            int start,
            int end,
            boolean blocking
    ) {
    }

    public record MemoryPrivacyScanResult(
            boolean safeToStore,
            List<MemoryPrivacyFinding> findings,
            Map<String, Integer> counts
    ) {
        public boolean hasBlockingFindings() {
            return findings.stream().anyMatch(MemoryPrivacyFinding::blocking);
        }
    }
}
