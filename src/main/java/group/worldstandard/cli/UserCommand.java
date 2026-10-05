package group.worldstandard.cli;

import group.worldstandard.ServiceRegistry;
import group.worldstandard.identity.User;
import group.worldstandard.identity.UserService;
import picocli.CommandLine;

@CommandLine.Command(
    name = "user",
    description = "User management",
    subcommands = {
        UserCommand.Add.class,
        UserCommand.Remove.class,
        UserCommand.Disable.class,
        UserCommand.Enable.class,
        UserCommand.Passwd.class,
        UserCommand.ListCmd.class,
        UserCommand.AddForwarding.class,
        UserCommand.SetForward.class,
        UserCommand.ToMailbox.class,
        UserCommand.ToForwarding.class,
        UserCommand.ForwardingList.class
    }
)
    /**
     * User (mailbox) management: {@code user add|remove|disable|enable|passwd|list}.
     *  *
     *  * <p>Disabled users are refused at authentication time rather than deleted,
     *  * because the generated Dovecot {@code password_query} and {@code user_query}
     *  * both filter on {@code status = 'ACTIVE'}.</p>
     *  *
     *  * <p><b>Note:</b> password storage is not production-ready; see
     *  * {@link group.worldstandard.security.PasswordHasher}.</p>
     */
