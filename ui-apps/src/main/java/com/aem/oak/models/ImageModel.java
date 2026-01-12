package com.aem.oak.models;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;

/**
 * Sling Model for image components.
 */
@Model(
    adaptables = Resource.class,
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class ImageModel {

    private static final Logger LOG = LoggerFactory.getLogger(ImageModel.class);

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    private String fileReference;

    @ValueMapValue
    private String alt;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String linkURL;

    @ValueMapValue
    private String linkTarget;

    @ValueMapValue
    private String width;

    @ValueMapValue
    private String height;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean decorative;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean lazyLoading;

    @ValueMapValue
    private String cssClass;

    @ValueMapValue
    private String caption;

    private String path;
    private String src;

    @PostConstruct
    protected void init() {
        this.path = resource.getPath();

        // Determine image source
        if (fileReference != null && !fileReference.isEmpty()) {
            this.src = fileReference;
            // Add rendition suffix for optimized delivery
            if (!src.contains(".")) {
                this.src = fileReference + ".img.jpg";
            }
        }

        LOG.debug("ImageModel initialized for: {}, src: {}", path, src);
    }

    public String getFileReference() {
        return fileReference;
    }

    public String getSrc() {
        return src;
    }

    public String getAlt() {
        // For decorative images, return empty alt
        if (decorative) {
            return "";
        }
        return alt != null ? alt : "";
    }

    public String getTitle() {
        return title;
    }

    public String getLinkURL() {
        return linkURL;
    }

    public String getLinkTarget() {
        return linkTarget != null ? linkTarget : "_self";
    }

    public String getWidth() {
        return width;
    }

    public String getHeight() {
        return height;
    }

    public boolean isDecorative() {
        return decorative;
    }

    public boolean isLazyLoading() {
        return lazyLoading;
    }

    public String getCssClass() {
        return cssClass;
    }

    public String getCaption() {
        return caption;
    }

    public String getPath() {
        return path;
    }

    /**
     * Check if image has content.
     */
    public boolean hasContent() {
        return src != null && !src.isEmpty();
    }

    /**
     * Check if image has a link.
     */
    public boolean hasLink() {
        return linkURL != null && !linkURL.isEmpty();
    }

    /**
     * Check if image has a caption.
     */
    public boolean hasCaption() {
        return caption != null && !caption.isEmpty();
    }

    /**
     * Get loading attribute for lazy loading.
     */
    public String getLoading() {
        return lazyLoading ? "lazy" : "eager";
    }

    /**
     * Get computed CSS classes.
     */
    public String getComputedClasses() {
        StringBuilder classes = new StringBuilder("image-component");

        if (cssClass != null && !cssClass.isEmpty()) {
            classes.append(" ").append(cssClass);
        }

        if (hasLink()) {
            classes.append(" image-linked");
        }

        if (hasCaption()) {
            classes.append(" image-captioned");
        }

        return classes.toString();
    }

    /**
     * Get srcset for responsive images.
     */
    public String getSrcset() {
        if (src == null || src.isEmpty()) {
            return null;
        }

        // Generate srcset for common breakpoints
        StringBuilder srcset = new StringBuilder();
        int[] widths = {320, 640, 1024, 1280, 1920};

        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                srcset.append(", ");
            }
            srcset.append(getRenditionUrl(widths[i])).append(" ").append(widths[i]).append("w");
        }

        return srcset.toString();
    }

    /**
     * Get URL for a specific width rendition.
     */
    public String getRenditionUrl(int targetWidth) {
        if (fileReference == null || fileReference.isEmpty()) {
            return null;
        }
        return fileReference + ".img." + targetWidth + ".jpg";
    }

    /**
     * Get thumbnail URL.
     */
    public String getThumbnailUrl() {
        return getRenditionUrl(140);
    }
}
