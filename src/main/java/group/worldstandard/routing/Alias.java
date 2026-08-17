package group.worldstandard.routing;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record Alias(
    @JsonProperty("id") UUID id,
    @JsonProperty("domainId") UUID domainId,
    @JsonProperty("alias") String alias,
    @JsonProperty("targets") String targets,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("updatedAt") Instant updatedAt
) {
    public static Alias create(UUID domainId, String alias, String targets) {
        return new Alias(
            UUID.randomUUID(),
            domainId,
            alias.toLowerCase(),
            targets.toLowerCase(),
            Instant.now(),
            Instant.now()
        );
    }

    public Alias withTargets(String newTargets) {
        return new Alias(id, domainId, alias, newTargets.toLowerCase(), createdAt, Instant.now());
    }
}