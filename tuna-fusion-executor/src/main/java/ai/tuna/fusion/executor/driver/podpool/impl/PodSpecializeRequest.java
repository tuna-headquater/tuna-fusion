package ai.tuna.fusion.executor.driver.podpool.impl;

import lombok.Builder;
import lombok.Data;

/**
 * @author robinqu
 */
@Data
@Builder
public class PodSpecializeRequest {
    private String filepath;
    private String functionName;
}
