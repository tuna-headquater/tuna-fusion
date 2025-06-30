# fission-a2a-env

A modern python environment for A2A compatible agent applications.


## Local test

`hello_world` example 

```shell
# run runtime server
USERFUNCVOL=$PWD/test/hello_word RUNTIME_PORT=8888 python server.py

# specialize to load agent executor
curl -XPOST localhost:8888/v2/specialize -d "{\"functionName\": \"app.handle\", \"filepath\": \"$PWD/test/hello_world\"}"

# run client test
python $PWD/test/hello_world/test_client.py
``` 


## Building images

For runtime image:

```shell
docker buildx build --push --platform linux/amd64,linux/arm64 -t robinqu/fission-a2a-python-env:$(date +%s) .
```

For builder image:

```shell
docker buildx build --push --platform linux/amd64,linux/arm64 -t robinqu/fission-a2a-python-builder-env:$(date +%s) -f ./builder/Dockerfile ./builder
```

