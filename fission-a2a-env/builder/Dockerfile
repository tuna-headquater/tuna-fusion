ARG BUILDER_IMAGE=fission/builder
FROM ${BUILDER_IMAGE} as builder

ARG PYTHON_VERSION=3.13.5-bullseye
FROM python:${PYTHON_VERSION}
COPY --from=builder /builder /builder

ADD build.sh /usr/local/bin/build
EXPOSE 8001