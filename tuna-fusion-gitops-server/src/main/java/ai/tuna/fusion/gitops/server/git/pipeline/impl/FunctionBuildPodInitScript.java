package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuildSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.SneakyThrows;

import java.util.HashMap;
import java.util.Map;

/**
 * @author robinqu
 */
public class FunctionBuildPodInitScript extends BaseInitScript {
    private final PodFunctionBuildSpec.SourceArchive sourceArchive;

    @Getter(AccessLevel.PROTECTED)
    private final ObjectMapper objectMapper;

    public FunctionBuildPodInitScript(PodFunctionBuildSpec.SourceArchive sourceArchive) {
        super(new HashMap<>(32));
        this.sourceArchive = sourceArchive;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    void configureFileAssets(Map<String, FileAsset> fileAssets) {
        fileAssets.put(PodFunctionBuild.BUILD_SCRIPT_FILENAME, renderBuildScript());
        fileAssets.put(PodFunctionBuild.SOURCE_ARCHIVE_MANIFEST, renderSourceArchiveJson());
    }

    @SneakyThrows
    private FileAsset renderSourceArchiveJson() {
        return FileAsset.builder()
                .content(objectMapper.writeValueAsString(sourceArchive))
                .executable(false)
                .build();
    }

    public FileAsset renderBuildScript() {
        var content =  """
                #!/bin/sh
                set -ex
                env
                
                uv run pre_build
                
                cp -r $SOURCE_ARCHIVE_PATH $DEPLOY_ARCHIVE_PATH
                if [ -f $DEPLOY_ARCHIVE_PATH/requirements.txt ]; then
                  uv pip install -i ${PYPI_INDEX} -r ${SOURCE_ARCHIVE_PATH}/requirements.txt --target ${DEPLOY_ARCHIVE_PATH}
                fi
                
                if [-f $DEPLOY_ARCHIVE_PATH/project.toml ]; then
                    cd $DEPLOY_ARCHIVE_PATH
                    uv sync -i ${PYPI_INDEX}
                    popd
                fi
                
                uv run post_build
                """;
        return FileAsset.builder()
                .content(content)
                .executable(true)
                .build();
    }


}
