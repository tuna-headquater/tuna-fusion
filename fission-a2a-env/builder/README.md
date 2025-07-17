# builder

This image is used to instantiate a Job resource for complete a `PodFunctionBuild`.

To prepare this image:

```shell
docker buildx build --push --platform linux/amd64,linux/arm64 -t robinqu/fusion-a2a-env-builder:$(date +%s) .
```