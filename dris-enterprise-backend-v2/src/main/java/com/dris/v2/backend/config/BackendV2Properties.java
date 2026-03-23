package com.dris.v2.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dris.v2")
public class BackendV2Properties {
    private String nodeId = "node-local";
    private String nodeLevel = "COUNTRY";
    private String regionCode = "1100";
    private String organizationCode = "ABCD";
    private String mode = "jdbc";
    private boolean drirpEnabled = true;
    private int drirpPort = 19090;
    private String drirpPublicUrl = "grpc://127.0.0.1:19090";
    private String parentNodeId = "";
    private String parentDrirpUrl = "";
    private String parentRegionCode = "";
    private String parentOrganizationCode = "";
    private boolean startupHelloEnabled = true;
    private String storageProvider = "local";
    private String storageEndpoint = "";
    private String storageRegion = "";
    private String storagePublicBaseUrl = "";
    private String storageSecretId = "";
    private String storageSecretKey = "";
    private String storageSessionToken = "";
    private String defaultBucket = "local-bucket";
    private int uploadSessionTtlSeconds = 1800;
    private int uploadPresignExpiresSeconds = 900;
    private int downloadPresignDefaultSeconds = 600;
    private int downloadPresignMaxSeconds = 3600;
    private boolean strictIndustryValidation = false;
    private boolean allowComponentTypeReplace = false;
    private boolean requireToolSpec = false;
    private boolean requireToolSha256 = true;
    private String resolveMode = "recursive";
    private int resolveMaxHops = 8;
    private long resolveTimeoutMs = 5000L;
    private boolean resolveAllowParentFallback = true;
    private boolean searchCacheEnabled = true;
    private long searchCacheTtlMs = 30000L;
    private int searchCacheMaxEntries = 1000;
    private boolean searchRateLimitEnabled = true;
    private int searchRateLimitQps = 100;
    private int searchRateLimitBurst = 200;
    private boolean searchCircuitBreakerEnabled = true;
    private int searchCircuitFailureThreshold = 10;
    private long searchCircuitOpenMs = 10000L;
    private int searchCircuitHalfOpenPermits = 1;
    private String searchEngine = "mysql_like";
    private String searchEsUrl = "";
    private String searchEsIndex = "";
    private String searchEsUsername = "";
    private String searchEsPassword = "";
    private int searchEsConnectTimeoutMs = 3000;
    private int searchEsRequestTimeoutMs = 5000;
    private boolean vectorAutoSyncEnabled = true;
    private long vectorAutoSyncInitialDelayMs = 5000L;
    private long vectorAutoSyncIntervalMs = 3000L;
    private String vectorEngine = "ann_lsh";
    private String vectorServerUrl = "";
    private String vectorCollection = "dris_metadata";
    private String vectorTenant = "default_tenant";
    private String vectorDatabase = "default_database";
    private int vectorRequestTimeoutMs = 5000;
    private String vectorServerApiKey = "";
    private String vectorServerApiKeyHeader = "X-Chroma-Token";
    private String embeddingProvider = "hash_local";
    private int embeddingDimensions = 128;
    private String embeddingApiBaseUrl = "";
    private String embeddingModel = "";
    private int embeddingRequestTimeoutMs = 5000;
    private String embeddingApiKey = "";
    private String embeddingApiKeyHeader = "Authorization";
    private KernelReadCacheProperties kernelReadCache = new KernelReadCacheProperties();
    private WorkloadExecutorProperties resolveExecutor = new WorkloadExecutorProperties();
    private WorkloadExecutorProperties searchExecutor = new WorkloadExecutorProperties();
    private WorkloadExecutorProperties vectorSyncExecutor = new WorkloadExecutorProperties();
    private BusinessAuditProperties businessAudit = new BusinessAuditProperties();
    private DrirpSecurityProperties drirpSecurity = new DrirpSecurityProperties();
    private DrirpOutboundClientPoolProperties drirpOutboundClientPool = new DrirpOutboundClientPoolProperties();
    private TopologyHealthProperties topologyHealth = new TopologyHealthProperties();

