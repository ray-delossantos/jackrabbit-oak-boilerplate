package com.aem.oak.models.forms;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Contact Form Component.
 * Pre-built contact form with standard fields.
 */
@Model(adaptables = Resource.class)
public class ContactModel {

    @ValueMapValue
    @Default(values = "Get in Touch")
    private String title;

    @ValueMapValue
    private String description;

    @ValueMapValue
    private String action;

    @ValueMapValue
    @Default(values = "Thank you for your message. We'll get back to you soon!")
    private String successMessage;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showName;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showEmail;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean showPhone;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean showSubject;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showMessage;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean nameRequired;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean emailRequired;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean phoneRequired;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean messageRequired;

    @ValueMapValue
    @Default(values = "Send Message")
    private String buttonText;

    @ValueMapValue
    @Default(values = "default")
    private String style;

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getAction() {
        return action;
    }

    public String getSuccessMessage() {
        return successMessage;
    }

    public boolean isShowName() {
        return showName;
    }

    public boolean isShowEmail() {
        return showEmail;
    }

    public boolean isShowPhone() {
        return showPhone;
    }

    public boolean isShowSubject() {
        return showSubject;
    }

    public boolean isShowMessage() {
        return showMessage;
    }

    public boolean isNameRequired() {
        return nameRequired;
    }

    public boolean isEmailRequired() {
        return emailRequired;
    }

    public boolean isPhoneRequired() {
        return phoneRequired;
    }

    public boolean isMessageRequired() {
        return messageRequired;
    }

    public String getButtonText() {
        return buttonText;
    }

    public String getStyle() {
        return style;
    }

    public String getStyleClass() {
        switch (style) {
            case "compact":
                return "venture-contact--compact";
            case "side-by-side":
                return "venture-contact--side-by-side";
            default:
                return "venture-contact--default";
        }
    }

    public boolean hasTitle() {
        return title != null && !title.isEmpty();
    }

    public boolean hasDescription() {
        return description != null && !description.isEmpty();
    }
}
