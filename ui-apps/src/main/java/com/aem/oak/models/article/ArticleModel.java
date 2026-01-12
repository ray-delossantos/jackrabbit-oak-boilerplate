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
import java.util.Date;

/**
 * Sling Model for the VENTURE Article Component.
 * Full article template with header, content, and metadata.
 */
@Model(adaptables = Resource.class)
public class ArticleModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String subtitle;

    @ValueMapValue
    private String heroImage;

    @ValueMapValue
    private String heroImageAlt;

    @ValueMapValue
    private String category;

    @ValueMapValue
    private String categoryLink;

    @ValueMapValue
    private String authorName;

    @ValueMapValue
    private String authorImage;

    @ValueMapValue
    private String authorLink;

    @ValueMapValue
    private Calendar publishDate;

    @ValueMapValue
    private String content;

    @ValueMapValue
    @Default(intValues = 0)
    private int wordCount;

    private String formattedDate;
    private int readingTime;

    @PostConstruct
    protected void init() {
        // Format publish date
        if (publishDate != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMMM d, yyyy");
            formattedDate = sdf.format(publishDate.getTime());
        }

        // Calculate reading time (average 200 words per minute)
        if (wordCount > 0) {
            readingTime = Math.max(1, (int) Math.ceil(wordCount / 200.0));
        } else if (content != null && !content.isEmpty()) {
            int words = content.split("\\s+").length;
            readingTime = Math.max(1, (int) Math.ceil(words / 200.0));
        } else {
            readingTime = 5; // Default
        }
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getHeroImage() {
        return heroImage;
    }

    public String getHeroImageAlt() {
        return heroImageAlt != null ? heroImageAlt : title;
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

    public String getAuthorLink() {
        return authorLink;
    }

    public String getFormattedDate() {
        return formattedDate;
    }

    public int getReadingTime() {
        return readingTime;
    }

    public String getContent() {
        return content;
    }

    public boolean hasContent() {
        return title != null && !title.isEmpty();
    }

    public boolean hasAuthor() {
        return authorName != null && !authorName.isEmpty();
    }

    public boolean hasCategory() {
        return category != null && !category.isEmpty();
    }
}
