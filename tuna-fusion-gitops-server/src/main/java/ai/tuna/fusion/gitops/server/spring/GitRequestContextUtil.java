package ai.tuna.fusion.gitops.server.spring;

import ai.tuna.fusion.gitops.server.spring.servlet.GitRequestContextFilter;
import ai.tuna.fusion.metadata.crd.AgentCatalogue;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Optional;

/**
 * @author robinqu
 */
public class GitRequestContextUtil {

    public static Optional<AgentDeployment> getAgentDeployment() {
        return Optional.ofNullable(
                (AgentDeployment) RequestContextHolder.currentRequestAttributes().getAttribute(GitRequestContextFilter.AgentCatalogueName, RequestAttributes.SCOPE_REQUEST)
        );
    }

    public static Optional<AgentCatalogue> getAgentCatalogue() {
        return Optional.ofNullable(
                (AgentCatalogue) RequestContextHolder.currentRequestAttributes().getAttribute(GitRequestContextFilter.AgentCatalogueName, RequestAttributes.SCOPE_REQUEST)
        );
    }

}
