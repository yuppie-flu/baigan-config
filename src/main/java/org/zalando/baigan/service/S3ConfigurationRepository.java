package org.zalando.baigan.service;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.baigan.model.Configuration;
import org.zalando.baigan.service.aws.S3FileLoader;

public class S3ConfigurationRepository extends AbstractConfigurationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(S3ConfigurationRepository.class);

    /**
     * The default refresh interval which is 60 seconds
     */
    private static final long DEFAULT_REFRESH_INTERVAL = 60;

    private final S3FileLoader s3Loader;
    private long refreshInterval = DEFAULT_REFRESH_INTERVAL;
    private final ScheduledThreadPoolExecutor executor;
    private volatile Map<String, Configuration> configurationsMap = ImmutableMap.of();

    /**
     * Provides a {@link ConfigurationRepository} that reads configurations from a JSON file stored in a S3 bucket.
     * It refreshes configurations using the default refresh interval (60 seconds)
     *
     * @param bucketName The name of the bucket
     * @param key        The object key, usually, the "full path" to the JSON file stored in the bucket
     */
    public S3ConfigurationRepository(@Nonnull final String bucketName, @Nonnull final String key) {
        this(bucketName, key, DEFAULT_REFRESH_INTERVAL);
    }

    /**
     * Provides a {@link ConfigurationRepository} that reads configurations from a JSON file stored in a S3 bucket.
     *
     * @param bucketName      The name of the bucket
     * @param key             The object key, usually, the "full path" to the JSON file stored in the bucket
     * @param refreshInterval The interval, in seconds, to refresh the configurations. A value of 0 disables refreshing
     *                        <p>
     * @see #S3ConfigurationRepository(String, String)
     */
    public S3ConfigurationRepository(@Nonnull final String bucketName, @Nonnull final String key, final long refreshInterval) {
        this(bucketName, key, refreshInterval, new ScheduledThreadPoolExecutor(1));
    }

    /**
     * Provides a {@link ConfigurationRepository} that reads configurations from a JSON file stored in a S3 bucket.
     *
     * @param bucketName      The name of the bucket
     * @param key             The object key, usually, the "full path" to the JSON file stored in the bucket
     * @param refreshInterval The interval, in seconds, to refresh the configurations. A value of 0 disables refreshing
     * @param executor        The executor to refresh the configurations in the specified interval
     *                        <p>
     * @see #S3ConfigurationRepository(String, String)
     */
    public S3ConfigurationRepository(@Nonnull final String bucketName, @Nonnull final String key,
          final long refreshInterval, final ScheduledThreadPoolExecutor executor) {
        checkNotNull(bucketName, "bucketName is required");
        checkNotNull(key, "key is required");
        checkArgument(refreshInterval >= 0, "refreshInterval has to be >= 0");

        this.refreshInterval = refreshInterval;
        this.executor =  executor;
        this.s3Loader = new S3FileLoader(bucketName, key);

        loadConfigurations();
        if (refreshInterval > 0) {
            setupRefresh();
        }
    }

    protected void loadConfigurations() {
        final String configurationText = s3Loader.loadContent();
        final List<Configuration> configurations = getConfigurations(configurationText);
        final ImmutableMap.Builder<String, Configuration> builder = ImmutableMap.builder();
        for (final Configuration configuration : configurations) {
            builder.put(configuration.getAlias(), configuration);
        }
        configurationsMap = builder.build();
    }

    private void setupRefresh() {
        executor.scheduleAtFixedRate(() -> {
                  try {
                      loadConfigurations();
                  } catch (RuntimeException e) {
                      LOG.warn("Failed to refresh S3 configuration", e);
                  }
              }, this.refreshInterval, this.refreshInterval,
              TimeUnit.SECONDS);
    }

    @Nonnull
    @Override
    public Optional<Configuration> get(@Nonnull String key) {
        return Optional.ofNullable(configurationsMap.get(key));
    }

    @Override
    public void put(@Nonnull String key, @Nonnull String value) {
        throw new UnsupportedOperationException("The S3ConfigurationRepository doesn't allow any changes.");
    }
}
