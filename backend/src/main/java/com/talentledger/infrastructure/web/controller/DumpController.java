package com.talentledger.infrastructure.web.controller;

import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.application.dto.response.DumpResponse;
import com.talentledger.application.port.inbound.ContactUseCase;
import com.talentledger.application.port.inbound.DumpUseCase;
import com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository;
import com.talentledger.infrastructure.persistence.entity.DataDumpEntity;
import com.talentledger.shared.exception.DomainException;
import com.talentledger.shared.exception.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Dump Controller — handles file upload, dump CRUD, export.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dumps")
@RequiredArgsConstructor
public class DumpController {

    private final DumpUseCase dumpUseCase;
    private final JpaDataDumpRepository jpaDataDumpRepository;
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    /**
     * Maps a lowercase file extension to the exact {@code FileType} enum
     * name / string that {@code DumpService.parseAndFinalizeSync}'s switch
     * expects ({@code CSV, XLSX, XLS, PDF, JSON, TXT}).
     */
    private static final java.util.Map<String, String> EXTENSION_TO_FILE_TYPE = java.util.Map.of(
            "csv", "CSV",
            "xlsx", "XLSX",
            "xls", "XLS",
            "pdf", "PDF",
            "json", "JSON",
            "txt", "TXT"
    );

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "tags", required = false) String tags,
            HttpServletRequest httpRequest) {

        UUID userId = getCurrentUserId(httpRequest);

        try {
            var command = new com.talentledger.application.port.inbound.DumpUseCase.UploadDumpCommand(
                    file.getOriginalFilename(),
                    resolveFileType(file),
                    file.getSize(),
                    null,
                    description,
                    tags != null ? java.util.List.of(tags.split(",")) : java.util.List.of(),
                    file.getInputStream()
            );

            var result = dumpUseCase.uploadDump(userId, command);
            return result.isSuccess()
                    ? ResponseEntity.status(HttpStatus.CREATED).body(Map.of("success", true, "data", result.getValue()))
                    : ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
        } catch (java.io.IOException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Failed to read file: " + e.getMessage()));
        }
    }

    /**
     * Determine the dump file type from the uploaded filename's extension
     * first (reliable — matches what {@link com.talentledger.domain.dump.FileType}
     * and {@code DumpService.parseAndFinalizeSync}'s switch expect), falling
     * back to the browser-supplied {@code Content-Type} header only if the
     * filename has no recognised extension. Browsers/OSes are inconsistent
     * about MIME types (e.g. .csv is sometimes sent as
     * application/vnd.ms-excel, text/csv, or application/octet-stream), so
     * Content-Type alone previously misclassified every non-JSON upload —
     * including PDFs and Excel files — as CSV.
     */
    private String resolveFileType(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename != null) {
            int dotIndex = filename.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
                String extension = filename.substring(dotIndex + 1).toLowerCase();
                String mapped = EXTENSION_TO_FILE_TYPE.get(extension);
                if (mapped != null) {
                    return mapped;
                }
            }
        }

        String contentType = file.getContentType();
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("json")) return "JSON";
            if (ct.contains("pdf")) return "PDF";
            if (ct.contains("spreadsheetml") || ct.contains("openxmlformats-officedocument.spreadsheetml")) return "XLSX";
            if (ct.contains("ms-excel")) return "XLS";
            if (ct.contains("text/plain")) return "TXT";
        }

        return "CSV"; // final fallback — matches DumpService's default case
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(value = "sort", defaultValue = "created_desc") String sort,
            @RequestParam(value = "q", required = false) String search,
            @RequestParam(value = "archived", defaultValue = "false") Boolean archived,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", defaultValue = "50") int size,
            HttpServletRequest httpRequest) {

        UUID userId = getCurrentUserId(httpRequest);
        var query = new com.talentledger.application.port.inbound.DumpUseCase.DumpListQuery(search, sort, archived, cursor, size);
        var result = dumpUseCase.listDumps(userId, query);

        if (result.isFailure()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
        }

        var page = result.getValue();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", page.dumps(),
                "meta", Map.of(
                        "nextCursor", page.nextCursor() != null ? page.nextCursor() : "",
                        "hasMore", page.hasMore()
                )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = dumpUseCase.getDump(id, userId);

        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", result.getValue()))
                : ResponseEntity.status(404).body(Map.of("success", false, "error", Map.of("code", "DUMP_NOT_FOUND", "message", result.getError())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID id,
            @RequestBody Map<String, Object> updates, HttpServletRequest httpRequest) {

        UUID userId = getCurrentUserId(httpRequest);
        var command = new com.talentledger.application.port.inbound.DumpUseCase.UpdateDumpCommand(
                (String) updates.get("name"),
                updates.containsKey("pinned") ? (Boolean) updates.get("pinned") : null,
                updates.containsKey("archived") ? (Boolean) updates.get("archived") : null
        );

        var result = dumpUseCase.updateDump(id, userId, command);
        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", result.getValue()))
                : ResponseEntity.status(404).body(Map.of("success", false, "error", result.getError()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = dumpUseCase.deleteDump(id, userId);

        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", Map.of("message", "Dump deleted")))
                : ResponseEntity.status(404).body(Map.of("success", false, "error", result.getError()));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retry(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = dumpUseCase.retryDump(id, userId);

        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", Map.of("message", "Retry queued")))
                : ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    @PostMapping("/{id}/confirm-save")
    public ResponseEntity<Map<String, Object>> confirmSave(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = dumpUseCase.confirmSaveDump(id, userId);

        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", Map.of("message", "Saved to your workspace")))
                : ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    @GetMapping("/{id}/contacts")
    public ResponseEntity<Map<String, Object>> getContacts(@PathVariable UUID id,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam(value = "size", defaultValue = "50") int size,
            HttpServletRequest httpRequest) {

        UUID userId = getCurrentUserId(httpRequest);
        var query = new com.talentledger.application.port.inbound.DumpUseCase.CursorQuery(cursor, size);
        var result = dumpUseCase.getDumpContacts(id, userId, query);

        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", result.getValue()))
                : ResponseEntity.status(404).body(Map.of("success", false, "error", result.getError()));
    }

    @GetMapping("/{id}/errors")
    public ResponseEntity<Map<String, Object>> getDumpErrors(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);

        // Ownership check via the same path every other dump endpoint uses.
        var ownershipCheck = dumpUseCase.getDump(id, userId);
        if (!ownershipCheck.isSuccess()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", ownershipCheck.getError()));
        }

        var dump = jpaDataDumpRepository.findById(id).orElseThrow();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", Map.of(
                        "errorCount", dump.getErrorCount(),
                        "errors", dump.getParseErrors(),
                        "note", dump.getErrorCount() > dump.getParseErrors().size()
                                ? "Showing the first " + dump.getParseErrors().size() + " of " + dump.getErrorCount() + " total errors"
                                : "")
        ));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<Map<String, Object>> export(@PathVariable UUID id,
            @RequestParam(value = "format", defaultValue = "csv") String format,
            HttpServletRequest httpRequest) {

        UUID userId = getCurrentUserId(httpRequest);
        var result = dumpUseCase.exportDump(id, userId, format);

        return result.isSuccess()
                ? ResponseEntity.ok(Map.of("success", true, "data", result.getValue()))
                : ResponseEntity.status(404).body(Map.of("success", false, "error", result.getError()));
    }

    @GetMapping(value = "/{id}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable UUID id, HttpServletRequest httpRequest) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 min timeout

        sseExecutor.execute(() -> {
            try {
                for (int i = 0; i < 300; i++) { // max 5 min
                    var dumpOpt = jpaDataDumpRepository.findById(id);
                    if (dumpOpt.isEmpty()) {
                        emitter.send(SseEmitter.event().name("error").data("{\"status\":\"NOT_FOUND\"}"));
                        emitter.complete();
                        return;
                    }
                    DataDumpEntity dump = dumpOpt.get();
                    String status = dump.getStatus().name();
                    Map<String, Object> progress = Map.of(
                            "dumpId", id.toString(), "status", status,
                            "totalRows", dump.getTotalRows(), "parsedContacts", dump.getParsedContactsCount(),
                            "liveContacts", dump.getLiveContactsCount(),
                            "duplicates", dump.getDuplicateWithinDumpCount(),
                            "errors", dump.getErrorCount());
                    emitter.send(SseEmitter.event().name("progress").data(progress));
                    if (status.equals("COMPLETED") || status.equals("COMPLETED_WITH_ERRORS") || status.equals("FAILED")) {
                        emitter.complete();
                        return;
                    }
                    Thread.sleep(1000);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.complete();
            }
        });

        emitter.onTimeout(emitter::complete);
        emitter.onError(e -> emitter.complete());
        return emitter;
    }

    private UUID getCurrentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute("userId");
        if (userId == null) {
            throw new com.talentledger.shared.exception.UnauthorizedException(
                    "No authenticated user on request (SessionAuthFilter should have rejected this earlier)");
        }
        return UUID.fromString(userId.toString());
    }
}