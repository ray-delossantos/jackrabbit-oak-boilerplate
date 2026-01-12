package com.aem.oak.models.article;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import javax.jcr.Node;
import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Sling Model for the VENTURE Article Teaser Component.
 * Article preview card with image, title, excerpt.
 */
@Model(adaptables = Resource.class)
public class ArticleTeaserModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    private String articlePath;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String excerpt;

    @ValueMapValue
    private String image;

    @ValueMapValue
    private String imageAlt;

    @ValueMapValue
    private String category;

    @ValueMapValue
    private String categoryLink;

    @ValueMapValue
    private String authorName;

    @ValueMapValue
    private Calendar publishDate;

    @ValueMapValue
    @Default(values = "medium")
    private String size;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean featured;

    private String formattedDate;
    private String link;

    @PostConstruct
    protected void init() {
        // Format publish date
        if (publishDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy");
            formattedDate = sdf.format(publishDate.getTime());
        }

        // Build article link
        if (articlePath != null && !articlePath.isEmpty()) {
            link = articlePath + ".html";
        }

        // If no explicit values, try to read from linked article
        if (articlePath != null && title == null) {
            loadFromArticle();
        }
    }

    private void loadFromArticle() {
        try {
            Resource articleResource = resourceResolver.getResource(articlePath + "/jcr:content");
            if (articleResource != null) {
                Node node = articleResource.adaptTo(Node.class);
                if (node != null) {
                    if (title == null && node.hasProperty("jcr:title")) {
                        title = node.getProperty("jcr:title").getString();
                    }
                    if (excerpt == null && node.hasProperty("jcr:description")) {
                        excerpt = node.getProperty("jcr:description").getString();
                    }
                    if (image == null && node.hasProperty("heroImage")) {
                        image = node.getProperty("heroImage").getString();
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail, use explicit values
        }
    }

    public String getTitle() {
        return title;
    }

    public String getExcerpt() {
        return excerpt;
    }

    public String getImage() {
        return image;
    }

    public String getImageAlt() {
        return imageAlt != null ? imageAlt : title;
    }

    public String getCategory() {
        return category;
    }

    public String getCategoryLink() {
        return categoryLink;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getFormattedDate() {
        return formattedDate;
    }

    public String getLink() {
        return link;
    }

    public String getSize() {
        return size;
    }

    public boolean isFeatured() {
        return featured;
    }

    public String getSizeClass() {
        switch (size) {
            case "large":
                return "venture-teaser--large";
            case "small":
                return "venture-teaser--small";
            default:
                return "venture-teaser--medium";
        }
    }

    public boolean hasContent() {
        return title != null && !title.isEmpty();
    }

    public boolean hasCategory() {
        return category != null && !category.isEmpty();
    }
}
