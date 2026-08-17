package group.worldstandard.cli;

import group.worldstandard.ServiceRegistry;
import group.worldstandard.routing.Alias;
import group.worldstandard.routing.RoutingService;
import picocli.CommandLine;

@CommandLine.Command(
    name = "alias",
    description = "Alias management",
    subcommands = {
        AliasCommand.Add.class,
        AliasCommand.Remove.class,
        AliasCommand.ListCmd.class
    }
)
public class AliasCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Usage: mailctl alias [add|remove|list]");
    }

    @CommandLine.Command(name = "add", description = "Add a new alias")
    public static class Add implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Alias address (e.g., support@example.com)")
        String alias;

        @CommandLine.Parameters(index = "1", description = "Target address(es), comma-separated (e.g., alice@example.com or alice@example.com,bob@example.com)")
        String targets;

        private final RoutingService routingService;

        public Add(RoutingService routingService) {
            this.routingService = routingService;
        }

        public Add() {
            this.routingService = null;
        }

        @Override
        public void run() {
            if (routingService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                Alias created = routingService.addAlias(alias, targets);
                System.out.println("Alias added successfully:");
                System.out.println("  ID: " + created.id());
                System.out.println("  Alias: " + created.alias());
                System.out.println("  Targets: " + created.targets());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "remove", description = "Remove an alias")
    public static class Remove implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Alias address")
        String alias;

        @CommandLine.Option(names = {"--force"}, description = "Force removal without confirmation")
        boolean force;

        private final RoutingService routingService;

        public Remove(RoutingService routingService) {
            this.routingService = routingService;
        }

        public Remove() {
            this.routingService = null;
        }

        @Override
        public void run() {
            if (routingService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            if (!force) {
                System.out.print("Are you sure you want to remove alias '" + alias + "'? (y/N): ");
                String confirm = System.console() != null ? System.console().readLine() : new java.util.Scanner(System.in).nextLine();
                if (!"y".equalsIgnoreCase(confirm)) {
                    System.out.println("Cancelled.");
                    return;
                }
            }
            
            try {
                routingService.removeAliasByAddress(alias);
                System.out.println("Alias removed: " + alias);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "list", description = "List aliases")
    public static class ListCmd implements Runnable {
        @CommandLine.Parameters(index = "0", arity = "0..1", description = "Domain (optional)")
        String domain;

        @CommandLine.Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        private final RoutingService routingService;

        public ListCmd() {
            this.routingService = ServiceRegistry.getInstance().getRoutingService();
        }

        public ListCmd(RoutingService routingService) {
            this.routingService = routingService;
        }

        @Override
        public void run() {
            if (routingService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                java.util.List<Alias> aliases;
                if (domain != null) {
                    aliases = routingService.listAllAliases().stream()
                        .filter(a -> a.alias().endsWith("@" + domain))
                        .toList();
                } else {
                    aliases = routingService.listAllAliases();
                }
                
                if (json) {
                    System.out.print("[");
                    for (int i = 0; i < aliases.size(); i++) {
                        Alias a = aliases.get(i);
                        System.out.print(String.format("{\"id\":\"%s\",\"alias\":\"%s\",\"targets\":\"%s\"}", 
                            a.id(), a.alias(), a.targets()));
                        if (i < aliases.size() - 1) System.out.print(",");
                    }
                    System.out.println("]");
                } else {
                    if (aliases.isEmpty()) {
                        System.out.println("No aliases found.");
                    } else {
                        System.out.println("Aliases:");
                        for (Alias a : aliases) {
                            System.out.printf("  %s -> %s%n", a.alias(), a.targets());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}