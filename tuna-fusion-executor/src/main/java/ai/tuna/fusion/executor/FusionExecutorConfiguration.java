package ai.tuna.fusion.executor;

import ai.tuna.fusion.executor.driver.podpool.FunctionPodManager;
import ai.tuna.fusion.executor.driver.podpool.impl.DefaultFunctionPodManager;
import ai.tuna.fusion.executor.driver.podpool.impl.DefaultPodPoolConnectorFactory;
import ai.tuna.fusion.metadata.informer.ConfigSourceResources;
import ai.tuna.fusion.metadata.informer.PodPoolResources;
import ai.tuna.fusion.metadata.informer.impl.DefaultAgentResources;
import ai.tuna.fusion.metadata.informer.impl.DefaultConfigSourceResources;
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
        return new DefaultFunctionPodManager(new DefaultPodPoolConnectorFactory(podPoolResources), podPoolResources);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public DefaultAgentResources agentResources(KubernetesClient kubernetesClient) {
        return new DefaultAgentResources(kubernetesClient);
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    public DefaultPodPoolResources podPoolResources(KubernetesClient kubernetesClient) {
        return new DefaultPodPoolResources(kubernetesClient);
    }
    @Bean
    DefaultConfigSourceResources defaultConfigSourceResources(KubernetesClient kubernetesClient) {
        return new DefaultConfigSourceResources(kubernetesClient);
    }

}
