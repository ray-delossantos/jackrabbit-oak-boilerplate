package com.aem.oak.publish.replication;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.service.component.annotations.*;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Servlet that receives replicated content from Author instances.
 * Handles content import and maintains synchronization with Author.
 */
@Component(
    service = Servlet.class,
    property = {
        "sling.servlet.paths=/bin/replicate",
        "sling.servlet.methods=POST"
    }
)
@Designate(ocd = ReplicationReceiver.Config.class)
public class ReplicationReceiver extends SlingAllMethodsServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ReplicationReceiver.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @ObjectClassDefinition(name = "AEM Oak Replication Receiver Configuration")
    public @interface Config {
        @AttributeDefinition(name = "Enabled", description = "Enable replication receiver")
        boolean enabled() default true;

        @AttributeDefinition(name = "Auth Token", description = "Required authentication token for replication")
        String authToken() default "";

        @AttributeDefinition(name = "Allowed Authors", description = "Allowed author IDs (empty = all)")
        String[] allowedAuthors() default {};
    }

    @Reference
    private Repository repository;

    private Config config;

    @Activate
    @Modified
    protected void activate(Config config) {
        this.config = config;
        LOG.info("Replication receiver activated, enabled={}", config.enabled());
    }

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response)
            throws ServletException, IOException {

        if (!config.enabled()) {
            sendError(response, 503, "Replication receiver is disabled");
            return;
        }

        // Authenticate request
        if (!authenticateRequest(request)) {
            sendError(response, 403, "Unauthorized replication request");
            return;
        }

        // Read package data
        byte[] packageData = request.getInputStream().readAllBytes();
        if (packageData.length == 0) {
            // For DELETE without body, check headers
            String action = request.getHeader("X-Replication-Action");
            String path = request.getHeader("X-Replication-Path");

            if ("DELETE".equals(action) && path != null) {
                handleDelete(path, response);
                return;
            }

            sendError(response, 400, "No package data received");
            return;
        }

        try {
            // Decompress and parse package
            ContentPackage pkg = ContentPackage.fromBytes(packageData);

            LOG.info("Received replication package: id={}, path={}, action={}",
                    pkg.getId(), pkg.getPath(), pkg.getAction());

            // Validate author
            if (config.allowedAuthors() != null && config.allowedAuthors().length > 0) {
                boolean allowed = false;
                for (String authorId : config.allowedAuthors()) {
                    if (authorId.equals(pkg.getAuthorId())) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    sendError(response, 403, "Author not allowed: " + pkg.getAuthorId());
                    return;
                }
            }

            // Process based on action
            switch (pkg.getAction()) {
                case ACTIVATE:
                    handleActivate(pkg, response);
                    break;
                case DEACTIVATE:
                case DELETE:
                    handleDelete(pkg.getPath(), response);
                    break;
                default:
                    sendError(response, 400, "Unknown action: " + pkg.getAction());
            }

        } catch (Exception e) {
            LOG.error("Failed to process replication package", e);
            sendError(response, 500, "Failed to process package: " + e.getMessage());
        }
    }

    private boolean authenticateRequest(SlingHttpServletRequest request) {
        String expectedToken = config.authToken();

        // If no token configured, authentication is not required
        if (expectedToken == null || expectedToken.isEmpty()) {
            return true;
        }

        String providedToken = request.getHeader("X-Replication-Token");
        return expectedToken.equals(providedToken);
    }

    private void handleActivate(ContentPackage pkg, SlingHttpServletResponse response) throws IOException {
        Session session = null;
        try {
            session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));

            // Import content
            importNode(session, pkg.getPath(), pkg.getRootNode(), pkg.getBinaries());

            session.save();

            LOG.info("Successfully imported content at: {}", pkg.getPath());

            // Send success response
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("path", pkg.getPath());
            result.put("packageId", pkg.getId());

            response.setContentType("application/json");
            response.setStatus(200);
            OBJECT_MAPPER.writeValue(response.getOutputStream(), result);

        } catch (RepositoryException e) {
            LOG.error("Failed to import content at {}", pkg.getPath(), e);
            sendError(response, 500, "Repository error: " + e.getMessage());
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private void handleDelete(String path, SlingHttpServletResponse response) throws IOException {
        Session session = null;
        try {
            session = repository.login(new SimpleCredentials("admin", "admin".toCharArray()));

            if (session.nodeExists(path)) {
                session.getNode(path).remove();
                session.save();
                LOG.info("Deleted content at: {}", path);
            } else {
                LOG.info("Content already deleted or doesn't exist: {}", path);
            }

            // Send success response
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("path", path);
            result.put("action", "deleted");

            response.setContentType("application/json");
            response.setStatus(200);
            OBJECT_MAPPER.writeValue(response.getOutputStream(), result);

        } catch (RepositoryException e) {
            LOG.error("Failed to delete content at {}", path, e);
            sendError(response, 500, "Repository error: " + e.getMessage());
        } finally {
            if (session != null) {
                session.logout();
            }
        }
    }

    private void importNode(Session session, String path, ContentPackage.NodeData nodeData,
                           Map<String, byte[]> binaries) throws RepositoryException, IOException {

        // Get or create parent path
        String parentPath = path.substring(0, path.lastIndexOf('/'));
        if (parentPath.isEmpty()) {
            parentPath = "/";
        }

        Node parent;
        if (session.nodeExists(parentPath)) {
            parent = session.getNode(parentPath);
        } else {
            parent = createPath(session, parentPath);
        }

        // Create or update node
        String nodeName = nodeData.getName();
        Node node;

        if (parent.hasNode(nodeName)) {
            node = parent.getNode(nodeName);
            // Remove existing children to replace with new content
            var children = node.getNodes();
            while (children.hasNext()) {
                children.nextNode().remove();
            }
        } else {
            node = parent.addNode(nodeName, nodeData.getPrimaryType());
        }

        // Set properties
        for (Map.Entry<String, ContentPackage.PropertyData> entry : nodeData.getProperties().entrySet()) {
            setProperty(session, node, entry.getKey(), entry.getValue(), binaries);
        }

        // Create child nodes recursively
        for (ContentPackage.NodeData childData : nodeData.getChildren()) {
            importChildNode(session, node, childData, binaries);
        }
    }

    private void importChildNode(Session session, Node parent, ContentPackage.NodeData nodeData,
                                Map<String, byte[]> binaries) throws RepositoryException, IOException {

        String nodeName = nodeData.getName();
        Node node;

        if (parent.hasNode(nodeName)) {
            node = parent.getNode(nodeName);
        } else {
            node = parent.addNode(nodeName, nodeData.getPrimaryType());
        }

        // Set properties
        for (Map.Entry<String, ContentPackage.PropertyData> entry : nodeData.getProperties().entrySet()) {
            setProperty(session, node, entry.getKey(), entry.getValue(), binaries);
        }

        // Create child nodes recursively
        for (ContentPackage.NodeData childData : nodeData.getChildren()) {
            importChildNode(session, node, childData, binaries);
        }
    }

    private void setProperty(Session session, Node node, String name,
                            ContentPackage.PropertyData propData, Map<String, byte[]> binaries)
            throws RepositoryException, IOException {

        ValueFactory valueFactory = session.getValueFactory();
        int type = propData.getType();
        List<String> values = propData.getValues();

        if (propData.isMultiple()) {
            Value[] jcrValues = new Value[values.size()];
            for (int i = 0; i < values.size(); i++) {
                jcrValues[i] = createValue(valueFactory, values.get(i), type, binaries);
            }
            node.setProperty(name, jcrValues);
        } else if (!values.isEmpty()) {
            Value value = createValue(valueFactory, values.get(0), type, binaries);
            node.setProperty(name, value);
        }
    }

    private Value createValue(ValueFactory valueFactory, String serialized, int type,
                             Map<String, byte[]> binaries) throws RepositoryException, IOException {

        switch (type) {
            case PropertyType.BINARY:
                if (serialized.startsWith("binary:")) {
                    String binaryId = serialized.substring(7);
                    byte[] data = binaries.get(binaryId);
                    if (data != null) {
                        Binary binary = valueFactory.createBinary(new ByteArrayInputStream(data));
                        return valueFactory.createValue(binary);
                    }
                }
                return valueFactory.createValue("");

            case PropertyType.DATE:
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(Instant.parse(serialized).toEpochMilli());
                return valueFactory.createValue(cal);

            case PropertyType.BOOLEAN:
                return valueFactory.createValue(Boolean.parseBoolean(serialized));

            case PropertyType.LONG:
                return valueFactory.createValue(Long.parseLong(serialized));

            case PropertyType.DOUBLE:
                return valueFactory.createValue(Double.parseDouble(serialized));

            case PropertyType.DECIMAL:
                return valueFactory.createValue(new BigDecimal(serialized));

            case PropertyType.NAME:
                return valueFactory.createValue(serialized, PropertyType.NAME);

            case PropertyType.PATH:
                return valueFactory.createValue(serialized, PropertyType.PATH);

            case PropertyType.REFERENCE:
                return valueFactory.createValue(serialized, PropertyType.REFERENCE);

            case PropertyType.WEAKREFERENCE:
                return valueFactory.createValue(serialized, PropertyType.WEAKREFERENCE);

            case PropertyType.URI:
                return valueFactory.createValue(serialized, PropertyType.URI);

            default:
                return valueFactory.createValue(serialized);
        }
    }

    private Node createPath(Session session, String path) throws RepositoryException {
        if (path.equals("/")) {
            return session.getRootNode();
        }

        String[] segments = path.split("/");
        Node current = session.getRootNode();

        for (String segment : segments) {
            if (segment.isEmpty()) continue;

            if (current.hasNode(segment)) {
                current = current.getNode(segment);
            } else {
                current = current.addNode(segment, "sling:Folder");
            }
        }

        return current;
    }

    private void sendError(SlingHttpServletResponse response, int status, String message) throws IOException {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);

        response.setContentType("application/json");
        response.setStatus(status);
        OBJECT_MAPPER.writeValue(response.getOutputStream(), error);
    }

    /**
     * Inner class for deserializing content packages.
     * This duplicates the ContentPackage structure for the publish side.
     */
    public static class ContentPackage {
        private String id;
        private String path;
        private String action;
        private long timestamp;
        private String authorId;
        private NodeData rootNode;
        private Map<String, byte[]> binaries;

        public static ContentPackage fromBytes(byte[] data) throws IOException {
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
                return OBJECT_MAPPER.readValue(gzip, ContentPackage.class);
            }
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getAuthorId() { return authorId; }
        public void setAuthorId(String authorId) { this.authorId = authorId; }
        public NodeData getRootNode() { return rootNode; }
        public void setRootNode(NodeData rootNode) { this.rootNode = rootNode; }
        public Map<String, byte[]> getBinaries() { return binaries != null ? binaries : new HashMap<>(); }
        public void setBinaries(Map<String, byte[]> binaries) { this.binaries = binaries; }
    }

    public static class NodeData {
        private String name;
        private String primaryType;
        private Map<String, PropertyData> properties;
        private List<NodeData> children;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPrimaryType() { return primaryType != null ? primaryType : "nt:unstructured"; }
        public void setPrimaryType(String primaryType) { this.primaryType = primaryType; }
        public Map<String, PropertyData> getProperties() { return properties != null ? properties : new HashMap<>(); }
        public void setProperties(Map<String, PropertyData> properties) { this.properties = properties; }
        public List<NodeData> getChildren() { return children != null ? children : List.of(); }
        public void setChildren(List<NodeData> children) { this.children = children; }
    }

    public static class PropertyData {
        private int type;
        private boolean multiple;
        private List<String> values;

        public int getType() { return type; }
        public void setType(int type) { this.type = type; }
        public boolean isMultiple() { return multiple; }
        public void setMultiple(boolean multiple) { this.multiple = multiple; }
        public List<String> getValues() { return values != null ? values : List.of(); }
        public void setValues(List<String> values) { this.values = values; }
    }
}
