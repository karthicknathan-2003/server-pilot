package com.serverpilot.app.server;

import com.serverpilot.app.server.entity.ServerEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides database operations for {@link ServerEntity}.
 *
 * <p>Spring Data generates the SQL for all methods at startup. No implementation class needed.</p>
 *
 * @author karthicknathan
 * @since 12 Sep, 2026
 */
public interface ServerRepository extends JpaRepository<ServerEntity, Long> {}
