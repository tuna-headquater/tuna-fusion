package ai.tuna.fusion.metadata.crd.podpool;

import lombok.Data;

import java.util.Map;

/**
 * @author robinqu
 */
@Data
public class PodPoolStatus {
    private String deploymentName;
    private Map<String, String> genericPodSelectors;
    private int availablePods;
    private int orphanPods;
    private String headlessServiceName;
}
