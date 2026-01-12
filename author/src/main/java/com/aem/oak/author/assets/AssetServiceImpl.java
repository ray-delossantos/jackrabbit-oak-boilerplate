package com.aem.oak.author.assets;

import com.aem.oak.api.assets.AssetService;
import com.aem.oak.api.models.Asset;
import com.aem.oak.api.models.Rendition;
import com.aem.oak.core.repository.JcrSessionFactory;
import org.apache.jackrabbit.commons.JcrUtils;
import org.osgi.service.component.annotations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.*;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.io.InputStream;
import java.util.*;

/**
 * Implementation of AssetService for Digital Asset Management.
 * Handles upload, retrieval, and processing of binary assets.
 */
@Component(service = AssetService.class, immediate = true)
public class AssetServiceImpl implements AssetService {

    private static final Logger LOG = LoggerFactory.getLogger(AssetServiceImpl.class);

    private static final String DAM_ASSET_TYPE = "dam:Asset";
    private static final String DAM_ASSET_CONTENT = "jcr:content";
    private static final String DAM_RENDITIONS = "renditions";
    private static final String DAM_METADATA = "metadata";
    private static final String DAM_ORIGINAL = "original";

    @Reference
    private JcrSessionFactory sessionFactory;

    @Reference(cardinality = ReferenceCardinality.OPTIONAL)
    private AssetProcessingService processingService;

