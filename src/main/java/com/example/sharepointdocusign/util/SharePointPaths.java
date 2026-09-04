package com.example.sharepointdocusign.util;

/**
 * Builds the SharePoint folder path for a PO. Two layouts are supported:
 * - With a sub-path: {poNumber}/{subPath}, e.g. "4500000105/02" or, since
 *   the sub-path may itself contain multiple "/"-separated levels,
 *   "4500000233/A1/A2/A3" - SAP fully controls both the depth and the name
 *   of each level. Every level is validated individually (same rules as a
 *   single segment always has been: no "..", no "/"/"\\" inside a level, no
 *   percent-encoded traversal, "^[A-Za-z0-9_-]+$"), so allowing more levels
 *   never weakens the existing path-traversal protection.
 * - Flat (no sub-path): {poNumber}, e.g. "4500000233"
 *
 * Each level is stored exactly as the caller sends it - no "REV-" or similar
 * label is added. Earlier versions prefixed the single second segment with
 * "REV-", assuming it always meant a PO revision number; real usage showed
 * that isn't always true, so this app makes no assumption about what any
 * level represents.
 */
public final class SharePointPaths {

    private SharePointPaths() {
    }

    /**
     * @param subPath one or more "/"-separated folder levels under poNumber
     *                (e.g. "02", or "A1/A2/A3") - every level is validated
     *                individually before any of it is used to build a path.
     */
    public static String buildFolderPath(String poNumber, String subPath) {
        FileValidationUtil.assertSafePathComponent(poNumber, "poNumber");
        if (subPath == null || subPath.isBlank()) {
            FileValidationUtil.assertSafePathComponent(subPath, "folder path segment");
        }
        for (String level : subPath.split("/")) {
            FileValidationUtil.assertSafePathComponent(level, "folder path segment");
        }
        return poNumber + "/" + subPath;
    }

    /** Flat layout: the PO number folder itself contains the documents directly, no revision subfolder. */
    public static String buildFolderPath(String poNumber) {
        FileValidationUtil.assertSafePathComponent(poNumber, "poNumber");
        return poNumber;
    }
}
