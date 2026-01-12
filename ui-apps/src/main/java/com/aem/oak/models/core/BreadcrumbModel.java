package com.aem.oak.models.core;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;

import javax.annotation.PostConstruct;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sling Model for the VENTURE Breadcrumb Component.
 * Navigation trail showing page hierarchy.
 */
@Model(adaptables = Resource.class)
public class BreadcrumbModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    private List<BreadcrumbItem> items;

    @PostConstruct
    protected void init() {
        items = new ArrayList<>();

        try {
            Resource currentPage = findPage(resource);
            if (currentPage != null) {
                buildBreadcrumb(currentPage);
            }
        } catch (Exception e) {
            // Log error, return empty breadcrumb
        }
    }

    private Resource findPage(Resource res) {
        Resource current = res;
        while (current != null) {
            if (current.getPath().startsWith("/content/") &&
                current.getChild("jcr:content") != null) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private void buildBreadcrumb(Resource page) throws RepositoryException {
        List<BreadcrumbItem> trail = new ArrayList<>();
        Resource current = page;

        while (current != null && current.getPath().startsWith("/content/")) {
            Resource content = current.getChild("jcr:content");
            if (content != null) {
                Node contentNode = content.adaptTo(Node.class);
                if (contentNode != null) {
                    String title = contentNode.hasProperty("jcr:title")
                        ? contentNode.getProperty("jcr:title").getString()
                        : current.getName();

                    boolean hideInNav = contentNode.hasProperty("hideInNav")
                        && contentNode.getProperty("hideInNav").getBoolean();

                    if (!hideInNav) {
                        boolean isActive = current.getPath().equals(page.getPath());
                        trail.add(new BreadcrumbItem(title, current.getPath() + ".html", isActive));
                    }
                }
            }
            current = current.getParent();
        }

        Collections.reverse(trail);
        this.items = trail;
    }

    public List<BreadcrumbItem> getItems() {
        return items;
    }

    public static class BreadcrumbItem {
        private final String title;
        private final String url;
        private final boolean active;

        public BreadcrumbItem(String title, String url, boolean active) {
            this.title = title;
            this.url = url;
            this.active = active;
        }

        public String getTitle() {
            return title;
        }

        public String getUrl() {
            return url;
        }

        public boolean isActive() {
            return active;
        }
    }
}
