package org.zalando.baigan.repository;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.baigan.model.Configuration;
import org.zalando.baigan.repository.aws.S3FileLoader;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.s3.S3Client;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A {@link ConfigurationRepository} implementation that loads the configuration from an S3 bucket in regular
 * intervals. It can read KMS-encrypted configuration files.
 */
public class S3ConfigurationRepository implements ConfigurationRepository {

    private static final Logger LOG = LoggerFactory.getLogger(S3ConfigurationRepository.class);

    private final ConfigurationParser configurationParser;
    private final S3FileLoader s3Loader;
    private final Duration refreshInterval;
    private final ScheduledExecutorService executor;
    private volatile Map<String, Configuration<?>> configurationsMap = ImmutableMap.of();

    S3ConfigurationRepository(@Nonnull final String bucketName, @Nonnull final String key,
                              final Duration refreshInterval, final ScheduledExecutorService executor,
                              final S3Client s3Client, final KmsClient kmsClient, ConfigurationParser configurationParser) {
        checkNotNull(bucketName, "bucketName is required");
        checkNotNull(key, "key is required");
        checkArgument(!refreshInterval.isNegative(), "refreshInterval has to be >= 0");
        checkNotNull(executor, "executor is required");
        checkNotNull(s3Client, "s3Client is required");
        checkNotNull(kmsClient, "kmsClient is required");

        this.refreshInterval = refreshInterval;
        this.executor = executor;
        this.s3Loader = new S3FileLoader(bucketName, key, s3Client, kmsClient);
        this.configurationParser = configurationParser;

        loadConfigurations();
        if (!refreshInterval.isZero()) {
            setupRefresh();
        }
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

    private void loadConfigurations() {
        LOG.debug("Loading configurations from S3 bucket {} at key {}", s3Loader.getBucketName(), s3Loader.getKey());
        final String configurationText = s3Loader.loadContent();
        final List<Configuration<?>> configurations = configurationParser.parseConfigurations(configurationText);
        final ImmutableMap.Builder<String, Configuration<?>> builder = ImmutableMap.builder();
        for (final Configuration<?> configuration : configurations) {
            builder.put(configuration.getAlias(), configuration);
        }
        configurationsMap = builder.build();
        LOG.debug("Loaded configurations from S3 bucket {} at key {}", s3Loader.getBucketName(), s3Loader.getKey());
    }

    private void setupRefresh() {
        executor.scheduleAtFixedRate(
                () -> {
                    try {
                        loadConfigurations();
                    } catch (RuntimeException e) {
                        LOG.error("Failed to refresh configuration from S3 bucket {} at key {}. Keeping old state.",
                                s3Loader.getBucketName(), s3Loader.getKey(), e);
                    }
                },
                this.refreshInterval.toMillis(),
                this.refreshInterval.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }
}
