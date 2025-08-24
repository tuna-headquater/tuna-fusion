package ai.tuna.fusion.gitops.server.spring.config;

import ai.tuna.fusion.gitops.server.git.CustomReceivePackFactory;
import ai.tuna.fusion.gitops.server.git.CustomRepositoryResolver;
import ai.tuna.fusion.gitops.server.git.pipeline.SourceArchiveHandler;
import ai.tuna.fusion.gitops.server.git.pipeline.impl.FolderSourceArchiveHandler;
import ai.tuna.fusion.gitops.server.git.pipeline.impl.LocalHttpZipArchiveSourceHandler;
import ai.tuna.fusion.gitops.server.spring.property.GitOpsServerProperties;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.transport.resolver.FileResolver;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author robinqu
 */
@Configuration
@Slf4j
public class GitOpsServerConfig {

    @Bean
    public CustomReceivePackFactory customReceivePackFactory(KubernetesClient kubernetesClient, GitOpsServerProperties properties) {
        return new CustomReceivePackFactory(
                kubernetesClient,
                sourceArchiveHandler(properties),
                properties.getWatchedNamespaces()
        );
    }

    private SourceArchiveHandler sourceArchiveHandler(GitOpsServerProperties properties) {
        return switch (properties.getSourceArchiveHandler().getType()) {
            case ZipArchiveOnLocalHttpServer -> new LocalHttpZipArchiveSourceHandler(properties.getSourceArchiveHandler().getZipArchiveOnLocalHttpServer());
            case FolderOnFilesystem -> new FolderSourceArchiveHandler(properties.getSourceArchiveHandler().getFolderOnFilesystem().getLocalSourceArchiveRootPath());
            case null, default -> throw new IllegalArgumentException("Invalid SourceArchiveHandler type");
        };
    }

    @Bean
    public GitServlet gitServlet(
            GitOpsServerProperties properties,
            CustomReceivePackFactory customReceivePackFactory,
            KubernetesClient kubernetesClient
            ) {
        GitServlet gitServlet = new GitServlet();
        gitServlet.setRepositoryResolver(new CustomRepositoryResolver(properties.getReposRootPath()));
//        gitServlet.setRepositoryResolver(new FileResolver<>(properties.getReposRootPath().toFile(), true));
        gitServlet.setReceivePackFactory(customReceivePackFactory);
        return gitServlet;
    }

    @Bean
    public ServletRegistrationBean<GitServlet> gitServletRegistration(GitServlet gitServlet) {
        ServletRegistrationBean<GitServlet> registration = new ServletRegistrationBean<>(
                gitServlet, "/repositories/*");
        registration.setLoadOnStartup(1);
        return registration;
    }
}
