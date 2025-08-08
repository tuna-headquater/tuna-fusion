package ai.tuna.fusion.metadata.informer.impl;

import ai.tuna.fusion.metadata.crd.ResourceUtils;
import ai.tuna.fusion.metadata.informer.ConfigSourceResources;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;

import java.util.List;
import java.util.Optional;

/**
 * @author robinqu
 */
public class DefaultConfigSourceResources extends AbstractResourceOperations implements ConfigSourceResources {
    private SharedIndexInformer<ConfigMap> configmaps;
    private SharedIndexInformer<Secret> secrets;

    public DefaultConfigSourceResources(KubernetesClient kubernetesClient) {
        super(kubernetesClient);
    }

    @Override
    public SharedIndexInformer<ConfigMap> configmaps() {
        return configmaps;
    }

    @Override
    public SharedIndexInformer<Secret> secrets() {
        return secrets;
    }

    @Override
    public Optional<ConfigMap> queryConfigMap(String namespace, String configMapName) {
        return ResourceUtils.getResourceFromInformer(configmaps, namespace, configMapName);
    }

    @Override
    public Optional<Secret> querySecret(String namespace, String secretName) {
        return ResourceUtils.getResourceFromInformer(secrets, namespace, secretName);
    }

    @Override
    protected List<SharedIndexInformer<?>> createInformers() {
        this.configmaps = createInformer(ConfigMap.class);
        this.secrets = createInformer(Secret.class);
        return List.of(this.configmaps, this.secrets);
    }
}
