package com.talentledger.infrastructure.adapter.outbound;

import com.talentledger.application.port.outbound.EmailSenderPort;
import com.talentledger.application.port.outbound.EmailSenderPort.EmailMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Console-based email adapter for development.
 * Logs email details instead of sending.
 *
 * <p>Active whenever {@code talentledger.email.provider} is unset or not
 * {@code RESEND} — i.e. the default. Set {@code talentledger.email.provider=RESEND}
 * (and {@code RESEND_API_KEY}) to switch to {@link ResendEmailAdapter} in production.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "talentledger.email", name = "provider", havingValue = "CONSOLE", matchIfMissing = true)
public class ConsoleEmailAdapter implements EmailSenderPort {

    @Override
    public void send(EmailMessage message) {
        log.info("[EMAIL] From: {} To: {} Subject: {}", message.from(), message.to(), message.subject());
        log.info("[EMAIL] Text body: {}", message.textBody());
    }

    @Override
    public void sendTemplated(String to, String templateId, Map<String, Object> variables) {
        log.info("[EMAIL] Templated email to: {} template: {} vars: {}", to, templateId, variables);
    }
}
