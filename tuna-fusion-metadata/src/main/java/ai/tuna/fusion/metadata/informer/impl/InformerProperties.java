package ai.tuna.fusion.metadata.informer.impl;

import lombok.Data;

import java.util.Set;

/**
 * @author robinqu
 */
@Data
public class InformerProperties {
    private Boolean clusterScoped;
    private Set<String> namespaces;
    private Long informerListLimit;
}
