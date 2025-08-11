package ai.tuna.fusion.executor;

import ai.tuna.fusion.metadata.informer.impl.InformerProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author robinqu
 */
@ConfigurationProperties(prefix = "executor")
@Data
public class ExecutorProperties {
    private InformerProperties informers;
}
