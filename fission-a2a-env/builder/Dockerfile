FROM ubuntu:24.04
ARG PYPI_INDEX=https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple
ENV PYPI_INDEX=${PYPI_INDEX}
COPY --from=ghcr.io/astral-sh/uv:0.7.12 /uv /uvx /usr/local/bin/
WORKDIR /builder
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
  --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -y wget curl git zip unzip
ADD pyproject.toml uv.lock /builder/
RUN uv sync -i ${PYPI_INDEX}
ADD . /builder/
RUN chmod +x build.sh
CMD ["sh", "build.sh"]