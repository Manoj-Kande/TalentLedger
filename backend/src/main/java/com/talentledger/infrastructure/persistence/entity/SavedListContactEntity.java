package com.talentledger.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity mapping the {@code saved_list_contacts} junction table.
 *
 * <p>Composite primary key: (list_id, contact_id). Uses {@link IdClass}.
 * 4 columns total.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "saved_list_contacts")
@IdClass(SavedListContactEntity.SavedListContactId.class)
public class SavedListContactEntity {

    @Id
    @ManyToOne
    @JoinColumn(name = "list_id", nullable = false, updatable = false)
    private SavedListEntity list;

    @Id
    @Column(name = "contact_id", nullable = false, updatable = false)
    private UUID contactId;

    @Column(name = "added_at")
    private Instant addedAt;

    @Column(name = "added_reason", length = 50)
    private String addedReason;

    // ── Composite Key Class ──────────────────────────────

    /**
     * Serializable composite primary key for the {@code saved_list_contacts} table.
     * Must implement {@link #equals(Object)} and {@link #hashCode()}.
     */
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SavedListContactId implements Serializable {

        private static final long serialVersionUID = 1L;

        private UUID list;
        private UUID contactId;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof SavedListContactId that)) return false;
            return Objects.equals(list, that.list)
                    && Objects.equals(contactId, that.contactId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(list, contactId);
        }
    }
}
