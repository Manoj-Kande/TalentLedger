package com.talentledger.infrastructure.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Prints one clean, unmissable line to stdout when the app is actually ready to serve traffic —
 * independent of whatever logback console filtering is active for the current profile.
 *
 * This does NOT use a Logger on purpose: the whole point is that this line shows up even when
 * the local/dev profile routes normal logging to files and keeps the console quiet.
 */
@Component
public class StartupBanner implements ApplicationListener<ApplicationReadyEvent> {

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String port = env.getProperty("local.server.port", env.getProperty("server.port", "8080"));
        String[] profiles = env.getActiveProfiles();
        String profile = profiles.length > 0 ? String.join(",", profiles) : "default";

        System.out.println();
        System.out.println("  ✅ TalentLedger backend started successfully");
        System.out.println("     → http://localhost:" + port);
        System.out.println("     → profile: " + profile);
        System.out.println("     → detailed logs: ./logs/application.log, ./logs/auth.log, ./logs/error.log");
        System.out.println();
    }
}
