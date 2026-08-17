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
        fileManager.writeFile(outputDir.resolve("virtual_alias_maps"), postfixConfig.toVirtualAliasMaps());
        fileManager.writeFile(outputDir.resolve("virtual_mailbox_maps"), postfixConfig.toVirtualMailboxMaps());
        fileManager.writeFile(outputDir.resolve("virtual_mailbox_domains"), postfixConfig.toVirtualMailboxDomains());
        
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
        fileManager.atomicReplace(sourceDir.resolve("main.cf"), targetDir.resolve("main.cf"));
        fileManager.atomicReplace(sourceDir.resolve("virtual_alias_maps"), targetDir.resolve("virtual_alias_maps"));
        fileManager.atomicReplace(sourceDir.resolve("virtual_mailbox_maps"), targetDir.resolve("virtual_mailbox_maps"));
        fileManager.atomicReplace(sourceDir.resolve("virtual_mailbox_domains"), targetDir.resolve("virtual_mailbox_domains"));
        
        // Run postmap on map files
        new ProcessRunner().run(new String[]{"postmap", targetDir.resolve("virtual_alias_maps").toString()});
        new ProcessRunner().run(new String[]{"postmap", targetDir.resolve("virtual_mailbox_maps").toString()});
        new ProcessRunner().run(new String[]{"postmap", targetDir.resolve("virtual_mailbox_domains").toString()});
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

    private PostfixConfig buildPostfixConfig() {
        List<Domain> domains = domainRepository.findByStatus(Domain.Status.ACTIVE);
        List<User> users = userRepository.findAll().stream()
            .filter(u -> u.status() == User.Status.ACTIVE)
            .toList();
        List<Alias> aliases = aliasRepository.findAll();
        
        // Build virtual_alias_maps
        Map<String, String> virtualAliasMaps = new HashMap<>();
        for (Alias alias : aliases) {
            virtualAliasMaps.put(alias.alias(), alias.targets());
        }
        
        // Build virtual_mailbox_maps
        Map<String, String> virtualMailboxMaps = new HashMap<>();
        for (User user : users) {
            virtualMailboxMaps.put(user.email(), user.mailboxPath().replace("/var/mail/", "") + "/");
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