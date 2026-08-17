package group.worldstandard.routing;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcAliasRepository implements AliasRepository {
    private final HikariDataSource dataSource;

    public JdbcAliasRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    private Alias mapRow(ResultSet rs) throws SQLException {
        return new Alias(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("domain_id")),
            rs.getString("alias"),
            rs.getString("targets"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    @Override
    public Alias save(Alias alias) {
        String sql = """
            INSERT INTO aliases (id, domain_id, alias, targets, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                domain_id = EXCLUDED.domain_id,
                alias = EXCLUDED.alias,
                targets = EXCLUDED.targets,
                updated_at = EXCLUDED.updated_at
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, alias.id());
            ps.setObject(2, alias.domainId());
            ps.setString(3, alias.alias());
            ps.setString(4, alias.targets());
            ps.setObject(5, alias.createdAt());
            ps.setObject(6, alias.updatedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save alias", e);
        }
        
        return alias;
    }

    @Override
    public Optional<Alias> findById(UUID id) {
        String sql = "SELECT * FROM aliases WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find alias by id", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<Alias> findByAlias(String alias) {
        String sql = "SELECT * FROM aliases WHERE alias = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alias.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find alias by address", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Alias> findByDomainId(UUID domainId) {
        String sql = "SELECT * FROM aliases WHERE domain_id = ? ORDER BY created_at DESC";
        List<Alias> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, domainId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find aliases by domain", e);
        }
        return results;
    }

    @Override
    public List<Alias> findAll() {
        String sql = "SELECT * FROM aliases ORDER BY created_at DESC";
        List<Alias> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all aliases", e);
        }
        return results;
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM aliases WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete alias", e);
        }
    }

    @Override
    public boolean existsByAlias(String alias) {
        String sql = "SELECT 1 FROM aliases WHERE alias = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, alias.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check alias existence", e);
        }
    }
}