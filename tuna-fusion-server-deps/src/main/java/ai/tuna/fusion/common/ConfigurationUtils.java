package ai.tuna.fusion.common;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.env.Environment;

import java.util.Optional;

/**
 * @author robinqu
 */
public class ConfigurationUtils implements ApplicationListener<ContextRefreshedEvent> {

    private static Environment environment;

    @Override
    public void onApplicationEvent(@NotNull ContextRefreshedEvent event) {
        environment = event.getApplicationContext().getEnvironment();
    }

    public static String getStaticValue(String key, String defaultValue) {
        return getStaticValue(key)
                .orElse(defaultValue);
    }

    public static Optional<String> getStaticValue(String key) {
        if (environment != null) {
            return Optional.ofNullable(environment.getProperty(key));
        }
        return Optional.ofNullable(System.getProperty(key))
                .or(()-> Optional.ofNullable(System.getenv(key)));
    }
}
