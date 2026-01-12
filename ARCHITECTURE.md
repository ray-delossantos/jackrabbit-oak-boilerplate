# AEM Oak Boilerplate - Architecture

## System Overview

```
                                    KUBERNETES CLUSTER (aem-oak namespace)
    ┌─────────────────────────────────────────────────────────────────────────────────────┐
    │                                                                                     │
    │   ┌─────────────────────────────────────────────────────────────────────────────┐   │
    │   │                           INGRESS (NGINX)                                   │   │
    │   │                    author.aem-oak.local | www.aem-oak.local                 │   │
    │   └───────────────────────────────┬─────────────────────────────────────────────┘   │
    │                                   │                                                 │
    │           ┌───────────────────────┴───────────────────────┐                         │
    │           │                                               │                         │
    │           ▼                                               ▼                         │
    │   ┌───────────────┐                               ┌───────────────┐                 │
    │   │    AUTHOR     │                               │    PUBLISH    │                 │
    │   │  StatefulSet  │ ─────── Replication ────────▶ │   Deployment  │                 │
    │   │  (2 replicas) │          (HTTP POST)          │  (2+ replicas)│                 │
    │   └───────┬───────┘                               └───────┬───────┘                 │
    │           │                                               │                         │
    │           │                                               │                         │
    │           │         ┌─────────────────────────────────────┤                         │
    │           │         │                                     │                         │
    │           │         ▼                                     │                         │
    │           │   ┌───────────┐                               │                         │
    │           │   │   REDIS   │◀──────────────────────────────┘                         │
    │           │   │  (cache)  │       Shared Cache                                      │
    │           │   └───────────┘                                                         │
    │           │                                                                         │
    │           └──────────────────────┬──────────────────────────────────────┐           │
    │                                  │                                      │           │
    │                                  ▼                                      ▼           │
    │                          ┌─────────────┐                        ┌─────────────┐     │
    │                          │  POSTGRESQL │                        │    MINIO    │     │
    │                          │  (metadata) │                        │   (blobs)   │     │
    │                          │  StatefulSet│                        │ StatefulSet │     │
    │                          └─────────────┘                        └─────────────┘     │
    │                                                                                     │
    └─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Content Authoring Flow

```
    ┌──────────┐      HTTP POST/PUT/DELETE       ┌─────────────────┐
    │  Author  │ ──────────────────────────────▶ │ ContentServlet  │
    │   User   │       /api/content/*            │                 │
    └──────────┘                                 └────────┬────────┘
                                                          │
                                                          ▼
                                                 ┌─────────────────┐
                                                 │ContentServiceImpl│
                                                 │                 │
                                                 │ createContent() │
                                                 │ updateContent() │
                                                 │ deleteContent() │
                                                 └────────┬────────┘
                                                          │
                                                          ▼
                                                 ┌─────────────────┐
                                                 │JcrSessionFactory│
                                                 │                 │
                                                 │doWithSession()  │
                                                 │     AndSave()   │
                                                 └────────┬────────┘
                                                          │
                                                          ▼
                                                 ┌─────────────────┐
                                                 │   Oak / JCR     │
                                                 │                 │
                                                 │  session.save() │
                                                 └────────┬────────┘
                                                          │
                                                          ▼
                                                 ┌─────────────────┐
                                                 │   PostgreSQL    │
                                                 │   (NODES table) │
                                                 └─────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `author/.../content/ContentServlet.java` | HTTP endpoint for CRUD |
| `author/.../content/ContentServiceImpl.java` | Business logic |
| `shared/oak-core-bundle/.../JcrSessionFactory.java` | Session management |

### Important Code

**ContentServlet.java:45-60** - Request handling
```java
@Override
protected void doPost(HttpServletRequest request, HttpServletResponse response) {
    String path = extractPath(request);
    Map<String, Object> properties = extractProperties(request);
    contentService.createContent(path, "nt:unstructured", properties);
}
```

**JcrSessionFactory.java:89-105** - Transaction wrapper
```java
public <T> T doWithSessionAndSave(SessionOperation<T> operation) {
    Session session = createAdminSession();
    try {
        T result = operation.execute(session);
        if (session.hasPendingChanges()) {
            session.save();  // Auto-commit
        }
        return result;
    } finally {
        session.logout();
    }
}
```

---

## 2. Replication Flow (Author → Publish)

```
    AUTHOR TIER                                              PUBLISH TIER
    ───────────                                              ────────────

    ┌─────────────────┐
    │ContentServiceImpl│
    │   (on save)     │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │ReplicationService│
    │   .replicate()  │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐      serialize + GZIP
    │  ContentPackage │ ───────────────────────┐
    │    .create()    │                        │
    └─────────────────┘                        │
                                               ▼
    ┌─────────────────┐                ┌───────────────┐
    │ReplicationQueue │◀───────────────│   byte[]      │
    │    .add()       │                │  (payload)    │
    └────────┬────────┘                └───────────────┘
             │
             │ poll every 1000ms
             ▼
    ┌─────────────────┐
    │ReplicationAgent │
    │ .processQueue() │
    └────────┬────────┘
             │
             │ HTTP POST to each publish endpoint
             │ Headers: X-Replication-Path, X-Replication-Action
             ▼
    ─────────────────────────────────────────────────────────────────────
             │
             ▼
    ┌─────────────────┐
    │ReplicationReceiver│
    │    .doPost()    │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │  ContentPackage │
    │  .fromBytes()   │  ◀─── decompress + deserialize
    └────────┬────────┘
             │
             │ ACTIVATE / DEACTIVATE / DELETE
             ▼
    ┌─────────────────┐
    │ JCR Repository  │
    │   (Publish)     │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │  Cache.invalidate│
    │     (path)      │
    └─────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `author/.../replication/ReplicationServiceImpl.java` | Orchestrates replication |
| `author/.../replication/ReplicationAgent.java` | HTTP transport |
| `author/.../replication/ReplicationQueue.java` | Queue with retry logic |
| `author/.../replication/ContentPackage.java` | Serialization |
| `publish/.../replication/ReplicationReceiver.java` | Receives on publish |

### Important Code

**ReplicationServiceImpl.java:67-85** - Entry point
```java
public void replicate(String path, ReplicationAction action) {
    ContentPackage pkg = ContentPackage.create(node, action, authorId);
    for (String endpoint : replicationAgent.getPublishEndpoints()) {
        ReplicationRequest request = new ReplicationRequest(path, action, pkg.toBytes());
        replicationQueue.add(request);
    }
}
```

**ReplicationAgent.java:112-130** - HTTP transport
```java
private boolean replicateToEndpoint(QueueItem item, String endpoint) {
    HttpPost request = new HttpPost(endpoint + "/bin/replicate");
    request.setHeader("X-Replication-Path", item.getPath());
    request.setHeader("X-Replication-Action", item.getAction().name());
    request.setEntity(new ByteArrayEntity(item.getPayload()));
    HttpResponse response = httpClient.execute(request);
    return response.getStatusLine().getStatusCode() < 300;
}
```

**ReplicationQueue.java:89-110** - Retry with exponential backoff
```java
public void markFailed(QueueItem item, String endpoint, String error) {
    item.incrementAttempts();
    if (item.getAttempts() < maxRetries) {
        long delay = retryDelayMs * (long) Math.pow(backoffMultiplier, item.getAttempts() - 1);
        item.setNextRetryTime(System.currentTimeMillis() + delay);
        queue.add(item);  // Re-queue with delay
    }
}
```

---

## 3. Content Delivery Flow (Publish)

```
    ┌──────────┐      HTTP GET               ┌──────────────────────┐
    │   User   │ ──────────────────────────▶ │ContentDeliveryServlet│
    │ Browser  │    /content/* or /api/*     │                      │
    └──────────┘                             └──────────┬───────────┘
                                                        │
                                    ┌───────────────────┴───────────────────┐
                                    │                                       │
                                    ▼                                       │
                           ┌─────────────────┐                              │
                      ┌────│  CacheService   │                              │
                      │    │    .get()       │                              │
                      │    └────────┬────────┘                              │
                      │             │                                       │
                      │    HIT?     │  MISS                                 │
                      │    ┌────────┴────────┐                              │
                      │    │                 │                              │
                      │    ▼                 ▼                              │
                      │  Return         ┌─────────────────┐                 │
                      │  cached         │RedisCacheService│                 │
                      │                 │    .get()       │                 │
                      │                 └────────┬────────┘                 │
                      │                          │                          │
                      │                 HIT?     │  MISS                    │
                      │                 ┌────────┴────────┐                 │
                      │                 │                 │                 │
                      │                 ▼                 ▼                 │
                      │               Return        ┌─────────────────┐     │
                      │               cached        │  JCR Repository │◀────┘
                      │                             │   (read-only)   │
                      │                             └────────┬────────┘
                      │                                      │
                      │                                      ▼
                      │                             ┌─────────────────┐
                      │                             │  serializeNode()│
                      │                             │   or stream     │
                      │                             │    binary       │
                      │                             └────────┬────────┘
                      │                                      │
                      │                                      ▼
                      │                             ┌─────────────────┐
                      │                             │  Cache.put()    │
                      │                             │  (both layers)  │
                      │                             └────────┬────────┘
                      │                                      │
                      └──────────────────────────────────────┤
                                                             │
                                                             ▼
                                                    ┌─────────────────┐
                                                    │    Response     │
                                                    │  + Cache-Control│
                                                    │  + ETag         │
                                                    └─────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `publish/.../content/ContentDeliveryServlet.java` | HTTP endpoint |
| `publish/.../content/CacheService.java` | In-memory LRU cache |
| `publish/.../content/RedisCacheService.java` | Distributed Redis cache |

### Important Code

**ContentDeliveryServlet.java:78-110** - Cache-first delivery
```java
protected void doGet(HttpServletRequest request, HttpServletResponse response) {
    String path = extractPath(request);

    // Check in-memory cache first
    CacheEntry cached = cacheService.get(path, selector, extension);
    if (cached != null) {
        response.setHeader("X-Cache", "HIT");
        writeResponse(response, cached);
        return;
    }

    // Check Redis cache
    if (redisCacheService.isAvailable()) {
        cached = redisCacheService.get(path, selector, extension);
        if (cached != null) {
            response.setHeader("X-Cache", "HIT-REDIS");
            cacheService.put(path, selector, extension, cached); // Warm local
            writeResponse(response, cached);
            return;
        }
    }

    // Load from repository
    response.setHeader("X-Cache", "MISS");
    // ... fetch and cache
}
```

**ContentDeliveryServlet.java:145-165** - HTTP cache headers
```java
private void setCacheHeaders(HttpServletResponse response, Node node) {
    response.setHeader("Cache-Control", "public, max-age=" + cacheMaxAge);

    String lastModified = node.getProperty("jcr:lastModified").getString();
    response.setHeader("Last-Modified", formatHttpDate(lastModified));

    String etag = generateETag(path, lastModified);
    response.setHeader("ETag", "\"" + etag + "\"");
}
```

---

## 4. Caching Architecture

```
                              PUBLISH POD 1                    PUBLISH POD 2
                         ┌─────────────────────┐          ┌─────────────────────┐
                         │                     │          │                     │
                         │  ┌───────────────┐  │          │  ┌───────────────┐  │
                         │  │ CacheService  │  │          │  │ CacheService  │  │
                         │  │ (in-memory)   │  │          │  │ (in-memory)   │  │
                         │  │               │  │          │  │               │  │
                         │  │ 10k entries   │  │          │  │ 10k entries   │  │
                         │  │ TTL: 300s     │  │          │  │ TTL: 300s     │  │
                         │  │ LRU eviction  │  │          │  │ LRU eviction  │  │
                         │  └───────┬───────┘  │          │  └───────┬───────┘  │
                         │          │          │          │          │          │
                         │          │          │          │          │          │
                         │  ┌───────▼───────┐  │          │  ┌───────▼───────┐  │
                         │  │RedisCacheService│ │          │  │RedisCacheService│ │
                         │  │  (Jedis client)│  │          │  │  (Jedis client)│  │
                         │  └───────┬───────┘  │          │  └───────┬───────┘  │
                         │          │          │          │          │          │
                         └──────────┼──────────┘          └──────────┼──────────┘
                                    │                                │
                                    │                                │
                                    └────────────┬───────────────────┘
                                                 │
                                                 ▼
                                    ┌─────────────────────────┐
                                    │         REDIS           │
                                    │    (shared cache)       │
                                    │                         │
                                    │  Key: aem:cache:{path}  │
                                    │  TTL: 300s              │
                                    │  Policy: allkeys-lru    │
                                    │  Memory: 256MB max      │
                                    └─────────────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `publish/.../content/CacheService.java` | Local LRU cache |
| `publish/.../content/RedisCacheService.java` | Distributed cache |
| `k8s/base/redis/statefulset.yaml` | Redis deployment |

### Important Code

**CacheService.java:127-147** - Put with LRU eviction
```java
public void put(String path, String selector, String extension, CacheEntry entry) {
    if (entry.getData().length > config.maxEntrySize()) {
        return;  // Too large
    }

    if (cache.size() >= config.maxEntries()) {
        evictOldest();  // LRU eviction
    }

    String key = buildKey(path, selector, extension);
    long expirationTime = System.currentTimeMillis() + (config.ttlSeconds() * 1000L);
    cache.put(key, new CacheItem(entry, expirationTime));
}
```

**RedisCacheService.java:145-165** - Redis operations
```java
public void put(String path, String selector, String extension, CacheEntry entry) {
    String key = CACHE_PREFIX + path + ":" + selector + ":" + extension;
    try (Jedis jedis = jedisPool.getResource()) {
        String encodedData = Base64.getEncoder().encodeToString(entry.getData());
        jedis.setex(key, config.ttlSeconds(), encodedData);
        jedis.setex(key + ":ct", config.ttlSeconds(), entry.getContentType());
    }
}
```

---

## 5. Asset/DAM Flow

```
    ┌──────────┐    POST /api/assets/*     ┌─────────────────┐
    │  Author  │ ────────────────────────▶ │  AssetServlet   │
    │   User   │    multipart/form-data    │                 │
    └──────────┘                           └────────┬────────┘
                                                    │
                                                    ▼
                                           ┌─────────────────┐
                                           │AssetServiceImpl │
                                           │ .uploadAsset()  │
                                           └────────┬────────┘
                                                    │
                        ┌───────────────────────────┼───────────────────────────┐
                        │                           │                           │
                        ▼                           ▼                           ▼
               ┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐
               │ Create Asset    │        │  Store Binary   │        │ Async Process   │
               │ Node Structure  │        │   in MinIO      │        │                 │
               └────────┬────────┘        └────────┬────────┘        └────────┬────────┘
                        │                          │                          │
                        ▼                          ▼                          ▼
               ┌─────────────────┐        ┌─────────────────┐        ┌─────────────────┐
               │  dam:Asset      │        │   S3BlobStore   │        │AssetProcessing  │
               │  └─jcr:content  │        │  .writeBlob()   │        │   Service       │
               │    └─renditions │        │                 │        │                 │
               │      └─original │        │  SHA-256 hash   │        │ - thumbnails    │
               │    └─metadata   │        │  = blobId       │        │ - metadata      │
               └─────────────────┘        └────────┬────────┘        └────────┬────────┘
                                                   │                          │
                                                   ▼                          │
                                          ┌─────────────────┐                 │
                                          │     MinIO       │                 │
                                          │                 │                 │
                                          │ blobs/{sha256}  │                 │
                                          └─────────────────┘                 │
                                                                              │
                        ┌─────────────────────────────────────────────────────┘
                        │
                        ▼
               ┌─────────────────┐          ┌─────────────────┐
               │  Thumbnailator  │          │metadata-extractor│
               │                 │          │                 │
               │ 140x100 thumb   │          │ EXIF, TIFF,     │
               │ 1280x1280 web   │          │ IPTC parsing    │
               └────────┬────────┘          └────────┬────────┘
                        │                            │
                        └────────────┬───────────────┘
                                     │
                                     ▼
                            ┌─────────────────┐
                            │   renditions/   │
                            │  ├─original     │
                            │  ├─thumbnail    │
                            │  └─web          │
                            │                 │
                            │   metadata/     │
                            │  ├─dc:title     │
                            │  ├─tiff:width   │
                            │  └─...          │
                            └─────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `author/.../asset/AssetServiceImpl.java` | Asset CRUD operations |
| `author/.../asset/AssetProcessingService.java` | Thumbnail/metadata extraction |
| `shared/oak-core-bundle/.../S3BlobStore.java` | MinIO blob storage |

### Important Code

**AssetServiceImpl.java:89-125** - Asset upload
```java
public Asset uploadAsset(String path, byte[] data, String fileName, String mimeType) {
    return sessionFactory.doWithSessionAndSave(session -> {
        // Create dam:Asset node
        Node assetNode = createAssetStructure(session, path, fileName);

        // Store binary in original rendition
        Binary binary = session.getValueFactory().createBinary(new ByteArrayInputStream(data));
        Node original = assetNode.getNode("jcr:content/renditions/original/jcr:content");
        original.setProperty("jcr:data", binary);
        original.setProperty("jcr:mimeType", mimeType);

        // Trigger async processing
        if (processingService != null) {
            processingService.processAssetAsync(assetNode.getPath());
        }

        return toAsset(assetNode);
    });
}
```

**S3BlobStore.java:78-105** - Binary storage with deduplication
```java
public String writeBlob(InputStream stream) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    MessageDigest digest = MessageDigest.getInstance("SHA-256");

    // Read and hash simultaneously
    byte[] chunk = new byte[8192];
    int len;
    while ((len = stream.read(chunk)) != -1) {
        buffer.write(chunk, 0, len);
        digest.update(chunk, 0, len);
    }

    String blobId = Hex.encodeHexString(digest.digest());

    // Only upload if not exists (deduplication)
    if (!blobExists(blobId)) {
        s3Client.putObject(bucket, "blobs/" + blobId, buffer.toByteArray());
    }

    return blobId;
}
```

---

## 6. Repository Architecture (Oak + PostgreSQL + MinIO)

```
                                    JCR API
                                       │
                                       ▼
                          ┌─────────────────────────┐
                          │   Apache Jackrabbit Oak │
                          │                         │
                          │  ┌───────────────────┐  │
                          │  │ DocumentNodeStore │  │
                          │  │   (RDB-backed)    │  │
                          │  └─────────┬─────────┘  │
                          │            │            │
                          │  ┌─────────┴─────────┐  │
                          │  │                   │  │
                          │  ▼                   ▼  │
                          │ Nodes            Blobs  │
                          └───┬───────────────┬────┘
                              │               │
                              ▼               ▼
                     ┌─────────────┐   ┌─────────────┐
                     │ PostgreSQL  │   │    MinIO    │
                     │             │   │    (S3)     │
                     │ ┌─────────┐ │   │             │
                     │ │ NODES   │ │   │ oak-blobs/  │
                     │ │ JOURNAL │ │   │  └─blobs/   │
                     │ │ SETTINGS│ │   │    └─{sha}  │
                     │ │ LEASE   │ │   │             │
                     │ └─────────┘ │   └─────────────┘
                     └─────────────┘

    CLUSTER COORDINATION (Multi-Author)
    ────────────────────────────────────

    ┌─────────────────┐                    ┌─────────────────┐
    │   Author-0      │                    │   Author-1      │
    │  Cluster ID: 1  │                    │  Cluster ID: 2  │
    └────────┬────────┘                    └────────┬────────┘
             │                                      │
             │         ┌─────────────────┐          │
             └────────▶│   PostgreSQL    │◀─────────┘
                       │                 │
                       │ JOURNAL table   │  ← Shared operations log
                       │ LEASE table     │  ← Cluster membership
                       │                 │
                       └─────────────────┘
```

### Key Files

| File | Purpose |
|------|---------|
| `shared/oak-core-bundle/.../OakRepositoryInitializer.java` | Repository setup |
| `shared/oak-core-bundle/.../PostgresDataSourceFactory.java` | Database pool |
| `shared/oak-core-bundle/.../S3BlobStore.java` | Blob storage |
| `k8s/base/configmaps/oak-config.yaml` | Repository configuration |

### Important Code

**OakRepositoryInitializer.java:67-95** - Repository creation
```java
private void initializeRepository() {
    // PostgreSQL for node storage
    DataSource ds = PostgresDataSourceFactory.createDataSource(config);

    // MinIO for blob storage
    BlobStore blobStore = S3BlobStoreFactory.createBlobStore(config);

    // Build DocumentNodeStore
    DocumentNodeStore nodeStore = new RDBDocumentNodeStoreBuilder()
        .setRDBConnection(ds)
        .setClusterId(config.clusterId())
        .setBlobStore(blobStore)
        .setLeaseCheckMode(LeaseCheckMode.STRICT)
        .build();

    // Create repository with security
    Oak oak = new Oak(nodeStore)
        .with(new InitialContent())
        .with(new SecurityProviderImpl());

    this.repository = new Jcr(oak).createRepository();
}
```

**PostgresDataSourceFactory.java:45-65** - Connection pool
```java
public static DataSource createDataSource(OakRepositoryConfig config) {
    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setJdbcUrl(config.postgresUrl());
    hikariConfig.setUsername(config.postgresUser());
    hikariConfig.setPassword(config.postgresPassword());
    hikariConfig.setMaximumPoolSize(50);
    hikariConfig.setMinimumIdle(10);
    hikariConfig.setConnectionTimeout(30000);
    hikariConfig.setIdleTimeout(600000);
    hikariConfig.setMaxLifetime(1800000);
    return new HikariDataSource(hikariConfig);
}
```

---

## 7. Request Lifecycle Summary

```
    BROWSER                 INGRESS                PUBLISH                 STORAGE
       │                       │                      │                       │
       │  GET /content/page    │                      │                       │
       │──────────────────────▶│                      │                       │
       │                       │                      │                       │
       │                       │  route to publish    │                       │
       │                       │─────────────────────▶│                       │
       │                       │                      │                       │
       │                       │                      │  check local cache    │
       │                       │                      │─────────┐             │
       │                       │                      │         │             │
       │                       │                      │◀────────┘ MISS        │
       │                       │                      │                       │
       │                       │                      │  check Redis          │
       │                       │                      │──────────────────────▶│
       │                       │                      │◀──────────────────────│ MISS
       │                       │                      │                       │
       │                       │                      │  query PostgreSQL     │
       │                       │                      │──────────────────────▶│
       │                       │                      │◀──────────────────────│ data
       │                       │                      │                       │
       │                       │                      │  cache in Redis       │
       │                       │                      │──────────────────────▶│
       │                       │                      │                       │
       │                       │                      │  cache locally        │
       │                       │                      │─────────┐             │
       │                       │                      │◀────────┘             │
       │                       │                      │                       │
       │                       │  JSON + headers      │                       │
       │                       │◀─────────────────────│                       │
       │                       │                      │                       │
       │  JSON response        │                      │                       │
       │◀──────────────────────│                      │                       │
       │  Cache-Control: 300s  │                      │                       │
       │  ETag: "abc123"       │                      │                       │
       │                       │                      │                       │
```

---

## Configuration Quick Reference

| Component | Config File | Key Settings |
|-----------|-------------|--------------|
| Repository | `oak-config.yaml` | clusterId, cacheSize, instanceType |
| PostgreSQL | `postgres-secrets.yaml` | url, user, password |
| MinIO | `minio-secrets.yaml` | endpoint, accessKey, secretKey |
| Redis | `felix-publish.properties` | host, port, ttl, poolSize |
| Replication | `felix-author.properties` | endpoints, pollInterval, workers |
| Cache | `felix-publish.properties` | maxAge, staticMaxAge |

---

## Port Reference

| Service | Port | Protocol |
|---------|------|----------|
| Author HTTP | 8080 | HTTP |
| Publish HTTP | 8080 | HTTP |
| PostgreSQL | 5432 | TCP |
| MinIO API | 9000 | HTTP |
| MinIO Console | 9001 | HTTP |
| Redis | 6379 | TCP |
