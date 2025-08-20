#!/usr/bin/env bash
set -ex

helm package ./tuna-fusion-charts
helm push ./tuna-fusion-*.tgz oci://ghcr.io/tuna-headquater