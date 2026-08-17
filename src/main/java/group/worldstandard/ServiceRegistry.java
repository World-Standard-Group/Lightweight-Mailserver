package group.worldstandard;

import group.worldstandard.cli.*;
import group.worldstandard.domain.*;
import group.worldstandard.identity.*;
import group.worldstandard.routing.*;
import group.worldstandard.security.*;
import group.worldstandard.infrastructure.*;
import group.worldstandard.mail.*;

import com.zaxxer.hikari.HikariDataSource;

/**
 * Service registry for dependency injection.
 * Initialized by Main before command execution.
 */
public class ServiceRegistry {
    private static final ServiceRegistry INSTANCE = new ServiceRegistry();
    
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
    
    private boolean initialized = false;
    
    private ServiceRegistry() {}
    
    public static ServiceRegistry getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initialize the registry with configuration.
     * Call this before executing commands.
     */
    public synchronized void initialize(String configFile) throws Exception {
        if (initialized) {
            return;
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
        
        initialized = true;
    }
    
    /**
     * Close all resources.
     */
    public void close() {
        if (databaseConfig != null) {
            databaseConfig.close();
            databaseConfig = null;
        }
        initialized = false;
    }
    
    // Getters for services
    public DomainRepository getDomainRepository() { return domainRepository; }
    public UserRepository getUserRepository() { return userRepository; }
    public AliasRepository getAliasRepository() { return aliasRepository; }
    public DkimKeyRepository getDkimKeyRepository() { return dkimKeyRepository; }
    public DomainService getDomainService() { return domainService; }
    public UserService getUserService() { return userService; }
    public RoutingService getRoutingService() { return routingService; }
    public DkimKeyManager getDkimKeyManager() { return dkimKeyManager; }
    public PasswordHasher getPasswordHasher() { return passwordHasher; }
    public FileManager getFileManager() { return fileManager; }
    public ProcessRunner getProcessRunner() { return processRunner; }
    public HealthChecker getHealthChecker() { return healthChecker; }
    public ConfigGenerator getConfigGenerator() { return configGenerator; }
    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
    public AppConfig getAppConfig() { return appConfig; }
    public boolean isInitialized() { return initialized; }
}