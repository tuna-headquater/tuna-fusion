package ai.tuna.fusion.executor.web.entity;

import ai.tuna.fusion.executor.driver.podpool.CountedPodAccess;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author robinqu
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PodFunctionListItem {
    private PodFunction podFunction;
    private List<CountedPodAccess> podAccesses;
}
