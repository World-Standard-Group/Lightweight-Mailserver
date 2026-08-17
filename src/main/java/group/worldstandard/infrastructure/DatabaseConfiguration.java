package group.worldstandard.infrastructure;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;

public class DatabaseConfiguration {

    private String dbUrl;

    private String dbUser;

    private String dbPassword;

    public DataSource dataSource() {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(dbUrl);
        ds.setUsername(dbUser);
        ds.setPassword(dbPassword);
        ds.setMaximumPoolSize(10);
        ds.setMinimumIdle(2);
        ds.setIdleTimeout(300000);
        ds.setConnectionTimeout(30000);
        ds.setMaxLifetime(1800000);
        ds.setLeakDetectionThreshold(60000);
        ds.setPoolName("mailctl-pool");
        return ds;
    }

    public Flyway flyway(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("db/migration")
            .baselineOnMigrate(true)
            .load();
        flyway.migrate();
        return flyway;
    }
}