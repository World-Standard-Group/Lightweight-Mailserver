package group.worldstandard.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Builds the PostgreSQL JDBC URL for the two supported deployment modes.
 *
 * <p><b>local</b> — the bundled {@code postgres} container on the Compose
 * network. No TLS, no published port; reachable only as {@code postgres:5432}
 * from sibling containers.</p>
 *
 * <p><b>external</b> — a remote PostgreSQL over TLS. This mode is enforced, not
 * merely defaulted: the URL is built with {@code sslmode=verify-full} and
 * {@code channelBinding=require}, and {@link #buildUrl} rejects any
 * attempt to weaken it. verify-full validates the server certificate against the
 * supplied CA <em>and</em> checks the hostname matches; channelBinding binds the
 * SCRAM authentication exchange to the TLS channel so a stolen password cannot
 * be replayed off-channel.</p>
 *
 * <p>Driver selection is left to PgJDBC: {@code sslrootcert}/{@code sslcert}/
 * {@code sslkey} are used directly, and the entrypoint stages the key at mode
 * 0600 in a writable location, which is what PgJDBC's PEM loader requires.</p>
 */
public final class DbConnection {

    private static final Logger log = LoggerFactory.getLogger(DbConnection.class);

    /** Where the entrypoint stages client TLS material with 0600 permissions. */
    public static final String PKI_DIR = "/etc/mailctl/pki";

    private DbConnection() {}

    public enum Mode {
        LOCAL,
        EXTERNAL;

        public static Mode fromEnv() {
            String raw = System.getenv("MAILCTL_DB_MODE");
            return (raw == null || raw.isBlank()) ? LOCAL : parse(raw);
        }

        public static Mode parse(String raw) {
            return switch (raw.trim().toLowerCase()) {
                case "local" -> LOCAL;
                case "external" -> EXTERNAL;
                default -> throw new IllegalArgumentException(
                        "MAILCTL_DB_MODE must be 'local' or 'external', got: " + raw);
            };
        }
    }

    /**
     * Builds the JDBC URL for the requested mode.
     *
     * @param mode      local or external
     * @param host      database hostname
     * @param port      database port
     * @param database  database name
     * @param user      database role
     * @param sslMode   requested sslmode; external mode refuses anything but verify-full
     */
    public static String buildUrl(Mode mode, String host, int port, String database,
                                  String user, String sslMode) {
        requirePresent(host, "database host");
        requirePresent(database, "database name");
        requirePresent(user, "database user");

        String base = "jdbc:postgresql://" + host + ":" + port + "/" + database;
        String effectiveSsl = (sslMode == null || sslMode.isBlank()) ? "disable" : sslMode.trim();

        if (mode == Mode.LOCAL) {
            // Local traffic never leaves the Compose bridge network.
            if (!"disable".equalsIgnoreCase(effectiveSsl)) {
                throw new IllegalArgumentException(
                        "local mode does not use TLS (got sslmode=" + effectiveSsl
                        + "). Set MAILCTL_DB_MODE=external for a TLS connection.");
            }
            log.info("PostgreSQL: local mode, no TLS, host={} db={} user={}", host, database, user);
            return base;
        }

        // --- EXTERNAL: TLS is mandatory and cannot be downgraded ---

        if (!"verify-full".equalsIgnoreCase(effectiveSsl)) {
            throw new IllegalArgumentException(
                    "external mode requires sslmode=verify-full, got: " + effectiveSsl
                    + ". Weaker modes (require/allow/prefer) do not verify the"
                    + " server identity and are refused.");
        }

        String rootCert = System.getenv("MAILCTL_DB_SSL_ROOT_CERT");
        String clientCert = System.getenv("MAILCTL_DB_SSL_CERT");
        String clientKey = System.getenv("MAILCTL_DB_SSL_KEY");

        // Defence in depth: the shell entrypoint and start.sh already refuse a
        // local/internal host in external mode, but enforce it here too so the
        // rule holds for every entry point (including a plain `java -jar`).
        String hostLower = host.trim().toLowerCase();
        if (hostLower.equals("localhost") || hostLower.equals("postgres")
                || hostLower.equals("127.0.0.1") || hostLower.equals("::1")) {
            throw new IllegalArgumentException(
                    "external mode cannot target a local/internal host: " + host
                    + ". Set MAILCTL_DB_MODE=local to use the bundled container.");
        }

        if (isBlank(rootCert)) {
            throw new IllegalArgumentException(
                    "external mode requires MAILCTL_DB_SSL_ROOT_CERT (the CA that signed"
                    + " the server certificate). Without it verify-full cannot validate"
                    + " the server.");
        }
        requireReadable(rootCert, "MAILCTL_DB_SSL_ROOT_CERT");

        StringBuilder url = new StringBuilder(base)
                .append("?sslmode=verify-full")
                .append("&sslrootcert=").append(rootCert)
                // Bind SCRAM auth to the TLS channel: prevents replay of a
                // captured exchange and blocks a downgrade to plain auth.
                .append("&channelBinding=require");

        if (!isBlank(clientCert) && !isBlank(clientKey)) {
            requireReadable(clientCert, "MAILCTL_DB_SSL_CERT");
            requireReadable(clientKey, "MAILCTL_DB_SSL_KEY");
            url.append("&sslcert=").append(clientCert)
               .append("&sslkey=").append(clientKey);
            log.info("PostgreSQL: external mode, mTLS enabled (client certificate presented)");
        } else {
            log.info("PostgreSQL: external mode, server-authenticated TLS only");
        }

        log.info("PostgreSQL: external mode, sslmode=verify-full channelBinding=require host={} db={} user={}",
                host, database, user);
        return url.toString();
    }

    /**
     * Reads connection settings from the environment and builds the URL.
     * Used by {@link DatabaseConfig} so the effective mode is visible in one place.
     */
    public static String fromEnv(Mode mode, String fallbackUrl) {
        String host = System.getenv("MAILCTL_DB_HOST");
        String port = System.getenv("MAILCTL_DB_PORT");
        String name = System.getenv("MAILCTL_DB_NAME");
        String user = System.getenv("MAILCTL_DB_USER");
        String sslMode = System.getenv("MAILCTL_DB_SSL_MODE");

        if (isBlank(host)) {
            // Nothing configured: honour whatever URL was supplied (config.yaml).
            return fallbackUrl;
        }

        return buildUrl(mode, host,
                isBlank(port) ? 5432 : Integer.parseInt(port.trim()),
                isBlank(name) ? "mailctl" : name,
                isBlank(user) ? "mailctl" : user,
                isBlank(sslMode) ? (mode == Mode.EXTERNAL ? "verify-full" : "disable") : sslMode);
    }

    /**
     * The database coordinates a NON-Java client needs.
     *
     * <p>Dovecot's PostgreSQL driver is not JDBC: it speaks libpq, so it needs
     * the host, port, database and role as separate values plus a connection
     * string it can hand to {@code PQconnectStart}. This record is that
     * description.</p>
     *
     * @param host       database host, as reachable from the client container
     * @param port       database port
     * @param database   database name
     * @param user       database role
     * @param password   role password
     * @param mode       local or external
     * @param sslMode    requested sslmode; external mode uses verify-full
     * @param sslRootCert CA that signed the server certificate, if any
     */
    public record DbEndpoint(
        String host,
        int port,
        String database,
        String user,
        String password,
        Mode mode,
        String sslMode,
        String sslRootCert
    ) {}

    /**
     * Resolves the database coordinates for a non-Java client.
     *
     * <p>The environment wins over the configured JDBC URL, exactly as
     * {@link #fromEnv(Mode, String)} does for JDBC, because that is what makes
     * the local and the external mode work: in local mode the host is the
     * Compose service name, in external mode it is the remote host.</p>
     *
     * <p>The result is a plain host/port/database triple, never the JDBC URL:
     * a JDBC URL is meaningless to libpq, and a client that is a sibling
     * container must not be pointed at {@code localhost}.</p>
     *
     * @param mode            local or external
     * @param fallbackUrl     configured JDBC URL, used when the environment
     *                         does not name a host
     * @param fallbackUser    configured role
     * @param fallbackPassword configured password
     */
    public static DbEndpoint endpointFromEnv(Mode mode, String fallbackUrl,
                                              String fallbackUser, String fallbackPassword) {
        String host = getenv("MAILCTL_DB_HOST");
        String port = getenv("MAILCTL_DB_PORT");
        String name = getenv("MAILCTL_DB_NAME");
        String user = getenv("MAILCTL_DB_USER");
        String password = getenv("MAILCTL_DB_PASSWORD");
        String sslMode = getenv("MAILCTL_DB_SSL_MODE");
        String rootCert = getenv("MAILCTL_DB_SSL_ROOT_CERT");

        if (isBlank(host)) {
            // Nothing configured: fall back to whatever the JDBC URL says.
            String[] parsed = parseJdbcUrl(fallbackUrl);
            host = parsed[0];
            port = parsed[1];
            name = parsed[2];
        }
        if (isBlank(user)) {
            user = fallbackUser;
        }
        if (isBlank(password)) {
            password = fallbackPassword;
        }
        if (isBlank(sslMode)) {
            sslMode = (mode == Mode.EXTERNAL) ? "verify-full" : "disable";
        }

        return new DbEndpoint(
                host,
                isBlank(port) ? 5432 : Integer.parseInt(port.trim()),
                isBlank(name) ? "mailctl" : name,
                isBlank(user) ? "mailctl" : user,
                password == null ? "" : password,
                mode,
                sslMode.trim(),
                rootCert);
    }

    /**
     * Renders a libpq connection string.
     *
     * <p>libpq keyword syntax, not a JDBC URL: {@code host=... port=...
     * dbname=... user=... password=...}. Values are single-quoted so a
     * password with a space or a quote cannot break the string apart.</p>
     *
     * <p>In external mode the string carries {@code sslmode=verify-full} and
     * the CA that signed the server certificate, so the client verifies the
     * server identity. The JDBC-only {@code channelBinding=require} has no
     * libpq spelling in every libpq version, so it is not emitted here; the
     * connection is still TLS-verified and the role still authenticates with
     * its password (or SCRAM).</p>
     */
    public static String libpqConnectString(DbEndpoint endpoint) {
        StringBuilder sb = new StringBuilder();
        sb.append("host=").append(quoteLibpq(endpoint.host()))
          .append(" port=").append(endpoint.port())
          .append(" dbname=").append(quoteLibpq(endpoint.database()))
          .append(" user=").append(quoteLibpq(endpoint.user()));
        if (endpoint.password() != null && !endpoint.password().isEmpty()) {
            sb.append(" password=").append(quoteLibpq(endpoint.password()));
        }
        if (endpoint.mode() == Mode.EXTERNAL) {
            sb.append(" sslmode=verify-full");
            if (!isBlank(endpoint.sslRootCert())) {
                sb.append(" sslrootcert=").append(quoteLibpq(endpoint.sslRootCert()));
            }
        }
        return sb.toString();
    }

    /** Splits a JDBC URL into host, port and database name. */
    private static String[] parseJdbcUrl(String url) {
        String host = "localhost";
        String port = "5432";
        String database = "mailctl";
        if (url == null || url.isBlank()) {
            return new String[]{host, port, database};
        }
        try {
            String rest = url.substring(url.indexOf("//") + 2);
            String query = rest.contains("?") ? rest.substring(rest.indexOf("?")) : "";
            rest = rest.contains("?") ? rest.substring(0, rest.indexOf("?")) : rest;
            String db = "";
            int slash = rest.indexOf('/');
            if (slash >= 0) {
                db = rest.substring(slash + 1);
                rest = rest.substring(0, slash);
            }
            int colon = rest.lastIndexOf(':');
            if (colon > 0) {
                port = rest.substring(colon + 1);
                rest = rest.substring(0, colon);
            }
            if (!rest.isBlank()) {
                host = rest;
            }
            if (!db.isBlank()) {
                database = db;
            }
        } catch (RuntimeException e) {
            log.warn("Could not parse JDBC URL, using defaults: {}", url);
        }
        return new String[]{host, port, database};
    }

    /** Single-quotes a libpq connection-string value. */
    private static String quoteLibpq(String value) {
        String v = value == null ? "" : value;
        return "'" + v.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String getenv(String key) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /** Reports the mode as a short string for logs and the doctor command. */
    public static String describe(Mode mode) {
        return mode == Mode.LOCAL
                ? "local (bundled postgres container, no TLS, unpublished port)"
                : "external (sslmode=verify-full, channelBinding=require)";
    }

    /** Validates that a JDBC URL actually carries the mandated TLS parameters. */
    public static boolean urlIsHardened(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("sslmode=verify-full") && lower.contains("channelbinding=require");
    }

    /** Best-effort read of the hostname from a JDBC URL, for diagnostics. */
    public static String hostOf(String jdbcUrl) {
        try {
            String withoutScheme = jdbcUrl.substring(jdbcUrl.indexOf("//") + 2);
            int slash = withoutScheme.indexOf('/');
            String authority = slash >= 0 ? withoutScheme.substring(0, slash) : withoutScheme;
            int q = authority.indexOf('?');
            if (q >= 0) {
                authority = authority.substring(0, q);
            }
            int colon = authority.lastIndexOf(':');
            return colon > 0 ? authority.substring(0, colon) : authority;
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static void requirePresent(String value, String label) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Missing " + label);
        }
    }

    /**
     * Fails fast when TLS material is missing. Without this the failure surfaces
     * later as an opaque SSL exception from inside the driver.
     */
    private static void requireReadable(String path, String label) {
        Path p = Paths.get(path);
        if (!Files.exists(p)) {
            throw new IllegalArgumentException(label + " not found: " + path);
        }
        if (!Files.isReadable(p)) {
            throw new IllegalArgumentException(label + " is not readable: " + path);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}