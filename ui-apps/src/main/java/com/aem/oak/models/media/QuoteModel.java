package com.aem.oak.models.media;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Quote Component.
 * Blockquote with attribution and styling options.
 */
@Model(adaptables = Resource.class)
public class QuoteModel {

    @ValueMapValue
    private String quote;

    @ValueMapValue
    private String attribution;

    @ValueMapValue
    private String attributionTitle;

    @ValueMapValue
    private String attributionImage;

    @ValueMapValue
    private String source;

    @ValueMapValue
    private String sourceUrl;

    @ValueMapValue
    @Default(values = "default")
    private String style;

    @ValueMapValue
    @Default(values = "left")
    private String alignment;

    public String getQuote() {
        return quote;
    }

    public String getAttribution() {
        return attribution;
    }

    public String getAttributionTitle() {
        return attributionTitle;
    }

    public String getAttributionImage() {
        return attributionImage;
    }

    public String getSource() {
        return source;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getStyle() {
        return style;
    }

    public String getStyleClass() {
        switch (style) {
            case "large":
                return "venture-quote--large";
            case "pullquote":
                return "venture-quote--pullquote";
            case "testimonial":
                return "venture-quote--testimonial";
            case "minimal":
                return "venture-quote--minimal";
            default:
                return "venture-quote--default";
        }
    }

    public String getAlignment() {
        return alignment;
    }

    public String getAlignmentClass() {
        switch (alignment) {
            case "center":
                return "venture-quote--center";
            case "right":
                return "venture-quote--right";
            default:
                return "venture-quote--left";
        }
    }

    public boolean hasAttribution() {
        return attribution != null && !attribution.isEmpty();
    }

    public boolean hasSource() {
        return source != null && !source.isEmpty();
    }

    public boolean hasSourceUrl() {
        return sourceUrl != null && !sourceUrl.isEmpty();
    }

    public boolean hasAttributionImage() {
        return attributionImage != null && !attributionImage.isEmpty();
    }

    public boolean hasContent() {
        return quote != null && !quote.isEmpty();
    }
}
