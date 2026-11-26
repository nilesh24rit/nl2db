package com.nl2db.auth;

import org.springframework.mail.MailException;
import org.springframework.stereotype.Service;

/**
 * Application-facing email delivery interface.
 * <p>
 * Defines the contract for sending email messages. The implementation
 * is injected via Spring, allowing the delivery provider to be swapped
 * (e.g., SMTP via JavaMailSender, test adapter, etc.) without changing
 * the verification workflow code.
 * <p>
 * Responsibilities:
 * - Send a simple email message (from, to, subject, text, html).
 * - Handle provider failures without exposing internal errors.
 * - Never log SMTP passwords or complete verification links.
 * <p>
 * This interface stays focused on email delivery and does not include
 * verification-specific content construction; that responsibility lies
 * with the verification token service.
 */
public interface EmailDeliveryService {

    /**
     * Send a simple email message.
     *
     * @param from    sender email address
     * @param to      recipient email address
     * @param subject email subject
     * @param text    plain-text body content
     * @param html    HTML body content (may be null)
     * @return true if the email was accepted for delivery
     * @throws MailException if the email cannot be sent
     */
    boolean sendEmail(String from, String to, String subject, String text, String html);
}