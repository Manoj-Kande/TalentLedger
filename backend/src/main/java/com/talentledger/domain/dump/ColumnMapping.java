package com.talentledger.domain.dump;

import com.talentledger.domain.shared.BusinessRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable value object representing the mapping between detected file
 * headers and standard contact fields.
 *
 * <p>Tracks which raw headers were successfully auto-mapped to known
 * fields and which could not be resolved.
 *
 * <p>Use {@link #builder()} to construct instances.
 */
public final class ColumnMapping {

    private final List<String> detectedHeaders;
    private final Map<String, String> mappedFields;
    private final double confidence;
    private final List<String> unmappedHeaders;

    private ColumnMapping(List<String> detectedHeaders,
                         Map<String, String> mappedFields,
                         double confidence,
                         List<String> unmappedHeaders) {
        this.detectedHeaders = Collections.unmodifiableList(
                new ArrayList<>(detectedHeaders));
        this.mappedFields = Collections.unmodifiableMap(
                new HashMap<>(mappedFields));
        this.unmappedHeaders = Collections.unmodifiableList(
                new ArrayList<>(unmappedHeaders));
        this.confidence = confidence;
    }

    /**
     * Factory method that computes confidence from detected and mapped headers.
     *
     * <p>Confidence = number of mapped headers / total detected headers.
     * If there are zero detected headers, confidence is {@code 0.0}.
     *
     * @param detectedHeaders all raw headers found in the file (must not be null)
     * @param mappedFields    map of detected header to standard field name (must not be null)
     * @return a new ColumnMapping with computed confidence and unmapped headers
     */
    public static ColumnMapping of(List<String> detectedHeaders,
                                   Map<String, String> mappedFields) {
        BusinessRule.notNull(detectedHeaders, "Detected headers");
        BusinessRule.notNull(mappedFields, "Mapped fields");

        Set<String> mappedKeys = new HashSet<>(mappedFields.keySet());
        List<String> unmapped = new ArrayList<>();

        for (String header : detectedHeaders) {
            if (!mappedKeys.contains(header)) {
                unmapped.add(header);
            }
        }

        double confidence = 0.0;
        if (!detectedHeaders.isEmpty()) {
            confidence = (double) mappedFields.size() / detectedHeaders.size();
        }

        return new ColumnMapping(
                detectedHeaders,
                mappedFields,
                confidence,
                unmapped
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    // -- Getters --

    public List<String> getDetectedHeaders() {
        return detectedHeaders;
    }

    public Map<String, String> getMappedFields() {
        return mappedFields;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<String> getUnmappedHeaders() {
        return unmappedHeaders;
    }

    // -- Builder --

    public static final class Builder {

        private List<String> detectedHeaders = new ArrayList<>();
        private Map<String, String> mappedFields = new HashMap<>();
        private Double confidence;
        private List<String> unmappedHeaders = new ArrayList<>();

        private Builder() {}

        public Builder detectedHeaders(List<String> detectedHeaders) {
            this.detectedHeaders = detectedHeaders != null
                    ? new ArrayList<>(detectedHeaders) : new ArrayList<>();
            return this;
        }

        public Builder mappedFields(Map<String, String> mappedFields) {
            this.mappedFields = mappedFields != null
                    ? new HashMap<>(mappedFields) : new HashMap<>();
            return this;
        }

        public Builder confidence(double confidence) {
            BusinessRule.ensure(confidence >= 0.0 && confidence <= 1.0,
                    "Confidence must be between 0.0 and 1.0");
            this.confidence = confidence;
            return this;
        }

        public Builder unmappedHeaders(List<String> unmappedHeaders) {
            this.unmappedHeaders = unmappedHeaders != null
                    ? new ArrayList<>(unmappedHeaders) : new ArrayList<>();
            return this;
        }

        public ColumnMapping build() {
            double finalConfidence;
            if (this.confidence != null) {
                finalConfidence = this.confidence;
            } else {
                finalConfidence = detectedHeaders.isEmpty()
                        ? 0.0
                        : (double) mappedFields.size() / detectedHeaders.size();
            }

            List<String> finalUnmapped = this.unmappedHeaders;
            if (finalUnmapped.isEmpty() && !detectedHeaders.isEmpty()) {
                Set<String> mappedKeys = new HashSet<>(mappedFields.keySet());
                finalUnmapped = new ArrayList<>();
                for (String header : detectedHeaders) {
                    if (!mappedKeys.contains(header)) {
                        finalUnmapped.add(header);
                    }
                }
            }

            return new ColumnMapping(
                    detectedHeaders,
                    mappedFields,
                    finalConfidence,
                    finalUnmapped
            );
        }
    }
}
