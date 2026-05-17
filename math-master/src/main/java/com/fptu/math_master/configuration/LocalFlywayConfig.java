package com.fptu.math_master.configuration;

import java.sql.Connection;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Local dev: skip Flyway on an empty database so Hibernate {@code ddl-auto: update} can create
 * the base schema first. On the next start (or after manager pre-migrate), Flyway applies
 * {@code db/migration} scripts.
 */
@Configuration
@Profile("local")
@Slf4j
public class LocalFlywayConfig {

  private final DataSource dataSource;

  public LocalFlywayConfig(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Bean
  public FlywayMigrationStrategy localFlywayMigrationStrategy() {
    return flyway -> {
      if (!hasApplicationTables()) {
        log.info(
            "Local Flyway: empty database — skipping migration; Hibernate will create base"
                + " tables. Run menu [4] again to apply Flyway scripts.");
        return;
      }
      log.info("Local Flyway: applying pending migrations from db/migration...");
      flyway.migrate();
    };
  }

  private boolean hasApplicationTables() {
    String sql =
        """
        SELECT COUNT(*)
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_type = 'BASE TABLE'
          AND table_name <> 'flyway_schema_history'
        """;
    try (Connection conn = dataSource.getConnection();
        var stmt = conn.createStatement();
        var rs = stmt.executeQuery(sql)) {
      if (rs.next()) {
        return rs.getInt(1) > 0;
      }
    } catch (Exception e) {
      log.warn("Local Flyway: could not inspect schema, running migrate anyway", e);
      return true;
    }
    return false;
  }
}
