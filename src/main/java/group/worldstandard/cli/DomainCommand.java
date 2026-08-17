package group.worldstandard.cli;

import group.worldstandard.ServiceRegistry;
import group.worldstandard.domain.Domain;
import group.worldstandard.domain.DomainService;
import picocli.CommandLine;

@CommandLine.Command(
    name = "domain",
    description = "Domain management",
    subcommands = {
        DomainCommand.Add.class,
        DomainCommand.Remove.class,
        DomainCommand.ListCmd.class
    }
)
public class DomainCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Usage: mailctl domain [add|remove|list]");
    }

    @CommandLine.Command(name = "add", description = "Add a new domain")
    public static class Add implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Domain name (e.g., example.com)")
        String domain;

        @CommandLine.Option(names = {"--dkim-selector"}, description = "DKIM selector", defaultValue = "mail")
        String dkimSelector;

        @CommandLine.Option(names = {"--dkim-key-size"}, description = "DKIM key size", defaultValue = "2048")
        int dkimKeySize;

        @CommandLine.Option(names = {"--dkim-algorithm"}, description = "DKIM algorithm (rsa2048, rsa4096, ed25519)", defaultValue = "rsa2048")
        String dkimAlgorithm;

        private final DomainService domainService;

        public Add(DomainService domainService) {
            this.domainService = domainService;
        }

        public Add() {
            this.domainService = null;
        }

        @Override
        public void run() {
            if (domainService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                Domain created = domainService.addDomain(domain, dkimSelector, dkimKeySize, dkimAlgorithm);
                System.out.println("Domain added successfully:");
                System.out.println("  ID: " + created.id());
                System.out.println("  Name: " + created.name());
                System.out.println("  Status: " + created.status());
                System.out.println("  DKIM Selector: " + created.dkimSelector());
                System.out.println("  DKIM Key Size: " + created.dkimKeySize());
                System.out.println("  DKIM Algorithm: " + created.dkimAlgorithm());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "remove", description = "Remove a domain")
    public static class Remove implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Domain name")
        String domain;

        @CommandLine.Option(names = {"--force"}, description = "Force removal without confirmation")
        boolean force;

        private final DomainService domainService;

        public Remove(DomainService domainService) {
            this.domainService = domainService;
        }

        public Remove() {
            this.domainService = null;
        }

        @Override
        public void run() {
            if (domainService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            if (!force) {
                System.out.print("Are you sure you want to remove domain '" + domain + "'? (y/N): ");
                String confirm = System.console() != null ? System.console().readLine() : new java.util.Scanner(System.in).nextLine();
                if (!"y".equalsIgnoreCase(confirm)) {
                    System.out.println("Cancelled.");
                    return;
                }
            }
            
            try {
                var domainOpt = domainService.getDomainByName(domain);
                if (domainOpt.isEmpty()) {
                    System.err.println("Domain not found: " + domain);
                    return;
                }
                domainService.removeDomain(domainOpt.get().id());
                System.out.println("Domain removed: " + domain);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "list", description = "List all domains")
    public static class ListCmd implements Runnable {
        @CommandLine.Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        private final DomainService domainService;

        public ListCmd() {
            this.domainService = ServiceRegistry.getInstance().getDomainService();
        }

        public ListCmd(DomainService domainService) {
            this.domainService = domainService;
        }

        @Override
        public void run() {
            if (domainService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                java.util.List<Domain> domains = domainService.listDomains();
                if (json) {
                    System.out.print("[");
                    for (int i = 0; i < domains.size(); i++) {
                        Domain d = domains.get(i);
                        System.out.printf("{\"id\":\"%s\",\"name\":\"%s\",\"status\":\"%s\"}",
                            d.id(), d.name(), d.status());
                        if (i < domains.size() - 1) System.out.print(",");
                    }
                    System.out.println("]");
                } else {
                    if (domains.isEmpty()) {
                        System.out.println("No domains found.");
                    } else {
                        System.out.println("Domains:");
                        for (Domain d : domains) {
                            System.out.printf("  %s (%s) - %s - DKIM: %s/%d/%s%n", 
                                d.name(), d.id(), d.status(), 
                                d.dkimSelector(), d.dkimKeySize(), d.dkimAlgorithm());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}