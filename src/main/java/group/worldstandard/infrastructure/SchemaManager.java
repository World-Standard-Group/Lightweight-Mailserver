package group.worldstandard.infrastructure;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.RecordComponent;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.*;

/**
 * Flexible schema management that generates migrations from Java entity classes.
 * Works alongside Flyway's versioned SQL migrations.
 */
public class SchemaManager {
    private static final Logger log = LoggerFactory.getLogger(SchemaManager.class);

    private final DataSource dataSource;
    private final Flyway flyway;
    private final Set<Class<?>> entityClasses;

    public SchemaManager(DataSource dataSource, Set<Class<?>> entityClasses) {
        this.dataSource = dataSource;
        this.entityClasses = entityClasses;
        
        this.flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("db/migration")
            .baselineOnMigrate(true)
            .load();
    }

    /**
     * Initialize database - runs Flyway migrations
     */
    public void initialize() {
        log.info("Running database migrations...");
        flyway.migrate();
        log.info("Database migrations completed");
    }

    /**
     * Generate DDL from entity classes (for initial setup or review)
     */
    public String generateDdl() {
        StringBuilder ddl = new StringBuilder();
        ddl.append("-- Generated from entity classes\n");
        ddl.append("-- DO NOT EDIT MANUALLY - modify entity classes instead\n\n");

        for (Class<?> entityClass : entityClasses) {
            if (!entityClass.isRecord()) {
                log.warn("Entity class {} is not a record, skipping", entityClass.getName());
                continue;
            }
            
            ddl.append(generateTableDdl(entityClass));
            ddl.append("\n");
        }

        return ddl.toString();
    }

