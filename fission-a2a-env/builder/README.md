# builder

This image is used to instantiate a Job resource for complete a `PodFunctionBuild`.

To prepare this image:

```shell
docker buildx build --push --platform linux/amd64,linux/arm64 -t robinqu/fusion-a2a-env-builder:$(date +%s) .
```

To test this image:

```shell
rm -rf /tmp/fusion_builder_test && \
    mkdir -p /tmp/fusion_builder_test/source_archive /tmp/fusion_builder_test/deploy_archive /tmp/fusion_builder_test/workspace

BUILDER_TAG=1752744761 

docker run -it \
 -e SKIP_POST_BUILD=ON \
 -e DEPLOY_ARCHIVE_PATH=/deploy_archive \
 -e WORKSPACE_ROOT_PATH=/workspace \
 -e SOURCE_ARCHIVE_PATH=/source_archive \
 -e SOURCE_ARCHIVE_JSON_PATH=/workspace/source_archive.json \
 -v $PWD/test/hello_world:/workspace \
 -v /tmp/fusion_builder_test/deploy_archive:/deploy_archive \
 -v /tmp/fusion_builder_test/source_archive:/source_archive \
 robinqu/fusion-a2a-env-builder:${BUILDER_TAG}
```

To test it in host env:

```shell
chmod +x build.sh

rm -rf /tmp/fusion_builder_test && \
    mkdir -p /tmp/fusion_builder_test/source_archive /tmp/fusion_builder_test/deploy_archive /tmp/fusion_builder_test/workspace
    
WORKSPACE_ROOT_PATH=/tmp/fusion_builder_test/workspace \
SOURCE_ARCHIVE_PATH=/tmp/fusion_builder_test/source_archive \
DEPLOY_ARCHIVE_PATH=/tmp/fusion_builder_test/deploy_archive \
SOURCE_ARCHIVE_JSON_PATH=$PWD/test/hello_world/source_archive.json \
SKIP_POST_BUILD=ON \
./build.sh
```