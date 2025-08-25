FROM ubuntu:24.04
ARG PYPI_INDEX=https://pypi.org/simple
ENV PYPI_INDEX=${PYPI_INDEX}
COPY --from=ghcr.io/astral-sh/uv:0.8.13 /uv /uvx /usr/local/bin/
WORKDIR /app
COPY pyproject.toml uv.lock .python-version /app/
RUN uv sync -i ${PYPI_INDEX}
ENV UV_PYTHON=/app/.venv/bin/python \
    PATH=/app/.venv/bin:$PATH
COPY . /app/
CMD ["uv", "run", "-m", "fastapi_runtime"]