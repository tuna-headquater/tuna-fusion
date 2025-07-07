import logging
import os

import uvicorn

from a2a_runtime.server import FuncApp

try:
    LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
    LOG_LEVEL = getattr(logging, LOG_LEVEL)
except:
    LOG_LEVEL = logging.INFO


RUNTIME_PORT = int(os.environ.get("RUNTIME_PORT", "8888"))

def main():
    app = FuncApp()
    app.add_api_route(path='/specialize', endpoint=app.load, methods=["POST"])
    app.add_api_route(path='/v2/specialize', endpoint=app.loadv2, methods=["POST"])
    app.add_api_route(path='/healthz', endpoint=app.healthz, methods=["GET"])
    app.add_api_route(path='/.well-known/agent.json', endpoint=app.agent_card_call, methods=["GET"])
    app.add_api_route(path='/', endpoint=app.agent_task_call, methods=["POST"])
    uvicorn.run(app, host="0.0.0.0", port=RUNTIME_PORT, log_level=LOG_LEVEL)


if __name__ == "__main__":
    main()