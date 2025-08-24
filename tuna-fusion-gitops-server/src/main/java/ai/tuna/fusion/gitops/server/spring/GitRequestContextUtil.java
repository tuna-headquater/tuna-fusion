package ai.tuna.fusion.gitops.server.spring;

import ai.tuna.fusion.metadata.crd.agent.AgentDeployment;
import ai.tuna.fusion.metadata.crd.agent.AgentEnvironment;
import ai.tuna.fusion.metadata.crd.podpool.PodFunction;
import ai.tuna.fusion.metadata.informer.impl.ResourceInformersWrapper;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.ServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.http.server.ServletUtils;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author robinqu
 */
@Slf4j
public class GitRequestContextUtil {

    public record URLParams (String namespace, String agentDeploymentName) {}

    public static Optional<AgentEnvironment> getAgentEnvironment() {
        return Optional.ofNullable(
                (AgentEnvironment) RequestContextHolder.currentRequestAttributes().getAttribute(AgentEnvironment.class.getName(), RequestAttributes.SCOPE_REQUEST)
        );
    }

    public static Optional<AgentDeployment> getAgentDeployment() {
        return Optional.ofNullable(
                (AgentDeployment) RequestContextHolder.currentRequestAttributes().getAttribute(AgentDeployment.class.getName(), RequestAttributes.SCOPE_REQUEST)
        );
    }

    public static Optional<PodFunction> getPodFunction() {
        return Optional.ofNullable(
                (PodFunction) RequestContextHolder.currentRequestAttributes().getAttribute(PodFunction.class.getName(), RequestAttributes.SCOPE_REQUEST)
        );
    }

    public static Optional<URLParams> getGitURLParams() {
        return Optional.ofNullable((URLParams) RequestContextHolder.currentRequestAttributes().getAttribute(URLParams.class.getName(), RequestAttributes.SCOPE_REQUEST));
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
        if (Objects.isNull(agentEnvironment)) {
            throw new ServiceNotEnabledException("AgentEnvironment (name=%s,ns=%s) doesn't exist.".formatted(agentDeployment.getSpec().getEnvironmentName(), params.namespace));
        }

        var podFunction = kubernetesClient.resources(PodFunction.class)
                .inNamespace(agentDeployment.getMetadata().getNamespace())
                .withName(agentDeployment.getStatus().getFunction().getFunctionName())
                .get();
        if (Objects.isNull(podFunction)) {
            throw new ServiceNotEnabledException("PodFunction (name=%s,ns=%s) doesn't exist.".formatted(agentDeployment.getStatus().getFunction().getFunctionName(), agentDeployment.getMetadata().getNamespace()));
        }

        RequestContextHolder.currentRequestAttributes().setAttribute(AgentDeployment.class.getName(), agentDeployment, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.currentRequestAttributes().setAttribute(AgentEnvironment.class.getName(), agentEnvironment, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.currentRequestAttributes().setAttribute(URLParams.class.getName(), params, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.currentRequestAttributes().setAttribute(PodFunction.class.getName(), podFunction, RequestAttributes.SCOPE_REQUEST);

    }

    public static URLParams parseUrlParams(ServletRequest request) throws ServiceNotEnabledException {
        String requestUri = ((jakarta.servlet.http.HttpServletRequest) request).getRequestURI();

        if (requestUri.endsWith("/")) {
            requestUri = requestUri.substring(0, requestUri.length() - 1);
        }

        var receivePack = Optional.ofNullable(request.getAttribute(ServletUtils.ATTRIBUTE_HANDLER))
                .map(h -> (ReceivePack) h);

        String pathTemplate = "/repositories/namespaces/{namespace}/agents/{agentDeploymentName}.git/*";
        // Validate URL starts with /repositories/
        if (!requestUri.startsWith("/repositories/")) {
            throw new ServiceNotEnabledException("Invalid URL format. Expected pattern: " + pathTemplate);
        }

        // Split the request URI into segments
        String[] segments = requestUri.split("/");

        // Minimum required segments:
        // "", "repositories", "namespaces", {namespace}, "agents", {agentDeploymentName}.git
        // This is 6 segments
        if (segments.length < 6) {
            receivePack.ifPresent(rp -> rp.sendError("Invalid URL format. Expected pattern: " + pathTemplate));
            throw new ServiceNotEnabledException("Invalid URL format. Expected pattern: " + pathTemplate);
        }

        // Check that segments at index 2 and 4 are exactly "namespaces" and "agents"
        if (!"namespaces".equals(segments[2]) || !"agents".equals(segments[4])) {
            receivePack.ifPresent(rp -> rp.sendError("Invalid URL format. Expected pattern: " + pathTemplate));
            throw new ServiceNotEnabledException("Invalid URL format. Expected pattern: " + pathTemplate);
        }

        String namespace = segments[3];

        // Handle subPath and agentDeploymentName
        String agentDeploymentName = "";
        
        // Extract agentDeploymentName from segments[5] by removing .git extension if present
        String potentialAgentName = segments[5];
        if (potentialAgentName.endsWith(".git")) {
            agentDeploymentName = potentialAgentName.substring(0, potentialAgentName.length() - 4); // Remove .git
        } else {
            receivePack.ifPresent(rp -> rp.sendError("URL must end with .git extension"));
            throw new ServiceNotEnabledException("URL must end with .git extension");
        }
        return new URLParams(namespace, agentDeploymentName);
    }


}
