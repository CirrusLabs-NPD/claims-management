package com.claire.claims.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Cors cors = new Cors();

    public Jwt getJwt() { return jwt; }
    public Cors getCors() { return cors; }

    public static class Jwt {
        /** HMAC signing secret. Must be at least 32 bytes for HS256. */
        private String secret = "change-me-in-production-this-is-a-demo-secret-key-32b";
        private String issuer = "claim-management";
        private long ttlMinutes = 480;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public long getTtlMinutes() { return ttlMinutes; }
        public void setTtlMinutes(long ttlMinutes) { this.ttlMinutes = ttlMinutes; }
    }

    public static class Cors {
        /** Comma-separated origins. Empty when nginx proxies /api (single origin). */
        private String allowedOrigins = "http://localhost:5173";

        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }
    }
}
