package com.aem.oak.models.specialty;

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
 * Sling Model for the VENTURE Featured Article Component.
 * Hero-style featured article display.
 */
@Model(adaptables = Resource.class)
public class FeaturedModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    private String articlePath;

    @ValueMapValue
    @Default(values = "full")
    private String layout;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showExcerpt;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showAuthor;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showDate;

    private String title;
    private String excerpt;
    private String image;
    private String category;
    private String categoryLink;
    private String authorName;
    private String authorImage;
    private String formattedDate;
    private String link;

    @PostConstruct
    protected void init() {
        if (articlePath == null) return;

        Resource articleResource = resourceResolver.getResource(articlePath);
        if (articleResource == null) return;

        Resource contentResource = articleResource.getChild("jcr:content");
        if (contentResource == null) return;

        link = articlePath + ".html";

        try {
            Node contentNode = contentResource.adaptTo(Node.class);
            if (contentNode == null) return;

            if (contentNode.hasProperty("jcr:title")) {
                title = contentNode.getProperty("jcr:title").getString();
            }

            if (contentNode.hasProperty("jcr:description")) {
                excerpt = contentNode.getProperty("jcr:description").getString();
            }

            if (contentNode.hasProperty("heroImage")) {
                image = contentNode.getProperty("heroImage").getString();
            }

            if (contentNode.hasProperty("category")) {
                category = contentNode.getProperty("category").getString();
            }

            if (contentNode.hasProperty("categoryLink")) {
                categoryLink = contentNode.getProperty("categoryLink").getString();
            }

            if (contentNode.hasProperty("authorName")) {
                authorName = contentNode.getProperty("authorName").getString();
            }

            if (contentNode.hasProperty("authorImage")) {
                authorImage = contentNode.getProperty("authorImage").getString();
            }

            if (contentNode.hasProperty("publishDate")) {
                Calendar cal = contentNode.getProperty("publishDate").getDate();
                SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy");
                formattedDate = sdf.format(cal.getTime());
            }

        } catch (Exception e) {
            // Silent fail
        }
    }

    public String getArticlePath() {
        return articlePath;
    }

    public String getLayout() {
        return layout;
    }

    public String getLayoutClass() {
        switch (layout) {
            case "split":
                return "venture-featured--split";
            case "overlay":
                return "venture-featured--overlay";
            case "minimal":
                return "venture-featured--minimal";
            default:
                return "venture-featured--full";
        }
    }

    public boolean isShowExcerpt() {
        return showExcerpt;
    }

    public boolean isShowAuthor() {
        return showAuthor;
    }

    public boolean isShowDate() {
        return showDate;
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

    public String getCategory() {
        return category;
    }

    public String getCategoryLink() {
        return categoryLink;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorImage() {
        return authorImage;
    }

    public String getFormattedDate() {
        return formattedDate;
    }

    public String getLink() {
        return link;
    }

    public boolean hasContent() {
        return title != null && !title.isEmpty();
    }

    public boolean hasCategory() {
        return category != null && !category.isEmpty();
    }

    public boolean hasAuthor() {
        return authorName != null && !authorName.isEmpty();
    }
}
