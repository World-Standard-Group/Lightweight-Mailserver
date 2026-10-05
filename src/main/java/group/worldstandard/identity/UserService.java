package group.worldstandard.identity;

import group.worldstandard.domain.Domain;
import group.worldstandard.domain.DomainRepository;
import group.worldstandard.security.PasswordHasher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Business rules for mailboxes.
 *
 * <p>Enforces that a user's domain part matches an existing domain, derives the
 * mailbox path from the address, creates the maildir on disk, and delegates
 * password storage to {@link PasswordHasher}.
 *
 * <p>Status changes ({@link #disableUser(UUID)} / {@link #enableUser(UUID)}) flip
 * {@code status} rather than deleting the row, because the generated Dovecot
 * SQL queries filter on {@code status = 'ACTIVE'} — a disabled user loses access
 * immediately with no mailbox deletion and no credential rotation.
 *
 * <p><b>Note:</b> password hashing is not production-ready; see
 * {@link PasswordHasher}.</p>
 */
public class UserService {
    private final UserRepository userRepository;
    private final DomainRepository domainRepository;
    private final PasswordHasher passwordHasher;
    private final String mailBase;

    public UserService(UserRepository userRepository, DomainRepository domainRepository, PasswordHasher passwordHasher) {
        this(userRepository, domainRepository, passwordHasher, User.DEFAULT_MAIL_BASE);
    }

    /**
     * @param mailBase maildir root; must match {@code paths.mailBase} so mailbox
     *                 paths and the generated Dovecot {@code mail_location} agree
     */
    public UserService(UserRepository userRepository, DomainRepository domainRepository,
                       PasswordHasher passwordHasher, String mailBase) {
        this.userRepository = userRepository;
        this.domainRepository = domainRepository;
        this.passwordHasher = passwordHasher;
        this.mailBase = (mailBase == null || mailBase.isBlank())
                ? User.DEFAULT_MAIL_BASE : mailBase;
    }

    /** Creates a user with a mailbox. */
    public User addUser(String email, String password, String quota) {
        return createUser(email, password, quota, true, null);
    }

    /**
     * Creates a forwarding-only user: no mailbox is created, mail addressed to it
     * is rewritten to {@code forwardTargets}, and it cannot read mail over IMAP.
     * It can still authenticate for outbound SMTP submission.
     */
    public User addForwardingUser(String email, String password, String forwardTargets) {
        if (forwardTargets == null || forwardTargets.isBlank()) {
            throw new IllegalArgumentException(
                    "A forwarding-only user needs at least one target address");
        }
        return createUser(email, password, "0", false, normalizeTargets(forwardTargets));
    }

    private User createUser(String email, String password, String quota,
                            boolean storageEnabled, String forwardTargets) {
        String[] parts = email.split("@");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }

        String domainName = parts[1].toLowerCase();
        Domain domain = domainRepository.findByName(domainName)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainName));

        if (domain.status() != Domain.Status.ACTIVE) {
            throw new IllegalArgumentException("Domain is not active: " + domainName);
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("User already exists: " + email);
        }

        if (forwardTargets != null) {
            // Forwarding sends mail to a third party, so targets are external by
            // definition. Validate the shape here; whether the mailbox exists is
            // the receiving server's problem.
            for (String raw : forwardTargets.split(",")) {
                String target = raw.trim();
                if (target.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Empty forwarding target in: " + forwardTargets);
                }
                String[] tp = target.split("@");
                if (tp.length != 2 || tp[0].isEmpty() || tp[1].isEmpty()) {
                    throw new IllegalArgumentException("Invalid forwarding target: " + target);
                }
            }
        }

        String passwordHash = passwordHasher.hash(password);
        User user = User.create(domain.id(), email, passwordHash, quota,
                storageEnabled, forwardTargets, mailBase);

        // Only storage users get a maildir. Creating one for a forwarding-only
        // user would defeat the point: mail would land on disk.
        if (storageEnabled) {
            createMaildir(user.mailboxPath());
        }

        return userRepository.save(user);
    }

    /**
     * Normalises a comma-separated target list: trimmed, lowercased, re-joined.
     */
    static String normalizeTargets(String targets) {
        StringBuilder sb = new StringBuilder();
        for (String raw : targets.split(",")) {
            String target = raw.trim().toLowerCase();
            if (target.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(target);
        }
        if (sb.isEmpty()) {
            throw new IllegalArgumentException("No valid forwarding targets in: " + targets);
        }
        return sb.toString();
    }

    /**
     * Replaces a user's forwarding targets, making them forwarding-only.
     *
     * @return the updated user
     */
    public User setForwardTargets(String email, String targets) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        String normalized = normalizeTargets(targets);
        User updated = new User(user.id(), user.domainId(), user.email(), user.passwordHash(),
                user.mailboxPath(), user.quota(), user.status(), user.createdAt(),
                java.time.Instant.now(), user.lastLoginAt(), false, normalized);
        return userRepository.save(updated);
    }

    /**
     * Converts a forwarding-only user into a normal mailbox user, creating the
     * maildir. Stored forwarding targets are cleared.
     */
    public User convertToMailbox(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        if (user.storageEnabled()) {
            return user;
        }
        String mailboxPath = mailBase + "/" + user.email().split("@")[1] + "/"
                + user.email().split("@")[0];
        createMaildir(mailboxPath);
        return userRepository.save(user.asStorageUser(mailboxPath));
    }

    /**
     * Converts a mailbox user into a forwarding-only user, removing its maildir.
     * Callers must confirm first: this deletes stored mail.
     */
    public User convertToForwarding(String email, String targets) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        if (!user.storageEnabled()) {
            return setForwardTargets(email, targets);
        }
        if (user.mailboxPath() != null && !user.mailboxPath().isEmpty()) {
            removeMaildir(user.mailboxPath());
        }
        return userRepository.save(user.asForwardingOnly(normalizeTargets(targets)));
    }

    public void removeUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        
        // Remove maildir. A forwarding-only user has an empty mailbox_path,
        // so there is nothing on disk to remove.
        if (user.mailboxPath() != null && !user.mailboxPath().isEmpty()) {
            removeMaildir(user.mailboxPath());
        }
        
        userRepository.deleteById(id);
    }

    public User disableUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        User updated = user.withStatus(User.Status.DISABLED);
        return userRepository.save(updated);
    }

    public User enableUser(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        User updated = user.withStatus(User.Status.ACTIVE);
        return userRepository.save(updated);
    }

    public User changePassword(UUID id, String newPassword) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        String passwordHash = passwordHasher.hash(newPassword);
        User updated = user.withPasswordHash(passwordHash);
        return userRepository.save(updated);
    }

    public User changePasswordByEmail(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        String passwordHash = passwordHasher.hash(newPassword);
        User updated = user.withPasswordHash(passwordHash);
        return userRepository.save(updated);
    }

    public Optional<User> getUser(UUID id) {
        return userRepository.findById(id);
    }

    public Optional<User> getUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public List<User> listUsers(UUID domainId) {
        return userRepository.findByDomainId(domainId);
    }

    public List<User> listActiveUsers(UUID domainId) {
        return userRepository.findByDomainIdAndStatus(domainId, User.Status.ACTIVE);
    }

    public List<User> listAllUsers() {
        return userRepository.findAll();
    }

    public User recordLogin(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        User updated = user.withLastLogin();
        return userRepository.save(updated);
    }
    
    public UserRepository getUserRepository() {
        return userRepository;
    }

    /** Users with a real mailbox, scoped to one domain (or all when null). */
    public List<User> listStorageUsers(UUID domainId) {
        return userRepository.findAll().stream()
                .filter(User::storageEnabled)
                .filter(u -> domainId == null || u.domainId().equals(domainId))
                .toList();
    }

    /** Forwarding-only users that have at least one target, optionally per domain. */
    public List<User> listForwardingUsers(UUID domainId) {
        return userRepository.findAll().stream()
                .filter(User::isForwardingOnly)
                .filter(u -> u.forwardTargets() != null && !u.forwardTargets().isBlank())
                .filter(u -> domainId == null || u.domainId().equals(domainId))
                .toList();
    }

    /** Parses the comma-separated targets of a forwarding-only user. */
    public static List<String> targetsOf(User user) {
        if (user.forwardTargets() == null || user.forwardTargets().isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(user.forwardTargets().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private void createMaildir(String path) {
        try {
            Path maildir = Paths.get(path);
            Files.createDirectories(maildir.resolve("new"));
            Files.createDirectories(maildir.resolve("cur"));
            Files.createDirectories(maildir.resolve("tmp"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create maildir: " + path, e);
        }
    }

    private void removeMaildir(String path) {
        try {
            Path maildir = Paths.get(path);
            if (Files.exists(maildir)) {
                Files.walk(maildir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to delete: " + p, e);
                        }
                    });
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to remove maildir: " + path, e);
        }
    }
}