package com.nl2db.auth;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real SMTP email delivery implementation using Spring Boot's JavaMailSender.
 * <p>
 * Responsibilities:
 * - Create MIME messages with plain-text and HTML alternatives.
 * - Configure TLS settings from validated configuration.
 * - Handle provider failures without exposing internal SMTP errors.
 * - Never log SMTP passwords or complete verification links.
 * <p>
 * SMTP configuration is externalized through Spring Boot properties
 * (app.mail.*). Credentials must never be committed to source control.
 * The sender address and TLS settings are configured at application startup.
 * <p>
 * This implementation is activated as the default EmailDeliveryService bean.
 * A test-only adapter may be activated separately via a profile-specific
 * bean definition, but it must not be active in production.
 */
@Service
public class SmtpEmailDeliveryService implements EmailDeliveryService {

    private final JavaMailSender mailSender;
    private final String mailSenderAddress;

    /**
     * Constructs an SMTP email delivery service with the given mail sender.
     *
     * @param mailSender      Spring JavaMailSender (configured with SMTP settings)
     * @param mailSenderAddress the verified sender address
     */
    public SmtpEmailDeliveryService(JavaMailSender mailSender,
                                    @Value("${app.mail.sender:nl2db@localhost}") String mailSenderAddress) {
        this.mailSender = mailSender;
        this.mailSenderAddress = mailSenderAddress;
    }

    /**
     * Send a simple email message with plain-text and optional HTML content.
     * <p>
     * The method creates a MIME message with both text/plain and text/html
     * parts. If HTML content is null, only plain-text is sent.
     * Provider failures (e.g., connection refused, authentication error)
     * are re-thrown as MailException without exposing internal SMTP details.
     *
     * @param from    sender email address
     * @param to      recipient email address
     * @param subject email subject
     * @param text    plain-text body content
     * @param html    HTML body content (may be null)
     * @return true if the email was accepted for delivery
     * @throws MailException if the email cannot be sent
     */
    @Transactional
    @Override
    public boolean sendEmail(String from, String to, String subject, String text, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);

            if (html != null) {
                helper.setText(text, html);
            } else {
                helper.setText(text);
            }

            mailSender.send(message);
            return true;
        } catch (MessagingException e) {
            throw new MailSendException("Email delivery failed; underlying cause not exposed", e);
        }
    }
}