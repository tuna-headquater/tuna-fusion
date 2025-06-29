#!/usr/bin/env python
import importlib
import json
import logging
import os
import sys
from typing import Callable, Optional, Any

import httpx
import uvicorn
from a2a.server.agent_execution import AgentExecutor
from a2a.server.apps.jsonrpc.fastapi_app import JSONRPCApplication
from a2a.server.request_handlers import DefaultRequestHandler
from a2a.server.tasks import InMemoryTaskStore, InMemoryPushNotifier
from a2a.types import AgentCard
from fastapi import FastAPI, Request, Response
from starlette.applications import Starlette

try:
    LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
    LOG_LEVEL = getattr(logging, LOG_LEVEL)
except:
    LOG_LEVEL = logging.INFO

USERFUNCVOL = os.environ.get("USERFUNCVOL", "/userfunc")
RUNTIME_PORT = int(os.environ.get("RUNTIME_PORT", "8888"))

def store_specialize_info(state):
    json.dump(state, open(os.path.join(USERFUNCVOL, "state.json"), "w"))

def check_specialize_info_exists():
    return os.path.exists(os.path.join(USERFUNCVOL, "state.json"))

def read_specialize_info():
    return json.load(open(os.path.join(USERFUNCVOL, "state.json")))

def import_src(path):
    return importlib.machinery.SourceFileLoader("mod", path).load_module()

def read_agent_card() ->AgentCard:
    return AgentCard.model_validate(json.load(open(os.path.join(USERFUNCVOL, "agent_card.json"))))



class A2AApplication(JSONRPCApplication):

    def __init__(self, agent_card: AgentCard, agent_executor: AgentExecutor):
        request_handler = DefaultRequestHandler(
            agent_executor=agent_executor, task_store=InMemoryTaskStore(), push_notifier=InMemoryPushNotifier(httpx_client=httpx.AsyncClient())
        )
        super().__init__(agent_card, http_handler=request_handler)

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
        self.agent_card = read_agent_card()

        if check_specialize_info_exists():
            self.logger.info('Found state.json')
            specialize_info = read_specialize_info()
            self.agent_app = self._load_v2(specialize_info)
            self.logger.info('Loaded user function {}'.format(specialize_info))

    def build_json_rpc_app(self, executor_factory: Callable[[], AgentExecutor]) -> JSONRPCApplication:
        agent_executor = None
        try:
            agent_executor = executor_factory()
        except Exception as e:
            self.logger.error("Failed to execute factory method", exc_info=e)
        return A2AApplication(agent_card=self.agent_card, agent_executor=agent_executor)


    async def load(self):
        self.logger.info("/specialize called")
        # load user function from codepath
        agent_executor_factory = import_src("/userfunc/user").main
        self.agent_app = self.build_json_rpc_app(agent_executor_factory)
        return ""

    async def loadv2(self, request: Request):
        specialize_info = await request.json()
        if check_specialize_info_exists():
            self.logger.warning("Found state.json, overwriting")
        agent_executor_factory = self._load_v2(specialize_info)
        self.agent_app = self.build_json_rpc_app(agent_executor_factory)
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


def main():
    app = FuncApp()
    app.add_api_route(path='/specialize', endpoint=app.load, methods=["POST"])
    app.add_api_route(path='/v2/specialize', endpoint=app.loadv2, methods=["POST"])
    app.add_api_route(path='/healthz', endpoint=app.healthz, methods=["GET"])
    app.add_api_route(path='/', endpoint=app.agent_task_call, methods=["POST"])
    app.add_api_route(path='/.well-known/agent.json', endpoint=app.agent_card_call, methods=["GET"])
    uvicorn.run(app, host="0.0.0.0", port=RUNTIME_PORT, log_level=LOG_LEVEL)


if __name__ == "__main__":
    main()