
package com.example.mcp.resource;

import com.example.mcp.exception.ToolExecutionException;
import com.google.common.util.concurrent.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Resource limiter to prevent abuse and ensure system stability
 */
public class ResourceLimiter {
    private static final Logger logger = LoggerFactory.getLogger(ResourceLimiter.class);

    private final Semaphore fileOperations;
    private final Semaphore networkOperations;
    private final Semaphore processOperations;
    private final RateLimiter globalRateLimiter;
    private final RateLimiter searchRateLimiter;
    private final AtomicLong totalMemoryUsed;
    private final long maxMemoryUsage;
    private final Map<String, AtomicLong> operationCounters;
    private final Map<String, RateLimiter> perOperationLimiters;

    private ResourceLimiter(Builder builder) {
        this.fileOperations = new Semaphore(builder.maxConcurrentFileOps);
        this.networkOperations = new Semaphore(builder.maxConcurrentNetworkOps);
        this.processOperations = new Semaphore(builder.maxConcurrentProcessOps);
        this.globalRateLimiter = RateLimiter.create(builder.globalRateLimit);
        this.searchRateLimiter = RateLimiter.create(builder.searchRateLimit);
        this.totalMemoryUsed = new AtomicLong(0);
        this.maxMemoryUsage = builder.maxMemoryUsage;
        this.operationCounters = new ConcurrentHashMap<>();
        this.perOperationLimiters = new ConcurrentHashMap<>();

        initializeOperationLimiters(builder);
    }

    private void initializeOperationLimiters(Builder builder) {
        perOperationLimiters.put("file_read", RateLimiter.create(builder.fileReadRateLimit));
        perOperationLimiters.put("file_write", RateLimiter.create(builder.fileWriteRateLimit));
        perOperationLimiters.put("file_search", RateLimiter.create(builder.fileSearchRateLimit));
        perOperationLimiters.put("process_exec", RateLimiter.create(builder.processExecRateLimit));
        perOperationLimiters.put("ai_request", RateLimiter.create(builder.aiRequestRateLimit));
    }

    /**
     * Acquire permission for file operations
     */
    public ResourcePermit acquireFileOperation(String operation) throws ToolExecutionException {
        return acquireOperation(fileOperations, "file_" + operation, "file operation");
    }

    /**
     * Acquire permission for network operations
     */
    public ResourcePermit acquireNetworkOperation() throws ToolExecutionException {
        return acquireOperation(networkOperations, "network", "network operation");
    }

    /**
     * Acquire permission for process operations
     */
    public ResourcePermit acquireProcessOperation() throws ToolExecutionException {
        return acquireOperation(processOperations, "process_exec", "process operation");
    }

    /**
     * Acquire permission for search operations
     */
    public ResourcePermit acquireSearchOperation() throws ToolExecutionException {
        if (!searchRateLimiter.tryAcquire(1, TimeUnit.SECONDS)) {
            throw new ToolExecutionException("Search rate limit exceeded. Please wait before searching again.");
        }

        return acquireOperation(fileOperations, "file_search", "search operation");
    }

    /**
     * Acquire permission for AI operations
     */
    public ResourcePermit acquireAIOperation() throws ToolExecutionException {
        return acquireOperation(networkOperations, "ai_request", "AI operation");
    }

