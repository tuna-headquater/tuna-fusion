package ai.tuna.fusion.executor.driver.podpool;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author robinqu
 */
@Getter
@SuperBuilder(toBuilder = true)
public class CountedPodAccess implements AutoCloseable {
    private final AtomicInteger usageCount;
    private final int maxUsageCount;
    private final FunctionPodManager functionPodManager;
    private final PodAccess podAccess;
    @Override
    public void close() throws Exception {
        functionPodManager.disposeAccess(this);
    }

    public String getUri() {
        return podAccess.getUri();
    }
}
