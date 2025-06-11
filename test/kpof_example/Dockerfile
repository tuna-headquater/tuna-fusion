# docker build bulidx -t exmaple-operator:latest -f ./Dockerfile --platform linux/amd64,linux/arm64  .

FROM python:3.13
ADD . /src
RUN pip install kopf
CMD kopf run /src/handlers.py --verbose