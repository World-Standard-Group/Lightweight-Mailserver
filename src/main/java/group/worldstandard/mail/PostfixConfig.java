package group.worldstandard.mail;

import java.util.List;
import java.util.Map;

    /**
     * Renders Postfix's {@code main.cf} and the three virtual map files.
     *  *
     *  * <p><b>Note:</b> the milter address defaults to {@code inet:localhost:11332} for
     *  * Rspamd. Under Docker, Postfix and Rspamd are separate containers, so this must
     *  * become {@code inet:rspamd:11332} or outbound mail is never filtered.</p>
     */
public record PostfixConfig(
    String myHostname,
    String myDomain,
    String myOrigin,
    List<String> myDestinations,
    String virtualMailboxBase,
    Map<String, String> virtualAliasMaps,
    Map<String, String> virtualMailboxMaps,
    Map<String, String> virtualMailboxDomains,
    String smtpdTlsCertFile,
    String smtpdTlsKeyFile,
    String smtpdTlsSecurityLevel,
    String smtpdSaslAuthEnable,
    String smtpdSaslType,
    String smtpdSaslPath,
    String smtpdRecipientRestrictions,
    String smtpdRelayRestrictions,
    String smtpdSenderRestrictions,
    String smtpdClientRestrictions,
    String smtpdHeloRestrictions,
    String smtpdDataRestrictions,
    String smtpdEtcRestrictions,
    String milterProtocol,
    String milterDefaultAction,
    String milterHost,
    Integer milterPort,
    String headerChecks,
    String bodyChecks,
    String mimeHeaderChecks,
    String nestedHeaderChecks
) {
    public static class Builder {
        private String myHostname = "mail.example.com";
        private String myDomain = "example.com";
        private String myOrigin = "$myDomain";
        private List<String> myDestinations = List.of("$myHostname", "localhost.$myDomain", "localhost");
        private String virtualMailboxBase = "/var/mail";
        private Map<String, String> virtualAliasMaps = Map.of();
        private Map<String, String> virtualMailboxMaps = Map.of();
        private Map<String, String> virtualMailboxDomains = Map.of();
        private String smtpdTlsCertFile = "/etc/ssl/certs/mailcert.pem";
        private String smtpdTlsKeyFile = "/etc/ssl/private/mailkey.pem";
        private String smtpdTlsSecurityLevel = "may";
        private String smtpdSaslAuthEnable = "yes";
        private String smtpdSaslType = "dovecot";
        private String smtpdSaslPath = "private/auth";
        private String smtpdRecipientRestrictions = "permit_mynetworks, permit_sasl_authenticated, reject_unauth_destination";
        // Relay only for authenticated senders: this is what lets a
        // forwarding-only user send outbound without turning the server into an
        // open relay.
        private String smtpdRelayRestrictions = "permit_mynetworks, permit_sasl_authenticated, reject_unauth_destination";
        private String smtpdSenderRestrictions = "";
        private String smtpdClientRestrictions = "";
        private String smtpdHeloRestrictions = "";
        private String smtpdDataRestrictions = "";
        private String smtpdEtcRestrictions = "";
        private String milterProtocol = "6";
        private String milterDefaultAction = "accept";
        // Compose service name by default. Under Docker, Rspamd is a sibling
        // container, so "localhost" would resolve to Postfix's own container and
        // the milter would never be reached.
        private String milterHost = "rspamd";
        private int milterPort = 11332;
        private String headerChecks = "";
        private String bodyChecks = "";
        private String mimeHeaderChecks = "";
        private String nestedHeaderChecks = "";

        public Builder myHostname(String v) { this.myHostname = v; return this; }
        public Builder myDomain(String v) { this.myDomain = v; return this; }
        public Builder myOrigin(String v) { this.myOrigin = v; return this; }
        public Builder myDestinations(List<String> v) { this.myDestinations = v; return this; }
        public Builder virtualMailboxBase(String v) { this.virtualMailboxBase = v; return this; }
        public Builder virtualAliasMaps(Map<String, String> v) { this.virtualAliasMaps = v; return this; }
        public Builder virtualMailboxMaps(Map<String, String> v) { this.virtualMailboxMaps = v; return this; }
        public Builder virtualMailboxDomains(Map<String, String> v) { this.virtualMailboxDomains = v; return this; }
        public Builder smtpdTlsCertFile(String v) { this.smtpdTlsCertFile = v; return this; }
        public Builder smtpdTlsKeyFile(String v) { this.smtpdTlsKeyFile = v; return this; }
        public Builder smtpdTlsSecurityLevel(String v) { this.smtpdTlsSecurityLevel = v; return this; }
        public Builder smtpdSaslAuthEnable(String v) { this.smtpdSaslAuthEnable = v; return this; }
        public Builder smtpdSaslType(String v) { this.smtpdSaslType = v; return this; }
        public Builder smtpdSaslPath(String v) { this.smtpdSaslPath = v; return this; }
        public Builder smtpdRecipientRestrictions(String v) { this.smtpdRecipientRestrictions = v; return this; }
        public Builder smtpdRelayRestrictions(String v) { this.smtpdRelayRestrictions = v; return this; }
        public Builder smtpdSenderRestrictions(String v) { this.smtpdSenderRestrictions = v; return this; }
        public Builder smtpdClientRestrictions(String v) { this.smtpdClientRestrictions = v; return this; }
        public Builder smtpdHeloRestrictions(String v) { this.smtpdHeloRestrictions = v; return this; }
        public Builder smtpdDataRestrictions(String v) { this.smtpdDataRestrictions = v; return this; }
        public Builder smtpdEtcRestrictions(String v) { this.smtpdEtcRestrictions = v; return this; }
        public Builder milterProtocol(String v) { this.milterProtocol = v; return this; }
        public Builder milterDefaultAction(String v) { this.milterDefaultAction = v; return this; }
        public Builder milterHost(String v) { this.milterHost = v; return this; }
        public Builder milterPort(int v) { this.milterPort = v; return this; }
        public Builder headerChecks(String v) { this.headerChecks = v; return this; }
        public Builder bodyChecks(String v) { this.bodyChecks = v; return this; }
        public Builder mimeHeaderChecks(String v) { this.mimeHeaderChecks = v; return this; }
        public Builder nestedHeaderChecks(String v) { this.nestedHeaderChecks = v; return this; }

        public PostfixConfig build() {
            return new PostfixConfig(
                myHostname, myDomain, myOrigin, myDestinations, virtualMailboxBase,
                virtualAliasMaps, virtualMailboxMaps, virtualMailboxDomains,
                smtpdTlsCertFile, smtpdTlsKeyFile, smtpdTlsSecurityLevel,
                smtpdSaslAuthEnable, smtpdSaslType, smtpdSaslPath,
                smtpdRecipientRestrictions, smtpdRelayRestrictions, smtpdSenderRestrictions, smtpdClientRestrictions,
                smtpdHeloRestrictions, smtpdDataRestrictions, smtpdEtcRestrictions,
                milterProtocol, milterDefaultAction, milterHost, milterPort,
                headerChecks, bodyChecks, mimeHeaderChecks, nestedHeaderChecks
            );
        }
    }

    public String toMainCf() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Postfix main.cf - Generated by mailctl\n");
        sb.append("# DO NOT EDIT DIRECTLY - Changes will be overwritten\n\n");
        
        sb.append("myhostname = ").append(myHostname).append("\n");
        sb.append("mydomain = ").append(myDomain).append("\n");
        sb.append("myorigin = ").append(myOrigin).append("\n");
        sb.append("mydestination = ").append(String.join(", ", myDestinations)).append("\n\n");
        
        sb.append("virtual_mailbox_base = ").append(virtualMailboxBase).append("\n");
        // Reference the compiled map files rather than inlining their contents.
        // Inline map syntax uses ", " as a key separator, which a multi-target
        // forwarding value cannot survive; the hash: files are also what the
        // virtual transport in master.cf reads.
        sb.append("virtual_alias_maps = hash:/etc/postfix/virtual_alias_maps\n");
        sb.append("virtual_mailbox_maps = hash:/etc/postfix/virtual_mailbox_maps\n");
        sb.append("virtual_mailbox_domains = hash:/etc/postfix/virtual_mailbox_domains\n");
        sb.append("sender_login_maps = hash:/etc/postfix/sender_login_maps\n\n");
        
        sb.append("smtpd_tls_cert_file = ").append(smtpdTlsCertFile).append("\n");
        sb.append("smtpd_tls_key_file = ").append(smtpdTlsKeyFile).append("\n");
        sb.append("smtpd_tls_security_level = ").append(smtpdTlsSecurityLevel).append("\n");
        // Outbound send policy. A forwarding-only user authenticates here (587)
        // and may only ever send as their own address, so a compromised or
        // misconfigured client cannot spoof another local sender.
        sb.append("smtpd_sender_restrictions = reject_sender_login_mismatch, reject_authenticated_sender_login_mismatch, permit\n");
        sb.append("smtpd_relay_restrictions = permit_mynetworks, permit_sasl_authenticated, reject_unauth_destination\n");
        sb.append("smtpd_tls_mandatory_protocols = !SSLv2, !SSLv3, !TLSv1, !TLSv1.1\n");
        sb.append("smtpd_tls_protocols = !SSLv2, !SSLv3, !TLSv1, !TLSv1.1\n");
        sb.append("tls_preempt_cipherlist = off\n");
        sb.append("tls_high_cipherlist = HIGH:!aNULL:!kRSA:!PSK:!SRP:!MD5:!RC4\n\n");
        
        sb.append("smtpd_sasl_auth_enable = ").append(smtpdSaslAuthEnable).append("\n");
        sb.append("smtpd_sasl_type = ").append(smtpdSaslType).append("\n");
        sb.append("smtpd_sasl_path = ").append(smtpdSaslPath).append("\n\n");
        
        sb.append("smtpd_recipient_restrictions = ").append(smtpdRecipientRestrictions).append("\n");
        if (!smtpdSenderRestrictions.isEmpty()) sb.append("smtpd_sender_restrictions = ").append(smtpdSenderRestrictions).append("\n");
        if (!smtpdClientRestrictions.isEmpty()) sb.append("smtpd_client_restrictions = ").append(smtpdClientRestrictions).append("\n");
        if (!smtpdHeloRestrictions.isEmpty()) sb.append("smtpd_helo_restrictions = ").append(smtpdHeloRestrictions).append("\n");
        if (!smtpdDataRestrictions.isEmpty()) sb.append("smtpd_data_restrictions = ").append(smtpdDataRestrictions).append("\n");
        if (!smtpdEtcRestrictions.isEmpty()) sb.append("smtpd_etrn_restrictions = ").append(smtpdEtcRestrictions).append("\n\n");
        
        sb.append("milter_protocol = ").append(milterProtocol).append("\n");
        sb.append("milter_default_action = ").append(milterDefaultAction).append("\n");
        // The same milter on both paths: non_smtpd_milters is what applies DKIM
        // /SPF /DMARC checks to INBOUND mail, which is what a forwarding setup
        // depends on.
        sb.append("smtpd_milters = inet:").append(milterHost).append(":").append(milterPort).append("\n");
        sb.append("non_smtpd_milters = inet:").append(milterHost).append(":").append(milterPort).append("\n\n");
        
        if (!headerChecks.isEmpty()) sb.append("header_checks = ").append(headerChecks).append("\n");
        if (!bodyChecks.isEmpty()) sb.append("body_checks = ").append(bodyChecks).append("\n");
        if (!mimeHeaderChecks.isEmpty()) sb.append("mime_header_checks = ").append(mimeHeaderChecks).append("\n");
        if (!nestedHeaderChecks.isEmpty()) sb.append("nested_header_checks = ").append(nestedHeaderChecks).append("\n");
        
        return sb.toString();
    }

    public String toVirtualAliasMaps() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Virtual alias maps - Generated by mailctl\n");
        for (Map.Entry<String, String> entry : virtualAliasMaps.entrySet()) {
            sb.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    public String toVirtualMailboxMaps() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Virtual mailbox maps - Generated by mailctl\n");
        for (Map.Entry<String, String> entry : virtualMailboxMaps.entrySet()) {
            sb.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    public String toVirtualMailboxDomains() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Virtual mailbox domains - Generated by mailctl\n");
        for (Map.Entry<String, String> entry : virtualMailboxDomains.entrySet()) {
            sb.append(entry.getKey()).append("\t").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
    /**
     * Renders {@code master.cf}.
     *
     * <p>This is not optional: {@code postfix check} fails without it, and the
     * submission listeners that let a user authenticate and send are declared
     * here rather than in {@code main.cf}. Port 25 is plain SMTP for inbound
     * mail; 587 is STARTTLS submission (authenticated senders only, which is how
     * a forwarding-only user sends without becoming an open relay); 465 is
     * implicit TLS for clients that prefer it.</p>
     *
     * <p>SASL authentication is delegated to Dovecot over the
     * {@code private/auth} Unix socket, so credentials are checked against the
     * same database mailctl writes.</p>
     */
    public String toMasterCf() {
        return """
            # master.cf - Generated by mailctl
            # DO NOT EDIT DIRECTLY - Changes will be overwritten
            #
            # service     type  private unpriv  chroot  wakeup  maxproc command
            smtp          inet  n       -       y       -       -       smtpd
            # Submission: authenticated senders over STARTTLS.
            submission    inet  n       -       y       -       -       smtpd
                -o syslog_name=postfix/submission
                -o smtpd_tls_security_level=encrypt
                -o smtpd_tls_auth_only=yes
                -o smtpd_sasl_auth_enable=yes
                -o smtpd_sasl_type=dovecot
                -o smtpd_sasl_path=private/auth
                -o smtpd_client_restrictions=permit_sasl_authenticated,reject
                -o smtpd_relay_restrictions=permit_sasl_authenticated,reject
                -o cleanup_service_name=submission-cleanup
            # Implicit TLS submission (SMTPS) on 465.
            smtps         inet  n       -       y       -       -       smtpd
                -o syslog_name=postfix/smtps
                -o smtpd_tls_wrappermode=yes
                -o smtpd_sasl_auth_enable=yes
                -o smtpd_tls_auth_only=yes
                -o smtpd_sasl_type=dovecot
                -o smtpd_sasl_path=private/auth
                -o smtpd_client_restrictions=permit_sasl_authenticated,reject
                -o smtpd_relay_restrictions=permit_sasl_authenticated,reject
                -o cleanup_service_name=submission-cleanup
            # Maps the authenticated login back to an address, so
            # reject_sender_login_mismatch can stop a user sending as someone else.
            smtpd         unix  -       -       y       -       -       smtp
                -o smtpd_client_restrictions=permit_sasl_authenticated,reject
                -o smtpd_relay_restrictions=permit_sasl_authenticated,reject
                -o cleanup_service_name=submission-cleanup
            # Rewrite forwarding-only recipients to their external targets.
            virtual       unix  -       n       n       -       -       virtual
                -o smtpd_generic_maps=regexp:/etc/postfix/virtual_generic
                -o smtpd_address_maps=hash:/etc/postfix/virtual_alias_maps
                -o virtual_alias_domains=hash:/etc/postfix/virtual_mailbox_domains
                -o virtual_mailbox_domains=hash:/etc/postfix/virtual_mailbox_domains
                -o virtual_mailbox_maps=hash:/etc/postfix/virtual_mailbox_maps
                -o virtual_transport=virtual
                -o virtual_delivery_transport=virtual
                -o virtual_mailbox_base=%%MAIL_BASE%%
            local         unix  -       n       n       -       -       local
            -o syslog_name=postfix/$service_name
            """;
    }
}
