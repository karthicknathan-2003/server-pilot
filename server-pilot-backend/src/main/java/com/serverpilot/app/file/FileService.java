package com.serverpilot.app.file;

import com.jcraft.jsch.Session;
import com.serverpilot.app.file.dto.FileEntry;
import com.serverpilot.app.server.ServerRepository;
import com.serverpilot.app.server.SshService;
import com.serverpilot.app.server.dto.CommandResult;
import com.serverpilot.app.server.entity.ServerEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
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
        if (firstQuote != -1) {
            // Everything before the first quote contains: perms, owner, size/devices, date, time.
            prefix = trimmed.substring(0, firstQuote).trim();
            // Decode the entry name using the complete C-style quoting decoder.
            DecodeResult nameResult = decodeQuotedString(trimmed, firstQuote);
            if (nameResult == null) return null;
            name = nameResult.text();
            // Decode optional link target if present (symlinks format as "name" -> "target").
            DecodeResult targetResult = decodeQuotedString(trimmed, nameResult.nextIndex());
            if (targetResult != null) {
                linkTarget = targetResult.text();
            }
        } else {
            /* Unquoted Fallback Parsing:
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
        boolean isDevice = perms.startsWith("c") || perms.startsWith("b");
        String type = perms.startsWith("d") ? "dir"
                : perms.startsWith("l") ? "link"
                : isDevice ? "device"
                : "file";
        String modified;
        String size = "—";
        if (isDevice) {
            // Devices shift date & time to columns 6 and 7 due to major/minor numbers.
            if (prefixParts.length >= 8) {
                modified = prefixParts[6] + " " + prefixParts[7];
            } else {
                modified = prefixParts[prefixParts.length - 2] + " " + prefixParts[prefixParts.length - 1];
            }
            size = "—";
        } else {
            if (prefixParts.length >= 7) {
                modified = prefixParts[5] + " " + prefixParts[6];
            } else {
                modified = prefixParts[prefixParts.length - 2] + " " + prefixParts[prefixParts.length - 1];
            }
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

    /**
     * Represents the result of decoding a C-style quoted string.
     *
     * @param text      The fully unescaped string.
     * @param nextIndex The index immediately following the closing quote.
     */
    private record DecodeResult(String text, int nextIndex) {
    }

    /**
     * Decodes a double-quoted string produced by {@code ls -la -Q} back into its real filename.
     *
     * <p><b>Why we need this:</b><br>
     * When we ask {@code ls} for quoted filenames using the {@code -Q} flag, Linux formats them
     * using C-style escaping. Ordinary characters stay as they are, but special or non-printable
     * characters get escaped. For example, spaces stay inside the quotes, quotes become {@code \"},
     * tabs become {@code \t}, backspaces become {@code \b}, and non-ASCII or control characters
     * are printed as octal byte sequences like {@code \001} or {@code \303\251}.</p>
     *
     * <p>If we don't decode these escapes properly (or only partially replace a few like {@code \n}),
     * a filename containing a backspace would turn into a literal letter 'b', or an accented letter
     * would turn into numbers. When a user later tries to click, rename, or delete that file, the server
     * will say "No such file or directory" because the filename in our database doesn't match the real one.</p>
     *
     * @param s          The raw line from {@code ls} containing the quoted text.
     * @param startIndex Where to start searching for the opening double quote.
     *
     * @return A {@link DecodeResult} holding the unescaped string and where the quote ended,
     * or {@code null} if no quote was found.
     */
    private DecodeResult decodeQuotedString(String s, int startIndex) {
        // Find where the filename starts (the first opening double-quote)
        int firstQuote = s.indexOf('"', startIndex);
        if (firstQuote == -1) {
            return null;
        }

        // We accumulate raw bytes instead of characters. This is important because
        // octal escapes (\303\251) represent raw UTF-8 bytes that must be pieced back together.
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        int i = firstQuote + 1;

        while (i < s.length()) {
            char c = s.charAt(i);

            // An unescaped quote marks the end of this filename
            if (c == '"') {
                return new DecodeResult(bout.toString(StandardCharsets.UTF_8), i + 1);
            }

            if (c == '\\') {
                i++;
                if (i >= s.length()) {
                    bout.write('\\');
                    break;
                }

                char esc = s.charAt(i);
                switch (esc) {
                    // Standard C control character escapes.
                    case 'a':
                        bout.write(7);
                        break;      // audible bell
                    case 'b':
                        bout.write('\b');
                        break;   // backspace
                    case 'f':
                        bout.write('\f');
                        break;   // form feed
                    case 'n':
                        bout.write('\n');
                        break;   // newline
                    case 'r':
                        bout.write('\r');
                        break;   // carriage return
                    case 't':
                        bout.write('\t');
                        break;   // tab
                    case 'v':
                        bout.write(11);
                        break;     // vertical tab
                    case '\\':
                        bout.write('\\');
                        break;   // literal backslash
                    case '"':
                        bout.write('"');
                        break;    // literal double quote
                    case '\'':
                        bout.write('\'');
                        break;   // literal single quote
                    case '?':
                        bout.write('?');
                        break;    // question mark

                    default:
                        // Handle octal numbers like \001 or \303.
                        // Linux ls prints non-printable bytes as 1 to 3 octal digits [0-7].
                        if (esc >= '0' && esc <= '7') {
                            int octVal = esc - '0';
                            int digits = 1;

                            // Grab up to two more octal digits if they follow immediately.
                            while (digits < 3 && i + 1 < s.length()) {
                                char next = s.charAt(i + 1);
                                if (next >= '0' && next <= '7') {
                                    octVal = (octVal << 3) + (next - '0');
                                    i++;
                                    digits++;
                                } else {
                                    break;
                                }
                            }
                            bout.write(octVal & 0xFF);
                        } else {
                            // If an unknown escape sequence shows up, keep the character as-is.
                            byte[] raw = String.valueOf(esc).getBytes(StandardCharsets.UTF_8);
                            bout.write(raw, 0, raw.length);
                        }
                        break;
                }
            } else {
                // Regular character: write its UTF-8 bytes directly.
                byte[] raw = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bout.write(raw, 0, raw.length);
            }
            i++;
        }
        // If line ended without a closing quote, return whatever we managed to decode.
        return new DecodeResult(bout.toString(StandardCharsets.UTF_8), i);
    }
}