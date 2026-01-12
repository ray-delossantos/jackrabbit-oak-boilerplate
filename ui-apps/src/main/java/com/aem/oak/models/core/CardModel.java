package com.aem.oak.models.core;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import java.text.SimpleDateFormat;
import java.util.Calendar;

/**
 * Sling Model for the VENTURE Card Component.
 * Content card with image, category, title, excerpt and link.
 */
@Model(adaptables = Resource.class)
public class CardModel {

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
    private String link;

    @ValueMapValue
    private String author;

    @ValueMapValue
    private Calendar date;

    @ValueMapValue
    private String ctaText;

    @ValueMapValue
    private String cssClass;

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
        return imageAlt;
    }

    public String getCategory() {
        return category;
    }

    public String getLink() {
        return link;
    }

    public String getAuthor() {
        return author;
    }

    public Calendar getDate() {
        return date;
    }

    public String getFormattedDate() {
        if (date == null) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy");
        return sdf.format(date.getTime());
    }

    public String getCtaText() {
        return ctaText;
    }

    public String getCssClass() {
        return cssClass != null ? cssClass : "";
    }

    public boolean hasContent() {
        return title != null && !title.isEmpty();
    }
}
