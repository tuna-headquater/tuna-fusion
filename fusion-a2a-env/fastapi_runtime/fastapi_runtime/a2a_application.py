import logging

from a2a.server.agent_execution import AgentExecutor
from a2a.server.apps import A2AFastAPIApplication
from a2a.server.events import InMemoryQueueManager
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore
from a2a.types import AgentCard
from redis.asyncio import Redis

from fastapi_runtime.database_task_store import DatabaseTaskStore
from fastapi_runtime.models import A2ARuntimeConfig
from fastapi_runtime.redis_queue_manager import RedisQueueManager


class A2AApplication(A2AFastAPIApplication):
    def __init__(self, agent_card: AgentCard, agent_executor: AgentExecutor, runtime_config: A2ARuntimeConfig):
        logging.debug("With runtime config: %s", runtime_config)
        task_store = None
        queue_manager = None
        match runtime_config.queue_manager.provider:
            case "InMemory":
                queue_manager = InMemoryQueueManager()
            case "Redis":
                queue_manager = RedisQueueManager(
                    redis_client=Redis.from_url(runtime_config.queue_manager.redis.redis_url),
                    relay_channel_key_prefix=runtime_config.queue_manager.redis.relay_channel_key_prefix,
                    task_registry_key=runtime_config.queue_manager.redis.task_registry_key,
                    task_id_ttl_in_second=runtime_config.queue_manager.redis.task_id_ttl_in_second
                )
            case _:
                raise Exception("Invalid queue manager provider")

        match runtime_config.task_store.provider:
            case "InMemory":
                task_store = InMemoryTaskStore()
            case "MySQL" | "Postgres" | "SQLite":
                task_store = DatabaseTaskStore(
                    db_url=runtime_config.task_store.sql.database_url,
                    create_table_if_not_exists=runtime_config.task_store.sql.create_table,
                    table_name=runtime_config.task_store.sql.task_store_table_name
                )
            case _:
                raise Exception("Invalid task store provider")

        request_handler = DefaultRequestHandler(
            agent_executor=agent_executor,
            task_store=task_store,
            queue_manager=queue_manager
        )
        super().__init__(agent_card=agent_card, http_handler=request_handler)

