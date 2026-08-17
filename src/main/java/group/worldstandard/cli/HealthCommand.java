package group.worldstandard.cli;

import group.worldstandard.infrastructure.HealthChecker;
import picocli.CommandLine;

@CommandLine.Command(
    name = "health",
    description = "Health checks",
    subcommands = {
        HealthCommand.Check.class,
        HealthCommand.Liveness.class,
        HealthCommand.Readiness.class
    }
)
public class HealthCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Usage: mailctl health [check|liveness|readiness]");
    }

    @CommandLine.Command(name = "check", description = "Full health check")
    public static class Check implements Runnable {
        @CommandLine.Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        private final HealthChecker healthChecker;

        public Check(HealthChecker healthChecker) {
            this.healthChecker = healthChecker;
        }

        public Check() {
            this.healthChecker = null;
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

    @CommandLine.Command(name = "liveness", description = "Kubernetes liveness probe")
    public static class Liveness implements Runnable {
        private final HealthChecker healthChecker;

        public Liveness(HealthChecker healthChecker) {
            this.healthChecker = healthChecker;
        }

        public Liveness() {
            this.healthChecker = null;
        }

        @Override
        public void run() {
            if (healthChecker == null) {
                System.err.println("Service not initialized. Use main entry point.");
                System.exit(1);
            }
            
            System.out.println("alive");
        }
    }

    @CommandLine.Command(name = "readiness", description = "Kubernetes readiness probe")
    public static class Readiness implements Runnable {
        private final HealthChecker healthChecker;

        public Readiness(HealthChecker healthChecker) {
            this.healthChecker = healthChecker;
        }

        public Readiness() {
            this.healthChecker = null;
        }

        @Override
        public void run() {
            if (healthChecker == null) {
                System.err.println("Service not initialized. Use main entry point.");
                System.exit(1);
            }
            
            try {
                var report = healthChecker.runFullCheck();
                if (report.overallHealthy()) {
                    System.out.println("ready");
                    System.exit(0);
                } else {
                    System.out.println("not ready");
                    System.exit(1);
                }
            } catch (Exception e) {
                System.err.println("Readiness check failed: " + e.getMessage());
                System.exit(1);
            }
        }
    }
}