package group.worldstandard;

import group.worldstandard.cli.*;
import group.worldstandard.domain.DomainService;
import group.worldstandard.identity.UserService;
import group.worldstandard.infrastructure.*;
import group.worldstandard.routing.RoutingService;
import group.worldstandard.security.DkimKeyManager;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.function.Supplier;

@CommandLine.Command(
    name = "mailctl",
    mixinStandardHelpOptions = true,
    version = "0.0.1-indev",
    description = "Lightweight Mail Server Control Plane",
    subcommands = {
        DomainCommand.class,
        UserCommand.class,
        AliasCommand.class,
        DkimCommand.class,
        ConfigCommand.class,
        ServiceCommand.class,
        HealthCommand.class,
        BackupCommand.class,
        DoctorCommand.class
    }
)
    /**
     * Command-line entry point for the mailctl control plane.
     *  *
     *  * <p>Responsible for three things: resolving the configuration path, initializing
     *  * the {@link ServiceRegistry} before picocli runs, and supplying the
     *  * {@link ServiceFactory} that injects services into every subcommand.</p>
     *  *
     *  * @see ServiceRegistry
     *  * @see Main.ServiceFactory
     */
public class Main implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Configuration file path")
    String configFile;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    public static void main(String[] args) {
        initialize(args);
        int exitCode = new CommandLine(new Main(), new ServiceFactory()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        if (verbose) {
            System.out.println("Verbose mode enabled");
        }
        System.out.println(spec.usageMessage());
    }

    /**
     * Builds subcommands with their dependencies injected.
     *
     * <p>Picocli instantiates subcommands itself using the no-arg constructor,
     * which used to leave every service field null - so {@code mailctl domain list}
     * printed "Service not initialized" and changed nothing. This factory prefers
     * the single-argument constructor and supplies the matching singleton from
     * {@link ServiceRegistry}, so every subcommand gets real services.
     *
     * <p>The registry itself is initialized up front by {@link #initialize(String[])},
     * which must run before picocli instantiates any subcommand.
     */
    static final class ServiceFactory implements CommandLine.IFactory {
        private final ServiceRegistry registry = ServiceRegistry.getInstance();

        @Override
        @SuppressWarnings("unchecked")
        public <K> K create(Class<K> cls) throws Exception {
            // Prefer the constructor whose parameters can all be supplied.
            // Several commands take TWO services (e.g. doctor run needs a
            // HealthChecker and a ProcessRunner), so a single-arg-only factory
            // would silently fall through to the no-arg constructor for them.
            Constructor<?> best = null;
            int bestScore = -1;

            for (Constructor<?> ctor : cls.getConstructors()) {
                Class<?>[] params = ctor.getParameterTypes();
                int score = 0;
                for (Class<?> p : params) {
                    if (supplierFor(p) != null) {
                        score++;
                    }
                }
                if (score == params.length && score > bestScore) {
                    best = ctor;
                    bestScore = score;
                }
            }

            if (best != null) {
                Class<?>[] params = best.getParameterTypes();
                Object[] args = new Object[params.length];
                for (int i = 0; i < params.length; i++) {
                    args[i] = supplierFor(params[i]).get();
                }
                return (K) best.newInstance(args);
            }

            // Nothing injectable: fall back to picocli's default behaviour.
            return cls.getDeclaredConstructor().newInstance();
        }

        private Supplier<?> supplierFor(Class<?> type) {
            if (type == DomainService.class)     return registry::getDomainService;
            if (type == UserService.class)        return registry::getUserService;
            if (type == RoutingService.class)     return registry::getRoutingService;
            if (type == DkimKeyManager.class)     return registry::getDkimKeyManager;
            if (type == ConfigGenerator.class)    return registry::getConfigGenerator;
            if (type == ProcessRunner.class)      return registry::getProcessRunner;
            if (type == FileManager.class)        return registry::getFileManager;
            if (type == HealthChecker.class)      return registry::getHealthChecker;
            if (type == DatabaseConfig.class)     return registry::getDatabaseConfig;
            return null;
        }
    }

    /**
     * Resolves the config path and initializes the registry before picocli parses
     * arguments, so subcommand constructors always receive live services.
     *
     * <p>Commands that do not touch the database still work when the database is
     * unreachable: initialization failure is reported and execution continues, so
     * {@code --help}, {@code --version} and the command list stay available.
     */
    static void initialize(String[] args) {
        // --help/--version must never touch the database: they are the commands
        // you reach for when something is already broken.
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h")
                    || arg.equals("--version") || arg.equals("-V")) {
                return;
            }
        }

        Path configPath = resolveConfigPath(args);
        try {
            ServiceRegistry.getInstance().initialize(configPath.toString());
        } catch (Exception e) {
            log.warn("Database-backed commands unavailable: {}", e.getMessage());
            System.err.println("Warning: could not initialize database (" + e.getMessage() + ")");
            System.err.println("Database-backed commands will fail. Config path: " + configPath);
        }
    }

    /**
     * Honours an explicit -c/--config argument, otherwise falls back to
     * MAILCTL_CONFIG or /etc/mailctl/config.yaml.
     */
    static Path resolveConfigPath(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ((arg.equals("-c") || arg.equals("--config")) && i + 1 < args.length) {
                return Path.of(args[i + 1]);
            }
            if (arg.startsWith("--config=")) {
                return Path.of(arg.substring("--config=".length()));
            }
        }
        return AppConfig.defaultConfigPath();
    }
}