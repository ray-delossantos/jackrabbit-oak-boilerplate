package com.aem.oak.models.navigation;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import javax.jcr.Node;
import java.util.ArrayList;
import java.util.List;

/**
 * Sling Model for the VENTURE Navigation Component.
 * Main site navigation with multi-level support.
 */
@Model(adaptables = Resource.class)
public class NavigationModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    private String rootPath;

    @ValueMapValue
    @Default(intValues = 2)
    private int depth;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showRoot;

    @ValueMapValue
    private String logoPath;

    @ValueMapValue
    private String logoAlt;

    @ValueMapValue
    private String homePath;

    private List<NavItem> items;
    private String currentPath;

    @PostConstruct
    protected void init() {
        items = new ArrayList<>();

        // Get current page path for active state
        currentPath = resource.getPath();
        if (currentPath.contains("/jcr:content")) {
            currentPath = currentPath.substring(0, currentPath.indexOf("/jcr:content"));
        }

        if (rootPath == null || rootPath.isEmpty()) {
            rootPath = "/content";
        }

        Resource rootResource = resourceResolver.getResource(rootPath);
        if (rootResource != null) {
            if (showRoot) {
                NavItem rootItem = createNavItem(rootResource, 1);
                if (rootItem != null) {
                    items.add(rootItem);
                }
            } else {
                // Add child pages directly
                for (Resource child : rootResource.getChildren()) {
                    if (isValidPage(child)) {
                        NavItem item = createNavItem(child, 1);
                        if (item != null) {
                            items.add(item);
                        }
                    }
                }
            }
        }
    }

    private NavItem createNavItem(Resource pageResource, int level) {
        Resource contentResource = pageResource.getChild("jcr:content");
        if (contentResource == null) return null;

        try {
            Node contentNode = contentResource.adaptTo(Node.class);
            if (contentNode == null) return null;

            // Check hideInNav
            if (contentNode.hasProperty("hideInNav") &&
                contentNode.getProperty("hideInNav").getBoolean()) {
                return null;
            }

            NavItem item = new NavItem();
            item.setPath(pageResource.getPath());
            item.setLink(pageResource.getPath() + ".html");

            if (contentNode.hasProperty("jcr:title")) {
                item.setTitle(contentNode.getProperty("jcr:title").getString());
            } else {
                item.setTitle(pageResource.getName());
            }

            if (contentNode.hasProperty("navTitle")) {
                item.setTitle(contentNode.getProperty("navTitle").getString());
            }

            // Check active state
            item.setActive(currentPath.equals(pageResource.getPath()));
            item.setCurrent(currentPath.startsWith(pageResource.getPath()));

            // Add children if within depth
            if (level < depth) {
                List<NavItem> children = new ArrayList<>();
                for (Resource child : pageResource.getChildren()) {
                    if (isValidPage(child)) {
                        NavItem childItem = createNavItem(child, level + 1);
                        if (childItem != null) {
                            children.add(childItem);
                        }
                    }
                }
                item.setChildren(children);
            }

            return item;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isValidPage(Resource resource) {
        return resource.getChild("jcr:content") != null &&
               !resource.getName().startsWith(".");
    }

    public List<NavItem> getItems() {
        return items;
    }

    public String getLogoPath() {
        return logoPath;
    }

    public String getLogoAlt() {
        return logoAlt;
    }

    public String getHomePath() {
        return homePath != null ? homePath : rootPath;
    }

    public boolean hasLogo() {
        return logoPath != null && !logoPath.isEmpty();
    }

    public boolean hasItems() {
        return items != null && !items.isEmpty();
    }

    /**
     * Navigation item class.
     */
    public static class NavItem {
        private String path;
        private String link;
        private String title;
        private boolean active;
        private boolean current;
        private List<NavItem> children;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }

        public boolean isCurrent() { return current; }
        public void setCurrent(boolean current) { this.current = current; }

        public List<NavItem> getChildren() { return children; }
        public void setChildren(List<NavItem> children) { this.children = children; }

        public boolean hasChildren() { return children != null && !children.isEmpty(); }
    }
}
