package ai.tuna.fusion.metadata.crd.podpool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * @author robinqu
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PodPoolStatus {
    private String deploymentName;
    private Map<String, String> genericPodSelectors;
    private int availablePods;
    private String headlessServiceName;
}
