ARG PYTHON_VERSION=3.13.5-bullseye

FROM python:${PYTHON_VERSION}
COPY --from=ghcr.io/astral-sh/uv:0.7.12 /uv /uvx /usr/local/bin/

WORKDIR /app


COPY *.py pyproject.toml uv.lock /app/
RUN uv sync -i https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple

CMD ["uv", "run", "server.py"]