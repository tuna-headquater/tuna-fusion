#!/usr/bin/env bash
set -ex

image_tag=$(date +%s)
namespace=ghcr.io/tuna-headquater

echo "Build $namespace/fusion-a2a-fastapi-runtime"
docker buildx build --push --platform linux/amd64,linux/arm64 \
  -t $namespace/fusion-a2a-fastapi-runtime:"$image_tag" \
  -t $namespace/fusion-a2a-fastapi-runtime:latest \
  --label "org.opencontainers.image.source=https://github.com/tuna-headquater/tuna-fusion" \
 ./fusion-a2a-env/fastapi_runtime

echo "Build $namespace/fusion-a2a-env-builder"
docker buildx build --push --platform linux/amd64,linux/arm64 \
  -t $namespace/fusion-a2a-env-builder:"$image_tag" \
  -t $namespace/fusion-a2a-env-builder:latest  \
  --label "org.opencontainers.image.source=https://github.com/tuna-headquater/tuna-fusion" \
  ./fusion-a2a-env/builder

modules=("tuna-fusion-operator" "tuna-fusion-gitops-server" "tuna-fusion-executor")

for module in "${modules[@]}"; do
    echo "Build $namespace/$module"
    docker buildx build --push \
      --platform linux/amd64,linux/arm64 \
      -t $namespace/"$module":"$image_tag" \
      -t $namespace/"$module":latest \
      --label "org.opencontainers.image.source=https://github.com/tuna-headquater/tuna-fusion" \
      --build-arg MAVEN_TARGET="$module" \
      .
done