package group.worldstandard.cli;

import group.worldstandard.security.DkimKey;
import group.worldstandard.security.DkimKeyManager;
import picocli.CommandLine;

@CommandLine.Command(
    name = "dkim",
    description = "DKIM key management",
    subcommands = {
        DkimCommand.Generate.class,
        DkimCommand.Show.class,
        DkimCommand.Rotate.class
    }
)
public class DkimCommand implements Runnable {
    @Override
    public void run() {
        System.out.println("Usage: mailctl dkim [generate|show|rotate]");
    }

    @CommandLine.Command(name = "generate", description = "Generate DKIM keys for a domain")
    public static class Generate implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Domain name")
        String domain;

        @CommandLine.Option(names = {"--selector"}, description = "DKIM selector", defaultValue = "mail")
        String selector;

        @CommandLine.Option(names = {"--key-size"}, description = "Key size in bits", defaultValue = "2048")
        int keySize;

        @CommandLine.Option(names = {"--algorithm"}, description = "Algorithm (rsa2048, rsa4096, ed25519)", defaultValue = "rsa2048")
        String algorithm;

        private final DkimKeyManager dkimKeyManager;

        public Generate(DkimKeyManager dkimKeyManager) {
            this.dkimKeyManager = dkimKeyManager;
        }

        public Generate() {
            this.dkimKeyManager = null;
        }

        @Override
        public void run() {
            if (dkimKeyManager == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                DkimKey key = dkimKeyManager.generateKey(domain, selector, keySize, algorithm);
                System.out.println("DKIM key generated successfully:");
                System.out.println("  ID: " + key.id());
                System.out.println("  Domain: " + domain);
                System.out.println("  Selector: " + key.selector());
                System.out.println("  Algorithm: " + key.algorithm());
                System.out.println("  Key Size: " + key.keySize());
                System.out.println("  Expires: " + key.expiresAt());
                System.out.println();
                System.out.println("DNS Record (add to your DNS):");
                System.out.println(key.dnsRecord());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "show", description = "Show DKIM public key for a domain")
    public static class Show implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Domain name")
        String domain;

        @CommandLine.Option(names = {"--selector"}, description = "DKIM selector", defaultValue = "mail")
        String selector;

        @CommandLine.Option(names = {"--dns-format"}, description = "Output in DNS TXT record format")
        boolean dnsFormat;

        private final DkimKeyManager dkimKeyManager;

        public Show(DkimKeyManager dkimKeyManager) {
            this.dkimKeyManager = dkimKeyManager;
        }

        public Show() {
            this.dkimKeyManager = null;
        }

        @Override
        public void run() {
            if (dkimKeyManager == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                var keyOpt = dkimKeyManager.getKey(domain, selector);
                if (keyOpt.isEmpty()) {
                    System.err.println("DKIM key not found for domain: " + domain + " selector: " + selector);
                    return;
                }
                
                DkimKey key = keyOpt.get();
                if (dnsFormat) {
                    System.out.println(key.dnsRecord());
                } else {
                    System.out.println("DKIM Public Key:");
                    System.out.println("  Domain: " + domain);
                    System.out.println("  Selector: " + key.selector());
                    System.out.println("  Algorithm: " + key.algorithm());
                    System.out.println("  Key Size: " + key.keySize());
                    System.out.println("  Status: " + key.status());
                    System.out.println("  Created: " + key.createdAt());
                    System.out.println("  Expires: " + key.expiresAt());
                    System.out.println();
                    System.out.println("Public Key:");
                    System.out.println(key.publicKeyPem());
                    System.out.println();
                    System.out.println("DNS Record:");
                    System.out.println(key.dnsRecord());
                }
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    @CommandLine.Command(name = "rotate", description = "Rotate DKIM keys for a domain")
    public static class Rotate implements Runnable {
        @CommandLine.Parameters(index = "0", description = "Domain name")
        String domain;

        @CommandLine.Option(names = {"--selector"}, description = "DKIM selector", defaultValue = "mail")
        String selector;

        private final DkimKeyManager dkimKeyManager;

        public Rotate(DkimKeyManager dkimKeyManager) {
            this.dkimKeyManager = dkimKeyManager;
        }

        public Rotate() {
            this.dkimKeyManager = null;
        }

        @Override
        public void run() {
            if (dkimKeyManager == null) {
                System.err.println("Service not initialized. Use main entry point.");
                return;
            }
            
            try {
                DkimKey newKey = dkimKeyManager.rotateKey(domain, selector);
                System.out.println("DKIM key rotated successfully:");
                System.out.println("  New ID: " + newKey.id());
                System.out.println("  Domain: " + domain);
                System.out.println("  Selector: " + newKey.selector());
                System.out.println("  Algorithm: " + newKey.algorithm());
                System.out.println("  Key Size: " + newKey.keySize());
                System.out.println("  Expires: " + newKey.expiresAt());
                System.out.println();
                System.out.println("New DNS Record (update your DNS):");
                System.out.println(newKey.dnsRecord());
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }
}