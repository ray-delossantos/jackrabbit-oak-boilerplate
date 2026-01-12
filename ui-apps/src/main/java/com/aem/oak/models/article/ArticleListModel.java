package com.aem.oak.models.article;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Sling Model for the VENTURE Article List Component.
 * Paginated article listing with filters.
 */
@Model(adaptables = Resource.class)
public class ArticleListModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    private String parentPath;

    @ValueMapValue
    private String categoryFilter;

    @ValueMapValue
    private String authorFilter;

    @ValueMapValue
    @Default(intValues = 10)
    private int pageSize;

    @ValueMapValue
    @Default(intValues = 1)
    private int currentPage;

    @ValueMapValue
    @Default(values = "grid")
    private String layout;

    @ValueMapValue
    @Default(values = "date")
    private String orderBy;

    private List<ArticleItem> articles;
    private int totalArticles;
    private int totalPages;

    @PostConstruct
    protected void init() {
        articles = new ArrayList<>();

        if (parentPath == null || parentPath.isEmpty()) {
            return;
        }

        try {
            Session session = resourceResolver.adaptTo(Session.class);
            if (session == null) return;

            QueryManager queryManager = session.getWorkspace().getQueryManager();

            // Build query
            StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("SELECT * FROM [cq:Page] AS page WHERE ISDESCENDANTNODE(page, '")
                       .append(parentPath)
                       .append("')");

            if (categoryFilter != null && !categoryFilter.isEmpty()) {
                queryBuilder.append(" AND page.[jcr:content/category] = '")
                           .append(categoryFilter)
                           .append("'");
            }

            if (authorFilter != null && !authorFilter.isEmpty()) {
                queryBuilder.append(" AND page.[jcr:content/authorName] = '")
                           .append(authorFilter)
                           .append("'");
            }

            queryBuilder.append(" ORDER BY page.[jcr:content/")
                       .append("date".equals(orderBy) ? "publishDate" : "jcr:title")
                       .append("] ")
                       .append("date".equals(orderBy) ? "DESC" : "ASC");

            Query query = queryManager.createQuery(queryBuilder.toString(), Query.JCR_SQL2);
            QueryResult result = query.execute();

            // Get total count
            NodeIterator countNodes = result.getNodes();
            totalArticles = (int) countNodes.getSize();
            totalPages = (int) Math.ceil((double) totalArticles / pageSize);

            // Apply pagination
            int offset = (currentPage - 1) * pageSize;
            query.setOffset(offset);
            query.setLimit(pageSize);

            QueryResult pagedResult = query.execute();
            NodeIterator nodes = pagedResult.getNodes();

            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy");

            while (nodes.hasNext()) {
                Node pageNode = nodes.nextNode();
                Node contentNode = pageNode.hasNode("jcr:content")
                    ? pageNode.getNode("jcr:content")
                    : null;

                if (contentNode != null) {
                    ArticleItem item = new ArticleItem();
                    item.setPath(pageNode.getPath());
                    item.setLink(pageNode.getPath() + ".html");

                    if (contentNode.hasProperty("jcr:title")) {
                        item.setTitle(contentNode.getProperty("jcr:title").getString());
                    } else {
                        item.setTitle(pageNode.getName());
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

                    if (contentNode.hasProperty("authorName")) {
                        item.setAuthorName(contentNode.getProperty("authorName").getString());
                    }

                    if (contentNode.hasProperty("publishDate")) {
                        Calendar cal = contentNode.getProperty("publishDate").getDate();
                        item.setFormattedDate(sdf.format(cal.getTime()));
                    }

                    articles.add(item);
                }
            }

        } catch (RepositoryException e) {
            // Log error, return empty list
        }
    }

    public List<ArticleItem> getArticles() {
        return articles;
    }

    public int getTotalArticles() {
        return totalArticles;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public boolean getHasPrevious() {
        return currentPage > 1;
    }

    public boolean getHasNext() {
        return currentPage < totalPages;
    }

    public int getPreviousPage() {
        return currentPage - 1;
    }

    public int getNextPage() {
        return currentPage + 1;
    }

    public String getLayout() {
        return layout;
    }

    public String getLayoutClass() {
        return "grid".equals(layout) ? "venture-article-list--grid" : "venture-article-list--list";
    }

    public boolean hasArticles() {
        return articles != null && !articles.isEmpty();
    }

    /**
     * Inner class representing an article item.
     */
    public static class ArticleItem {
        private String path;
        private String link;
        private String title;
        private String excerpt;
        private String image;
        private String category;
        private String authorName;
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

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }

        public String getFormattedDate() { return formattedDate; }
        public void setFormattedDate(String formattedDate) { this.formattedDate = formattedDate; }
    }
}
