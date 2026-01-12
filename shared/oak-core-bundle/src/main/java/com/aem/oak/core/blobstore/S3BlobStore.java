package com.aem.oak.core.blobstore;

import org.apache.jackrabbit.oak.spi.blob.BlobStore;
import org.apache.jackrabbit.oak.spi.blob.BlobOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom BlobStore implementation backed by S3 (MinIO compatible).
 * Stores blobs in S3 with SHA-256 content addressing.
 */
public class S3BlobStore implements BlobStore {

    private static final Logger LOG = LoggerFactory.getLogger(S3BlobStore.class);
    private static final int CHUNK_SIZE = 2 * 1024 * 1024; // 2MB chunks
    private static final String PREFIX = "blobs/";

    private final S3Client s3Client;
    private final String bucket;
    private final ConcurrentHashMap<String, Long> blobLengthCache = new ConcurrentHashMap<>();

    public S3BlobStore(S3Client s3Client, String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Override
    public String writeBlob(InputStream in) throws IOException {
        return writeBlob(in, new BlobOptions());
    }

    @Override
    public String writeBlob(InputStream in, BlobOptions options) throws IOException {
        try {
            // Read the entire stream to calculate hash
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] data = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
                digest.update(data, 0, bytesRead);
            }

            byte[] content = buffer.toByteArray();
            String blobId = bytesToHex(digest.digest());

            // Check if blob already exists
            String key = PREFIX + blobId;
            if (!blobExists(key)) {
                // Upload to S3
                s3Client.putObject(
                        PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(key)
                                .contentLength((long) content.length)
                                .build(),
                        RequestBody.fromBytes(content)
                );
                LOG.debug("Uploaded blob: {} ({} bytes)", blobId, content.length);
            }

            blobLengthCache.put(blobId, (long) content.length);
            return blobId;

        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 not available", e);
        }
    }

    @Override
    public int readBlob(String blobId, long pos, byte[] buff, int off, int length) throws IOException {
        String key = PREFIX + blobId;

        try {
            // Calculate range
            long end = pos + length - 1;
            String range = "bytes=" + pos + "-" + end;

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .range(range)
                    .build();

            try (InputStream is = s3Client.getObject(request, ResponseTransformer.toInputStream())) {
                int totalRead = 0;
                int bytesRead;
                while (totalRead < length && (bytesRead = is.read(buff, off + totalRead, length - totalRead)) != -1) {
                    totalRead += bytesRead;
                }
                return totalRead;
            }

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IOException("Blob not found: " + blobId);
            }
            throw new IOException("Failed to read blob: " + blobId, e);
        }
    }

    @Override
    public long getBlobLength(String blobId) throws IOException {
        // Check cache first
        Long cachedLength = blobLengthCache.get(blobId);
        if (cachedLength != null) {
            return cachedLength;
        }

        String key = PREFIX + blobId;
        try {
            HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );
            long length = response.contentLength();
            blobLengthCache.put(blobId, length);
            return length;

        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IOException("Blob not found: " + blobId);
            }
            throw new IOException("Failed to get blob length: " + blobId, e);
        }
    }

    @Override
    public InputStream getInputStream(String blobId) throws IOException {
        String key = PREFIX + blobId;
        try {
            return s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build(),
                    ResponseTransformer.toInputStream()
            );
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new IOException("Blob not found: " + blobId);
            }
            throw new IOException("Failed to get blob stream: " + blobId, e);
        }
    }

    @Override
    public String getBlobId(String reference) {
        // In this implementation, reference equals blobId
        return reference;
    }

    @Override
    public String getReference(String blobId) {
        // In this implementation, reference equals blobId
        return blobId;
    }

    /**
     * Deletes a blob from S3.
     */
    public void deleteBlob(String blobId) {
        String key = PREFIX + blobId;
        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );
            blobLengthCache.remove(blobId);
            LOG.debug("Deleted blob: {}", blobId);
        } catch (S3Exception e) {
            LOG.warn("Failed to delete blob: {}", blobId, e);
        }
    }

    /**
     * Checks if a blob exists in S3.
     */
    private boolean blobExists(String key) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build()
            );
            return true;
        } catch (S3Exception e) {
            return false;
        }
    }

    /**
     * Lists all blob IDs.
     */
    public Iterator<String> getAllBlobIds() {
        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(PREFIX)
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        return response.contents().stream()
                .map(S3Object::key)
                .map(key -> key.substring(PREFIX.length()))
                .iterator();
    }

    /**
     * Closes the blob store.
     */
    public void close() {
        blobLengthCache.clear();
        LOG.info("S3BlobStore closed");
    }

    /**
     * Converts bytes to hex string.
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
