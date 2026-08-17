package group.worldstandard;

import group.worldstandard.cli.*;
import picocli.CommandLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public class Main implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    @CommandLine.Option(names = {"-v", "--verbose"}, description = "Enable verbose output")
    boolean verbose;

    @CommandLine.Option(names = {"-c", "--config"}, description = "Configuration file path", defaultValue = "/etc/mailctl/config.yaml")
    String configFile;

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            ServiceRegistry.getInstance().initialize(configFile);
        } catch (Exception e) {
            log.error("Failed to initialize application: {}", e.getMessage(), e);
            System.err.println("Initialization failed: " + e.getMessage());
            System.exit(1);
        }
        
        if (verbose) {
            System.out.println("Verbose mode enabled");
        }
        System.out.println(spec.usageMessage());
    }
}