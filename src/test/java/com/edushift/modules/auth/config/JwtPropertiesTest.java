package com.edushift.modules.auth.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    @DisplayName("sensible defaults applied when no properties bound")
    void defaults() {
        var p = new JwtProperties();
        assertThat(p.getIssuer()).isEqualTo("edushift");
        assertThat(p.getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(p.getRefreshTokenTtl()).isEqualTo(Duration.ofDays(7));
        assertThat(p.getHeader()).isEqualTo("Authorization");
        assertThat(p.getTokenPrefix()).isEqualTo("Bearer ");
        assertThat(p.getAudience()).isNull();
        assertThat(p.getSecret()).isNull();
    }

    @Test
    @DisplayName("setters round-trip values")
    void setters() {
        var p = new JwtProperties();
        p.setSecret("0123456789-abcdef-verylongsecret");
        p.setIssuer("edushift-test");
        p.setAudience("front");
        p.setAccessTokenTtl(Duration.ofMinutes(30));
        p.setRefreshTokenTtl(Duration.ofDays(30));
        p.setHeader("X-Auth");
        p.setTokenPrefix("Token ");
        assertThat(p.getSecret()).isEqualTo("0123456789-abcdef-verylongsecret");
        assertThat(p.getIssuer()).isEqualTo("edushift-test");
        assertThat(p.getAudience()).isEqualTo("front");
        assertThat(p.getAccessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(p.getRefreshTokenTtl()).isEqualTo(Duration.ofDays(30));
        assertThat(p.getHeader()).isEqualTo("X-Auth");
        assertThat(p.getTokenPrefix()).isEqualTo("Token ");
    }
}