public class UserCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Usage: mailctl user [add|remove|disable|enable|passwd|list]");
    }

    @CommandLine.Command(name = "add", description = "Add a new user")
    public static class Add implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Email address (e.g., alice@example.com)")
        String email;

        @CommandLine.Option(names = {"--password"}, description = "Initial password (prompt if omitted)")
        String password;

        @CommandLine.Option(names = {"--quota"}, description = "Mailbox quota (e.g., 1G, 500M)", defaultValue = "1G")
        String quota;

        private final UserService userService;

        public Add(UserService userService) {
            this.userService = userService;
        }

        public Add() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            if (password == null) {
                System.out.print("Enter password: ");
                password = System.console() != null ? new String(System.console().readPassword()) : new java.util.Scanner(System.in).nextLine();
            }
            
            try {
                User created = userService.addUser(email, password, quota);
                System.out.println("User added successfully:");
                System.out.println("  ID: " + created.id());
                System.out.println("  Email: " + created.email());
                System.out.println("  Mailbox: " + created.mailboxPath());
                System.out.println("  Quota: " + created.quota());
                System.out.println("  Status: " + created.status());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "remove", description = "Remove a user")
    public static class Remove implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Email address")
        String email;

        @CommandLine.Option(names = {"--force"}, description = "Force removal without confirmation")
        boolean force;

        private final UserService userService;

        public Remove(UserService userService) {
            this.userService = userService;
        }

        public Remove() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            if (!force) {
                System.out.print("Are you sure you want to remove user '" + email + "'? (y/N): ");
                String confirm = System.console() != null ? System.console().readLine() : new java.util.Scanner(System.in).nextLine();
                if (!"y".equalsIgnoreCase(confirm)) {
                    System.out.println("Cancelled.");
                    return;
                }
            }
            
            try {
                var userOpt = userService.getUserByEmail(email);
                if (userOpt.isEmpty()) {
                    System.err.println("User not found: " + email);
                    return;
                }
                userService.removeUser(userOpt.get().id());
                System.out.println("User removed: " + email);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "disable", description = "Disable a user")
    public static class Disable implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Email address")
        String email;

        private final UserService userService;

        public Disable(UserService userService) {
            this.userService = userService;
        }

        public Disable() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                var userOpt = userService.getUserByEmail(email);
                if (userOpt.isEmpty()) {
                    System.err.println("User not found: " + email);
                    return;
                }
                User disabled = userService.disableUser(userOpt.get().id());
                System.out.println("User disabled: " + disabled.email());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "enable", description = "Enable a user")
    public static class Enable implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Email address")
        String email;

        private final UserService userService;

        public Enable(UserService userService) {
            this.userService = userService;
        }

        public Enable() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                var userOpt = userService.getUserByEmail(email);
                if (userOpt.isEmpty()) {
                    System.err.println("User not found: " + email);
                    return;
                }
                User enabled = userService.enableUser(userOpt.get().id());
                System.out.println("User enabled: " + enabled.email());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "passwd", description = "Change user password")
    public static class Passwd implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Email address")
        String email;

        @CommandLine.Option(names = {"--password"}, description = "New password (prompt if omitted)")
        String password;

        private final UserService userService;

        public Passwd(UserService userService) {
            this.userService = userService;
        }

        public Passwd() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            if (password == null) {
                System.out.print("Enter new password: ");
                password = System.console() != null ? new String(System.console().readPassword()) : new java.util.Scanner(System.in).nextLine();
            }
            
            try {
                userService.changePasswordByEmail(email, password);
                System.out.println("Password changed for: " + email);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "list", description = "List users")
    public static class ListCmd implements Runnable {
        @CommandLine.Parameters(index = "0", arity = "0..1", description = "Domain (optional)")
        String domain;

        @CommandLine.Option(names = {"--json"}, description = "Output as JSON")
        boolean json;

        private final UserService userService;

        public ListCmd() {
            this.userService = ServiceRegistry.getInstance().getUserService();
        }

        public ListCmd(UserService userService) {
            this.userService = userService;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                java.util.List<User> users;
                if (domain != null) {
                    users = userService.listAllUsers().stream()
                        .filter(u -> u.email().endsWith("@" + domain))
                        .toList();
                } else {
                    users = userService.listAllUsers();
                }
                
                if (json) {
                    System.out.print("[");
                    for (int i = 0; i < users.size(); i++) {
                        User u = users.get(i);
                        System.out.printf("{\"id\":\"%s\",\"email\":\"%s\",\"status\":\"%s\"}",
                            u.id(), u.email(), u.status());
                        if (i < users.size() - 1) System.out.print(",");
                    }
                    System.out.println("]");
                } else {
                    if (users.isEmpty()) {
                        System.out.println("No users found.");
                    } else {
                        System.out.println("Users:");
                        for (User u : users) {
                            System.out.printf("  %s (%s) - %s - Quota: %s%n", 
                                u.email(), u.id(), u.status(), u.quota());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "add-forwarding",
        description = "Add a forwarding-only user (no mailbox, mail is forwarded)")
    public static class AddForwarding implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Email address (e.g., zazalng@example.com)")
        String email;

        @CommandLine.Option(names = {"--password"}, description = "Password used to send mail (prompt if omitted)")
        String password;

        @CommandLine.Option(names = {"--forward-to"}, required = true, split = ",",
            description = "External address(es), comma-separated (e.g., a@gmail.com,b@outlook.com)")
        java.util.List<String> forwardTo;

        private final UserService userService;

        public AddForwarding(UserService userService) {
            this.userService = userService;
        }

        public AddForwarding() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
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
                User created = userService.addForwardingUser(email, password, String.join(",", forwardTo));
                System.out.println("Forwarding-only user created:");
                System.out.println("  Email:  " + created.email());
                System.out.println("  Forwards to: " + created.forwardTargets());
                System.out.println("  Storage: none (no mailbox is created)");
                System.out.println("  The user can send via SMTP submission on port 587.");
                System.out.println();
                System.out.println("Run 'mailctl config generate' then 'bash scripts/apply-config.sh' to apply.");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "set-forward", description = "Replace a forwarding-only user's targets")
    public static class SetForward implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Email address")
        String email;

        @CommandLine.Option(names = {"--forward-to"}, required = true, split = ",",
            description = "External address(es), comma-separated")
        java.util.List<String> forwardTo;

        private final UserService userService;

        public SetForward(UserService userService) {
            this.userService = userService;
        }

        public SetForward() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            try {
                User updated = userService.setForwardTargets(email, String.join(",", forwardTo));
                System.out.println("Forwarding targets updated for " + updated.email());
                System.out.println("  Now forwards to: " + updated.forwardTargets());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "to-mailbox",
        description = "Convert a forwarding-only user into a normal mailbox user")
    public static class ToMailbox implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Email address")
        String email;

        @CommandLine.Option(names = {"--quota"}, description = "Mailbox quota", defaultValue = "1G")
        String quota;

        private final UserService userService;

        public ToMailbox(UserService userService) {
            this.userService = userService;
        }

        public ToMailbox() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            try {
                User updated = userService.convertToMailbox(email);
                System.out.println("Converted to a mailbox user: " + updated.email());
                System.out.println("  Mailbox: " + updated.mailboxPath());
                System.out.println("  Forwarding targets cleared.");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "to-forwarding",
        description = "Convert a mailbox user into forwarding-only (DELETES stored mail)")
    public static class ToForwarding implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Email address")
        String email;

        @CommandLine.Option(names = {"--forward-to"}, required = true, split = ",",
            description = "External address(es), comma-separated")
        java.util.List<String> forwardTo;

        @CommandLine.Option(names = {"--force"}, description = "Skip the destructive-action confirmation")
        boolean force;

        private final UserService userService;

        public ToForwarding(UserService userService) {
            this.userService = userService;
        }

        public ToForwarding() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            if (!force) {
                System.out.println("WARNING: this deletes the user's maildir and all stored mail.");
                System.out.print("Continue for '" + email + "'? (y/N): ");
                String confirm = System.console() != null
                        ? System.console().readLine()
                        : new java.util.Scanner(System.in).nextLine();
                if (!"y".equalsIgnoreCase(confirm)) {
                    System.out.println("Cancelled.");
                    return;
                }
            }
            try {
                User updated = userService.convertToForwarding(email, String.join(",", forwardTo));
                System.out.println("Converted to forwarding-only: " + updated.email());
                System.out.println("  Now forwards to: " + updated.forwardTargets());
                System.out.println("  Stored mail was removed.");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "forwarding-list", description = "List forwarding-only users and their targets")
    public static class ForwardingList implements Runnable {
        @CommandLine.Parameters(index = "0", arity = "0..1", description = "Domain (optional)")
        String domain;

        private final UserService userService;

        public ForwardingList(UserService userService) {
            this.userService = userService;
        }

        public ForwardingList() {
            this.userService = null;
        }

        @Override
        public void run() {
            if (userService == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            try {
                java.util.UUID domainId = null;
                if (domain != null) {
                    var repo = userService.getUserRepository();
                    for (User u : repo.findAll()) {
                        if (u.email().endsWith("@" + domain)) {
                            domainId = u.domainId();
                            break;
                        }
                    }
                }
                var users = userService.listForwardingUsers(domainId);
                if (users.isEmpty()) {
                    System.out.println("No forwarding-only users found.");
                    return;
                }
                System.out.println("Forwarding-only users:");
                for (User u : users) {
                    System.out.printf("  %s -> %s%n", u.email(),
                            String.join(", ", UserService.targetsOf(u)));
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}
