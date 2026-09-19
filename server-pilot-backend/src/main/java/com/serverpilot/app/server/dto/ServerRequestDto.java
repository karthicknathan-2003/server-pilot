package com.serverpilot.app.server.dto;

/**
 * A read only DTO, used to hold and transfer the requested details from client to the server.
 *
 * <p>This is a Java record, all fields are set once at construction and cannot be changed.</p>
 *
 * @author karthicknathan
 * @since 13 Sep, 2026
 */
public record ServerRequestDto(
        String name,
        String host,
        int port,
        String username,
        String authType,   // "PASSWORD" | "KEY"
        String password,   // Null when authType=KEY
        String privateKey  // Null when authType=PASSWORD
) {
}