package com.hollow.build.auth.sso;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "com.hollow.sso")
public class SsoClientProperties {
    private boolean enabled;
    private String publicBaseUrl;
    private String internalBaseUrl;
    private String issuer;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private boolean cookieSecure;

    @PostConstruct
    void validate() {
        if (enabled && (!StringUtils.hasText(publicBaseUrl)
                || !StringUtils.hasText(internalBaseUrl)
                || !StringUtils.hasText(issuer)
                || !StringUtils.hasText(clientId)
                || !StringUtils.hasText(clientSecret)
                || !StringUtils.hasText(redirectUri))) {
            throw new IllegalStateException("SSO 已启用，但客户端配置不完整");
        }
        if (enabled) {
            publicBaseUrl = stripTrailingSlash(publicBaseUrl);
            internalBaseUrl = stripTrailingSlash(internalBaseUrl);
        }
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
