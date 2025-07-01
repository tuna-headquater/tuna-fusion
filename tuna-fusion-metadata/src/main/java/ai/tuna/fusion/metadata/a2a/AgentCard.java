package ai.tuna.fusion.metadata.a2a;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.EnumNamingStrategies;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.EnumNaming;
import io.fabric8.generator.annotation.Required;
import io.fabric8.generator.annotation.Size;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * @author robinqu
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AgentCard {
    @Data
    public static class Capabilities {
        private boolean streaming;
        private boolean pushNotifications;
        private boolean stateTransitionHistory;
    }

    @Data
    public static class Authentication {
        private List<String> schemes;
        private String credentials;
    }

    @Data
    public static class Provider {
        @Required
        private String organization;
        @Required
        private String url;
    }

    @Data
    public static class Skill {
        @Required
        private String id;
        @Required
        private String name;
        @Required
        private String description;
        @Required
        private List<String> tags;
        private List<String> examples;
        private List<String> inputModes;
        private List<String> outputModes;
    }

    @Data
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SecurityScheme.APIKeySecurityScheme.class, name = "apiKey"),
            @JsonSubTypes.Type(value = SecurityScheme.HTTPAuthSecurityScheme.class, name = "http"),
            @JsonSubTypes.Type(value = SecurityScheme.OAuth2SecurityScheme.class, name = "oauth2"),
            @JsonSubTypes.Type(value = SecurityScheme.OpenIdConnectSecurityScheme.class, name = "openIdConnect"),
    })
    public static abstract class SecurityScheme {
        @Required
        private String type;
        private String description;

        @EqualsAndHashCode(callSuper = true)
        @Data
        public static class APIKeySecurityScheme extends SecurityScheme {
            @EnumNaming(EnumNamingStrategies.LowerCamelCaseStrategy.class)
            public enum In {
                Cookie,
                Header,
                Query
            }
            @Required
            private In in;
            @Required
            private String name;
        }

        @Data
        @EqualsAndHashCode(callSuper = true)
        public static class HTTPAuthSecurityScheme extends SecurityScheme {
            private String bearerFormat;
            private String scheme;
        }

        @Data
        @EqualsAndHashCode(callSuper = true)
        public static class OAuth2SecurityScheme extends SecurityScheme {
            @Data
            public static class OAuthFlows {
                @Data
                public static class AuthorizationCodeOAuthFlow {
                    @Required
                    private String authorizationUrl;
                    private String refreshUrl;
                    @Required
                    private Map<String, String> scopes;
                    @Required
                    private String tokenUrl;
                }

                @Data
                public static class ImplicitOAuthFlow {
                    @Required
                    private String authorizationUrl;
                    private String refreshUrl;
                    @Required
                    private Map<String, String> scopes;
                }

                @Data
                public static class PasswordOAuthFlow {
                    private String refreshUrl;
                    @Required
                    private Map<String, String> scopes;
                    @Required
                    private String tokenUrl;
                }

                private AuthorizationCodeOAuthFlow authorizationCode;
                private ImplicitOAuthFlow implicit;
                private PasswordOAuthFlow password;

            }

            @Required
            private OAuthFlows flows;
        }

        @Data
        public static class OpenIdConnectSecurityScheme {
            private String openIdConnectUrl;
        }

    }

    @Required
    private String name;

    @Required
    private String description;

    /**
     * Definition of this field differs from that in A2A specification. As agent URL is dynamically generated, the complete URL cannot be determined at the time of writing AgentDeploymentCR.
     * So it's designed to contain a relative URL template which can be used to generate the complete URL.
     * Possible variables: agentCatalogName, agentDeploymentName, agentEnvironmentName, namespace
     */
    @Required
    @Builder.Default
    private String url = "/{namespace}/{agentCatalogueName}/{agentDeploymentName}";

    private String iconUrl;

    @Required
    private String version;

    private String documentationUrl;

    @Required
    private Provider provider;

    @Required
    private Capabilities capabilities;

    @Required
    @Size(min = 1)
    private List<String> defaultInputModes;

    @Required
    @Size(min = 1)
    private List<String> defaultOutputModes;

    @Required
    @Size(min = 1)
    private List<Skill> skills;

    private Map<String, SecurityScheme> securitySchemes;

    private List<Map<String, List<String>>> security;

    private boolean supportsAuthenticatedExtendedCard;


}
