package com.aem.oak.models.specialty;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

/**
 * Sling Model for the VENTURE Gear Review Component.
 * Product review card with rating.
 */
@Model(adaptables = Resource.class)
public class GearModel {

    @ValueMapValue
    private String name;

    @ValueMapValue
    private String brand;

    @ValueMapValue
    private String image;

    @ValueMapValue
    private String description;

    @ValueMapValue
    @Default(doubleValues = 0)
    private double rating;

    @ValueMapValue
    private String price;

    @ValueMapValue
    private String buyLink;

    @ValueMapValue
    private String pros;

    @ValueMapValue
    private String cons;

    @ValueMapValue
    private String verdict;

    @ValueMapValue
    @Default(values = "card")
    private String layout;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean editorsChoice;

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getImage() {
        return image;
    }

    public String getDescription() {
        return description;
    }

    public double getRating() {
        return rating;
    }

    public int getRatingStars() {
        return (int) Math.round(rating);
    }

    public String getRatingDisplay() {
        return String.format("%.1f", rating);
    }

    public String getPrice() {
        return price;
    }

    public String getBuyLink() {
        return buyLink;
    }

    public String getPros() {
        return pros;
    }

    public String[] getProsList() {
        return pros != null ? pros.split("\n") : new String[0];
    }

    public String getCons() {
        return cons;
    }

    public String[] getConsList() {
        return cons != null ? cons.split("\n") : new String[0];
    }

    public String getVerdict() {
        return verdict;
    }

    public String getLayout() {
        return layout;
    }

    public String getLayoutClass() {
        return "full".equals(layout) ? "venture-gear--full" : "venture-gear--card";
    }

    public boolean isEditorsChoice() {
        return editorsChoice;
    }

    public boolean hasContent() {
        return name != null && !name.isEmpty();
    }

    public boolean hasBuyLink() {
        return buyLink != null && !buyLink.isEmpty();
    }

    public boolean hasPros() {
        return pros != null && !pros.isEmpty();
    }

    public boolean hasCons() {
        return cons != null && !cons.isEmpty();
    }

    public boolean hasVerdict() {
        return verdict != null && !verdict.isEmpty();
    }
}
