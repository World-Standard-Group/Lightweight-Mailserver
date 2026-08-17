package group.worldstandard.routing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AliasRepository {
    Alias save(Alias alias);
    Optional<Alias> findById(UUID id);
    Optional<Alias> findByAlias(String alias);
    List<Alias> findByDomainId(UUID domainId);
    List<Alias> findAll();
    void deleteById(UUID id);
    boolean existsByAlias(String alias);
}