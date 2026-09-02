package com.example.sharepointdocusign.model;

/**
 * Result of storing one document into a SharePoint folder. {@code fileName}
 * is the name it was actually stored under - it may differ from the name
 * that was requested if Microsoft Graph auto-renamed it to avoid overwriting
 * an existing file with the same name (see
 * {@code @microsoft.graph.conflictBehavior=rename} in MicrosoftGraphClient),
 * in which case {@code renamed} is true.
 */
public record SharePointUploadResult(String itemId, String fileName, long size, String sha256, boolean renamed) {
}
