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
     * directory of the remote server. Runs {@code ls -la -Q --time-style=long-iso}, so entry
     * names are quoted and date format is consistently {@code YYYY-MM-DD HH:MM}.</p>
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
            // -Q wraps filenames and link targets in double quotes, providing unambiguous boundaries.
            CommandResult commandResult = sshService.exec(session,
                    "ls -la -Q --time-style=long-iso " + escapeShell(path));

            if (commandResult.exitStatus() != 0) {
                String error = commandResult.stderr().isBlank()
                        ? "Command exited with status " + commandResult.exitStatus()
                        : commandResult.stderr();
                throw new RuntimeException(error);
            }

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
     * Renames or moves a file or directory on the remote server without replacing existing destinations.
     *
     * @param serverId ID of the remote server to connect.
     * @param oldPath  Current absolute path of the file or directory.
     * @param newPath  New absolute path to rename or move.
     *
     * @throws RuntimeException If the server is not found, destination exists, or command fails.
     */
    public void rename(Long serverId, String oldPath, String newPath) {
        rename(serverId, oldPath, newPath, false);
    }

    /**
     * Renames or moves a file or directory on the remote server.
     *
     * @param serverId  ID of the remote server to connect.
     * @param oldPath   Current absolute path of the file or directory.
     * @param newPath   New absolute path to rename or move.
     * @param overwrite If true, allows replacing an existing destination; otherwise rejects it.
     *
     * @throws RuntimeException If destination exists without overwrite confirmation or SSH command fails.
     */
    public void rename(Long serverId, String oldPath, String newPath, boolean overwrite) {
        if (!overwrite) {
            // Prevent Accidental Overwrite:
            // Why: Standard Linux `mv` silently overwrites existing destination files,
            // which can cause unintentional data loss. GNU `mv -n` is insufficient because
            // older versions silently exit with status 0 without notifying the caller.
            //
            // What this shell script does:
            // 1. `[ -e ... ]`: Checks if destination exists as a regular file, dir, or device.
            // 2. `[ -L ... ]`: Checks if destination exists as a symlink. This is crucial
            //    because broken symlinks return FALSE for `-e`, yet `mv` would still overwrite them.
            // 3. If either test succeeds, write an error message to stderr (>&2) and exit with code 1.
            //    `exec()` detects this non-zero exit code and bubbles the error up to the client.
            // 4. If destination does not exist, run `mv` to complete the rename operation.
            String command = "if [ -e " + escapeShell(newPath) + " ] || [ -L " + escapeShell(newPath) +
                    " ]; then echo 'Destination already exists: " + escapeShell(newPath)
                    .replace("'", "") + "' >&2; exit 1; fi; mv "
                    + escapeShell(oldPath) + " " + escapeShell(newPath);
            exec(serverId, command);
        } else {
            exec(serverId, "mv -f " + escapeShell(oldPath) + " " + escapeShell(newPath));
        }
    }

    // Helper Methods.

    /**
     * Opens an SSH session for the given server, runs the given command and disconnects.
     * Shared by file operation methods to avoid repeating the same SSH call logic.
     *
     * @param serverId ID of the remote server to connect.
     * @param command  Linux shell command to be executed on the remote server.
     */
    private void exec(Long serverId, String command) {
        ServerEntity server = serverRepo.findById(serverId).orElseThrow();
        Session session = null;
        try {
            session = sshService.openSession(server);
            CommandResult result = sshService.exec(session, command);
            if (result.exitStatus() != 0) {
                String error = result.stderr().isBlank()
                        ? "Command exited with status " + result.exitStatus()
                        : result.stderr();
                throw new RuntimeException(error);
            }
        } catch (Exception e) {
            throw new RuntimeException("SSH command failed: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * Parses a single line from {@code ls -la -Q} output into a {@link FileEntry}.
     *
     * <p>Using {@code -Q} guarantees that filenames and symlink targets are enclosed in
     * double quotes with C-style escaping. This prevents filename corruption when names
     * contain spaces, special characters, or {@code ->}.</p>
     *
     * @param line A single raw line from the {@code ls} output.
     *
     * @return A populated {@link FileEntry}, or {@code null} if the line should be skipped.
     */
    private FileEntry parseLsLine(String line) {
        // Skip header lines (e.g. "total 10452") and empty lines produced by trailing newlines.
        if (line.startsWith("total") || line.isBlank()) return null;

        String trimmed = line.trim();
        int firstQuote = trimmed.indexOf('"');

        String prefix;
        String name;
        String linkTarget = null;

        // Quoted Output Parsing (from `ls -la -Q`):
        // Why: Standard `ls` outputs symlinks as `name -> target`. If a filename itself
        // contains " -> " or spaces, splitting by whitespace or " -> " is ambiguous and
        // corrupts filenames. GNU `ls -Q` cleanly wraps names as:
        //   - Regular file:  drwxr-xr-x ... "my file.txt"
        //   - Symlink:       lrwxrwxrwx ... "link name" -> "target path"
        // The first quote is guaranteed to mark the start of the file name.
        if (firstQuote != -1) {
            // Everything before the first quote contains standard metadata:
            // perms, owner, size/devices, date, time
            prefix = trimmed.substring(0, firstQuote).trim();

            // Parse the first quoted string (the entry's exact name),
            // handling C-style escapes (\", \\, \n, \t).
            StringBuilder nameSb = new StringBuilder();
            int i = firstQuote + 1;
            boolean escaped = false;
            while (i < trimmed.length()) {
                char c = trimmed.charAt(i);
                if (escaped) {
                    if (c == 'n') nameSb.append('\n');
                    else if (c == 't') nameSb.append('\t');
                    else if (c == 'r') nameSb.append('\r');
                    else nameSb.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true; // Next character is escaped (e.g. embedded quote \").
                } else if (c == '"') {
                    i++; // Closing quote found; marks the end of the entry name.
                    break;
                } else {
                    nameSb.append(c);
                }
                i++;
            }
            name = nameSb.toString();

            /* If this is a symlink, `ls -Q` appends ` -> "target"`. We locate the next quoted string
            to extract the target path cleanly without ever splitting on the "->" token. */
            int nextQuote = trimmed.indexOf('"', i);
            if (nextQuote != -1) {
                StringBuilder targetSb = new StringBuilder();
                int j = nextQuote + 1;
                escaped = false;
                while (j < trimmed.length()) {
                    char c = trimmed.charAt(j);
                    if (escaped) {
                        if (c == 'n') targetSb.append('\n');
                        else if (c == 't') targetSb.append('\t');
                        else if (c == 'r') targetSb.append('\r');
                        else targetSb.append(c);
                        escaped = false;
                    } else if (c == '\\') {
                        escaped = true;
                    } else if (c == '"') {
                        break; // Closing quote for the link target.
                    } else {
                        targetSb.append(c);
                    }
                    j++;
                }
                linkTarget = targetSb.toString();
            }
        } else {
            /* Unquoted Fallback Parsing:
            Why: Fallback in case `ls` output was executed without `-Q`.
            Device entries shift columns (9 parts instead of 8), so we detect device type
            before determining where the name token begins. */
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 8) return null;

            String perms = parts[0];
            boolean isDev = perms.startsWith("c") || perms.startsWith("b");
            int minParts = isDev ? 9 : 8;

            String[] splitParts = trimmed.split("\\s+", minParts);
            if (splitParts.length < minParts) return null;

            prefix = trimmed.substring(0, trimmed.lastIndexOf(splitParts[minParts - 1])).trim();
            name = splitParts[minParts - 1];
        }

        // Skip current and parent directory pointers.
        if (name.equals(".") || name.equals("..")) return null;

        // Metadata Token Resolution.
        String[] prefixParts = prefix.split("\\s+");
        if (prefixParts.length < 6) return null;

        String perms = prefixParts[0];

        /* Why: In Linux, permissions mode string reliably identifies file type by its 1st character:
        'd' = directory, 'l' = symlink, 'c' = character device, 'b' = block device, '-' = regular file. */
        boolean isDevice = perms.startsWith("c") || perms.startsWith("b");
        String type = perms.startsWith("d") ? "dir"
                : perms.startsWith("l") ? "link"
                : isDevice ? "device"
                : "file";

        String modified;
        String size = "—";

        if (isDevice) {
            /* Device Major/Minor Handling:
            Why: Devices (e.g. /dev/null, /dev/sda) do not have a byte size.
            Instead, `ls -l` outputs "major, minor" numbers (e.g., `1, 3` with spaces),
            which shifts modified date and time to columns 6 and 7 (instead of 5 and 6).
            We set size to "—" and never pass "1," to Long.parseLong to avoid NumberFormatException. */
            if (prefixParts.length >= 8) {
                modified = prefixParts[6] + " " + prefixParts[7];
            } else {
                modified = prefixParts[prefixParts.length - 2] + " " + prefixParts[prefixParts.length - 1];
            }
            size = "—";
        } else {
            // Regular entries have size at index 4, date at 5, and time at 6
            if (prefixParts.length >= 7) {
                modified = prefixParts[5] + " " + prefixParts[6];
            } else {
                modified = prefixParts[prefixParts.length - 2] + " " + prefixParts[prefixParts.length - 1];
            }

            // Only regular files display byte sizes. We guard with try-catch so that an
            // unexpected token never crashes or aborts the entire directory listing.
            if (type.equals("file")) {
                try {
                    size = formatBytes(Long.parseLong(prefixParts[4]));
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    size = "—";
                }
            }
        }

        return new FileEntry(name, type, size, perms, modified, linkTarget);
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