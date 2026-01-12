package com.aem.oak.api.assets;

import com.aem.oak.api.models.Asset;
import com.aem.oak.api.models.Rendition;

import javax.jcr.RepositoryException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Service interface for Digital Asset Management (DAM) operations.
 * Handles upload, retrieval, and processing of binary assets.
 */
public interface AssetService {

    /**
     * Uploads a new asset to the repository.
     *
     * @param path     the destination path for the asset
     * @param data     the binary data stream
     * @param fileName the original file name
     * @param mimeType the MIME type of the asset
     * @param metadata additional metadata properties
     * @return the created Asset object
     * @throws RepositoryException if upload fails
     */
    Asset uploadAsset(String path, InputStream data, String fileName, String mimeType,
                      Map<String, Object> metadata) throws RepositoryException;

    /**
     * Retrieves an asset by its path.
     *
     * @param path the JCR path of the asset
     * @return the Asset object, or null if not found
     * @throws RepositoryException if retrieval fails
     */
    Asset getAsset(String path) throws RepositoryException;

    /**
     * Retrieves the original binary data of an asset.
     *
     * @param path the JCR path of the asset
     * @return InputStream of the binary data
     * @throws RepositoryException if retrieval fails
     */
    InputStream getAssetData(String path) throws RepositoryException;

    /**
     * Updates asset metadata.
     *
     * @param path     the JCR path of the asset
     * @param metadata the metadata properties to update
     * @return the updated Asset object
     * @throws RepositoryException if update fails
     */
    Asset updateAssetMetadata(String path, Map<String, Object> metadata) throws RepositoryException;

    /**
     * Deletes an asset and all its renditions.
     *
     * @param path the JCR path of the asset to delete
     * @throws RepositoryException if deletion fails
     */
    void deleteAsset(String path) throws RepositoryException;

    /**
     * Lists all assets under a folder path.
     *
     * @param folderPath the parent folder path
     * @param recursive  whether to include assets from subfolders
     * @return list of Asset objects
     * @throws RepositoryException if listing fails
     */
    List<Asset> listAssets(String folderPath, boolean recursive) throws RepositoryException;

    /**
     * Retrieves all renditions of an asset.
     *
     * @param assetPath the JCR path of the asset
     * @return list of Rendition objects
     * @throws RepositoryException if retrieval fails
     */
    List<Rendition> getRenditions(String assetPath) throws RepositoryException;

    /**
     * Retrieves a specific rendition of an asset.
     *
     * @param assetPath     the JCR path of the asset
     * @param renditionName the name of the rendition (e.g., "thumbnail.png")
     * @return the Rendition object, or null if not found
     * @throws RepositoryException if retrieval fails
     */
    Rendition getRendition(String assetPath, String renditionName) throws RepositoryException;

    /**
     * Creates a new rendition for an asset.
     *
     * @param assetPath     the JCR path of the asset
     * @param renditionName the name of the rendition
     * @param data          the binary data stream
     * @param mimeType      the MIME type of the rendition
     * @return the created Rendition object
     * @throws RepositoryException if creation fails
     */
    Rendition createRendition(String assetPath, String renditionName, InputStream data,
                               String mimeType) throws RepositoryException;

    /**
     * Triggers asset processing (thumbnails, metadata extraction).
     *
     * @param assetPath the JCR path of the asset to process
     * @throws RepositoryException if processing fails
     */
    void processAsset(String assetPath) throws RepositoryException;

    /**
     * Searches for assets by metadata.
     *
     * @param searchTerms search criteria as key-value pairs
     * @param limit       maximum number of results
     * @param offset      starting offset for pagination
     * @return list of matching Asset objects
     * @throws RepositoryException if search fails
     */
    List<Asset> searchAssets(Map<String, String> searchTerms, int limit, int offset)
            throws RepositoryException;
}
