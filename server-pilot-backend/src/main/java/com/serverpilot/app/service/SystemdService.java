package com.serverpilot.app.service;

import com.jcraft.jsch.Session;
import com.serverpilot.app.server.ServerRepository;
import com.serverpilot.app.server.SshService;
import com.serverpilot.app.server.entity.ServerEntity;
import com.serverpilot.app.service.dto.ServiceInfo;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles systemd service operations on remote Linux servers, such as
 * viewing service details and starting, stopping, restarting or managing
 * services through SSH.
 *
 * <p>This class uses {@link SshService} to execute systemctl commands
 * on the remote server and retrieve service information.</p>
 *
 * @author karthicknathan
 * @since Nov 02, 2026
 */
@Service
public class SystemdService {

    private final ServerRepository serverRepo;
    private final SshService sshService;

    public SystemdService(ServerRepository serverRepo, SshService sshService) {
        this.serverRepo = serverRepo;
        this.sshService = sshService;
    }

    /**
     * Retrieves the list of systemd services available on the remote server.
     *
     * @param serverId The unique ID of the saved remote server.
     *
     * @return A list of systemd service details.
     */
    public List<ServiceInfo> list(Long serverId) {
        ServerEntity server = serverRepo.findById(serverId).orElseThrow();
        List<ServiceInfo> serviceInfoList = new ArrayList<>();
        try {
            Session session = sshService.openSession(server);
            // List only .service(not sockets/timers) in machine-readable form.
            String raw = sshService.exec(session,
                    "systemctl list-units --type=service --no-pager --no-legend");
            session.disconnect();

            ServiceInfo serviceInfo = null;
            for (String line : raw.split("\n")) {
                serviceInfo = parseServiceLine(line);
                if (serviceInfo != null) serviceInfoList.add(serviceInfo);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list services: " + e.getMessage(), e);
        }
        return serviceInfoList;
    }

    /**
     * Performs the requested action on a systemd service.
     *
     * @param serverId The unique ID of the saved remote server.
     * @param name     The name of the service to manage.
     * @param action   The action to perform (start, stop, restart, enable or disable).
     *
     * @throws IllegalArgumentException If the requested action is not supported.
     */
    public void control(Long serverId, String name, String action) {
        // "start" | "stop" | "restart" | "enable" | "disable"
        if (!List.of("start", "stop", "restart", "enable", "disable").contains(action)) {
            throw new IllegalArgumentException("Unknown action: " + action);
        }
        ServerEntity server = serverRepo.findById(serverId).orElseThrow();
        try {
            Session session = sshService.openSession(server);
            sshService.exec(session, "sudo systemctl " + action + " " + name);
            session.disconnect();
        } catch (Exception e) {
            throw new RuntimeException("systemctl " + action + " failed: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts service details from a single line of systemctl command output.
     *
     * @param line A line containing systemd service information.
     *
     * @return The parsed service details, or null if the line is invalid.
     */
    private ServiceInfo parseServiceLine(String line) {
        if (line.isBlank()) return null;
        String[] parts = line.trim().split("\\s+", 5);
        if (parts.length < 5) return null;

        String unitName = parts[0].replace(".service", "");
        // active/inactive/failed
        String status = parts[2];
        String description = parts[4];
        boolean enabled = true; // simplified - query isEnabled separately if needed.

        return new ServiceInfo(unitName, status, enabled, description);
    }
}