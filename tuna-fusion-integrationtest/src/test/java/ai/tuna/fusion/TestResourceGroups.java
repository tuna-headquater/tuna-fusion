package ai.tuna.fusion;

import ai.tuna.fusion.intgrationtest.ResourceTreeNode;

/**
 * @author robinqu
 */
public class TestResourceGroups {
    public static final ResourceTreeNode RESOURCE_GROUP_1 = ResourceTreeNode.parseYaml("""
            type: PodFunctionBuild
            classpath: yaml/podpool/pod_function_build_1.yaml
            dependencies:
            - type: PodFunction
              classpath: yaml/podpool/pod_function_1.yaml
              dependencies:
              - type: PodPool
                classpath: yaml/podpool/podpool_1.yaml
            """);

    public static final ResourceTreeNode RESOURCE_GROUP_2 = ResourceTreeNode.parseYaml("""
            type: PodFunctionBuild
            classpath: yaml/agent_1/pod_function_build_1.yaml
            dependencies:
            - type: AgentDeployment
              classpath: yaml/agent_1/agent_deployment_1.yaml
              dependencies:
              - type: AgentEnvironment
                classpath: yaml/agent_1/agent_env_1.yaml
            """);

}
