package group.worldstandard.security;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcDkimKeyRepository implements DkimKeyRepository {
    private final HikariDataSource dataSource;

    public JdbcDkimKeyRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    private DkimKey mapRow(ResultSet rs) throws SQLException {
        return new DkimKey(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("domain_id")),
            rs.getString("selector"),
            rs.getString("algorithm"),
            rs.getInt("key_size"),
            rs.getString("private_key_pem"),
            rs.getString("public_key_pem"),
            rs.getString("dns_record"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("expires_at").toInstant(),
            DkimKey.Status.valueOf(rs.getString("status"))
        );
    }

    @Override
    public DkimKey save(DkimKey key) {
        String sql = """
            INSERT INTO dkim_keys (id, domain_id, selector, algorithm, key_size, private_key_pem, public_key_pem, dns_record, created_at, expires_at, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                domain_id = EXCLUDED.domain_id,
                selector = EXCLUDED.selector,
                algorithm = EXCLUDED.algorithm,
                key_size = EXCLUDED.key_size,
                private_key_pem = EXCLUDED.private_key_pem,
                public_key_pem = EXCLUDED.public_key_pem,
                dns_record = EXCLUDED.dns_record,
                created_at = EXCLUDED.created_at,
                expires_at = EXCLUDED.expires_at,
                status = EXCLUDED.status
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, key.id());
            ps.setObject(2, key.domainId());
            ps.setString(3, key.selector());
            ps.setString(4, key.algorithm());
            ps.setInt(5, key.keySize());
            ps.setString(6, key.privateKeyPem());
            ps.setString(7, key.publicKeyPem());
            ps.setString(8, key.dnsRecord());
            ps.setObject(9, key.createdAt());
            ps.setObject(10, key.expiresAt());
            ps.setString(11, key.status().name());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save DKIM key", e);
        }
        
        return key;
    }

    @Override
    public Optional<DkimKey> findById(UUID id) {
        String sql = "SELECT * FROM dkim_keys WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find DKIM key by id", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<DkimKey> findByDomainIdAndSelector(UUID domainId, String selector) {
        String sql = "SELECT * FROM dkim_keys WHERE domain_id = ? AND selector = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, domainId);
            ps.setString(2, selector);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find DKIM key by domain and selector", e);
        }
        return Optional.empty();
    }

    @Override
    public List<DkimKey> findByDomainId(UUID domainId) {
        String sql = "SELECT * FROM dkim_keys WHERE domain_id = ? ORDER BY created_at DESC";
        List<DkimKey> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, domainId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find DKIM keys by domain", e);
        }
        return results;
    }

    @Override
    public List<DkimKey> findActiveByDomainId(UUID domainId) {
        String sql = "SELECT * FROM dkim_keys WHERE domain_id = ? AND status = 'ACTIVE' ORDER BY created_at DESC";
        List<DkimKey> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, domainId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active DKIM keys by domain", e);
        }
        return results;
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM dkim_keys WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete DKIM key", e);
        }
    }
}