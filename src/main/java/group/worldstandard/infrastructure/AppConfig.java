package group.worldstandard.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    private static final ObjectMapper YAML_MAPPER = new ObjectMapper();
    private static final Path DEFAULT_CONFIG_PATH = Paths.get("/etc/mailctl/config.yaml");

    public static AppConfig load() throws IOException {
        return load(DEFAULT_CONFIG_PATH);
    }

    public static AppConfig load(Path path) throws IOException {
        if (Files.exists(path)) {
            return YAML_MAPPER.readValue(path.toFile(), AppConfig.class);
        }
        return new AppConfig(
            new DatabaseConfig(),
            new MailConfig(),
            new SecurityConfig(),
            new PathsConfig(),
            new ServicesConfig()
        );
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), this);
    }

    public void save() throws IOException {
        save(DEFAULT_CONFIG_PATH);
    }
}