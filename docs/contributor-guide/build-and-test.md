# Building the project


## Build docker images

Following standalone images should be built with Maven projects:

```shell
MAVEN_TARGET=tuna-fusion-operator \
docker buildx build --push \
      --platform linux/amd64,linux/arm64 \
      -t robinqu/$module:$(date +%s) \
      -t robinqu/$module:latest \
      --build-arg MAVEN_TARGET=$MAVEN_TARGET .
```


See `build-all-images.sh` for more details.

``` title="build-all-images.sh"
--8<-- "build-all-images.sh"
```


## Build Helm chart

Official charts are hosted on `ghcr.io/tuna-headquater`. To trigger push, please run `build-charts.sh`.

```shell title="build-charts.sh"
--8<-- "build-charts.sh"
```

## Build docs

This project employs [mkdocs](https://www.mkdocs.org/) to build documentation. And material theme is used for better experience, alongside other Markdown plugins:
* plantuml_markdown
* mermaid2
* mike

To prepare build tools:

```shell
pip install mkdocs mkdocs-material mkdocs-mermaid2-plugin plantuml-markdown mike
```