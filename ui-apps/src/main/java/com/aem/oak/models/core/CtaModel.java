package com.aem.oak.models.core;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE CTA Component.
 * Call-to-action button with style variants.
 */
@Model(adaptables = Resource.class)
public class CtaModel {

    @ValueMapValue
    private String text;

    @ValueMapValue
    private String link;

    @ValueMapValue
    @Default(values = "primary")
    private String style;

    @ValueMapValue
    @Default(values = "normal")
    private String size;

    @ValueMapValue
    @Default(values = "_self")
    private String target;

    public String getText() {
        return text;
    }

    public String getLink() {
        return link;
    }

    public String getTarget() {
        return target;
    }

    public String getStyleClass() {
        switch (style) {
            case "secondary":
                return "venture-btn--secondary";
            case "outline":
                return "venture-btn--outline";
            case "coral":
                return "venture-btn--coral";
            default:
                return "venture-btn--primary";
        }
    }

    public String getSizeClass() {
        switch (size) {
            case "large":
                return "venture-btn--large";
            case "small":
                return "venture-btn--small";
            default:
                return "";
        }
    }

    public boolean hasContent() {
        return text != null && !text.isEmpty() && link != null && !link.isEmpty();
    }
}
