package com.serverpilot.app.server;

import com.jcraft.jsch.Session;
import com.serverpilot.app.server.dto.ServerRequestDto;
import com.serverpilot.app.server.dto.ServerResponseDto;
import com.serverpilot.app.server.dto.SystemMetricsDto;
import com.serverpilot.app.server.entity.ServerEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides server management operations, including CRUD operations,
 * SSH connectivity checks, and live system metric retrieval.
 *
 * @author karthicknathan
 * @since 12 Sep, 2026
 */
@Service
public class ServerService {

    private final ServerRepository serverRepo;
    private final SshService sshService;

    public ServerService(ServerRepository serverRepo, SshService sshService) {
        this.serverRepo = serverRepo;
        this.sshService = sshService;
    }

    /**
     * Retrieves all configured servers with their current connection status.
     *
     * @return list of configured servers with online or offline status.
     */
    public List<ServerResponseDto> listAll() {
        return serverRepo.findAll().stream().map(server ->
                new ServerResponseDto(server.getId(), server.getName(), server.getHost(),
                        server.getPort(), server.getUsername(), server.getAuthType(),
                        testConnectivity(server) ? "online" : "offline")).toList();
    }

    /**
     * Saves a new server using the provided connection details.
     *
     * @param requestDto Dto contains connection details.
     *
     * @return Saved server details with connection status.
     */
    public ServerResponseDto save(ServerRequestDto requestDto) {
        ServerEntity s = new ServerEntity();
        s.setName(requestDto.name());
        s.setHost(requestDto.host());
        s.setPort(requestDto.port());
        s.setUsername(requestDto.username());
        s.setAuthType(requestDto.authType());
        s.setPassword(requestDto.password());
        s.setPrivateKey(requestDto.privateKey());
        ServerEntity saved = serverRepo.save(s);
        return toResponse(saved, testConnectivity(saved));
    }

    /**
     * Deletes a configured server by its ID.
     *
     * @param id ID of the server to delete.
     */
    public void delete(Long id) {
        serverRepo.deleteById(id);
    }

    /**
     * Retrieves live system metrics from a remote server.
     *
     * <p>Run the shell commands for collecting CPU, memory, disk, network, uptime,
     * and operating system information.</p>
     *
     * @param serverId ID of the server from which to retrieve metrics.
     *
     * @return Current system metrics of the remote server.
     *
     * @throws RuntimeException If the server is not found or metrics cannot be retrieved.
     */
    public SystemMetricsDto getMetrics(Long serverId) {
        ServerEntity serverEntity = serverRepo.findById(serverId).orElseThrow(() ->
                new RuntimeException("ServerEntity not found: " + serverId));
        try {
            Session session = sshService.openSession(serverEntity);
            // Run all metrics in one SSH exec to minimize the round-trips.
            String raw = sshService.exec(session, """
                    echo "HOSTNAME=$(hostname)"
                    echo "OS=$(grep PRETTY_NAME /etc/os-release | cut -d= -f2 | tr -d '"')"
                    echo "KERNEL=$(uname -r)"
                    echo "ARCH=$(uname -m)"
                    echo "UPTIME=$(uptime -p)"
                    echo "CPU=$(top -bn1 | grep 'Cpu(s)' | awk '{print $2}' | cut -d'%' -f1)"
                    echo "RAM_USED=$(free -m | awk '/Mem/{print $3}')"
                    echo "RAM_TOTAL=$(free -m | awk '/Mem/{print $2}')"
                    echo "DISK_USED=$(df -BG / | awk 'NR==2{gsub(/G/,"",$3); print $3}')"
                    echo "DISK_TOTAL=$(df -BG / | awk 'NR==2{gsub(/G/,"",$2); print $2}')"
                    echo "NET_RX=$(cat /sys/class/net/eth0/statistics/rx_bytes 2>/dev/null || echo 0)"
                    echo "NET_TX=$(cat /sys/class/net/eth0/statistics/tx_bytes 2>/dev/null || echo 0)"
                    """);
            session.disconnect();
            return parseMetrics(raw);
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch metrics: " + e.getMessage(), e);
        }
    }

    /**
     * Parses the key value output returned by the remote metric commands.
     *
     * @param raw Raw command output containing KEY=VALUE pairs.
     *
     * @return DTO contains parsed system metrics.
     */
    private SystemMetricsDto parseMetrics(String raw) {
        Map<String, String> systemMetricsMap = new HashMap<>();
        for (String line : raw.split("\n")) {
            int eqIndex = line.indexOf('=');
            if (eqIndex > 0) {
                systemMetricsMap.put(line.substring(0, eqIndex).trim(),
                        line.substring(eqIndex + 1).trim());
            }
        }
        return new SystemMetricsDto(systemMetricsMap.getOrDefault("HOSTNAME", "unknown"),
                systemMetricsMap.getOrDefault("OS", "unknown"),
                systemMetricsMap.getOrDefault("KERNEL", "unknown"),
                systemMetricsMap.getOrDefault("ARCH", "unknown"),
                systemMetricsMap.getOrDefault("UPTIME", "unknown"),
                parseDouble(systemMetricsMap.get("CPU")), parseMbToGb(systemMetricsMap.get("RAM_USED")),
                parseMbToGb(systemMetricsMap.get("RAM_TOTAL")),
                parseDouble(systemMetricsMap.get("DISK_USED")),
                parseDouble(systemMetricsMap.get("DISK_TOTAL")),
                formatBytes(parseLong(systemMetricsMap.get("NET_RX"))) + "/s",
                formatBytes(parseLong(systemMetricsMap.get("NET_TX"))) + "/s");
    }

    /**
     * Tests whether an SSH connection can be established with the serverEntity.
     *
     * @param serverEntity Entity holds the server details.
     *
     * @return {@code true} if the SSH connection succeeds, {@code false} otherwise.
     */
    private boolean testConnectivity(ServerEntity serverEntity) {
        try {
            Session session = sshService.openSession(serverEntity);
            session.disconnect();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Converts a serverEntity entity into a response object.
     *
     * @param serverEntity Entity holds the server details.
     * @param isOnline     Flay indicates whether the remote connection has been
     *                     established successfully or not.
     *
     * @return DTO contains server details.
     */
    private ServerResponseDto toResponse(ServerEntity serverEntity, boolean isOnline) {
        return new ServerResponseDto(serverEntity.getId(), serverEntity.getName(),
                serverEntity.getHost(), serverEntity.getPort(), serverEntity.getUsername(),
                serverEntity.getAuthType(), isOnline ? "isOnline" : "offline");
    }

    /**
     * Safely parses a string value into a double.
     *
     * @param value Value to be parsed.
     *
     * @return Parsed double, or {@code 0} if the value is invalid.
     */
    private double parseDouble(String value) {
        try {
            return value == null ? 0 : Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Safely parses a string value into a long.
     *
     * @param value Value to be parsed.
     *
     * @return Parsed long, or {@code 0} if the value is invalid.
     */
    private long parseLong(String value) {
        try {
            return value == null ? 0 : Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Converts memory value from megabytes to gigabytes and rounds the result.
     *
     * @param mb Memory value in megabytes.
     *
     * @return Converted memory value in gigabytes.
     */
    private double parseMbToGb(String mb) {
        return Math.round(parseDouble(mb) / 1024.0 * 10.0) / 10.0;
    }

    /**
     * Converts a byte value into a readable network size.
     *
     * @param bytes Size in bytes.
     *
     * @return Formatted size using B, KB, or MB units
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
