package com.serverpilot.app.file.dto;

/**
 * Represents a file or directory entry retrieved from a remote server.
 * Holds basic information about a file or directory metadata.
 *
 * @author karthicknathan
 * @since Nov 11, 2026
 */
public record FileEntry(
        String name,
        String type,
        String size,
        String permissions,
        String modified
) {
}