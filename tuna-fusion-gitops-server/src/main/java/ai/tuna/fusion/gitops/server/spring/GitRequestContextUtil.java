package ai.tuna.fusion.gitops.server.spring;

import ai.tuna.fusion.metadata.crd.agent.AgentCatalogue;
import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.ServletRequest;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Objects;
import java.util.Optional;

/**
 * @author robinqu
 */
public class GitRequestContextUtil {

    public record URLParams (String namespace, String agentCatalogueName, String agentDeploymentName) {}

    public static String AgentEnvironmentName = "ai.tuna.fusion.gitops.server.agent-environment";
    public static String AgentCatalogueName = "ai.tuna.fusion.gitops.server.agent-catalogue";
    public static String AgentDeploymentName = "ai.tuna.fusion.gitops.server.agent-deployment";

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

    public static Optional<AgentCatalogue> getAgentCatalogue() {
        return Optional.ofNullable(
                (AgentCatalogue) RequestContextHolder.currentRequestAttributes().getAttribute(AgentCatalogueName, RequestAttributes.SCOPE_REQUEST)
        );
    }

    public static void initializeRequestAttributes(
            KubernetesClient kubernetesClient,
            ServletRequest request) throws ServiceNotEnabledException {
        var params = parseUrlParams(request);
        var agentCatalogue = kubernetesClient.resources(AgentCatalogue.class)
                .inNamespace(params.namespace)
                .withName(params.agentCatalogueName)
                .get();
        if (Objects.isNull(agentCatalogue)) {
            throw new ServiceNotEnabledException("AgentCatalogue (name=%s,ns=%s) doesn't exist.".formatted(params.agentCatalogueName, params.namespace));
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
        RequestContextHolder.currentRequestAttributes().setAttribute(AgentCatalogueName, agentCatalogue, RequestAttributes.SCOPE_REQUEST);
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
            throw new ServiceNotEnabledException("Invalid URL format. Expected pattern: /repositories/<namespace>/<agent-catalogue-name>/<agent-deployment-name>.git");
        }

        String[] segments = requestUri.split("/");

        // Ensure we have at least the required parts: "", "repositories", namespace, catalogue, deployment.git
        if (segments.length < 5) {
            receivePack.ifPresent(rp -> rp.sendError("Invalid URL format. Expected pattern: /repositories/<namespace>/<agent-catalogue-name>/<agent-deployment-name>.git/*"));
            throw new ServiceNotEnabledException("Invalid URL format. Expected pattern: /repositories/<namespace>/<agent-catalogue-name>/<agent-deployment-name>.git/*");
        }

        String namespace = segments[2];
        String agentCatalogueName = segments[3];
        String gitSegment = segments[4];

        // Ensure URL ends with .git
        if (!gitSegment.endsWith(".git")) {
            receivePack.ifPresent(rp -> rp.sendError("URL must end with .git extension"));
            throw new ServiceNotEnabledException("URL must end with .git extension");
        }

        String agentDeploymentName = gitSegment.substring(0, gitSegment.length() - 4);

        return new URLParams(namespace, agentCatalogueName, agentDeploymentName);
    }

}
