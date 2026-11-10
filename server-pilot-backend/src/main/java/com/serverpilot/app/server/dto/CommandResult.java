package com.serverpilot.app.server.dto;

/**
 * Combine and holds the execution result of an SSH command.
 *
 * @param exitStatus The command exit code (0 for success).
 * @param stdout     Standard output returned by the command.
 * @param stderr     Standard error returned by the command.
 *
 * @author karthicknathan
 * @since Nov 06, 2026
 */
public record CommandResult(int exitStatus, String stdout, String stderr) {
    public boolean isSuccess() {
        return exitStatus == 0;
    }
}