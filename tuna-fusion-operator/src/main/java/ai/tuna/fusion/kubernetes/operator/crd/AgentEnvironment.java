package ai.tuna.fusion.kubernetes.operator.crd;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;

/**
 * @author robinqu
 */
public class AgentEnvironment extends CustomResource<AgentEnvironmentSpec, AgentEnvironmentStatus> implements Namespaced {
}
