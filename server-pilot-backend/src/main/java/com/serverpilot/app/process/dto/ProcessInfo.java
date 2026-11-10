package com.serverpilot.app.process.dto;

/**
 * Holds basic information about a running process on a remote server.
 *
 * @param pid     The unique ID of the process.
 * @param name    The name of the process.
 * @param user    The user running the process.
 * @param cpu     The CPU usage percentage of the process.
 * @param memGb   The memory used by the process in gigabytes.
 * @param command The full command used to start the process.
 *
 * @author karthicknathan
 * @since Nov 01, 2026
 */
public record ProcessInfo(
        int pid,
        String name,
        String user,
        double cpu,
        double memGb,
        String command
) {
}