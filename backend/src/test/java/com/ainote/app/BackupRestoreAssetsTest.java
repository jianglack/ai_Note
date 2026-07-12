package com.ainote.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class BackupRestoreAssetsTest {

    private final Path root = repositoryRoot();

    @TempDir
    Path tempDir;

    @Test
    void packageAndRestoreScriptsKeepRequiredFailClosedControls() throws IOException {
        String backup = Files.readString(root.resolve("scripts/backup-ainote.ps1"));
        String restore = Files.readString(root.resolve("scripts/restore-ainote.ps1"));
        String common = Files.readString(root.resolve("scripts/lib/AiNoteBackup.Common.ps1"));

        assertThat(backup)
                .contains("pg_dump")
                .contains("--format=custom")
                .contains("--no-owner")
                .contains("RequireQuiescentInventory")
                .contains("AES-256-GCM")
                .contains("productionProven = $false")
                .doesNotContain("POSTGRES_PASSWORD=");
        assertThat(restore)
                .contains("Backup manifest SHA-256 mismatch")
                .contains("Backup manifest HMAC authentication failed")
                .contains("Encrypted backup SHA-256 mismatch")
                .contains("Target database is not empty; restore refused")
                .contains("pg_restore")
                .contains("--exit-on-error")
                .contains("Compare-AiNoteInventory")
                .contains("productionProven = $false");
        assertThat(common)
                .contains("must decode to exactly 32 bytes")
                .contains("AiNoteBackupCrypto.java")
                .contains("Get-AiNotePostgresInventory")
                .contains("Get-AiNotePostgresSequences")
                .contains("Get-AiNoteManifestHmac")
                .contains("Test-AiNoteFixedTimeHexEquals")
                .contains("@(\"t\", \"true\", \"1\")");
    }

    @Test
    void drillIsNamespacedDestructiveAndTestsNegativePaths() throws IOException {
        String drill = Files.readString(root.resolve("scripts/run-backup-restore-drill.ps1"));

        assertThat(drill)
                .contains("^ainote-dr-")
                .contains("Refusing to remove non-drill container")
                .contains("Source PostgreSQL still exists after destruction step")
                .contains("authenticatedCorruptionRejected")
                .contains("corruptTargetUntouched")
                .contains("nonEmptyTargetRejected")
                .contains("productionProven = $false")
                .contains("rotate_secret")
                .contains("rebuild_per_user_from_postgresql")
                .doesNotContain("docker rm -f ainote-postgres");
    }

    @Test
    void streamingCryptoRoundTripsMultipleChunksAndRejectsTampering() throws Exception {
        Path helper = root.resolve("scripts/lib/AiNoteBackupCrypto.java");
        Path plain = tempDir.resolve("plain.bin");
        Path encrypted = tempDir.resolve("backup.enc");
        Path restored = tempDir.resolve("restored.bin");
        Path rejectedOutput = tempDir.resolve("rejected.bin");
        byte[] content = new byte[5 * 1024 * 1024 + 137];
        new SecureRandom().nextBytes(content);
        Files.write(plain, content);
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encodedKey = Base64.getEncoder().encodeToString(key);

        assertThat(runCrypto(helper, "encrypt", plain, encrypted, encodedKey)).isZero();
        assertThat(runCrypto(helper, "decrypt", encrypted, restored, encodedKey)).isZero();
        assertThat(Files.mismatch(plain, restored)).isEqualTo(-1L);

        byte[] tampered = Files.readAllBytes(encrypted);
        tampered[tampered.length / 2] ^= 1;
        Files.write(encrypted, tampered);
        assertThat(runCrypto(helper, "decrypt", encrypted, rejectedOutput, encodedKey)).isNotZero();
        assertThat(rejectedOutput).doesNotExist();
    }

    @Test
    void designDeclaresAuthorityRpoRtoAndNonClaims() throws IOException {
        String design = Files.readString(root.resolve(
                "docs/superpowers/specs/2026-07-11-backup-restore-disaster-recovery-design.md"));

        assertThat(design)
                .contains("PostgreSQL is the sole authoritative data backup")
                .contains("Redis starts empty")
                .contains("Neo4j starts empty")
                .contains("RPO is the interval")
                .contains("RTO is measured")
                .contains("does not prove cloud snapshot integration")
                .contains("productionProven=false");
    }

    private int runCrypto(Path helper, String mode, Path input, Path output, String encodedKey)
            throws IOException, InterruptedException {
        Path javaExecutable = Path.of(
                System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        ProcessBuilder builder = new ProcessBuilder(
                javaExecutable.toString(), helper.toString(), mode, input.toString(), output.toString(),
                "AINOTE_TEST_BACKUP_KEY");
        builder.environment().put("AINOTE_TEST_BACKUP_KEY", encodedKey);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        return process.waitFor();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (current.getFileName() != null && "backend".equals(current.getFileName().toString())) {
            return current.getParent();
        }
        return current;
    }
}
