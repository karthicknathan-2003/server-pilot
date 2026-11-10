package com.serverpilot.app.server;

import com.jcraft.jsch.*;
import com.serverpilot.app.server.dto.CommandResult;
import com.serverpilot.app.server.entity.ServerEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Provides low-level SSH operations for connecting to remote servers
 * and executing commands or opening interactive shell sessions.
 *
 * @author karthicknathan
 * @since 12 Sep, 2026
 */
@Service
public class SshService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_COMMAND_TIMEOUT_MS = 30_000;

    /**
     * Opens an authenticated SSH session to the specified serverEntity.
     *
     * @param serverEntity Entity holds the connection and authentication details.
     *
     * @return An authenticated SSH session.
     *
     * @throws JSchException If the SSH session cannot be created or connected.
     */
    public Session openSession(ServerEntity serverEntity) throws JSchException {
        JSch jsch = new JSch();
        if ("KEY".equalsIgnoreCase(serverEntity.getAuthType())) {
            // Load the stored private key string.
            byte[] keyBytes = serverEntity.getPrivateKey().getBytes(StandardCharsets.UTF_8);
            jsch.addIdentity("key", keyBytes, null, null);
        }

        Session session = jsch.getSession(serverEntity.getUsername(), serverEntity.getHost(), serverEntity.getPort());

        if ("PASSWORD".equalsIgnoreCase(serverEntity.getAuthType())) {
            session.setPassword(serverEntity.getPassword());
        }

        // Disable host-key checking (add known_hosts verification in production).
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(CONNECT_TIMEOUT_MS); // 10-second timeout.
        return session;
    }

    /**
     * Executes a command on an already authenticated SSH session with a default deadline.
     *
     * @param session Active SSH session.
     * @param command Command to execute on the remote server.
     *
     * @return A Record containing exit status, stdout, and stderr.
     *
     * @throws JSchException If the SSH channel cannot be opened or connected.
     * @throws IOException   If execution fails, times out, or output cannot be read.
     */
    public CommandResult exec(Session session, String command) throws JSchException, IOException {
        return exec(session, command, DEFAULT_COMMAND_TIMEOUT_MS);
    }

    /**
     * Executes a command on an already authenticated SSH session with a given deadline.
     *
     * @param session Active SSH session.
     * @param command Command to execute on the remote server.
     *
     * @return A Record containing exit status, stdout, and stderr.
     *
     * @throws JSchException If the SSH channel cannot be opened or connected.
     * @throws IOException   If the command output cannot be read.
     */
    public CommandResult exec(Session session, String command, int timeoutMs) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        // Wiring up the buffers to capture the stdout and stderr.
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        channel.setOutputStream(stdout);
        channel.setErrStream(stderr);
        // Start the channel.
        channel.connect(CONNECT_TIMEOUT_MS);
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            // Poll every 50ms until the remote side, signals that it's done.
            // isClosed() becomes true once the command exits and the channel is fully flushed.
            while (!channel.isClosed()) {
                // If the connection crosses the deadline, don't wait any longer,
                // kill the channel and the whole session.
                if (System.currentTimeMillis() > deadline) {
                    channel.disconnect();
                    session.disconnect();
                    throw new IOException("SSH command execution timed out after " + timeoutMs + "ms: " + command);
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.disconnect();
            session.disconnect();
            throw new IOException("SSH command execution interrupted: " + command, e);
        }
        int exitStatus = channel.getExitStatus();
        channel.disconnect();
        // Decode the captured raw bytes into proper strings.
        String stdoutOutput = stdout.toString(StandardCharsets.UTF_8).trim();
        String stderrOutput = stderr.toString(StandardCharsets.UTF_8).trim();
        return new CommandResult(exitStatus, stdoutOutput, stderrOutput);
    }
}