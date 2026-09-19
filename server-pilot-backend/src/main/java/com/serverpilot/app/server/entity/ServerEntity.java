package com.serverpilot.app.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * An Entity represents a remote server that can be connected and managed by the application.
 *
 * <p>Each server stores the connection details required to establish an SSH connection,
 * including the host, port, authentication type, username, and private key when applicable.</p>
 *
 * @author karthicknathan
 * @since 12 Sep, 2026
 */
@Entity
@Table(name = "servers")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class ServerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column(nullable = false)
    private String username;

    /** "PASSWORD" or "KEY" */
    @Column(nullable = false)
    private String authType;

    /** Stored only when authType = PASSWORD */
    @Column
    private String password;

    /** Private key text */
    @Column(columnDefinition = "TEXT")
    private String privateKey;
}
