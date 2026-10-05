package group.worldstandard.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

    /**
     * A mailbox belonging to exactly one domain.
     *  *
     *  * <p>{@code mailboxPath} is derived as {@code /var/mail/<domain>/<localpart>} at
     *  * creation time and rendered into the Dovecot {@code user_query}. The email is
     *  * always stored lowercased. A {@code DISABLED} user is excluded by Dovecot's SQL
     *  * queries, so access is revoked without deleting the row.</p>
     */
public record User(
    @JsonProperty("id") UUID id,
    @JsonProperty("domainId") UUID domainId,
    @JsonProperty("email") String email,
    @JsonProperty("passwordHash") String passwordHash,
    @JsonProperty("mailboxPath") String mailboxPath,
    @JsonProperty("quota") String quota,
    @JsonProperty("status") Status status,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("updatedAt") Instant updatedAt,
    @JsonProperty("lastLoginAt") Instant lastLoginAt,
    @JsonProperty("storageEnabled") boolean storageEnabled,
    @JsonProperty("forwardTargets") String forwardTargets
) {
    public enum Status {
        ACTIVE,
        DISABLED,
        PENDING
    }

    /** Default maildir root, used when no base is supplied. */
    public static final String DEFAULT_MAIL_BASE = "/var/mail";

    /** A normal user with a mailbox. */
    public static User create(UUID domainId, String email, String passwordHash, String quota) {
        return create(domainId, email, passwordHash, quota, true, null);
    }

    /** As {@link #create(UUID, String, String, String, boolean, String)} with the default maildir root. */
    public static User create(UUID domainId, String email, String passwordHash, String quota,
                              boolean storageEnabled, String forwardTargets) {
        return create(domainId, email, passwordHash, quota, storageEnabled, forwardTargets,
                DEFAULT_MAIL_BASE);
    }

    /**
     * Creates a user that either stores mail or forwards it.
     *
     * <p>A forwarding-only user gets no mailbox path at all: it must never appear
     * in Postfix's {@code virtual_mailbox_maps}, and its maildir is never
     * created on disk. Mail addressed to it is rewritten by
     * {@code virtual_alias_maps} instead.</p>
     *
     * @param forwardTargets comma-separated external addresses, or {@code null}
     *                       for a storage user
     */
    public static User create(UUID domainId, String email, String passwordHash, String quota,
                              boolean storageEnabled, String forwardTargets, String mailBase) {
        String localPart = email.split("@")[0];
        String domainPart = email.split("@")[1];
        // Deliberately empty for a forwarding-only user: an empty path makes it
        // obvious in the database that there is no mailbox, rather than leaving
        // a path that looks valid but has no directory.
        String base = (mailBase == null || mailBase.isBlank()) ? DEFAULT_MAIL_BASE : mailBase;
        String mailboxPath = storageEnabled
                ? base + "/" + domainPart + "/" + localPart
                : "";

        return new User(
            UUID.randomUUID(),
            domainId,
            email.toLowerCase(),
            passwordHash,
            mailboxPath,
            quota,
            Status.ACTIVE,
            Instant.now(),
            Instant.now(),
            null,
            storageEnabled,
            forwardTargets
        );
    }

    /** True when this user only forwards mail and stores nothing. */
    public boolean isForwardingOnly() {
        return !storageEnabled;
    }

    public User withStatus(Status newStatus) {
        return new User(id, domainId, email, passwordHash, mailboxPath, quota, newStatus,
                createdAt, Instant.now(), lastLoginAt, storageEnabled, forwardTargets);
    }

    public User withPasswordHash(String newPasswordHash) {
        return new User(id, domainId, email, newPasswordHash, mailboxPath, quota, status,
                createdAt, Instant.now(), lastLoginAt, storageEnabled, forwardTargets);
    }

    public User withQuota(String newQuota) {
        return new User(id, domainId, email, passwordHash, mailboxPath, newQuota, status,
                createdAt, Instant.now(), lastLoginAt, storageEnabled, forwardTargets);
    }

    public User withLastLogin() {
        return new User(id, domainId, email, passwordHash, mailboxPath, quota, status,
                createdAt, Instant.now(), Instant.now(), storageEnabled, forwardTargets);
    }

    /** Converts this user to forwarding-only (no mailbox). */
    public User asForwardingOnly(String targets) {
        return new User(id, domainId, email, passwordHash, "", quota, status,
                createdAt, Instant.now(), lastLoginAt, false, targets);
    }

    /** Converts this user back to a mailbox user with the given targets cleared. */
    public User asStorageUser(String newMailboxPath) {
        return new User(id, domainId, email, passwordHash, newMailboxPath, quota, status,
                createdAt, Instant.now(), lastLoginAt, true, null);
    }
}
