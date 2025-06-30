#!/bin/sh
set -ex

if [ -f ${SRC_PKG}/requirements.txt ]; then
  uv pip install -i https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple -r ${SRC_PKG}/requirements.txt --target ${SRC_PKG}
fi

cp -r ${SRC_PKG} ${DEPLOY_PKG}