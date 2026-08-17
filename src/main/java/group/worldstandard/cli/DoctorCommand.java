package group.worldstandard.cli;

import group.worldstandard.infrastructure.HealthChecker;
import group.worldstandard.infrastructure.ProcessRunner;
import picocli.CommandLine;

@CommandLine.Command(
    name = "doctor",
    description = "Comprehensive system health diagnostics",
    subcommands = {
        DoctorCommand.Run.class,
        DoctorCommand.Dns.class,
        DoctorCommand.Tls.class,
        DoctorCommand.Services.class,
        DoctorCommand.Storage.class,
        DoctorCommand.Security.class
    }
)
public class DoctorCommand implements Runnable {
    @Override
    public void run() {
        // Default to full run
        new Run(null, null).run();
    }

    @CommandLine.Command(name = "run", description = "Run all diagnostic checks")
    public static class Run implements Runnable {
        @CommandLine.Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        @CommandLine.Option(names = {"--fix"}, description = "Attempt to auto-fix issues where possible")
        boolean fix;

        private final HealthChecker healthChecker;
        private final ProcessRunner processRunner;

        public Run(HealthChecker healthChecker, ProcessRunner processRunner) {
            this.healthChecker = healthChecker;
            this.processRunner = processRunner;
        }

        public Run() {
            this.healthChecker = null;
            this.processRunner = null;
        }

        @Override
        public void run() {
            if (healthChecker == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                var report = healthChecker.runFullCheck();
                if (json) {
                    System.out.println("{");
                    System.out.println("  \"healthy\": " + report.overallHealthy() + ",");
                    System.out.println("  \"checks\": [");
                    for (int i = 0; i < report.checks().size(); i++) {
                        var c = report.checks().get(i);
                        System.out.print("    {\"name\":\"" + c.name() + "\",\"passed\":" + c.passed() + ",\"message\":\"" + c.message().replace("\"", "\\\"") + "\"}");
                        if (i < report.checks().size() - 1) System.out.print(",");
                        System.out.println();
                    }
                    System.out.println("  ]");
                    System.out.println("}");
                } else {
                    System.out.println(healthChecker.formatReport(report));
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "dns", description = "Check DNS records")
    public static class Dns implements Runnable {
        private final HealthChecker healthChecker;

        public Dns(HealthChecker healthChecker) {
            this.healthChecker = healthChecker;
        }

        public Dns() {
            this.healthChecker = null;
        }

        @Override
        public void run() {
            if (healthChecker == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            System.out.println("DNS Checks (placeholder - implement actual DNS lookups)");
        }
    }

    @CommandLine.Command(name = "tls", description = "Check TLS certificates")
    public static class Tls implements Runnable {
        private final HealthChecker healthChecker;

        public Tls(HealthChecker healthChecker) {
            this.healthChecker = healthChecker;
        }

        public Tls() {
            this.healthChecker = null;
        }

        @Override
        public void run() {
            if (healthChecker == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            System.out.println("TLS Checks (placeholder - implement actual cert validation)");
        }
    }

    @CommandLine.Command(name = "services", description = "Check service status")
    public static class Services implements Runnable {
        private final HealthChecker healthChecker;
        private final ProcessRunner processRunner;

        public Services(HealthChecker healthChecker, ProcessRunner processRunner) {
            this.healthChecker = healthChecker;
            this.processRunner = processRunner;
        }

        public Services() {
            this.healthChecker = null;
            this.processRunner = null;
        }

        @Override
        public void run() {
            if (healthChecker == null || processRunner == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            String[] services = {"postfix", "dovecot", "rspamd", "postgresql"};
            for (String svc : services) {
                try {
                    var result = processRunner.run(ProcessRunner.Command.SYSTEMD_STATUS, svc);
                    String status = result.stdout().trim();
                    System.out.printf("  %s: %s%n", svc, status);
                } catch (Exception e) {
                    System.out.printf("  %s: error (%s)%n", svc, e.getMessage());
                }
            }
        }
    }

    @CommandLine.Command(name = "storage", description = "Check storage and permissions")
    public static class Storage implements Runnable {
        private final HealthChecker healthChecker;

        public Storage(HealthChecker healthChecker) {
            this.healthChecker = healthChecker;
        }

        public Storage() {
            this.healthChecker = null;
        }

        @Override
        public void run() {
            if (healthChecker == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            System.out.println("Storage Checks (placeholder - implement disk/permissions checks)");
        }
    }

    @CommandLine.Command(name = "security", description = "Check security posture")
    public static class Security implements Runnable {
        private final HealthChecker healthChecker;

        public Security(HealthChecker healthChecker) {
            this.healthChecker = healthChecker;
        }

        public Security() {
            this.healthChecker = null;
        }

        @Override
        public void run() {
            if (healthChecker == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            System.out.println("Security Checks (placeholder - implement security checks)");
        }
    }
}