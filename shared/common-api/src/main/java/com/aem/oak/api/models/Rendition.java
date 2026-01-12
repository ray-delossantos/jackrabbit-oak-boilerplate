package com.aem.oak.api.models;

import java.util.Date;

/**
 * Represents a rendition (derived version) of an asset.
 * Examples: thumbnails, web-optimized images, transcoded videos.
 */
public class Rendition {

    private String path;
    private String name;
    private String mimeType;
    private long size;
    private int width;
    private int height;
    private Date created;
    private String parentAssetPath;

    public Rendition() {
    }

    public Rendition(String path, String name) {
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

    public String getParentAssetPath() {
        return parentAssetPath;
    }

    public void setParentAssetPath(String parentAssetPath) {
        this.parentAssetPath = parentAssetPath;
    }

    /**
     * Checks if this is a thumbnail rendition.
     */
    public boolean isThumbnail() {
        return name != null && (
                name.contains("thumbnail") ||
                name.contains("cq5dam.thumbnail")
        );
    }

    /**
     * Checks if this is a web-optimized rendition.
     */
    public boolean isWebRendition() {
        return name != null && name.contains("cq5dam.web");
    }

    @Override
    public String toString() {
        return "Rendition{" +
                "path='" + path + '\'' +
                ", name='" + name + '\'' +
                ", mimeType='" + mimeType + '\'' +
                ", size=" + size +
                '}';
    }
}
