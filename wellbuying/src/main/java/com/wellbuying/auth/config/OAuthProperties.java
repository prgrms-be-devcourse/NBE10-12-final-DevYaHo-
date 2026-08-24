package com.wellbuying.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "oauth")
public record OAuthProperties(
        String successRedirectUri,
        String failureRedirectUri
) {
}
