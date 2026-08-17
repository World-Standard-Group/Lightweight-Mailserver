package group.worldstandard.routing;

import group.worldstandard.domain.Domain;
import group.worldstandard.domain.DomainRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class RoutingService {
    private final AliasRepository aliasRepository;
    private final DomainRepository domainRepository;

    public RoutingService(AliasRepository aliasRepository, DomainRepository domainRepository) {
        this.aliasRepository = aliasRepository;
        this.domainRepository = domainRepository;
    }

    public Alias addAlias(String alias, String targets) {
        String[] aliasParts = alias.split("@");
        if (aliasParts.length != 2) {
            throw new IllegalArgumentException("Invalid alias format: " + alias);
        }
        
        String domainName = aliasParts[1].toLowerCase();
        Domain domain = domainRepository.findByName(domainName)
            .orElseThrow(() -> new IllegalArgumentException("Domain not found: " + domainName));
        
        if (domain.status() != Domain.Status.ACTIVE) {
            throw new IllegalArgumentException("Domain is not active: " + domainName);
        }
        
        if (aliasRepository.existsByAlias(alias)) {
            throw new IllegalArgumentException("Alias already exists: " + alias);
        }
        
        // Validate targets are local addresses (for now)
        String[] targetList = targets.split(",");
        for (String target : targetList) {
            String trimmed = target.trim();
            if (!trimmed.endsWith("@" + domainName)) {
                throw new IllegalArgumentException("External forwarding not yet supported: " + trimmed);
            }
        }
        
        Alias newAlias = Alias.create(domain.id(), alias, targets);
        return aliasRepository.save(newAlias);
    }

    public void removeAlias(UUID id) {
        aliasRepository.deleteById(id);
    }

    public void removeAliasByAddress(String alias) {
        aliasRepository.findByAlias(alias)
            .ifPresent(a -> aliasRepository.deleteById(a.id()));
    }

    public Optional<Alias> getAlias(UUID id) {
        return aliasRepository.findById(id);
    }

    public Optional<Alias> getAliasByAddress(String alias) {
        return aliasRepository.findByAlias(alias);
    }

    public List<Alias> listAliases(UUID domainId) {
        return aliasRepository.findByDomainId(domainId);
    }

    public List<Alias> listAllAliases() {
        return aliasRepository.findAll();
    }

    public Alias updateAliasTargets(UUID id, String newTargets) {
        Alias alias = aliasRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Alias not found: " + id));
        
        // Validate targets
        String[] aliasParts = alias.alias().split("@");
        String domainName = aliasParts[1].toLowerCase();
        
        String[] targetList = newTargets.split(",");
        for (String target : targetList) {
            String trimmed = target.trim();
            if (!trimmed.endsWith("@" + domainName)) {
                throw new IllegalArgumentException("External forwarding not yet supported: " + trimmed);
            }
        }
        
        Alias updated = alias.withTargets(newTargets);
        return aliasRepository.save(updated);
    }

    public String resolveAlias(String alias) {
        return aliasRepository.findByAlias(alias)
            .map(Alias::targets)
            .orElse(null);
    }
}