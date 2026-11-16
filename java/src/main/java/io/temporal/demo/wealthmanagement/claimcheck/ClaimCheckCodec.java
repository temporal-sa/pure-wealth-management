package io.temporal.demo.wealthmanagement.claimcheck;

import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.converter.DataConverterException;
import io.temporal.common.converter.EncodingKeys;
import io.temporal.payload.codec.PayloadCodec;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Claim check codec: each payload is stored in Redis under a new UUID, and only the UUID is
 * sent to Temporal. The format matches the Python codec (src/common/claim_check.py), so the
 * two workers can read each other's payloads: metadata encoding=claim-checked and
 * temporal.io/claim-check-codec=v1, data=the UUID (the Redis key), and the Redis value is the
 * original payload serialized as protobuf.
 */
@Component
public class ClaimCheckCodec implements PayloadCodec {
    static final ByteString ENCODING = ByteString.copyFromUtf8("claim-checked");
    static final String CODEC_KEY = "temporal.io/claim-check-codec";
    static final ByteString CODEC_VERSION = ByteString.copyFromUtf8("v1");

    private final RedisTemplate<String, Payload> redisTemplate;

    public ClaimCheckCodec(RedisTemplate<String, Payload> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Nonnull
    @Override
    public List<Payload> encode(@Nonnull List<Payload> payloads) {
        return payloads.stream().map(this::encodePayload).collect(Collectors.toList());
    }

    @Nonnull
    @Override
    public List<Payload> decode(@Nonnull List<Payload> payloads) {
        return payloads.stream().map(this::decodePayload).collect(Collectors.toList());
    }

    private Payload encodePayload(Payload payload) {
        String key = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(key, payload);
        return Payload.newBuilder()
                .putMetadata(EncodingKeys.METADATA_ENCODING_KEY, ENCODING)
                .putMetadata(CODEC_KEY, CODEC_VERSION)
                .setData(ByteString.copyFromUtf8(key))
                .build();
    }

    private Payload decodePayload(Payload source) {
        // Payloads that weren't claim-checked pass through unchanged
        if (!CODEC_VERSION.equals(source.getMetadataOrDefault(CODEC_KEY, null))) {
            return source;
        }
        String key = source.getData().toStringUtf8();
        Payload original;
        try {
            original = redisTemplate.opsForValue().get(key);
        } catch (Exception ex) {
            throw new DataConverterException(ex);
        }
        if (original == null) {
            throw new DataConverterException(
                    "Claim check " + key + " not found in Redis: the payload can't be decoded", (Throwable) null);
        }
        return original;
    }
}
