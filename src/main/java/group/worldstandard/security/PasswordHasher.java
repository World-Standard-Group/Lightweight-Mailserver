package group.worldstandard.security;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;

/**
 * Hashes and verifies mailbox passwords with bcrypt (OpenBSD dialect).
 *
 * <p>The stored value is <code>{BLF-CRYPT}$2y$&lt;cost&gt;$&lt;salt&gt;&lt;digest&gt;</code>.
 * The scheme prefix is not decoration. Dovecot's SQL passdb defaults to
 * <code>default_pass_scheme = MD5</code> (Dovecot 2.3.21,
 * <code>src/auth/db-sql.c</code>), so a bare <code>$2y$...</code> value would be
 * routed to <code>md5_verify</code> and could never match. The prefix makes
 * Dovecot strip it and select BLF-CRYPT explicitly, which is exactly what
 * <code>crypt_verify_blowfish</code> expects: it checks only that the value
 * starts with <code>$2</code>, that the third character is a letter and that
 * the fourth is <code>$</code>, then recomputes the digest and compares
 * (<code>src/auth/password-scheme-crypt.c</code>).</p>
 *
 * <p>Why <code>$2y$</code> and not <code>$2a$</code>: Dovecot's bundled
 * <code>crypt_blowfish</code> assigns different flags per subtype
 * (<code>src/auth/crypt-blowfish.c</code>, <code>flags_by_subtype</code>:
 * <code>a</code> maps to 2, <code>b</code> and <code>y</code> map to 4), where
 * the <code>y</code> subtype carries the corrected handling of 8-bit
 * characters. Bouncy Castle's implementation is the corrected one, and the two
 * were checked against each other with an oracle compiled from Dovecot's own
 * <code>crypt_blowfish.c</code>: a hash generated here verifies under Dovecot,
 * and the canonical jBCrypt vector
 * <code>$2a$06$DCq7YPn5Rq63x1Lad4cll.TV4S6ytwfsfvkgY8jIucDrjc8deX1s.</code>
 * verifies as well.</p>
 *
 * <p>Passwords hashed by the previous SHA-256 placeholder cannot be
 * verified: they are not bcrypt values. They fail closed - verification
 * returns false and logs a warning - so affected accounts must have their
 * password reset.</p>
 */
public class PasswordHasher {
    private static final Logger log = LoggerFactory.getLogger(PasswordHasher.class);

    /** Dovecot password scheme name for bcrypt. */
    public static final String SCHEME = "BLF-CRYPT";

    /** The prefix Dovecot strips before selecting the scheme. */
    public static final String SCHEME_PREFIX = "{" + SCHEME + "}";

    /** bcrypt cost factor. 12 is the current sensible default. */
    private static final int COST = 12;

    /** bcrypt salt length in bytes (encoded as 22 base64 characters). */
    private static final int SALT_BYTES = 16;

    private final SecureRandom random = new SecureRandom();

    /**
     * Hashes a password for storage.
     *
     * @param password the cleartext password
     * @return a <code>{BLF-CRYPT}$2y$...</code> string
     */
    public String hash(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return SCHEME_PREFIX + OpenBSDBCrypt.generate("2y", password.toCharArray(), salt, COST);
    }

    /**
     * Verifies a password against a stored hash.
     *
     * <p>The scheme prefix is stripped before the value reaches Bouncy
     * Castle, whose verifier only accepts a bare bcrypt string. A value that
     * is not a bcrypt hash at all - including the SHA-256 placeholders written
     * by earlier versions - returns false: it must never be reported as a
     * match.</p>
     *
     * @param password   the cleartext password to check
     * @param storedHash the stored hash, with or without the scheme prefix
     * @return true only when the password produces the stored digest
     */
    public boolean verify(String password, String storedHash) {
        if (password == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        String value = storedHash.trim();
        if (value.startsWith("{")) {
            int end = value.indexOf('}');
            if (end < 0) {
                log.warn("Stored password has an unterminated scheme prefix; refusing to verify");
                return false;
            }
            value = value.substring(end + 1);
        }
        try {
            return OpenBSDBCrypt.checkPassword(value, password.toCharArray());
        } catch (IllegalArgumentException e) {
            log.warn("Stored password is not a bcrypt hash; refusing to verify");
            return false;
        }
    }
}
