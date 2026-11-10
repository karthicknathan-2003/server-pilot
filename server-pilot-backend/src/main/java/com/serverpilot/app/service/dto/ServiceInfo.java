package com.serverpilot.app.service.dto;

/**
 * Holds basic information about a systemd service running on a remote server.
 *
 * @param name        The name of the systemd service.
 * @param status      The current status of the service (active, inactive or failed).
 * @param enabled     Whether the service is configured to start automatically at boot.
 * @param description A short description of what the service does.
 *
 * @author karthicknathan
 * @since Nov 02, 2026
 */
public record ServiceInfo(
        String name,
        String status,      // "active" | "inactive" | "failed"
        boolean enabled,
        String description
) {
}