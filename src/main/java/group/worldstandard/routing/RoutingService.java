package group.worldstandard.routing;

import group.worldstandard.domain.Domain;
import group.worldstandard.domain.DomainRepository;
import group.worldstandard.mail.MailOwner;
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
     * Business rules for aliases, including owner-domain validation and
     * mailbox provisioning.
     *
     * <p>An alias is provisioned as a real mail principal: it gets a mailbox
     * on disk, a hashed password, a quota and a status. Mail addressed to it
     * is collected into that mailbox <em>and</em> forwarded to its targets;
     * with a password it can also authenticate over IMAP, POP3 and SMTP
     * submission.</p>
     *
     * <p>Forwarding targets may be external addresses. They are validated for
     * shape only - whether the target mailbox exists is the receiving
     * server's problem - exactly as for forwarding-only users. Note that a
     * forwarded copy keeps the original envelope sender, so the receiving
     * server's SPF and DMARC evaluation applies to the original sender, not
     * to this server.</p>
     */
public class RoutingService {
    private final AliasRepository aliasRepository;
    private final DomainRepository domainRepository;
    private final PasswordHasher passwordHasher;
    private final String mailBase;

    public RoutingService(AliasRepository aliasRepository, DomainRepository domainRepository) {
        this(aliasRepository, domainRepository, new PasswordHasher(), Alias.DEFAULT_MAIL_BASE);
    }

    /**
     * @param mailBase maildir root; must match {@code paths.mailBase} so
     *                 mailbox paths and the generated Dovecot
     *                 {@code mail_location} agree
     */
    public RoutingService(AliasRepository aliasRepository, DomainRepository domainRepository,
                          PasswordHasher passwordHasher, String mailBase) {
        this.aliasRepository = aliasRepository;
        this.domainRepository = domainRepository;
        this.passwordHasher = passwordHasher;
        this.mailBase = (mailBase == null || mailBase.isBlank())
                ? Alias.DEFAULT_MAIL_BASE : mailBase;
    }

    /**
     * Creates an alias with a mailbox but no credentials. The alias collects
     * mail and forwards it, but cannot log in anywhere.
     */
    public Alias addAlias(String alias, String targets) {
        return addAlias(alias, targets, null, "1G");
    }

    /**
     * Creates an alias that collects mail into a mailbox of its own and
     * forwards a copy to its targets.
     *
     * @param password the alias password; when null or blank the alias has no
     *                 credentials and cannot authenticate
     * @param quota    the mailbox storage limit, for example {@code 1G}
     */
    public Alias addAlias(String alias, String targets, String password, String quota) {
        return addAlias(alias, targets, password, quota, true);
    }

