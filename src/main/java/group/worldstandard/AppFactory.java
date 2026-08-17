package group.worldstandard;

import group.worldstandard.cli.*;
import group.worldstandard.domain.*;
import group.worldstandard.identity.*;
import group.worldstandard.routing.*;
import group.worldstandard.security.*;
import group.worldstandard.infrastructure.*;
import group.worldstandard.mail.*;

import com.zaxxer.hikari.HikariDataSource;
import picocli.CommandLine.IFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating command instances with dependency injection.
 * Uses a singleton pattern to share repositories and services across commands.
 */
public class AppFactory implements IFactory {
    private static final AppFactory INSTANCE = new AppFactory();
    
    private HikariDataSource dataSource;
    private AppConfig appConfig;
    private DatabaseConfig databaseConfig;
    
    // Repositories
    private DomainRepository domainRepository;
    private UserRepository userRepository;
    private AliasRepository aliasRepository;
    private DkimKeyRepository dkimKeyRepository;
    
    // Services
    private DomainService domainService;
    private UserService userService;
    private RoutingService routingService;
    private DkimKeyManager dkimKeyManager;
    private PasswordHasher passwordHasher;
    private FileManager fileManager;
    private ProcessRunner processRunner;
    private HealthChecker healthChecker;
    private ConfigGenerator configGenerator;
    
    // Store created command instances for picocli to reuse
    private final Map<Class<?>, Object> commandInstances = new ConcurrentHashMap<>();
    
    private AppFactory() {}
    
    public static AppFactory getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize the factory with configuration.
     * Call this before executing commands.
     */
    public synchronized void initialize(String configFile) throws Exception {
        if (databaseConfig != null) {
            return; // Already initialized
        }
        
        // Load app config
        appConfig = AppConfig.load(java.nio.file.Paths.get(configFile));
        
        // Create database config and data source
        databaseConfig = new DatabaseConfig(appConfig.database().url(), 
                                           appConfig.database().user(), 
                                           appConfig.database().password());
        dataSource = (HikariDataSource) databaseConfig.getDataSource();
        
        // Initialize repositories
        domainRepository = new JdbcDomainRepository(dataSource);
        userRepository = new JdbcUserRepository(dataSource);
        aliasRepository = new JdbcAliasRepository(dataSource);
        dkimKeyRepository = new JdbcDkimKeyRepository(dataSource);
        
        // Initialize services
        passwordHasher = new PasswordHasher();
        domainService = new DomainService(domainRepository);
        userService = new UserService(userRepository, domainRepository, passwordHasher);
        routingService = new RoutingService(aliasRepository, domainRepository);
        dkimKeyManager = new DkimKeyManager(dkimKeyRepository, domainRepository);
        
        // Initialize infrastructure
        fileManager = new FileManager();
        processRunner = new ProcessRunner();
        healthChecker = new HealthChecker(processRunner, fileManager);
        configGenerator = new ConfigGenerator(
            domainRepository, userRepository, aliasRepository, dkimKeyRepository,
            fileManager, appConfig
        );
    }
    
    /**
     * Close all resources.
     */
    public void close() {
        if (databaseConfig != null) {
            databaseConfig.close();
            databaseConfig = null;
        }
    }
    
    @Override
    public <K> K create(Class<K> type) throws Exception {
        // Check if we already have an instance
        @SuppressWarnings("unchecked")
        K existing = (K) commandInstances.get(type);
        if (existing != null) {
            return existing;
        }
        
        K instance = createInstance(type);
        commandInstances.put(type, instance);
        return instance;
    }
    
