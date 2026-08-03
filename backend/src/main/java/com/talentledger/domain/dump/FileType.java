package com.talentledger.domain.dump;

/**
 * Supported file types for data dump uploads.
 *
 * <p>Each constant maps to a recognised file extension.
 * Pure Java enum — zero framework dependency.
 */
public enum FileType {

    CSV,
    XLSX,
    XLS,
    PDF,
    JSON,
    TXT
}
