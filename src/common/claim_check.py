"""Claim check: Temporal stores an ID, and the payload itself is kept in Redis.

With USE_CLAIM_CHECK=true, every client this port creates (worker, API, scripts) encodes
each payload it sends to Temporal (workflow and activity inputs and results, signals,
updates, queries) by storing it in Redis under a new UUID and sending only the UUID.
Decoding looks the UUID up again. See the Claim Check pattern:
https://www.enterpriseintegrationpatterns.com/patterns/messaging/StoreInLibrary.html

The format matches the Java worker's codec (java/.../claimcheck/ClaimCheckCodec.java), so
the Python and Java workers can read each other's payloads:

* metadata ``encoding`` = ``claim-checked`` and ``temporal.io/claim-check-codec`` = ``v1``;
* data = the UUID, which is the Redis key;
* the Redis value is the original payload, serialized as protobuf.

Redis becomes part of every workflow's history: if a key is lost, that payload can't be
decoded, so the keys are never expired.
"""

import os
import uuid
from typing import Iterable, List, Optional

import redis.asyncio as redis
from temporalio.api.common.v1 import Payload
from temporalio.converter import DataConverter, PayloadCodec

from common.redis_config import RedisConfig
from common.util import str_to_bool

ENCODING = b"claim-checked"
CODEC_KEY = "temporal.io/claim-check-codec"
CODEC_VERSION = b"v1"


def use_claim_check() -> bool:
    return str_to_bool(os.getenv("USE_CLAIM_CHECK", "false"))


class ClaimCheckCodec(PayloadCodec):

    def __init__(self, config: Optional[RedisConfig] = None, redis_client=None):
        # redis_client is for tests; otherwise one connection pool for the process.
        config = config or RedisConfig()
        self.redis_client = redis_client or redis.Redis(host=config.hostname, port=config.port)

    async def encode(self, payloads: Iterable[Payload]) -> List[Payload]:
        return [await self._encode(p) for p in payloads]

    async def decode(self, payloads: Iterable[Payload]) -> List[Payload]:
        return [await self._decode(p) for p in payloads]

    async def _encode(self, payload: Payload) -> Payload:
        key = str(uuid.uuid4())
        await self.redis_client.set(key, payload.SerializeToString())
        return Payload(metadata={"encoding": ENCODING, CODEC_KEY: CODEC_VERSION}, data=key.encode())

    async def _decode(self, payload: Payload) -> Payload:
        # Payloads that weren't claim-checked (for example, written before claim check was
        # turned on) pass through unchanged.
        if payload.metadata.get(CODEC_KEY) != CODEC_VERSION:
            return payload
        key = payload.data.decode()
        value = await self.redis_client.get(key)
        if value is None:
            raise ValueError(f"Claim check {key} not found in Redis: the payload can't be decoded")
        return Payload.FromString(value)


def claim_check_data_converter() -> DataConverter:
    return DataConverter(payload_codec=ClaimCheckCodec())
