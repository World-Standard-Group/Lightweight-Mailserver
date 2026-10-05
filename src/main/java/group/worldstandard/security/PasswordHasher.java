package group.worldstandard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Hashes and verifies mailbox passwords.
 *
 * <p><b>This is not production-ready and must not be used with real
 * passwords.</b> The class declares Argon2id constants and the inner helper is
 * named {@code BCrypt}, but neither is true: the actual operation is
 * {@code SHA-256(salt + password)} with the result stored behind a
 * BCrypt-<i>looking</i> {@code $2a$12$} prefix.</p>
 *
 * <p>Three concrete problems:</p>
 * <ol>
 *   <li>SHA-256 is far too fast to resist offline brute force. Argon2id or
 *       bcrypt with a real work factor is required.</li>
 *   <li>The {@code $2a$} prefix misrepresents the hash to every tool that
 *       parses it, including Dovecot, which is generated with
 *       {@code default_pass_scheme = BLF-CRYPT} and will fail to verify these
 *       values.</li>
 *   <li>{@link #verify(String, String)} compares hashes with
 *       {@link String#equals(String)}, which short-circuits on the first
 *       differing byte and is not constant-time.</li>
 * </ol>
 *
 * <p>Replace with a vetted implementation (for example Spring Security's
 * {@code BCryptPasswordEncoder}, or {@code argon2-jvm}) before creating any real
 * user. Existing hashes will need migrating, since the format changes.</p>
 */
public class PasswordHasher {
    private static final Logger log = LoggerFactory.getLogger(PasswordHasher.class);
    
    // Argon2id parameters
    private static final int MEMORY = 65536; // 64 MB
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 4;
    private static final int HASH_LENGTH = 32;
    private static final int SALT_LENGTH = 16;

    public String hash(String password) {
        // Using BCrypt as a simpler alternative for now
        // In production, use Argon2id via BouncyCastle
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }

    public boolean verify(String password, String hash) {
        return BCrypt.checkpw(password, hash);
    }

    // Simple BCrypt implementation wrapper
    private static class BCrypt {
        static String hashpw(String password, String salt) {
            // This is a placeholder - in reality use a proper BCrypt library
            // For now, we'll use a simple approach
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(salt.getBytes());
                md.update(password.getBytes());
                byte[] hash = md.digest();
                return salt + "$" + bytesToHex(hash);
            } catch (Exception e) {
                throw new RuntimeException("Hashing failed", e);
            }
        }

        static boolean checkpw(String password, String hash) {
            String[] parts = hash.split("\\$");
            if (parts.length != 2) return false;
            String computed = hashpw(password, parts[0]);
            return computed.equals(hash);
        }

        static String gensalt(int rounds) {
            // Generate a random salt
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            return "$2a$" + String.format("%02d", rounds) + "$" + bytesToHex(salt);
        }

        static String bytesToHex(byte[] bytes) {
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }
}