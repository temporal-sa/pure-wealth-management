package io.temporal.demo.wealthmanagement.claimcheck;

import com.google.protobuf.ByteString;
import io.temporal.api.common.v1.Payload;
import io.temporal.common.converter.DataConverterException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** The codec with Redis replaced by a map, so no Redis is needed. */
class ClaimCheckCodecTest {

    private final Map<String, Payload> redis = new HashMap<>();
    private ClaimCheckCodec codec;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RedisTemplate<String, Payload> template = mock(RedisTemplate.class);
        ValueOperations<String, Payload> ops = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(ops);
        doAnswer(inv -> redis.put(inv.getArgument(0), inv.getArgument(1))).when(ops).set(anyString(), any(Payload.class));
        when(ops.get(any())).thenAnswer(inv -> redis.get((String) inv.getArgument(0)));
        codec = new ClaimCheckCodec(template);
    }

    private static Payload payload(String json) {
        return Payload.newBuilder()
                .putMetadata("encoding", ByteString.copyFromUtf8("json/plain"))
                .setData(ByteString.copyFromUtf8(json))
                .build();
    }

    @Test
    void encodeStoresThePayloadAndSendsOnlyItsKey() {
        Payload original = payload("{\"client_id\": \"123\"}");

        Payload encoded = codec.encode(List.of(original)).get(0);

        assertEquals("claim-checked", encoded.getMetadataOrThrow("encoding").toStringUtf8());
        assertEquals("v1", encoded.getMetadataOrThrow("temporal.io/claim-check-codec").toStringUtf8());
        String key = encoded.getData().toStringUtf8();
        UUID.fromString(key);
        assertEquals(original, redis.get(key));
        assertEquals(List.of(original), codec.decode(List.of(encoded)));
    }

    @Test
    void decodesWhatThePythonCodecWrites() {
        // The Python codec (src/common/claim_check.py) writes exactly these metadata keys.
        Payload original = payload("{\"account_name\": \"Vacation\"}");
        redis.put("python-key", original);
        Payload fromPython = Payload.newBuilder()
                .putMetadata("encoding", ByteString.copyFromUtf8("claim-checked"))
                .putMetadata("temporal.io/claim-check-codec", ByteString.copyFromUtf8("v1"))
                .setData(ByteString.copyFromUtf8("python-key"))
                .build();

        assertEquals(List.of(original), codec.decode(List.of(fromPython)));
    }

    @Test
    void payloadsThatWereNotClaimCheckedPassThrough() {
        Payload plain = payload("\"hello\"");
        assertEquals(List.of(plain), codec.decode(List.of(plain)));
    }

    @Test
    void aMissingClaimCheckFailsClearly() {
        Payload missing = Payload.newBuilder()
                .putMetadata("encoding", ByteString.copyFromUtf8("claim-checked"))
                .putMetadata("temporal.io/claim-check-codec", ByteString.copyFromUtf8("v1"))
                .setData(ByteString.copyFromUtf8("no-such-key"))
                .build();
        DataConverterException e = assertThrows(DataConverterException.class, () -> codec.decode(List.of(missing)));
        assertTrue(e.getMessage().contains("no-such-key not found in Redis"), e.getMessage());
    }
}
