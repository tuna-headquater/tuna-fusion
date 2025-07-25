package ai.tuna.fusion.gitops.server.spring.config;

import ai.tuna.fusion.gitops.server.git.CustomReceivePackFactory;
import ai.tuna.fusion.gitops.server.git.CustomRepositoryResolver;
import ai.tuna.fusion.gitops.server.git.pipeline.SourceArchiveHandler;
import ai.tuna.fusion.gitops.server.git.pipeline.impl.FolderSourceArchiveHandler;
import ai.tuna.fusion.gitops.server.git.pipeline.impl.LocalHttpZipArchiveSourceHandler;
import ai.tuna.fusion.gitops.server.spring.property.GitOpsServerProperties;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.http.server.GitServlet;
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
        return new CustomReceivePackFactory(kubernetesClient, sourceArchiveHandler(properties));
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
        // 启用 push 支持
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

//    @Bean
//    public S3Client s3Client(GitOpsServerProperties properties) {
//        return S3Client.builder()
//                .region(Region.AWS_GLOBAL)
//                .endpointProvider(endpointParams -> CompletableFuture.completedFuture(Endpoint.builder()
//                        .url(URI.create(properties.getS3Properties().getEndpointUrl() + "/" + Optional.ofNullable(endpointParams.bucket()).orElse("")))
//                        .build()))
//                .credentialsProvider(()-> AwsBasicCredentials.create(properties.getS3Properties().getAccessKey(), properties.getS3Properties().getAccessSecret()))
//                .build();
//    }

}
