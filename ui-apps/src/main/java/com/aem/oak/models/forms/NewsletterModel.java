package com.aem.oak.models.forms;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Newsletter Component.
 * Email signup form.
 */
@Model(adaptables = Resource.class)
public class NewsletterModel {

    @ValueMapValue
    @Default(values = "Subscribe to our newsletter")
    private String title;

    @ValueMapValue
    private String description;

    @ValueMapValue
    @Default(values = "Enter your email")
    private String placeholder;

    @ValueMapValue
    @Default(values = "Subscribe")
    private String buttonText;

    @ValueMapValue
    private String action;

    @ValueMapValue
    @Default(values = "Thank you for subscribing!")
    private String successMessage;

    @ValueMapValue
    @Default(values = "default")
    private String style;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean showPrivacyNote;

    @ValueMapValue
    private String privacyNote;

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getButtonText() {
        return buttonText;
    }

    public String getAction() {
        return action;
    }

    public String getSuccessMessage() {
        return successMessage;
    }

    public String getStyle() {
        return style;
    }

    public String getStyleClass() {
        switch (style) {
            case "dark":
                return "venture-newsletter--dark";
            case "card":
                return "venture-newsletter--card";
            case "minimal":
                return "venture-newsletter--minimal";
            case "banner":
                return "venture-newsletter--banner";
            default:
                return "venture-newsletter--default";
        }
    }

    public boolean isShowPrivacyNote() {
        return showPrivacyNote;
    }

    public String getPrivacyNote() {
        return privacyNote != null ? privacyNote : "We respect your privacy. Unsubscribe at any time.";
    }

    public boolean hasTitle() {
        return title != null && !title.isEmpty();
    }

    public boolean hasDescription() {
        return description != null && !description.isEmpty();
    }
}
