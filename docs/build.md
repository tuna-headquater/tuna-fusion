# Building the project


## Docker images

Following standalone images should be built with Maven projects:

```shell
docker buildx build --push \
  --platform linux/amd64,linux/arm64 \
  -t robinqu/tuna-fusion-operator:$(date +%s) \
  -t robinqu/tuna-fusion-operator:latest \
  --build-arg MAVEN_TARGET=tuna-fusion-operator \
  .
```