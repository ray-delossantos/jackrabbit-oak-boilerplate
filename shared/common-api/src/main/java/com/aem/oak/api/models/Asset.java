package com.aem.oak.api.models;

import java.util.Date;
import java.util.Map;

/**
 * Represents a digital asset in the DAM.
 */
public class Asset {

    private String path;
    private String name;
    private String title;
    private String mimeType;
    private long size;
    private int width;
    private int height;
    private Date created;
    private Date modified;
    private String createdBy;
    private String modifiedBy;
    private Map<String, Object> metadata;

    public Asset() {
    }

    public Asset(String path, String name) {
        this.path = path;
        this.name = name;
    }

    // Getters and Setters

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public Date getCreated() {
        return created;
    }

    public void setCreated(Date created) {
        this.created = created;
    }

    public Date getModified() {
        return modified;
    }

    public void setModified(Date modified) {
        this.modified = modified;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getModifiedBy() {
        return modifiedBy;
    }

    public void setModifiedBy(String modifiedBy) {
        this.modifiedBy = modifiedBy;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    /**
     * Checks if this is an image asset.
     */
    public boolean isImage() {
        return mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Checks if this is a video asset.
     */
    public boolean isVideo() {
        return mimeType != null && mimeType.startsWith("video/");
    }

    /**
     * Checks if this is a document asset.
     */
    public boolean isDocument() {
        return mimeType != null && (
                mimeType.startsWith("application/pdf") ||
                mimeType.startsWith("application/msword") ||
                mimeType.startsWith("application/vnd.openxmlformats") ||
                mimeType.startsWith("text/")
        );
    }

    /**
     * Gets the file extension from the name.
     */
    public String getExtension() {
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf(".") + 1).toLowerCase();
        }
        return "";
    }

    @Override
    public String toString() {
        return "Asset{" +
                "path='" + path + '\'' +
                ", name='" + name + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", size=" + size +
                '}';
    }
}
