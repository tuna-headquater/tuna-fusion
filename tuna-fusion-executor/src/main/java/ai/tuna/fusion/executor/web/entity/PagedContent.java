package ai.tuna.fusion.executor.web.entity;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * @author robinqu
 */
@Getter
@Builder(toBuilder = true)
public class PagedContent<T> {
    private List<T> items;
}
