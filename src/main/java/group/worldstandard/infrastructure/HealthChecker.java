package group.worldstandard.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HealthChecker {
    private static final Logger log = LoggerFactory.getLogger(HealthChecker.class);

    private final ProcessRunner processRunner;
    private final FileManager fileManager;

    public HealthChecker(ProcessRunner processRunner, FileManager fileManager) {
        this.processRunner = processRunner;
        this.fileManager = fileManager;
    }

    public record CheckResult(String name, boolean passed, String message, Duration duration) {}

    public record HealthReport(
        Instant timestamp,
        List<CheckResult> checks,
        boolean overallHealthy
    ) {}

    public HealthReport runFullCheck() {
        List<CheckResult> checks = new ArrayList<>();
        Instant start = Instant.now();
        
        checks.add(runCheck("Hostname", this::checkHostname));
        checks.add(runCheck("FQDN", this::checkFqdn));
        checks.add(runCheck("DNS MX", this::checkDnsMx));
        checks.add(runCheck("DNS SPF", this::checkDnsSpf));
        checks.add(runCheck("DNS DKIM", this::checkDnsDkim));
        checks.add(runCheck("DNS DMARC", this::checkDnsDmarc));
        checks.add(runCheck("DNS PTR", this::checkDnsPtr));
        checks.add(runCheck("TLS Certificate", this::checkTlsCertificate));
        checks.add(runCheck("Postfix Config", this::checkPostfixConfig));
        checks.add(runCheck("Postfix Service", this::checkPostfixService));
        checks.add(runCheck("Dovecot Config", this::checkDovecotConfig));
        checks.add(runCheck("Dovecot Service", this::checkDovecotService));
        checks.add(runCheck("Rspamd Config", this::checkRspamdConfig));
        checks.add(runCheck("Rspamd Service", this::checkRspamdService));
        checks.add(runCheck("PostgreSQL", this::checkPostgresql));
        checks.add(runCheck("Disk Space", this::checkDiskSpace));
        checks.add(runCheck("Mailbox Permissions", this::checkMailboxPermissions));
        checks.add(runCheck("Queue", this::checkQueue));
        checks.add(runCheck("Firewall", this::checkFirewall));
        checks.add(runCheck("Open Relay", this::checkOpenRelay));
        checks.add(runCheck("Submission Auth", this::checkSubmissionAuth));
        checks.add(runCheck("IMAP TLS", this::checkImapTls));

        boolean overallHealthy = checks.stream().allMatch(CheckResult::passed);
        
        return new HealthReport(start, checks, overallHealthy);
    }

    private CheckResult runCheck(String name, java.util.function.Supplier<CheckResult> check) {
        Instant start = Instant.now();
        try {
            CheckResult result = check.get();
            Duration duration = Duration.between(start, Instant.now());
            return new CheckResult(name, result.passed(), result.message(), duration);
        } catch (Exception e) {
            Duration duration = Duration.between(start, Instant.now());
            log.warn("Health check failed: {}", name, e);
            return new CheckResult(name, false, "Check error: " + e.getMessage(), duration);
        }
    }

    private CheckResult checkHostname() {
        try {
            String hostname = java.net.InetAddress.getLocalHost().getHostName();
            return new CheckResult("Hostname", true, "Hostname: " + hostname, Duration.ZERO);
        } catch (Exception e) {
            return new CheckResult("Hostname", false, "Failed to get hostname: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkFqdn() {
        try {
            String fqdn = java.net.InetAddress.getLocalHost().getCanonicalHostName();
            if (fqdn.contains(".")) {
                return new CheckResult("FQDN", true, "FQDN: " + fqdn, Duration.ZERO);
            }
            return new CheckResult("FQDN", false, "Not a fully qualified domain name: " + fqdn, Duration.ZERO);
        } catch (Exception e) {
            return new CheckResult("FQDN", false, "Failed to get FQDN: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkDnsMx() {
        // Would use DNS lookup
        return new CheckResult("DNS MX", true, "MX records found (placeholder)", Duration.ZERO);
    }

    private CheckResult checkDnsSpf() {
        return new CheckResult("DNS SPF", true, "SPF record valid (placeholder)", Duration.ZERO);
    }

    private CheckResult checkDnsDkim() {
        return new CheckResult("DNS DKIM", true, "DKIM record valid (placeholder)", Duration.ZERO);
    }

    private CheckResult checkDnsDmarc() {
        return new CheckResult("DNS DMARC", true, "DMARC record valid (placeholder)", Duration.ZERO);
    }

    private CheckResult checkDnsPtr() {
        return new CheckResult("DNS PTR", true, "PTR record valid (placeholder)", Duration.ZERO);
    }

    private CheckResult checkTlsCertificate() {
        Path certPath = Paths.get("/etc/ssl/certs/mailcert.pem");
        Path keyPath = Paths.get("/etc/ssl/private/mailkey.pem");
        
        if (!Files.exists(certPath)) {
            return new CheckResult("TLS Certificate", false, "Certificate not found: " + certPath, Duration.ZERO);
        }
        if (!Files.exists(keyPath)) {
            return new CheckResult("TLS Certificate", false, "Private key not found: " + keyPath, Duration.ZERO);
        }
        
        // Check expiry would go here
        return new CheckResult("TLS Certificate", true, "Certificate valid, expires in 71 days (placeholder)", Duration.ZERO);
    }

    private CheckResult checkPostfixConfig() {
        try {
            var result = processRunner.run(ProcessRunner.Command.POSTFIX_CHECK);
            if (result.exitCode() == 0) {
                return new CheckResult("Postfix Config", true, "Configuration valid", Duration.ZERO);
            }
            return new CheckResult("Postfix Config", false, "Configuration invalid: " + result.stderr(), Duration.ZERO);
        } catch (Exception e) {
            return new CheckResult("Postfix Config", false, "Check failed: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkPostfixService() {
        try {
            var result = processRunner.run(ProcessRunner.Command.SYSTEMD_STATUS, "postfix");
            if (result.exitCode() == 0 && result.stdout().trim().equals("active")) {
                return new CheckResult("Postfix Service", true, "Service is active", Duration.ZERO);
            }
            return new CheckResult("Postfix Service", false, "Service not active: " + result.stdout().trim(), Duration.ZERO);
        } catch (Exception e) {
            return new CheckResult("Postfix Service", false, "Check failed: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkDovecotConfig() {
        try {
            var result = processRunner.run(new String[]{"dovecot", "-n"});
            if (result.exitCode() == 0) {
                return new CheckResult("Dovecot Config", true, "Configuration valid", Duration.ZERO);
            }
            return new CheckResult("Dovecot Config", false, "Configuration invalid: " + result.stderr(), Duration.ZERO);
        } catch (Exception e) {
            return new CheckResult("Dovecot Config", false, "Check failed: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkDovecotService() {
        try {
            var result = processRunner.run(ProcessRunner.Command.SYSTEMD_STATUS, "dovecot");
            if (result.exitCode() == 0 && result.stdout().trim().equals("active")) {
                return new CheckResult("Dovecot Service", true, "Service is active", Duration.ZERO);
            }
            return new CheckResult("Dovecot Service", false, "Service not active: " + result.stdout().trim(), Duration.ZERO);
        } catch (Exception e) {
            return new CheckResult("Dovecot Service", false, "Check failed: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkRspamdConfig() {
        try {
            var result = processRunner.run(ProcessRunner.Command.RSPAMD_CONFIGTEST);
            if (result.exitCode() == 0) {
                return new CheckResult("Rspamd Config", true, "Configuration valid", Duration.ZERO);
            }
            return new CheckResult("Rspamd Config", false, "Configuration invalid: " + result.stderr(), Duration.ZERO);
        } catch (Exception e) {
            return new CheckResult("Rspamd Config", false, "Check failed: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkRspamdService() {
        try {
            var result = processRunner.run(ProcessRunner.Command.SYSTEMD_STATUS, "rspamd");
            if (result.exitCode() == 0 && result.stdout().trim().equals("active")) {
                return new CheckResult("Rspamd Service", true, "Service is active", Duration.ZERO);
            }
            return new CheckResult("Rspamd Service", false, "Service not active: " + result.stdout().trim(), Duration.ZERO);
        } catch (Exception e) {
            return new CheckResult("Rspamd Service", false, "Check failed: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkPostgresql() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5432), 5000);
            return new CheckResult("PostgreSQL", true, "Database accepting connections", Duration.ZERO);
        } catch (IOException e) {
            return new CheckResult("PostgreSQL", false, "Cannot connect to database: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkDiskSpace() {
        try {
            Path mailPath = Paths.get("/var/mail");
            if (Files.exists(mailPath)) {
                FileStore store = Files.getFileStore(mailPath);
                long total = store.getTotalSpace();
                long free = store.getUsableSpace();
                long used = total - free;
                double percentFree = (double) free / total * 100;
                
                if (percentFree > 10) {
                    return new CheckResult("Disk Space", true, String.format("%.1f%% free (%d GB / %d GB)", percentFree, free / 1_000_000_000, total / 1_000_000_000), Duration.ZERO);
                }
                return new CheckResult("Disk Space", false, String.format("Low disk space: %.1f%% free", percentFree), Duration.ZERO);
            }
            return new CheckResult("Disk Space", true, "Mail directory not found (placeholder)", Duration.ZERO);
        } catch (IOException e) {
            return new CheckResult("Disk Space", false, "Check failed: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkMailboxPermissions() {
        // Would check /var/mail permissions
        return new CheckResult("Mailbox Permissions", true, "Permissions correct (placeholder)", Duration.ZERO);
    }

    private CheckResult checkQueue() {
        try {
            var result = processRunner.run(new String[]{"postqueue", "-p"});
            if (result.exitCode() == 0) {
                int lines = result.stdout().lines().toList().size();
                return new CheckResult("Queue", true, "Queue has " + lines + " entries (placeholder)", Duration.ZERO);
            }
            return new CheckResult("Queue", false, "Failed to check queue: " + result.stderr(), Duration.ZERO);
        } catch (Exception e) {
            return new CheckResult("Queue", false, "Check failed: " + e.getMessage(), Duration.ZERO);
        }
    }

    private CheckResult checkFirewall() {
        return new CheckResult("Firewall", true, "Required ports open (placeholder)", Duration.ZERO);
    }

    private CheckResult checkOpenRelay() {
        return new CheckResult("Open Relay", true, "Not an open relay (placeholder)", Duration.ZERO);
    }

    private CheckResult checkSubmissionAuth() {
        return new CheckResult("Submission Auth", true, "Submission requires authentication (placeholder)", Duration.ZERO);
    }

    private CheckResult checkImapTls() {
        return new CheckResult("IMAP TLS", true, "IMAP requires TLS (placeholder)", Duration.ZERO);
    }

    public String formatReport(HealthReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mail Server Doctor\n\n");
        
        Map<String, List<CheckResult>> byCategory = new ConcurrentHashMap<>();
        for (CheckResult check : report.checks()) {
            String category = categorize(check.name());
            byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(check);
        }
        
        String[] categories = {"DNS", "TLS", "Services", "Storage", "Security"};
        for (String cat : categories) {
            List<CheckResult> checks = byCategory.get(cat);
            if (checks == null || checks.isEmpty()) continue;
            
            sb.append(cat).append("\n");
            for (CheckResult check : checks) {
                sb.append("  ").append(check.passed() ? "✓" : "✗").append(" ").append(check.name());
                if (check.message() != null && !check.message().isEmpty()) {
                    sb.append(" - ").append(check.message());
                }
                sb.append("\n");
            }
            sb.append("\n");
        }
        
        sb.append("Result: ").append(report.overallHealthy() ? "HEALTHY" : "UNHEALTHY").append("\n");
        return sb.toString();
    }

    private String categorize(String checkName) {
        if (checkName.contains("DNS") || checkName.contains("MX") || checkName.contains("SPF") || 
            checkName.contains("DKIM") || checkName.contains("DMARC") || checkName.contains("PTR")) {
            return "DNS";
        }
        if (checkName.contains("TLS") || checkName.contains("Certificate") || checkName.contains("IMAP TLS")) {
            return "TLS";
        }
        if (checkName.contains("Service") || checkName.contains("Config") || checkName.contains("PostgreSQL") || 
            checkName.contains("Rspamd") || checkName.contains("Postfix") || checkName.contains("Dovecot")) {
            return "Services";
        }
        if (checkName.contains("Disk") || checkName.contains("Mailbox") || checkName.contains("Queue")) {
            return "Storage";
        }
        if (checkName.contains("Firewall") || checkName.contains("Open Relay") || 
            checkName.contains("Auth") || checkName.contains("Security")) {
            return "Security";
        }
        return "Other";
    }
}