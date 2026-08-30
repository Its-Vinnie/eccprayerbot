package com.mapharitechnologies.eccprayerbot.repository;

import com.mapharitechnologies.eccprayerbot.model.ApiKey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for API key management and lookup.
 */
@Repository
public interface ApiKeyRepository extends MongoRepository<ApiKey, String> {

    /** Find an active API key by its key string. */
    Optional<ApiKey> findByKeyAndEnabledTrue(String key);

    /** Find any API key by its key string (including disabled). */
    Optional<ApiKey> findByKey(String key);

    /** List all keys for a given app name. */
    List<ApiKey> findByAppName(String appName);

    /** Count active keys for a given app. */
    long countByAppNameAndEnabledTrue(String appName);
}