    @Override
    public Asset uploadAsset(String path, InputStream data, String fileName, String mimeType,
                             Map<String, Object> metadata) throws RepositoryException {
        LOG.info("Uploading asset: {} to path: {}", fileName, path);

        return sessionFactory.doWithSessionAndSave(session -> {
            // Ensure parent folder exists
            String folderPath = path.endsWith("/") ? path : path + "/";
            Node folder = JcrUtils.getOrCreateByPath(folderPath, "sling:Folder", session);

            // Create asset node
            String assetPath = folder.getPath() + "/" + fileName;
            Node assetNode;

            if (session.nodeExists(assetPath)) {
                // Update existing asset
                assetNode = session.getNode(assetPath);
                LOG.debug("Updating existing asset: {}", assetPath);
            } else {
                // Create new asset
                assetNode = folder.addNode(fileName, DAM_ASSET_TYPE);
            }

            // Create or get jcr:content
            Node contentNode;
            if (assetNode.hasNode(DAM_ASSET_CONTENT)) {
                contentNode = assetNode.getNode(DAM_ASSET_CONTENT);
            } else {
                contentNode = assetNode.addNode(DAM_ASSET_CONTENT, "dam:AssetContent");
            }

            // Create renditions folder
            Node renditionsNode;
            if (contentNode.hasNode(DAM_RENDITIONS)) {
                renditionsNode = contentNode.getNode(DAM_RENDITIONS);
            } else {
                renditionsNode = contentNode.addNode(DAM_RENDITIONS, "nt:folder");
            }

            // Store original
            Node originalNode;
            if (renditionsNode.hasNode(DAM_ORIGINAL)) {
                originalNode = renditionsNode.getNode(DAM_ORIGINAL);
            } else {
                originalNode = renditionsNode.addNode(DAM_ORIGINAL, "nt:file");
            }

            Node originalContent;
            if (originalNode.hasNode("jcr:content")) {
                originalContent = originalNode.getNode("jcr:content");
            } else {
                originalContent = originalNode.addNode("jcr:content", "nt:resource");
            }

            // Set binary data
            Binary binary = session.getValueFactory().createBinary(data);
            originalContent.setProperty("jcr:data", binary);
            originalContent.setProperty("jcr:mimeType", mimeType);
            originalContent.setProperty("jcr:lastModified", Calendar.getInstance());

            // Create/update metadata node
            Node metadataNode;
            if (contentNode.hasNode(DAM_METADATA)) {
                metadataNode = contentNode.getNode(DAM_METADATA);
            } else {
                metadataNode = contentNode.addNode(DAM_METADATA, "nt:unstructured");
            }

            // Set metadata properties
            metadataNode.setProperty("dc:title", fileName);
            metadataNode.setProperty("dc:format", mimeType);

            if (metadata != null) {
                for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                    setProperty(metadataNode, entry.getKey(), entry.getValue());
                }
            }

            // Set asset-level properties
            contentNode.setProperty("jcr:mimeType", mimeType);
            assetNode.setProperty("jcr:created", Calendar.getInstance());
            assetNode.setProperty("jcr:createdBy", session.getUserID());

            LOG.info("Asset uploaded successfully: {}", assetNode.getPath());

            // Convert to Asset model
            Asset asset = nodeToAsset(assetNode);

            // Trigger async processing
            if (processingService != null) {
                processingService.processAssetAsync(assetNode.getPath());
            }

            return asset;
        });
    }

    @Override
    public Asset getAsset(String path) throws RepositoryException {
        LOG.debug("Getting asset: {}", path);

        return sessionFactory.doWithSession(session -> {
            if (!session.nodeExists(path)) {
                return null;
            }

            Node node = session.getNode(path);
            if (!node.isNodeType(DAM_ASSET_TYPE)) {
                return null;
            }

            return nodeToAsset(node);
        });
    }

    @Override
    public InputStream getAssetData(String path) throws RepositoryException {
        LOG.debug("Getting asset data: {}", path);

        return sessionFactory.doWithSession(session -> {
            if (!session.nodeExists(path)) {
                throw new RepositoryException("Asset not found: " + path);
            }

            Node assetNode = session.getNode(path);
            String originalPath = path + "/" + DAM_ASSET_CONTENT + "/" + DAM_RENDITIONS + "/" + DAM_ORIGINAL + "/jcr:content";

            if (!session.nodeExists(originalPath)) {
                throw new RepositoryException("Original rendition not found: " + path);
            }

            Node originalContent = session.getNode(originalPath);
            Property dataProp = originalContent.getProperty("jcr:data");
            return dataProp.getBinary().getStream();
        });
    }

    @Override
    public Asset updateAssetMetadata(String path, Map<String, Object> metadata) throws RepositoryException {
        LOG.debug("Updating asset metadata: {}", path);

        return sessionFactory.doWithSessionAndSave(session -> {
            if (!session.nodeExists(path)) {
                throw new RepositoryException("Asset not found: " + path);
            }

            Node assetNode = session.getNode(path);
            Node contentNode = assetNode.getNode(DAM_ASSET_CONTENT);
            Node metadataNode = contentNode.getNode(DAM_METADATA);

            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                setProperty(metadataNode, entry.getKey(), entry.getValue());
            }

            assetNode.setProperty("jcr:lastModified", Calendar.getInstance());
            assetNode.setProperty("jcr:lastModifiedBy", session.getUserID());

            return nodeToAsset(assetNode);
        });
    }

    @Override
    public void deleteAsset(String path) throws RepositoryException {
        LOG.info("Deleting asset: {}", path);

        sessionFactory.doWithSessionVoidAndSave(session -> {
            if (!session.nodeExists(path)) {
                throw new RepositoryException("Asset not found: " + path);
            }

            Node assetNode = session.getNode(path);
            assetNode.remove();

            LOG.info("Asset deleted: {}", path);
        });
    }

    @Override
    public List<Asset> listAssets(String folderPath, boolean recursive) throws RepositoryException {
        LOG.debug("Listing assets in: {} (recursive: {})", folderPath, recursive);

        return sessionFactory.doWithSession(session -> {
            List<Asset> assets = new ArrayList<>();

            if (!session.nodeExists(folderPath)) {
                return assets;
            }

            if (recursive) {
                // Use JCR-SQL2 query for recursive search
                String query = String.format(
                        "SELECT * FROM [%s] WHERE ISDESCENDANTNODE('%s')",
                        DAM_ASSET_TYPE, folderPath);

                QueryManager qm = session.getWorkspace().getQueryManager();
                Query q = qm.createQuery(query, Query.JCR_SQL2);
                QueryResult result = q.execute();
                NodeIterator nodes = result.getNodes();

                while (nodes.hasNext()) {
                    assets.add(nodeToAsset(nodes.nextNode()));
                }
            } else {
                Node folder = session.getNode(folderPath);
                NodeIterator nodes = folder.getNodes();

                while (nodes.hasNext()) {
                    Node node = nodes.nextNode();
                    if (node.isNodeType(DAM_ASSET_TYPE)) {
                        assets.add(nodeToAsset(node));
                    }
                }
            }

            return assets;
        });
    }

    @Override
    public List<Rendition> getRenditions(String assetPath) throws RepositoryException {
        LOG.debug("Getting renditions for: {}", assetPath);

        return sessionFactory.doWithSession(session -> {
            List<Rendition> renditions = new ArrayList<>();

            String renditionsPath = assetPath + "/" + DAM_ASSET_CONTENT + "/" + DAM_RENDITIONS;
            if (!session.nodeExists(renditionsPath)) {
                return renditions;
            }

            Node renditionsNode = session.getNode(renditionsPath);
            NodeIterator nodes = renditionsNode.getNodes();

            while (nodes.hasNext()) {
                Node node = nodes.nextNode();
                renditions.add(nodeToRendition(node, assetPath));
            }

            return renditions;
        });
    }

    @Override
    public Rendition getRendition(String assetPath, String renditionName) throws RepositoryException {
        LOG.debug("Getting rendition {} for: {}", renditionName, assetPath);

        return sessionFactory.doWithSession(session -> {
            String renditionPath = assetPath + "/" + DAM_ASSET_CONTENT + "/" + DAM_RENDITIONS + "/" + renditionName;

            if (!session.nodeExists(renditionPath)) {
                return null;
            }

            return nodeToRendition(session.getNode(renditionPath), assetPath);
        });
    }

    @Override
    public Rendition createRendition(String assetPath, String renditionName, InputStream data,
                                      String mimeType) throws RepositoryException {
        LOG.info("Creating rendition {} for: {}", renditionName, assetPath);

        return sessionFactory.doWithSessionAndSave(session -> {
            String renditionsPath = assetPath + "/" + DAM_ASSET_CONTENT + "/" + DAM_RENDITIONS;

            if (!session.nodeExists(renditionsPath)) {
                throw new RepositoryException("Asset renditions folder not found: " + assetPath);
            }

            Node renditionsNode = session.getNode(renditionsPath);

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
            Binary binary = session.getValueFactory().createBinary(data);
            contentNode.setProperty("jcr:data", binary);
            contentNode.setProperty("jcr:mimeType", mimeType);
            contentNode.setProperty("jcr:lastModified", Calendar.getInstance());

            return nodeToRendition(renditionNode, assetPath);
        });
    }

    @Override
    public void processAsset(String assetPath) throws RepositoryException {
        LOG.info("Processing asset: {}", assetPath);

        if (processingService != null) {
            processingService.processAssetAsync(assetPath);
        }
    }

    @Override
    public List<Asset> searchAssets(Map<String, String> searchTerms, int limit, int offset)
            throws RepositoryException {
        LOG.debug("Searching assets with terms: {}", searchTerms);

        return sessionFactory.doWithSession(session -> {
            List<Asset> assets = new ArrayList<>();

            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT * FROM [").append(DAM_ASSET_TYPE).append("]");

            if (!searchTerms.isEmpty()) {
                queryBuilder.append(" WHERE ");
                List<String> conditions = new ArrayList<>();

                for (Map.Entry<String, String> entry : searchTerms.entrySet()) {
                    conditions.add(String.format("CONTAINS([%s], '%s')",
                            entry.getKey(), entry.getValue().replace("'", "''")));
                }

                queryBuilder.append(String.join(" AND ", conditions));
            }

            QueryManager qm = session.getWorkspace().getQueryManager();
            Query query = qm.createQuery(queryBuilder.toString(), Query.JCR_SQL2);

            if (limit > 0) {
                query.setLimit(limit);
            }
            if (offset > 0) {
                query.setOffset(offset);
            }

            QueryResult result = query.execute();
            NodeIterator nodes = result.getNodes();

            while (nodes.hasNext()) {
                assets.add(nodeToAsset(nodes.nextNode()));
            }

            return assets;
        });
    }

    /**
     * Converts a JCR Node to Asset model.
     */
    private Asset nodeToAsset(Node node) throws RepositoryException {
        Asset asset = new Asset();
        asset.setPath(node.getPath());
        asset.setName(node.getName());

        // Get metadata
        if (node.hasNode(DAM_ASSET_CONTENT)) {
            Node contentNode = node.getNode(DAM_ASSET_CONTENT);

            if (contentNode.hasProperty("jcr:mimeType")) {
                asset.setMimeType(contentNode.getProperty("jcr:mimeType").getString());
            }

            if (contentNode.hasNode(DAM_METADATA)) {
                Node metadataNode = contentNode.getNode(DAM_METADATA);
                Map<String, Object> metadata = new HashMap<>();

                PropertyIterator props = metadataNode.getProperties();
                while (props.hasNext()) {
                    Property prop = props.nextProperty();
                    if (!prop.getName().startsWith("jcr:")) {
                        metadata.put(prop.getName(), prop.getString());
                    }
                }

                asset.setMetadata(metadata);

                if (metadataNode.hasProperty("dc:title")) {
                    asset.setTitle(metadataNode.getProperty("dc:title").getString());
                }
                if (metadataNode.hasProperty("tiff:ImageWidth")) {
                    asset.setWidth((int) metadataNode.getProperty("tiff:ImageWidth").getLong());
                }
                if (metadataNode.hasProperty("tiff:ImageLength")) {
                    asset.setHeight((int) metadataNode.getProperty("tiff:ImageLength").getLong());
                }
            }

            // Get file size from original
            if (contentNode.hasNode(DAM_RENDITIONS + "/" + DAM_ORIGINAL + "/jcr:content")) {
                Node originalContent = contentNode.getNode(DAM_RENDITIONS + "/" + DAM_ORIGINAL + "/jcr:content");
                if (originalContent.hasProperty("jcr:data")) {
                    asset.setSize(originalContent.getProperty("jcr:data").getBinary().getSize());
                }
            }
        }

        if (node.hasProperty("jcr:created")) {
            asset.setCreated(node.getProperty("jcr:created").getDate().getTime());
        }
        if (node.hasProperty("jcr:createdBy")) {
            asset.setCreatedBy(node.getProperty("jcr:createdBy").getString());
        }
        if (node.hasProperty("jcr:lastModified")) {
            asset.setModified(node.getProperty("jcr:lastModified").getDate().getTime());
        }
        if (node.hasProperty("jcr:lastModifiedBy")) {
            asset.setModifiedBy(node.getProperty("jcr:lastModifiedBy").getString());
        }

        return asset;
    }

    /**
     * Converts a JCR Node to Rendition model.
     */
    private Rendition nodeToRendition(Node node, String parentAssetPath) throws RepositoryException {
        Rendition rendition = new Rendition();
        rendition.setPath(node.getPath());
        rendition.setName(node.getName());
        rendition.setParentAssetPath(parentAssetPath);

        if (node.hasNode("jcr:content")) {
            Node contentNode = node.getNode("jcr:content");

            if (contentNode.hasProperty("jcr:mimeType")) {
                rendition.setMimeType(contentNode.getProperty("jcr:mimeType").getString());
            }
            if (contentNode.hasProperty("jcr:data")) {
                rendition.setSize(contentNode.getProperty("jcr:data").getBinary().getSize());
            }
            if (contentNode.hasProperty("jcr:lastModified")) {
                rendition.setCreated(contentNode.getProperty("jcr:lastModified").getDate().getTime());
            }
        }

        return rendition;
    }

    /**
     * Sets a property on a node.
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
        } else {
            node.setProperty(name, value.toString());
        }
    }
}
