package group.worldstandard.identity;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class JdbcUserRepository implements UserRepository {
    private final HikariDataSource dataSource;

    public JdbcUserRepository(HikariDataSource dataSource) {
        this.dataSource = dataSource;
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("domain_id")),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("mailbox_path"),
            rs.getString("quota"),
            User.Status.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("last_login_at") != null ? rs.getTimestamp("last_login_at").toInstant() : null
        );
    }

    @Override
    public User save(User user) {
        String sql = """
            INSERT INTO users (id, domain_id, email, password_hash, mailbox_path, quota, status, created_at, updated_at, last_login_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE SET
                domain_id = EXCLUDED.domain_id,
                email = EXCLUDED.email,
                password_hash = EXCLUDED.password_hash,
                mailbox_path = EXCLUDED.mailbox_path,
                quota = EXCLUDED.quota,
                status = EXCLUDED.status,
                updated_at = EXCLUDED.updated_at,
                last_login_at = EXCLUDED.last_login_at
            """;
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, user.id());
            ps.setObject(2, user.domainId());
            ps.setString(3, user.email());
            ps.setString(4, user.passwordHash());
            ps.setString(5, user.mailboxPath());
            ps.setString(6, user.quota());
            ps.setString(7, user.status().name());
            ps.setObject(8, user.createdAt());
            ps.setObject(9, user.updatedAt());
            ps.setObject(10, user.lastLoginAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save user", e);
        }
        
        return user;
    }

    @Override
    public Optional<User> findById(UUID id) {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by id", e);
        }
        return Optional.empty();
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find user by email", e);
        }
        return Optional.empty();
    }

    @Override
    public List<User> findByDomainId(UUID domainId) {
        String sql = "SELECT * FROM users WHERE domain_id = ? ORDER BY created_at DESC";
        List<User> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, domainId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find users by domain", e);
        }
        return results;
    }

    @Override
    public List<User> findByDomainIdAndStatus(UUID domainId, User.Status status) {
        String sql = "SELECT * FROM users WHERE domain_id = ? AND status = ? ORDER BY created_at DESC";
        List<User> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, domainId);
            ps.setString(2, status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find users by domain and status", e);
        }
        return results;
    }

    @Override
    public List<User> findAll() {
        String sql = "SELECT * FROM users ORDER BY created_at DESC";
        List<User> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find all users", e);
        }
        return results;
    }

    @Override
    public void deleteById(UUID id) {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete user", e);
        }
    }

    @Override
    public boolean existsByEmail(String email) {
        String sql = "SELECT 1 FROM users WHERE email = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check user existence", e);
        }
    }
}