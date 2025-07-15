package ai.tuna.fusion.gitops.server.git.pipeline.impl;

import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;

import java.util.Map;

/**
 * @author robinqu
 */
public class FunctionBuildPodInitScript extends BaseInitScript {

    public FunctionBuildPodInitScript(Map<String, String> fileAssets) {
        super(fileAssets);
    }

    @Override
    void configureFileAssets(Map<String, String> fileAssets) {
        fileAssets.put(PodFunctionBuild.BUILD_SCRIPT_FILENAME, renderBuildScript());
    }

    public String renderBuildScript() {
        return """
                #!/bin/sh
                set -ex
                
                if [ -f ${SRC_PKG}/requirements.txt ]; then
                  uv pip install -i https://mirrors.tuna.tsinghua.edu.cn/pypi/web/simple -r ${SRC_PKG}/requirements.txt --target ${SRC_PKG}
                fi
                
                cp -r ${SRC_PKG} ${DEPLOY_ARCHIVE_SUBPATH}
                """;
    }


}
