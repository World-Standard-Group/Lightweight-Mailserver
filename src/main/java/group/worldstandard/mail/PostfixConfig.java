package group.worldstandard.mail;

import java.util.List;
import java.util.Map;

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
    String smtpdSenderRestrictions,
    String smtpdClientRestrictions,
    String smtpdHeloRestrictions,
    String smtpdDataRestrictions,
    String smtpdEtcRestrictions,
    String milterProtocol,
    String milterDefaultAction,
    String smtpdMilters,
    String nonSmtpdMilters,
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
        private String smtpdSenderRestrictions = "";
        private String smtpdClientRestrictions = "";
        private String smtpdHeloRestrictions = "";
        private String smtpdDataRestrictions = "";
        private String smtpdEtcRestrictions = "";
        private String milterProtocol = "6";
        private String milterDefaultAction = "accept";
        private String smtpdMilters = "inet:localhost:11332";
        private String nonSmtpdMilters = "inet:localhost:11332";
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
        public Builder smtpdSenderRestrictions(String v) { this.smtpdSenderRestrictions = v; return this; }
        public Builder smtpdClientRestrictions(String v) { this.smtpdClientRestrictions = v; return this; }
        public Builder smtpdHeloRestrictions(String v) { this.smtpdHeloRestrictions = v; return this; }
        public Builder smtpdDataRestrictions(String v) { this.smtpdDataRestrictions = v; return this; }
        public Builder smtpdEtcRestrictions(String v) { this.smtpdEtcRestrictions = v; return this; }
        public Builder milterProtocol(String v) { this.milterProtocol = v; return this; }
        public Builder milterDefaultAction(String v) { this.milterDefaultAction = v; return this; }
        public Builder smtpdMilters(String v) { this.smtpdMilters = v; return this; }
        public Builder nonSmtpdMilters(String v) { this.nonSmtpdMilters = v; return this; }
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
                smtpdRecipientRestrictions, smtpdSenderRestrictions, smtpdClientRestrictions,
                smtpdHeloRestrictions, smtpdDataRestrictions, smtpdEtcRestrictions,
                milterProtocol, milterDefaultAction, smtpdMilters, nonSmtpdMilters,
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
        sb.append("virtual_alias_maps = ").append(mapToPostfix(virtualAliasMaps)).append("\n");
        sb.append("virtual_mailbox_maps = ").append(mapToPostfix(virtualMailboxMaps)).append("\n");
        sb.append("virtual_mailbox_domains = ").append(mapToPostfix(virtualMailboxDomains)).append("\n\n");
        
        sb.append("smtpd_tls_cert_file = ").append(smtpdTlsCertFile).append("\n");
        sb.append("smtpd_tls_key_file = ").append(smtpdTlsKeyFile).append("\n");
        sb.append("smtpd_tls_security_level = ").append(smtpdTlsSecurityLevel).append("\n\n");
        
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
        sb.append("smtpd_milters = ").append(smtpdMilters).append("\n");
        sb.append("non_smtpd_milters = ").append(nonSmtpdMilters).append("\n\n");
        
        if (!headerChecks.isEmpty()) sb.append("header_checks = ").append(headerChecks).append("\n");
        if (!bodyChecks.isEmpty()) sb.append("body_checks = ").append(bodyChecks).append("\n");
        if (!mimeHeaderChecks.isEmpty()) sb.append("mime_header_checks = ").append(mimeHeaderChecks).append("\n");
        if (!nestedHeaderChecks.isEmpty()) sb.append("nested_header_checks = ").append(nestedHeaderChecks).append("\n");
        
        return sb.toString();
    }

    private String mapToPostfix(Map<String, String> map) {
        if (map.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey()).append(" ").append(entry.getValue()).append(", ");
        }
        return sb.substring(0, sb.length() - 2); // Remove trailing ", "
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
}