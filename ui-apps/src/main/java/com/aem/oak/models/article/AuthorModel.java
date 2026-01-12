package com.aem.oak.models.article;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import javax.jcr.Node;

/**
 * Sling Model for the VENTURE Author Component.
 * Author byline and profile information.
 */
@Model(adaptables = Resource.class)
public class AuthorModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    private String authorPath;

    @ValueMapValue
    private String name;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String bio;

    @ValueMapValue
    private String image;

    @ValueMapValue
    private String link;

    @ValueMapValue
    private String twitterHandle;

    @ValueMapValue
    private String linkedinUrl;

    @ValueMapValue
    private String instagramHandle;

    @ValueMapValue
    @Default(values = "byline")
    private String displayMode;

    @PostConstruct
    protected void init() {
        // If author path is provided, load author data from that page
        if (authorPath != null && name == null) {
            loadFromAuthorPage();
        }

        // Build link if not provided
        if (link == null && authorPath != null) {
            link = authorPath + ".html";
        }
    }

    private void loadFromAuthorPage() {
        try {
            Resource authorResource = resourceResolver.getResource(authorPath + "/jcr:content");
            if (authorResource != null) {
                Node node = authorResource.adaptTo(Node.class);
                if (node != null) {
                    if (node.hasProperty("jcr:title")) {
                        name = node.getProperty("jcr:title").getString();
                    }
                    if (node.hasProperty("authorTitle")) {
                        title = node.getProperty("authorTitle").getString();
                    }
                    if (node.hasProperty("bio")) {
                        bio = node.getProperty("bio").getString();
                    }
                    if (node.hasProperty("authorImage")) {
                        image = node.getProperty("authorImage").getString();
                    }
                    if (node.hasProperty("twitterHandle")) {
                        twitterHandle = node.getProperty("twitterHandle").getString();
                    }
                    if (node.hasProperty("linkedinUrl")) {
                        linkedinUrl = node.getProperty("linkedinUrl").getString();
                    }
                    if (node.hasProperty("instagramHandle")) {
                        instagramHandle = node.getProperty("instagramHandle").getString();
                    }
                }
            }
        } catch (Exception e) {
            // Silently fail, use explicit values
        }
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public String getBio() {
        return bio;
    }

    public String getImage() {
        return image;
    }

    public String getLink() {
        return link;
    }

    public String getTwitterHandle() {
        return twitterHandle;
    }

    public String getTwitterUrl() {
        return twitterHandle != null ? "https://twitter.com/" + twitterHandle.replace("@", "") : null;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public String getInstagramHandle() {
        return instagramHandle;
    }

    public String getInstagramUrl() {
        return instagramHandle != null ? "https://instagram.com/" + instagramHandle.replace("@", "") : null;
    }

    public String getDisplayMode() {
        return displayMode;
    }

    public boolean isByline() {
        return "byline".equals(displayMode);
    }

    public boolean isCard() {
        return "card".equals(displayMode);
    }

    public boolean isFull() {
        return "full".equals(displayMode);
    }

    public boolean hasSocialLinks() {
        return twitterHandle != null || linkedinUrl != null || instagramHandle != null;
    }

    public boolean hasContent() {
        return name != null && !name.isEmpty();
    }
}
