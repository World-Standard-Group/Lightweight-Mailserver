package group.worldstandard.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

    /**
     * Persistence contract for {@link DkimKey} records.
     *  *
     *  * <p>Uniqueness of {@code (domain_id, selector)} is enforced by a database
     *  * constraint; {@link DkimKeyManager#rotateKey} relies on it.</p>
     */
public interface DkimKeyRepository {
    DkimKey save(DkimKey key);
    Optional<DkimKey> findById(UUID id);
    Optional<DkimKey> findByDomainIdAndSelector(UUID domainId, String selector);
    List<DkimKey> findByDomainId(UUID domainId);
    List<DkimKey> findActiveByDomainId(UUID domainId);
    void deleteById(UUID id);
}