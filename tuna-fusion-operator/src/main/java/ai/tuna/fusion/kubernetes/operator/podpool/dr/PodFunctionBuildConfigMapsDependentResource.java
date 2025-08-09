package ai.tuna.fusion.kubernetes.operator.podpool.dr;

import ai.tuna.fusion.kubernetes.operator.podpool.reconciler.PodFunctionBuildReconciler;
import ai.tuna.fusion.kubernetes.operator.support.impl.FunctionBuildPodBuilderFileAssets;
import ai.tuna.fusion.metadata.crd.PodPoolResourceUtils;
import ai.tuna.fusion.metadata.crd.podpool.PodFunctionBuild;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.javaoperatorsdk.operator.api.config.informer.Informer;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.CRUDBulkDependentResource;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependent;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.sourcePathConfigMapName;
import static ai.tuna.fusion.metadata.crd.PodPoolResourceUtils.workspaceConfigMapName;

/**
 * @author robinqu
 */
@KubernetesDependent(informer = @Informer(labelSelector = PodFunctionBuildReconciler.SELECTOR))
@Slf4j
public class PodFunctionBuildConfigMapsDependentResource extends KubernetesDependentResource<ConfigMap, PodFunctionBuild> implements CRUDBulkDependentResource<ConfigMap, PodFunctionBuild> {

    @Override
    public Map<String, ConfigMap> getSecondaryResources(PodFunctionBuild primary, Context<PodFunctionBuild> context) {
        var wantedNames = Arrays.asList(
                workspaceConfigMapName(primary),
                sourcePathConfigMapName(primary)
        );
        return context.getSecondaryResourcesAsStream(ConfigMap.class)
                .filter(configMap -> wantedNames.contains(configMap.getMetadata().getName()))
                .collect(Collectors.toMap(configMap -> configMap.getMetadata().getName(), configMap -> configMap));
    }

    @Override
    public Map<String, ConfigMap> desiredResources(PodFunctionBuild primary, Context<PodFunctionBuild> context) {
        var podFunction = PodPoolResourceUtils.getReferencedPodFunction(primary, context.getClient()).orElseThrow();
        var podPool = PodPoolResourceUtils.getReferencedPodPool(podFunction, context.getClient()).orElseThrow();
        var builderFileAssets = new FunctionBuildPodBuilderFileAssets(
                primary,
                podFunction,
                podPool
        );
        return Map.of(
                workspaceConfigMapName(primary), builderFileAssets.workspaceFileAssetsConfigMap(),
                sourcePathConfigMapName(primary), builderFileAssets.sourcePatchFileAssetsConfigMap()
        );
    }
}
