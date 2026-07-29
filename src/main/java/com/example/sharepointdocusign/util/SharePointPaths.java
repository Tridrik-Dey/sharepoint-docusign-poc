package com.example.sharepointdocusign.util;

/**
 * Builds the SharePoint folder path for a PO. Two layouts are supported:
 * - With revision: {poNumber}/REV-{revision}, e.g. "4500000105/REV-02"
 * - Flat (no revision subfolder): {poNumber}, e.g. "4500000233"
 */
public final class SharePointPaths {

    private SharePointPaths() {
    }

    public static String buildFolderPath(String poNumber, String revision) {
        FileValidationUtil.assertSafePathComponent(poNumber, "poNumber");
        FileValidationUtil.assertSafePathComponent(revision, "revision");
        return poNumber + "/REV-" + revision;
    }

    /** Flat layout: the PO number folder itself contains the documents directly, no revision subfolder. */
    public static String buildFolderPath(String poNumber) {
        FileValidationUtil.assertSafePathComponent(poNumber, "poNumber");
        return poNumber;
    }
}
