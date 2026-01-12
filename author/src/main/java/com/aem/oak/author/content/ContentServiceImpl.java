package com.aem.oak.author.content;

import com.aem.oak.api.content.ContentService;
import com.aem.oak.core.repository.JcrSessionFactory;
import org.apache.jackrabbit.commons.JcrUtils;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.util.*;

/**
 * Implementation of ContentService for JCR content management.
 * Provides CRUD operations for content nodes in the repository.
 */
@Component(service = ContentService.class, immediate = true)
public class ContentServiceImpl implements ContentService {

    private static final Logger LOG = LoggerFactory.getLogger(ContentServiceImpl.class);

    @Reference
    private JcrSessionFactory sessionFactory;

    @Override
    public Node createContent(String path, String nodeType, Map<String, Object> properties)
            throws RepositoryException {
        LOG.debug("Creating content at path: {} with type: {}", path, nodeType);

        return sessionFactory.doWithSessionAndSave(session -> {
            // Ensure parent path exists
            String parentPath = getParentPath(path);
            String nodeName = getNodeName(path);

            Node parent = JcrUtils.getOrCreateByPath(parentPath, "sling:Folder", session);

            // Create the node
            Node node = parent.addNode(nodeName, nodeType != null ? nodeType : "nt:unstructured");

            // Set properties
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    setProperty(node, entry.getKey(), entry.getValue());
                }
            }

            // Set metadata
            node.setProperty("jcr:created", Calendar.getInstance());
            node.setProperty("jcr:createdBy", session.getUserID());

            LOG.info("Created content at: {}", node.getPath());
            return node;
        });
    }

    @Override
    public Node getContent(String path) throws RepositoryException {
        LOG.debug("Getting content at path: {}", path);

        return sessionFactory.doWithSession(session -> {
            if (session.nodeExists(path)) {
                return session.getNode(path);
            }
            return null;
        });
    }

    @Override
    public Node updateContent(String path, Map<String, Object> properties) throws RepositoryException {
        LOG.debug("Updating content at path: {}", path);

        return sessionFactory.doWithSessionAndSave(session -> {
            if (!session.nodeExists(path)) {
                throw new RepositoryException("Node not found: " + path);
            }

            Node node = session.getNode(path);

            // Update properties
            if (properties != null) {
                for (Map.Entry<String, Object> entry : properties.entrySet()) {
                    setProperty(node, entry.getKey(), entry.getValue());
                }
            }

            // Update metadata
            node.setProperty("jcr:lastModified", Calendar.getInstance());
            node.setProperty("jcr:lastModifiedBy", session.getUserID());

            LOG.info("Updated content at: {}", path);
            return node;
        });
    }

    @Override
    public void deleteContent(String path) throws RepositoryException {
        LOG.debug("Deleting content at path: {}", path);

        sessionFactory.doWithSessionVoidAndSave(session -> {
            if (!session.nodeExists(path)) {
                throw new RepositoryException("Node not found: " + path);
            }

            Node node = session.getNode(path);
            node.remove();

            LOG.info("Deleted content at: {}", path);
        });
    }

    @Override
    public void moveContent(String sourcePath, String destinationPath) throws RepositoryException {
        LOG.debug("Moving content from {} to {}", sourcePath, destinationPath);

        sessionFactory.doWithSessionVoidAndSave(session -> {
            session.move(sourcePath, destinationPath);
            LOG.info("Moved content from {} to {}", sourcePath, destinationPath);
        });
    }

    @Override
    public void copyContent(String sourcePath, String destinationPath) throws RepositoryException {
        LOG.debug("Copying content from {} to {}", sourcePath, destinationPath);

        sessionFactory.doWithSessionVoidAndSave(session -> {
            Workspace workspace = session.getWorkspace();
            workspace.copy(sourcePath, destinationPath);
            LOG.info("Copied content from {} to {}", sourcePath, destinationPath);
        });
    }

    @Override
    public boolean exists(String path) throws RepositoryException {
        return sessionFactory.doWithSession(session -> session.nodeExists(path));
    }

    @Override
    public List<Node> getChildren(String path) throws RepositoryException {
        LOG.debug("Getting children at path: {}", path);

        return sessionFactory.doWithSession(session -> {
            List<Node> children = new ArrayList<>();

            if (!session.nodeExists(path)) {
                return children;
            }

            Node parent = session.getNode(path);
            NodeIterator nodeIterator = parent.getNodes();

            while (nodeIterator.hasNext()) {
                children.add(nodeIterator.nextNode());
            }

            return children;
        });
    }

    @Override
    public List<Node> query(String queryString, int limit, int offset) throws RepositoryException {
        LOG.debug("Executing query: {} (limit: {}, offset: {})", queryString, limit, offset);

        return sessionFactory.doWithSession(session -> {
            List<Node> results = new ArrayList<>();

            QueryManager queryManager = session.getWorkspace().getQueryManager();
            Query query = queryManager.createQuery(queryString, Query.JCR_SQL2);

            if (limit > 0) {
                query.setLimit(limit);
            }
            if (offset > 0) {
                query.setOffset(offset);
            }

            QueryResult result = query.execute();
            NodeIterator nodeIterator = result.getNodes();

            while (nodeIterator.hasNext()) {
                results.add(nodeIterator.nextNode());
            }

            LOG.debug("Query returned {} results", results.size());
            return results;
        });
    }

    /**
     * Sets a property on a node, handling type conversion.
     */
    private void setProperty(Node node, String name, Object value) throws RepositoryException {
        if (value == null) {
            if (node.hasProperty(name)) {
                node.getProperty(name).remove();
            }
            return;
        }

        if (value instanceof String) {
            node.setProperty(name, (String) value);
        } else if (value instanceof Long) {
            node.setProperty(name, (Long) value);
        } else if (value instanceof Double) {
            node.setProperty(name, (Double) value);
        } else if (value instanceof Boolean) {
            node.setProperty(name, (Boolean) value);
        } else if (value instanceof Calendar) {
            node.setProperty(name, (Calendar) value);
        } else if (value instanceof Date) {
            Calendar cal = Calendar.getInstance();
            cal.setTime((Date) value);
            node.setProperty(name, cal);
        } else if (value instanceof String[]) {
            node.setProperty(name, (String[]) value);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            String[] array = list.stream()
                    .map(Object::toString)
                    .toArray(String[]::new);
            node.setProperty(name, array);
        } else {
            node.setProperty(name, value.toString());
        }
    }

    /**
     * Gets the parent path from a full path.
     */
    private String getParentPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        if (lastSlash <= 0) {
            return "/";
        }
        return path.substring(0, lastSlash);
    }

    /**
     * Gets the node name from a full path.
     */
    private String getNodeName(String path) {
        int lastSlash = path.lastIndexOf('/');
        return path.substring(lastSlash + 1);
    }
}
