package com.serverpilot.app.service;

import com.serverpilot.app.service.dto.ServiceInfo;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * REST controller to view and manage system services running on a remote server.
 *
 * @author karthicknathan
 * @since Nov 10, 2026
 */
@RestController
@RequestMapping("/api/servers/{serverId}/services")
public class ServiceController {

    private final SystemdService systemdService;

    public ServiceController(SystemdService systemdService) {
        this.systemdService = systemdService;
    }

    /**
     * Retrieves the list of system services available on the selected server.
     *
     * @param serverId ID of the selected remote server.
     *
     * @return List of system services that are configured on the selected server.
     */
    @GetMapping
    public List<ServiceInfo> list(@PathVariable Long serverId) {
        return systemdService.list(serverId);
    }

    /**
     * Executes the requested action on a system service on the remote server.
     *
     * @param serverId ID of the selected remote server.
     * @param name     Name of the service to control.
     * @param action   Action to perform on the service (start, stop, restart, enable, or disable).
     *
     * @return Empty response with HTTP 200, if the action is successful.
     */
    @PostMapping("/{name}/{action}")
    public ResponseEntity<Void> control(@PathVariable Long serverId, @PathVariable String name,
                                        @PathVariable String action) {
        try {
            systemdService.control(serverId, name, action);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}