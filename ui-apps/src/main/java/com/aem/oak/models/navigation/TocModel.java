package com.aem.oak.models.navigation;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Table of Contents Component.
 * Auto-generated navigation from page headings.
 */
@Model(adaptables = Resource.class)
public class TocModel {

    @ValueMapValue
    @Default(values = "Table of Contents")
    private String title;

    @ValueMapValue
    @Default(values = "h2")
    private String startLevel;

    @ValueMapValue
    @Default(values = "h3")
    private String endLevel;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean numbered;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean collapsible;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean startCollapsed;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean smooth;

    @ValueMapValue
    @Default(values = "80")
    private String scrollOffset;

    public String getTitle() {
        return title;
    }

    public String getStartLevel() {
        return startLevel;
    }

    public String getEndLevel() {
        return endLevel;
    }

    public String getHeadingSelector() {
        // Build CSS selector for headings
        int start = Integer.parseInt(startLevel.replace("h", ""));
        int end = Integer.parseInt(endLevel.replace("h", ""));

        StringBuilder selector = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i > start) selector.append(", ");
            selector.append("h").append(i);
        }
        return selector.toString();
    }

    public boolean isNumbered() {
        return numbered;
    }

    public boolean isCollapsible() {
        return collapsible;
    }

    public boolean isStartCollapsed() {
        return startCollapsed;
    }

    public boolean isSmooth() {
        return smooth;
    }

    public String getScrollOffset() {
        return scrollOffset;
    }

    public boolean hasTitle() {
        return title != null && !title.isEmpty();
    }
}