    @SuppressWarnings("unchecked")
    private <K> K createInstance(Class<K> type) {
        try {
            // Domain commands
            if (type == DomainCommand.Add.class) {
                return (K) new DomainCommand.Add(domainService);
            }
            if (type == DomainCommand.Remove.class) {
                return (K) new DomainCommand.Remove(domainService);
            }
            if (type == DomainCommand.ListCmd.class) {
                return (K) new DomainCommand.ListCmd(domainService);
            }
            
            // User commands
            if (type == UserCommand.Add.class) {
                return (K) new UserCommand.Add(userService);
            }
            if (type == UserCommand.Remove.class) {
                return (K) new UserCommand.Remove(userService);
            }
            if (type == UserCommand.Disable.class) {
                return (K) new UserCommand.Disable(userService);
            }
            if (type == UserCommand.Enable.class) {
                return (K) new UserCommand.Enable(userService);
            }
            if (type == UserCommand.Passwd.class) {
                return (K) new UserCommand.Passwd(userService);
            }
            if (type == UserCommand.ListCmd.class) {
                return (K) new UserCommand.ListCmd(userService);
            }
            
            // Alias commands
            if (type == AliasCommand.Add.class) {
                return (K) new AliasCommand.Add(routingService);
            }
            if (type == AliasCommand.Remove.class) {
                return (K) new AliasCommand.Remove(routingService);
            }
            if (type == AliasCommand.ListCmd.class) {
                return (K) new AliasCommand.ListCmd(routingService);
            }
            
            // DKIM commands
            if (type == DkimCommand.Generate.class) {
                return (K) new DkimCommand.Generate(dkimKeyManager);
            }
            if (type == DkimCommand.Show.class) {
                return (K) new DkimCommand.Show(dkimKeyManager);
            }
            if (type == DkimCommand.Rotate.class) {
                return (K) new DkimCommand.Rotate(dkimKeyManager);
            }
            
            // Config commands
            if (type == ConfigCommand.Generate.class) {
                return (K) new ConfigCommand.Generate(configGenerator);
            }
            if (type == ConfigCommand.Validate.class) {
                return (K) new ConfigCommand.Validate(configGenerator, processRunner);
            }
            if (type == ConfigCommand.Show.class) {
                return (K) new ConfigCommand.Show();
            }
            if (type == ConfigCommand.Deploy.class) {
                return (K) new ConfigCommand.Deploy(configGenerator, processRunner);
            }
            
            // Service commands
            if (type == ServiceCommand.Status.class) {
                return (K) new ServiceCommand.Status(processRunner);
            }
            if (type == ServiceCommand.Reload.class) {
                return (K) new ServiceCommand.Reload(processRunner);
            }
            if (type == ServiceCommand.Restart.class) {
                return (K) new ServiceCommand.Restart(processRunner);
            }
            if (type == ServiceCommand.Enable.class) {
                return (K) new ServiceCommand.Enable(processRunner);
            }
            if (type == ServiceCommand.Disable.class) {
                return (K) new ServiceCommand.Disable(processRunner);
            }
            
            // Health commands
            if (type == HealthCommand.Check.class) {
                return (K) new HealthCommand.Check(healthChecker);
            }
            if (type == HealthCommand.Liveness.class) {
                return (K) new HealthCommand.Liveness(healthChecker);
            }
            if (type == HealthCommand.Readiness.class) {
                return (K) new HealthCommand.Readiness(healthChecker);
            }
            
            // Backup commands
            if (type == BackupCommand.Create.class) {
                return (K) new BackupCommand.Create(fileManager, databaseConfig);
            }
            if (type == BackupCommand.Restore.class) {
                return (K) new BackupCommand.Restore(fileManager, databaseConfig);
            }
            if (type == BackupCommand.List.class) {
                return (K) new BackupCommand.List(fileManager);
            }
            
            // Doctor commands
            if (type == DoctorCommand.Run.class) {
                return (K) new DoctorCommand.Run(healthChecker, processRunner);
            }
            if (type == DoctorCommand.Dns.class) {
                return (K) new DoctorCommand.Dns(healthChecker);
            }
            if (type == DoctorCommand.Tls.class) {
                return (K) new DoctorCommand.Tls(healthChecker);
            }
            if (type == DoctorCommand.Services.class) {
                return (K) new DoctorCommand.Services(healthChecker, processRunner);
            }
            if (type == DoctorCommand.Storage.class) {
                return (K) new DoctorCommand.Storage(healthChecker);
            }
            if (type == DoctorCommand.Security.class) {
                return (K) new DoctorCommand.Security(healthChecker);
            }
            
            // Default: use no-arg constructor
            return type.getDeclaredConstructor().newInstance();
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + type.getName(), e);
        }
    }
    
    // Getters for services (for potential future use)
    public DomainRepository getDomainRepository() { return domainRepository; }
    public UserRepository getUserRepository() { return userRepository; }
    public AliasRepository getAliasRepository() { return aliasRepository; }
    public DkimKeyRepository getDkimKeyRepository() { return dkimKeyRepository; }
    public DomainService getDomainService() { return domainService; }
    public UserService getUserService() { return userService; }
    public RoutingService getRoutingService() { return routingService; }
    public DkimKeyManager getDkimKeyManager() { return dkimKeyManager; }
    public ConfigGenerator getConfigGenerator() { return configGenerator; }
    public HealthChecker getHealthChecker() { return healthChecker; }
    public ProcessRunner getProcessRunner() { return processRunner; }
    public FileManager getFileManager() { return fileManager; }
    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
    public AppConfig getAppConfig() { return appConfig; }
}