package com.serverpilot.app.server.dto;

/**
 * A read only DTO, used to hold Live System metrics like hostname, os and other related details,
 * which are retrieved from the remote server via SSH.
 *
 * <p>This is a Java record, all fields are set once at construction and cannot be changed.</p>
 *
 * @author karthicknathan
 * @since 13 Sep, 2026
 */
public record SystemMetricsDto(
        String hostname,
        String os,
        String kernel,
        String arch,
        String uptime,
        double cpuPercent,
        double ramUsedGb,
        double ramTotalGb,
        double diskUsedGb,
        double diskTotalGb,
        String networkDownload,
        String networkUpload
) {
}
