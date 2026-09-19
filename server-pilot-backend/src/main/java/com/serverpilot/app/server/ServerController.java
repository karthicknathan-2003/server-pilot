package com.serverpilot.app.server;

import com.jcraft.jsch.Session;
import com.serverpilot.app.server.dto.ServerRequestDto;
import com.serverpilot.app.server.dto.ServerResponseDto;
import com.serverpilot.app.server.dto.SystemMetricsDto;
import com.serverpilot.app.server.entity.ServerEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for managing remote server connections and server operations.
 *
 * <p>It also provides an SSH connectivity test to verify the provided server
 * credentials before saving the server details.</p>
 *
 * @author karthicknathan
 * @since 12 Sep, 2026
 */
@RestController
@RequestMapping("/api/servers")
public class ServerController {

    private final ServerService serverService;
    private final SshService sshService;

    public ServerController(ServerService serverService, SshService sshService) {
        this.serverService = serverService;
        this.sshService = sshService;
    }

    /**
     * Retrieves all configured servers.
     *
     * @return List of configured servers.
     */
    @GetMapping
    public List<ServerResponseDto> list() {
        return serverService.listAll();
    }

    /**
     * Adds a new server using the provided connection details.
     *
     * @param requestDto Dto contains connection details.
     *
     * @return The saved server details.
     */
    @PostMapping
    public ResponseEntity<ServerResponseDto> add(@RequestBody ServerRequestDto requestDto) {
        return ResponseEntity.ok(serverService.save(requestDto));
    }

    /**
     * Deletes a configured server by its ID.
     *
     * @param id ID of the server to delete.
     *
     * @return Empty response with HTTP 204 status.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        serverService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Retrieves the current system metrics of a configured server.
     *
     * @param id ID of the server.
     *
     * @return Current system metrics.
     */
    @GetMapping("/{id}/metrics")
    public ResponseEntity<SystemMetricsDto> metrics(@PathVariable Long id) {
        return ResponseEntity.ok(serverService.getMetrics(id));
    }

    /**
     * Tests SSH connectivity using the provided server credentials.
     *
     * <p>Establish an SSH connection. The connection will be closed
     * immediately after the test completes.</p>
     *
     * @param requestDto Dto contains connection details to test.
     *
     * @return Response contains connection status.
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect(@RequestBody ServerRequestDto requestDto) {
        // Build a temporary ServerEntity object just for the connection test.
        // TODO: On next visit, need to remove the usage of entity other than persisting.
        ServerEntity tempServerEntity = new ServerEntity();
        tempServerEntity.setHost(requestDto.host());
        tempServerEntity.setPort(requestDto.port());
        tempServerEntity.setUsername(requestDto.username());
        tempServerEntity.setAuthType(requestDto.authType());
        tempServerEntity.setPassword(requestDto.password());
        tempServerEntity.setPrivateKey(requestDto.privateKey());

        try {
            Session session = sshService.openSession(tempServerEntity);
            session.disconnect();
            return ResponseEntity.ok(Map.of("connected", true, "message", "SSH connection successful"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("connected", false, "message", e.getMessage()));
        }
    }
}
