#!/usr/bin/env bash
set -ex

modules=("tuna-fusion-operator" "tuna-fusion-gitops-server" "tuna-fusion-executor")

for module in "${modules[@]}"; do
    docker buildx build --push \
      --platform linux/amd64,linux/arm64 \
      -t robinqu/$module:$(date +%s) \
      -t robinqu/$module:latest \
      --build-arg MAVEN_TARGET=$module \
      .
done