package group.worldstandard.mail;

public record DovecotConfig(
    String listen,
    String protocols,
    String mailLocation,
    String mailUid,
    String mailGid,
    String mailPrivilegedGroup,
    String firstValidUid,
    String lastValidUid,
    String authMechanisms,
    String passdbDriver,
    String passdbArgs,
    String userdbDriver,
    String userdbArgs,
    String sslCert,
    String sslKey,
    String sslRequireCrl,
    String sslMinProtocol,
    String sslCipherList,
    String sslPreferServerCiphers,
    String namespaceInboxPrefix,
    String namespaceInboxSeparator,
    String namespaceInboxInbox,
    String mailboxDraftsSpecialUse,
    String mailboxJunkSpecialUse,
    String mailboxTrashSpecialUse,
    String mailboxSentSpecialUse,
    String mailboxArchiveSpecialUse,
    String quotaRule,
    String quotaRule2,
    String quotaWarning,
    String quotaWarning2,
    String quotaWarning3,
    String pluginQuota,
    String pluginQuotaStatus,
    String pluginQuotaStatusSuccess,
    String pluginQuotaStatusNoUser,
    String pluginQuotaStatusOverQuota,
    String serviceAuthUnixListenerPath,
    String serviceAuthUnixListenerUser,
    String serviceAuthUnixListenerGroup,
    String serviceAuthUnixListenerMode,
    String serviceAuthInetListenerAddress,
    String serviceAuthInetListenerPort,
    String serviceLmtpUnixListenerPath,
    String serviceLmtpUnixListenerUser,
    String serviceLmtpUnixListenerGroup,
    String serviceLmtpUnixListenerMode
) {
    public static class Builder {
        private String listen = "*";
        private String protocols = "imap lmtp";
        private String mailLocation = "maildir:/var/mail/%d/%n";
        private String mailUid = "mail";
        private String mailGid = "mail";
        private String mailPrivilegedGroup = "mail";
        private String firstValidUid = "1000";
        private String lastValidUid = "0";
        private String authMechanisms = "plain login";
        private String passdbDriver = "sql";
        private String passdbArgs = "/etc/dovecot/dovecot-sql.conf.ext";
        private String userdbDriver = "sql";
        private String userdbArgs = "/etc/dovecot/dovecot-sql.conf.ext";
        private String sslCert = "/etc/ssl/certs/mailcert.pem";
        private String sslKey = "/etc/ssl/private/mailkey.pem";
        private String sslRequireCrl = "no";
        private String sslMinProtocol = "TLSv1.2";
        private String sslCipherList = "HIGH:!aNULL:!kRSA:!PSK:!SRP:!MD5:!RC4";
        private String sslPreferServerCiphers = "yes";
        private String namespaceInboxPrefix = "";
        private String namespaceInboxSeparator = "/";
        private String namespaceInboxInbox = "yes";
        private String mailboxDraftsSpecialUse = "\\Drafts";
        private String mailboxJunkSpecialUse = "\\Junk";
        private String mailboxTrashSpecialUse = "\\Trash";
        private String mailboxSentSpecialUse = "\\Sent";
        private String mailboxArchiveSpecialUse = "\\Archive";
        private String quotaRule = "*:storage=1G";
        private String quotaRule2 = "*:messages=100000";
        private String quotaWarning = "quota_warning = storage=95%% quota-warning 95 %u";
        private String quotaWarning2 = "quota_warning2 = storage=80%% quota-warning 80 %u";
        private String quotaWarning3 = "quota_warning3 = storage=50%% quota-warning 50 %u";
        private String pluginQuota = "quota = maildir:User quota";
        private String pluginQuotaStatus = "quota_status = userdb";
        private String pluginQuotaStatusSuccess = "quota_status_success = DUNNO";
        private String pluginQuotaStatusNoUser = "quota_status_nouser = DUNNO";
        private String pluginQuotaStatusOverQuota = "quota_status_overquota = 552 5.2.2 Mailbox is over quota";
        private String serviceAuthUnixListenerPath = "/var/spool/postfix/private/auth";
        private String serviceAuthUnixListenerUser = "postfix";
        private String serviceAuthUnixListenerGroup = "postfix";
        private String serviceAuthUnixListenerMode = "0660";
        private String serviceAuthInetListenerAddress = "127.0.0.1";
        private String serviceAuthInetListenerPort = "12345";
        private String serviceLmtpUnixListenerPath = "/var/spool/postfix/private/dovecot-lmtp";
        private String serviceLmtpUnixListenerUser = "postfix";
        private String serviceLmtpUnixListenerGroup = "postfix";
        private String serviceLmtpUnixListenerMode = "0660";

        public Builder listen(String v) { this.listen = v; return this; }
        public Builder protocols(String v) { this.protocols = v; return this; }
        public Builder mailLocation(String v) { this.mailLocation = v; return this; }
        public Builder mailUid(String v) { this.mailUid = v; return this; }
        public Builder mailGid(String v) { this.mailGid = v; return this; }
        public Builder mailPrivilegedGroup(String v) { this.mailPrivilegedGroup = v; return this; }
        public Builder firstValidUid(String v) { this.firstValidUid = v; return this; }
        public Builder lastValidUid(String v) { this.lastValidUid = v; return this; }
        public Builder authMechanisms(String v) { this.authMechanisms = v; return this; }
        public Builder passdbDriver(String v) { this.passdbDriver = v; return this; }
        public Builder passdbArgs(String v) { this.passdbArgs = v; return this; }
        public Builder userdbDriver(String v) { this.userdbDriver = v; return this; }
        public Builder userdbArgs(String v) { this.userdbArgs = v; return this; }
        public Builder sslCert(String v) { this.sslCert = v; return this; }
        public Builder sslKey(String v) { this.sslKey = v; return this; }
        public Builder sslRequireCrl(String v) { this.sslRequireCrl = v; return this; }
        public Builder sslMinProtocol(String v) { this.sslMinProtocol = v; return this; }
        public Builder sslCipherList(String v) { this.sslCipherList = v; return this; }
        public Builder sslPreferServerCiphers(String v) { this.sslPreferServerCiphers = v; return this; }
        public Builder namespaceInboxPrefix(String v) { this.namespaceInboxPrefix = v; return this; }
        public Builder namespaceInboxSeparator(String v) { this.namespaceInboxSeparator = v; return this; }
        public Builder namespaceInboxInbox(String v) { this.namespaceInboxInbox = v; return this; }
        public Builder mailboxDraftsSpecialUse(String v) { this.mailboxDraftsSpecialUse = v; return this; }
        public Builder mailboxJunkSpecialUse(String v) { this.mailboxJunkSpecialUse = v; return this; }
        public Builder mailboxTrashSpecialUse(String v) { this.mailboxTrashSpecialUse = v; return this; }
        public Builder mailboxSentSpecialUse(String v) { this.mailboxSentSpecialUse = v; return this; }
        public Builder mailboxArchiveSpecialUse(String v) { this.mailboxArchiveSpecialUse = v; return this; }
        public Builder quotaRule(String v) { this.quotaRule = v; return this; }
        public Builder quotaRule2(String v) { this.quotaRule2 = v; return this; }
        public Builder quotaWarning(String v) { this.quotaWarning = v; return this; }
        public Builder quotaWarning2(String v) { this.quotaWarning2 = v; return this; }
        public Builder quotaWarning3(String v) { this.quotaWarning3 = v; return this; }
        public Builder pluginQuota(String v) { this.pluginQuota = v; return this; }
        public Builder pluginQuotaStatus(String v) { this.pluginQuotaStatus = v; return this; }
        public Builder pluginQuotaStatusSuccess(String v) { this.pluginQuotaStatusSuccess = v; return this; }
        public Builder pluginQuotaStatusNoUser(String v) { this.pluginQuotaStatusNoUser = v; return this; }
        public Builder pluginQuotaStatusOverQuota(String v) { this.pluginQuotaStatusOverQuota = v; return this; }
        public Builder serviceAuthUnixListenerPath(String v) { this.serviceAuthUnixListenerPath = v; return this; }
        public Builder serviceAuthUnixListenerUser(String v) { this.serviceAuthUnixListenerUser = v; return this; }
        public Builder serviceAuthUnixListenerGroup(String v) { this.serviceAuthUnixListenerGroup = v; return this; }
        public Builder serviceAuthUnixListenerMode(String v) { this.serviceAuthUnixListenerMode = v; return this; }
        public Builder serviceAuthInetListenerAddress(String v) { this.serviceAuthInetListenerAddress = v; return this; }
        public Builder serviceAuthInetListenerPort(String v) { this.serviceAuthInetListenerPort = v; return this; }
        public Builder serviceLmtpUnixListenerPath(String v) { this.serviceLmtpUnixListenerPath = v; return this; }
        public Builder serviceLmtpUnixListenerUser(String v) { this.serviceLmtpUnixListenerUser = v; return this; }
        public Builder serviceLmtpUnixListenerGroup(String v) { this.serviceLmtpUnixListenerGroup = v; return this; }
        public Builder serviceLmtpUnixListenerMode(String v) { this.serviceLmtpUnixListenerMode = v; return this; }

        public DovecotConfig build() {
            return new DovecotConfig(
                listen, protocols, mailLocation, mailUid, mailGid, mailPrivilegedGroup,
                firstValidUid, lastValidUid, authMechanisms, passdbDriver, passdbArgs,
                userdbDriver, userdbArgs, sslCert, sslKey, sslRequireCrl, sslMinProtocol,
                sslCipherList, sslPreferServerCiphers, namespaceInboxPrefix, namespaceInboxSeparator,
                namespaceInboxInbox, mailboxDraftsSpecialUse, mailboxJunkSpecialUse,
                mailboxTrashSpecialUse, mailboxSentSpecialUse, mailboxArchiveSpecialUse,
                quotaRule, quotaRule2, quotaWarning, quotaWarning2, quotaWarning3,
                pluginQuota, pluginQuotaStatus, pluginQuotaStatusSuccess, pluginQuotaStatusNoUser,
                pluginQuotaStatusOverQuota, serviceAuthUnixListenerPath, serviceAuthUnixListenerUser,
                serviceAuthUnixListenerGroup, serviceAuthUnixListenerMode, serviceAuthInetListenerAddress,
                serviceAuthInetListenerPort, serviceLmtpUnixListenerPath, serviceLmtpUnixListenerUser,
                serviceLmtpUnixListenerGroup, serviceLmtpUnixListenerMode
            );
        }
    }

    public String toDovecotConf() {

        String sb = "# Dovecot configuration - Generated by mailctl\n" +
                "# DO NOT EDIT DIRECTLY - Changes will be overwritten\n\n" +
                "listen = " + listen + "\n" +
                "protocols = " + protocols + "\n\n" +
                "mail_location = " + mailLocation + "\n" +
                "mail_uid = " + mailUid + "\n" +
                "mail_gid = " + mailGid + "\n" +
                "mail_privileged_group = " + mailPrivilegedGroup + "\n" +
                "first_valid_uid = " + firstValidUid + "\n" +
                "last_valid_uid = " + lastValidUid + "\n\n" +
                "auth_mechanisms = " + authMechanisms + "\n\n" +
                "passdb {\n" +
                "  driver = " + passdbDriver + "\n" +
                "  args = " + passdbArgs + "\n" +
                "}\n\n" +
                "userdb {\n" +
                "  driver = " + userdbDriver + "\n" +
                "  args = " + userdbArgs + "\n" +
                "}\n\n" +
                "ssl_cert = </etc/ssl/certs/mailcert.pem\n" +
                "ssl_key = </etc/ssl/private/mailkey.pem\n" +
                "ssl_require_crl = " + sslRequireCrl + "\n" +
                "ssl_min_protocol = " + sslMinProtocol + "\n" +
                "ssl_cipher_list = " + sslCipherList + "\n" +
                "ssl_prefer_server_ciphers = " + sslPreferServerCiphers + "\n\n" +
                "namespace inbox {\n" +
                "  prefix = " + namespaceInboxPrefix + "\n" +
                "  separator = " + namespaceInboxSeparator + "\n" +
                "  inbox = " + namespaceInboxInbox + "\n" +
                "  mailbox Drafts {\n" +
                "    special_use = " + mailboxDraftsSpecialUse + "\n" +
                "  }\n" +
                "  mailbox Junk {\n" +
                "    special_use = " + mailboxJunkSpecialUse + "\n" +
                "  }\n" +
                "  mailbox Trash {\n" +
                "    special_use = " + mailboxTrashSpecialUse + "\n" +
                "  }\n" +
                "  mailbox Sent {\n" +
                "    special_use = " + mailboxSentSpecialUse + "\n" +
                "  }\n" +
                "  mailbox Archive {\n" +
                "    special_use = " + mailboxArchiveSpecialUse + "\n" +
                "  }\n" +
                "}\n\n" +
                "plugin {\n" +
                "  " + pluginQuota + "\n" +
                "  " + pluginQuotaStatus + "\n" +
                "  " + pluginQuotaStatusSuccess + "\n" +
                "  " + pluginQuotaStatusNoUser + "\n" +
                "  " + pluginQuotaStatusOverQuota + "\n" +
                "  quota_rule = " + quotaRule + "\n" +
                "  quota_rule2 = " + quotaRule2 + "\n" +
                "  " + quotaWarning + "\n" +
                "  " + quotaWarning2 + "\n" +
                "  " + quotaWarning3 + "\n" +
                "}\n\n" +
                "service auth {\n" +
                "  unix_listener " + serviceAuthUnixListenerPath + " {\n" +
                "    user = " + serviceAuthUnixListenerUser + "\n" +
                "    group = " + serviceAuthUnixListenerGroup + "\n" +
                "    mode = " + serviceAuthUnixListenerMode + "\n" +
                "  }\n" +
                "  inet_listener {\n" +
                "    address = " + serviceAuthInetListenerAddress + "\n" +
                "    port = " + serviceAuthInetListenerPort + "\n" +
                "  }\n" +
                "}\n\n" +
                "service lmtp {\n" +
                "  unix_listener " + serviceLmtpUnixListenerPath + " {\n" +
                "    user = " + serviceLmtpUnixListenerUser + "\n" +
                "    group = " + serviceLmtpUnixListenerGroup + "\n" +
                "    mode = " + serviceLmtpUnixListenerMode + "\n" +
                "  }\n" +
                "}\n";
        
        return sb;
    }
    
    public String toSqlConf(String dbUrl, String dbUser, String dbPassword) {

        String sb = "# Dovecot SQL configuration - Generated by mailctl\n" +
                "# DO NOT EDIT DIRECTLY - Changes will be overwritten\n\n" +
                "driver = pgsql\n" +
                "connect = host=" + extractHost(dbUrl) + " dbname=" + extractDbName(dbUrl) +
                " user=" + dbUser + " password=" + dbPassword + "\n" +
                "default_pass_scheme = BLF-CRYPT\n\n" +
                "password_query = SELECT email AS user, password_hash AS password \\\n" +
                "  FROM users WHERE email = '%u' AND status = 'ACTIVE'\n\n" +
                "user_query = SELECT email AS user, mailbox_path AS home, \\\n" +
                "  'maildir:' || mailbox_path AS mail, \\\n" +
                "  quota_rule AS quota_rule \\\n" +
                "  FROM users WHERE email = '%u' AND status = 'ACTIVE'\n";
        
        return sb;
    }
    
    private String extractHost(String url) {
        // jdbc:postgresql://host:port/db
        String[] parts = url.split("://");
        if (parts.length < 2) return "localhost";
        String hostPort = parts[1].split("/")[0];
        return hostPort.split(":")[0];
    }
    
    private String extractDbName(String url) {
        String[] parts = url.split("/");
        return parts[parts.length - 1];
    }
}