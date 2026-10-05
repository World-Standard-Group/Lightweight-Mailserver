package group.worldstandard.infrastructure;

import group.worldstandard.domain.Domain;
import group.worldstandard.domain.DomainRepository;
import group.worldstandard.identity.User;
import group.worldstandard.identity.UserRepository;
import group.worldstandard.mail.DovecotConfig;
import group.worldstandard.mail.PostfixConfig;
import group.worldstandard.mail.RspamdConfig;
import group.worldstandard.routing.Alias;
import group.worldstandard.routing.AliasRepository;
import group.worldstandard.security.DkimKey;
import group.worldstandard.security.DkimKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

    /**
     * Turns database state into Postfix, Dovecot and Rspamd configuration.
     *  *
     *  * <p>Two distinct responsibilities:</p>
     *  * <ul>
     *  *   <li>{@link #generateAll(Path)} renders files into a staging directory. This is
     *  *       the path that works.</li>
     *  *   <li>{@link #deployConfig(Path)} validates, installs and reloads. It shells out
     *  *       to {@code postfix}, {@code dovecot} and {@code rspamadm}, which do not exist
     *  *       in the mailctl image, so it cannot run under Docker. Use
     *  *       {@code scripts/apply-config.sh} instead.</li>
     *  * </ul>
     *  *
     *  * <p>Generated files are installed through {@link FileManager#atomicReplace} so a
     *  * service reading the directory never observes a partially written file.</p>
     */
public class ConfigGenerator {
    private static final Logger log = LoggerFactory.getLogger(ConfigGenerator.class);

    private final DomainRepository domainRepository;
    private final UserRepository userRepository;
    private final AliasRepository aliasRepository;
    private final DkimKeyRepository dkimKeyRepository;
    private final FileManager fileManager;
    private final AppConfig appConfig;

    public ConfigGenerator(
            DomainRepository domainRepository,
            UserRepository userRepository,
            AliasRepository aliasRepository,
            DkimKeyRepository dkimKeyRepository,
            FileManager fileManager,
            AppConfig appConfig) {
        this.domainRepository = domainRepository;
        this.userRepository = userRepository;
        this.aliasRepository = aliasRepository;
        this.dkimKeyRepository = dkimKeyRepository;
        this.fileManager = fileManager;
        this.appConfig = appConfig;
    }

