package com.example.mcp.config;

import com.example.mcp.resource.ResourceLimiter;
import com.example.mcp.security.SecurityContext;

/**
 * Configuration factory for creating properly configured tools
 */
public class ToolConfiguration {

    public static class Environment {
        public static final String DEVELOPMENT = "development";
        public static final String PRODUCTION = "production";
        public static final String TESTING = "testing";
    }

    /**
     * Create security context based on environment
     */
    public static SecurityContext createSecurityContext(String environment) {
        return switch (environment) {
            case Environment.DEVELOPMENT -> SecurityContext.createDevelopmentContext();
            case Environment.PRODUCTION -> SecurityContext.createProductionContext(
                    System.getProperty("mcp.workdir", System.getProperty("user.dir"))
            );
            case Environment.TESTING -> createTestingSecurityContext();
            default -> SecurityContext.createDevelopmentContext();
        };
    }

    /**
     * Create resource limiter based on environment
     */
    public static ResourceLimiter createResourceLimiter(String environment) {
        return switch (environment) {
            case Environment.DEVELOPMENT -> ResourceLimiter.createDevelopmentLimiter();
            case Environment.PRODUCTION -> ResourceLimiter.createProductionLimiter();
            case Environment.TESTING -> createTestingResourceLimiter();
            default -> ResourceLimiter.createDevelopmentLimiter();
        };
    }

    private static SecurityContext createTestingSecurityContext() {
        return new SecurityContext.Builder()
                .addAllowedPath(System.getProperty("java.io.tmpdir"))
                .addAllowedPath("src/test/resources")
                .maxFileSize(5 * 1024 * 1024) // 5MB for testing
                .maxPathDepth(10)
                .build();
    }

    private static ResourceLimiter createTestingResourceLimiter() {
        return new ResourceLimiter.Builder()
                .maxConcurrentFileOps(5)
                .maxConcurrentNetworkOps(2)
                .maxConcurrentProcessOps(1)
                .globalRateLimit(50.0) // High rate for testing
                .maxMemoryUsage(50 * 1024 * 1024) // 50MB for testing
                .build();
    }
}
