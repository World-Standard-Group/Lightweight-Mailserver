package group.worldstandard.security;

import group.worldstandard.domain.Domain;
import group.worldstandard.domain.DomainRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.security.spec.RSAKeyGenParameterSpec;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

    /**
     * DKIM key lifecycle: generate, rotate and query.
     *  *
     *  * <p>Rotation marks the existing key {@code RETIRED} and stores a new one under the
     *  * same selector, so the DNS record changes at the same selector name. Plan a
     *  * propagation window: mail signed by the retired key stops verifying once DNS
     *  * updates.</p>
     *  *
     *  * <p>Relies on the {@code (domain_id, selector)} unique constraint to replace the
     *  * key rather than accumulate duplicates.</p>
     */
public class DkimKeyManager {
    private static final Logger log = LoggerFactory.getLogger(DkimKeyManager.class);
    
    private final DkimKeyRepository dkimKeyRepository;
    private final DomainRepository domainRepository;

    public DkimKeyManager(DkimKeyRepository dkimKeyRepository, DomainRepository domainRepository) {
        this.dkimKeyRepository = dkimKeyRepository;
        this.domainRepository = domainRepository;
    }

    public DkimKey generateKey(String domainName, String selector, int keySize, String algorithm) {
        Domain domain = domainRepository.findByName(domainName)
            .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainName));
        
        if (dkimKeyRepository.findByDomainIdAndSelector(domain.id(), selector).isPresent()) {
            throw new IllegalArgumentException("DKIM key already exists for selector: " + selector);
        }
        
        KeyPair keyPair = generateKeyPair(algorithm, keySize);
        Instant expiresAt = Instant.now().plusSeconds(365L * 24 * 60 * 60); // 1 year

        DkimKey dkimKey = DkimKey.create(domain.id(), selector, algorithm,
                validateRsaSize(keySize), keyPair, expiresAt);
        return dkimKeyRepository.save(dkimKey);
    }

    public DkimKey rotateKey(String domainName, String selector) {
        Domain domain = domainRepository.findByName(domainName)
            .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainName));
        
        DkimKey oldKey = dkimKeyRepository.findByDomainIdAndSelector(domain.id(), selector)
            .orElseThrow(() -> new IllegalArgumentException("DKIM key not found for selector: " + selector));
        
        // Mark old key as retired
        DkimKey retiredKey = new DkimKey(
            oldKey.id(),
            oldKey.domainId(),
            oldKey.selector(),
            oldKey.algorithm(),
            oldKey.keySize(),
            oldKey.privateKeyPem(),
            oldKey.publicKeyPem(),
            oldKey.dnsRecord(),
            oldKey.createdAt(),
            oldKey.expiresAt(),
            DkimKey.Status.RETIRED
        );
        dkimKeyRepository.save(retiredKey);
        
        // Generate new key with same selector (rejects any non-RSA legacy row)
        KeyPair keyPair = generateKeyPair(oldKey.algorithm(), oldKey.keySize());
        Instant expiresAt = Instant.now().plusSeconds(365L * 24 * 60 * 60);
        
        DkimKey newKey = DkimKey.create(domain.id(), selector, oldKey.algorithm(), oldKey.keySize(), keyPair, expiresAt);
        return dkimKeyRepository.save(newKey);
    }

    public Optional<DkimKey> getKey(String domainName, String selector) {
        Domain domain = domainRepository.findByName(domainName).orElse(null);
        if (domain == null) return Optional.empty();
        return dkimKeyRepository.findByDomainIdAndSelector(domain.id(), selector);
    }

    public List<DkimKey> getKeysForDomain(String domainName) {
        Domain domain = domainRepository.findByName(domainName).orElse(null);
        if (domain == null) return List.of();
        return dkimKeyRepository.findByDomainId(domain.id());
    }

    public List<DkimKey> getActiveKeysForDomain(String domainName) {
        Domain domain = domainRepository.findByName(domainName).orElse(null);
        if (domain == null) return List.of();
        return dkimKeyRepository.findActiveByDomainId(domain.id());
    }

    public String getDnsRecord(String domainName, String selector) {
        return getKey(domainName, selector)
            .map(DkimKey::dnsRecord)
            .orElseThrow(() -> new IllegalArgumentException("DKIM key not found"));
    }

    /**
     * Generates an RSA key pair.
     *
     * <p>Only RSA is supported. Ed25519 is rejected on purpose: a number of major
     * receiving servers still fail to verify Ed25519 DKIM signatures, so an
     * Ed25519 key would mean silently unsigned or unverifiable mail.</p>
     */
    private KeyPair generateKeyPair(String algorithm, int keySize) {
        requireRsa(algorithm);
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(new RSAKeyGenParameterSpec(keySize, RSAKeyGenParameterSpec.F4));
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Failed to generate key pair: " + e.getMessage(), e);
        }
    }

    /**
     * Rejects any non-RSA algorithm with an actionable message.
     */
    public static void requireRsa(String algorithm) {
        String alg = algorithm == null ? "" : algorithm.trim().toLowerCase();
        boolean rsa = alg.equals("rsa") || alg.equals("rsa1024")
                || alg.equals("rsa2048") || alg.equals("rsa3072") || alg.equals("rsa4096");
        if (!rsa) {
            // Distinguish "you asked for a non-RSA algorithm" from "you asked for
            // an RSA algorithm at a size that does not exist", so the caller
            // gets the error that actually applies.
            if (alg.startsWith("rsa")) {
                throw new IllegalArgumentException(
                        "Unknown RSA variant: '" + algorithm
                        + "'. Use rsa2048 or rsa4096 (or set --key-size to a"
                        + " supported size: 1024, 2048, 3072, 4096).");
            }
            throw new IllegalArgumentException(
                    "Unsupported DKIM algorithm: '" + algorithm
                    + "'. This server signs with RSA only; use rsa2048 or rsa4096."
                    + " (Ed25519 is not offered because several major receiving"
                    + " servers cannot verify Ed25519 DKIM signatures.)");
        }
    }

    /** Valid RSA key sizes in bits. */
    private static int validateRsaSize(int keySize) {
        if (keySize != 1024 && keySize != 2048 && keySize != 3072 && keySize != 4096) {
            throw new IllegalArgumentException(
                    "Unsupported RSA key size: " + keySize + " (expected 1024, 2048, 3072 or 4096)");
        }
        return keySize;
    }
}