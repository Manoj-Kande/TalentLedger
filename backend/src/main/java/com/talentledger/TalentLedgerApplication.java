package com.talentledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TalentLedger — Contact Intelligence Platform.
 *
 * <p>Strict Hexagonal / Ports &amp; Adapters architecture.
 * Domain → Application → Infrastructure. Zero framework leakage into domain.
 *
 * <p>Java 21 Virtual Threads enabled for I/O-bound workloads.
 */
@SpringBootApplication
@EnableScheduling
public class TalentLedgerApplication {

    public static void main(String[] args) {
        SpringApplication.run(TalentLedgerApplication.class, args);
    }
}