    public void generateAll(Path outputDir) throws IOException {
        log.info("Generating all configuration to: {}", outputDir);
        
        Files.createDirectories(outputDir);
        
        // Generate Postfix config
        PostfixConfig postfixConfig = buildPostfixConfig();
        fileManager.writeFile(outputDir.resolve("main.cf"), postfixConfig.toMainCf());
        // master.cf is required by `postfix check` and defines the submission
        // listeners, so it must be generated alongside main.cf.
        fileManager.writeFile(outputDir.resolve("master.cf"),
                postfixConfig.toMasterCf().replace("%%MAIL_BASE%%", appConfig.paths().mailBase()));
        fileManager.writeFile(outputDir.resolve("virtual_alias_maps"), postfixConfig.toVirtualAliasMaps());
        fileManager.writeFile(outputDir.resolve("virtual_mailbox_maps"), postfixConfig.toVirtualMailboxMaps());
        fileManager.writeFile(outputDir.resolve("virtual_mailbox_domains"), postfixConfig.toVirtualMailboxDomains());
        // Maps each authenticated sender to itself, so
        // reject_sender_login_mismatch can reject a client trying to send as a
        // different local address.
        fileManager.writeFile(outputDir.resolve("sender_login_maps"), buildSenderLoginMaps());
        
        // Generate Dovecot config
        DovecotConfig dovecotConfig = buildDovecotConfig();
        fileManager.writeFile(outputDir.resolve("dovecot.conf"), dovecotConfig.toDovecotConf());
        fileManager.writeFile(outputDir.resolve("dovecot-sql.conf.ext"), 
            dovecotConfig.toSqlConf(appConfig.database().url(), appConfig.database().user(), appConfig.database().password()));
        
        // Generate Rspamd config
        RspamdConfig rspamdConfig = buildRspamdConfig();
        fileManager.writeFile(outputDir.resolve("dkim_signing.conf"), rspamdConfig.toDkimSigningConf());
        fileManager.writeFile(outputDir.resolve("options.conf"), rspamdConfig.toOptionsConf());
        
        // Generate DKIM maps
        List<DkimKey> activeKeys = dkimKeyRepository.findByDomainId(null); // Would need to iterate all domains
        // For now, get all active keys across all domains
        List<DkimKey> allActiveKeys = new ArrayList<>();
        for (Domain domain : domainRepository.findAll()) {
            allActiveKeys.addAll(dkimKeyRepository.findActiveByDomainId(domain.id()));
        }
        
        fileManager.writeFile(outputDir.resolve("dkim_selectors.map"), rspamdConfig.toDkimSelectorMap(allActiveKeys));
        fileManager.writeFile(outputDir.resolve("dkim_domains.map"), rspamdConfig.toDkimDomainMap(allActiveKeys));
        fileManager.writeFile(outputDir.resolve("dkim_keys.map"), rspamdConfig.toDkimKeyMap(allActiveKeys));
        
        // Write DKIM private keys
        Path dkimDir = outputDir.resolve("dkim");
        Files.createDirectories(dkimDir);
        for (DkimKey key : allActiveKeys) {
            fileManager.writeFile(dkimDir.resolve(key.selector() + ".private"), key.privateKeyPem());
        }
        
        log.info("Configuration generation complete");
    }

    public void deployConfig(Path sourceDir) throws IOException, InterruptedException {
        log.info("Deploying configuration from: {}", sourceDir);
        
        ProcessRunner runner = new ProcessRunner();
        
        // Validate Postfix config
        Path tempPostfixDir = sourceDir.resolve("postfix");
        Files.createDirectories(tempPostfixDir);
        Files.copy(sourceDir.resolve("main.cf"), tempPostfixDir.resolve("main.cf"));
        Files.copy(sourceDir.resolve("master.cf"), tempPostfixDir.resolve("master.cf"));
        Files.copy(sourceDir.resolve("virtual_alias_maps"), tempPostfixDir.resolve("virtual_alias_maps"));
        Files.copy(sourceDir.resolve("virtual_mailbox_maps"), tempPostfixDir.resolve("virtual_mailbox_maps"));
        Files.copy(sourceDir.resolve("virtual_mailbox_domains"), tempPostfixDir.resolve("virtual_mailbox_domains"));
        
        // Run postfix check
        ProcessBuilder pb = new ProcessBuilder("postfix", "-c", tempPostfixDir.toString(), "check");
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Postfix configuration validation failed");
        }
        
