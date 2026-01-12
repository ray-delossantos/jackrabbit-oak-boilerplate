package com.aem.oak.api.content;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.List;
import java.util.Map;

/**
 * Service interface for content management operations.
 * Provides CRUD operations for JCR content nodes.
 */
public interface ContentService {

    /**
     * Creates content at the specified path with the given properties.
     *
     * @param path       the JCR path where content should be created
     * @param nodeType   the primary node type (e.g., "nt:unstructured")
     * @param properties the properties to set on the node
     * @return the created Node
     * @throws RepositoryException if content creation fails
     */
    Node createContent(String path, String nodeType, Map<String, Object> properties)
            throws RepositoryException;

    /**
     * Retrieves content at the specified path.
     *
     * @param path the JCR path of the content
     * @return the Node at the path, or null if not found
     * @throws RepositoryException if retrieval fails
     */
    Node getContent(String path) throws RepositoryException;

    /**
     * Updates content at the specified path with the given properties.
     *
     * @param path       the JCR path of the content to update
     * @param properties the properties to update
     * @return the updated Node
     * @throws RepositoryException if update fails
     */
    Node updateContent(String path, Map<String, Object> properties) throws RepositoryException;

    /**
     * Deletes content at the specified path.
     *
     * @param path the JCR path of the content to delete
     * @throws RepositoryException if deletion fails
     */
    void deleteContent(String path) throws RepositoryException;

    /**
     * Moves content from one path to another.
     *
     * @param sourcePath      the source JCR path
     * @param destinationPath the destination JCR path
     * @throws RepositoryException if move fails
     */
    void moveContent(String sourcePath, String destinationPath) throws RepositoryException;

    /**
     * Copies content from one path to another.
     *
     * @param sourcePath      the source JCR path
     * @param destinationPath the destination JCR path
     * @throws RepositoryException if copy fails
     */
    void copyContent(String sourcePath, String destinationPath) throws RepositoryException;

    /**
     * Checks if content exists at the specified path.
     *
     * @param path the JCR path to check
     * @return true if content exists, false otherwise
     * @throws RepositoryException if check fails
     */
    boolean exists(String path) throws RepositoryException;

    /**
     * Lists child nodes at the specified path.
     *
     * @param path the parent JCR path
     * @return list of child Node objects
     * @throws RepositoryException if listing fails
     */
    List<Node> getChildren(String path) throws RepositoryException;

    /**
     * Queries content using JCR-SQL2.
     *
     * @param query  the JCR-SQL2 query string
     * @param limit  maximum number of results
     * @param offset starting offset for pagination
     * @return list of matching Node objects
     * @throws RepositoryException if query fails
     */
    List<Node> query(String query, int limit, int offset) throws RepositoryException;
}
