package group.worldstandard.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DomainService {
    private final DomainRepository domainRepository;

    public DomainService(DomainRepository domainRepository) {
        this.domainRepository = domainRepository;
    }

    public Domain addDomain(String name, String dkimSelector, int dkimKeySize, String dkimAlgorithm) {
        if (domainRepository.existsByName(name)) {
            throw new IllegalArgumentException("Domain already exists: " + name);
        }
        
        validateDomain(name);
        
        Domain domain = Domain.create(name);
        if (dkimSelector != null) {
            domain = domain.withDkimSettings(dkimSelector, dkimKeySize, dkimAlgorithm);
        }
        
        return domainRepository.save(domain);
    }

    public void removeDomain(UUID id) {
        Domain domain = domainRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + id));
        domainRepository.deleteById(id);
    }

    public Optional<Domain> getDomain(UUID id) {
        return domainRepository.findById(id);
    }

    public Optional<Domain> getDomainByName(String name) {
        return domainRepository.findByName(name);
    }

    public List<Domain> listDomains() {
        return domainRepository.findAll();
    }

    public List<Domain> listActiveDomains() {
        return domainRepository.findByStatus(Domain.Status.ACTIVE);
    }

    public Domain updateDomainStatus(UUID id, Domain.Status status) {
        Domain domain = domainRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + id));
        Domain updated = domain.withStatus(status);
        return domainRepository.save(updated);
    }

    private void validateDomain(String domain) {
        if (domain == null || domain.trim().isEmpty()) {
            throw new IllegalArgumentException("Domain name cannot be empty");
        }
        
        String lower = domain.toLowerCase();
        if (!lower.matches("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$")) {
            throw new IllegalArgumentException("Invalid domain format: " + domain);
        }
        
        if (lower.length() > 253) {
            throw new IllegalArgumentException("Domain name too long: " + domain);
        }
        
        // Check each label
        for (String label : lower.split("\\.")) {
            if (label.length() > 63) {
                throw new IllegalArgumentException("Domain label too long: " + label);
            }
            if (label.startsWith("-") || label.endsWith("-")) {
                throw new IllegalArgumentException("Domain label cannot start or end with hyphen: " + label);
            }
        }
    }
}