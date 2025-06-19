package ai.tuna.fusion.kubernetes.operator.dr;

import ai.tuna.fusion.kubernetes.operator.crd.AgentEnvironment;
import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.GroupVersionKind;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GenericKubernetesDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.GroupVersionKindPlural;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringSubstitutor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import static ai.tuna.fusion.kubernetes.operator.reconciler.AgentEnvironmentReconciler.SELECTOR;


/**
 * @author robinqu
 */
@KubernetesDependent(
        informer = @Informer(labelSelector = SELECTOR)
)
public class FissionEnvDependentResource extends GenericKubernetesDependentResource<AgentEnvironment> {

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
""";


    public static String API_VERSION = "fission.io/v1";
    public static String KIND = "Environment";


    public FissionEnvDependentResource(GroupVersionKindPlural groupVersionKind, String name) {
        super(new GroupVersionKind("fission.io", "v1", "Environment"), "environments.fission.io");
    }

    @Override
    protected GenericKubernetesResource desired(AgentEnvironment primary, Context<AgentEnvironment> context) {
        Map<String, String> valuesMap = Map.of(
                "name", primary.getMetadata().getName(),
                "namespace", primary.getMetadata().getNamespace(),
                "builderImage", primary.getSpec().getFissionEnv().getBuilderImage(),
                "runtimeImage", primary.getSpec().getFissionEnv().getRuntimeImage(),
                "poolSize", String.valueOf(primary.getSpec().getFissionEnv().getPoolSize())
        );
        StringSubstitutor sub = new StringSubstitutor(valuesMap);
        try (var is = IOUtils.toInputStream(sub.replace(ENV_TEMPLATE), Charset.defaultCharset())) {
            return context.getClient().genericKubernetesResources(API_VERSION, KIND).load(is)
                    .get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
