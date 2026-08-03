package com.talentledger.application.service;

import com.talentledger.application.dto.response.ContactResponse;
import com.talentledger.application.port.inbound.DumpUseCase;
import com.talentledger.application.port.inbound.DumpUseCase.CursorQuery;
import com.talentledger.application.port.inbound.DumpUseCase.DumpContactsResult;
import com.talentledger.application.port.inbound.DumpUseCase.DumpListQuery;
import com.talentledger.application.port.inbound.DumpUseCase.DumpListResult;
import com.talentledger.application.port.inbound.DumpUseCase.ExportStream;
import com.talentledger.application.port.inbound.DumpUseCase.ParseErrorsResult;
import com.talentledger.application.port.inbound.DumpUseCase.UpdateDumpCommand;
import com.talentledger.application.port.inbound.DumpUseCase.UploadDumpCommand;
import com.talentledger.domain.dump.DataDump;
import com.talentledger.domain.dump.DumpRepository;
import com.talentledger.domain.shared.Result;
import com.talentledger.domain.user.UserQuota;
import com.talentledger.domain.user.UserQuotaRepository;
import com.talentledger.domain.user.UserRepository;
import com.talentledger.infrastructure.persistence.adapter.DumpRepositoryAdapter;
import com.talentledger.infrastructure.persistence.entity.ContactEntity;
import com.talentledger.infrastructure.persistence.entity.DataDumpEntity;
import com.talentledger.infrastructure.persistence.entity.DumpContactEntity;
import com.talentledger.infrastructure.persistence.repository.ContactJpaRepository;
import com.talentledger.infrastructure.persistence.repository.JpaDumpContactRepository;
import com.talentledger.infrastructure.persistence.repository.JpaDataDumpRepository;
import com.talentledger.infrastructure.persistence.repository.CompanyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DumpService implements DumpUseCase {

    private final DumpRepository dumpRepository;
    private final DumpRepositoryAdapter dumpRepositoryAdapter;
    private final JpaDataDumpRepository jpaDataDumpRepository;
    private final JpaDumpContactRepository dumpContactJpaRepository;
    private final ContactJpaRepository contactJpaRepository;
    private final CompanyJpaRepository companyJpaRepository;
    private final UserQuotaRepository userQuotaRepository;
    private final UserRepository userRepository;
    private final DumpParsingWorker dumpParsingWorker;

    @Value("${talentledger.storage.local.path:./uploads}")
    private String storagePath;

    @Value("${talentledger.demo.ttl-days:7}")
    private int previewTtlDays;

    @Override
    @Transactional
    public Result<DataDump, String> uploadDump(UUID userId, UploadDumpCommand command) {
        // Fetch (or lazily create, for users who predate quota tracking) this
        // user's quota, then enforce the tier limits documented in section 14
        // of the architecture doc. None of this was previously checked at all.
        UserQuota quota = userQuotaRepository.findByUserId(userId)
                .orElseGet(() -> {
                    var user = userRepository.findById(userId).orElseThrow(
                            () -> new IllegalStateException("Uploading user does not exist: " + userId));
                    return userQuotaRepository.save(UserQuota.Builder.forUser(userId, user.getPlan()).build());
                });

        // Item #6/#1: every upload is a PREVIEW until the user explicitly
        // confirms via POST /dumps/{id}/confirm-save (DumpController) — so the
        // plan's dump-count/upload-count limits are enforced there, not here.
        // The one limit that DOES apply immediately, even in preview, is the
        // storage byte cap (100MB for guests) — that's the free-exploration
        // ceiling from the architecture doc, independent of "have you saved yet".
        if (!quota.canStoreBytes(command.fileSizeBytes())) {
            return Result.failure("This file would exceed your plan's storage limit.");
        }

        Path filePath = null;
        try {
            // Store file locally
            Path uploadDir = Paths.get(storagePath);
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            String fileExtension = command.fileType().toLowerCase();
            String storedFilename = UUID.randomUUID() + "." + fileExtension;
            filePath = uploadDir.resolve(storedFilename);

            InputStream fileStream = command.fileStream();
            if (fileStream != null) {
                Files.copy(fileStream, filePath);
                fileStream.close();
            }

            // Create dump entity in PARSING status
            DataDumpEntity entity = DataDumpEntity.builder()
                    .userId(userId)
                    .name(command.originalFilename() != null ? command.originalFilename() : "Untitled Dump")
                    .description(command.description())
                    .tags(command.tags() != null ? command.tags() : List.of())
                    .originalFilename(command.originalFilename())
                    .fileType(com.talentledger.domain.dump.FileType.valueOf(command.fileType()))
                    .fileSizeBytes(command.fileSizeBytes())
                    .status(com.talentledger.domain.dump.DumpStatus.PARSING)
                    .totalRows(0)
                    .parsedContactsCount(0)
                    .liveContactsCount(0)
                    .duplicateWithinDumpCount(0)
                    .crossDumpDuplicateCount(0)
                    .errorCount(0)
                    // Not persisted until confirm-save (item #6). expiresAt drives
                    // ScheduledJobs' existing (previously unused) cleanup of dumps
                    // that never get confirmed — findExpiredFreeDumps() already
                    // queried on exactly this isPersisted=false+expiresAt pair.
                    .isPersisted(false)
                    .expiresAt(Instant.now().plus(previewTtlDays, java.time.temporal.ChronoUnit.DAYS))
                    .originalFileKey(filePath.toString())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            DataDumpEntity saved = jpaDataDumpRepository.save(entity);
            UUID dumpId = saved.getId();

            // IMPORTANT: this method is @Transactional and has not committed yet.
            // dumpParsingWorker.parseAndFinalize(...) is @Async and starts running
            // on a separate thread the instant it's called — if we call it directly
            // here, that thread can race ahead of this transaction's commit and try
            // to findById(dumpId) a row that isn't visible outside this transaction
            // yet (intermittent "Dump disappeared before async parsing could run").
            // Registering a synchronization defers the dispatch until Spring has
            // actually committed the INSERT, so the worker is guaranteed to see it.
            final Path finalFilePath = filePath;
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            dumpParsingWorker.parseAndFinalize(
                                    dumpId, userId, finalFilePath, command.fileType(), command.fileSizeBytes());
                        }
                    });

            log.info("Dump {} queued for async parsing (user {}, type {})", dumpId, userId, command.fileType());

            return dumpRepository.findByIdAndUserId(dumpId, userId)
                    .<Result<DataDump, String>>map(Result::success)
                    .orElseGet(() -> Result.failure("Dump not found after save"));

        } catch (IOException e) {
            log.error("Failed to store upload file", e);
            return Result.failure("Failed to process file: " + e.getMessage());
        }
    }

    /**
     * Parse the given file (dispatching on format) and roll the results into
     * the dump row + user quota. Called from {@link DumpParsingWorker} on the
     * async parser executor — package-visible rather than private so that
     * worker can invoke it after the request thread has already returned.
     */
    void parseAndFinalizeSync(UUID dumpId, UUID userId, Path filePath, String fileType, long fileSizeBytes) {
        DataDumpEntity saved = jpaDataDumpRepository.findById(dumpId).orElse(null);
        if (saved == null) {
            log.error("Dump {} disappeared before async parsing could run", dumpId);
            return;
        }

        try {
            ParseResult parseResult = switch (fileType.toUpperCase()) {
                case "XLSX" -> parseExcelFile(filePath, userId, dumpId, false);
                case "XLS" -> parseExcelFile(filePath, userId, dumpId, true);
                case "JSON" -> parseJsonFile(filePath, userId, dumpId);
                case "PDF" -> parsePdfFile(filePath, userId, dumpId);
                default -> parseCsvFile(filePath, userId, dumpId); // CSV, TXT
            };

            saved.setStatus(com.talentledger.domain.dump.DumpStatus.COMPLETED);
            saved.setTotalRows(parseResult.totalRows);
            saved.setParsedContactsCount(parseResult.parsedContacts);
            saved.setLiveContactsCount(parseResult.liveContacts);
            saved.setDuplicateWithinDumpCount(parseResult.duplicateWithinDump);
            saved.setCrossDumpDuplicateCount(parseResult.crossDumpDuplicates);
            saved.setErrorCount(parseResult.errorCount);
            saved.setParseErrors(parseResult.errors);
            saved.setCompletedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            jpaDataDumpRepository.save(saved);

            userQuotaRepository.findByUserId(userId).ifPresent(q -> {
                UserQuota updated = q.addContacts(parseResult.liveContacts).addStorageBytes(fileSizeBytes);
                userQuotaRepository.save(updated);
            });

            log.info("Dump {} parsed for user {}: {} rows, {} contacts, {} dups, {} errors",
                    dumpId, userId, parseResult.totalRows, parseResult.liveContacts,
                    parseResult.duplicateWithinDump, parseResult.errorCount);

        } catch (Exception e) {
            log.error("Async parse failed for dump {}: {}", dumpId, e.getMessage(), e);
            saved.setStatus(com.talentledger.domain.dump.DumpStatus.FAILED);
            saved.setUpdatedAt(Instant.now());
            jpaDataDumpRepository.save(saved);
        }
    }

    /**
     * Parse a CSV file and create contacts + dump_contact junction records.
     */
    private ParseResult parseCsvFile(Path filePath, UUID userId, UUID dumpId) throws IOException {
        ParseResult result = new ParseResult();
        DataDumpEntity dumpRef = jpaDataDumpRepository.findById(dumpId).orElseThrow();
        RowProcessingState state = new RowProcessingState();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(filePath.toFile()), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return result;
            }

            String[] headers = parseCsvLine(headerLine);
            ColumnIndices indices = resolveColumnIndices(buildHeaderMap(headers));

            String line;
            int rowNum = 0;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                result.totalRows++;
                if (line.isBlank()) continue;

                String[] values = parseCsvLine(line);
                if (values.length < headers.length) {
                    values = Arrays.copyOf(values, headers.length);
                }
                processDataRow(values, indices, rowNum, userId, dumpId, dumpRef, result, state, "csv");
            }
        }

        return result;
    }

    /**
     * Stream an .xlsx file row-by-row via fastexcel (per ADR-041 — never load
     * the whole workbook into DOM memory; a 50MB .xlsx would OOM on a 384MB JVM
     * with XSSFWorkbook). .xls (legacy binary) is inherently size-bounded by
     * its own format (max 65536 rows), so POI's DOM-based HSSFWorkbook is safe.
     */
    private ParseResult parseExcelFile(Path filePath, UUID userId, UUID dumpId, boolean legacyXls) throws IOException {
        ParseResult result = new ParseResult();
        DataDumpEntity dumpRef = jpaDataDumpRepository.findById(dumpId).orElseThrow();
        RowProcessingState state = new RowProcessingState();

        if (legacyXls) {
            try (var wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(Files.newInputStream(filePath))) {
                var sheet = wb.getSheetAt(0);
                var rowIterator = sheet.iterator();
                if (!rowIterator.hasNext()) return result;

                String[] headers = excelRowToStrings(rowIterator.next());
                ColumnIndices indices = resolveColumnIndices(buildHeaderMap(headers));

                int rowNum = 0;
                while (rowIterator.hasNext()) {
                    rowNum++;
                    result.totalRows++;
                    String[] values = excelRowToStrings(rowIterator.next());
                    if (values.length < headers.length) values = Arrays.copyOf(values, headers.length);
                    processDataRow(values, indices, rowNum, userId, dumpId, dumpRef, result, state, "xls");
                }
            }
            return result;
        }

        try (var wb = new org.dhatim.fastexcel.reader.ReadableWorkbook(Files.newInputStream(filePath))) {
            var sheet = wb.getFirstSheet();
            try (var rows = sheet.openStream()) {
                java.util.Iterator<org.dhatim.fastexcel.reader.Row> it = rows.iterator();
                if (!it.hasNext()) return result;

                String[] headers = fastexcelRowToStrings(it.next());
                ColumnIndices indices = resolveColumnIndices(buildHeaderMap(headers));

                int rowNum = 0;
                while (it.hasNext()) {
                    rowNum++;
                    result.totalRows++;
                    String[] values = fastexcelRowToStrings(it.next());
                    if (values.length < headers.length) values = Arrays.copyOf(values, headers.length);
                    processDataRow(values, indices, rowNum, userId, dumpId, dumpRef, result, state, "xlsx");
                }
            }
        }
        return result;
    }

    /**
     * Parse a JSON array of flat objects. Streamed via Jackson's MappingIterator
     * (one object materialized at a time) rather than reading the whole array
     * into memory. Assumes a uniform schema across objects — the first object's
     * key set defines the columns, matching how a typical uniform contact-dump
     * JSON export looks.
     */
    private ParseResult parseJsonFile(Path filePath, UUID userId, UUID dumpId) throws IOException {
        ParseResult result = new ParseResult();
        DataDumpEntity dumpRef = jpaDataDumpRepository.findById(dumpId).orElseThrow();
        RowProcessingState state = new RowProcessingState();

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try (var parser = mapper.getFactory().createParser(filePath.toFile())) {
            com.fasterxml.jackson.databind.MappingIterator<Map<String, Object>> it =
                    mapper.readValues(parser, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            String[] headers = null;
            ColumnIndices indices = null;
            int rowNum = 0;

            while (it.hasNext()) {
                Map<String, Object> obj = it.next();
                rowNum++;
                result.totalRows++;

                if (headers == null) {
                    headers = obj.keySet().toArray(new String[0]);
                    indices = resolveColumnIndices(buildHeaderMap(headers));
                }
                String[] values = new String[headers.length];
                for (int i = 0; i < headers.length; i++) {
                    Object v = obj.get(headers[i]);
                    values[i] = v != null ? String.valueOf(v) : null;
                }
                processDataRow(values, indices, rowNum, userId, dumpId, dumpRef, result, state, "json");
            }
        }
        return result;
    }

    /**
     * Best-effort PDF parsing: extract text, then treat it like a delimited
     * table (comma, tab, or pipe — whichever the header line actually uses).
     * PDF table extraction is inherently unreliable for arbitrary layouts;
     * this handles the common case of a PDF that's fundamentally a rendered
     * spreadsheet/table, not arbitrary free-form prose.
     */
    private ParseResult parsePdfFile(Path filePath, UUID userId, UUID dumpId) throws IOException {
        ParseResult result = new ParseResult();
        DataDumpEntity dumpRef = jpaDataDumpRepository.findById(dumpId).orElseThrow();
        RowProcessingState state = new RowProcessingState();

        String text;
        try (var document = org.apache.pdfbox.Loader.loadPDF(filePath.toFile())) {
            text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
        }

        List<String> lines = text.lines().filter(l -> !l.isBlank()).toList();
        if (lines.isEmpty()) return result;

        String delimiter = detectDelimiter(lines.get(0));
        String[] headers = lines.get(0).split(Pattern.quote(delimiter));
        for (int i = 0; i < headers.length; i++) headers[i] = headers[i].trim();
        ColumnIndices indices = resolveColumnIndices(buildHeaderMap(headers));

        int rowNum = 0;
        for (int i = 1; i < lines.size(); i++) {
            rowNum++;
            result.totalRows++;
            String[] values = lines.get(i).split(Pattern.quote(delimiter));
            if (values.length < headers.length) values = Arrays.copyOf(values, headers.length);
            for (int j = 0; j < values.length; j++) if (values[j] != null) values[j] = values[j].trim();
            processDataRow(values, indices, rowNum, userId, dumpId, dumpRef, result, state, "pdf");
        }
        return result;
    }

    private String detectDelimiter(String headerLine) {
        if (headerLine.contains("\t")) return "\t";
        if (headerLine.contains("|")) return "|";
        return ",";
    }

    private String[] excelRowToStrings(org.apache.poi.ss.usermodel.Row row) {
        var formatter = new org.apache.poi.ss.usermodel.DataFormatter();
        int last = row.getLastCellNum();
        String[] out = new String[Math.max(last, 0)];
        for (int i = 0; i < out.length; i++) {
            var cell = row.getCell(i);
            out[i] = cell != null ? formatter.formatCellValue(cell).trim() : "";
        }
        return out;
    }

    private String[] fastexcelRowToStrings(org.dhatim.fastexcel.reader.Row row) {
        int count = row.getCellCount();
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            var cell = row.getCellText(i);
            out[i] = cell != null ? cell.trim() : "";
        }
        return out;
    }

    private Map<String, Integer> buildHeaderMap(String[] headers) {
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            headerMap.put(headers[i].trim().toLowerCase().replaceAll("[^a-z0-9]", ""), i);
        }
        return headerMap;
    }

    private record ColumnIndices(int nameIdx, int emailIdx, int phoneIdx, int titleIdx, int companyIdx,
                                  int linkedinIdx, int locationIdx, int departmentIdx, int seniorityIdx) {}

    /** Mutable dedup state shared across all rows of a single file — one instance per parse call. */
    private static class RowProcessingState {
        final Set<String> seenEmailsInDump = new HashSet<>();
        final Set<String> existingUserEmails = new HashSet<>();
        // Tracks which contacts already have a dump_contacts row for this dump
        // (dump_id, contact_id) is uniquely indexed — see idx_dump_contacts_active
        // in V1 — so a duplicate-within-dump row resolving to the same contact
        // must NOT attempt a second insert, or it throws a constraint violation
        // that gets counted as a spurious row error.
        final Set<UUID> linkedContactIdsInDump = new HashSet<>();
    }

    private ColumnIndices resolveColumnIndices(Map<String, Integer> headerMap) {
        return new ColumnIndices(
                findColumnIndex(headerMap, "name", "fullname", "full_name", "firstname", "first_name"),
                findColumnIndex(headerMap, "email", "emailaddress", "email_address", "email1"),
                findColumnIndex(headerMap, "phone", "phone number", "phonenumber", "mobile", "tel"),
                findColumnIndex(headerMap, "title", "jobtitle", "job_title", "position", "role"),
                findColumnIndex(headerMap, "company", "companyname", "company_name", "organization", "org"),
                findColumnIndex(headerMap, "linkedin", "linkedinurl", "linkedin_url"),
                findColumnIndex(headerMap, "location", "city", "address"),
                findColumnIndex(headerMap, "department", "dept"),
                findColumnIndex(headerMap, "seniority", "senioritylevel", "seniority_level")
        );
    }

    /**
     * Process one data row, shared by every file-format parser (CSV/Excel/JSON/PDF).
     * Per-row failures are caught and counted rather than aborting the whole file
     * (ADR-015: partial import — fail rows, not entire dump).
     */
    private void processDataRow(String[] values, ColumnIndices idx, int rowNum, UUID userId, UUID dumpId,
                                 DataDumpEntity dumpRef, ParseResult result, RowProcessingState state, String source) {
        try {
            String name = getValue(values, idx.nameIdx());
            String email = getValue(values, idx.emailIdx());
            String phone = getValue(values, idx.phoneIdx());
            String title = getValue(values, idx.titleIdx());
            String company = getValue(values, idx.companyIdx());
            String linkedin = getValue(values, idx.linkedinIdx());
            String location = getValue(values, idx.locationIdx());
            String department = getValue(values, idx.departmentIdx());
            String seniority = getValue(values, idx.seniorityIdx());

            if (email == null || email.isBlank()) {
                result.recordError(rowNum, "Missing email address");
                return;
            }

            String normalizedEmail = normalizeEmailForDedup(email);
            boolean isDuplicateWithinDump = !state.seenEmailsInDump.add(normalizedEmail);

            boolean isCrossDump = false;
            if (!isDuplicateWithinDump) {
                if (state.existingUserEmails.isEmpty()) {
                    contactJpaRepository.findByUserIdNewest(userId, PageRequest.of(0, 50000))
                            .forEach(c -> {
                                if (c.getNormalizedEmail() != null) {
                                    state.existingUserEmails.add(c.getNormalizedEmail().toLowerCase());
                                }
                            });
                }
                isCrossDump = state.existingUserEmails.contains(normalizedEmail);
            }

            if (isDuplicateWithinDump) result.duplicateWithinDump++;
            if (isCrossDump) result.crossDumpDuplicates++;

            ContactEntity savedContact;
            if (!isDuplicateWithinDump && !isCrossDump) {
                UUID companyId = null;
                if (company != null && !company.isBlank()) {
                    companyId = resolveOrCreateCompany(company);
                }

                ContactEntity contact = ContactEntity.builder()
                        .userId(userId)
                        .name(name != null ? name.trim() : "Unknown")
                        .email(email.trim())
                        .normalizedEmail(normalizedEmail)
                        .phone(phone)
                        .linkedinUrl(linkedin)
                        .title(title)
                        .department(department)
                        .seniorityLevel(seniority != null ? parseSeniority(seniority) : null)
                        .location(location)
                        .companyId(companyId)
                        .primaryDumpId(dumpId)
                        .verificationScore(0)
                        .source(source)
                        .status(com.talentledger.domain.contact.ContactStatus.ACTIVE)
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build();
                savedContact = contactJpaRepository.save(contact);
                result.liveContacts++;
                state.existingUserEmails.add(normalizedEmail);
            } else {
                savedContact = contactJpaRepository
                        .findByNormalizedEmailAndUserIdAndDeletedAtIsNull(normalizedEmail, userId).orElse(null);
            }

            result.parsedContacts++;

            // Only create the dump_contacts link the first time this contact is
            // seen for this dump — a second row for the same (dump_id, contact_id)
            // pair would violate idx_dump_contacts_active and get miscounted as
            // a row error even though the row itself was valid data.
            if (savedContact != null && state.linkedContactIdsInDump.add(savedContact.getId())) {
                DumpContactEntity dc = DumpContactEntity.builder()
                        .dump(dumpRef)
                        .contact(savedContact)
                        .rowNumber(rowNum)
                        .rawData(Map.of(
                                "name", name != null ? name : "",
                                "email", email,
                                "phone", phone != null ? phone : "",
                                "title", title != null ? title : "",
                                "company", company != null ? company : "",
                                "linkedin", linkedin != null ? linkedin : "",
                                "location", location != null ? location : ""
                        ))
                        .isDuplicateWithinDump(isDuplicateWithinDump)
                        .isCrossDumpDuplicate(isCrossDump)
                        .createdAt(Instant.now())
                        .build();
                dumpContactJpaRepository.save(dc);
            }
        } catch (Exception e) {
            log.warn("Error parsing row {} in dump {}: {}", rowNum, dumpId, e.getMessage());
            result.recordError(rowNum, e.getMessage());
        }
    }

    private UUID resolveOrCreateCompany(String companyName) {
        try {
            // Try to find existing company by normalized name
            var existing = companyJpaRepository.findByNormalizedName(
                    com.talentledger.domain.company.CompanyNormalizer.normalize(companyName));
            if (existing.isPresent()) {
                return existing.get().getId();
            }
            // Create new company
            var company = com.talentledger.domain.company.Company.create(companyName, null, null, null, null, null, null);
            var entity = com.talentledger.infrastructure.persistence.entity.CompanyEntity.builder()
                    .id(company.getId())
                    .normalizedName(company.getNormalizedName())
                    .displayName(company.getDisplayName())
                    .category(company.getCategory())
                    .industry(company.getIndustry())
                    .sizeRange(company.getSizeRange())
                    .headquarters(company.getHeadquarters())
                    .domain(company.getDomain())
                    .logoUrl(company.getLogoUrl())
                    .createdAt(company.getCreatedAt())
                    .updatedAt(company.getUpdatedAt())
                    .build();
            var saved = companyJpaRepository.save(entity);
            return saved.getId();
        } catch (Exception e) {
            log.warn("Failed to resolve/create company '{}': {}", companyName, e.getMessage());
            return null;
        }
    }

    /**
     * Normalize an email for dedup comparison. Per ADR-018/ADR-046, Gmail
     * treats "j.doe@gmail.com", "jdoe@gmail.com", and "jdoe+work@gmail.com"
     * as the same inbox, but this folding must NEVER be applied to other
     * providers (dots and plus-tags are meaningful elsewhere).
     */
    private String normalizeEmailForDedup(String email) {
        String trimmed = email.toLowerCase().trim();
        int at = trimmed.indexOf('@');
        if (at < 0) {
            return trimmed;
        }
        String local = trimmed.substring(0, at);
        String domain = trimmed.substring(at + 1);
        if (domain.equals("gmail.com") || domain.equals("googlemail.com")) {
            local = local.replace(".", "").split("\\+")[0];
        }
        return local + "@" + domain;
    }

    private com.talentledger.domain.contact.SeniorityLevel parseSeniority(String value) {
        if (value == null) return null;
        String v = value.toUpperCase().trim();
        return switch (v) {
            case "C_LEVEL", "C-SUITE", "EXECUTIVE", "CXO", "CEO", "CTO", "CFO", "COO", "CIO" -> com.talentledger.domain.contact.SeniorityLevel.CXO;
            case "VP", "VICE_PRESIDENT" -> com.talentledger.domain.contact.SeniorityLevel.VP;
            case "DIRECTOR", "SENIOR_DIRECTOR" -> com.talentledger.domain.contact.SeniorityLevel.DIRECTOR;
            case "MANAGER", "SR_MANAGER", "SENIOR_MANAGER", "LEAD", "TEAM_LEAD", "HEAD" -> com.talentledger.domain.contact.SeniorityLevel.MANAGER;
            case "FOUNDER", "CO_FOUNDER", "CO-FOUNDER", "OWNER" -> com.talentledger.domain.contact.SeniorityLevel.FOUNDER;
            default -> com.talentledger.domain.contact.SeniorityLevel.IC;
        };
    }

    private int findColumnIndex(Map<String, Integer> headerMap, String... names) {
        for (String name : names) {
            String key = name.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (headerMap.containsKey(key)) {
                return headerMap.get(key);
            }
        }
        return -1;
    }

    private String getValue(String[] values, int index) {
        if (index < 0 || index >= values.length) return null;
        String val = values[index];
        if (val == null) return null;
        val = val.trim();
        return val.isEmpty() ? null : val;
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        sb.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    result.add(sb.toString());
                    sb = new StringBuilder();
                } else {
                    sb.append(c);
                }
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[0]);
    }

    private static class ParseResult {
        int totalRows = 0;
        int parsedContacts = 0;
        int liveContacts = 0;
        int duplicateWithinDump = 0;
        int crossDumpDuplicates = 0;
        int errorCount = 0;
        final List<Map<String, Object>> errors = new ArrayList<>();

        /** ADR-049: store only the last 100 row-level failures; errorCount still tracks the true total. */
        void recordError(int rowNum, String message) {
            if (errors.size() < 100) {
                errors.add(Map.of("row", rowNum, "message", message != null ? message : "Unknown error"));
            }
            errorCount++;
        }
    }

    @Override
    public Result<DataDump, String> getDump(UUID dumpId, UUID userId) {
        return dumpRepository.findByIdAndUserId(dumpId, userId)
                .<Result<DataDump, String>>map(Result::success)
                .orElseGet(() -> Result.failure("Dump not found"));
    }

    @Override
    public Result<DumpListResult, String> listDumps(UUID userId, DumpListQuery query) {
        var dumps = dumpRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return Result.success(new DumpListResult(dumps, null, false));
    }

    @Override
    @Transactional
    public Result<DataDump, String> updateDump(UUID dumpId, UUID userId, UpdateDumpCommand command) {
        return dumpRepository.findByIdAndUserId(dumpId, userId)
                .map(dump -> {
                    if (command.name() != null) dump.rename(command.name());
                    if (command.pinned() != null && command.pinned()) dump.pin();
                    else if (command.pinned() != null) dump.unpin();
                    if (command.archived() != null && command.archived()) dump.archive();
                    var saved = dumpRepository.save(dump);
                    return Result.<DataDump, String>success(saved);
                })
                .orElseGet(() -> Result.failure("Dump not found"));
    }

    @Override
    @Transactional
    public Result<Void, String> confirmSaveDump(UUID dumpId, UUID userId) {
        DataDumpEntity entity = jpaDataDumpRepository.findById(dumpId).orElse(null);
        if (entity == null || entity.getUserId() == null || !entity.getUserId().equals(userId)) {
            return Result.failure("Dump not found");
        }
        if (Boolean.TRUE.equals(entity.getIsPersisted())) {
            return Result.success(null); // already saved — idempotent
        }
        if (entity.getStatus() != com.talentledger.domain.dump.DumpStatus.COMPLETED) {
            return Result.failure("This upload hasn't finished processing yet — try again in a moment.");
        }

        UserQuota quota = userQuotaRepository.findByUserId(userId).orElse(null);
        if (quota == null) {
            return Result.failure("Could not verify your account quota — please try again.");
        }
        if (!quota.canCreateDump()) {
            return Result.failure("Active dump limit reached for your plan. Upgrade or delete an existing dump to save this one.");
        }
        if (!quota.canUpload()) {
            return Result.failure("Monthly upload limit reached for your plan.");
        }

        entity.setIsPersisted(true);
        entity.setExpiresAt(null);
        entity.setUpdatedAt(Instant.now());
        jpaDataDumpRepository.save(entity);

        userQuotaRepository.save(quota.addDump().incrementUploads());

        log.info("Dump {} confirmed and saved permanently by user {}", dumpId, userId);
        return Result.success(null);
    }

    @Override
    @Transactional
    public Result<Void, String> retryDump(UUID dumpId, UUID userId) {
        DataDumpEntity entity = jpaDataDumpRepository.findById(dumpId).orElse(null);
        if (entity == null || entity.getUserId() == null || !entity.getUserId().equals(userId)) {
            return Result.failure("Dump not found");
        }
        if (entity.getStatus() != com.talentledger.domain.dump.DumpStatus.FAILED) {
            return Result.failure("Only failed uploads can be retried");
        }
        if (entity.getOriginalFileKey() == null) {
            return Result.failure("Original file is no longer available — please re-upload");
        }
        Path filePath = Paths.get(entity.getOriginalFileKey());
        if (!Files.exists(filePath)) {
            return Result.failure("Original file is no longer available — please re-upload");
        }

        entity.setStatus(com.talentledger.domain.dump.DumpStatus.PENDING);
        entity.setErrorCount(0);
        entity.setParseErrors(null);
        entity.setUpdatedAt(Instant.now());
        jpaDataDumpRepository.save(entity);

        String fileType = entity.getFileType().name();
        long fileSizeBytes = entity.getFileSizeBytes() != null ? entity.getFileSizeBytes() : 0L;

        // Same reasoning as uploadDump(): defer dispatch until this transaction
        // (the PENDING status reset above) actually commits, so the worker
        // doesn't race ahead and read the pre-reset row.
        org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dumpParsingWorker.parseAndFinalize(dumpId, userId, filePath, fileType, fileSizeBytes);
                    }
                });

        log.info("Dump {} queued for retry by user {}", dumpId, userId);
        return Result.success(null);
    }

    @Override
    @Transactional
    public Result<Void, String> deleteDump(UUID dumpId, UUID userId) {
        return dumpRepository.findByIdAndUserId(dumpId, userId)
                .<Result<Void, String>>map(dump -> {
                    dumpRepository.delete(dump);
                    log.info("Dump {} deleted by user {}", dumpId, userId);
                    return Result.success(null);
                })
                .orElseGet(() -> Result.failure("Dump not found"));
    }

    @Override
    public Result<DumpContactsResult, String> getDumpContacts(UUID dumpId, UUID userId, CursorQuery cursor) {
        // Ownership check — was missing entirely before, so any authenticated
        // user could read another user's dump contacts just by guessing a
        // dumpId. Every other dump endpoint already goes through
        // dumpRepository.findByIdAndUserId; this one must too.
        if (dumpRepository.findByIdAndUserId(dumpId, userId).isEmpty()) {
            return Result.failure("Dump not found");
        }

        var pageable = PageRequest.of(0, cursor.pageSize() + 1);
        List<DumpContactEntity> dcEntities = dumpContactJpaRepository.findByDumpIdAndDeletedAtIsNull(dumpId, pageable).getContent();

        boolean hasMore = dcEntities.size() > cursor.pageSize();
        List<DumpContactEntity> page = hasMore ? dcEntities.subList(0, cursor.pageSize()) : dcEntities;

        List<ContactResponse> items = page.stream()
                .map(DumpContactEntity::getContact)
                .filter(Objects::nonNull)
                .map(this::entityToContactResponse)
                .toList();
        String nextCursor = hasMore && !page.isEmpty() ? page.get(page.size() - 1).getId().toString() : null;

        return Result.success(new DumpContactsResult(items, nextCursor, hasMore));
    }

    /** Mirrors ContactService#entityToResponse — kept local to avoid a cross-service dependency. */
    private ContactResponse entityToContactResponse(ContactEntity e) {
        return new ContactResponse(
                e.getId(), e.getName(),
                e.getEmail(), e.getNormalizedEmail(),
                e.getPhone(), e.getLinkedinUrl(), e.getSecondaryEmail(),
                e.getTitle(), e.getDepartment(),
                e.getSeniorityLevel() != null ? e.getSeniorityLevel().name() : null,
                e.getLocation(), e.getTimezone(), e.getLanguage(),
                e.getDomain(), e.getVerificationScore(), e.getSource(),
                e.getPrimaryDumpId(), e.getCompanyId(), null,
                e.getNotes(),
                e.getTags() != null ? new java.util.ArrayList<>(e.getTags()) : new java.util.ArrayList<>(),
                e.getCustomFields() != null ? new java.util.HashMap<>(e.getCustomFields()) : new java.util.HashMap<>(),
                e.getAiEnrichment() != null ? new java.util.HashMap<>(e.getAiEnrichment()) : new java.util.HashMap<>(),
                e.getStatus() != null ? e.getStatus().name() : null,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    @Override
    public Result<ParseErrorsResult, String> getDumpErrors(UUID dumpId, UUID userId) {
        return dumpRepository.findByIdAndUserId(dumpId, userId)
                .map(dump -> Result.<ParseErrorsResult, String>success(new ParseErrorsResult(List.of(), 0)))
                .orElseGet(() -> Result.failure("Dump not found"));
    }

    @Override
    public Result<ExportStream, String> exportDump(UUID dumpId, UUID userId, String format) {
        return dumpRepository.findByIdAndUserId(dumpId, userId)
                .map(dump -> {
                    try {
                        List<DumpContactEntity> dcEntities = dumpContactJpaRepository.findByDumpIdAndDeletedAtIsNull(dumpId, PageRequest.of(0, Integer.MAX_VALUE)).getContent();
                        List<ContactEntity> contacts = new ArrayList<>();
                        for (DumpContactEntity dc : dcEntities) {
                            if (dc.getContact() != null && dc.getContact().getId() != null) {
                                contactJpaRepository.findByIdAndUserId(dc.getContact().getId(), userId)
                                        .ifPresent(contacts::add);
                            }
                        }

                        StringBuilder csv = new StringBuilder();
                        csv.append("Name,Email,Phone,Title,Department,Seniority,Company,Location,Domain,Source\n");
                        for (ContactEntity c : contacts) {
                            csv.append(escapeCsv(c.getName())).append(",");
                            csv.append(escapeCsv(c.getEmail())).append(",");
                            csv.append(escapeCsv(c.getPhone() != null ? c.getPhone() : "")).append(",");
                            csv.append(escapeCsv(c.getTitle() != null ? c.getTitle() : "")).append(",");
                            csv.append(escapeCsv(c.getDepartment() != null ? c.getDepartment() : "")).append(",");
                            csv.append(escapeCsv(c.getSeniorityLevel() != null ? c.getSeniorityLevel().name() : "")).append(",");
                            String companyName = "";
                            if (c.getCompanyId() != null) {
                                companyName = companyJpaRepository.findById(c.getCompanyId())
                                        .map(co -> co.getDisplayName() != null ? co.getDisplayName() : "")
                                        .orElse("");
                            }
                            csv.append(escapeCsv(companyName)).append(",");
                            csv.append(escapeCsv(c.getLocation() != null ? c.getLocation() : "")).append(",");
                            csv.append(escapeCsv(c.getDomain() != null ? c.getDomain() : "")).append(",");
                            csv.append(escapeCsv(c.getSource() != null ? c.getSource() : "")).append("\n");
                        }

                        String filename = dump.getOriginalFilename().replaceFirst("\\.[^.]+$", "") + "_export." + format.toLowerCase();
                        String contentType = "csv".equalsIgnoreCase(format) ? "text/csv" : "application/json";

                        return Result.<ExportStream, String>success(new ExportStream(
                                filename, contentType,
                                new ByteArrayInputStream(csv.toString().getBytes(StandardCharsets.UTF_8))));
                    } catch (Exception e) {
                        log.error("Export failed for dump {}", dumpId, e);
                        return Result.<ExportStream, String>failure("Export failed: " + e.getMessage());
                    }
                })
                .orElseGet(() -> Result.failure("Dump not found"));
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
