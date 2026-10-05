package group.worldstandard.infrastructure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;

    /**
     * Owns the HikariCP pool and the schema manager.
     *  *
     *  * <p>Runs Flyway migrations in the constructor, which means constructing this
     *  * class requires a reachable database — that is why the CLI reports a database
     *  * warning instead of failing outright for {@code --help}.</p>
     *  *
     *  * <p>The connection URL is produced by {@link DbConnection}, which implements the
     *  * local/external policy; this class refuses to hand a non-hardened URL to Hikari
     *  * when external mode is selected.</p>
     */
public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private final String url;
    private final String username;
    private final String password;
    private final DbConnection.Mode mode;
    private final HikariDataSource dataSource;
    private final SchemaManager schemaManager;

    // Entity classes for schema generation
    private static final Set<Class<?>> ENTITY_CLASSES = Set.of(
        group.worldstandard.domain.Domain.class,
        group.worldstandard.identity.User.class,
        group.worldstandard.routing.Alias.class,
        group.worldstandard.security.DkimKey.class
    );

    /**
     * @param url base JDBC URL; TLS parameters for external mode are derived
     *            from MAILCTL_DB_* environment variables by {@link DbConnection}
     */
    public DatabaseConfig(String url, String username, String password) {
        DbConnection.Mode mode = DbConnection.Mode.fromEnv();
        this.url = DbConnection.fromEnv(mode, url);
        this.username = username;
        this.password = password;
        this.mode = mode;

        // Fail loudly rather than silently connecting in a downgraded mode.
        if (mode == DbConnection.Mode.EXTERNAL && !DbConnection.urlIsHardened(this.url)) {
            throw new IllegalStateException(
                    "external mode requires sslmode=verify-full and channelBinding=require; got: "
                    + this.url);
        }
        log.info("Database connection: {}", DbConnection.describe(mode));
        
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setLeakDetectionThreshold(60000);
        config.setPoolName("mailctl-pool");
        
        this.dataSource = new HikariDataSource(config);
        
        // Initialize schema manager
        this.schemaManager = new SchemaManager(dataSource, ENTITY_CLASSES);
        
        // Run migrations
        runMigrations();
    }

    public DatabaseConfig(Properties props) {
        this(
            props.getProperty("db.url", "jdbc:postgresql://localhost:5432/mailctl"),
            props.getProperty("db.user", "mailctl"),
            props.getProperty("db.password", "mailctl")
        );
    }

    public static DatabaseConfig fromConfigFile(Path configFile) throws IOException {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(configFile)) {
            props.load(reader);
        }
        return new DatabaseConfig(props);
    }

    private void runMigrations() {
        log.info("Running database migrations...");
        schemaManager.initialize();
        log.info("Database migrations completed");
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public void close() {
        dataSource.close();
    }

    public DbConnection.Mode getMode() {
        return mode;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Get the schema manager for advanced operations
     */
    public SchemaManager getSchemaManager() {
        return schemaManager;
    }
}