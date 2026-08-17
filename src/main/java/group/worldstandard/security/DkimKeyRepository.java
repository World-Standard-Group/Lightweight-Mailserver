package group.worldstandard.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DkimKeyRepository {
    DkimKey save(DkimKey key);
    Optional<DkimKey> findById(UUID id);
    Optional<DkimKey> findByDomainIdAndSelector(UUID domainId, String selector);
    List<DkimKey> findByDomainId(UUID domainId);
    List<DkimKey> findActiveByDomainId(UUID domainId);
    void deleteById(UUID id);
}