        // Validate Dovecot config
        pb = new ProcessBuilder("dovecot", "-c", sourceDir.resolve("dovecot.conf").toString(), "-n");
        process = pb.start();
        exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Dovecot configuration validation failed");
        }
        
        // Validate Rspamd config
        pb = new ProcessBuilder("rspamadm", "configtest");
        process = pb.start();
        exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("Rspamd configuration validation failed");
        }
        
        // Atomic deploy
        deployPostfix(sourceDir);
        deployDovecot(sourceDir);
        deployRspamd(sourceDir);
        
        // Reload services
        runner.requireSuccess(ProcessRunner.Command.POSTFIX_RELOAD);
        runner.requireSuccess(ProcessRunner.Command.DOVECOT_RELOAD);
        runner.requireSuccess(ProcessRunner.Command.RSPAMD_RELOAD);
        
        log.info("Configuration deployed successfully");
    }

    private void deployPostfix(Path sourceDir) throws IOException {
        Path targetDir = Paths.get(appConfig.paths().postfixConfigDir());
        fileManager.backupFile(targetDir.resolve("main.cf"));
        fileManager.backupFile(targetDir.resolve("master.cf"));
        fileManager.atomicReplace(sourceDir.resolve("main.cf"), targetDir.resolve("main.cf"));
        fileManager.atomicReplace(sourceDir.resolve("master.cf"), targetDir.resolve("master.cf"));
        fileManager.atomicReplace(sourceDir.resolve("virtual_alias_maps"), targetDir.resolve("virtual_alias_maps"));
        fileManager.atomicReplace(sourceDir.resolve("virtual_mailbox_maps"), targetDir.resolve("virtual_mailbox_maps"));
        fileManager.atomicReplace(sourceDir.resolve("virtual_mailbox_domains"), targetDir.resolve("virtual_mailbox_domains"));
        
        // Run postmap on map files
        new ProcessRunner().run(new String[]{"postmap", targetDir.resolve("virtual_alias_maps").toString()});
        new ProcessRunner().run(new String[]{"postmap", targetDir.resolve("virtual_mailbox_maps").toString()});
        new ProcessRunner().run(new String[]{"postmap", targetDir.resolve("virtual_mailbox_domains").toString()});
        // sender_login_maps gates sender spoofing, so it must be compiled too.
        new ProcessRunner().run(new String[]{"postmap", targetDir.resolve("sender_login_maps").toString()});
    }

    private void deployDovecot(Path sourceDir) throws IOException {
        Path targetDir = Paths.get(appConfig.paths().dovecotConfigDir());
        fileManager.backupFile(targetDir.resolve("dovecot.conf"));
        fileManager.atomicReplace(sourceDir.resolve("dovecot.conf"), targetDir.resolve("dovecot.conf"));
        fileManager.atomicReplace(sourceDir.resolve("dovecot-sql.conf.ext"), targetDir.resolve("dovecot-sql.conf.ext"));
    }

    private void deployRspamd(Path sourceDir) throws IOException {
        Path targetDir = Paths.get(appConfig.paths().rspamdConfigDir());
        Files.createDirectories(targetDir.resolve("local.d"));
        
        fileManager.atomicReplace(sourceDir.resolve("dkim_signing.conf"), targetDir.resolve("local.d").resolve("dkim_signing.conf"));
        fileManager.atomicReplace(sourceDir.resolve("options.conf"), targetDir.resolve("local.d").resolve("options.conf"));
        fileManager.atomicReplace(sourceDir.resolve("dkim_selectors.map"), targetDir.resolve("dkim_selectors.map"));
        fileManager.atomicReplace(sourceDir.resolve("dkim_domains.map"), targetDir.resolve("dkim_domains.map"));
        fileManager.atomicReplace(sourceDir.resolve("dkim_keys.map"), targetDir.resolve("dkim_keys.map"));
        
        // Deploy DKIM private keys
        Path dkimTargetDir = Paths.get(appConfig.security().dkimKeyDirectory());
        Files.createDirectories(dkimTargetDir);
        Path sourceDkimDir = sourceDir.resolve("dkim");
        if (Files.exists(sourceDkimDir)) {
            Files.list(sourceDkimDir).forEach(src -> {
                try {
                    fileManager.atomicReplace(src, dkimTargetDir.resolve(src.getFileName()));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to deploy DKIM key: " + src, e);
                }
            });
        }
    }

    /**
     * Builds the {@code sender_login_maps} table: every active user, whether
     * storage or forwarding-only, may send as its own address only.
     */
    private String buildSenderLoginMaps() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Sender login maps - Generated by mailctl\n");
        sb.append("# Authenticated senders may only send as their own address.\n");
        for (User user : userRepository.findAll()) {
            if (user.status() != User.Status.ACTIVE) {
                continue;
            }
            sb.append(user.email()).append("\t").append(user.email()).append("\n");
        }
        return sb.toString();
    }

    private PostfixConfig buildPostfixConfig() {
        List<Domain> domains = domainRepository.findByStatus(Domain.Status.ACTIVE);
        List<User> users = userRepository.findAll().stream()
            .filter(u -> u.status() == User.Status.ACTIVE)
            .toList();
        List<Alias> aliases = aliasRepository.findAll();

        // Build virtual_alias_maps
        //
        // Two sources feed this map. Explicit aliases, and forwarding-only users,
        // whose mail is rewritten to external targets instead of being delivered
        // locally. Postfix evaluates virtual_alias_maps BEFORE
        // virtual_mailbox_maps, so a forwarding user is redirected without a
        // mailbox ever being consulted.
        Map<String, String> virtualAliasMaps = new HashMap<>();
        for (Alias alias : aliases) {
            virtualAliasMaps.put(alias.alias(), alias.targets());
        }
        for (User user : users) {
            if (user.isForwardingOnly()
                    && user.forwardTargets() != null
                    && !user.forwardTargets().isBlank()) {
                virtualAliasMaps.put(user.email(), user.forwardTargets());
            }
        }

        // Build virtual_mailbox_maps
        //
        // Storage users ONLY. A forwarding-only user must never appear here: if
        // they did, Postfix would treat them as a local delivery target and the
        // mail would be written to disk instead of forwarded.
        Map<String, String> virtualMailboxMaps = new HashMap<>();
        for (User user : users) {
            if (!user.storageEnabled()) {
                continue;
            }
            if (user.mailboxPath() == null || user.mailboxPath().isEmpty()) {
                continue;
            }
            // Postfix expects a path RELATIVE to virtual_mailbox_base, so strip
            // the configured base rather than a hardcoded literal.
            String base = appConfig.paths().mailBase();
            String relative = user.mailboxPath();
            if (base != null && !base.isBlank() && relative.startsWith(base)) {
                relative = relative.substring(base.length());
            }
            while (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            virtualMailboxMaps.put(user.email(), relative + "/");
        }
        
        // Build virtual_mailbox_domains
        Map<String, String> virtualMailboxDomains = new HashMap<>();
        for (Domain domain : domains) {
            virtualMailboxDomains.put(domain.name(), "OK");
        }
        
        return new PostfixConfig.Builder()
            .myHostname(appConfig.mail().hostname())
            .myDomain(appConfig.mail().domain())
            .virtualMailboxBase(appConfig.paths().mailBase())
            .virtualAliasMaps(virtualAliasMaps)
            .virtualMailboxMaps(virtualMailboxMaps)
            .virtualMailboxDomains(virtualMailboxDomains)
            .smtpdTlsCertFile(appConfig.mail().tlsCert())
            .smtpdTlsKeyFile(appConfig.mail().tlsKey())
            // Rspamd is a sibling container by default; override when the
            // milter lives elsewhere.
            .milterHost(System.getenv().getOrDefault("MAILCTL_RSPAMD_HOST", "rspamd"))
            .milterPort(Integer.parseInt(System.getenv().getOrDefault("MAILCTL_RSPAMD_MILTER_PORT", "11332")))
            .build();
    }

    private DovecotConfig buildDovecotConfig() {
        return new DovecotConfig.Builder()
            .mailLocation("maildir:" + appConfig.paths().mailBase() + "/%d/%n")
            .sslCert(appConfig.mail().tlsCert())
            .sslKey(appConfig.mail().tlsKey())
            .build();
    }

    private RspamdConfig buildRspamdConfig() {
        return new RspamdConfig.Builder()
            .dkimSelectorMap(appConfig.paths().rspamdConfigDir() + "/dkim_selectors.map")
            .dkimDomainMap(appConfig.paths().rspamdConfigDir() + "/dkim_domains.map")
            .dkimKeyMap(appConfig.paths().rspamdConfigDir() + "/dkim_keys.map")
            .build();
    }
}