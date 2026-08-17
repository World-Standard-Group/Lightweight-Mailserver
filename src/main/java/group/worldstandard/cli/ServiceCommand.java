package group.worldstandard.cli;

import group.worldstandard.infrastructure.ProcessRunner;
import picocli.CommandLine;

@CommandLine.Command(
    name = "service",
    description = "Service management",
    subcommands = {
        ServiceCommand.Status.class,
        ServiceCommand.Reload.class,
        ServiceCommand.Restart.class,
        ServiceCommand.Enable.class,
        ServiceCommand.Disable.class
    }
)
public class ServiceCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Usage: mailctl service [status|reload|restart|enable|disable]");
    }

    @CommandLine.Command(name = "status", description = "Show service status")
    public static class Status implements Runnable {
        @CommandLine.Parameters(index = "0", arity = "0..1", description = "Service name (optional)")
        String service;

        private final ProcessRunner processRunner;

        public Status(ProcessRunner processRunner) {
            this.processRunner = processRunner;
        }

        public Status() {
            this.processRunner = null;
        }

        @Override
        public void run() {
            if (processRunner == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            String[] services = service != null ? new String[]{service} : new String[]{"postfix", "dovecot", "rspamd", "postgresql"};
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

    @CommandLine.Command(name = "reload", description = "Reload service configuration")
    public static class Reload implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Service name (postfix|dovecot|rspamd|all)")
        String service;

        private final ProcessRunner processRunner;

        public Reload(ProcessRunner processRunner) {
            this.processRunner = processRunner;
        }

        public Reload() {
            this.processRunner = null;
        }

        @Override
        public void run() {
            if (processRunner == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            String[] services = "all".equals(service) ? 
                new String[]{"postfix", "dovecot", "rspamd"} : new String[]{service};
            
            for (String svc : services) {
                try {
                    processRunner.requireSuccess(getReloadCommand(svc));
                    System.out.println("Reloaded: " + svc);
                } catch (Exception e) {
                    System.err.println("Failed to reload " + svc + ": " + e.getMessage());
                }
            }
        }

        private ProcessRunner.Command getReloadCommand(String service) {
            return switch (service) {
                case "postfix" -> ProcessRunner.Command.POSTFIX_RELOAD;
                case "dovecot" -> ProcessRunner.Command.DOVECOT_RELOAD;
                case "rspamd" -> ProcessRunner.Command.RSPAMD_RELOAD;
                default -> throw new IllegalArgumentException("Unknown service: " + service);
            };
        }
    }

    @CommandLine.Command(name = "restart", description = "Restart service")
    public static class Restart implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Service name (postfix|dovecot|rspamd|all)")
        String service;

        private final ProcessRunner processRunner;

        public Restart(ProcessRunner processRunner) {
            this.processRunner = processRunner;
        }

        public Restart() {
            this.processRunner = null;
        }

        @Override
        public void run() {
            if (processRunner == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            String[] services = "all".equals(service) ? 
                new String[]{"postfix", "dovecot", "rspamd"} : new String[]{service};
            
            for (String svc : services) {
                try {
                    processRunner.requireSuccess(ProcessRunner.Command.SYSTEMD_RESTART, svc);
                    System.out.println("Restarted: " + svc);
                } catch (Exception e) {
                    System.err.println("Failed to restart " + svc + ": " + e.getMessage());
                }
            }
        }
    }

    @CommandLine.Command(name = "enable", description = "Enable service at boot")
    public static class Enable implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Service name")
        String service;

        private final ProcessRunner processRunner;

        public Enable(ProcessRunner processRunner) {
            this.processRunner = processRunner;
        }

        public Enable() {
            this.processRunner = null;
        }

        @Override
        public void run() {
            if (processRunner == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                processRunner.requireSuccess(ProcessRunner.Command.SYSTEMD_ENABLE, service);
                System.out.println("Enabled: " + service);
            } catch (Exception e) {
                System.err.println("Failed to enable " + service + ": " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "disable", description = "Disable service at boot")
    public static class Disable implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Service name")
        String service;

        private final ProcessRunner processRunner;

        public Disable(ProcessRunner processRunner) {
            this.processRunner = processRunner;
        }

        public Disable() {
            this.processRunner = null;
        }

        @Override
        public void run() {
            if (processRunner == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                processRunner.requireSuccess(ProcessRunner.Command.SYSTEMD_DISABLE, service);
                System.out.println("Disabled: " + service);
            } catch (Exception e) {
                System.err.println("Failed to disable " + service + ": " + e.getMessage());
            }
        }
    }
}