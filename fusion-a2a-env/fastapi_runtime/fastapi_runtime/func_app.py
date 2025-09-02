import asyncio
import importlib
import json
import logging
import os
import sys
import time
from typing import Callable, Optional

from a2a.server.agent_execution import AgentExecutor
from a2a.types import AgentCard
from fastapi import Request, Response, HTTPException, FastAPI
from pydantic import ValidationError
from starlette.routing import Mount
from starlette.types import ASGIApp

from fastapi_runtime.a2a_application import A2AApplication
from fastapi_runtime.models import A2ARuntimeConfig, SpecializeRequest, AppType, SpecializeResponse


def import_src(path):
    return importlib.machinery.SourceFileLoader("mod", path).load_module()

def read_agent_card(file_root) ->AgentCard:
    with open(os.path.join(file_root, "agent_card.json")) as f:
        return AgentCard.model_validate(json.load(f))

def read_runtime_config(file_root: str) -> A2ARuntimeConfig:
    with open(os.path.join(file_root, "a2a_runtime.json")) as f:
        return A2ARuntimeConfig.model_validate(json.load(f))

AGENT_CARD_PATH = "/.well-known/agent.json"

WebAppFn = Callable[[Request], Response]


class FuncApp(FastAPI):
    def __init__(self, loglevel=logging.DEBUG, app_prefix:str="/"):
        super().__init__(title="tuna-fusion runtime app")
        # init the class members
        self._app_prefix = app_prefix
        self._mount: Optional[Mount] = None
        self.logger = logging.getLogger()
        self.ch = logging.StreamHandler(sys.stdout)
        self.logger.setLevel(loglevel)
        self.ch.setLevel(loglevel)
        self.ch.setFormatter(
            logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
        )
        self.logger.addHandler(self.ch)
        self._mutex = asyncio.Lock()
        self._configure_routes()

    def _configure_routes(self):
        app = self
        app.add_api_route(path='/specialize', endpoint=self.load, methods=["POST"])
        app.add_api_route(path='/health', endpoint=self.health, methods=["GET"])

    def build_a2a_application(self, executor_factory: Callable[[], AgentExecutor], file_root: str) -> ASGIApp:
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

        app =  A2AApplication(agent_executor=agent_executor, agent_card=agent_card, runtime_config=runtime_config)
        return Mount(app=app.build(), path=self._app_prefix)

    # def build_fastmcp_server(self) -> :
    def build_web_app(self, fn: WebAppFn) -> ASGIApp:
        self.logger.info("Build web application using a WebAppFn")
        app = FastAPI()
        app.add_api_route("/{path_name:path}", endpoint=fn, methods=["GET", "POST", "PUT", "HEAD", "OPTIONS", "DELETE"])
        return Mount(app=app, path=self._app_prefix)

    async def load(self, request: SpecializeRequest) -> SpecializeResponse:
        handler = request.entrypoint
        filepath = request.deployArchive.filesystemFolderSource.path
        self.logger.info("Load app from %s with handler %s", filepath, handler)

        t1 = time.time()
        try:
            fn = self._load(handler, filepath)
            async with self._mutex:
                if self._mount:
                    self.router.routes.remove(self._mount)
                if request.appType is AppType.AgentApp:
                    self._mount = self.build_a2a_application(fn, filepath)
                if request.appType is AppType.WebApp:
                    self._mount = self.build_web_app(fn)

                self.router.routes.append(self._mount)
            return SpecializeResponse(elapsedTime=time.time() - t1)
        except Exception as e:
            self.logger.error(f"Specialization failed: %s", e)
            raise HTTPException(status_code=500, detail=str(e))

    async def health(self):
        return Response(status_code=200)

    def _load(self, handler: str, filepath: str):
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
            sys.path = sys.path + [filepath, filepath + "/" + module_name, filepath + "/" + module_name.split(".")[0]]

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

