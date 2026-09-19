package com.serverpilot.app.server.dto;

/**
 * A read only DTO, used to transfer the response from server to the client.
 *
 * <p>This is a Java record, all fields are set once at construction and cannot be changed.</p>
 *
 * @author karthicknathan
 * @since 13 Sep, 2026
 */
public record ServerResponseDto(
        Long id,
        String name,
        String host,
        int port,
        String username,
        String authType,
        String status      // "online" or "offline"
) {
}