    /**
     * Creates an alias.
     *
     * @param withMailbox true for an alias that owns a mailbox (it collects
     *                    a copy of its own mail and forwards a copy);
     *                    false for a routing-only alias, which forwards
     *                    everything and stores nothing
     */
    public Alias addAlias(String alias, String targets, String password, String quota,
                          boolean withMailbox) {
        String[] aliasParts = alias.split("@");
        if (aliasParts.length != 2 || aliasParts[0].isEmpty() || aliasParts[1].isEmpty()) {
            throw new IllegalArgumentException("Invalid alias format: " + alias);
        }

        String domainName = aliasParts[1].toLowerCase();
        Domain domain = domainRepository.findByName(domainName)
                .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainName));

        if (domain.status() != Domain.Status.ACTIVE) {
            throw new IllegalArgumentException("Domain is not active: " + domainName);
        }

        if (aliasRepository.existsByAlias(alias)) {
            throw new IllegalArgumentException("Alias already exists: " + alias);
        }

        String normalizedTargets = normalizeTargets(targets);
        String passwordHash = (password == null || password.isEmpty())
                ? "" : passwordHasher.hash(password);

        Alias newAlias;
        if (withMailbox) {
            newAlias = Alias.create(domain.id(), alias, normalizedTargets,
                    passwordHash, (quota == null || quota.isBlank()) ? "1G" : quota, mailBase);
            createMaildir(newAlias.mailboxPath());
        } else {
            newAlias = Alias.createRoutingOnly(domain.id(), alias, normalizedTargets);
        }
        return aliasRepository.save(newAlias);
    }

    /**
     * Normalises a comma-separated target list: trimmed, lowercased, re-joined.
     * An empty list is allowed - it means the alias only collects mail.
     */
    static String normalizeTargets(String targets) {
        if (targets == null || targets.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String raw : targets.split(",")) {
            String target = raw.trim().toLowerCase();
            if (target.isEmpty()) {
                continue;
            }
            String[] parts = target.split("@");
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new IllegalArgumentException("Invalid forwarding target: " + target);
            }
            if (!sb.isEmpty()) {
                sb.append(',');
            }
            sb.append(target);
        }
        return sb.toString();
    }

    /**
     * Sets or replaces the alias password, which is what makes the alias able
     * to authenticate for IMAP, POP3 and SMTP submission.
     */
    public Alias setAliasPassword(String alias, String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        Alias existing = aliasRepository.findByAlias(alias)
                .orElseThrow(() -> new IllegalArgumentException("Alias not found: " + alias));
        String passwordHash = passwordHasher.hash(password);
        return aliasRepository.save(existing.withPasswordHash(passwordHash));
    }

    /** Grants the alias a quota limit. */
    public Alias setAliasQuota(String alias, String quota) {
        Alias existing = aliasRepository.findByAlias(alias)
                .orElseThrow(() -> new IllegalArgumentException("Alias not found: " + alias));
        return aliasRepository.save(existing.withQuota(quota));
    }

    /** Disables the alias: Dovecot's SQL filters on status, so access stops. */
    public Alias disableAlias(UUID id) {
        Alias alias = aliasRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alias not found: " + id));
        return aliasRepository.save(alias.withStatus(Alias.Status.DISABLED));
    }

    /** Re-enables a disabled alias. */
    public Alias enableAlias(UUID id) {
        Alias alias = aliasRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alias not found: " + id));
        return aliasRepository.save(alias.withStatus(Alias.Status.ACTIVE));
    }

    /** Disables the alias addressed by name. */
    public Alias disableAlias(String alias) {
        Alias existing = aliasRepository.findByAlias(alias)
                .orElseThrow(() -> new IllegalArgumentException("Alias not found: " + alias));
        return aliasRepository.save(existing.withStatus(Alias.Status.DISABLED));
    }

    /** Re-enables the alias addressed by name. */
    public Alias enableAlias(String alias) {
        Alias existing = aliasRepository.findByAlias(alias)
                .orElseThrow(() -> new IllegalArgumentException("Alias not found: " + alias));
        return aliasRepository.save(existing.withStatus(Alias.Status.ACTIVE));
    }

    public void removeAlias(UUID id) {
        Alias alias = aliasRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alias not found: " + id));
        if (alias.hasMailbox()) {
            removeMaildir(alias.mailboxPath());
        }
        aliasRepository.deleteById(id);
    }

    public void removeAliasByAddress(String alias) {
        aliasRepository.findByAlias(alias).ifPresent(a -> removeAlias(a.id()));
    }

    public Optional<Alias> getAlias(UUID id) {
        return aliasRepository.findById(id);
    }

    public Optional<Alias> getAliasByAddress(String alias) {
        return aliasRepository.findByAlias(alias);
    }

    public List<Alias> listAliases(UUID domainId) {
        return aliasRepository.findByDomainId(domainId);
    }

    public List<Alias> listAllAliases() {
        return aliasRepository.findAll();
    }

    public Alias updateAliasTargets(UUID id, String newTargets) {
        Alias alias = aliasRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Alias not found: " + id));
        Alias updated = alias.withTargets(normalizeTargets(newTargets));
        return aliasRepository.save(updated);
    }

    public String resolveAlias(String alias) {
        return aliasRepository.findByAlias(alias)
                .map(Alias::targets)
                .orElse(null);
    }

    /**
     * Creates the maildir the alias collects mail into.
     *
     * <p>The directories are created owner-only and then given to the mailbox
     * owner (uid 8, gid 8): the Postfix delivery agent writes there as that
     * uid and Dovecot reads it as the same uid, and both containers see the
     * same numeric ids on the shared volume. The chown is best effort -
     * without POSIX ownership it is skipped rather than fatal.</p>
     */
    private void createMaildir(String path) {
        try {
            Path maildir = Paths.get(path);
            Files.createDirectories(maildir.resolve("new"));
            Files.createDirectories(maildir.resolve("cur"));
            Files.createDirectories(maildir.resolve("tmp"));
            MailOwner.applyOwnerOnlyPermissions(maildir);
            MailOwner.applyOwnerOnlyPermissions(maildir.resolve("new"));
            MailOwner.applyOwnerOnlyPermissions(maildir.resolve("cur"));
            MailOwner.applyOwnerOnlyPermissions(maildir.resolve("tmp"));
            if (!MailOwner.applyOwnership(maildir)) {
                // Not fatal: on a filesystem without POSIX ownership the
                // maildir still exists and a server running as the same user
                // can use it.
                MailOwner.applyOwnerOnlyPermissions(maildir);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create alias maildir: " + path, e);
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
            throw new RuntimeException("Failed to remove alias maildir: " + path, e);
        }
    }
}
