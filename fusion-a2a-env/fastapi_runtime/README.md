# Python runtime for A2A server


# Building images

```shell
docker buildx build --push --platform linux/amd64,linux/arm64 -t robinqu/fusion-a2a-fastapi-runtime:$(date +%s) .
```

## Local test

#### Test with minimum setup

TODO


#### Test with custom runtime configurations

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