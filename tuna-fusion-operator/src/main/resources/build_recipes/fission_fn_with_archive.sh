#!/bin/bash

set -e
set -x

cd /tmp
echo "Build script with fission archive"
env

echo "Downloading archive to src.zip"
fission archive download -id="$FUNCTION_SOURCE_ARCHIVE_ID" -o src.zip

if ! fission fn get --name "$FUNCTION_NAME" -n "$NAMESPACE" &>/dev/null; then
    echo "Function $FUNCTION_NAME does not exist. Creating it now..."

    # Create the function
    fission fn create \
      -n "$NAMESPACE" \
      --name "$FUNCTION_NAME" \
      --env "$FUNCTION_ENV" \
      --src src.zip \
      --entrypoint "main.handler"
else
    echo "Function $FUNCTION_NAME is to be updated."
    fission fn update \
      -n "$NAMESPACE" \
      --name "$FUNCTION_NAME" \
      --env "$FUNCTION_ENV" \
      --src src.zip \
      --entrypoint "main.handler"
fi

# Create route if necessary
if ! fission route get --name "$FUNCTION_NAME" -n "$NAMESPACE" &>/dev/null; then
  echo "Route $FUNCTION_NAME does not exist. Creating it now..."
  # Create the function
  fission route create \
    -n "$NAMESPACE" \
    --name "$FUNCTION_NAME" \
    --function "$FUNCTION_NAME" \
    --url "/$FUNCTION_NAME" \
    --prefix "/$CATALOGUE_NAME"
else
  echo "Route $FUNCTION_NAME already exists."
fi


echo "Testing fn"
fission fn test -n "$NAMESPACE" --name "$FUNCTION_NAME"
