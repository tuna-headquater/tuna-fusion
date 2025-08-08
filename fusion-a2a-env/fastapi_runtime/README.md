# Python runtime for A2A server


# Building images

```shell
docker buildx build --push --platform linux/amd64,linux/arm64 -t robinqu/fusion-a2a-fastapi-runtime:$(date +%s) .
```

## Local test

### `hello_world` example 

```shell
# run runtime server
RUNTIME_SERVICE_PORT=8888 uv run -m fastapi_runtime

# specialize to load agent executor
curl -XPOST localhost:8888/specialize -d "{\"functionName\": \"server.handle\", \"filepath\": \"$PWD/test/hello_world\", \"app_type\": \"web_app\"}"

# run client test
curl localhost:8888/
``` 


### A2A examples

#### Example 1: A2A runtime with default in-memory configurations

```shell
RUNTIME_SERVICE_PORT=8888 uv run -m fastapi_runtime

curl -XPOST localhost:8888/specialize -d "{\"functionName\": \"app.handle\", \"filepath\": \"$PWD/samples/hello_world\", \"app_type\": \"agent_app\"}"

uv run $PWD/samples/hello_world/test_client.py
```

#### Example 2: With custom runtime configurations

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