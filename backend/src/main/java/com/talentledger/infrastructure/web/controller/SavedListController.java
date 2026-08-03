package com.talentledger.infrastructure.web.controller;

import com.talentledger.application.port.inbound.SavedListUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/saved-lists")
@RequiredArgsConstructor
public class SavedListController {

    private final SavedListUseCase savedListUseCase;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list(HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = savedListUseCase.listLists(userId);
        return toResponse(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = savedListUseCase.getList(id, userId);
        return toResponse(result);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody SavedListUseCase.CreateListCommand command,
                                                       HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = savedListUseCase.createList(userId, command);
        return toResponse(result);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable UUID id,
            @RequestBody SavedListUseCase.UpdateListCommand command,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = savedListUseCase.updateList(id, userId, command);
        return toResponse(result);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = savedListUseCase.deleteList(id, userId);
        return toResponse(result);
    }

    @PostMapping("/{id}/contacts")
    public ResponseEntity<Map<String, Object>> addContacts(@PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        @SuppressWarnings("unchecked")
        java.util.List<UUID> contactIds = ((java.util.List<String>) body.getOrDefault("contactIds", java.util.List.of()))
                .stream().map(UUID::fromString).toList();
        var result = savedListUseCase.addContactsToList(id, userId, contactIds);
        return toResponse(result);
    }

    @DeleteMapping("/{id}/contacts/{contactId}")
    public ResponseEntity<Map<String, Object>> removeContact(@PathVariable UUID id,
            @PathVariable UUID contactId,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = savedListUseCase.removeContactFromList(id, userId, contactId);
        return toResponse(result);
    }

    @GetMapping("/{id}/contacts")
    public ResponseEntity<Map<String, Object>> getContacts(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "50") int size,
            HttpServletRequest httpRequest) {
        UUID userId = getCurrentUserId(httpRequest);
        var result = savedListUseCase.getListContacts(id, userId, cursor, size);
        if (result.isSuccess()) {
            var page = result.getValue();
            return ResponseEntity.ok(Map.of("success", true, "data", page.contacts(), "meta",
                    Map.of("nextCursor", page.nextCursor() != null ? page.nextCursor() : "", "hasMore", page.hasMore())));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
    }

    private ResponseEntity<Map<String, Object>> toResponse(com.talentledger.domain.shared.Result<?, String> result) {
        if (result.isSuccess()) {
            return ResponseEntity.ok(Map.of("success", true, "data", result.getValue()));
        }
        return ResponseEntity.badRequest().body(Map.of("success", false, "error", result.getError()));
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
