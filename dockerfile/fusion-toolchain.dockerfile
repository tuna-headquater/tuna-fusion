FROM ubuntu:latest
COPY --from=ghcr.io/astral-sh/uv:0.7.12 /uv /uvx /bin/
RUN curl -Lo fission https://github.com/fission/fission/releases/download/v1.21.0/fission-v1.21.0-linux-amd64 \
    && chmod +x fission && sudo mv fission /usr/local/bin/
