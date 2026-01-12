package com.aem.oak.models.core;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Container Component.
 * Provides section wrappers with background and styling options.
 */
@Model(adaptables = Resource.class)
public class ContainerModel {

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String subtitle;

    @ValueMapValue
    @Default(values = "default")
    private String theme;

    @ValueMapValue
    @Default(values = "normal")
    private String width;

    @ValueMapValue
    private String backgroundColor;

    @ValueMapValue
    private String backgroundImage;

    @ValueMapValue
    private String textColor;

    @ValueMapValue
    private String padding;

    @ValueMapValue
    private String cssClass;

    @ValueMapValue
    private String anchorId;

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getThemeClass() {
        switch (theme) {
            case "dark":
                return "venture-section--dark";
            case "gray":
                return "venture-section--gray";
            case "yellow":
                return "venture-section--yellow";
            default:
                return "";
        }
    }

    public String getWidthClass() {
        switch (width) {
            case "narrow":
                return "venture-container--narrow";
            case "wide":
                return "venture-container--wide";
            case "full":
                return "venture-container--full";
            default:
                return "";
        }
    }

    public String getCssClass() {
        return cssClass != null ? cssClass : "";
    }

    public String getAnchorId() {
        return anchorId;
    }

    public String getComputedStyles() {
        StringBuilder styles = new StringBuilder();

        if (backgroundColor != null && !backgroundColor.isEmpty()) {
            styles.append("background-color: ").append(backgroundColor).append(";");
        }

        if (backgroundImage != null && !backgroundImage.isEmpty()) {
            styles.append("background-image: url('").append(backgroundImage).append("');");
            styles.append("background-size: cover;");
            styles.append("background-position: center;");
        }

        if (textColor != null && !textColor.isEmpty()) {
            styles.append("color: ").append(textColor).append(";");
        }

        if (padding != null && !padding.isEmpty()) {
            styles.append("padding: ").append(padding).append(";");
        }

        return styles.length() > 0 ? styles.toString() : null;
    }
}
