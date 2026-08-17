package group.worldstandard.security;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.security.Key;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

public record DkimKey(
    @JsonProperty("id") UUID id,
    @JsonProperty("domainId") UUID domainId,
    @JsonProperty("selector") String selector,
    @JsonProperty("algorithm") String algorithm,
    @JsonProperty("keySize") int keySize,
    @JsonProperty("privateKeyPem") String privateKeyPem,
    @JsonProperty("publicKeyPem") String publicKeyPem,
    @JsonProperty("dnsRecord") String dnsRecord,
    @JsonProperty("createdAt") Instant createdAt,
    @JsonProperty("expiresAt") Instant expiresAt,
    @JsonProperty("status") Status status
) {
    public enum Status {
        ACTIVE,
        RETIRED,
        REVOKED
    }

    public static DkimKey create(UUID domainId, String selector, String algorithm, int keySize, 
                                  KeyPair keyPair, Instant expiresAt) {
        String privateKeyPem = keyToPem(keyPair.getPrivate(), "PRIVATE KEY");
        String publicKeyPem = keyToPem(keyPair.getPublic(), "PUBLIC KEY");
        String dnsRecord = generateDnsRecord(selector, publicKeyPem, algorithm);
        
        return new DkimKey(
            UUID.randomUUID(),
            domainId,
            selector,
            algorithm,
            keySize,
            privateKeyPem,
            publicKeyPem,
            dnsRecord,
            Instant.now(),
            expiresAt,
            Status.ACTIVE
        );
    }

    private static String keyToPem(Key key, String type) {
        String encoded = Base64.getEncoder().encodeToString(key.getEncoded());
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < encoded.length(); i += 64) {
            sb.append(encoded.substring(i, Math.min(i + 64, encoded.length()))).append("\n");
        }
        sb.append("-----END ").append(type).append("-----\n");
        return sb.toString();
    }

    private static String generateDnsRecord(String selector, String publicKeyPem, String algorithm) {
        // Extract the base64 public key (without headers)
        String key = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        
        String k = "rsa".equalsIgnoreCase(algorithm) ? "rsa" : "ed25519";
        return String.format("v=DKIM1; k=%s; p=%s", k, key);
    }
}