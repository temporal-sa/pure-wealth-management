from temporalio.envconfig import ClientConfig

from common import task_queues
from common.claim_check import claim_check_data_converter, use_claim_check
from common.redis_config import RedisConfig


class ClientHelper:
    """Temporal connection settings and task queue names, read from the environment.

    The connection uses Temporal's environment configuration (TEMPORAL_ADDRESS,
    TEMPORAL_NAMESPACE, TLS settings or a config profile), defaulting to a local dev server.
    With USE_CLAIM_CHECK=true, the client also uses the claim check codec (common.claim_check).
    """

    def __init__(self):
        self.client_config = ClientConfig.load_client_connect_config()
        # Default to a local dev server when no address is configured.
        if self.client_config.get('target_host') is None:
            self.client_config['target_host'] = '127.0.0.1:7233'

        self.use_claim_check = use_claim_check()
        if self.use_claim_check:
            self.client_config['data_converter'] = claim_check_data_converter()

        self.address = self.client_config.get('target_host')
        self.namespace = self.client_config.get('namespace')
        self.taskQueue = task_queues.TASK_QUEUE
        self.taskQueueOpenAccount = task_queues.TASK_QUEUE_OPEN_ACCOUNT
        self.taskQueueLLM = task_queues.TASK_QUEUE_LLM

    def claim_check_status(self) -> str:
        """For startup messages: whether claim check is on, and which Redis it uses."""
        if not self.use_claim_check:
            return "Claim check: off"
        redis = RedisConfig()
        return f"Claim check: ON (payloads stored in Redis at {redis.hostname}:{redis.port})"
