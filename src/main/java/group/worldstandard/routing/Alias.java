package group.worldstandard.routing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

    /**
     * An address that receives mail, collects it into a mailbox, forwards a
     * copy to one or more targets, and - once it has credentials - can send
     * outbound mail as itself.
     *  *
     *  * <p>An alias is a <b>mail principal</b>, not just a routing entry. To do
     *  * the things a real alias must do it needs the same identity data a
     *  * user has:</p>
     *  * <ul>
     *  *   <li>{@code passwordHash} - authenticate for IMAP, POP3 and SMTP
     *  *       submission. Without it the alias cannot log in anywhere.</li>
     *  *   <li>{@code mailboxPath} - the maildir the alias collects its mail
     *  *       into. Empty means routing-only: no mailbox is claimed and the
     *  *       address never appears in Postfix's
     *  *       {@code virtual_mailbox_maps}.</li>
     *  *   <li>{@code quota} - the per-mailbox storage limit.</li>
     *  *   <li>{@code status} - ACTIVE or DISABLED, so access can be revoked
     *  *       without deleting the row.</li>
     *  * </ul>
     *  *
     *  * <p>An alias with a mailbox is rendered into Postfix's
     *  * {@code virtual_alias_maps} as {@code <alias>, <targets>} - it retains
     *  * its own address in the expansion. That is what makes the mailbox copy
     *  * work: Postfix replaces a recipient with the map result, so an alias
     *  * that expanded to only its targets would never reach the virtual
     *  * transport at all. The targets are additional recipients, so the alias
     *  * both collects the mail and forwards a copy.</p>
     *  *
     *  * <p>An alias without a mailbox (a legacy routing-only alias) is
     *  * rendered as {@code <targets>} alone, which is the classic alias
     *  * rewrite: the mail is forwarded and nothing is kept.</p>
     */
public record Alias(
    @JsonProperty("id") UUID id,
    @JsonProperty("domainId") UUID domainId,
    @JsonProperty("alias") String alias,
    @JsonProperty("targets") String targets,
    @JsonProperty("passwordHash") String passwordHash,
    @JsonProperty("mailboxPath") String mailboxPath,
    @JsonProperty("quota") String quota,
    @JsonProperty("status") Status status,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("updatedAt") Instant updatedAt
) {
    public enum Status {
        ACTIVE,
        DISABLED
    }

    /** Default maildir root, used when no base is supplied. */
    public static final String DEFAULT_MAIL_BASE = "/var/mail";

    /**
     * Creates a routing-only alias: it forwards mail to its targets but has
     * no mailbox and cannot authenticate.
     */
    public static Alias createRoutingOnly(UUID domainId, String alias, String targets) {
        return new Alias(
            UUID.randomUUID(),
            domainId,
            alias.toLowerCase(),
            targets.toLowerCase(),
            "",
            "",
            "1G",
            Status.ACTIVE,
            Instant.now(),
            Instant.now()
        );
    }

    /**
     * Creates an alias with full principal data.
     *
     * @param mailBase maildir root; must match {@code paths.mailBase} so
     *                 mailbox paths and the generated Dovecot
     *                 {@code mail_location} agree
     */
    public static Alias create(UUID domainId, String alias, String targets,
                               String passwordHash, String quota, String mailBase) {
        String[] parts = alias.split("@");
        String localPart = parts[0];
        String domainPart = parts[1];
        String base = (mailBase == null || mailBase.isBlank()) ? DEFAULT_MAIL_BASE : mailBase;

        return new Alias(
            UUID.randomUUID(),
            domainId,
            alias.toLowerCase(),
            targets.toLowerCase(),
            passwordHash == null ? "" : passwordHash,
            base + "/" + domainPart + "/" + localPart,
            quota == null || quota.isBlank() ? "1G" : quota,
            Status.ACTIVE,
            Instant.now(),
            Instant.now()
        );
    }

    /** True when this alias has a mailbox of its own. */
    public boolean hasMailbox() {
        return mailboxPath != null && !mailboxPath.isEmpty();
    }

    /** True when this alias can authenticate. */
    public boolean canAuthenticate() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    /** True when this alias only forwards and stores nothing. */
    public boolean isRoutingOnly() {
        return !hasMailbox();
    }

    public Alias withTargets(String newTargets) {
        return new Alias(id, domainId, alias, newTargets.toLowerCase(), passwordHash,
                mailboxPath, quota, status, createdAt, Instant.now());
    }

    public Alias withPasswordHash(String newPasswordHash) {
        return new Alias(id, domainId, alias, targets, newPasswordHash,
                mailboxPath, quota, status, createdAt, Instant.now());
    }

    public Alias withMailboxPath(String newMailboxPath) {
        return new Alias(id, domainId, alias, targets, passwordHash,
                newMailboxPath, quota, status, createdAt, Instant.now());
    }

    public Alias withQuota(String newQuota) {
        return new Alias(id, domainId, alias, targets, passwordHash,
                mailboxPath, newQuota, status, createdAt, Instant.now());
    }

    public Alias withStatus(Status newStatus) {
        return new Alias(id, domainId, alias, targets, passwordHash,
                mailboxPath, quota, newStatus, createdAt, Instant.now());
    }
}
