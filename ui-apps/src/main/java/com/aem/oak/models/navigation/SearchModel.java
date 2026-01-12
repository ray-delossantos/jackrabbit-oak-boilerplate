package com.aem.oak.models.navigation;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Session;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Sling Model for the VENTURE Search Component.
 * Search form and results display.
 */
@Model(adaptables = Resource.class)
public class SearchModel {

    @SlingObject
    private Resource resource;

    @SlingObject
    private ResourceResolver resourceResolver;

    @ValueMapValue
    private String searchPath;

    @ValueMapValue
    private String query;

    @ValueMapValue
    @Default(values = "Search...")
    private String placeholder;

    @ValueMapValue
    @Default(values = "Search")
    private String buttonText;

    @ValueMapValue
    @Default(intValues = 10)
    private int resultsPerPage;

    @ValueMapValue
    @Default(intValues = 1)
    private int currentPage;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showExcerpt;

    @ValueMapValue
    @Default(values = "default")
    private String style;

    private List<SearchResult> results;
    private int totalResults;
    private int totalPages;
    private boolean hasSearched;

    @PostConstruct
    protected void init() {
        results = new ArrayList<>();
        hasSearched = query != null && !query.trim().isEmpty();

        if (hasSearched) {
            executeSearch();
        }
    }

    private void executeSearch() {
        if (searchPath == null || searchPath.isEmpty()) {
            searchPath = "/content";
        }

        try {
            Session session = resourceResolver.adaptTo(Session.class);
            if (session == null) return;

            QueryManager queryManager = session.getWorkspace().getQueryManager();

            // Build full-text search query
            String escapedQuery = query.replace("'", "''");
            String jcrQuery = String.format(
                "SELECT * FROM [cq:Page] AS page " +
                "WHERE ISDESCENDANTNODE(page, '%s') " +
                "AND CONTAINS(page.*, '%s') " +
                "ORDER BY [jcr:score] DESC",
                searchPath, escapedQuery
            );

            Query jcrQueryObj = queryManager.createQuery(jcrQuery, Query.JCR_SQL2);

            // Get total count
            QueryResult countResult = jcrQueryObj.execute();
            NodeIterator countNodes = countResult.getNodes();
            totalResults = (int) countNodes.getSize();
            totalPages = (int) Math.ceil((double) totalResults / resultsPerPage);

            // Apply pagination
            int offset = (currentPage - 1) * resultsPerPage;
            jcrQueryObj.setOffset(offset);
            jcrQueryObj.setLimit(resultsPerPage);

            QueryResult pagedResult = jcrQueryObj.execute();
            NodeIterator nodes = pagedResult.getNodes();

            SimpleDateFormat sdf = new SimpleDateFormat("MMM d, yyyy");

            while (nodes.hasNext()) {
                Node pageNode = nodes.nextNode();
                Node contentNode = pageNode.hasNode("jcr:content")
                    ? pageNode.getNode("jcr:content")
                    : null;

                if (contentNode != null) {
                    SearchResult result = new SearchResult();
                    result.setPath(pageNode.getPath());
                    result.setLink(pageNode.getPath() + ".html");

                    if (contentNode.hasProperty("jcr:title")) {
                        result.setTitle(contentNode.getProperty("jcr:title").getString());
                    } else {
                        result.setTitle(pageNode.getName());
                    }

                    if (contentNode.hasProperty("jcr:description")) {
                        String excerpt = contentNode.getProperty("jcr:description").getString();
                        result.setExcerpt(highlightQuery(excerpt, query));
                    }

                    if (contentNode.hasProperty("heroImage")) {
                        result.setImage(contentNode.getProperty("heroImage").getString());
                    }

                    if (contentNode.hasProperty("publishDate")) {
                        Calendar cal = contentNode.getProperty("publishDate").getDate();
                        result.setFormattedDate(sdf.format(cal.getTime()));
                    }

                    results.add(result);
                }
            }

        } catch (Exception e) {
            // Log error, return empty results
        }
    }

    private String highlightQuery(String text, String query) {
        if (text == null || query == null) return text;

        // Simple case-insensitive highlight
        String lowerText = text.toLowerCase();
        String lowerQuery = query.toLowerCase();
        int index = lowerText.indexOf(lowerQuery);

        if (index >= 0) {
            return text.substring(0, index) +
                   "<mark>" + text.substring(index, index + query.length()) + "</mark>" +
                   text.substring(index + query.length());
        }
        return text;
    }

    public String getQuery() {
        return query;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public String getButtonText() {
        return buttonText;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public int getTotalResults() {
        return totalResults;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public boolean isShowExcerpt() {
        return showExcerpt;
    }

    public String getStyle() {
        return style;
    }

    public String getStyleClass() {
        switch (style) {
            case "minimal":
                return "venture-search--minimal";
            case "prominent":
                return "venture-search--prominent";
            default:
                return "venture-search--default";
        }
    }

    public boolean hasSearched() {
        return hasSearched;
    }

    public boolean hasResults() {
        return results != null && !results.isEmpty();
    }

    public boolean getHasPrevious() {
        return currentPage > 1;
    }

    public boolean getHasNext() {
        return currentPage < totalPages;
    }

    /**
     * Search result class.
     */
    public static class SearchResult {
        private String path;
        private String link;
        private String title;
        private String excerpt;
        private String image;
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

        public String getFormattedDate() { return formattedDate; }
        public void setFormattedDate(String formattedDate) { this.formattedDate = formattedDate; }

        public boolean hasImage() { return image != null && !image.isEmpty(); }
    }
}