    public String getRegionCode() {
        return regionCode;
    }

    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }

    public String getOrganizationCode() {
        return organizationCode;
    }

    public void setOrganizationCode(String organizationCode) {
        this.organizationCode = organizationCode;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getNodeLevel() {
        return nodeLevel;
    }

    public void setNodeLevel(String nodeLevel) {
        this.nodeLevel = nodeLevel;
    }

    public boolean isDrirpEnabled() {
        return drirpEnabled;
    }

    public void setDrirpEnabled(boolean drirpEnabled) {
        this.drirpEnabled = drirpEnabled;
    }

    public int getDrirpPort() {
        return drirpPort;
    }

    public void setDrirpPort(int drirpPort) {
        this.drirpPort = drirpPort;
    }

    public String getDrirpPublicUrl() {
        return drirpPublicUrl;
    }

    public void setDrirpPublicUrl(String drirpPublicUrl) {
        this.drirpPublicUrl = drirpPublicUrl;
    }

    public String getParentNodeId() {
        return parentNodeId;
    }

    public void setParentNodeId(String parentNodeId) {
        this.parentNodeId = parentNodeId;
    }

    public String getParentDrirpUrl() {
        return parentDrirpUrl;
    }

    public void setParentDrirpUrl(String parentDrirpUrl) {
        this.parentDrirpUrl = parentDrirpUrl;
    }

    public String getParentRegionCode() {
        return parentRegionCode;
    }

    public void setParentRegionCode(String parentRegionCode) {
        this.parentRegionCode = parentRegionCode;
    }

    public String getParentOrganizationCode() {
        return parentOrganizationCode;
    }

    public void setParentOrganizationCode(String parentOrganizationCode) {
        this.parentOrganizationCode = parentOrganizationCode;
    }

    public boolean isStartupHelloEnabled() {
        return startupHelloEnabled;
    }

    public void setStartupHelloEnabled(boolean startupHelloEnabled) {
        this.startupHelloEnabled = startupHelloEnabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStorageProvider() {
        return storageProvider;
    }

    public void setStorageProvider(String storageProvider) {
        this.storageProvider = storageProvider;
    }

    public String getStorageEndpoint() {
        return storageEndpoint;
    }

    public void setStorageEndpoint(String storageEndpoint) {
        this.storageEndpoint = storageEndpoint;
    }

    public String getStorageRegion() {
        return storageRegion;
    }

    public void setStorageRegion(String storageRegion) {
        this.storageRegion = storageRegion;
    }

    public String getStoragePublicBaseUrl() {
        return storagePublicBaseUrl;
    }

    public void setStoragePublicBaseUrl(String storagePublicBaseUrl) {
        this.storagePublicBaseUrl = storagePublicBaseUrl;
    }


    public String getStorageSecretId() {
        return storageSecretId;
    }

    public void setStorageSecretId(String storageSecretId) {
        this.storageSecretId = storageSecretId;
    }

    public String getStorageSecretKey() {
        return storageSecretKey;
    }

    public void setStorageSecretKey(String storageSecretKey) {
        this.storageSecretKey = storageSecretKey;
    }

    public String getStorageSessionToken() {
        return storageSessionToken;
    }

    public void setStorageSessionToken(String storageSessionToken) {
        this.storageSessionToken = storageSessionToken;
    }
    public String getDefaultBucket() {
        return defaultBucket;
    }

    public void setDefaultBucket(String defaultBucket) {
        this.defaultBucket = defaultBucket;
    }

    public int getUploadSessionTtlSeconds() {
        return uploadSessionTtlSeconds;
    }

    public void setUploadSessionTtlSeconds(int uploadSessionTtlSeconds) {
        this.uploadSessionTtlSeconds = uploadSessionTtlSeconds;
    }

    public int getUploadPresignExpiresSeconds() {
        return uploadPresignExpiresSeconds;
    }

    public void setUploadPresignExpiresSeconds(int uploadPresignExpiresSeconds) {
        this.uploadPresignExpiresSeconds = uploadPresignExpiresSeconds;
    }

    public int getDownloadPresignDefaultSeconds() {
        return downloadPresignDefaultSeconds;
    }

    public void setDownloadPresignDefaultSeconds(int downloadPresignDefaultSeconds) {
        this.downloadPresignDefaultSeconds = downloadPresignDefaultSeconds;
    }

    public int getDownloadPresignMaxSeconds() {
        return downloadPresignMaxSeconds;
    }

    public void setDownloadPresignMaxSeconds(int downloadPresignMaxSeconds) {
        this.downloadPresignMaxSeconds = downloadPresignMaxSeconds;
    }

    public boolean isStrictIndustryValidation() {
        return strictIndustryValidation;
    }

    public void setStrictIndustryValidation(boolean strictIndustryValidation) {
        this.strictIndustryValidation = strictIndustryValidation;
    }

    public boolean isAllowComponentTypeReplace() {
        return allowComponentTypeReplace;
    }

    public void setAllowComponentTypeReplace(boolean allowComponentTypeReplace) {
        this.allowComponentTypeReplace = allowComponentTypeReplace;
    }

    public boolean isRequireToolSpec() {
        return requireToolSpec;
    }

    public void setRequireToolSpec(boolean requireToolSpec) {
        this.requireToolSpec = requireToolSpec;
    }

    public boolean isRequireToolSha256() {
        return requireToolSha256;
    }

    public void setRequireToolSha256(boolean requireToolSha256) {
        this.requireToolSha256 = requireToolSha256;
    }

    public String getResolveMode() {
        return resolveMode;
    }

    public void setResolveMode(String resolveMode) {
        this.resolveMode = resolveMode;
    }

    public int getResolveMaxHops() {
        return resolveMaxHops;
    }

    public void setResolveMaxHops(int resolveMaxHops) {
        this.resolveMaxHops = resolveMaxHops;
    }

    public long getResolveTimeoutMs() {
        return resolveTimeoutMs;
    }

    public void setResolveTimeoutMs(long resolveTimeoutMs) {
        this.resolveTimeoutMs = resolveTimeoutMs;
    }

    public boolean isResolveAllowParentFallback() {
        return resolveAllowParentFallback;
    }

    public void setResolveAllowParentFallback(boolean resolveAllowParentFallback) {
        this.resolveAllowParentFallback = resolveAllowParentFallback;
    }

    public boolean isSearchCacheEnabled() {
        return searchCacheEnabled;
    }

    public void setSearchCacheEnabled(boolean searchCacheEnabled) {
        this.searchCacheEnabled = searchCacheEnabled;
    }

    public long getSearchCacheTtlMs() {
        return searchCacheTtlMs;
    }

    public void setSearchCacheTtlMs(long searchCacheTtlMs) {
        this.searchCacheTtlMs = searchCacheTtlMs;
    }

    public int getSearchCacheMaxEntries() {
        return searchCacheMaxEntries;
    }

    public void setSearchCacheMaxEntries(int searchCacheMaxEntries) {
        this.searchCacheMaxEntries = searchCacheMaxEntries;
    }

    public boolean isSearchRateLimitEnabled() {
        return searchRateLimitEnabled;
    }

    public void setSearchRateLimitEnabled(boolean searchRateLimitEnabled) {
        this.searchRateLimitEnabled = searchRateLimitEnabled;
    }

    public int getSearchRateLimitQps() {
        return searchRateLimitQps;
    }

    public void setSearchRateLimitQps(int searchRateLimitQps) {
        this.searchRateLimitQps = searchRateLimitQps;
    }

    public int getSearchRateLimitBurst() {
        return searchRateLimitBurst;
    }

    public void setSearchRateLimitBurst(int searchRateLimitBurst) {
        this.searchRateLimitBurst = searchRateLimitBurst;
    }

    public boolean isSearchCircuitBreakerEnabled() {
        return searchCircuitBreakerEnabled;
    }

    public void setSearchCircuitBreakerEnabled(boolean searchCircuitBreakerEnabled) {
        this.searchCircuitBreakerEnabled = searchCircuitBreakerEnabled;
    }

    public int getSearchCircuitFailureThreshold() {
        return searchCircuitFailureThreshold;
    }

    public void setSearchCircuitFailureThreshold(int searchCircuitFailureThreshold) {
        this.searchCircuitFailureThreshold = searchCircuitFailureThreshold;
    }

    public long getSearchCircuitOpenMs() {
        return searchCircuitOpenMs;
    }

    public void setSearchCircuitOpenMs(long searchCircuitOpenMs) {
        this.searchCircuitOpenMs = searchCircuitOpenMs;
    }

    public int getSearchCircuitHalfOpenPermits() {
        return searchCircuitHalfOpenPermits;
    }

    public void setSearchCircuitHalfOpenPermits(int searchCircuitHalfOpenPermits) {
        this.searchCircuitHalfOpenPermits = searchCircuitHalfOpenPermits;
    }

    public String getSearchEngine() {
        return searchEngine;
    }

    public void setSearchEngine(String searchEngine) {
        this.searchEngine = searchEngine;
    }

    public String getSearchEsUrl() {
        return searchEsUrl;
    }

    public void setSearchEsUrl(String searchEsUrl) {
        this.searchEsUrl = searchEsUrl;
    }

    public String getSearchEsIndex() {
        return searchEsIndex;
    }

    public void setSearchEsIndex(String searchEsIndex) {
        this.searchEsIndex = searchEsIndex;
    }

    public String getSearchEsUsername() {
        return searchEsUsername;
    }

    public void setSearchEsUsername(String searchEsUsername) {
        this.searchEsUsername = searchEsUsername;
    }

    public String getSearchEsPassword() {
        return searchEsPassword;
    }

    public void setSearchEsPassword(String searchEsPassword) {
        this.searchEsPassword = searchEsPassword;
    }

    public int getSearchEsConnectTimeoutMs() {
        return searchEsConnectTimeoutMs;
    }

    public void setSearchEsConnectTimeoutMs(int searchEsConnectTimeoutMs) {
        this.searchEsConnectTimeoutMs = searchEsConnectTimeoutMs;
    }

    public int getSearchEsRequestTimeoutMs() {
        return searchEsRequestTimeoutMs;
    }

    public void setSearchEsRequestTimeoutMs(int searchEsRequestTimeoutMs) {
        this.searchEsRequestTimeoutMs = searchEsRequestTimeoutMs;
    }

    public boolean isVectorAutoSyncEnabled() {
        return vectorAutoSyncEnabled;
    }

    public void setVectorAutoSyncEnabled(boolean vectorAutoSyncEnabled) {
        this.vectorAutoSyncEnabled = vectorAutoSyncEnabled;
    }

    public long getVectorAutoSyncInitialDelayMs() {
        return vectorAutoSyncInitialDelayMs;
    }

    public void setVectorAutoSyncInitialDelayMs(long vectorAutoSyncInitialDelayMs) {
        this.vectorAutoSyncInitialDelayMs = vectorAutoSyncInitialDelayMs;
    }

    public long getVectorAutoSyncIntervalMs() {
        return vectorAutoSyncIntervalMs;
    }

    public void setVectorAutoSyncIntervalMs(long vectorAutoSyncIntervalMs) {
        this.vectorAutoSyncIntervalMs = vectorAutoSyncIntervalMs;
    }

    public String getVectorEngine() {
        return vectorEngine;
    }

    public void setVectorEngine(String vectorEngine) {
        this.vectorEngine = vectorEngine;
    }

    public String getVectorServerUrl() {
        return vectorServerUrl;
    }

    public void setVectorServerUrl(String vectorServerUrl) {
        this.vectorServerUrl = vectorServerUrl;
    }

    public String getVectorCollection() {
        return vectorCollection;
    }

    public void setVectorCollection(String vectorCollection) {
        this.vectorCollection = vectorCollection;
    }

    public String getVectorTenant() {
        return vectorTenant;
    }

    public void setVectorTenant(String vectorTenant) {
        this.vectorTenant = vectorTenant;
    }

    public String getVectorDatabase() {
        return vectorDatabase;
    }

    public void setVectorDatabase(String vectorDatabase) {
        this.vectorDatabase = vectorDatabase;
    }

    public int getVectorRequestTimeoutMs() {
        return vectorRequestTimeoutMs;
    }

    public void setVectorRequestTimeoutMs(int vectorRequestTimeoutMs) {
        this.vectorRequestTimeoutMs = vectorRequestTimeoutMs;
    }

    public String getVectorServerApiKey() {
        return vectorServerApiKey;
    }

    public void setVectorServerApiKey(String vectorServerApiKey) {
        this.vectorServerApiKey = vectorServerApiKey;
    }

    public String getVectorServerApiKeyHeader() {
        return vectorServerApiKeyHeader;
    }

    public void setVectorServerApiKeyHeader(String vectorServerApiKeyHeader) {
        this.vectorServerApiKeyHeader = vectorServerApiKeyHeader;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public void setEmbeddingProvider(String embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    public int getEmbeddingDimensions() {
        return embeddingDimensions;
    }

    public void setEmbeddingDimensions(int embeddingDimensions) {
        this.embeddingDimensions = embeddingDimensions;
    }

    public String getEmbeddingApiBaseUrl() {
        return embeddingApiBaseUrl;
    }

    public void setEmbeddingApiBaseUrl(String embeddingApiBaseUrl) {
        this.embeddingApiBaseUrl = embeddingApiBaseUrl;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingRequestTimeoutMs() {
        return embeddingRequestTimeoutMs;
    }

    public void setEmbeddingRequestTimeoutMs(int embeddingRequestTimeoutMs) {
        this.embeddingRequestTimeoutMs = embeddingRequestTimeoutMs;
    }

    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    public void setEmbeddingApiKey(String embeddingApiKey) {
        this.embeddingApiKey = embeddingApiKey;
    }

    public String getEmbeddingApiKeyHeader() {
        return embeddingApiKeyHeader;
    }

    public void setEmbeddingApiKeyHeader(String embeddingApiKeyHeader) {
        this.embeddingApiKeyHeader = embeddingApiKeyHeader;
    }

    public KernelReadCacheProperties getKernelReadCache() {
        return kernelReadCache;
    }
    public void setKernelReadCache(KernelReadCacheProperties kernelReadCache) {
        this.kernelReadCache = kernelReadCache == null ? new KernelReadCacheProperties() : kernelReadCache;
    }
    public static class KernelReadCacheProperties {
        private boolean enabled = true;
        private long ttlMs = 15000L;
        private int maxEntries = 2000;
        public boolean isEnabled() {
            return enabled;
        }
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        public long getTtlMs() {
            return ttlMs;
        }
        public void setTtlMs(long ttlMs) {
            this.ttlMs = ttlMs;
        }
        public int getMaxEntries() {
            return maxEntries;
        }
        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }
    }

    public WorkloadExecutorProperties getResolveExecutor() {
        return resolveExecutor;
    }

    public void setResolveExecutor(WorkloadExecutorProperties resolveExecutor) {
        this.resolveExecutor = resolveExecutor == null ? new WorkloadExecutorProperties() : resolveExecutor;
    }

    public WorkloadExecutorProperties getSearchExecutor() {
        return searchExecutor;
    }

    public void setSearchExecutor(WorkloadExecutorProperties searchExecutor) {
        this.searchExecutor = searchExecutor == null ? new WorkloadExecutorProperties() : searchExecutor;
    }

    public WorkloadExecutorProperties getVectorSyncExecutor() {
        return vectorSyncExecutor;
    }

    public void setVectorSyncExecutor(WorkloadExecutorProperties vectorSyncExecutor) {
        this.vectorSyncExecutor = vectorSyncExecutor == null ? new WorkloadExecutorProperties() : vectorSyncExecutor;
    }

    public BusinessAuditProperties getBusinessAudit() {
        return businessAudit;
    }

    public void setBusinessAudit(BusinessAuditProperties businessAudit) {
        this.businessAudit = businessAudit == null ? new BusinessAuditProperties() : businessAudit;
    }

    public DrirpSecurityProperties getDrirpSecurity() {
        return drirpSecurity;
    }

    public void setDrirpSecurity(DrirpSecurityProperties drirpSecurity) {
        this.drirpSecurity = drirpSecurity == null ? new DrirpSecurityProperties() : drirpSecurity;
    }

    public DrirpOutboundClientPoolProperties getDrirpOutboundClientPool() {
        return drirpOutboundClientPool;
    }

    public void setDrirpOutboundClientPool(DrirpOutboundClientPoolProperties drirpOutboundClientPool) {
        this.drirpOutboundClientPool = drirpOutboundClientPool == null
                ? new DrirpOutboundClientPoolProperties()
                : drirpOutboundClientPool;
    }

    public TopologyHealthProperties getTopologyHealth() {
        return topologyHealth;
    }

    public void setTopologyHealth(TopologyHealthProperties topologyHealth) {
        this.topologyHealth = topologyHealth == null ? new TopologyHealthProperties() : topologyHealth;
    }

    public static class DrirpOutboundClientPoolProperties {
        private int maxEntries = 64;
        private long maxIdleMs = 600000L;
        private long cleanupIntervalMs = 30000L;

        public int getMaxEntries() {
            return maxEntries;
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
        }

        public long getMaxIdleMs() {
            return maxIdleMs;
        }

        public void setMaxIdleMs(long maxIdleMs) {
            this.maxIdleMs = maxIdleMs;
        }

        public long getCleanupIntervalMs() {
            return cleanupIntervalMs;
        }

        public void setCleanupIntervalMs(long cleanupIntervalMs) {
            this.cleanupIntervalMs = cleanupIntervalMs;
        }
    }

    public static class TopologyHealthProperties {
        private boolean enabled = true;
        private long initialDelayMs = 5000L;
        private long intervalMs = 15000L;
        private long timeoutMs = 3000L;
        private long offlineThresholdMs = 45000L;
        private int offlineFailureThreshold = 3;
        private int startupHelloMaxAttempts = 3;
        private long startupHelloRetryIntervalMs = 2000L;
        private boolean probeChildrenEnabled = true;
        private boolean notifyOnShutdown = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getInitialDelayMs() {
            return initialDelayMs;
        }

        public void setInitialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public long getOfflineThresholdMs() {
            return offlineThresholdMs;
        }

        public void setOfflineThresholdMs(long offlineThresholdMs) {
            this.offlineThresholdMs = offlineThresholdMs;
        }

        public int getOfflineFailureThreshold() {
            return offlineFailureThreshold;
        }

        public void setOfflineFailureThreshold(int offlineFailureThreshold) {
            this.offlineFailureThreshold = offlineFailureThreshold;
        }

        public int getStartupHelloMaxAttempts() {
            return startupHelloMaxAttempts;
        }

        public void setStartupHelloMaxAttempts(int startupHelloMaxAttempts) {
            this.startupHelloMaxAttempts = startupHelloMaxAttempts;
        }

        public long getStartupHelloRetryIntervalMs() {
            return startupHelloRetryIntervalMs;
        }

        public void setStartupHelloRetryIntervalMs(long startupHelloRetryIntervalMs) {
            this.startupHelloRetryIntervalMs = startupHelloRetryIntervalMs;
        }

        public boolean isProbeChildrenEnabled() {
            return probeChildrenEnabled;
        }

        public void setProbeChildrenEnabled(boolean probeChildrenEnabled) {
            this.probeChildrenEnabled = probeChildrenEnabled;
        }

        public boolean isNotifyOnShutdown() {
            return notifyOnShutdown;
        }

        public void setNotifyOnShutdown(boolean notifyOnShutdown) {
            this.notifyOnShutdown = notifyOnShutdown;
        }
    }

    public static class WorkloadExecutorProperties {
        private int corePoolSize;
        private int maxPoolSize;
        private int queueCapacity;
        private int keepAliveSeconds;
        private String rejectionPolicy = "";

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }

        public String getRejectionPolicy() {
            return rejectionPolicy;
        }

        public void setRejectionPolicy(String rejectionPolicy) {
            this.rejectionPolicy = rejectionPolicy;
        }
    }

    public static class BusinessAuditProperties {
        private boolean enabled = true;
        private boolean asyncEnabled = true;
        private int corePoolSize = 1;
        private int maxPoolSize = 2;
        private int queueCapacity = 2000;
        private int keepAliveSeconds = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isAsyncEnabled() {
            return asyncEnabled;
        }

        public void setAsyncEnabled(boolean asyncEnabled) {
            this.asyncEnabled = asyncEnabled;
        }

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public int getKeepAliveSeconds() {
            return keepAliveSeconds;
        }

        public void setKeepAliveSeconds(int keepAliveSeconds) {
            this.keepAliveSeconds = keepAliveSeconds;
        }
    }

    public static class DrirpSecurityProperties {
        private boolean enabled = false;
        private String mode = "enforce";
        private boolean requireResponseAuth = true;
        private String sharedSecret = "";
        private String keyId = "";
        private long maxSkewMs = 300000L;
        private long nonceTtlMs = 300000L;
        private int replayMaxEntries = 100000;
        private boolean allowAnonymous = false;
        private boolean auditEnabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public boolean isRequireResponseAuth() {
            return requireResponseAuth;
        }

        public void setRequireResponseAuth(boolean requireResponseAuth) {
            this.requireResponseAuth = requireResponseAuth;
        }

        public String getSharedSecret() {
            return sharedSecret;
        }

        public void setSharedSecret(String sharedSecret) {
            this.sharedSecret = sharedSecret;
        }

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public long getMaxSkewMs() {
            return maxSkewMs;
        }

        public void setMaxSkewMs(long maxSkewMs) {
            this.maxSkewMs = maxSkewMs;
        }

        public long getNonceTtlMs() {
            return nonceTtlMs;
        }

        public void setNonceTtlMs(long nonceTtlMs) {
            this.nonceTtlMs = nonceTtlMs;
        }

        public int getReplayMaxEntries() {
            return replayMaxEntries;
        }

        public void setReplayMaxEntries(int replayMaxEntries) {
            this.replayMaxEntries = replayMaxEntries;
        }

        public boolean isAllowAnonymous() {
            return allowAnonymous;
        }

        public void setAllowAnonymous(boolean allowAnonymous) {
            this.allowAnonymous = allowAnonymous;
        }

        public boolean isAuditEnabled() {
            return auditEnabled;
        }

        public void setAuditEnabled(boolean auditEnabled) {
            this.auditEnabled = auditEnabled;
        }
    }
}



