package com.aem.oak.models.article;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Category Tag Component.
 * Category/tag display with optional link.
 */
@Model(adaptables = Resource.class)
public class CategoryModel {

    @ValueMapValue
    private String name;

    @ValueMapValue
    private String link;

    @ValueMapValue
    @Default(values = "default")
    private String style;

    @ValueMapValue
    private String color;

    public String getName() {
        return name;
    }

    public String getLink() {
        return link;
    }

    public String getStyle() {
        return style;
    }

    public String getStyleClass() {
        switch (style) {
            case "pill":
                return "venture-tag--pill";
            case "badge":
                return "venture-tag--badge";
            case "outline":
                return "venture-tag--outline";
            default:
                return "venture-tag--default";
        }
    }

    public String getColor() {
        return color;
    }

    public String getColorStyle() {
        if (color != null && !color.isEmpty()) {
            return "background-color: " + color + ";";
        }
        return "";
    }

    public boolean hasLink() {
        return link != null && !link.isEmpty();
    }

    public boolean hasContent() {
        return name != null && !name.isEmpty();
    }
}
