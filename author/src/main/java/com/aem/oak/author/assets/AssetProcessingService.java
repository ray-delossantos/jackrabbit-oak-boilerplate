package com.aem.oak.author.assets;

import com.aem.oak.core.repository.JcrSessionFactory;
import net.coobird.thumbnailator.Thumbnails;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.Directory;
import com.drew.metadata.Tag;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import java.io.*;
import java.util.Calendar;
import java.util.concurrent.*;

/**
 * Service for asynchronous asset processing.
 * Generates thumbnails and extracts metadata from uploaded assets.
 */
@Component(service = AssetProcessingService.class, immediate = true)
public class AssetProcessingService {

    private static final Logger LOG = LoggerFactory.getLogger(AssetProcessingService.class);

    private static final String DAM_ASSET_CONTENT = "jcr:content";
    private static final String DAM_RENDITIONS = "renditions";
    private static final String DAM_METADATA = "metadata";
    private static final String DAM_ORIGINAL = "original";

    // Thumbnail sizes
    private static final int THUMBNAIL_WIDTH = 140;
    private static final int THUMBNAIL_HEIGHT = 100;
    private static final int WEB_WIDTH = 1280;
    private static final int WEB_HEIGHT = 1280;

    @Reference
    private JcrSessionFactory sessionFactory;

    private ExecutorService executorService;

    @Activate
    protected void activate() {
        executorService = Executors.newFixedThreadPool(4);
        LOG.info("Asset Processing Service activated");
    }

    @Deactivate
    protected void deactivate() {
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
        LOG.info("Asset Processing Service deactivated");
    }

    /**
     * Processes an asset asynchronously.
     */
    public void processAssetAsync(String assetPath) {
        executorService.submit(() -> {
            try {
                processAsset(assetPath);
            } catch (Exception e) {
                LOG.error("Error processing asset: {}", assetPath, e);
            }
        });
    }

    /**
     * Processes an asset synchronously.
     */
    public void processAsset(String assetPath) throws RepositoryException {
        LOG.info("Processing asset: {}", assetPath);

        sessionFactory.doWithSessionVoidAndSave(session -> {
            if (!session.nodeExists(assetPath)) {
                LOG.warn("Asset not found for processing: {}", assetPath);
                return;
            }

            Node assetNode = session.getNode(assetPath);
            Node contentNode = assetNode.getNode(DAM_ASSET_CONTENT);

            // Get MIME type
            String mimeType = "";
            if (contentNode.hasProperty("jcr:mimeType")) {
                mimeType = contentNode.getProperty("jcr:mimeType").getString();
            }

            // Get original data
            String originalPath = DAM_RENDITIONS + "/" + DAM_ORIGINAL + "/jcr:content";
            if (!contentNode.hasNode(originalPath)) {
                LOG.warn("Original rendition not found: {}", assetPath);
                return;
            }

            Node originalContent = contentNode.getNode(originalPath);
            Binary originalBinary = originalContent.getProperty("jcr:data").getBinary();

            // Process based on MIME type
            if (mimeType.startsWith("image/")) {
                processImage(session, contentNode, originalBinary, mimeType);
            }

            // Extract metadata
            try {
                extractMetadata(session, contentNode, originalBinary);
            } catch (Exception e) {
                LOG.warn("Could not extract metadata for: {}", assetPath, e);
            }

            // Update processing status
            contentNode.setProperty("dam:processingStatus", "completed");
            contentNode.setProperty("dam:lastProcessed", Calendar.getInstance());

            LOG.info("Asset processing completed: {}", assetPath);
        });
    }

