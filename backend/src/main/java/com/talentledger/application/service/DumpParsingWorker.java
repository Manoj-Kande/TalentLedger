package com.talentledger.application.service;

import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.UUID;

/**
 * Runs dump parsing on the {@code parserExecutor} virtual-thread pool.
 *
 * <p>Deliberately a separate bean from {@link DumpService}: an {@code @Async}
 * method only actually runs asynchronously when called <em>through the Spring
 * proxy</em> — calling it via {@code this.someAsyncMethod()} from inside the
 * same class silently executes synchronously with no error, a well-known
 * Spring pitfall. {@code @Lazy} on the {@link DumpService} dependency breaks
 * the otherwise-circular constructor injection (DumpService → this worker →
 * DumpService) at startup.
 */
@Component
public class DumpParsingWorker {

    private final DumpService dumpService;

    public DumpParsingWorker(@Lazy DumpService dumpService) {
        this.dumpService = dumpService;
    }

    @Async("parserExecutor")
    public void parseAndFinalize(UUID dumpId, UUID userId, Path filePath, String fileType, long fileSizeBytes) {
        dumpService.parseAndFinalizeSync(dumpId, userId, filePath, fileType, fileSizeBytes);
    }
}
