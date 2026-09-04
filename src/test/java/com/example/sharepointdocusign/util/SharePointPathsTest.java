package com.example.sharepointdocusign.util;

import com.example.sharepointdocusign.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SharePointPathsTest {

    @Test
    void buildsFolderPathFromPoNumberAndRevision() {
        assertThat(SharePointPaths.buildFolderPath("4500000105", "02")).isEqualTo("4500000105/02");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc", "a/b", "a\\b", "..", "a..b/../c", "%2e%2e%2fsecrets", "%2F", "%5c"})
    void rejectsPathTraversalInPoNumber(String malicious) {
        assertThatThrownBy(() -> SharePointPaths.buildFolderPath(malicious, "02"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"../02", "02/..", "0\\2"})
    void rejectsPathTraversalInRevision(String malicious) {
        assertThatThrownBy(() -> SharePointPaths.buildFolderPath("4500000105", malicious))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void buildsFolderPathFromMultiLevelSubPath() {
        assertThat(SharePointPaths.buildFolderPath("4500000233", "A1/A2"))
                .isEqualTo("4500000233/A1/A2");
        assertThat(SharePointPaths.buildFolderPath("4500000233", "A1/A2/A3/A4"))
                .isEqualTo("4500000233/A1/A2/A3/A4");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../A2", "A1/..", "A1/../A2", "A1/A2/..", "A1/A2/../A3",
            "A1/A..2/../A3", "a\\b/A2", "A1/a\\b", "%2e%2e%2f/A2", "A1/%2F"})
    void rejectsPathTraversalInAnyLevelOfAMultiSegmentSubPath(String malicious) {
        assertThatThrownBy(() -> SharePointPaths.buildFolderPath("4500000233", malicious))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsBlankLevelWithinAMultiSegmentSubPath() {
        assertThatThrownBy(() -> SharePointPaths.buildFolderPath("4500000233", "A1//A2"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsBlankPoNumber() {
        assertThatThrownBy(() -> SharePointPaths.buildFolderPath(" ", "02"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void buildsFlatFolderPathFromPoNumberAlone() {
        assertThat(SharePointPaths.buildFolderPath("4500000233")).isEqualTo("4500000233");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../etc", "a/b", "a\\b", "..", "a..b/../c", "%2e%2e%2fsecrets", "%2F", "%5c"})
    void rejectsPathTraversalInFlatPoNumber(String malicious) {
        assertThatThrownBy(() -> SharePointPaths.buildFolderPath(malicious))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsBlankFlatPoNumber() {
        assertThatThrownBy(() -> SharePointPaths.buildFolderPath(" "))
                .isInstanceOf(InvalidRequestException.class);
    }
}
