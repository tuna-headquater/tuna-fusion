FROM ubuntu:latest
COPY --from=ghcr.io/astral-sh/uv:0.7.12 /uv /uvx /usr/local/bin/
COPY --from=bitnami/kubectl:1.33.1 /opt/bitnami/kubectl/bin/kubectl /usr/local/bin/
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
  --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt update  && \
  apt-get install -y curl git

RUN ARCH=$(uname -m) && \
    echo "Detected architecture: ${ARCH}" && \
    case "${ARCH}" in \
      x86_64) URL="https://github.com/fission/fission/releases/download/v1.21.0/fission-v1.21.0-linux-amd64" ;; \
      aarch64) URL="https://github.com/fission/fission/releases/download/v1.21.0/fission-v1.21.0-linux-arm64" ;; \
      *) echo "Unsupported architecture: ${ARCH}"; exit 1 ;; \
    esac && \
    echo "Downloading fission from: ${URL}" && \
    curl -Lo fission "${URL}" && \
    chmod +x fission && \
    mv fission /usr/local/bin/

CMD ["/bin/bash"]