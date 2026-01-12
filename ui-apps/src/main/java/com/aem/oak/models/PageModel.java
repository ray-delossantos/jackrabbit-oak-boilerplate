package com.aem.oak.models;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Sling Model for page components.
 * Used by HTL templates to access page properties.
 */
@Model(
    adaptables = Resource.class,
    defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class PageModel {

    private static final Logger LOG = LoggerFactory.getLogger(PageModel.class);

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    @Default(values = "Untitled Page")
    private String title;

    @ValueMapValue
    private String description;

    @ValueMapValue
    private String pageTitle;

    @ValueMapValue
    private String navTitle;

    @ValueMapValue
    private String template;

    @ValueMapValue(name = "jcr:created")
    private Calendar created;

    @ValueMapValue(name = "jcr:createdBy")
    private String createdBy;

    @ValueMapValue(name = "jcr:lastModified")
    private Calendar lastModified;

    @ValueMapValue(name = "jcr:lastModifiedBy")
    private String lastModifiedBy;

    @ValueMapValue
    private String[] keywords;

    @ValueMapValue
    private boolean hideInNav;

    @ChildResource(name = "content")
    private Resource contentResource;

    private String path;
    private String name;
    private List<PageModel> children;

    @PostConstruct
    protected void init() {
        this.path = resource.getPath();
        this.name = resource.getName();
        LOG.debug("PageModel initialized for: {}", path);
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getPageTitle() {
        return pageTitle != null ? pageTitle : title;
    }

    public String getNavTitle() {
        return navTitle != null ? navTitle : title;
    }

    public String getTemplate() {
        return template;
    }

    public Calendar getCreated() {
        return created;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public Calendar getLastModified() {
        return lastModified;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public String[] getKeywords() {
        return keywords;
    }

    public boolean isHideInNav() {
        return hideInNav;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public Resource getResource() {
        return resource;
    }

    public Resource getContentResource() {
        return contentResource;
    }

    /**
     * Get child pages.
     */
    public List<PageModel> getChildren() {
        if (children == null) {
            children = new ArrayList<>();
            for (Resource child : resource.getChildren()) {
                // Only include content pages, not jcr: or rep: nodes
                String childName = child.getName();
                if (!childName.startsWith("jcr:") && !childName.startsWith("rep:") &&
                    !childName.equals("content")) {

                    PageModel childPage = child.adaptTo(PageModel.class);
                    if (childPage != null && !childPage.isHideInNav()) {
                        children.add(childPage);
                    }
                }
            }
        }
        return children;
    }

    /**
     * Get navigation children (visible in navigation).
     */
    public List<PageModel> getNavChildren() {
        List<PageModel> navChildren = new ArrayList<>();
        for (PageModel child : getChildren()) {
            if (!child.isHideInNav()) {
                navChildren.add(child);
            }
        }
        return navChildren;
    }

    /**
     * Get parent page.
     */
    public PageModel getParent() {
        Resource parent = resource.getParent();
        if (parent != null && !parent.getPath().equals("/content")) {
            return parent.adaptTo(PageModel.class);
        }
        return null;
    }

    /**
     * Get breadcrumb trail from root to this page.
     */
    public List<PageModel> getBreadcrumbs() {
        List<PageModel> breadcrumbs = new ArrayList<>();
        PageModel current = this;

        while (current != null) {
            breadcrumbs.add(0, current);
            current = current.getParent();
        }

        return breadcrumbs;
    }

    /**
     * Check if this is the home page.
     */
    public boolean isHomePage() {
        return resource.getPath().equals("/content") ||
               resource.getPath().matches("/content/[^/]+");
    }

    /**
     * Get canonical URL for SEO.
     */
    public String getCanonicalUrl() {
        // Remove /content prefix and add .html extension
        String url = path;
        if (url.startsWith("/content/")) {
            url = url.substring("/content".length());
        }
        return url + ".html";
    }
}
