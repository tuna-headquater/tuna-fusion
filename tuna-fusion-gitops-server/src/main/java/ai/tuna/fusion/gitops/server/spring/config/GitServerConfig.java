package ai.tuna.fusion.gitops.server.spring.config;

import ai.tuna.fusion.gitops.server.git.BuildPipelinePreReceiveHook;
import ai.tuna.fusion.gitops.server.git.CustomReceivePackFactory;
import ai.tuna.fusion.gitops.server.git.CustomRepositoryResolver;
import ai.tuna.fusion.gitops.server.spring.properties.GitOpsServerProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.http.server.GitServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * @author robinqu
 */
@Configuration
@Slf4j
public class GitServerConfig {

    @Bean
    public BuildPipelinePreReceiveHook buildPipelinePreReceiveHook(GitOpsServerProperties properties) {
        return new BuildPipelinePreReceiveHook(properties.getDefaultBranch());
    }

    @Bean
    public GitServlet gitServlet(BuildPipelinePreReceiveHook buildPipelinePreReceiveHook, GitOpsServerProperties properties) {
        GitServlet gitServlet = new GitServlet();
        gitServlet.setRepositoryResolver(new CustomRepositoryResolver(properties.getReposRootPath()));
        // 启用 push 支持
        gitServlet.setReceivePackFactory(new CustomReceivePackFactory(buildPipelinePreReceiveHook));

        // 添加访问日志过滤器
        gitServlet.addUploadPackFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res,
                                 FilterChain chain) throws IOException, ServletException {
                HttpServletRequest httpReq = (HttpServletRequest) req;
                String requestURI = httpReq.getRequestURI();
                String method = httpReq.getMethod();
                String user = httpReq.getRemoteUser() != null ?
                        httpReq.getRemoteUser() : "anonymous";
                log.info("Git access: {} {} by {}", method, requestURI, user);
                chain.doFilter(req, res);
            }
        });

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
