#!/usr/bin/env bash
set -e
set -x

NEW_FUNCTION_NAME=$FUNCTION_NAME

# Get the associated package name from the function CRD
ORIGINAL_PACKAGE_NAME=$(kubectl get function "$FUNCTION_NAME" -n "$STAGING_NAMESPACE" -o jsonpath='{.spec.package.packageref.name}')

if [ -z "$ORIGINAL_PACKAGE_NAME" ]; then
  echo "Failed to get package name from function $FUNCTION_NAME"
  exit 1
fi

echo "Found associated package: $PACKAGE_NAME"


# Get deployment URL and checksum from original package
DEPLOY_URL=$(kubectl get package "$ORIGINAL_PACKAGE_NAME" -n "$STAGING_NAMESPACE" -o jsonpath='{.spec.deployment.url}')
CHECKSUM_SUM=$(kubectl get package "$ORIGINAL_PACKAGE_NAME" -n "$STAGING_NAMESPACE" -o jsonpath='{.spec.deployment.checksum.sum}')


if [ -z "$DEPLOY_URL" ] || [ -z "$CHECKSUM_SUM" ]; then
  echo "Failed to retrieve deployment URL or checksum from package: $ORIGINAL_PACKAGE_NAME"
  exit 1
fi

echo "Deployment URL: $DEPLOY_URL"
echo "Checksum Sum: $CHECKSUM_SUM"

NEW_PACKAGE_NAME=uuid=$NEW_FUNCTION_NAME-$(cat /proc/sys/kernel/random/uuid)

# Create a new package using the retrieved values
fission package create \
  --name "$NEW_PACKAGE_NAME" \
  -n "$PRODUCTION_NAMESPACE" \
  --deployarchive="$DEPLOY_URL" \
  --deploychecksum="$CHECKSUM_SUM" \
  --env="$FUNCTION_RUNTIME_ENV"

# Wait for the new package to be ready
sleep 2

# Create new function using the selected package
echo "Creating new function: $NEW_FUNCTION_NAME using package: $NEW_PACKAGE_NAME"
fission fn create \
  --name "$NEW_FUNCTION_NAME" \
  -n "$PRODUCTION_NAMESPACE" \
  --pkg "$NEW_PACKAGE_NAME" \
  --entrypoint "agent.main"  # Adjust entrypoint according to your code

echo "New function $NEW_FUNCTION_NAME has been successfully created."

# Create the function
fission route create \
  --name "$NEW_FUNCTION_NAME" \
  -n "$PRODUCTION_NAMESPACE" \
  --function "$NEW_FUNCTION_NAME" \
  --url "$NEW_FUNCTION_NAME" \
  --prefix /production
echo "Route $NEW_FUNCTION_NAME has been successfully created."