FROM ubuntu:24.04
ARG PYPI_INDEX=https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple
ENV PYPI_INDEX=${PYPI_INDEX}
COPY --from=ghcr.io/astral-sh/uv:0.7.12 /uv /uvx /usr/local/bin/
WORKDIR /app
COPY . /app/
RUN uv sync -i ${PYPI_INDEX}
CMD ["uv", "run", "-m", "fastapi_runtime"]