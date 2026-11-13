package com.parceltracker.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.regex.Pattern;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class MigrationVersionsTest {
    @Test
    void packagedMigrationsHaveUniqueVersions() throws Exception {
        var pattern = Pattern.compile("^V(.+)__.+\\.sql$");
        var versions = new HashSet<MigrationVersion>();
        var directory = Path.of(Objects.requireNonNull(
                getClass().getClassLoader().getResource("db/migration")).toURI());
        try (var files = Files.list(directory)) {
            for (Path file : files.toList()) {
                var match = pattern.matcher(file.getFileName().toString());
                if (match.matches()) {
                    var version = MigrationVersion.fromVersion(match.group(1).replace('_', '.'));
                    assertTrue(versions.add(version), "Duplicate Flyway migration version " + version);
                }
            }
        }
    }
}
