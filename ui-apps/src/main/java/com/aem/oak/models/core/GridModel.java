package com.aem.oak.models.core;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Grid Component.
 * Provides responsive grid layouts with configurable columns.
 */
@Model(adaptables = Resource.class)
public class GridModel {

    @ValueMapValue
    @Default(intValues = 3)
    private int columns;

    @ValueMapValue
    private String gap;

    @ValueMapValue
    private String cssClass;

    @ValueMapValue
    private String backgroundColor;

    @ValueMapValue
    private String padding;

    public int getColumns() {
        return columns;
    }

    public String getGap() {
        return gap;
    }

    public String getCssClass() {
        return cssClass != null ? cssClass : "";
    }

    public String getComputedStyles() {
        StringBuilder styles = new StringBuilder();

        if (gap != null && !gap.isEmpty()) {
            styles.append("gap: ").append(gap).append(";");
        }

        if (backgroundColor != null && !backgroundColor.isEmpty()) {
            styles.append("background-color: ").append(backgroundColor).append(";");
        }

        if (padding != null && !padding.isEmpty()) {
            styles.append("padding: ").append(padding).append(";");
        }

        return styles.length() > 0 ? styles.toString() : null;
    }
}
