#!/usr/bin/env python
import importlib
import json
import logging
import os
import sys
from typing import Callable, Optional, Any

from a2a.server.agent_execution import AgentExecutor
from a2a.server.apps.jsonrpc.fastapi_app import JSONRPCApplication
from a2a.server.events.in_memory_queue_manager import InMemoryQueueManager
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore
from a2a.types import AgentCard
from fastapi import FastAPI, Request, Response
from redis.asyncio import Redis
from starlette.applications import Starlette

from a2a_runtime.database_task_store import DatabaseTaskStore
from a2a_runtime.models import A2ARuntimeConfig
from a2a_runtime.redis_queue_manager import RedisQueueManager

USERFUNCVOL = os.environ.get("USERFUNCVOL", "/userfunc")


def store_specialize_info(state):
    json.dump(state, open(os.path.join(USERFUNCVOL, "state.json"), "w"))

def check_specialize_info_exists():
    return os.path.exists(os.path.join(USERFUNCVOL, "state.json"))

def read_specialize_info():
    return json.load(open(os.path.join(USERFUNCVOL, "state.json")))

def import_src(path):
    return importlib.machinery.SourceFileLoader("mod", path).load_module()

def read_agent_card(file_root) ->AgentCard:
    with open(os.path.join(file_root, "agent_card.json")) as f:
        return AgentCard.model_validate(json.load(f))

def read_runtime_config(file_root: str) -> A2ARuntimeConfig:
    with open(os.path.join(file_root, "a2a_runtime.json")) as f:
        return A2ARuntimeConfig.model_validate(json.load(f))


class A2AApplication(JSONRPCApplication):

    def __init__(self, agent_card: AgentCard, agent_executor: AgentExecutor, runtime_config: A2ARuntimeConfig):
        logging.debug("With runtime config: %s", runtime_config)
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

    def build(self, agent_card_url: str = '/.well-known/agent.json', rpc_url: str = '/',
              **kwargs: Any) -> FastAPI | Starlette:
        pass

    async def handle_requests(self, request: Request) -> Response:
        return await self._handle_requests(request)

    async def get_agent_card(self, request: Request) -> Response:
        return await self._handle_get_agent_card(request)


class FuncApp(FastAPI):
    def __init__(self, loglevel=logging.DEBUG):
        super().__init__(title="tuna-fusion A2A Server")
        self.fastapi_app = FastAPI()
        # init the class members
        self.agent_app: Optional[A2AApplication] = None
        self.logger = logging.getLogger()
        self.ch = logging.StreamHandler(sys.stdout)

        self.logger.setLevel(loglevel)
        self.ch.setLevel(loglevel)
        self.ch.setFormatter(
            logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
        )
        self.logger.addHandler(self.ch)

        if check_specialize_info_exists():
            self.logger.info('Found state.json')
            specialize_info = read_specialize_info()
            self.agent_app = self._load_v2(specialize_info)
            self.logger.info('Loaded user function {}'.format(specialize_info))

    def build_json_rpc_app(self, executor_factory: Callable[[], AgentExecutor], file_root: str) -> JSONRPCApplication:
        agent_executor = None
        try:
            agent_executor = executor_factory()
        except Exception as e:
            self.logger.error("Failed to execute factory method", exc_info=e)
            raise e

        agent_card = None
        try:
            agent_card = read_agent_card(file_root)
        except Exception as e:
            self.logger.error("Failed to read agent card", exc_info=e)
            raise  e

        runtime_config = None
        try:
            runtime_config = read_runtime_config(file_root)
        except Exception as e:
            self.logger.error("Failed to read runtime config", exc_info=e)
            raise e

        return A2AApplication(agent_executor=agent_executor, agent_card=agent_card, runtime_config=runtime_config)

    async def load(self):
        self.logger.info("/specialize called")
        # load user function from code path
        agent_executor_factory = import_src("/userfunc/user").main
        self.agent_app = self.build_json_rpc_app(agent_executor_factory, "/userfunc/user")
        return ""

    async def loadv2(self, request: Request):
        specialize_info = await request.json()
        if check_specialize_info_exists():
            self.logger.warning("Found state.json, overwriting")
        agent_executor_factory = self._load_v2(specialize_info)
        self.agent_app = self.build_json_rpc_app(agent_executor_factory, specialize_info.get("filepath"))
        store_specialize_info(specialize_info)
        return ""

    async def healthz(self):
        return "", Response(status_code=200)

    async def agent_task_call(self, request: Request):
        if self.agent_app is None:
            self.logger.error("agent_app is None")
            return Response(status_code=500)
        self.logger.info(self.agent_app)
        return await self.agent_app.handle_requests(request)

    async def agent_card_call(self, request: Request):
        if self.agent_app is None:
            self.logger.error("agent_app is None")
            return Response(status_code=500)
        self.logger.info(self.agent_app)
        return await self.agent_app.get_agent_card(request)

    def _load_v2(self, specialize_info):
        filepath = specialize_info['filepath']
        handler = specialize_info['functionName']
        self.logger.info(
            'specialize called with  filepath = "{}"   handler = "{}"'.format(
                filepath, handler))
        # handler looks like `path.to.module.function`
        parts = handler.rsplit(".", 1)
        if len(handler) == 0:
            # default to main.main if entrypoint wasn't provided
            module_name = 'main'
            func_name = 'main'
        elif len(parts) == 1:
            module_name = 'main'
            func_name = parts[0]
        else:
            module_name = parts[0]
            func_name = parts[1]
        self.logger.debug('moduleName = "{}"    funcName = "{}"'.format(
            module_name, func_name))

        # check whether the destination is a directory or a file
        if os.path.isdir(filepath):
            # add package directory path into module search path
            sys.path.append(filepath)

            self.logger.debug('__package__ = "{}"'.format(__package__))
            if __package__:
                mod = importlib.import_module(module_name, __package__)
            else:
                mod = importlib.import_module(module_name)

        else:
            # load source from destination python file
            mod = import_src(filepath)

        # load user function from module
        return getattr(mod, func_name)

