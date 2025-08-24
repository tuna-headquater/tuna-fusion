package ai.tuna.fusion.kubernetes.operator.config;

import ai.tuna.fusion.common.ConfigurationUtils;
import io.javaoperatorsdk.operator.api.config.ConfigurationServiceOverrider;
import io.javaoperatorsdk.operator.api.config.LeaderElectionConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * @author robinqu
 */
@Configuration
public class OperatorConfig {

    @Bean
    @ConditionalOnProperty(name = "operator.leader-election.enabled", havingValue = "true")
    Consumer<ConfigurationServiceOverrider> configurationServiceOverrider(OperatorProperties operatorProperties) {
        String identity = Optional.ofNullable(operatorProperties.getLeaderElection().getIdentity())
                .orElseGet(() -> Optional.ofNullable(System.getenv("POD_NAME"))
                        .orElse("process-" + ProcessHandle.current().pid()));
        String namespace = Optional.ofNullable(operatorProperties.getLeaderElection().getNamespace())
                .orElseGet(()-> Optional.ofNullable(System.getenv("POD_NAMESPACE")).orElse("default"));
        return configurationServiceOverrider -> {
            configurationServiceOverrider.withLeaderElectionConfiguration(
                    new LeaderElectionConfiguration("leader-election-test", namespace, identity));
        };
    }

    @Bean
    ConfigurationUtils configurationUtils() {
        return new ConfigurationUtils();
    }

}
