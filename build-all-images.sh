#!/usr/bin/env bash
set -ex

image_tag=$(date +%s)

echo "Build robinqu/fusion-a2a-fastapi-runtime"
docker buildx build --push --platform linux/amd64,linux/arm64 -t robinqu/fusion-a2a-fastapi-runtime:"$image_tag" -t robinqu/fusion-a2a-fastapi-runtime:latest  ./fusion-a2a-env/fastapi_runtime

echo "Build robinqu/fusion-a2a-env-builder"
docker buildx build --push --platform linux/amd64,linux/arm64 -t robinqu/fusion-a2a-env-builder:"$image_tag" -t robinqu/fusion-a2a-env-builder:latest  ./fusion-a2a-env/builder

modules=("tuna-fusion-operator" "tuna-fusion-gitops-server" "tuna-fusion-executor")

for module in "${modules[@]}"; do
    echo "Build robinqu/$module"
    docker buildx build --push \
      --platform linux/amd64,linux/arm64 \
      -t robinqu/"$module":"$image_tag" \
      -t robinqu/"$module":latest \
      --build-arg MAVEN_TARGET="$module" \
      .
done
