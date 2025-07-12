package ai.tuna.fusion.metadata.crd.podpool;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

/**
 * @author robinqu
 */
public class PodFunction extends CustomResource<PodFunctionSpec, PodFunctionStatus> implements Namespaced {
}
