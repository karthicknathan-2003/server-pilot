package com.serverpilot.app.process;

import com.serverpilot.app.process.dto.ProcessInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for viewing and managing processes running on a remote server.
 *
 * @author karthicknathan
 * @since Nov 10, 2026
 */
@RestController
@RequestMapping("/api/servers/{serverId}/processes")
public class ProcessController {

    private final ProcessService processService;

    public ProcessController(ProcessService processService) {
        this.processService = processService;
    }

    /**
     * Retrieves the list of processes currently running on the selected server.
     *
     * @param serverId ID of the remote server whose processes need to be retrieved.
     *
     * @return List of processes running on the remote server.
     */
    @GetMapping
    public List<ProcessInfo> list(@PathVariable Long serverId) {
        return processService.list(serverId);
    }

    /**
     * Stops a running process on the selected server using its process ID(PID).
     *
     * @param serverId ID of the remote server where the process is running.
     * @param pid      ID of the process to kill.
     *
     * @return Empty response with HTTP 200, if the process is stopped successfully.
     */
    @DeleteMapping("/{pid}")
    public ResponseEntity<Void> kill(@PathVariable Long serverId, @PathVariable int pid) {
        processService.kill(serverId, pid);
        return ResponseEntity.ok().build();
    }
}