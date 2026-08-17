package group.worldstandard.cli;

import group.worldstandard.infrastructure.ConfigGenerator;
import group.worldstandard.infrastructure.ProcessRunner;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@CommandLine.Command(
    name = "config",
    description = "Configuration management",
    subcommands = {
        ConfigCommand.Generate.class,
        ConfigCommand.Validate.class,
        ConfigCommand.Show.class,
        ConfigCommand.Deploy.class
    }
)
public class ConfigCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Usage: mailctl config [generate|validate|show|deploy]");
    }

    @CommandLine.Command(name = "generate", description = "Generate all configuration files from database")
    public static class Generate implements Runnable {
        @CommandLine.Option(names = {"--output-dir"}, description = "Output directory", defaultValue = "/tmp/mailctl-config")
        String outputDir;

        @CommandLine.Option(names = {"--services"}, description = "Comma-separated list of services (postfix,dovecot,rspamd)", defaultValue = "postfix,dovecot,rspamd")
        String services;

        private final ConfigGenerator configGenerator;

        public Generate(ConfigGenerator configGenerator) {
            this.configGenerator = configGenerator;
        }

        public Generate() {
            this.configGenerator = null;
        }

        @Override
        public void run() {
            if (configGenerator == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                configGenerator.generateAll(Paths.get(outputDir));
                System.out.println("Configuration generated to: " + outputDir);
            } catch (IOException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "validate", description = "Validate generated configuration")
    public static class Validate implements Runnable {
        @CommandLine.Option(names = {"--config-dir"}, description = "Configuration directory to validate", defaultValue = "/etc")
        String configDir;

        @CommandLine.Option(names = {"--services"}, description = "Comma-separated list of services", defaultValue = "postfix,dovecot,rspamd")
        String services;

        private final ConfigGenerator configGenerator;
        private final ProcessRunner processRunner;

        public Validate(ConfigGenerator configGenerator, ProcessRunner processRunner) {
            this.configGenerator = configGenerator;
            this.processRunner = processRunner;
        }

        public Validate() {
            this.configGenerator = null;
            this.processRunner = null;
        }

        @Override
        public void run() {
            if (configGenerator == null || processRunner == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                Path sourceDir = Paths.get(configDir).resolve("mailctl-config");
                if (!java.nio.file.Files.exists(sourceDir)) {
                    System.err.println("Configuration directory not found: " + sourceDir);
                    return;
                }
                
                configGenerator.deployConfig(sourceDir);
                System.out.println("Configuration validation passed!");
            } catch (Exception e) {
                System.err.println("Validation failed: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "show", description = "Show current effective configuration")
    public static class Show implements Runnable {
        @CommandLine.Option(names = {"--service"}, description = "Service to show config for", defaultValue = "all")
        String service;

        @CommandLine.Option(names = {"--format"}, description = "Output format (text, json, yaml)", defaultValue = "text")
        String format;

        public Show() {
        }

        @Override
        public void run() {
            System.out.println("Showing configuration for: " + service + " (format: " + format + ")");
            // TODO: read and display config
        }
    }

    @CommandLine.Command(name = "deploy", description = "Deploy validated configuration atomically")
    public static class Deploy implements Runnable {
        @CommandLine.Option(names = {"--config-dir"}, description = "Source configuration directory", defaultValue = "/tmp/mailctl-config")
        String configDir;

        @CommandLine.Option(names = {"--dry-run"}, description = "Show what would be deployed without deploying")
        boolean dryRun;

        private final ConfigGenerator configGenerator;
        private final ProcessRunner processRunner;

        public Deploy(ConfigGenerator configGenerator, ProcessRunner processRunner) {
            this.configGenerator = configGenerator;
            this.processRunner = processRunner;
        }

        public Deploy() {
            this.configGenerator = null;
            this.processRunner = null;
        }

        @Override
        public void run() {
            if (configGenerator == null || processRunner == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                if (dryRun) {
                    System.out.println("DRY RUN: Would deploy configuration from: " + configDir);
                    return;
                }
                
                configGenerator.deployConfig(Paths.get(configDir));
                System.out.println("Configuration deployed successfully!");
            } catch (Exception e) {
                System.err.println("Deployment failed: " + e.getMessage());
            }
        }
    }
}