package com.aem.oak.author.content;

import com.aem.oak.api.content.ContentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.*;

/**
 * REST API servlet for content management operations.
 * Provides endpoints for CRUD operations on JCR content.
 *
 * Endpoints:
 * - GET /api/content?path=/content/path - Get content
 * - POST /api/content - Create content
 * - PUT /api/content - Update content
 * - DELETE /api/content?path=/content/path - Delete content
 */
@Component(
    service = Servlet.class,
    property = {
        "osgi.http.whiteboard.servlet.pattern=/api/content/*",
        "osgi.http.whiteboard.servlet.name=ContentServlet"
    }
)
public class ContentServlet extends HttpServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ContentServlet.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Reference
    private ContentService contentService;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = getPathParameter(request);

        if (path == null || path.isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'path' parameter");
            return;
        }

        try {
            Node node = contentService.getContent(path);

            if (node == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Content not found: " + path);
                return;
            }

            // Check for children parameter
            boolean includeChildren = "true".equals(request.getParameter("children"));
            int depth = parseIntParam(request, "depth", 1);

            ObjectNode json = nodeToJson(node, depth, includeChildren);
            sendJson(response, json);

        } catch (RepositoryException e) {
            LOG.error("Error getting content at: {}", path, e);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());

            String path = body.has("path") ? body.get("path").asText() : null;
            String nodeType = body.has("nodeType") ? body.get("nodeType").asText() : "nt:unstructured";

            if (path == null || path.isEmpty()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'path' in request body");
                return;
            }

            // Check if content already exists
            if (contentService.exists(path)) {
                sendError(response, HttpServletResponse.SC_CONFLICT, "Content already exists: " + path);
                return;
            }

            // Extract properties
            Map<String, Object> properties = extractProperties(body);

            // Create content
            Node node = contentService.createContent(path, nodeType, properties);

            // Return created content
            response.setStatus(HttpServletResponse.SC_CREATED);
            sendJson(response, nodeToJson(node, 1, false));

        } catch (RepositoryException e) {
            LOG.error("Error creating content", e);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            JsonNode body = MAPPER.readTree(request.getInputStream());

            String path = body.has("path") ? body.get("path").asText() : getPathParameter(request);

            if (path == null || path.isEmpty()) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'path'");
                return;
            }

            // Check if content exists
            if (!contentService.exists(path)) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Content not found: " + path);
                return;
            }

            // Extract properties
            Map<String, Object> properties = extractProperties(body);

            // Update content
            Node node = contentService.updateContent(path, properties);

            sendJson(response, nodeToJson(node, 1, false));

        } catch (RepositoryException e) {
            LOG.error("Error updating content", e);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = getPathParameter(request);

        if (path == null || path.isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Missing 'path' parameter");
            return;
        }

        try {
            // Check if content exists
            if (!contentService.exists(path)) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Content not found: " + path);
                return;
            }

            contentService.deleteContent(path);

            ObjectNode json = MAPPER.createObjectNode();
            json.put("status", "deleted");
            json.put("path", path);
            sendJson(response, json);

        } catch (RepositoryException e) {
            LOG.error("Error deleting content at: {}", path, e);
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    /**
     * Converts a JCR Node to JSON.
     */
    private ObjectNode nodeToJson(Node node, int depth, boolean includeChildren)
            throws RepositoryException {
        ObjectNode json = MAPPER.createObjectNode();

        json.put("path", node.getPath());
        json.put("name", node.getName());
        json.put("primaryType", node.getPrimaryNodeType().getName());

        // Add properties
        ObjectNode properties = MAPPER.createObjectNode();
        PropertyIterator propIter = node.getProperties();
        while (propIter.hasNext()) {
            Property prop = propIter.nextProperty();
            String propName = prop.getName();

            // Skip binary and system properties for basic response
            if (prop.getType() == PropertyType.BINARY) {
                properties.put(propName, "[binary]");
            } else if (prop.isMultiple()) {
                ArrayNode array = MAPPER.createArrayNode();
                for (Value value : prop.getValues()) {
                    array.add(valueToString(value));
                }
                properties.set(propName, array);
            } else {
                properties.put(propName, valueToString(prop.getValue()));
            }
        }
        json.set("properties", properties);

        // Add children if requested
        if (includeChildren && depth > 0 && node.hasNodes()) {
            ArrayNode children = MAPPER.createArrayNode();
            NodeIterator nodeIter = node.getNodes();
            while (nodeIter.hasNext()) {
                Node child = nodeIter.nextNode();
                children.add(nodeToJson(child, depth - 1, includeChildren));
            }
            json.set("children", children);
        }

        return json;
    }

    /**
     * Converts a JCR Value to String.
     */
    private String valueToString(Value value) throws RepositoryException {
        switch (value.getType()) {
            case PropertyType.DATE:
                return value.getDate().toInstant().toString();
            case PropertyType.BINARY:
                return "[binary]";
            default:
                return value.getString();
        }
    }

    /**
     * Extracts properties from JSON body.
     */
    private Map<String, Object> extractProperties(JsonNode body) {
        Map<String, Object> properties = new HashMap<>();

        if (body.has("properties")) {
            JsonNode propsNode = body.get("properties");
            Iterator<String> fieldNames = propsNode.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                JsonNode value = propsNode.get(fieldName);
                properties.put(fieldName, jsonToValue(value));
            }
        }

        return properties;
    }

    /**
     * Converts JSON value to Java object.
     */
    private Object jsonToValue(JsonNode node) {
        if (node.isNull()) {
            return null;
        } else if (node.isTextual()) {
            return node.asText();
        } else if (node.isNumber()) {
            if (node.isInt() || node.isLong()) {
                return node.asLong();
            } else {
                return node.asDouble();
            }
        } else if (node.isBoolean()) {
            return node.asBoolean();
        } else if (node.isArray()) {
            List<String> list = new ArrayList<>();
            for (JsonNode item : node) {
                list.add(item.asText());
            }
            return list;
        }
        return node.toString();
    }

    /**
     * Gets the path parameter from request.
     */
    private String getPathParameter(HttpServletRequest request) {
        // Try query parameter first
        String path = request.getParameter("path");
        if (path != null && !path.isEmpty()) {
            return path;
        }

        // Try path info
        String pathInfo = request.getPathInfo();
        if (pathInfo != null && !pathInfo.isEmpty() && !"/".equals(pathInfo)) {
            return pathInfo;
        }

        return null;
    }

    /**
     * Parses an integer parameter with default value.
     */
    private int parseIntParam(HttpServletRequest request, String param, int defaultValue) {
        String value = request.getParameter(param);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * Sends JSON response.
     */
    private void sendJson(HttpServletResponse response, ObjectNode json) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getOutputStream(), json);
    }

    /**
     * Sends error response.
     */
    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        ObjectNode json = MAPPER.createObjectNode();
        json.put("error", true);
        json.put("status", status);
        json.put("message", message);

        MAPPER.writeValue(response.getOutputStream(), json);
    }
}
