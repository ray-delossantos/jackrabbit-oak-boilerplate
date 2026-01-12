package com.aem.oak.publish.content;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * Content delivery servlet for Publish tier.
 * Provides read-only access to content with caching support.
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/api/content",
        "sling.servlet.paths=/content",
        "sling.servlet.methods=GET"
    }
)
@Designate(ocd = ContentDeliveryServlet.Config.class)
public class ContentDeliveryServlet extends SlingSafeMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ContentDeliveryServlet.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ObjectClassDefinition(name = "AEM Oak Content Delivery Configuration")
    public @interface Config {
        @AttributeDefinition(name = "Cache Max Age (seconds)", description = "Cache-Control max-age header value")
        int cacheMaxAge() default 300;

        @AttributeDefinition(name = "Enable ETag", description = "Enable ETag-based caching")
        boolean enableETag() default true;

        @AttributeDefinition(name = "Max Depth", description = "Maximum depth for JSON serialization")
        int maxDepth() default 5;

        @AttributeDefinition(name = "Stream Buffer Size", description = "Buffer size for binary streaming")
        int streamBufferSize() default 8192;
    }

    @Reference
    private Repository repository;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private CacheService cacheService;

    private Config config;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.config = config;
        LOG.info("Content delivery servlet activated, cacheMaxAge={}", config.cacheMaxAge());
    }

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        String path = extractPath(request);
        String selector = request.getRequestPathInfo().getSelectorString();
        String extension = request.getRequestPathInfo().getExtension();

        LOG.debug("Content request: path={}, selector={}, extension={}", path, selector, extension);

        // Check cache first
        if (cacheService != null) {
            CacheService.CacheEntry cached = cacheService.get(path, selector, extension);
            if (cached != null) {
                sendCachedResponse(response, cached);
                return;
            }
        }

        Session session = null;
        try {
            session = repository.login(new SimpleCredentials("anonymous", "".toCharArray()));

            if (!session.nodeExists(path)) {
                sendNotFound(response, path);
                return;
            }

            Node node = session.getNode(path);

            // Check for binary content
            if (isBinaryRequest(node, extension)) {
                streamBinary(node, extension, response);
                return;
            }

            // Return JSON representation
            Map<String, Object> content = serializeNode(node, config.maxDepth());

            // Set cache headers
            setCacheHeaders(response, node);

            // Send response
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(200);

            String json = OBJECT_MAPPER.writeValueAsString(content);

            // Cache the response
            if (cacheService != null) {
                cacheService.put(path, selector, extension,
                        new CacheService.CacheEntry(json.getBytes(), "application/json"));
            }

            response.getWriter().write(json);

        } catch (RepositoryException e) {
            LOG.error("Error retrieving content at {}", path, e);
            sendError(response, 500, "Repository error: " + e.getMessage());
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private String extractPath(SlingHttpServletRequest request) {
        String path = request.getRequestPathInfo().getResourcePath();

        // Handle /api/content prefix
        if (path.startsWith("/api/content")) {
            path = path.substring("/api/content".length());
            if (path.isEmpty()) {
                path = "/content";
            }
        }

        // Remove extension and selectors for path resolution
        int dotIndex = path.indexOf('.');
        if (dotIndex > 0) {
            path = path.substring(0, dotIndex);
        }

        // Ensure valid path
        if (path.isEmpty() || !path.startsWith("/")) {
            path = "/content";
        }

        return path;
    }

    private boolean isBinaryRequest(Node node, String extension) throws RepositoryException {
        if (extension == null) {
            return false;
        }

        // Check for image/asset requests
        if (node.hasNode("jcr:content") &&
            node.getNode("jcr:content").hasProperty("jcr:data")) {
            return true;
        }

        // Check for rendition requests
        if (node.hasNode("jcr:content/renditions/original")) {
            return true;
        }

        return false;
    }

    private void streamBinary(Node node, String extension, SlingHttpServletResponse response)
            throws RepositoryException, IOException {

        Binary binary = null;
        String mimeType = "application/octet-stream";
        long size = -1;

        // Try to find binary content
        if (node.hasNode("jcr:content")) {
            Node content = node.getNode("jcr:content");

            // Check for direct binary
            if (content.hasProperty("jcr:data")) {
                binary = content.getProperty("jcr:data").getBinary();
                size = binary.getSize();

                if (content.hasProperty("jcr:mimeType")) {
                    mimeType = content.getProperty("jcr:mimeType").getString();
                }
            }
            // Check for renditions
            else if (content.hasNode("renditions/original")) {
                Node rendition = content.getNode("renditions/original");
                if (rendition.hasNode("jcr:content")) {
                    Node renditionContent = rendition.getNode("jcr:content");
                    if (renditionContent.hasProperty("jcr:data")) {
                        binary = renditionContent.getProperty("jcr:data").getBinary();
                        size = binary.getSize();

                        if (renditionContent.hasProperty("jcr:mimeType")) {
                            mimeType = renditionContent.getProperty("jcr:mimeType").getString();
                        }
                    }
                }
            }
        }

        if (binary == null) {
            sendError(response, 404, "Binary not found");
            return;
        }

        try {
            // Set response headers
            response.setContentType(mimeType);
            if (size >= 0) {
                response.setContentLengthLong(size);
            }

            // Set cache headers for assets
            response.setHeader("Cache-Control", "public, max-age=" + (config.cacheMaxAge() * 10));

            // Stream binary data
            try (InputStream in = binary.getStream();
                 OutputStream out = response.getOutputStream()) {

                byte[] buffer = new byte[config.streamBufferSize()];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            binary.dispose();
        }
    }

    private Map<String, Object> serializeNode(Node node, int depth) throws RepositoryException {
        Map<String, Object> result = new LinkedHashMap<>();

        // Add path and type
        result.put("jcr:path", node.getPath());
        result.put("jcr:primaryType", node.getPrimaryNodeType().getName());

        // Add properties
        PropertyIterator properties = node.getProperties();
        while (properties.hasNext()) {
            Property prop = properties.nextProperty();
            String name = prop.getName();

            // Skip binary data in JSON response
            if (prop.getType() == PropertyType.BINARY) {
                result.put(name, "[binary]");
                continue;
            }

            try {
                if (prop.isMultiple()) {
                    result.put(name, getMultipleValues(prop));
                } else {
                    result.put(name, getValue(prop.getValue()));
                }
            } catch (Exception e) {
                LOG.debug("Error serializing property {}: {}", name, e.getMessage());
            }
        }

        // Add children if depth allows
        if (depth > 0) {
            NodeIterator children = node.getNodes();
            if (children.hasNext()) {
                Map<String, Object> childMap = new LinkedHashMap<>();
                while (children.hasNext()) {
                    Node child = children.nextNode();
                    String childName = child.getName();

                    // Skip system nodes
                    if (childName.startsWith("rep:")) {
                        continue;
                    }

                    childMap.put(childName, serializeNode(child, depth - 1));
                }
                if (!childMap.isEmpty()) {
                    result.put(":children", childMap);
                }
            }
        }

        return result;
    }

    private List<Object> getMultipleValues(Property prop) throws RepositoryException {
        List<Object> values = new ArrayList<>();
        for (Value v : prop.getValues()) {
            values.add(getValue(v));
        }
        return values;
    }

    private Object getValue(Value value) throws RepositoryException {
        switch (value.getType()) {
            case PropertyType.BOOLEAN:
                return value.getBoolean();
            case PropertyType.LONG:
                return value.getLong();
            case PropertyType.DOUBLE:
                return value.getDouble();
            case PropertyType.DECIMAL:
                return value.getDecimal();
            case PropertyType.DATE:
                return value.getDate().toInstant().toString();
            default:
                return value.getString();
        }
    }

    private void setCacheHeaders(SlingHttpServletResponse response, Node node) throws RepositoryException {
        // Set Cache-Control
        response.setHeader("Cache-Control", "public, max-age=" + config.cacheMaxAge());

        // Generate ETag if enabled
        if (config.enableETag()) {
            String etag = generateETag(node);
            response.setHeader("ETag", "\"" + etag + "\"");
        }

        // Set Last-Modified if available
        if (node.hasProperty("jcr:lastModified")) {
            long lastModified = node.getProperty("jcr:lastModified").getDate().getTimeInMillis();
            response.setDateHeader("Last-Modified", lastModified);
        }
    }

    private String generateETag(Node node) throws RepositoryException {
        StringBuilder sb = new StringBuilder();
        sb.append(node.getPath());

        if (node.hasProperty("jcr:lastModified")) {
            sb.append(node.getProperty("jcr:lastModified").getString());
        }

        return Integer.toHexString(sb.toString().hashCode());
    }

    private void sendNotFound(SlingHttpServletResponse response, String path) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Not Found");
        error.put("path", path);
        error.put("status", 404);

        response.setContentType("application/json");
        response.setStatus(404);
        OBJECT_MAPPER.writeValue(response.getOutputStream(), error);
    }

    private void sendError(SlingHttpServletResponse response, int status, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("status", status);

        response.setContentType("application/json");
        response.setStatus(status);
        OBJECT_MAPPER.writeValue(response.getOutputStream(), error);
    }

    private void sendCachedResponse(SlingHttpServletResponse response, CacheService.CacheEntry entry)
            throws IOException {
        response.setContentType(entry.getContentType());
        response.setHeader("X-Cache", "HIT");
        response.getOutputStream().write(entry.getData());
    }
}