    private ResourcePermit acquireOperation(Semaphore semaphore, String operationType, String displayName)
            throws ToolExecutionException {

        // Check global rate limit
        if (!globalRateLimiter.tryAcquire(1, TimeUnit.SECONDS)) {
            throw new ToolExecutionException("Global rate limit exceeded. Please slow down requests.");
        }

        // Check operation-specific rate limit
        RateLimiter operationLimiter = perOperationLimiters.get(operationType);
        if (operationLimiter != null && !operationLimiter.tryAcquire(1, TimeUnit.SECONDS)) {
            throw new ToolExecutionException(displayName + " rate limit exceeded. Please wait.");
        }

        // Try to acquire semaphore permit
        try {
            if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                throw new ToolExecutionException("Too many concurrent " + displayName + "s. Please try again later.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolExecutionException("Operation interrupted while waiting for resources");
        }

        // Increment operation counter
        operationCounters.computeIfAbsent(operationType, k -> new AtomicLong(0)).incrementAndGet();

        logger.debug("Acquired resource permit for: {}", operationType);
        return new ResourcePermit(semaphore, operationType);
    }

    /**
     * Check and reserve memory usage
     */
    public void checkMemoryUsage(long additionalMemory) throws ToolExecutionException {
        long currentMemory = totalMemoryUsed.get();
        if (currentMemory + additionalMemory > maxMemoryUsage) {
            throw new ToolExecutionException(
                    String.format("Memory limit exceeded. Current: %d bytes, Requested: %d bytes, Max: %d bytes",
                            currentMemory, additionalMemory, maxMemoryUsage)
            );
        }
    }

    /**
     * Reserve memory for an operation
     */
    public MemoryReservation reserveMemory(long memoryAmount) throws ToolExecutionException {
        checkMemoryUsage(memoryAmount);

        long newTotal = totalMemoryUsed.addAndGet(memoryAmount);
        if (newTotal > maxMemoryUsage) {
            // Rollback the addition
            totalMemoryUsed.addAndGet(-memoryAmount);
            throw new ToolExecutionException("Memory limit exceeded during reservation");
        }

        logger.debug("Reserved {} bytes of memory. Total: {} bytes", memoryAmount, newTotal);
        return new MemoryReservation(memoryAmount);
    }

    /**
     * Get current resource usage statistics
     */
    public ResourceStats getStats() {
        return new ResourceStats(
                fileOperations.availablePermits(),
                networkOperations.availablePermits(),
                processOperations.availablePermits(),
                totalMemoryUsed.get(),
                maxMemoryUsage,
                Map.copyOf(operationCounters.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().get()
                        )))
        );
    }

    /**
     * Resource permit that must be released after use
     */
    public class ResourcePermit implements AutoCloseable {
        private final Semaphore semaphore;
        private final String operationType;
        private boolean released = false;

        private ResourcePermit(Semaphore semaphore, String operationType) {
            this.semaphore = semaphore;
            this.operationType = operationType;
        }

        @Override
        public void close() {
            if (!released) {
                semaphore.release();
                released = true;
                logger.debug("Released resource permit for: {}", operationType);
            }
        }
    }

    /**
     * Memory reservation that must be released after use
     */
    public class MemoryReservation implements AutoCloseable {
        private final long memoryAmount;
        private boolean released = false;

        private MemoryReservation(long memoryAmount) {
            this.memoryAmount = memoryAmount;
        }

        @Override
        public void close() {
            if (!released) {
                totalMemoryUsed.addAndGet(-memoryAmount);
                released = true;
                logger.debug("Released {} bytes of memory. Total: {} bytes",
                        memoryAmount, totalMemoryUsed.get());
            }
        }
    }

    /**
     * Resource usage statistics
     */
    public static class ResourceStats {
        public final int availableFilePermits;
        public final int availableNetworkPermits;
        public final int availableProcessPermits;
        public final long currentMemoryUsage;
        public final long maxMemoryUsage;
        public final Map<String, Long> operationCounts;

        private ResourceStats(int availableFilePermits, int availableNetworkPermits,
                              int availableProcessPermits, long currentMemoryUsage,
                              long maxMemoryUsage, Map<String, Long> operationCounts) {
            this.availableFilePermits = availableFilePermits;
            this.availableNetworkPermits = availableNetworkPermits;
            this.availableProcessPermits = availableProcessPermits;
            this.currentMemoryUsage = currentMemoryUsage;
            this.maxMemoryUsage = maxMemoryUsage;
            this.operationCounts = operationCounts;
        }

        @Override
        public String toString() {
            return String.format(
                    "ResourceStats{filePermits=%d, networkPermits=%d, processPermits=%d, " +
                            "memory=%d/%d bytes, operations=%s}",
                    availableFilePermits, availableNetworkPermits, availableProcessPermits,
                    currentMemoryUsage, maxMemoryUsage, operationCounts
            );
        }
    }

    /**
     * Builder for ResourceLimiter
     */
    public static class Builder {
        private int maxConcurrentFileOps = 10;
        private int maxConcurrentNetworkOps = 5;
        private int maxConcurrentProcessOps = 3;
        private double globalRateLimit = 10.0; // requests per second
        private double searchRateLimit = 2.0; // searches per second
        private double fileReadRateLimit = 5.0;
        private double fileWriteRateLimit = 2.0;
        private double fileSearchRateLimit = 1.0;
        private double processExecRateLimit = 1.0;
        private double aiRequestRateLimit = 2.0;
        private long maxMemoryUsage = 100 * 1024 * 1024; // 100MB default

        public Builder maxConcurrentFileOps(int max) {
            this.maxConcurrentFileOps = max;
            return this;
        }

        public Builder maxConcurrentNetworkOps(int max) {
            this.maxConcurrentNetworkOps = max;
            return this;
        }

        public Builder maxConcurrentProcessOps(int max) {
            this.maxConcurrentProcessOps = max;
            return this;
        }

        public Builder globalRateLimit(double ratePerSecond) {
            this.globalRateLimit = ratePerSecond;
            return this;
        }

        public Builder searchRateLimit(double ratePerSecond) {
            this.searchRateLimit = ratePerSecond;
            return this;
        }

        public Builder fileReadRateLimit(double ratePerSecond) {
            this.fileReadRateLimit = ratePerSecond;
            return this;
        }

        public Builder fileWriteRateLimit(double ratePerSecond) {
            this.fileWriteRateLimit = ratePerSecond;
            return this;
        }

        public Builder processExecRateLimit(double ratePerSecond) {
            this.processExecRateLimit = ratePerSecond;
            return this;
        }

        public Builder aiRequestRateLimit(double ratePerSecond) {
            this.aiRequestRateLimit = ratePerSecond;
            return this;
        }

        public Builder maxMemoryUsage(long bytes) {
            this.maxMemoryUsage = bytes;
            return this;
        }

        public ResourceLimiter build() {
            return new ResourceLimiter(this);
        }
    }

    /**
     * Create default resource limiter for development
     */
    public static ResourceLimiter createDevelopmentLimiter() {
        return new Builder()
                .maxConcurrentFileOps(15)
                .maxConcurrentNetworkOps(8)
                .maxConcurrentProcessOps(5)
                .globalRateLimit(20.0)
                .maxMemoryUsage(200 * 1024 * 1024) // 200MB for development
                .build();
    }

    /**
     * Create restricted resource limiter for production
     */
    public static ResourceLimiter createProductionLimiter() {
        return new Builder()
                .maxConcurrentFileOps(8)
                .maxConcurrentNetworkOps(4)
                .maxConcurrentProcessOps(2)
                .globalRateLimit(5.0)
                .searchRateLimit(1.0)
                .processExecRateLimit(0.5)
                .maxMemoryUsage(100 * 1024 * 1024) // 100MB for production
                .build();
    }
}


