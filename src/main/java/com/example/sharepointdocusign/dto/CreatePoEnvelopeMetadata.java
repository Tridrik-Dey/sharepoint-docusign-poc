package com.example.sharepointdocusign.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * JSON "metadata" part of the multipart POST /api/v1/po-envelopes request.
 */
public record CreatePoEnvelopeMetadata(

        @NotBlank(message = "poNumber is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "poNumber may contain letters, numbers, hyphens and underscores only")
        String poNumber,

        @NotBlank(message = "revision is required")
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "revision may contain letters, numbers, hyphens and underscores only")
        String revision,

        @NotBlank(message = "vendorName is required")
        String vendorName,

        @NotBlank(message = "vendorEmail is required")
        @Email(message = "vendorEmail must be a valid email address")
        String vendorEmail) {
}
