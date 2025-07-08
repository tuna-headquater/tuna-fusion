# fission-a2a-env

A modern python environment for A2A compatible agent applications.


## Local test

`hello_world` example 

```shell
# run runtime server
USERFUNCVOL=$PWD/test/hello_world RUNTIME_PORT=8888 uv run -m a2a_runtime

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
docker buildx build --push --platform linux/amd64,linux/arm64 -t robinqu/fission-a2a-python-builder-env:$(date +%s) ./builder
```


## Test dependencies


```shell
$ docker run -d \
	--name pg-test \
	-e POSTGRES_USER=app \
	-e POSTGRES_PASSWORD=mysecretpassword \
	-e POSTGRES_DB=app \
	-e PGDATA=/var/lib/postgresql/data/pgdata \
	-v ./pg_data:/var/lib/postgresql/data \
	postgres

```