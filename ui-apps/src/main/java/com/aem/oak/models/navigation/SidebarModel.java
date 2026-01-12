package com.aem.oak.models.navigation;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Sidebar Component.
 * Sidebar widget container.
 */
@Model(adaptables = Resource.class)
public class SidebarModel {

    @SlingObject
    private Resource resource;

    @ValueMapValue
    private String title;

    @ValueMapValue
    @Default(values = "right")
    private String position;

    @ValueMapValue
    @Default(values = "default")
    private String style;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean sticky;

    @ValueMapValue
    @Default(values = "100px")
    private String stickyOffset;

    public String getTitle() {
        return title;
    }

    public String getPosition() {
        return position;
    }

    public String getPositionClass() {
        return "left".equals(position) ? "venture-sidebar--left" : "venture-sidebar--right";
    }

    public String getStyle() {
        return style;
    }

    public String getStyleClass() {
        switch (style) {
            case "bordered":
                return "venture-sidebar--bordered";
            case "shadowed":
                return "venture-sidebar--shadowed";
            case "minimal":
                return "venture-sidebar--minimal";
            default:
                return "venture-sidebar--default";
        }
    }

    public boolean isSticky() {
        return sticky;
    }

    public String getStickyOffset() {
        return stickyOffset;
    }

    public String getStickyStyle() {
        if (sticky) {
            return "position: sticky; top: " + stickyOffset + ";";
        }
        return "";
    }

    public boolean hasTitle() {
        return title != null && !title.isEmpty();
    }

    public String getWidgetsPath() {
        return resource.getPath() + "/widgets";
    }
}
