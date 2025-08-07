package ai.tuna.fusion.common;

import java.util.Optional;

/**
 * @author robinqu
 */
public class ConfigurationUtils {
    public static String getStaticValue(String key, String defaultValue) {
        return getStaticValue(key)
                .orElse(defaultValue);
    }

    public static Optional<String> getStaticValue(String key) {
        return Optional.ofNullable(System.getProperty(key))
                .or(()-> Optional.ofNullable(System.getenv(key)));
    }



}
