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
        AliasCommand.ListCmd.class,
        AliasCommand.Passwd.class,
        AliasCommand.Enable.class,
        AliasCommand.Disable.class
    }
)
    /**
     * Alias management commands: {@code alias add|remove|list|passwd|enable|disable}.
     *
     * <p>An alias is a mail principal. By default it owns a mailbox, so mail
     * addressed to it is <em>collected</em> into that mailbox and a copy is
     * <em>forwarded</em> to its targets. With a password it can also
     * authenticate over IMAP, POP3 and SMTP submission, which is what lets
     * it send outbound mail as itself.</p>
     *
     * <p>{@code --forward-only} creates the classic routing-only alias: it
     * forwards everything and stores nothing, and it cannot log in anywhere.</p>
     */
public class AliasCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Usage: mailctl alias [add|remove|list|passwd|enable|disable]");
    }

    @CommandLine.Command(name = "add", description = "Add a new alias")
    public static class Add implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Alias address (e.g., support@example.com)")
        String alias;

        @CommandLine.Parameters(index = "1", arity = "0..1", description = "Target address(es), comma-separated (e.g., alice@example.com or alice@example.com,bob@example.com)")
        String targets;

        @CommandLine.Option(names = {"--password"}, description = "Alias password (prompt if omitted and a mailbox is created)")
        String password;

        @CommandLine.Option(names = {"--quota"}, description = "Mailbox quota (e.g., 1G, 500M)", defaultValue = "1G")
        String quota;

        @CommandLine.Option(names = {"--forward-only"}, description = "Create a routing-only alias: forwards mail, stores nothing, cannot log in")
        boolean forwardOnly;

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
                // A mailbox alias is a principal that can log in, so it needs
                // a password. A routing-only alias never authenticates, so it
                // does not prompt for one.
                if (!forwardOnly && password == null) {
                    System.out.print("Enter alias password: ");
                    password = System.console() != null
                            ? new String(System.console().readPassword())
                            : new java.util.Scanner(System.in).nextLine();
                }

                Alias created = routingService.addAlias(alias, targets == null ? "" : targets,
                        password, quota, !forwardOnly);
                System.out.println("Alias added successfully:");
                System.out.println("  ID: " + created.id());
                System.out.println("  Alias: " + created.alias());
                System.out.println("  Targets: " + (created.targets().isEmpty() ? "(none)" : created.targets()));
                if (created.hasMailbox()) {
                    System.out.println("  Mailbox: " + created.mailboxPath());
                    System.out.println("  Quota: " + created.quota());
                } else {
                    System.out.println("  Mailbox: (none - routing-only)");
                }
                System.out.println("  Status: " + created.status());
                if (created.canAuthenticate()) {
                    System.out.println("  Authentication: enabled (IMAP, POP3, SMTP submission)");
                } else {
                    System.out.println("  Authentication: disabled (set one with: mailctl alias passwd " + created.alias() + ")");
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "passwd", description = "Set or replace the alias password (enables IMAP/POP3/SMTP login)")
    public static class Passwd implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Alias address")
        String alias;

        @CommandLine.Option(names = {"--password"}, description = "New password (prompt if omitted)")
        String password;

        private final RoutingService routingService;

        public Passwd(RoutingService routingService) {
            this.routingService = routingService;
        }

        public Passwd() {
            this.routingService = null;
        }

        @Override
        public void run() {
            if (routingService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }

            if (password == null) {
                System.out.print("Enter password: ");
                password = System.console() != null
                        ? new String(System.console().readPassword())
                        : new java.util.Scanner(System.in).nextLine();
            }

            try {
                Alias updated = routingService.setAliasPassword(alias, password);
                System.out.println("Password updated: " + updated.alias());
                System.out.println("  The alias can now authenticate for IMAP, POP3 and SMTP submission.");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "enable", description = "Enable an alias")
    public static class Enable implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Alias address")
        String alias;

        private final RoutingService routingService;

        public Enable(RoutingService routingService) {
            this.routingService = routingService;
        }

        public Enable() {
            this.routingService = null;
        }

        @Override
        public void run() {
            if (routingService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            try {
                Alias updated = routingService.enableAlias(alias);
                System.out.println("Alias enabled: " + updated.alias());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "disable", description = "Disable an alias")
    public static class Disable implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Alias address")
        String alias;

        private final RoutingService routingService;

        public Disable(RoutingService routingService) {
            this.routingService = routingService;
        }

        public Disable() {
            this.routingService = null;
        }

        @Override
        public void run() {
            if (routingService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            try {
                Alias updated = routingService.disableAlias(alias);
                System.out.println("Alias disabled: " + updated.alias());
                System.out.println("  Mail delivery and login for this alias stop immediately.");
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
                        System.out.print(String.format("{\"id\":\"%s\",\"alias\":\"%s\",\"targets\":\"%s\",\"mailbox\":\"%s\",\"quota\":\"%s\",\"status\":\"%s\",\"canAuthenticate\":%s}",
                            a.id(), a.alias(), a.targets(), a.mailboxPath(), a.quota(), a.status(), a.canAuthenticate()));
                        if (i < aliases.size() - 1) System.out.print(",");
                    }
                    System.out.println("]");
                } else {
                    if (aliases.isEmpty()) {
                        System.out.println("No aliases found.");
                    } else {
                        System.out.println("Aliases:");
                        for (Alias a : aliases) {
                            System.out.printf("  %s -> %s%n", a.alias(),
                                    a.targets().isEmpty() ? "(no targets)" : a.targets());
                            System.out.printf("      mailbox: %s | quota: %s | status: %s | login: %s%n",
                                    a.hasMailbox() ? a.mailboxPath() : "(none, routing-only)",
                                    a.quota(), a.status(), a.canAuthenticate() ? "yes" : "no");
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}
