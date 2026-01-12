package com.aem.oak.author.replication;

import com.aem.oak.api.replication.ReplicationService.ReplicationAction;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Content package for replication between Author and Publish instances.
 * Serializes JCR content to a portable format that can be transferred over HTTP.
 */
public class ContentPackage {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String id;
    private final String path;
    private final ReplicationAction action;
    private final long timestamp;
    private final String authorId;
    private final NodeData rootNode;
    private final Map<String, byte[]> binaries;

    @JsonCreator
    public ContentPackage(
            @JsonProperty("id") String id,
            @JsonProperty("path") String path,
            @JsonProperty("action") ReplicationAction action,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("authorId") String authorId,
            @JsonProperty("rootNode") NodeData rootNode,
            @JsonProperty("binaries") Map<String, byte[]> binaries) {
        this.id = id;
        this.path = path;
        this.action = action;
        this.timestamp = timestamp;
        this.authorId = authorId;
        this.rootNode = rootNode;
        this.binaries = binaries != null ? binaries : new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public String getPath() {
        return path;
    }

    public ReplicationAction getAction() {
        return action;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getAuthorId() {
        return authorId;
    }

    public NodeData getRootNode() {
        return rootNode;
    }

    public Map<String, byte[]> getBinaries() {
        return binaries;
    }

    /**
     * Serialize package to compressed bytes for network transfer.
     */
    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            OBJECT_MAPPER.writeValue(gzip, this);
        }
        return baos.toByteArray();
    }

    /**
     * Deserialize package from compressed bytes.
     */
    public static ContentPackage fromBytes(byte[] data) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return OBJECT_MAPPER.readValue(gzip, ContentPackage.class);
        }
    }

    /**
     * Deserialize package from input stream.
     */
    public static ContentPackage fromStream(InputStream in) throws IOException {
        return fromBytes(in.readAllBytes());
    }

    /**
     * Create a content package from a JCR node.
     */
    public static ContentPackage create(Node node, ReplicationAction action, String authorId)
            throws RepositoryException, IOException {
        String id = UUID.randomUUID().toString();
        String path = node.getPath();
        long timestamp = System.currentTimeMillis();
        Map<String, byte[]> binaries = new HashMap<>();

        NodeData rootNode = serializeNode(node, binaries);

        return new ContentPackage(id, path, action, timestamp, authorId, rootNode, binaries);
    }

    /**
     * Create a delete package (no node data needed).
     */
    public static ContentPackage createDelete(String path, String authorId) {
        String id = UUID.randomUUID().toString();
        long timestamp = System.currentTimeMillis();

        return new ContentPackage(id, path, ReplicationAction.DELETE, timestamp, authorId, null, null);
    }

    private static NodeData serializeNode(Node node, Map<String, byte[]> binaries)
            throws RepositoryException, IOException {
        String name = node.getName();
        String primaryType = node.getPrimaryNodeType().getName();

        // Serialize properties
        Map<String, PropertyData> properties = new HashMap<>();
        PropertyIterator propIter = node.getProperties();
        while (propIter.hasNext()) {
            Property prop = propIter.nextProperty();
            String propName = prop.getName();

            // Skip protected properties that will be auto-generated
            if (propName.equals("jcr:uuid") || propName.equals("jcr:created") ||
                propName.equals("jcr:createdBy")) {
                continue;
            }

            PropertyData propData = serializeProperty(prop, binaries);
            if (propData != null) {
                properties.put(propName, propData);
            }
        }

        // Serialize child nodes recursively
        List<NodeData> children = new ArrayList<>();
        var nodeIter = node.getNodes();
        while (nodeIter.hasNext()) {
            Node child = nodeIter.nextNode();
            children.add(serializeNode(child, binaries));
        }

        return new NodeData(name, primaryType, properties, children);
    }

    private static PropertyData serializeProperty(Property prop, Map<String, byte[]> binaries)
            throws RepositoryException, IOException {
        int type = prop.getType();
        boolean isMultiple = prop.isMultiple();

        if (isMultiple) {
            Value[] values = prop.getValues();
            List<String> serializedValues = new ArrayList<>();
            for (Value value : values) {
                serializedValues.add(serializeValue(value, type, binaries));
            }
            return new PropertyData(type, true, serializedValues);
        } else {
            Value value = prop.getValue();
            String serializedValue = serializeValue(value, type, binaries);
            return new PropertyData(type, false, List.of(serializedValue));
        }
    }

    private static String serializeValue(Value value, int type, Map<String, byte[]> binaries)
            throws RepositoryException, IOException {
        switch (type) {
            case PropertyType.BINARY:
                // Store binary in separate map, return reference
                String binaryId = UUID.randomUUID().toString();
                try (InputStream in = value.getBinary().getStream()) {
                    binaries.put(binaryId, in.readAllBytes());
                }
                return "binary:" + binaryId;

            case PropertyType.DATE:
                return value.getDate().toInstant().toString();

            case PropertyType.BOOLEAN:
                return String.valueOf(value.getBoolean());

            case PropertyType.LONG:
                return String.valueOf(value.getLong());

            case PropertyType.DOUBLE:
                return String.valueOf(value.getDouble());

            case PropertyType.DECIMAL:
                return value.getDecimal().toString();

            default:
                return value.getString();
        }
    }

    /**
     * Represents serialized node data.
     */
    public static class NodeData {
        private final String name;
        private final String primaryType;
        private final Map<String, PropertyData> properties;
        private final List<NodeData> children;

        @JsonCreator
        public NodeData(
                @JsonProperty("name") String name,
                @JsonProperty("primaryType") String primaryType,
                @JsonProperty("properties") Map<String, PropertyData> properties,
                @JsonProperty("children") List<NodeData> children) {
            this.name = name;
            this.primaryType = primaryType;
            this.properties = properties != null ? properties : new HashMap<>();
            this.children = children != null ? children : new ArrayList<>();
        }

        public String getName() { return name; }
        public String getPrimaryType() { return primaryType; }
        public Map<String, PropertyData> getProperties() { return properties; }
        public List<NodeData> getChildren() { return children; }
    }

    /**
     * Represents serialized property data.
     */
    public static class PropertyData {
        private final int type;
        private final boolean multiple;
        private final List<String> values;

        @JsonCreator
        public PropertyData(
                @JsonProperty("type") int type,
                @JsonProperty("multiple") boolean multiple,
                @JsonProperty("values") List<String> values) {
            this.type = type;
            this.multiple = multiple;
            this.values = values != null ? values : new ArrayList<>();
        }

        public int getType() { return type; }
        public boolean isMultiple() { return multiple; }
        public List<String> getValues() { return values; }
    }
}
