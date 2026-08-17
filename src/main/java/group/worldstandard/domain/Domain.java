package group.worldstandard.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record Domain(
    @JsonProperty("id") UUID id,
    @JsonProperty("name") String name,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("updatedAt") Instant updatedAt,
    @JsonProperty("status") Status status,
    @JsonProperty("dkimSelector") String dkimSelector,
    @JsonProperty("dkimKeySize") int dkimKeySize,
    @JsonProperty("dkimAlgorithm") String dkimAlgorithm
) {
    public enum Status {
        ACTIVE,
        DISABLED,
        PENDING
    }

    public static Domain create(String name) {
        return new Domain(
            UUID.randomUUID(),
            name.toLowerCase(),
            Instant.now(),
            Instant.now(),
            Status.ACTIVE,
            "mail",
            2048,
            "rsa2048"
        );
    }

    public Domain withStatus(Status newStatus) {
        return new Domain(id, name, createdAt, Instant.now(), newStatus, dkimSelector, dkimKeySize, dkimAlgorithm);
    }

    public Domain withDkimSettings(String selector, int keySize, String algorithm) {
        return new Domain(id, name, createdAt, Instant.now(), status, selector, keySize, algorithm);
    }
}