package group.worldstandard.security;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.security.Key;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

    /**
     * A DKIM key pair plus the DNS record that publishes its public half.
     *
     * <p>The private key is stored in the database so the control plane can render
     * Rspamd's signing maps without external key storage. Treat this table as a
     * secret: a leak lets anyone sign mail as the domain.</p>
     */
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

    /**
     * Builds the DKIM DNS TXT record.
     *
     * <p>Only RSA is supported. For RSA, {@code p=} is the base64 of the
     * DER-encoded X.509 SubjectPublicKeyInfo, which is exactly what the PEM body
     * holds.</p>
     *
     * <p>Ed25519 (RFC 8463) is deliberately not implemented: it would need the
     * raw 32-byte key in {@code p=} rather than the SPKI, and a number of major
     * receiving servers still fail to verify Ed25519 DKIM signatures. Use
     * {@code rsa2048} or {@code rsa4096}.</p>
     */
    private static String generateDnsRecord(String selector, String publicKeyPem, String algorithm) {
        // Strip the PEM armour to recover the base64 SPKI payload.
        String key = publicKeyPem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");

        return String.format("v=DKIM1; k=rsa; p=%s", key);
    }
}