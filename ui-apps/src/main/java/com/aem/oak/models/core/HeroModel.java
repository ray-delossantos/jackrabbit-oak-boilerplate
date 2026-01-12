package com.aem.oak.models.core;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;

/**
 * Sling Model for the VENTURE Hero Component.
 * Provides a full-width banner with background image, title, subtitle and CTA buttons.
 */
@Model(adaptables = Resource.class)
public class HeroModel {

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String subtitle;

    @ValueMapValue
    private String category;

    @ValueMapValue
    private String backgroundImage;

    @ValueMapValue
    private String backgroundAlt;

    @ValueMapValue
    @Default(values = "medium")
    private String size;

    @ValueMapValue
    @Default(values = "0.5")
    private String overlayOpacity;

    @ValueMapValue
    private String primaryCtaText;

    @ValueMapValue
    private String primaryCtaLink;

    @ValueMapValue
    private String secondaryCtaText;

    @ValueMapValue
    private String secondaryCtaLink;

    private String overlayGradient;

    @PostConstruct
    protected void init() {
        double opacity = 0.5;
        try {
            opacity = Double.parseDouble(overlayOpacity);
        } catch (NumberFormatException e) {
            opacity = 0.5;
        }

        overlayGradient = String.format(
            "linear-gradient(to bottom, rgba(0,0,0,%.1f), rgba(0,0,0,%.1f))",
            opacity * 0.6, opacity
        );
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getCategory() {
        return category;
    }

    public String getBackgroundImage() {
        return backgroundImage;
    }

    public String getBackgroundAlt() {
        return backgroundAlt;
    }

    public String getSize() {
        return size;
    }

    public String getOverlayGradient() {
        return overlayGradient;
    }

    public String getPrimaryCtaText() {
        return primaryCtaText;
    }

    public String getPrimaryCtaLink() {
        return primaryCtaLink;
    }

    public String getSecondaryCtaText() {
        return secondaryCtaText;
    }

    public String getSecondaryCtaLink() {
        return secondaryCtaLink;
    }

    public boolean hasContent() {
        return title != null && !title.isEmpty();
    }

    public boolean hasCta() {
        return (primaryCtaText != null && !primaryCtaText.isEmpty()) ||
               (secondaryCtaText != null && !secondaryCtaText.isEmpty());
    }
}
