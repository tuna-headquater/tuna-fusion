package ai.tuna.fusion.gitops.server.spring.servlet;

import ai.tuna.fusion.metadata.crd.AgentCatalogue;
import ai.tuna.fusion.metadata.crd.AgentDeployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.*;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;
import java.util.Objects;

/**
 * @author robinqu
 */
@Component
public class GitRequestContextFilter implements Filter {

    record URLParams (String namespace, String agentCatalogueName, String agentDeploymentName) {}

    public static String AgentCatalogueName = "agent-catalogue";
    public static String AgentDeploymentName = "agent-deployment";

    private final KubernetesClient kubernetesClient;


    public GitRequestContextFilter(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        var params = parseUrlParams(request);
        var agentCatalogue = kubernetesClient.resources(AgentCatalogue.class)
                .inNamespace(params.namespace)
                .withName(params.agentCatalogueName)
                .get();
        if (Objects.isNull(agentCatalogue)) {
            throw new ServletException("AgentCatalogue (name=%s,ns=%s) doesn't exist.".formatted(params.agentCatalogueName, params.namespace));
        }
        var agentDeployment = kubernetesClient.resources(AgentDeployment.class)
                .inNamespace(params.namespace)
                .withName(params.agentDeploymentName)
                .get();
        if (Objects.isNull(agentDeployment)) {
            throw new ServletException("AgentDeployment (name=%s,ns=%s) doesn't exist.".formatted(params.agentDeploymentName, params.namespace));
        }
        RequestContextHolder.currentRequestAttributes().setAttribute(AgentCatalogueName, agentCatalogue, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.currentRequestAttributes().setAttribute(AgentDeploymentName, agentDeployment, RequestAttributes.SCOPE_REQUEST);
    }

    /**
     * URL patterns： /repositories/<namespace>/<agent-catalogue-name>/<agent-deployment-name>.git
     * Some constrains:
     * 1. URL should always end with `.git`, or exception should be thrown.
     * 2. namespace, agentCatalogueName and agentDeploymentName should come together, or exception should be thrown.
     * 3. URL is always prefixed with `/repositories/`, or exception should be thrown.
     * @param request Original request
     * @return parsed data
     */
    private URLParams parseUrlParams(ServletRequest request) throws ServletException {
        String requestURI = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();
        
        // Validate URL starts with /repositories/
        if (!requestURI.startsWith("/repositories/")) {
            throw new ServletException("Invalid URL format. Expected pattern: /repositories/<namespace>/<agent-catalogue-name>/<agent-deployment-name>.git");
        }

        String[] segments = requestURI.split("/");
        
        // Ensure we have at least the required parts: "", "repositories", namespace, catalogue, deployment.git
        if (segments.length < 5) {
            throw new ServletException("Invalid URL format. Expected pattern: /repositories/<namespace>/<agent-catalogue-name>/<agent-deployment-name>.git");
        }
        
        String namespace = segments[2];
        String agentCatalogueName = segments[3];
        String gitSegment = segments[4];

        // Ensure URL ends with .git
        if (!gitSegment.endsWith(".git")) {
            throw new ServletException("URL must end with .git extension");
        }

        String agentDeploymentName = gitSegment.substring(0, gitSegment.length() - 4);

        return new URLParams(namespace, agentCatalogueName, agentDeploymentName);
    }
}
