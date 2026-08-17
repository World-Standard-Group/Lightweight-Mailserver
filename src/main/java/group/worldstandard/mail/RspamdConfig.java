package group.worldstandard.mail;

import group.worldstandard.security.DkimKey;

import java.util.List;

public record RspamdConfig(
    String dkimSigningEnabled,
    String dkimSelectorMap,
    String dkimDomainMap,
    String dkimKeyMap,
    String dkimArcSigningEnabled,
    String dkimArcSelectorMap,
    String dkimArcDomainMap,
    String dkimArcKeyMap,
    String redisServers,
    String redisTimeout,
    String redisDatabase,
    String workerControllerBind,
    String workerControllerPassword,
    String workerNormalBind,
    String workerNormalCount,
    String workerProxyBind,
    String workerProxyUpstream,
    String classifierBayesEnabled,
    String classifierBayesBackend,
    String classifierBayesExpire,
    String classifierBayesLearnThreshold,
    String classifierBayesMinTokens,
    String classifierBayesMaxTokens,
    String fuzzyStorageBackend,
    String fuzzyStorageExpire,
    String greylistingEnabled,
    String greylistingServers,
    String dmarcEnabled,
    String dmarcReportEmail,
    String dmarcReportOrg,
    String dmarcReportDomain,
    String spfEnabled,
    String dkimEnabled,
    String arcEnabled,
    String enabled) {
    public static class Builder {
        private String dkimSigningEnabled = "true";
        private String dkimSelectorMap = "/etc/rspamd/dkim_selectors.map";
        private String dkimDomainMap = "/etc/rspamd/dkim_domains.map";
        private String dkimKeyMap = "/etc/rspamd/dkim_keys.map";
        private String dkimArcSigningEnabled = "true";
        private String dkimArcSelectorMap = "/etc/rspamd/arc_selectors.map";
        private String dkimArcDomainMap = "/etc/rspamd/arc_domains.map";
        private String dkimArcKeyMap = "/etc/rspamd/arc_keys.map";
        private String redisServers = "127.0.0.1:6379";
        private String redisTimeout = "1s";
        private String redisDatabase = "0";
        private String workerControllerBind = "127.0.0.1:11334";
        private String workerControllerPassword = "";
        private String workerNormalBind = "127.0.0.1:11333";
        private String workerNormalCount = "2";
        private String workerProxyBind = "127.0.0.1:11332";
        private String workerProxyUpstream = "127.0.0.1:11333";
        private String classifierBayesEnabled = "true";
        private String classifierBayesBackend = "redis";
        private String classifierBayesExpire = "8640000";
        private String classifierBayesLearnThreshold = "200";
        private String classifierBayesMinTokens = "11";
        private String classifierBayesMaxTokens = "200";
        private String fuzzyStorageBackend = "redis";
        private String fuzzyStorageExpire = "8640000";
        private String greylistingEnabled = "true";
        private String greylistingServers = "127.0.0.1:11335";
        private String dmarcEnabled = "true";
        private String dmarcReportEmail = "dmarc@example.com";
        private String dmarcReportOrg = "Example Org";
        private String dmarcReportDomain = "example.com";
        private String spfEnabled = "true";
        private String dkimEnabled = "true";
        private String dmarcEnabled2 = "true";
        private String arcEnabled = "true";

        public Builder dkimSigningEnabled(String v) { this.dkimSigningEnabled = v; return this; }
        public Builder dkimSelectorMap(String v) { this.dkimSelectorMap = v; return this; }
        public Builder dkimDomainMap(String v) { this.dkimDomainMap = v; return this; }
        public Builder dkimKeyMap(String v) { this.dkimKeyMap = v; return this; }
        public Builder dkimArcSigningEnabled(String v) { this.dkimArcSigningEnabled = v; return this; }
        public Builder dkimArcSelectorMap(String v) { this.dkimArcSelectorMap = v; return this; }
        public Builder dkimArcDomainMap(String v) { this.dkimArcDomainMap = v; return this; }
        public Builder dkimArcKeyMap(String v) { this.dkimArcKeyMap = v; return this; }
        public Builder redisServers(String v) { this.redisServers = v; return this; }
        public Builder redisTimeout(String v) { this.redisTimeout = v; return this; }
        public Builder redisDatabase(String v) { this.redisDatabase = v; return this; }
        public Builder workerControllerBind(String v) { this.workerControllerBind = v; return this; }
        public Builder workerControllerPassword(String v) { this.workerControllerPassword = v; return this; }
        public Builder workerNormalBind(String v) { this.workerNormalBind = v; return this; }
        public Builder workerNormalCount(String v) { this.workerNormalCount = v; return this; }
        public Builder workerProxyBind(String v) { this.workerProxyBind = v; return this; }
        public Builder workerProxyUpstream(String v) { this.workerProxyUpstream = v; return this; }
        public Builder classifierBayesEnabled(String v) { this.classifierBayesEnabled = v; return this; }
        public Builder classifierBayesBackend(String v) { this.classifierBayesBackend = v; return this; }
        public Builder classifierBayesExpire(String v) { this.classifierBayesExpire = v; return this; }
        public Builder classifierBayesLearnThreshold(String v) { this.classifierBayesLearnThreshold = v; return this; }
        public Builder classifierBayesMinTokens(String v) { this.classifierBayesMinTokens = v; return this; }
        public Builder classifierBayesMaxTokens(String v) { this.classifierBayesMaxTokens = v; return this; }
        public Builder fuzzyStorageBackend(String v) { this.fuzzyStorageBackend = v; return this; }
        public Builder fuzzyStorageExpire(String v) { this.fuzzyStorageExpire = v; return this; }
        public Builder greylistingEnabled(String v) { this.greylistingEnabled = v; return this; }
        public Builder greylistingServers(String v) { this.greylistingServers = v; return this; }
        public Builder dmarcEnabled(String v) { this.dmarcEnabled = v; return this; }
        public Builder dmarcReportEmail(String v) { this.dmarcReportEmail = v; return this; }
        public Builder dmarcReportOrg(String v) { this.dmarcReportOrg = v; return this; }
        public Builder dmarcReportDomain(String v) { this.dmarcReportDomain = v; return this; }
        public Builder spfEnabled(String v) { this.spfEnabled = v; return this; }
        public Builder dkimEnabled(String v) { this.dkimEnabled = v; return this; }
        public Builder dmarcEnabled2(String v) { this.dmarcEnabled2 = v; return this; }
        public Builder arcEnabled(String v) { this.arcEnabled = v; return this; }

        public RspamdConfig build() {
            return new RspamdConfig(
                dkimSigningEnabled, dkimSelectorMap, dkimDomainMap, dkimKeyMap,
                dkimArcSigningEnabled, dkimArcSelectorMap, dkimArcDomainMap, dkimArcKeyMap,
                redisServers, redisTimeout, redisDatabase,
                workerControllerBind, workerControllerPassword, workerNormalBind, workerNormalCount,
                workerProxyBind, workerProxyUpstream,
                classifierBayesEnabled, classifierBayesBackend, classifierBayesExpire,
                classifierBayesLearnThreshold, classifierBayesMinTokens, classifierBayesMaxTokens,
                fuzzyStorageBackend, fuzzyStorageExpire,
                greylistingEnabled, greylistingServers,
                dmarcEnabled, dmarcReportEmail, dmarcReportOrg, dmarcReportDomain,
                spfEnabled, dkimEnabled, dmarcEnabled2, arcEnabled
            );
        }
    }

    public String toDkimSigningConf() {

        String sb = "# DKIM signing configuration - Generated by mailctl\n" +
                "# DO NOT EDIT DIRECTLY - Changes will be overwritten\n\n" +
                "dkim_signing {\n" +
                "  enabled = " + dkimSigningEnabled + ";\n" +
                "  selector_map = \"" + dkimSelectorMap + "\";\n" +
                "  domain_map = \"" + dkimDomainMap + "\";\n" +
                "  key_map = \"" + dkimKeyMap + "\";\n" +
                "  arc_signing = " + dkimArcSigningEnabled + ";\n" +
                "  arc_selector_map = \"" + dkimArcSelectorMap + "\";\n" +
                "  arc_domain_map = \"" + dkimArcDomainMap + "\";\n" +
                "  arc_key_map = \"" + dkimArcKeyMap + "\";\n" +
                "}\n";
        
        return sb;
    }

    public String toOptionsConf() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Rspamd options - Generated by mailctl\n");
        sb.append("# DO NOT EDIT DIRECTLY - Changes will be overwritten\n\n");
        
        sb.append("redis {\n");
        sb.append("  servers = \"").append(redisServers).append("\";\n");
        sb.append("  timeout = ").append(redisTimeout).append(";\n");
        sb.append("  database = ").append(redisDatabase).append(";\n");
        sb.append("}\n\n");
        
        sb.append("worker {\n");
        sb.append("  controller {\n");
        sb.append("    bind_socket = \"").append(workerControllerBind).append("\";\n");
        if (!workerControllerPassword.isEmpty()) {
            sb.append("    password = \"").append(workerControllerPassword).append("\";\n");
        }
        sb.append("  }\n");
        sb.append("  normal {\n");
        sb.append("    bind_socket = \"").append(workerNormalBind).append("\";\n");
        sb.append("    count = ").append(workerNormalCount).append(";\n");
        sb.append("  }\n");
        sb.append("  proxy {\n");
        sb.append("    bind_socket = \"").append(workerProxyBind).append("\";\n");
        sb.append("    upstream = \"").append(workerProxyUpstream).append("\";\n");
        sb.append("  }\n");
        sb.append("}\n\n");
        
        sb.append("classifier {\n");
        sb.append("  bayes {\n");
        sb.append("    enabled = ").append(classifierBayesEnabled).append(";\n");
        sb.append("    backend = \"").append(classifierBayesBackend).append("\";\n");
        sb.append("    expire = ").append(classifierBayesExpire).append(";\n");
        sb.append("    learn_threshold = ").append(classifierBayesLearnThreshold).append(";\n");
        sb.append("    min_tokens = ").append(classifierBayesMinTokens).append(";\n");
        sb.append("    max_tokens = ").append(classifierBayesMaxTokens).append(";\n");
        sb.append("  }\n");
        sb.append("}\n\n");
        
        sb.append("fuzzy_storage {\n");
        sb.append("  backend = \"").append(fuzzyStorageBackend).append("\";\n");
        sb.append("  expire = ").append(fuzzyStorageExpire).append(";\n");
        sb.append("}\n\n");
        
        sb.append("greylist {\n");
        sb.append("  enabled = ").append(greylistingEnabled).append(";\n");
        sb.append("  servers = \"").append(greylistingServers).append("\";\n");
        sb.append("}\n\n");
        
        sb.append("dmarc {\n");
        sb.append("  enabled = ").append(dmarcEnabled).append(";\n");
        sb.append("  report_email = \"").append(dmarcReportEmail).append("\";\n");
        sb.append("  report_org = \"").append(dmarcReportOrg).append("\";\n");
        sb.append("  report_domain = \"").append(dmarcReportDomain).append("\";\n");
        sb.append("}\n");
        
        return sb.toString();
    }

    public String toDkimSelectorMap(List<DkimKey> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DKIM selector map - Generated by mailctl\n");
        for (DkimKey key : keys) {
            if (key.status() == DkimKey.Status.ACTIVE) {
                sb.append(key.selector()).append("\n");
            }
        }
        return sb.toString();
    }

    public String toDkimDomainMap(List<DkimKey> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DKIM domain map - Generated by mailctl\n");
        for (DkimKey key : keys) {
            if (key.status() == DkimKey.Status.ACTIVE) {
                sb.append(key.selector()).append("\t").append(key.domainId()).append("\n");
            }
        }
        return sb.toString();
    }

    public String toDkimKeyMap(List<DkimKey> keys) {
        StringBuilder sb = new StringBuilder();
        sb.append("# DKIM key map - Generated by mailctl\n");
        for (DkimKey key : keys) {
            if (key.status() == DkimKey.Status.ACTIVE) {
                String privateKeyPath = "/etc/rspamd/dkim/" + key.selector() + ".private";
                sb.append(key.selector()).append("\t").append(privateKeyPath).append("\n");
            }
        }
        return sb.toString();
    }
}