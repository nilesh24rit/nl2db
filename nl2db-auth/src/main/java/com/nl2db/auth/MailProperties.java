package com.nl2db.auth;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mail properties configured from environment variables.
 * <p>
 * Loads SMTP sender configuration from NL2DB_MAIL_SENDER environment variable.
 * Default values are provided for local development.
 */
@Component
public class MailProperties {

    @Value("${NL2DB_MAIL_SENDER:nl2db@localhost}")
    private String mailSender;

    @Value("${NL2DB_MAIL_HOST:localhost}")
    private String mailHost;

    @Value("${NL2DB_MAIL_PORT:25}")
    private int mailPort;

    @Value("${NL2DB_MAIL_USERNAME:}")
    private String mailUsername;

    @Value("${NL2DB_MAIL_PASSWORD:}")
    private String mailPassword;

    @Value("${NL2DB_MAIL_TLS_ENABLED:true}")
    private boolean tlsEnabled;

    /**
     * No-arg constructor required by Spring @Component with @Value injection.
     */
    public MailProperties() {
        // Default constructor
    }

    public String getMailSender() {
        return mailSender;
    }

    public void setMailSender(String mailSender) {
        this.mailSender = mailSender;
    }

    public String getMailHost() {
        return mailHost;
    }

    public void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    public int getMailPort() {
        return mailPort;
    }

    public void setMailPort(int mailPort) {
        this.mailPort = mailPort;
    }

    public String getMailUsername() {
        return mailUsername;
    }

    public void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    public String getMailPassword() {
        return mailPassword;
    }

    public void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    @PreDestroy
    public void cleanup() {
        // Clear sensitive mail configuration from memory
    }
}