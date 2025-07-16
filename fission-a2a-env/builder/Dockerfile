ARG PYTHON_VERSION=3.13.5-bullseye
ARG PYPI_INDEX=https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple
FROM ${BUILDER_IMAGE} as builder
COPY --from=ghcr.io/astral-sh/uv:0.7.12 /uv /uvx /usr/local/bin/
FROM python:${PYTHON_VERSION}
ENV PYPI_INDEX=${PYPI_INDEX}
WORKDIR /src
ADD *.py requirements.txt /src/builder/
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
  --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -y jo wget curl git zip unzip
RUN uv pip install -r requirements.txt -i ${PYPI_INDEX}