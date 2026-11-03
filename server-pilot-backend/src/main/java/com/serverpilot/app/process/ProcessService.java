package com.serverpilot.app.process;

import com.jcraft.jsch.Session;
import com.serverpilot.app.process.dto.ProcessInfo;
import com.serverpilot.app.server.ServerRepository;
import com.serverpilot.app.server.SshService;
import com.serverpilot.app.server.entity.ServerEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles process related operations on remote Linux servers. Allows viewing
 * running processes and stopping a process through SSH.
 *
 * <p>This class retrieves process details and executes process related commands
 * on the remote server using the methods provided by {@link SshService}.</p>
 *
 * @author karthicknathan
 * @since Nov 01, 2026
 */
@Service
public class ProcessService {

    private final ServerRepository serverRepo;
    private final SshService sshService;

    public ProcessService(ServerRepository serverRepo, SshService sshService) {
        this.serverRepo = serverRepo;
        this.sshService = sshService;
    }

    /**
     * Retrieves all currently running processes from the remote server.
     *
     * @param serverId The Unique ID of the saved remove server.
     *
     * @return A list of process details from the remote server.
     */
    public List<ProcessInfo> list(Long serverId) {
        ServerEntity server = serverRepo.findById(serverId).orElseThrow();
        List<ProcessInfo> processList = new ArrayList<>();
        try {
            Session session = sshService.openSession(server);
            // ps columns: PID, USER, %CPU, RSS(KB), COMM, CMD
            String raw = sshService.exec(session,
                    "ps aux --sort=-%cpu | head -30 | tail -n +2");
            session.disconnect();

            ProcessInfo processInfo = null;
            for (String line : raw.split("\n")) {
                processInfo = parsePsLine(line);
                if (processInfo != null) processList.add(processInfo);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list processes: " + e.getMessage(), e);
        }
        return processList;
    }

    /**
     * Stops a running process on the remote server using its process ID.
     *
     * @param serverId The unique ID of the saved remote server.
     * @param pid      The ID of the process to stop.
     */
    public void kill(Long serverId, int pid) {
        ServerEntity server = serverRepo.findById(serverId).orElseThrow();
        try {
            Session session = sshService.openSession(server);
            sshService.exec(session, "kill -9 " + pid);
            session.disconnect();
        } catch (Exception e) {
            throw new RuntimeException("Failed to kill process: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts process details from a single line of Linux ps command output.
     *
     * @param line A line containing process information.
     *
     * @return The parsed process details, or null if the line is invalid.
     */
    private ProcessInfo parsePsLine(String line) {
        if (line.isBlank()) return null;
        String[] partsArray = line.trim().split("\\s+", 11);
        if (partsArray.length < 11) return null;
        try {
            return new ProcessInfo(
                    Integer.parseInt(partsArray[1]), // PID
                    partsArray[10].split("/")[partsArray[10].split("/").length - 1], // name from cmd
                    partsArray[0], // USER
                    Double.parseDouble(partsArray[2]), // %CPU
                    Math.round(Long.parseLong(partsArray[5]) / 1024.0 / 1024.0 * 100) / 100.0, // RSS KB -> GB
                    partsArray[10] // full command
            );
        } catch (Exception e) {
            return null;
        }
    }
}