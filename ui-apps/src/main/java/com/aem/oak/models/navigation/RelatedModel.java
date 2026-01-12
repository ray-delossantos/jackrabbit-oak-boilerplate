package com.aem.oak.models.navigation;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import javax.jcr.Node;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Sling Model for the VENTURE Related Articles Component.
 * Related content suggestions.
 */
@Model(adaptables = Resource.class)
public class RelatedModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    @Default(values = "Related Articles")
    private String title;

    @ValueMapValue
    @Default(intValues = 3)
    private int limit;

    @ValueMapValue
    @Default(values = "grid")
    private String layout;

    @ChildResource(name = "articles")
    private List<Resource> articleResources;

    private List<RelatedItem> articles;

    @PostConstruct
    protected void init() {
        articles = new ArrayList<>();

        if (articleResources != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy");
            int count = 0;

            for (Resource articleRef : articleResources) {
                if (count >= limit) break;

                String articlePath = articleRef.getValueMap().get("path", String.class);
                if (articlePath == null) continue;

                Resource articleResource = resourceResolver.getResource(articlePath);
                if (articleResource == null) continue;

                Resource contentResource = articleResource.getChild("jcr:content");
                if (contentResource == null) continue;

                try {
                    Node contentNode = contentResource.adaptTo(Node.class);
                    if (contentNode == null) continue;

                    RelatedItem item = new RelatedItem();
                    item.setPath(articleResource.getPath());
                    item.setLink(articleResource.getPath() + ".html");

                    if (contentNode.hasProperty("jcr:title")) {
                        item.setTitle(contentNode.getProperty("jcr:title").getString());
                    } else {
                        item.setTitle(articleResource.getName());
                    }

                    if (contentNode.hasProperty("jcr:description")) {
                        item.setExcerpt(contentNode.getProperty("jcr:description").getString());
                    }

                    if (contentNode.hasProperty("heroImage")) {
                        item.setImage(contentNode.getProperty("heroImage").getString());
                    }

                    if (contentNode.hasProperty("category")) {
                        item.setCategory(contentNode.getProperty("category").getString());
                    }

                    if (contentNode.hasProperty("publishDate")) {
                        Calendar cal = contentNode.getProperty("publishDate").getDate();
                        item.setFormattedDate(sdf.format(cal.getTime()));
                    }

                    articles.add(item);
                    count++;
                } catch (Exception e) {
                    // Skip this article
                }
            }
        }
    }

    public String getTitle() {
        return title;
    }

    public int getLimit() {
        return limit;
    }

    public String getLayout() {
        return layout;
    }

    public String getLayoutClass() {
        return "list".equals(layout) ? "venture-related--list" : "venture-related--grid";
    }

    public List<RelatedItem> getArticles() {
        return articles;
    }

    public boolean hasArticles() {
        return articles != null && !articles.isEmpty();
    }

    public boolean hasTitle() {
        return title != null && !title.isEmpty();
    }

    /**
     * Related item class.
     */
    public static class RelatedItem {
        private String path;
        private String link;
        private String title;
        private String excerpt;
        private String image;
        private String category;
        private String formattedDate;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getLink() { return link; }
        public void setLink(String link) { this.link = link; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getExcerpt() { return excerpt; }
        public void setExcerpt(String excerpt) { this.excerpt = excerpt; }

        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getFormattedDate() { return formattedDate; }
        public void setFormattedDate(String formattedDate) { this.formattedDate = formattedDate; }

        public boolean hasImage() { return image != null && !image.isEmpty(); }
        public boolean hasCategory() { return category != null && !category.isEmpty(); }
    }
}
