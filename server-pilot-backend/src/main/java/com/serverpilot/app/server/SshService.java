package com.serverpilot.app.server;

import com.jcraft.jsch.*;
import com.serverpilot.app.server.entity.ServerEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
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

        Session session = jsch.getSession(
                serverEntity.getUsername(),
                serverEntity.getHost(),
                serverEntity.getPort()
        );

        if ("PASSWORD".equalsIgnoreCase(serverEntity.getAuthType())) {
            session.setPassword(serverEntity.getPassword());
        }

        // Disable host-key checking (add known_hosts verification in production).
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(10_000); // 10-second timeout.
        return session;
    }

    /**
     * Executes a command on an already authenticated SSH session.
     *
     * @param session Active SSH session.
     * @param command Command to execute on the remote server.
     *
     * @return Executed Command output.
     *
     * @throws JSchException If the SSH channel cannot be opened or connected.
     * @throws IOException   If the command output cannot be read.
     */
    public String exec(Session session, String command) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);

        InputStream stdout = channel.getInputStream();
        channel.connect();

        String output = new String(stdout.readAllBytes(), StandardCharsets.UTF_8);
        channel.disconnect();
        return output.trim();
    }
}