package com.aem.oak.models.media;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Figure Component.
 * Image with caption and credit.
 */
@Model(adaptables = Resource.class)
public class FigureModel {

    @ValueMapValue
    private String image;

    @ValueMapValue
    private String alt;

    @ValueMapValue
    private String caption;

    @ValueMapValue
    private String credit;

    @ValueMapValue
    private String creditUrl;

    @ValueMapValue
    private String link;

    @ValueMapValue
    @Default(values = "_self")
    private String linkTarget;

    @ValueMapValue
    @Default(values = "default")
    private String size;

    @ValueMapValue
    @Default(values = "center")
    private String alignment;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean rounded;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean shadow;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean border;

    public String getImage() {
        return image;
    }

    public String getAlt() {
        return alt != null ? alt : "";
    }

    public String getCaption() {
        return caption;
    }

    public String getCredit() {
        return credit;
    }

    public String getCreditUrl() {
        return creditUrl;
    }

    public String getLink() {
        return link;
    }

    public String getLinkTarget() {
        return linkTarget;
    }

    public String getSize() {
        return size;
    }

    public String getSizeClass() {
        switch (size) {
            case "small":
                return "venture-figure--small";
            case "large":
                return "venture-figure--large";
            case "full":
                return "venture-figure--full";
            default:
                return "venture-figure--default";
        }
    }

    public String getAlignment() {
        return alignment;
    }

    public String getAlignmentClass() {
        switch (alignment) {
            case "left":
                return "venture-figure--left";
            case "right":
                return "venture-figure--right";
            default:
                return "venture-figure--center";
        }
    }

    public boolean isRounded() {
        return rounded;
    }

    public boolean isShadow() {
        return shadow;
    }

    public boolean isBorder() {
        return border;
    }

    public String getModifierClasses() {
        StringBuilder classes = new StringBuilder();
        if (rounded) classes.append(" venture-figure--rounded");
        if (shadow) classes.append(" venture-figure--shadow");
        if (border) classes.append(" venture-figure--border");
        return classes.toString();
    }

    public boolean hasCaption() {
        return caption != null && !caption.isEmpty();
    }

    public boolean hasCredit() {
        return credit != null && !credit.isEmpty();
    }

    public boolean hasCreditUrl() {
        return creditUrl != null && !creditUrl.isEmpty();
    }

    public boolean hasLink() {
        return link != null && !link.isEmpty();
    }

    public boolean hasContent() {
        return image != null && !image.isEmpty();
    }
}
