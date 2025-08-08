package ai.tuna.fusion.metadata.informer;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

import java.util.Optional;

/**
 * @author robinqu
 */
public interface ConfigSourceResources {
    SharedIndexInformer<ConfigMap> configmaps();
    SharedIndexInformer<Secret> secrets();
    Optional<ConfigMap> queryConfigMap(String namespace, String configMapName);
    Optional<Secret> querySecret(String namespace, String secretName);
}
