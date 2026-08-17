package group.worldstandard.domain;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcDomainRepository implements DomainRepository {
    private final HikariDataSource dataSource;

    public JdbcDomainRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Domain mapRow(ResultSet rs) throws SQLException {
        return new Domain(
            UUID.fromString(rs.getString("id")),
            rs.getString("name"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            Domain.Status.valueOf(rs.getString("status")),
            rs.getString("dkim_selector"),
            rs.getInt("dkim_key_size"),
            rs.getString("dkim_algorithm")
        );
    }

    @Override
    public Domain save(Domain domain) {
        String sql = """
            INSERT INTO domains (id, name, created_at, updated_at, status, dkim_selector, dkim_key_size, dkim_algorithm)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                name = EXCLUDED.name,
                updated_at = EXCLUDED.updated_at,
                status = EXCLUDED.status,
                dkim_selector = EXCLUDED.dkim_selector,
                dkim_key_size = EXCLUDED.dkim_key_size,
                dkim_algorithm = EXCLUDED.dkim_algorithm
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, domain.id());
            ps.setString(2, domain.name());
            ps.setObject(3, domain.createdAt());
            ps.setObject(4, domain.updatedAt());
            ps.setString(5, domain.status().name());
            ps.setString(6, domain.dkimSelector());
            ps.setInt(7, domain.dkimKeySize());
            ps.setString(8, domain.dkimAlgorithm());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save domain", e);
        }
        
        return domain;
    }

    @Override
    public Optional<Domain> findById(UUID id) {
        String sql = "SELECT * FROM domains WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find domain by id", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Domain> findByName(String name) {
        String sql = "SELECT * FROM domains WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find domain by name", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Domain> findAll() {
        String sql = "SELECT * FROM domains ORDER BY created_at DESC";
        List<Domain> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all domains", e);
        }
        return results;
    }

    @Override
    public List<Domain> findByStatus(Domain.Status status) {
        String sql = "SELECT * FROM domains WHERE status = ? ORDER BY created_at DESC";
        List<Domain> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find domains by status", e);
        }
        return results;
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM domains WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete domain", e);
        }
    }

    @Override
    public boolean existsByName(String name) {
        String sql = "SELECT 1 FROM domains WHERE name = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check domain existence", e);
        }
    }
}