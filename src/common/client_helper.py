from temporalio.envconfig import ClientConfig

from common import task_queues


class ClientHelper:
    """Temporal connection settings and task queue names, read from the environment.

    The connection uses Temporal's environment configuration (TEMPORAL_ADDRESS,
    TEMPORAL_NAMESPACE, TLS settings or a config profile), defaulting to a local dev server.
    """

    def __init__(self):
        self.client_config = ClientConfig.load_client_connect_config()
        # Default to a local dev server when no address is configured.
        if self.client_config.get('target_host') is None:
            self.client_config['target_host'] = '127.0.0.1:7233'

        self.address = self.client_config.get('target_host')
        self.namespace = self.client_config.get('namespace')
        self.taskQueue = task_queues.TASK_QUEUE
        self.taskQueueOpenAccount = task_queues.TASK_QUEUE_OPEN_ACCOUNT
        self.taskQueueLLM = task_queues.TASK_QUEUE_LLM