    /**
     * Processes an image asset - generates thumbnails.
     */
    private void processImage(Session session, Node contentNode, Binary originalBinary, String mimeType)
            throws RepositoryException {
        LOG.debug("Processing image");

        Node renditionsNode = contentNode.getNode(DAM_RENDITIONS);

        try (InputStream originalStream = originalBinary.getStream()) {
            // Generate thumbnail
            generateThumbnail(session, renditionsNode, originalStream,
                    "cq5dam.thumbnail.140.100.png", THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT);
        } catch (IOException e) {
            LOG.warn("Error generating thumbnail", e);
        }

        try (InputStream originalStream = originalBinary.getStream()) {
            // Generate web rendition
            generateThumbnail(session, renditionsNode, originalStream,
                    "cq5dam.web.1280.1280.jpeg", WEB_WIDTH, WEB_HEIGHT);
        } catch (IOException e) {
            LOG.warn("Error generating web rendition", e);
        }
    }

    /**
     * Generates a thumbnail rendition.
     */
    private void generateThumbnail(Session session, Node renditionsNode, InputStream source,
                                    String renditionName, int width, int height)
            throws RepositoryException, IOException {
        LOG.debug("Generating thumbnail: {} ({}x{})", renditionName, width, height);

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        String format = renditionName.endsWith(".png") ? "png" : "jpeg";
        Thumbnails.of(source)
                .size(width, height)
                .keepAspectRatio(true)
                .outputFormat(format)
                .toOutputStream(output);

        // Create rendition node
        Node renditionNode;
        if (renditionsNode.hasNode(renditionName)) {
            renditionNode = renditionsNode.getNode(renditionName);
        } else {
            renditionNode = renditionsNode.addNode(renditionName, "nt:file");
        }

        Node contentNode;
        if (renditionNode.hasNode("jcr:content")) {
            contentNode = renditionNode.getNode("jcr:content");
        } else {
            contentNode = renditionNode.addNode("jcr:content", "nt:resource");
        }

        // Store binary
        byte[] data = output.toByteArray();
        Binary binary = session.getValueFactory().createBinary(new ByteArrayInputStream(data));
        contentNode.setProperty("jcr:data", binary);
        contentNode.setProperty("jcr:mimeType", format.equals("png") ? "image/png" : "image/jpeg");
        contentNode.setProperty("jcr:lastModified", Calendar.getInstance());

        LOG.debug("Thumbnail generated: {} ({} bytes)", renditionName, data.length);
    }

    /**
     * Extracts metadata from an asset using metadata-extractor library.
     */
    private void extractMetadata(Session session, Node contentNode, Binary binary)
            throws Exception {
        LOG.debug("Extracting metadata");

        Node metadataNode;
        if (contentNode.hasNode(DAM_METADATA)) {
            metadataNode = contentNode.getNode(DAM_METADATA);
        } else {
            metadataNode = contentNode.addNode(DAM_METADATA, "nt:unstructured");
        }

        try (InputStream stream = binary.getStream()) {
            Metadata metadata = ImageMetadataReader.readMetadata(stream);

            for (Directory directory : metadata.getDirectories()) {
                String prefix = directory.getName().toLowerCase().replace(" ", "_");

                for (Tag tag : directory.getTags()) {
                    String tagName = tag.getTagName().replace(" ", "");
                    String propertyName = prefix + ":" + tagName;
                    String description = tag.getDescription();

                    if (description != null && !description.isEmpty()) {
                        // Handle specific metadata fields
                        if (tagName.equals("ImageWidth") || tagName.contains("Width")) {
                            try {
                                metadataNode.setProperty("tiff:ImageWidth", Long.parseLong(description.split(" ")[0]));
                            } catch (NumberFormatException e) {
                                metadataNode.setProperty(propertyName, description);
                            }
                        } else if (tagName.equals("ImageHeight") || tagName.equals("ImageLength") || tagName.contains("Height")) {
                            try {
                                metadataNode.setProperty("tiff:ImageLength", Long.parseLong(description.split(" ")[0]));
                            } catch (NumberFormatException e) {
                                metadataNode.setProperty(propertyName, description);
                            }
                        } else {
                            // Truncate long values
                            if (description.length() > 500) {
                                description = description.substring(0, 500) + "...";
                            }
                            metadataNode.setProperty(propertyName, description);
                        }
                    }
                }
            }

            LOG.debug("Metadata extracted successfully");
        }
    }
}
