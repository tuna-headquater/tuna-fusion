package ai.tuna.fusion.kubernetes.operator.dr;


import ai.tuna.fusion.metadata.crd.AgentEnvironment;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.GarbageCollected;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Updater;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.StringSubstitutor;

import java.util.Map;

import static ai.tuna.fusion.kubernetes.operator.reconciler.AgentEnvironmentReconciler.SELECTOR;


/**
 * @author robinqu
 */
@Slf4j
@KubernetesDependent(
        informer = @Informer(labelSelector = SELECTOR)
)
public class FissionEnvDependentResource extends GenericKubernetesDependentResource<AgentEnvironment> implements Creator<GenericKubernetesResource, AgentEnvironment>, Updater<GenericKubernetesResource, AgentEnvironment>, GarbageCollected<AgentEnvironment> {

    public static String ENV_TEMPLATE = """
apiVersion: fission.io/v1
kind: Environment
metadata:
  name: ${name}
  namespace: ${namespace}
  labels:
    managed: true
spec:
  builder:
    command: build
    image: ${builderImage}
  keeparchive: false
  poolsize: ${poolSize}
  runtime:
    image: ${runtimeImage}
  version: 3
""";


    public static String API_VERSION = "fission.io/v1";
    public static String KIND = "Environment";

    public FissionEnvDependentResource() {
        super(new GroupVersionKind("fission.io", "v1", "Environment"), "environments.fission.io");
    }

    @Override
    protected GenericKubernetesResource desired(AgentEnvironment primary, Context<AgentEnvironment> context) {
        StringSubstitutor sub = getStringSubstitutor(primary);
        var yamlContent = sub.replace(ENV_TEMPLATE);
        var res = Serialization.unmarshal(yamlContent, GenericKubernetesResource.class);
        log.info("desired res: {}", res);
        return res;
    }

    private static StringSubstitutor getStringSubstitutor(AgentEnvironment primary) {
        Map<String, String> valuesMap = Map.of(
                "name", primary.getMetadata().getName(),
                "namespace", primary.getMetadata().getNamespace(),
                "builderImage", primary.getSpec().getFissionEnv().getBuilderImage(),
                "runtimeImage", primary.getSpec().getFissionEnv().getRuntimeImage(),
                "poolSize", String.valueOf(primary.getSpec().getFissionEnv().getPoolSize())
        );
        StringSubstitutor sub = new StringSubstitutor(valuesMap);
        return sub;
    }
}
