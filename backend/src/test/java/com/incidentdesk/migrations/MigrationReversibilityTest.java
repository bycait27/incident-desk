package com.incidentdesk.migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

class MigrationReversibilityTest {

    private static final String INDEX_NAME = "idx_audit_logs_resource_history";
    private static final String DEFAULT_URL = "jdbc:postgresql://localhost:5432/incident_desk";

    @Test
    void auditHistoryIndexMigrationCanBeAppliedRevertedAndReapplied() throws Exception {
        String schema = "migration_test_" + UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = openConnection()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA \"" + schema + "\"");
                statement.execute("SET search_path TO \"" + schema + "\"");
            }

            try {
                flyway(schema, null).migrate();
                assertThat(latestAppliedVersion(connection)).isEqualTo("5");
                assertThat(indexExists(connection, schema)).isTrue();
                long incidentCount = countRows(connection, "incidents");
                long auditLogCount = countRows(connection, "audit_logs");

                executeRollback(connection);
                assertThat(indexExists(connection, schema)).isFalse();
                assertThat(countRows(connection, "incidents")).isEqualTo(incidentCount);
                assertThat(countRows(connection, "audit_logs")).isEqualTo(auditLogCount);

                flyway(schema, "4").migrate();
                assertThat(latestAppliedVersion(connection)).isEqualTo("4");

                flyway(schema, null).migrate();
                assertThat(latestAppliedVersion(connection)).isEqualTo("5");
                assertThat(indexExists(connection, schema)).isTrue();
                assertThat(countRows(connection, "incidents")).isEqualTo(incidentCount);
                assertThat(countRows(connection, "audit_logs")).isEqualTo(auditLogCount);
            } finally {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA IF EXISTS \"" + schema + "\" CASCADE");
                }
            }
        }
    }

    private static Flyway flyway(String schema, String targetVersion) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(
                        System.getenv().getOrDefault("SPRING_DATASOURCE_URL", DEFAULT_URL),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres"),
                        System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "postgres"))
                .schemas(schema)
                .defaultSchema(schema)
                .locations("classpath:db/migration");
        if (targetVersion != null) {
            configuration.target(MigrationVersion.fromVersion(targetVersion));
        }
        return configuration.load();
    }

    private static Connection openConnection() throws SQLException {
        return DriverManager.getConnection(
                System.getenv().getOrDefault("SPRING_DATASOURCE_URL", DEFAULT_URL),
                System.getenv().getOrDefault("SPRING_DATASOURCE_USERNAME", "postgres"),
                System.getenv().getOrDefault("SPRING_DATASOURCE_PASSWORD", "postgres"));
    }

    private static String latestAppliedVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery(
                     "SELECT version FROM flyway_schema_history WHERE success = TRUE "
                             + "ORDER BY installed_rank DESC LIMIT 1")) {
            assertThat(results.next()).isTrue();
            return results.getString(1);
        }
    }

    private static boolean indexExists(Connection connection, String schema) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = ? AND indexname = ?)")) {
            statement.setString(1, schema);
            statement.setString(2, INDEX_NAME);
            try (ResultSet results = statement.executeQuery()) {
                assertThat(results.next()).isTrue();
                return results.getBoolean(1);
            }
        }
    }

    private static long countRows(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet results = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertThat(results.next()).isTrue();
            return results.getLong(1);
        }
    }

    private static void executeRollback(Connection connection) throws IOException, SQLException {
        String rollbackSql;
        try (InputStream input = MigrationReversibilityTest.class.getResourceAsStream(
                "/db/rollback/R5__audit_log_resource_history_index.sql")) {
            if (input == null) {
                throw new IOException("The V5 rollback script is missing.");
            }
            rollbackSql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        try (Statement statement = connection.createStatement()) {
            for (String sql : rollbackSql.split(";")) {
                if (!sql.isBlank()) {
                    statement.execute(sql);
                }
            }
        }
    }
}
