package ai.tuna.fusion.gitops.server.spring;

import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.informer.impl.ResourceInformersWrapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.ServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Strings;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author robinqu
 */
@Slf4j
public class GitRequestContextUtil {

    public record URLParams (String namespace, String agentDeploymentName) {}

    public static final String AgentEnvironmentName = "ai.tuna.fusion.gitops.server.agent-environment";
    public static final String AgentDeploymentName = "ai.tuna.fusion.gitops.server.agent-deployment";

    public static Optional<AgentEnvironment> getAgentEnvironment() {
        return Optional.ofNullable(
                (AgentEnvironment) RequestContextHolder.currentRequestAttributes().getAttribute(AgentEnvironmentName, RequestAttributes.SCOPE_REQUEST)
        );
    }

    public static Optional<AgentDeployment> getAgentDeployment() {
        return Optional.ofNullable(
                (AgentDeployment) RequestContextHolder.currentRequestAttributes().getAttribute(AgentDeploymentName, RequestAttributes.SCOPE_REQUEST)
        );
    }

    public static void initializeRequestAttributes(
            KubernetesClient kubernetesClient,
            ServletRequest request,
            Set<String> watchedNamespaces
            ) throws ServiceNotEnabledException {
        var params = parseUrlParams(request);
        if (watchedNamespaces.size()==1 && watchedNamespaces.contains(ResourceInformersWrapper.ALL_NAMESPACE_IDENTIFIER)) {
            log.info("GitOps server is accepting push in all namespaces.");
        } else {
            if(!watchedNamespaces.contains(params.namespace())) {
                throw new ServiceNotEnabledException("Namespace %s is not watched by GitOps server".formatted(params.namespace));
            }
        }
        var agentDeployment = kubernetesClient.resources(AgentDeployment.class)
                .inNamespace(params.namespace)
                .withName(params.agentDeploymentName)
                .get();
        if (Objects.isNull(agentDeployment)) {
            throw new ServiceNotEnabledException("AgentDeployment (name=%s,ns=%s) doesn't exist.".formatted(params.agentDeploymentName, params.namespace));
        }
        var agentEnvironment = kubernetesClient.resources(AgentEnvironment.class)
                .inNamespace(params.namespace)
                .withName(agentDeployment.getSpec().getEnvironmentName())
                .get();
        RequestContextHolder.currentRequestAttributes().setAttribute(AgentDeploymentName, agentDeployment, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.currentRequestAttributes().setAttribute(AgentEnvironmentName, agentEnvironment, RequestAttributes.SCOPE_REQUEST);
    }

    public static URLParams parseUrlParams(ServletRequest request) throws ServiceNotEnabledException {
        String requestUri = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();

        if (requestUri.endsWith("/")) {
            requestUri = requestUri.substring(0, requestUri.length() - 1);
        }

        var receivePack = Optional.ofNullable(request.getAttribute(ServletUtils.ATTRIBUTE_HANDLER))
                .map(h -> (ReceivePack) h);

        // Validate URL starts with /repositories/
        if (!requestUri.startsWith("/repositories/")) {
            throw new ServiceNotEnabledException("Invalid URL format. Expected pattern: /repositories/<namespace>/<agent-deployment-name>.git");
        }

        String[] segments = requestUri.split("/");

        // Ensure we have at least the required parts: "", "repositories", namespace, deployment.git
        if (segments.length < 4) {
            receivePack.ifPresent(rp -> rp.sendError("Invalid URL format. Expected pattern: /repositories/<namespace>/<agent-deployment-name>.git/*"));
            throw new ServiceNotEnabledException("Invalid URL format. Expected pattern: /repositories/<namespace>/<agent-deployment-name>.git/*");
        }

        String namespace = segments[2];
        String gitSegment = segments[3];

        // Ensure URL ends with .git
        if (!gitSegment.endsWith(".git")) {
            receivePack.ifPresent(rp -> rp.sendError("URL must end with .git extension"));
            throw new ServiceNotEnabledException("URL must end with .git extension");
        }

        String agentDeploymentName = gitSegment.substring(0, gitSegment.length() - 4);

        return new URLParams(namespace, agentDeploymentName);
    }

}
