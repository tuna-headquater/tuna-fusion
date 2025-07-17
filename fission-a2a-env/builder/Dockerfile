ARG PYTHON_VERSION=3.13.5-bullseye
FROM python:${PYTHON_VERSION}
ARG PYPI_INDEX=https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple
ENV PYPI_INDEX=${PYPI_INDEX}
COPY --from=ghcr.io/astral-sh/uv:0.7.12 /uv /uvx /usr/local/bin/
WORKDIR /src
ADD *.py requirements.txt /src/builder/
ADD build.sh /src/
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
  --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && apt-get install -y jo wget curl git zip unzip
RUN chmod +x build.sh && \
    cd builder && \
    uv venv && \
    uv pip install -r requirements.txt -i ${PYPI_INDEX}
CMD ["sh", "build.sh"]