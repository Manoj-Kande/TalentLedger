package com.talentledger.application.port.outbound;

/**
 * Outbound port — Email sending (provider-agnostic).
 * Implemented by ResendEmailAdapter or ConsoleEmailAdapter (dev).
 */
public interface EmailSenderPort {

    /** Send an email. */
    void send(EmailMessage message);

    /** Send a templated email. */
    void sendTemplated(String to, String templateId, java.util.Map<String, Object> variables);

    // ── Inner type ───────────────────────────────────────

    record EmailMessage(
        String from,
        String to,
        String subject,
        String htmlBody,
        String textBody
    ) {}
}
