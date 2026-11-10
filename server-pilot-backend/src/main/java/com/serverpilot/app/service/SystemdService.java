package com.serverpilot.app.service;

import com.jcraft.jsch.Session;
import com.serverpilot.app.server.ServerRepository;
import com.serverpilot.app.server.SshService;
import com.serverpilot.app.server.dto.CommandResult;
import com.serverpilot.app.server.entity.ServerEntity;
import com.serverpilot.app.service.dto.ServiceInfo;
import org.springframework.stereotype.Service;

import java.util.*;

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
        Session session = null;
        try {
            session = sshService.openSession(server);
            // Installed unit files to obtain enablement states and discover unloaded services.
            CommandResult unitFilesResult = sshService.exec(session,
                    "systemctl list-unit-files --type=service --no-pager --no-legend");
            if (unitFilesResult.exitStatus() != 0) {
                String error = unitFilesResult.stderr().isBlank()
                        ? "Command exited with status " + unitFilesResult.exitStatus()
                        : unitFilesResult.stderr();
                throw new RuntimeException("Failed to list unit files: " + error);
            }
            String[] parts;
            String unitName;
            Map<String, Boolean> enabledMap = new HashMap<>();
            for (String line : unitFilesResult.stdout().split("\n")) {
                if (line.isBlank()) continue;
                parts = line.trim().split("\\s+");
                if (parts.length >= 2 && parts[0].endsWith(".service")) {
                    unitName = parts[0].replace(".service", "");
                    enabledMap.put(unitName, "enabled".equalsIgnoreCase(parts[1]));
                }
            }
            // Loaded units (including inactive and failed units) in plain format.
            CommandResult unitsResult = sshService.exec(session,
                    "systemctl list-units --type=service --all --no-pager --no-legend --plain");
            if (unitsResult.exitStatus() != 0) {
                String error = unitsResult.stderr().isBlank()
                        ? "Command exited with status " + unitsResult.exitStatus()
                        : unitsResult.stderr();
                throw new RuntimeException("Failed to list units: " + error);
            }
            Map<String, ServiceInfo> servicesMap = new LinkedHashMap<>();
            ServiceInfo serviceInfo;
            for (String line : unitsResult.stdout().split("\n")) {
                serviceInfo = parseServiceLine(line, enabledMap);
                if (serviceInfo != null) {
                    servicesMap.put(serviceInfo.name(), serviceInfo);
                }
            }
            // Merge installed unit files that are not currently loaded (unloaded services).
            String name;
            for (Map.Entry<String, Boolean> entry : enabledMap.entrySet()) {
                name = entry.getKey();
                if (!name.endsWith("@") && !servicesMap.containsKey(name)) {
                    servicesMap.put(name, new ServiceInfo(name, "inactive", entry.getValue(), ""));
                }
            }
            return new ArrayList<>(servicesMap.values());
        } catch (Exception e) {
            throw new RuntimeException("Failed to list services: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
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
        if (name == null || !name.matches("[A-Za-z0-9:_.@][A-Za-z0-9:_.@-]*")) {
            throw new IllegalArgumentException("Invalid service name: " + name);
        }
        ServerEntity server = serverRepo.findById(serverId).orElseThrow();
        Session session = null;
        try {
            session = sshService.openSession(server);
            CommandResult result = sshService.exec(session, "sudo systemctl " + action + " " + name + ".service");
            if (result.exitStatus() != 0) {
                String error = result.stderr().isBlank()
                        ? "Command exited with status " + result.exitStatus()
                        : result.stderr();
                throw new RuntimeException(error);
            }
        } catch (Exception e) {
            throw new RuntimeException("systemctl " + action + " failed: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * Extracts service details from a single line of systemctl list-units output.
     * Handles stripped markers defensively and looks up true enablement state.
     *
     * @param line       A line containing systemd service information.
     * @param enabledMap Map of unit names to their enablement status.
     *
     * @return The parsed service details, or null if the line is invalid.
     */
    private ServiceInfo parseServiceLine(String line, Map<String, Boolean> enabledMap) {
        if (line.isBlank()) return null;
        // Strip optional bullet/marker (e.g. ● or *) if present in output.
        String cleanLine = line.replaceFirst("^[●*]\\s*", "").trim();
        String[] parts = cleanLine.split("\\s+", 5);
        if (parts.length < 5 || !parts[0].endsWith(".service")) return null;
        String unitName = parts[0].replace(".service", "");
        // active/inactive/failed
        String status = parts[2];
        String description = parts[4];
        boolean enabled = enabledMap.getOrDefault(unitName, false);
        return new ServiceInfo(unitName, status, enabled, description);
    }
}