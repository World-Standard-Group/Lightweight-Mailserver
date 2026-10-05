package group.worldstandard.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

    /**
     * Persistence contract for {@link group.worldstandard.identity.User} records.
     *  *
     *  * <p>Implementations must treat lookups as case-insensitive.</p>
     */
public interface UserRepository {
    User save(User user);
    Optional<User> findById(UUID id);
    Optional<User> findByEmail(String email);
    List<User> findByDomainId(UUID domainId);
    List<User> findByDomainIdAndStatus(UUID domainId, User.Status status);
    List<User> findAll();
    void deleteById(UUID id);
    boolean existsByEmail(String email);
}