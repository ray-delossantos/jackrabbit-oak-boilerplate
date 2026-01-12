package com.aem.oak.models.media;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sling Model for the VENTURE Gallery Component.
 * Image gallery with lightbox support.
 */
@Model(adaptables = Resource.class)
public class GalleryModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    private String title;

    @ValueMapValue
    @Default(intValues = 3)
    private int columns;

    @ValueMapValue
    @Default(values = "grid")
    private String layout;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean enableLightbox;

    @ValueMapValue
    @Default(values = "1rem")
    private String gap;

    @ChildResource(name = "images")
    private List<Resource> imageResources;

    private List<GalleryImage> images;

    @PostConstruct
    protected void init() {
        images = new ArrayList<>();

        if (imageResources != null) {
            for (Resource imgResource : imageResources) {
                GalleryImage image = new GalleryImage();
                image.setSrc(imgResource.getValueMap().get("src", String.class));
                image.setAlt(imgResource.getValueMap().get("alt", ""));
                image.setCaption(imgResource.getValueMap().get("caption", String.class));
                image.setThumbnail(imgResource.getValueMap().get("thumbnail", String.class));
                images.add(image);
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public int getColumns() {
        return columns;
    }

    public String getLayout() {
        return layout;
    }

    public String getLayoutClass() {
        switch (layout) {
            case "masonry":
                return "venture-gallery--masonry";
            case "slider":
                return "venture-gallery--slider";
            default:
                return "venture-gallery--grid";
        }
    }

    public boolean isEnableLightbox() {
        return enableLightbox;
    }

    public String getGap() {
        return gap;
    }

    public List<GalleryImage> getImages() {
        return images;
    }

    public int getImageCount() {
        return images.size();
    }

    public boolean hasImages() {
        return images != null && !images.isEmpty();
    }

    public String getGridColumnsStyle() {
        return "grid-template-columns: repeat(" + columns + ", 1fr);";
    }

    /**
     * Inner class representing a gallery image.
     */
    public static class GalleryImage {
        private String src;
        private String alt;
        private String caption;
        private String thumbnail;

        public String getSrc() { return src; }
        public void setSrc(String src) { this.src = src; }

        public String getAlt() { return alt != null ? alt : ""; }
        public void setAlt(String alt) { this.alt = alt; }

        public String getCaption() { return caption; }
        public void setCaption(String caption) { this.caption = caption; }

        public String getThumbnail() { return thumbnail != null ? thumbnail : src; }
        public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }

        public boolean hasCaption() { return caption != null && !caption.isEmpty(); }
    }
}