    /**
     * Generate CREATE TABLE DDL for a record class
     */
    private String generateTableDdl(Class<?> recordClass) {
        String tableName = toSnakeCase(recordClass.getSimpleName());
        StringBuilder sb = new StringBuilder();
        
        sb.append("-- Table: ").append(tableName).append("\n");
        sb.append("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" (\n");

        List<String> columns = new ArrayList<>();
        List<String> constraints = new ArrayList<>();

        for (RecordComponent component : recordClass.getRecordComponents()) {
            String columnName = toSnakeCase(component.getName());
            String columnType = mapJavaTypeToSql(component.getType());
            
            // Check for primary key
            if (component.getName().equalsIgnoreCase("id")) {
                columns.add(columnName + " " + columnType + " PRIMARY KEY");
            } else if (component.getName().equalsIgnoreCase("domainId") || component.getName().endsWith("Id")) {
                // Foreign key reference
                String refTable = toSnakeCase(component.getName().replace("Id", "").replace("id", ""));
                if ("domain".equals(refTable)) refTable = "domains";
                columns.add(columnName + " " + columnType + " NOT NULL REFERENCES " + refTable + "(id) ON DELETE CASCADE");
            } else if (component.getName().contains("email") || component.getName().contains("alias") || component.getName().contains("selector")) {
                columns.add(columnName + " " + columnType + " NOT NULL UNIQUE");
            } else if (component.getName().contains("status")) {
                columns.add(columnName + " " + columnType + " NOT NULL DEFAULT 'ACTIVE'");
            } else if (component.getName().contains("CreatedAt") || component.getName().contains("UpdatedAt") || 
                       component.getName().contains("At") || component.getName().contains("at")) {
                columns.add(columnName + " " + columnType + " NOT NULL DEFAULT NOW()");
            } else {
                columns.add(columnName + " " + columnType);
            }
        }

        sb.append("    ").append(String.join(",\n    ", columns)).append("\n");
        sb.append(");\n\n");

        // Add indexes
        sb.append(generateIndexes(tableName, recordClass)).append("\n");

        return sb.toString();
    }

    private String generateIndexes(String tableName, Class<?> recordClass) {
        StringBuilder sb = new StringBuilder();
        
        for (RecordComponent component : recordClass.getRecordComponents()) {
            String columnName = toSnakeCase(component.getName());
            
            // Add index for foreign keys
            if (component.getName().equalsIgnoreCase("domainId") || component.getName().endsWith("Id")) {
                sb.append("CREATE INDEX IF NOT EXISTS idx_")
                  .append(tableName).append("_").append(columnName)
                  .append(" ON ").append(tableName).append("(").append(columnName).append(");\n");
            }
            // Add index for email/alias/selector (unique already covered)
            else if (component.getName().contains("email") || component.getName().contains("alias") 
                     || component.getName().contains("selector")) {
                sb.append("CREATE INDEX IF NOT EXISTS idx_")
                  .append(tableName).append("_").append(columnName)
                  .append(" ON ").append(tableName).append("(").append(columnName).append(");\n");
            }
            // Add index for status
            else if (component.getName().contains("status")) {
                sb.append("CREATE INDEX IF NOT EXISTS idx_")
                  .append(tableName).append("_status")
                  .append(" ON ").append(tableName).append("(status);\n");
            }
        }
        
        return sb.toString();
    }

    /**
     * Map Java type to PostgreSQL type
     */
    private String mapJavaTypeToSql(Class<?> javaType) {
        if (javaType == UUID.class) return "UUID";
        if (javaType == String.class) return "TEXT";
        if (javaType == Integer.class || javaType == int.class) return "INTEGER";
        if (javaType == Long.class || javaType == long.class) return "BIGINT";
        if (javaType == Boolean.class || javaType == boolean.class) return "BOOLEAN";
        if (javaType == java.time.Instant.class) return "TIMESTAMPTZ";
        if (javaType == java.time.LocalDateTime.class) return "TIMESTAMP";
        if (javaType == java.time.LocalDate.class) return "DATE";
        if (javaType.isEnum()) return "VARCHAR(20)";
        
        return "TEXT";
    }

    /**
     * Convert camelCase to snake_case
     */
    private String toSnakeCase(String camelCase) {
        return camelCase
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .toLowerCase();
    }

    /**
     * Check if table exists
     */
    public boolean tableExists(String tableName) {
        try (var conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (var rs = meta.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (SQLException e) {
            log.error("Failed to check table existence: {}", tableName, e);
            return false;
        }
    }

    /**
     * Get current migration version
     */
    public String getCurrentVersion() {
        var info = flyway.info();
        var current = info.current();
        return current != null ? current.getVersion().toString() : "0";
    }

    /**
     * Validate current schema against entities
     */
    public ValidationResult validateSchema() {
        List<String> issues = new ArrayList<>();
        
        for (Class<?> entityClass : entityClasses) {
            String tableName = toSnakeCase(entityClass.getSimpleName());
            
            if (!tableExists(tableName)) {
                issues.add("Missing table: " + tableName);
                continue;
            }
            
            // Check columns
            try (var conn = dataSource.getConnection()) {
                DatabaseMetaData meta = conn.getMetaData();
                Set<String> existingColumns = new HashSet<>();
                
                try (var rs = meta.getColumns(null, null, tableName.toUpperCase(), null)) {
                    while (rs.next()) {
                        existingColumns.add(rs.getString("COLUMN_NAME").toLowerCase());
                    }
                }
                
                for (RecordComponent component : entityClass.getRecordComponents()) {
                    String columnName = toSnakeCase(component.getName()).toUpperCase();
                    if (!existingColumns.contains(columnName)) {
                        issues.add("Missing column " + tableName + "." + columnName);
                    }
                }
            } catch (SQLException e) {
                issues.add("Failed to validate " + tableName + ": " + e.getMessage());
            }
        }
        
        return new ValidationResult(issues.isEmpty(), issues);
    }

    public record ValidationResult(boolean valid, List<String> issues) {}

    /**
     * Export current schema as DDL (for generating migration scripts)
     */
    public String exportSchemaAsDdl() {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Schema export from database\n");
        sb.append("-- Generated at: ").append(java.time.Instant.now()).append("\n\n");
        
        try (var conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            
            try (var rs = meta.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    sb.append(exportTableDdl(conn, tableName)).append("\n");
                }
            }
        } catch (SQLException e) {
            log.error("Failed to export schema", e);
        }
        
        return sb.toString();
    }

    private String exportTableDdl(java.sql.Connection conn, String tableName) throws SQLException {
        StringBuilder sb = new StringBuilder();
        
        // Get columns
        DatabaseMetaData meta = conn.getMetaData();
        sb.append("-- Table: ").append(tableName).append("\n");
        sb.append("CREATE TABLE ").append(tableName).append(" (\n");
        
        List<String> columns = new ArrayList<>();
        try (var rs = meta.getColumns(null, null, tableName.toUpperCase(), null)) {
            while (rs.next()) {
                String colName = rs.getString("COLUMN_NAME");
                String colType = rs.getString("TYPE_NAME");
                int colSize = rs.getInt("COLUMN_SIZE");
                int nullable = rs.getInt("NULLABLE");
                
                String colDef = colName + " " + colType;
                if (colSize > 0 && !colType.equals("UUID") && !colType.equals("TEXT")) {
                    colDef += "(" + colSize + ")";
                }
                if (nullable == 0) { // DatabaseMetaData.columnNoNulls
                    colDef += " NOT NULL";
                }
                columns.add(colDef);
            }
        }
        
        sb.append("    ").append(String.join(",\n    ", columns)).append("\n");
        sb.append(");\n\n");
        
        // Indexes
        try (var rs = meta.getIndexInfo(null, null, tableName.toUpperCase(), false, false)) {
            Set<String> seenIndexes = new HashSet<>();
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName != null && !seenIndexes.contains(indexName) && !indexName.startsWith("pk_")) {
                    seenIndexes.add(indexName);
                    sb.append("-- Index: ").append(indexName).append("\n");
                }
            }
        }
        
        return sb.toString();
    }
}