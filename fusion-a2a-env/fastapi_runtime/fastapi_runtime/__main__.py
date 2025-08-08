import logging
import os

import uvicorn

from fastapi_runtime.func_app import FuncApp

try:
    LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO")
    LOG_LEVEL = getattr(logging, LOG_LEVEL)
except:
    LOG_LEVEL = logging.INFO


RUNTIME_PORT = int(os.environ.get("RUNTIME_SERVICE_PORT", "8888"))

def main():
    app = FuncApp()
    app.add_api_route(path='/specialize', endpoint=app.load, methods=["POST"])
    app.add_api_route(path='/health', endpoint=app.health, methods=["GET"])
    app.add_api_route(path='/', endpoint=app.dispatch, methods=["POST"])
    app.add_api_route(path='/{path_name:path}', endpoint=app.dispatch, methods=["GET", "POST", "PUT", "HEAD", "OPTIONS", "DELETE"])
    uvicorn.run(app, host="0.0.0.0", port=RUNTIME_PORT, log_level=LOG_LEVEL)


if __name__ == "__main__":
    main()