package com.serverpilot.app.file;

import com.serverpilot.app.file.dto.FileEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for file system and file operations on remote servers.
 *
 * @author karthicknathan
 * @since Nov 11, 2026
 */
@RestController
@RequestMapping("/api/servers/{serverId}/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * Lists the files and directories at the given path on a remote server.
     * If no path is provided, the root directory (/) is listed by default.
     *
     * @param serverId ID of the remote server.
     * @param path     Directory path to list (defaults to /).
     *
     * @return list of files and directories at the given path.
     */
    @GetMapping
    public List<FileEntry> list(@PathVariable Long serverId, @RequestParam(defaultValue = "/") String path) {
        return fileService.list(serverId, path);
    }

    /**
     * Creates a new directory on the remote server.
     *
     * @param serverId ID of the remote server.
     * @param body     Request body containing the directory path.
     *
     * @return HTTP 200 OK if the directory is created successfully.
     */
    @PostMapping("/mkdir")
    public ResponseEntity<Void> mkdir(@PathVariable Long serverId, @RequestBody Map<String, String> body) {
        fileService.mkdir(serverId, body.get("path"));
        return ResponseEntity.ok().build();
    }

    /**
     * Deletes a file or directory from the remote server.
     *
     * @param serverId ID of the remote server.
     * @param path     Path of the file or directory to delete.
     *
     * @return HTTP 200 OK if the deletion is successful.
     */
    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable Long serverId, @RequestParam String path) {
        fileService.delete(serverId, path);
        return ResponseEntity.ok().build();
    }

    /**
     * Renames or moves a file or directory on the remote server.
     *
     * @param serverId ID of the remote server/
     * @param body     Request body containing the current and new file paths.
     *
     * @return HTTP 200 OK if the rename or move is successful.
     */
    @PostMapping("/rename")
    public ResponseEntity<Void> rename(@PathVariable Long serverId, @RequestBody Map<String, String> body) {
        fileService.rename(serverId, body.get("oldPath"), body.get("newPath"));
        return ResponseEntity.ok().build();
    }
}