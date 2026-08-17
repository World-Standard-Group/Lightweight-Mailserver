package group.worldstandard.cli;

import group.worldstandard.infrastructure.DatabaseConfig;
import group.worldstandard.infrastructure.FileManager;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@CommandLine.Command(
    name = "backup",
    description = "Backup and restore",
    subcommands = {
        BackupCommand.Create.class,
        BackupCommand.Restore.class,
        BackupCommand.List.class
    }
)
public class BackupCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Usage: mailctl backup [create|restore|list]");
    }

    @CommandLine.Command(name = "create", description = "Create a backup")
    public static class Create implements Runnable {
        @CommandLine.Option(names = {"--output"}, description = "Output file path")
        String output;

        @CommandLine.Option(names = {"--include"}, description = "Comma-separated: database,config,mail,dkim", defaultValue = "database,config,dkim")
        String include;

        private final FileManager fileManager;
        private final DatabaseConfig databaseConfig;

        public Create(FileManager fileManager, DatabaseConfig databaseConfig) {
            this.fileManager = fileManager;
            this.databaseConfig = databaseConfig;
        }

        public Create() {
            this.fileManager = null;
            this.databaseConfig = null;
        }

        @Override
        public void run() {
            if (fileManager == null || databaseConfig == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                String outputPath = output != null ? output : "/var/backups/mailctl/backup-" + timestamp + ".tar.gz";
                
                System.out.println("Creating backup: " + outputPath);
                System.out.println("Include: " + include);
                
                System.out.println("Backup created (placeholder): " + outputPath);
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "restore", description = "Restore from backup")
    public static class Restore implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Backup file path")
        String backupFile;

        @CommandLine.Option(names = {"--include"}, description = "Comma-separated: database,config,mail,dkim", defaultValue = "database,config,dkim")
        String include;

        @CommandLine.Option(names = {"--force"}, description = "Force restore without confirmation")
        boolean force;

        private final FileManager fileManager;
        private final DatabaseConfig databaseConfig;

        public Restore(FileManager fileManager, DatabaseConfig databaseConfig) {
            this.fileManager = fileManager;
            this.databaseConfig = databaseConfig;
        }

        public Restore() {
            this.fileManager = null;
            this.databaseConfig = null;
        }

        @Override
        public void run() {
            if (fileManager == null || databaseConfig == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            if (!force) {
                System.out.print("Are you sure you want to restore from '" + backupFile + "'? (y/N): ");
                String confirm = System.console() != null ? System.console().readLine() : new java.util.Scanner(System.in).nextLine();
                if (!"y".equalsIgnoreCase(confirm)) {
                    System.out.println("Cancelled.");
                    return;
                }
            }
            
            try {
                System.out.println("Restoring from: " + backupFile);
                System.out.println("Include: " + include);
                
                System.out.println("Restore completed (placeholder)");
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "list", description = "List available backups")
    public static class List implements Runnable {
        @CommandLine.Option(names = {"--dir"}, description = "Backup directory", defaultValue = "/var/backups/mailctl")
        String dir;

        private final FileManager fileManager;

        public List(FileManager fileManager) {
            this.fileManager = fileManager;
        }

        public List() {
            this.fileManager = null;
        }

        @Override
        public void run() {
            if (fileManager == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                Path backupDir = Paths.get(dir);
                if (!Files.exists(backupDir)) {
                    System.out.println("Backup directory does not exist: " + dir);
                    return;
                }
                
                System.out.println("Backups in: " + dir);
                Files.list(backupDir)
                    .filter(p -> p.toString().endsWith(".tar.gz"))
                    .sorted()
                    .forEach(p -> {
                        try {
                            long size = Files.size(p);
                            String sizeStr = formatSize(size);
                            System.out.printf("  %s (%s)%n", p.getFileName(), sizeStr);
                        } catch (IOException e) {
                            System.out.printf("  %s (size unknown)%n", p.getFileName());
                        }
                    });
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
        
        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}