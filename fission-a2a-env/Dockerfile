ARG PYTHON_VERSION=3.13.5-bullseye

FROM python:${PYTHON_VERSION}

WORKDIR /app


COPY *.py /app

ENTRYPOINT ["python3"]
CMD ["server.py"]