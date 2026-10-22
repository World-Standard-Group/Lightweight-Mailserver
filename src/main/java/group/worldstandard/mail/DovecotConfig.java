package group.worldstandard.mail;

/**
 * Renders Dovecot's {@code dovecot.conf} and SQL auth configuration.
 *
 * <p><b>Target: Dovecot 2.3.21.</b> The image ships no {@code conf.d/} tree
 * under {@code /etc/dovecot}, so the generated file has to be
 * self-contained: nothing is inherited from the package defaults and every
 * setting the daemon needs must be present here.</p>
 *
 * <p><b>Secure client access.</b> IMAP and POP3 are both offered, each with a
 * plaintext listener for STARTTLS and an implicit-TLS listener:</p>
 * <pre>
 *   service imap-login { inet_listener imap { port = 143 }
 *                         inet_listener imaps { port = 993 ; ssl = yes } }
 *   service pop3-login { inet_listener pop3 { port = 110 }
 *                         inet_listener pop3s { port = 995 ; ssl = yes } }
 * </pre>
 *
 * <p>{@code ssl = required} at root level means authentication is only allowed
 * over an encrypted channel. It does not disable STARTTLS: a session that
 * completed STARTTLS is {@code secured}, so plain 143/110 remain usable once
 * the client upgrades. {@code disable_plaintext_auth = yes} is stated
 * explicitly as well; it is the default in 2.3.21 and it is the setting that
 * makes an unencrypted AUTH attempt fail.</p>
 *
 * <p><b>Per-user quota.</b> The quota plugin is only loaded when
 * {@code mail_plugins} names it - a {@code plugin { }} block alone sets
 * environment values and loads nothing. The per-mailbox limit comes from the
 * userdb: a result column aliased {@code quota_rule} becomes the plugin
 * environment value of the same name, because a key that is not a valid
 * settings key is rewritten to {@code plugin/<key>} on the way in.</p>
 *
 * <p><b>Ownership.</b> {@code mail_uid} must be at or above
 * {@code first_valid_uid}, or every mailbox open fails with "Mail access for
 * users with UID x not permitted". Both are pinned from
 * {@link MailOwner} so that Postfix's delivery agent and Dovecot agree on
 * the uid that owns the files in the shared volume.</p>
 */
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
    String authUsernameFormat,
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
    String mailPlugins,
    String quotaRule,
    String quotaRule2,
    String quotaGrace,
    String pluginQuota,
    String pluginQuotaStatusNoUser,
    String pluginQuotaStatusSuccess,
    String pluginQuotaStatusOverQuota,
    String authSocketPath,
    String authSocketMode,
    String lmtpSocketPath,
    String lmtpSocketMode,
    String imapListenerPort,
    String imapsListenerPort,
    String pop3ListenerPort,
    String pop3sListenerPort,
    String mailMaxUseripConnections,
    String pop3UidlFormat,
    String pop3SaveUidl,
    String pop3ClientWorkarounds,
    String baseDir,
    String stateDir,
    String logPath
) {
    public static class Builder {
        private String listen = "*";
        private String protocols = "imap pop3";
        private String mailLocation = "maildir:/var/mail/%d/%n";
        private String mailUid = String.valueOf(MailOwner.UID);
        private String mailGid = String.valueOf(MailOwner.GID);
        private String mailPrivilegedGroup = MailOwner.GROUP;
        // Must be <= mail_uid, or Dovecot refuses to open the mailbox.
        private String firstValidUid = String.valueOf(MailOwner.UID);
        // 0 means "no upper bound" in 2.3.21.
        private String lastValidUid = "0";
        private String authMechanisms = "plain login";
        private String authUsernameFormat = "%Lu";
        private String passdbDriver = "sql";
        private String passdbArgs = "/etc/dovecot/dovecot-sql.conf.ext";
        private String userdbDriver = "sql";
        private String userdbArgs = "/etc/dovecot/dovecot-sql.conf.ext";
        private String sslCert = "/etc/ssl/certs/mailcert.pem";
        private String sslKey = "/etc/ssl/private/mailkey.pem";
        private String sslRequireCrl = "no";
        private String sslMinProtocol = "TLSv1.2";
        private String sslCipherList = "ALL:!kRSA:!SRP:!kDHd:!DSS:!aNULL:!eNULL:!EXPORT:!DES:!3DES:!MD5:!PSK:!RC4:!ADH:!LOW@STRENGTH";
        private String sslPreferServerCiphers = "no";
        private String namespaceInboxPrefix = "";
        private String namespaceInboxSeparator = "/";
        private String namespaceInboxInbox = "yes";
        private String mailboxDraftsSpecialUse = "\\Drafts";
        private String mailboxJunkSpecialUse = "\\Junk";
        private String mailboxTrashSpecialUse = "\\Trash";
        private String mailboxSentSpecialUse = "\\Sent";
        private String mailboxArchiveSpecialUse = "\\Archive";
        // The quota module is only loaded when mail_plugins names it.
        private String mailPlugins = "quota";
        private String quotaRule = "*:storage=1G";
        private String quotaRule2 = "*:messages=100000";
        private String quotaGrace = "10%%";
        private String pluginQuota = "quota = maildir:User quota";
        private String pluginQuotaStatusNoUser = "quota_status_nouser = DUNNO";
        private String pluginQuotaStatusSuccess = "quota_status_success = DUNNO";
        private String pluginQuotaStatusOverQuota = "quota_status_overquota = 552 5.2.2 Mailbox is over quota";
        // The auth socket is shared with the Postfix container through a
        // volume, so it is an absolute path rather than the usual
        // /var/spool/postfix/private/auth.
        private String authSocketPath = "/run/mailctl-sasl/auth";
        // 0666: the socket lives in a volume only the two mail containers
        // mount, and Postfix's smtpd runs unprivileged, so it needs to be
        // able to connect. The socket authenticates; it does not grant
        // anything by itself.
        private String authSocketMode = "0666";
        // LMTP is optional: this server delivers through Postfix's virtual
        // transport into the shared maildir, so Dovecot never needs an LMTP
        // socket. It stays off unless a deployment asks for it.
        private String lmtpSocketPath = "";
        private String lmtpSocketMode = "0600";
        private String imapListenerPort = "143";
        private String imapsListenerPort = "993";
        private String pop3ListenerPort = "110";
        private String pop3sListenerPort = "995";
        // Outlook opens many parallel IMAP connections per user and IP; the
        // 2.3.21 default of 10 is easily exceeded.
        private String mailMaxUseripConnections = "100";
        private String pop3UidlFormat = "%08Xu%08Xv";
        private String pop3SaveUidl = "yes";
        private String pop3ClientWorkarounds = "outlook-no-nuls";
        private String baseDir = "/run/dovecot";
        private String stateDir = "/var/lib/dovecot";
        private String logPath = "/dev/stdout";

        public Builder listen(String v) { this.listen = v; return this; }
        public Builder protocols(String v) { this.protocols = v; return this; }
        public Builder mailLocation(String v) { this.mailLocation = v; return this; }
        public Builder mailUid(String v) { this.mailUid = v; return this; }
        public Builder mailGid(String v) { this.mailGid = v; return this; }
        public Builder mailPrivilegedGroup(String v) { this.mailPrivilegedGroup = v; return this; }
        public Builder firstValidUid(String v) { this.firstValidUid = v; return this; }
        public Builder lastValidUid(String v) { this.lastValidUid = v; return this; }
        public Builder authMechanisms(String v) { this.authMechanisms = v; return this; }
        public Builder authUsernameFormat(String v) { this.authUsernameFormat = v; return this; }
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
        public Builder mailPlugins(String v) { this.mailPlugins = v; return this; }
        public Builder quotaRule(String v) { this.quotaRule = v; return this; }
        public Builder quotaRule2(String v) { this.quotaRule2 = v; return this; }
        public Builder quotaGrace(String v) { this.quotaGrace = v; return this; }
        public Builder pluginQuota(String v) { this.pluginQuota = v; return this; }
        public Builder pluginQuotaStatusNoUser(String v) { this.pluginQuotaStatusNoUser = v; return this; }
        public Builder pluginQuotaStatusSuccess(String v) { this.pluginQuotaStatusSuccess = v; return this; }
        public Builder pluginQuotaStatusOverQuota(String v) { this.pluginQuotaStatusOverQuota = v; return this; }
        public Builder authSocketPath(String v) { this.authSocketPath = v; return this; }
        public Builder authSocketMode(String v) { this.authSocketMode = v; return this; }
        public Builder lmtpSocketPath(String v) { this.lmtpSocketPath = v; return this; }
        public Builder lmtpSocketMode(String v) { this.lmtpSocketMode = v; return this; }
        public Builder imapListenerPort(String v) { this.imapListenerPort = v; return this; }
        public Builder imapsListenerPort(String v) { this.imapsListenerPort = v; return this; }
        public Builder pop3ListenerPort(String v) { this.pop3ListenerPort = v; return this; }
        public Builder pop3sListenerPort(String v) { this.pop3sListenerPort = v; return this; }
        public Builder mailMaxUseripConnections(String v) { this.mailMaxUseripConnections = v; return this; }
        public Builder pop3UidlFormat(String v) { this.pop3UidlFormat = v; return this; }
        public Builder pop3SaveUidl(String v) { this.pop3SaveUidl = v; return this; }
        public Builder pop3ClientWorkarounds(String v) { this.pop3ClientWorkarounds = v; return this; }
        public Builder baseDir(String v) { this.baseDir = v; return this; }
        public Builder stateDir(String v) { this.stateDir = v; return this; }
        public Builder logPath(String v) { this.logPath = v; return this; }

        public DovecotConfig build() {
            return new DovecotConfig(
                listen, protocols, mailLocation, mailUid, mailGid, mailPrivilegedGroup,
                firstValidUid, lastValidUid, authMechanisms, authUsernameFormat,
                passdbDriver, passdbArgs, userdbDriver, userdbArgs,
                sslCert, sslKey, sslRequireCrl, sslMinProtocol, sslCipherList, sslPreferServerCiphers,
                namespaceInboxPrefix, namespaceInboxSeparator, namespaceInboxInbox,
                mailboxDraftsSpecialUse, mailboxJunkSpecialUse, mailboxTrashSpecialUse,
                mailboxSentSpecialUse, mailboxArchiveSpecialUse,
                mailPlugins, quotaRule, quotaRule2, quotaGrace,
                pluginQuota, pluginQuotaStatusNoUser, pluginQuotaStatusSuccess, pluginQuotaStatusOverQuota,
                authSocketPath, authSocketMode, lmtpSocketPath, lmtpSocketMode,
                imapListenerPort, imapsListenerPort, pop3ListenerPort, pop3sListenerPort,
                mailMaxUseripConnections, pop3UidlFormat, pop3SaveUidl, pop3ClientWorkarounds,
                baseDir, stateDir, logPath
            );
        }
    }

    public String toDovecotConf() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Dovecot configuration - Generated by mailctl\n");
        sb.append("# DO NOT EDIT DIRECTLY - Changes will be overwritten\n");
        sb.append("# Target: Dovecot 2.3.21 - self-contained, the image ships no conf.d/.\n\n");

        sb.append("# --- Master ------------------------------------------------------\n");
        sb.append("# pop3 must be listed or the pop3-login service is never started.\n");
        sb.append("protocols = ").append(protocols).append("\n");
        sb.append("listen = ").append(listen).append("\n");
        sb.append("# Authentication is only allowed over an encrypted channel. A session\n");
        sb.append("# that completed STARTTLS counts as secured, so 143/110 stay usable.\n");
        sb.append("ssl = required\n");
        sb.append("base_dir = ").append(baseDir).append("\n");
        sb.append("state_dir = ").append(stateDir).append("\n");
        sb.append("log_path = ").append(logPath).append("\n");
        sb.append("info_log_path = ").append(logPath).append("\n");
        sb.append("debug_log_path = ").append(logPath).append("\n\n");

        sb.append("# --- Mail storage ------------------------------------------------\n");
        sb.append("mail_location = ").append(mailLocation).append("\n");
        sb.append("mail_uid = ").append(mailUid).append("\n");
        sb.append("mail_gid = ").append(mailGid).append("\n");
        sb.append("mail_privileged_group = ").append(mailPrivilegedGroup).append("\n");
        sb.append("# first_valid_uid must admit mail_uid, or every mailbox open fails\n");
        sb.append("# with \"Mail access for users with UID x not permitted\".\n");
        sb.append("first_valid_uid = ").append(firstValidUid).append("\n");
        sb.append("last_valid_uid = ").append(lastValidUid).append("\n\n");

        sb.append("# --- Authentication ----------------------------------------------\n");
        sb.append("# The default in 2.3.21 is yes; stated explicitly so the intent is\n");
        sb.append("# visible: no AUTH on an unencrypted channel.\n");
        sb.append("disable_plaintext_auth = yes\n");
        sb.append("auth_mechanisms = ").append(authMechanisms).append("\n");
        // %Lu keeps the whole lowercased address, so a principal that is
        // addressed as user@domain is found under the same key in the SQL.
        sb.append("auth_username_format = ").append(authUsernameFormat).append("\n\n");
        sb.append("passdb {\n");
        sb.append("  driver = ").append(passdbDriver).append("\n");
        sb.append("  args = ").append(passdbArgs).append("\n");
        sb.append("}\n\n");
        sb.append("userdb {\n");
        sb.append("  driver = ").append(userdbDriver).append("\n");
        sb.append("  args = ").append(userdbArgs).append("\n");
        sb.append("}\n\n");

        sb.append("# --- TLS ---------------------------------------------------------\n");
        sb.append("# The '<' form is required in 2.3.21: it inlines the PEM contents.\n");
        sb.append("ssl_cert = <").append(sslCert).append("\n");
        sb.append("ssl_key = <").append(sslKey).append("\n");
        sb.append("ssl_require_crl = ").append(sslRequireCrl).append("\n");
        sb.append("ssl_min_protocol = ").append(sslMinProtocol).append("\n");
        sb.append("ssl_cipher_list = ").append(sslCipherList).append("\n");
        sb.append("ssl_prefer_server_ciphers = ").append(sslPreferServerCiphers).append("\n\n");

        sb.append("# --- Namespace and special-use mailboxes --------------------------\n");
        sb.append("namespace inbox {\n");
        sb.append("  prefix = ").append(namespaceInboxPrefix).append("\n");
        sb.append("  separator = ").append(namespaceInboxSeparator).append("\n");
        sb.append("  inbox = ").append(namespaceInboxInbox).append("\n");
        sb.append("  mailbox Drafts {\n");
        sb.append("    special_use = ").append(mailboxDraftsSpecialUse).append("\n");
        sb.append("  }\n");
        sb.append("  mailbox Junk {\n");
        sb.append("    special_use = ").append(mailboxJunkSpecialUse).append("\n");
        sb.append("  }\n");
        sb.append("  mailbox Trash {\n");
        sb.append("    special_use = ").append(mailboxTrashSpecialUse).append("\n");
        sb.append("  }\n");
        sb.append("  mailbox Sent {\n");
        sb.append("    special_use = ").append(mailboxSentSpecialUse).append("\n");
        sb.append("  }\n");
        sb.append("  mailbox Archive {\n");
        sb.append("    special_use = ").append(mailboxArchiveSpecialUse).append("\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("# --- Quota --------------------------------------------------------\n");
        sb.append("# mail_plugins is what loads the quota module; the plugin block below\n");
        sb.append("# only sets environment values.\n");
        sb.append("mail_plugins = ").append(mailPlugins).append("\n");
        sb.append("plugin {\n");
        sb.append("  ").append(pluginQuota).append("\n");
        sb.append("  quota_rule = ").append(quotaRule).append("\n");
        sb.append("  quota_rule2 = ").append(quotaRule2).append("\n");
        sb.append("  quota_grace = ").append(quotaGrace).append("\n");
        sb.append("  ").append(pluginQuotaStatusNoUser).append("\n");
        sb.append("  ").append(pluginQuotaStatusSuccess).append("\n");
        sb.append("  ").append(pluginQuotaStatusOverQuota).append("\n");
        sb.append("}\n\n");

        sb.append("# --- Login listeners -----------------------------------------------\n");
        sb.append("# Both protocols, each with a STARTTLS port and an implicit-TLS port.\n");
        sb.append("service imap-login {\n");
        sb.append("  inet_listener imap {\n");
        sb.append("    port = ").append(imapListenerPort).append("\n");
        sb.append("  }\n");
        sb.append("  inet_listener imaps {\n");
        sb.append("    port = ").append(imapsListenerPort).append("\n");
        sb.append("    ssl = yes\n");
        sb.append("  }\n");
        sb.append("}\n\n");
        sb.append("service pop3-login {\n");
        sb.append("  inet_listener pop3 {\n");
        sb.append("    port = ").append(pop3ListenerPort).append("\n");
        sb.append("  }\n");
        sb.append("  inet_listener pop3s {\n");
        sb.append("    port = ").append(pop3sListenerPort).append("\n");
        sb.append("    ssl = yes\n");
        sb.append("  }\n");
        sb.append("}\n\n");

        sb.append("# --- Third-party client compatibility -------------------------------\n");
        sb.append("mail_max_userip_connections = ").append(mailMaxUseripConnections).append("\n");
        sb.append("pop3_uidl_format = ").append(pop3UidlFormat).append("\n");
        sb.append("pop3_save_uidl = ").append(pop3SaveUidl).append("\n");
        sb.append("pop3_client_workarounds = ").append(pop3ClientWorkarounds).append("\n\n");

        sb.append("# --- Services for Postfix ------------------------------------------\n");
        sb.append("# The auth socket is shared with the Postfix container through a\n");
        sb.append("# volume, so it is an absolute path in a shared directory.\n");
        sb.append("service auth {\n");
        sb.append("  unix_listener ").append(authSocketPath).append(" {\n");
        sb.append("    mode = ").append(authSocketMode).append("\n");
        sb.append("  }\n");
        sb.append("}\n");
        if (lmtpSocketPath != null && !lmtpSocketPath.isBlank()) {
            sb.append("service lmtp {\n");
            sb.append("  unix_listener ").append(lmtpSocketPath).append(" {\n");
            sb.append("    mode = ").append(lmtpSocketMode).append("\n");
            sb.append("  }\n");
            sb.append("}\n");
        }
        return sb.toString();
    }

    /**
     * Renders {@code dovecot-sql.conf.ext}.
     *
     * <p>Both queries read the {@code mail_principals} view, which presents
     * users and aliases as one row set. A user and an alias are both mail
     * principals: they both authenticate, and one of them may own a
     * mailbox.</p>
     *
     * <p>The passdb query admits every active principal that has a password,
     * whether or not it owns a mailbox, because a forwarding-only principal
     * still has to authenticate for outbound submission. The userdb query
     * admits only principals that own a mailbox, because that row is what
     * makes a mailbox openable: a principal with no mailbox resolves to no
     * row and is refused by the IMAP/POP3 front ends.</p>
     *
     * <p>{@code quota_rule} must be the column alias, not the column name:
     * the SQL driver uses the alias as the field name, and the quota plugin
     * only reads a field called {@code quota_rule}. The rule value uses the
     * {@code <mailbox mask>:<limits>} form the plugin parses.</p>
     */
    public String toSqlConf(String dbUrl, String dbUser, String dbPassword) {
        return "# Dovecot SQL configuration - Generated by mailctl\n" +
                "# DO NOT EDIT DIRECTLY - Changes will be overwritten\n\n" +
                "driver = pgsql\n" +
                "connect = host=" + extractHost(dbUrl) + " dbname=" + extractDbName(dbUrl) +
                " user=" + dbUser + " password=" + dbPassword + "\n" +
                // The stored value carries its own {BLF-CRYPT} prefix, which is
                // what selects the scheme; this is the fallback for a value
                // stored without one.
                "default_pass_scheme = BLF-CRYPT\n\n" +
                // Any active principal with a password may authenticate, so a
                // forwarding-only alias can send on port 587.
                "password_query = SELECT principal AS user, password_hash AS password \\\n" +
                "  FROM mail_principals WHERE principal = '%u' AND status = 'ACTIVE' \\\n" +
                "    AND password_hash <> ''\n\n" +
                // Only a principal that owns a mailbox resolves to a userdb
                // row, so only it can read mail.
                "user_query = SELECT principal AS user, home, mail, quota_rule \\\n" +
                "  FROM mail_principals WHERE principal = '%u' AND status = 'ACTIVE' \\\n" +
                "    AND has_mailbox\n";
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
