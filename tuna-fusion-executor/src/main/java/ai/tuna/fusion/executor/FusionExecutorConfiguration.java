package ai.tuna.fusion.executor;

import ai.tuna.fusion.metadata.informer.AgentResources;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import ai.tuna.fusion.metadata.informer.impl.DefaultAgentResources;
import ai.tuna.fusion.metadata.informer.impl.DefaultPodPoolResources;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author robinqu
 */
@Configuration
public class FusionExecutorConfiguration {

    @Bean
    public AgentResources agentResources(KubernetesClient kubernetesClient) {
        return new DefaultAgentResources(kubernetesClient);
    }

    @Bean
    public PodPoolResources podPoolResources(KubernetesClient kubernetesClient) {
        return new DefaultPodPoolResources(kubernetesClient);
    }

}
