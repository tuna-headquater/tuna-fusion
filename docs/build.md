# Building the project


## Docker images

### Java-based modules

Following standalone images should be built with Maven projects:

```shell
MAVEN_TARGET=tuna-fusion-operator \
docker buildx build --push \
      --platform linux/amd64,linux/arm64 \
      -t robinqu/$module:$(date +%s) \
      -t robinqu/$module:latest \
      --build-arg MAVEN_TARGET=$MAVEN_TARGET .
```

See [build-java-images.sh](/build-all-images.sh) for more details.


### `fusion-a2a-env` module 

```shell
docker buildx build --push \
  --platform linux/amd64,linux/arm64 \
  -t robinqu/fusion-a2a-fastapi-runtime:$(date +%s) \
  -t robinqu/fusion-a2a-fastapi-runtime:latest .
```


```shell
docker buildx build --push \
  --platform linux/amd64,linux/arm64 \
  -t robinqu/fusion-a2a-env-builder:$(date +%s) \
  -t robinqu/fusion-a2a-env-builder:latest .
```