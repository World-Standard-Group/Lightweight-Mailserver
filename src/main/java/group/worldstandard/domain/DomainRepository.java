package group.worldstandard.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DomainRepository {
    Domain save(Domain domain);
    Optional<Domain> findById(UUID id);
    Optional<Domain> findByName(String name);
    List<Domain> findAll();
    List<Domain> findByStatus(Domain.Status status);
    void deleteById(UUID id);
    boolean existsByName(String name);
}