package com.aem.oak.models.forms;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import java.util.UUID;

/**
 * Sling Model for the VENTURE Form Component.
 * Form container with validation and submission handling.
 */
@Model(adaptables = Resource.class)
public class FormModel {

    @SlingObject
    private Resource resource;

    @ValueMapValue
    private String action;

    @ValueMapValue
    @Default(values = "POST")
    private String method;

    @ValueMapValue
    private String name;

    @ValueMapValue
    private String successMessage;

    @ValueMapValue
    private String errorMessage;

    @ValueMapValue
    private String redirectUrl;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showLabels;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean clientValidation;

    @ValueMapValue
    @Default(values = "default")
    private String style;

    @ValueMapValue
    private String submitButtonText;

    @ValueMapValue
    @Default(values = "primary")
    private String submitButtonStyle;

    private String formId;

    public FormModel() {
        this.formId = "form-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public String getAction() {
        return action != null ? action : resource.getPath() + ".submit.json";
    }

    public String getMethod() {
        return method;
    }

    public String getName() {
        return name;
    }

    public String getFormId() {
        return formId;
    }

    public String getSuccessMessage() {
        return successMessage != null ? successMessage : "Thank you! Your submission has been received.";
    }

    public String getErrorMessage() {
        return errorMessage != null ? errorMessage : "Sorry, there was an error. Please try again.";
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public boolean isShowLabels() {
        return showLabels;
    }

    public boolean isClientValidation() {
        return clientValidation;
    }

    public String getStyle() {
        return style;
    }

    public String getStyleClass() {
        switch (style) {
            case "compact":
                return "venture-form--compact";
            case "inline":
                return "venture-form--inline";
            case "floating":
                return "venture-form--floating";
            default:
                return "venture-form--default";
        }
    }

    public String getSubmitButtonText() {
        return submitButtonText != null ? submitButtonText : "Submit";
    }

    public String getSubmitButtonStyle() {
        return submitButtonStyle;
    }

    public String getSubmitButtonClass() {
        switch (submitButtonStyle) {
            case "secondary":
                return "venture-btn--secondary";
            case "coral":
                return "venture-btn--coral";
            default:
                return "venture-btn--primary";
        }
    }

    public boolean hasRedirect() {
        return redirectUrl != null && !redirectUrl.isEmpty();
    }

    public String getFieldsPath() {
        return resource.getPath() + "/fields";
    }
}
