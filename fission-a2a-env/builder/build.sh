set -e
set -x
env

echo "Run pre_build.py"
uv run ./builder/pre_build.py

echo "Copy source to deploy"
cp -r "$SOURCE_ARCHIVE_PATH" "$DEPLOY_ARCHIVE_PATH"

if [ -f "$WORKSPACE_ROOT_PATH"/build_source.sh ]; then
  echo "Run user provided build_source.sh"
  sh "$WORKSPACE_ROOT_PATH"/build_source.sh
else
  echo "Copy source to deploy"
  cp -r "$SOURCE_ARCHIVE_PATH" "$DEPLOY_ARCHIVE_PATH"
  echo "Attempt to install deps"
  if [ -f "$SOURCE_ARCHIVE_PATH"/requirements.txt ]; then
    uv pip install -i "${PYPI_INDEX}" -r "$SOURCE_ARCHIVE_PATH"/requirements.txt --target "$DEPLOY_ARCHIVE_PATH"
  fi
  if [ -f "$SOURCE_ARCHIVE_PATH"/pyproject.toml ]; then
    uv pip compile -o /tmp/requirements.txt "$SOURCE_ARCHIVE_PATH"/pyproject.toml
    uv pip install -i "${PYPI_INDEX}" -r /tmp/requirements.txt/requirements.txt --target "$DEPLOY_ARCHIVE_PATH"
  fi
fi

echo "Run post_build.py"
uv run ./builder/post_build.py

echo "Done!"