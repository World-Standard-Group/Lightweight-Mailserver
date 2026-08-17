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

public class DatabaseConfig {
    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    private final String url;
    private final String username;
    private final String password;
    private final HikariDataSource dataSource;
    private final SchemaManager schemaManager;

    // Entity classes for schema generation
    private static final Set<Class<?>> ENTITY_CLASSES = Set.of(
        group.worldstandard.domain.Domain.class,
        group.worldstandard.identity.User.class,
        group.worldstandard.routing.Alias.class,
        group.worldstandard.security.DkimKey.class
    );

    public DatabaseConfig(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
        
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