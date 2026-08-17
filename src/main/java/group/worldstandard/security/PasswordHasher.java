package group.worldstandard.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.SecureRandom;

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