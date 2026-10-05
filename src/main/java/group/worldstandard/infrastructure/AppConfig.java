package group.worldstandard.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

    /**
     * Configuration model, loaded from {@code config.yaml} and then overlaid with
     *  * {@code MAILCTL_*} environment variables.
     *  *
     *  * <p>The YAML reader is a {@code YAMLMapper}: a plain databind {@code ObjectMapper}
     *  * cannot parse YAML and throws on the first key. Environment variables win over
     *  * the file so a container can be retargeted without editing anything, which is
     *  * how Compose selects the local or external database.</p>
     *  *
     *  * @see DbConnection
     */
public record AppConfig(
    @JsonProperty("database") DatabaseConfig database,
    @JsonProperty("mail") MailConfig mail,
    @JsonProperty("security") SecurityConfig security,
    @JsonProperty("paths") PathsConfig paths,
    @JsonProperty("services") ServicesConfig services
) {
    public record DatabaseConfig(
        @JsonProperty("url") String url,
        @JsonProperty("user") String user,
        @JsonProperty("password") String password
    ) {
        public DatabaseConfig() {
            this("jdbc:postgresql://localhost:5432/mailctl", "mailctl", "mailctl");
        }
    }

    public record MailConfig(
        @JsonProperty("hostname") String hostname,
        @JsonProperty("domain") String domain,
        @JsonProperty("postmaster") String postmaster,
        @JsonProperty("tlsCert") String tlsCert,
        @JsonProperty("tlsKey") String tlsKey
    ) {
        public MailConfig() {
            this("mail.example.com", "example.com", "postmaster@example.com",
                 "/etc/ssl/certs/mailcert.pem", "/etc/ssl/private/mailkey.pem");
        }
    }

    public record SecurityConfig(
        @JsonProperty("dkimKeyDirectory") String dkimKeyDirectory,
        @JsonProperty("dkimDefaultSelector") String dkimDefaultSelector,
        @JsonProperty("dkimDefaultKeySize") int dkimDefaultKeySize,
        @JsonProperty("dkimDefaultAlgorithm") String dkimDefaultAlgorithm
    ) {
        public SecurityConfig() {
            this("/etc/rspamd/dkim", "mail", 2048, "rsa2048");
        }
    }

    public record PathsConfig(
        @JsonProperty("mailBase") String mailBase,
        @JsonProperty("postfixConfigDir") String postfixConfigDir,
        @JsonProperty("dovecotConfigDir") String dovecotConfigDir,
        @JsonProperty("rspamdConfigDir") String rspamdConfigDir,
        @JsonProperty("backupDir") String backupDir
    ) {
        public PathsConfig() {
            this("/var/mail", "/etc/postfix", "/etc/dovecot", "/etc/rspamd", "/var/backups/mailctl");
        }
    }

    public record ServicesConfig(
        @JsonProperty("postfix") ServiceConfig postfix,
        @JsonProperty("dovecot") ServiceConfig dovecot,
        @JsonProperty("rspamd") ServiceConfig rspamd,
        @JsonProperty("postgresql") ServiceConfig postgresql
    ) {
        public ServicesConfig() {
            this(new ServiceConfig("postfix", true),
                 new ServiceConfig("dovecot", true),
                 new ServiceConfig("rspamd", true),
                 new ServiceConfig("postgresql", true));
        }
    }

    public record ServiceConfig(
        @JsonProperty("name") String name,
        @JsonProperty("enabled") boolean enabled
    ) {
        public ServiceConfig() {
            this("", true);
        }
    }

    /**
     * Reads and writes config.yaml. A plain databind ObjectMapper cannot parse
     * YAML — it throws "Unrecognized token 'database'" on the very first key.
     */
    private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder()
            .build();
    private static final Path DEFAULT_CONFIG_PATH = Paths.get("/etc/mailctl/config.yaml");

    /**
     * Config file path. MAILCTL_CONFIG wins so the Docker image can be pointed
     * at a mounted config without rewriting the command line.
     */
    public static Path defaultConfigPath() {
        String fromEnv = System.getenv("MAILCTL_CONFIG");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Paths.get(fromEnv);
        }
        return DEFAULT_CONFIG_PATH;
    }

    public static AppConfig load() throws IOException {
        return load(defaultConfigPath());
    }

    public static AppConfig load(Path path) throws IOException {
        if (Files.exists(path)) {
            AppConfig cfg = YAML_MAPPER.readValue(path.toFile(), AppConfig.class);
            return cfg.withEnvOverrides();
        }
        return new AppConfig(
            new DatabaseConfig(),
            new MailConfig(),
            new SecurityConfig(),
            new PathsConfig(),
            new ServicesConfig()
        ).withEnvOverrides();
    }

    /**
     * Docker passes configuration as environment variables; they win over the
     * file so a container can be retargeted without editing config.yaml.
     * Each variable is optional — unset means "keep the file value".
     */
    private AppConfig withEnvOverrides() {
        return new AppConfig(
            new DatabaseConfig(
                // MAILCTL_DB_HOST/PORT/NAME drive local vs external selection
                // in DbConnection; MAILCTL_DB_URL remains the fallback.
                env("MAILCTL_DB_URL", database.url()),
                env("MAILCTL_DB_USER", database.user()),
                env("MAILCTL_DB_PASSWORD", database.password())
            ),
            new MailConfig(
                env("MAILCTL_MAIL_HOSTNAME", mail.hostname()),
                env("MAILCTL_MAIL_DOMAIN", mail.domain()),
                env("MAILCTL_MAIL_POSTMASTER", mail.postmaster()),
                env("MAILCTL_MAIL_TLS_CERT", mail.tlsCert()),
                env("MAILCTL_MAIL_TLS_KEY", mail.tlsKey())
            ),
            new SecurityConfig(
                env("MAILCTL_DKIM_DIR", security.dkimKeyDirectory()),
                env("MAILCTL_DKIM_SELECTOR", security.dkimDefaultSelector()),
                Integer.parseInt(env("MAILCTL_DKIM_KEY_SIZE", String.valueOf(security.dkimDefaultKeySize()))),
                env("MAILCTL_DKIM_ALGORITHM", security.dkimDefaultAlgorithm())
            ),
            new PathsConfig(
                env("MAILCTL_MAIL_BASE", paths.mailBase()),
                env("MAILCTL_POSTFIX_DIR", paths.postfixConfigDir()),
                env("MAILCTL_DOVECOT_DIR", paths.dovecotConfigDir()),
                env("MAILCTL_RSPAMD_DIR", paths.rspamdConfigDir()),
                env("MAILCTL_BACKUP_DIR", paths.backupDir())
            ),
            services
        );
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }

    public void save(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), this);
    }

    public void save() throws IOException {
        save(defaultConfigPath());
    }
}