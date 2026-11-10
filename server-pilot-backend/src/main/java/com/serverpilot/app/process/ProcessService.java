package com.serverpilot.app.process;

import com.jcraft.jsch.Session;
import com.serverpilot.app.process.dto.ProcessInfo;
import com.serverpilot.app.server.ServerRepository;
import com.serverpilot.app.server.SshService;
import com.serverpilot.app.server.dto.CommandResult;
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
        Session session = null;
        try {
            session = sshService.openSession(server);
            // ps columns: PID, USER, %CPU, RSS(KB), COMM, CMD
            CommandResult result = sshService.exec(session,
                    "ps -eo pid,user,%cpu,rss,comm,args --sort=-%cpu");
            if (result.exitStatus() != 0) {
                String error = result.stderr().isBlank()
                        ? "Process listing failed with exit code " + result.exitStatus()
                        : result.stderr();
                throw new RuntimeException(error);
            }
            String[] lines = result.stdout().split("\n");
            ProcessInfo processInfo;
            for (int i = 1; i < lines.length; i++) {
                processInfo = parsePsLine(lines[i]);
                if (processInfo != null) processList.add(processInfo);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to list processes: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
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
        if (pid <= 0) {
            throw new IllegalArgumentException("Process ID must be positive: " + pid);
        }
        ServerEntity server = serverRepo.findById(serverId).orElseThrow();
        Session session = null;
        try {
            session = sshService.openSession(server);
            CommandResult result = sshService.exec(session, "kill -9 " + pid);
            if (result.exitStatus() != 0) {
                String error = result.stderr().isBlank()
                        ? "Process termination failed with exit code " + result.exitStatus()
                        : result.stderr();
                throw new RuntimeException(error);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to kill process: " + e.getMessage(), e);
        } finally {
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
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
        String[] partsArray = line.trim().split("\\s+", 6);
        if (partsArray.length < 6) return null;
        try {
            return new ProcessInfo(
                    Integer.parseInt(partsArray[0]), // PID
                    partsArray[4],                   // name from COMM
                    partsArray[1],                   // USER
                    Double.parseDouble(partsArray[2]), // %CPU
                    Math.round(Long.parseLong(partsArray[3]) / 1024.0 / 1024.0 * 100) / 100.0, // RSS KB -> GB
                    partsArray[5]                    // full command (args)
            );
        } catch (Exception e) {
            return null;
        }
    }
}