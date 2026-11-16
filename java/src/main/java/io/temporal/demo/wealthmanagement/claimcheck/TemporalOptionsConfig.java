package io.temporal.demo.wealthmanagement.claimcheck;

import io.temporal.client.WorkflowClientOptions;
import io.temporal.common.converter.CodecDataConverter;
import io.temporal.common.converter.DataConverter;
import io.temporal.common.converter.DefaultDataConverter;
import io.temporal.demo.wealthmanagement.common.ServerInfo;
import io.temporal.demo.wealthmanagement.common.TemporalClient;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Nonnull;
import java.util.Collections;

/**
 * With USE_CLAIM_CHECK=true, the worker's client (configured by Spring) and the status
 * client (common/TemporalClient) both use the claim check codec. Without it, a signal sent
 * by one would carry a raw payload while the other expects a claim check, or the reverse.
 */
@Configuration
public class TemporalOptionsConfig {
    private static final Logger logger = LoggerFactory.getLogger(TemporalOptionsConfig.class);

    public static DataConverter claimCheckDataConverter(ClaimCheckCodec codec) {
        return new CodecDataConverter(DefaultDataConverter.newDefaultInstance(), Collections.singletonList(codec));
    }

    @Bean
    public TemporalOptionsCustomizer<WorkflowClientOptions.Builder> customClientOptions(ClaimCheckCodec codec) {
        return new TemporalOptionsCustomizer<>() {
            @Nonnull
            @Override
            public WorkflowClientOptions.Builder customize(@Nonnull WorkflowClientOptions.Builder optionsBuilder) {
                if (ServerInfo.useClaimCheck()) {
                    logger.info("Using the claim check codec");
                    DataConverter converter = claimCheckDataConverter(codec);
                    optionsBuilder.setDataConverter(converter);
                    TemporalClient.setDataConverter(converter);
                }
                return optionsBuilder;
            }
        };
    }
}
