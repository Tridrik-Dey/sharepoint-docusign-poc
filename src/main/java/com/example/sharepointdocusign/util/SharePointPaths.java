package com.example.sharepointdocusign.util;

/**
 * Builds the SharePoint folder path for a PO number and revision:
 * {poNumber}/REV-{revision}, e.g. "4500000105/REV-02".
 */
public final class SharePointPaths {

    private SharePointPaths() {
    }

    public static String buildFolderPath(String poNumber, String revision) {
        FileValidationUtil.assertSafePathComponent(poNumber, "poNumber");
        FileValidationUtil.assertSafePathComponent(revision, "revision");
        return poNumber + "/REV-" + revision;
    }
}
