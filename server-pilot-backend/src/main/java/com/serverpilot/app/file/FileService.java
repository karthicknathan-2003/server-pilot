package com.serverpilot.app.file;

import com.jcraft.jsch.Session;
import com.serverpilot.app.file.dto.FileEntry;
import com.serverpilot.app.server.ServerRepository;
import com.serverpilot.app.server.SshService;
import com.serverpilot.app.server.dto.CommandResult;
import com.serverpilot.app.server.entity.ServerEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides the browsing and managing files on remote servers.
 *
 * <p>All file operations (listing, creating, deleting, renaming) are executed
 * over SSH using the shared {@link SshService}. Each operation opens a new
 * session, runs the command, and disconnects immediately to keep connections short-lived.</p>
 *
 * @author karthicknathan
 * @since Nov 11, 2026
 */
@Service
public class FileService {

    private final ServerRepository serverRepo;
    private final SshService sshService;

    public FileService(ServerRepository serverRepo, SshService sshService) {
        this.serverRepo = serverRepo;
        this.sshService = sshService;
    }

    /**
     * Lists all files and directories at the given path on the remote server.
     *
     * <p>On the initial call, the default value will be "/". This will list the root
     * directory of the remote server. Runs {@code ls -la --time-style=long-iso}, so the
     * date format is always {@code YYYY-MM-DD HH:MM}, which is easy to parse consistently.</p>
     *
     * @param serverId ID of the remote server to connect.
     * @param path     Absolute path of the directory to list (e.g. {@code /home/ubuntu}).
     *
     * @return List of {@link FileEntry} objects. One per file or directory found.
     *
     * @throws RuntimeException If the server is not found or the SSH command fails.
     */
    public List<FileEntry> list(Long serverId, String path) {
        ServerEntity server = serverRepo.findById(serverId).orElseThrow();
        List<FileEntry> fileEntryList = new ArrayList<>();
        Session session = null;
        try {
            session = sshService.openSession(server);
            // Using long-iso format to keep file dates consistent.
            CommandResult commandResult = sshService.exec(session,
                    "ls -la --time-style=long-iso " + escapeShell(path));
            // Parse each line of the ls output into a FileEntry.
            FileEntry fileEntry;
            for (String line : commandResult.stdout().split("\n")) {
                fileEntry = parseLsLine(line);
                if (fileEntry != null) fileEntryList.add(fileEntry);
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to list directory: " + ex.getMessage(), ex);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
        return fileEntryList;
    }

    /**
     * Creates a directory and any missing parent directories at the given path.
     *
     * @param serverId ID of the remote server.
     * @param path     Absolute path of the directory to create.
     *
     * @throws RuntimeException If the server is not found or the SSH command fails.
     */
    public void mkdir(Long serverId, String path) {
        exec(serverId, "mkdir -p " + escapeShell(path));
    }

    /**
     * Deletes a file or directory and all its contents at the given path.
     *
     * <p>Executes {@code rm -rf}, so this operation is irreversible. Blank paths
     * and {@code /} are rejected, to prevent accidental deletion of the entire file system.</p>
     *
     * @param serverId ID of the remote server.
     * @param path     Absolute path of the file or directory to delete.
     *
     * @throws RuntimeException If the path is blank or root, the server is not found,
     *                          or the SSH command fails.
     */
    public void delete(Long serverId, String path) {
        // The path "/" is the root directory. So, need to prevent
        // any accidental deletion of the entire file system.
        if (path.equals("/") || path.isBlank()) throw new RuntimeException("Root Directory cannot be deleted.");
        exec(serverId, "rm -rf " + escapeShell(path));
    }

    /**
     * Renames or moves a file or directory on the remote server.
     *
     * @param serverId ID of the remote server to connect.
     * @param oldPath  Current absolute path of the file or directory.
     * @param newPath  New absolute path to rename or move.
     *
     * @throws RuntimeException If the server is not found or the SSH command fails.
     */
    public void rename(Long serverId, String oldPath, String newPath) {
        exec(serverId, "mv " + escapeShell(oldPath) + " " + escapeShell(newPath));
    }

    // Helper Methods.

    /**
     * Opens an SSH session for the given server, runs the given command and disconnects.
     * Shared by file operation methods to avoid repeating the same SHH call logic.
     *
     * @param serverId ID of the remote server to connect.
     * @param command  Linux shell command to be executed on the remote server.
     */
    private void exec(Long serverId, String command) {
        ServerEntity server = serverRepo.findById(serverId).orElseThrow();
        Session session = null;
        try {
            session = sshService.openSession(server);
            sshService.exec(session, command);
            session.disconnect();
        } catch (Exception e) {
            throw new RuntimeException("SSH command failed: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * Parses a single line from {@code ls -la} output into a {@link FileEntry}.
     *
     * <p>Expected format (8 whitespace-separated tokens, last token is the name):
     * <pre>drwxr-xr-x 2 ubuntu ubuntu 4096 2026-09-01 12:30 app</pre>
     * Lines starting with "total", blank lines, and {@code .} / {@code ..} entries
     * are silently skipped by returning {@code null}.</p>
     *
     * @param line A single raw line from the {@code ls} output.
     *
     * @return A populated {@link FileEntry}, or {@code null} if the line should be skipped.
     */
    private FileEntry parseLsLine(String line) {
        // Skip the summary line and current/parent directory entries.
        if (line.startsWith("total") || line.isBlank()) return null;
        String[] parts = line.trim().split("\\s+", 8);
        if (parts.length < 8) return null;

        String perms = parts[0];
        String name = parts[7];

        if (name.equals(".") || name.equals("..")) return null;

        String type = perms.startsWith("d") ? "dir"
                : perms.startsWith("l") ? "link"
                : "file";

        String size = type.equals("file") ? formatBytes(Long.parseLong(parts[4])) : "—";

        return new FileEntry(name, type, size, perms, parts[5] + " " + parts[6]);
    }

    /**
     * Converts a raw byte count into a human-readable string (B, KB, or MB).
     *
     * @param bytes Size in bytes.
     *
     * @return Formatted size string, e.g. {@code "512 B"}, {@code "4 KB"}, or {@code "1.5 MB"}.
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    /**
     * Wraps a shell path in single quotes and escapes any embedded single quotes.
     *
     * <p>This prevents shell injection when the path contains spaces or special
     * characters. For example, {@code /home/my dir} becomes {@code '/home/my dir'}.</p>
     *
     * @param path The file system path to escape.
     *
     * @return A single-quoted, shell-safe version of the provided path.
     */
    private String escapeShell(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }
}