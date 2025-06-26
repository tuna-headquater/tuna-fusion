package ai.tuna.fusion.gitops.server.spring.config;

import ai.tuna.fusion.gitops.server.git.CustomReceivePackFactory;
import ai.tuna.fusion.gitops.server.git.CustomRepositoryResolver;
import ai.tuna.fusion.gitops.server.spring.servlet.GitRequestContextFilter;
import ai.tuna.fusion.gitops.server.spring.properties.GitOpsServerProperties;
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
public class GitServerConfig {

    @Bean
    public GitServlet gitServlet(GitOpsServerProperties properties, GitRequestContextFilter gitRequestContextFilter) {
        GitServlet gitServlet = new GitServlet();
        gitServlet.setRepositoryResolver(new CustomRepositoryResolver(properties.getReposRootPath()));
        // 启用 push 支持
        gitServlet.setReceivePackFactory(new CustomReceivePackFactory());
        // 添加过滤器
        gitServlet.addReceivePackFilter(gitRequestContextFilter);
        gitServlet.addUploadPackFilter(gitRequestContextFilter);
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
