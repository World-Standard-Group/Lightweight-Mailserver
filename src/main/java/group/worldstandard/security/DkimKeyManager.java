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
        
        DkimKey dkimKey = DkimKey.create(domain.id(), selector, algorithm, keySize, keyPair, expiresAt);
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
        
        // Generate new key with same selector
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

    private KeyPair generateKeyPair(String algorithm, int keySize) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(getJavaAlgorithm(algorithm));
            if ("RSA".equalsIgnoreCase(getJavaAlgorithm(algorithm))) {
                keyGen.initialize(new RSAKeyGenParameterSpec(keySize, RSAKeyGenParameterSpec.F4));
            } else {
                keyGen.initialize(keySize);
            }
            return keyGen.generateKeyPair();
        } catch (NoSuchAlgorithmException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Failed to generate key pair", e);
        }
    }

    private String getJavaAlgorithm(String algorithm) {
        return switch (algorithm.toLowerCase()) {
            case "rsa2048", "rsa4096" -> "RSA";
            case "ed25519" -> "Ed25519";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };
    }
}