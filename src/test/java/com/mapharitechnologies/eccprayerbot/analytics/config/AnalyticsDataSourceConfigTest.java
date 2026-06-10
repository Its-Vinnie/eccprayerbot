package com.mapharitechnologies.eccprayerbot.analytics.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsDataSourceConfigTest {

    @Test
    void normalizesPostgresqlUrlWithEmbeddedCredentials() {
        AnalyticsDataSourceConfig.NormalizedJdbcUrl normalized =
                AnalyticsDataSourceConfig.normalizeJdbcUrl(
                        "postgresql://postgres:secret%40value@db.example.supabase.co:5432/postgres?sslmode=require"
                );

        assertThat(normalized.jdbcUrl())
                .isEqualTo("jdbc:postgresql://db.example.supabase.co:5432/postgres?sslmode=require");
        assertThat(normalized.username()).isEqualTo("postgres");
        assertThat(normalized.password()).isEqualTo("secret@value");
    }

    @Test
    void normalizesRailwayPostgresUrlWithDefaultPath() {
        AnalyticsDataSourceConfig.NormalizedJdbcUrl normalized =
                AnalyticsDataSourceConfig.normalizeJdbcUrl("postgres://user:pass@host.internal:6543");

        assertThat(normalized.jdbcUrl()).isEqualTo("jdbc:postgresql://host.internal:6543/postgres");
        assertThat(normalized.username()).isEqualTo("user");
        assertThat(normalized.password()).isEqualTo("pass");
    }

    @Test
    void leavesJdbcPostgresqlUrlUnchanged() {
        AnalyticsDataSourceConfig.NormalizedJdbcUrl normalized =
                AnalyticsDataSourceConfig.normalizeJdbcUrl("jdbc:postgresql://host:5432/postgres");

        assertThat(normalized.jdbcUrl()).isEqualTo("jdbc:postgresql://host:5432/postgres");
        assertThat(normalized.username()).isNull();
        assertThat(normalized.password()).isNull();
    }
}
