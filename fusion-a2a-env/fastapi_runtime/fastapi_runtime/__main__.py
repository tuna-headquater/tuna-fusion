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
    uvicorn.run(app, host="0.0.0.0", port=RUNTIME_PORT, log_level=LOG_LEVEL)


if __name__ == "__main__":
    main()