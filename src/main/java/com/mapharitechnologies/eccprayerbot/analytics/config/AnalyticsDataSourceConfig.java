package com.mapharitechnologies.eccprayerbot.analytics.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;

/**
 * Configures the optional Supabase/Postgres analytics data source.
 */
@Configuration
@EnableConfigurationProperties(AnalyticsProperties.class)
@ConditionalOnProperty(prefix = "analytics.supabase", name = "enabled", havingValue = "true")
public class AnalyticsDataSourceConfig {

    @Bean(destroyMethod = "close")
    public DataSource analyticsDataSource(AnalyticsProperties properties) {
        if (!StringUtils.hasText(properties.getJdbcUrl())) {
            throw new IllegalStateException("analytics.supabase.jdbc-url must be configured when analytics is enabled");
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getJdbcUrl());
        config.setUsername(properties.getUsername());
        config.setPassword(properties.getPassword());
        config.setMaximumPoolSize(properties.getMaximumPoolSize());
        config.setMinimumIdle(properties.getMinimumIdle());
        config.setConnectionTimeout(properties.getConnectionTimeoutMs());
        config.setValidationTimeout(properties.getValidationTimeoutMs());
        config.setIdleTimeout(properties.getIdleTimeoutMs());
        config.setMaxLifetime(properties.getMaxLifetimeMs());
        config.setPoolName("supabase-analytics");
        config.addDataSourceProperty("stringtype", "unspecified");
        if (StringUtils.hasText(properties.getSchema())) {
            config.setSchema(properties.getSchema());
        }

        return new HikariDataSource(config);
    }

    @Bean
    public Flyway analyticsFlyway(DataSource analyticsDataSource) {
        Flyway flyway = Flyway.configure()
                .locations("classpath:db/analytics/migration")
                .baselineOnMigrate(true)
                .dataSource(analyticsDataSource)
                .load();
        flyway.migrate();
        return flyway;
    }

    @Bean
    public NamedParameterJdbcTemplate analyticsJdbcTemplate(DataSource analyticsDataSource, Flyway analyticsFlyway) {
        return new NamedParameterJdbcTemplate(analyticsDataSource);
    }

}
