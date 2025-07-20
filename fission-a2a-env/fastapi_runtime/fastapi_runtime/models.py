from enum import StrEnum
from typing import Optional

from pydantic import BaseModel, ConfigDict
from pydantic.alias_generators import to_camel

SHARED_MODEL_CONFIG = ConfigDict(
    alias_generator=to_camel,
    validate_by_alias=True,
    validate_by_name=True
)


class TaskStoreProvider(StrEnum):
    Postgers='Postgres'
    MySQL='MySQL',
    SQLite='SQLite',
    InMemory='InMemory'


class SQLConfig(BaseModel):
    model_config = SHARED_MODEL_CONFIG

    database_url: str
    create_table: bool
    task_store_table_name: str


class RedisConfig(BaseModel):
    model_config = SHARED_MODEL_CONFIG

    redis_url: str
    task_id_ttl_in_second: int
    task_registry_key: str
    relay_channel_key_prefix: str


class QueueManagerProvider(StrEnum):
    Redis='Redis',
    InMemory='InMemory'


class QueueManager(BaseModel):
    model_config = SHARED_MODEL_CONFIG

    redis: Optional[RedisConfig] = None
    provider: QueueManagerProvider


class TaskStore(BaseModel):
    model_config = SHARED_MODEL_CONFIG
    provider: Optional[TaskStoreProvider] = None
    sql: Optional[SQLConfig] = None


class A2ARuntimeConfig(BaseModel):
    model_config = SHARED_MODEL_CONFIG
    queue_manager: QueueManager
    task_store: TaskStore


class FilesystemFolderSource(BaseModel):
    path: str


class DeployArchive(BaseModel):
    filesystemFolderSource: FilesystemFolderSource


class AppType(StrEnum):
    WebApp = "WebApp"
    AgentApp = "AgentApp"


class SpecializeRequest(BaseModel):
    entrypoint: str
    deployArchive: DeployArchive
    appType: AppType

