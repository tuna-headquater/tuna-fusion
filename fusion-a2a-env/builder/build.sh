set -e
set -x

echo "Run pre_build.py"
python -m fusion_builder.pre_build

echo "Copy source to deploy"
mkdir -p "$DEPLOY_ARCHIVE_PATH"
cp -r "$SOURCE_ARCHIVE_PATH"/* "$DEPLOY_ARCHIVE_PATH"/

if [ -f "$WORKSPACE_ROOT_PATH"/patch_source.sh ]; then
  echo "Patch sources"
  sh "$WORKSPACE_ROOT_PATH"/patch_source.sh
fi

if [ -f "$WORKSPACE_ROOT_PATH"/build_source.sh ]; then
  echo "Run user provided build_source.sh"
  sh "$WORKSPACE_ROOT_PATH"/build_source.sh
else
  echo "Attempt to install deps"
  if [ -f "$SOURCE_ARCHIVE_PATH"/requirements.txt ]; then
    uv pip install --link-mode=copy -i "${PYPI_INDEX}" -r "$SOURCE_ARCHIVE_PATH"/requirements.txt --target "$DEPLOY_ARCHIVE_PATH"
  fi
  if [ -f "$SOURCE_ARCHIVE_PATH"/pyproject.toml ]; then
    uv pip compile -o /tmp/requirements.txt "$SOURCE_ARCHIVE_PATH"/pyproject.toml
    uv pip install --link-mode=copy -i "${PYPI_INDEX}" -r /tmp/requirements.txt/requirements.txt --target "$DEPLOY_ARCHIVE_PATH"
  fi
fi

echo "Run post_build.py"
python -m fusion_builder.post_build

echo "Done!"