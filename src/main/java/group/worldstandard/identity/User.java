package group.worldstandard.identity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

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
    @JsonProperty("lastLoginAt") Instant lastLoginAt
) {
    public enum Status {
        ACTIVE,
        DISABLED,
        PENDING
    }

    public static User create(UUID domainId, String email, String passwordHash, String quota) {
        String localPart = email.split("@")[0];
        String domainPart = email.split("@")[1];
        String mailboxPath = "/var/mail/" + domainPart + "/" + localPart;
        
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
            null
        );
    }

    public User withStatus(Status newStatus) {
        return new User(id, domainId, email, passwordHash, mailboxPath, quota, newStatus, createdAt, Instant.now(), lastLoginAt);
    }

    public User withPasswordHash(String newPasswordHash) {
        return new User(id, domainId, email, newPasswordHash, mailboxPath, quota, status, createdAt, Instant.now(), lastLoginAt);
    }

    public User withQuota(String newQuota) {
        return new User(id, domainId, email, passwordHash, mailboxPath, newQuota, status, createdAt, Instant.now(), lastLoginAt);
    }

    public User withLastLogin() {
        return new User(id, domainId, email, passwordHash, mailboxPath, quota, status, createdAt, Instant.now(), Instant.now());
    }
}