package com.example.sharepointdocusign.exception;

import org.springframework.http.HttpStatus;

public class EmptySharePointFolderException extends PoEnvelopeException {

    public EmptySharePointFolderException(String message) {
        super("SHAREPOINT_FOLDER_EMPTY", message, HttpStatus.NOT_FOUND);
    }
}
