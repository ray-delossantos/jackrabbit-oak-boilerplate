package com.aem.oak.models.specialty;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sling Model for the VENTURE Adventure Map Component.
 * Interactive location map with markers.
 */
@Model(adaptables = Resource.class)
public class MapModel {

    @ValueMapValue
    private String title;

    @ValueMapValue
    @Default(doubleValues = 0)
    private double centerLat;

    @ValueMapValue
    @Default(doubleValues = 0)
    private double centerLng;

    @ValueMapValue
    @Default(intValues = 4)
    private int zoom;

    @ValueMapValue
    @Default(values = "400px")
    private String height;

    @ValueMapValue
    @Default(values = "default")
    private String style;

    @ChildResource(name = "locations")
    private List<Resource> locationResources;

    private List<Location> locations;

    @PostConstruct
    protected void init() {
        locations = new ArrayList<>();

        if (locationResources != null) {
            for (Resource locResource : locationResources) {
                Location loc = new Location();
                loc.setName(locResource.getValueMap().get("name", String.class));
                loc.setDescription(locResource.getValueMap().get("description", String.class));
                loc.setLat(locResource.getValueMap().get("lat", 0.0));
                loc.setLng(locResource.getValueMap().get("lng", 0.0));
                loc.setLink(locResource.getValueMap().get("link", String.class));
                loc.setImage(locResource.getValueMap().get("image", String.class));
                loc.setCategory(locResource.getValueMap().get("category", String.class));
                locations.add(loc);
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public double getCenterLat() {
        return centerLat;
    }

    public double getCenterLng() {
        return centerLng;
    }

    public int getZoom() {
        return zoom;
    }

    public String getHeight() {
        return height;
    }

    public String getStyle() {
        return style;
    }

    public List<Location> getLocations() {
        return locations;
    }

    public String getLocationsJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < locations.size(); i++) {
            Location loc = locations.get(i);
            if (i > 0) json.append(",");
            json.append("{")
                .append("\"name\":\"").append(escapeJson(loc.getName())).append("\",")
                .append("\"description\":\"").append(escapeJson(loc.getDescription())).append("\",")
                .append("\"lat\":").append(loc.getLat()).append(",")
                .append("\"lng\":").append(loc.getLng()).append(",")
                .append("\"link\":\"").append(escapeJson(loc.getLink())).append("\",")
                .append("\"image\":\"").append(escapeJson(loc.getImage())).append("\",")
                .append("\"category\":\"").append(escapeJson(loc.getCategory())).append("\"")
                .append("}");
        }
        json.append("]");
        return json.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    public boolean hasContent() {
        return locations != null && !locations.isEmpty();
    }

    public boolean hasTitle() {
        return title != null && !title.isEmpty();
    }

    /**
     * Location class.
     */
    public static class Location {
        private String name;
        private String description;
        private double lat;
        private double lng;
        private String link;
        private String image;
        private String category;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }

        public double getLng() { return lng; }
        public void setLng(double lng) { this.lng = lng; }

        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }

        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
    }
}
