package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

public class SharePointFolderNotFoundException extends PoEnvelopeException {

    public SharePointFolderNotFoundException(String message) {
        super("SHAREPOINT_FOLDER_NOT_FOUND", message, HttpStatus.NOT_FOUND);
    }
}
