#!/usr/bin/env bash
set -e
set -x

mkdir /build && cd /build
# clone repo
git clone --depths=1 "$GIT_REPO_URL" /src

# assuming /build.sh is mounted
cp /build.sh /src

# package source code
zip -jr src.zip /src

# Check if function exists
if ! fission fn get --name "$FUNCTION_NAME" -n "$STAGING_NAMESPACE" &>/dev/null; then
    echo "Function $FUNCTION_NAME does not exist. Creating it now..."

    # Create the function
    fission fn create \
      -n "$STAGING_NAMESPACE" \
      --name "$FUNCTION_NAME" \
      --env "$FUNCTION_RUNTIME_ENV" \
      --src src.zip \
      --entrypoint "$FUNCTION_ENTRYPOINT" \
      --buildcmd "./build.sh"
else
    echo "Function $FUNCTION_NAME already exists."
fi


# Create route if necessary
if ! fission route get --name "$FUNCTION_NAME" &>/dev/null; then
    echo "Route $FUNCTION_NAME does not exist. Creating it now..."

    # Create the function
    fission route create \
      -n "$STAGING_NAMESPACE" \
      --name "$FUNCTION_NAME" \
      --function "$FUNCTION_NAME" \
      --url "/$FUNCTION_NAME" \
      --prefix /staging
else
    echo "Route $FUNCTION_NAME already exists."
fi
