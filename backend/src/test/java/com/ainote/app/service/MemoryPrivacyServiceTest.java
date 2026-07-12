package com.ainote.app.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryPrivacyServiceTest {

    private final MemoryPrivacyService privacyService = new MemoryPrivacyService();

    @Test
    void scansStrongPiiWithoutReturningRawValues() {
        String text = String.join(" ",
                "email alice@example.com",
                "phone 13812345678",
                "id 110105199003077614",
                "card 4111 1111 1111 1111",
                "api_key=sk-abcdefghijklmnopqrstuvwxyz");

        MemoryPrivacyService.MemoryPrivacyScanResult scan = privacyService.scan(text);

        assertThat(scan.safeToStore()).isFalse();
        assertThat(scan.hasBlockingFindings()).isTrue();
        assertThat(scan.counts().keySet())
                .contains("email", "phone", "china_id", "payment_card", "credential");
        assertThat(scan.findings())
                .extracting(MemoryPrivacyService.MemoryPrivacyFinding::tokenHash)
                .allSatisfy(hash -> {
                    assertThat(hash).hasSize(64);
                    assertThat(text).doesNotContain(hash);
                });
    }

    @Test
    void redactsDetectedValuesByCategory() {
        String redacted = privacyService.redact(
                "email alice@example.com and card 4111111111111111 and phone 13812345678");

        assertThat(redacted).contains("[REDACTED:email]");
        assertThat(redacted).contains("[REDACTED:payment_card]");
        assertThat(redacted).contains("[REDACTED:phone]");
        assertThat(redacted).doesNotContain("alice@example.com");
        assertThat(redacted).doesNotContain("4111111111111111");
        assertThat(redacted).doesNotContain("13812345678");
    }

    @Test
    void countsFindingsAcrossMultipleTexts() {
        assertThat(privacyService.countFindings(List.of(
                "email a@example.com",
                "backup email b@example.com token=sk-abcdefghijklmnop")))
                .containsEntry("email", 2)
                .containsEntry("credential", 1);
    }

    @Test
    void detectsPersonalAddressButDoesNotFlagTechnicalAddressWords() {
        assertThat(privacyService.scan("my home address is 123 Main Street").counts())
                .containsEntry("address_like", 1);

        assertThat(privacyService.scan(
                "Address width is 32 bits. Address the following project questions.").safeToStore())
                .isTrue();
    }
}
