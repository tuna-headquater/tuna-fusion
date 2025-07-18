package ai.tuna.fusion.executor;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.executor.driver.podpool.impl.DefaulltFunctionPodManager;
import ai.tuna.fusion.executor.driver.podpool.impl.DefaultPodPoolConnectorFactory;
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
    public FunctionPodManager functionPodManager(PodPoolResources podPoolResources) {
        return new DefaulltFunctionPodManager(podPoolResources, new DefaultPodPoolConnectorFactory(podPoolResources));
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public DefaultAgentResources agentResources(KubernetesClient kubernetesClient) {
        return new DefaultAgentResources(kubernetesClient);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public DefaultPodPoolResources podPoolResources(KubernetesClient kubernetesClient) {
        return new DefaultPodPoolResources(kubernetesClient);
    }

}
