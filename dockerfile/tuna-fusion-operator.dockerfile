# This image contains all python code, actual container should have explictly assign entrypoint cmd.
#
FROM python:3.13
COPY --from=ghcr.io/astral-sh/uv:0.7.12 /uv /uvx /bin/
ADD . /app
WORKDIR /app/
RUN uv sync --locked --index https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple/
CMD ["/bin/bash"]