package com.aem.oak.models;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;

/**
 * Sling Model for text/richtext components.
 */
@Model(
    adaptables = Resource.class,
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class TextModel {

    private static final Logger LOG = LoggerFactory.getLogger(TextModel.class);

    @SlingObject
    private Resource resource;

    @ValueMapValue
    private String text;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean richText;

    @ValueMapValue
    private String textAlignment;

    @ValueMapValue
    private String textSize;

    @ValueMapValue
    private String textColor;

    @ValueMapValue
    private String backgroundColor;

    @ValueMapValue
    private String cssClass;

    private String path;

    @PostConstruct
    protected void init() {
        this.path = resource.getPath();
        LOG.debug("TextModel initialized for: {}", path);
    }

    public String getText() {
        return text;
    }

    public boolean isRichText() {
        return richText;
    }

    public String getTextAlignment() {
        return textAlignment != null ? textAlignment : "left";
    }

    public String getTextSize() {
        return textSize != null ? textSize : "normal";
    }

    public String getTextColor() {
        return textColor;
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public String getCssClass() {
        return cssClass;
    }

    public String getPath() {
        return path;
    }

    /**
     * Check if the component has content.
     */
    public boolean hasContent() {
        return text != null && !text.trim().isEmpty();
    }

    /**
     * Get computed CSS classes based on properties.
     */
    public String getComputedClasses() {
        StringBuilder classes = new StringBuilder("text-component");

        if (cssClass != null && !cssClass.isEmpty()) {
            classes.append(" ").append(cssClass);
        }

        if (textAlignment != null) {
            classes.append(" text-").append(textAlignment);
        }

        if (textSize != null) {
            classes.append(" text-size-").append(textSize);
        }

        return classes.toString();
    }

    /**
     * Get computed inline styles.
     */
    public String getComputedStyles() {
        StringBuilder styles = new StringBuilder();

        if (textColor != null && !textColor.isEmpty()) {
            styles.append("color: ").append(textColor).append(";");
        }

        if (backgroundColor != null && !backgroundColor.isEmpty()) {
            styles.append("background-color: ").append(backgroundColor).append(";");
        }

        return styles.length() > 0 ? styles.toString() : null;
    }
}
