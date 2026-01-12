package com.aem.oak.models.navigation;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sling Model for the VENTURE Pagination Component.
 * Page navigation with numbers, prev/next.
 */
@Model(adaptables = Resource.class)
public class PaginationModel {

    @ValueMapValue
    @Default(intValues = 1)
    private int currentPage;

    @ValueMapValue
    @Default(intValues = 1)
    private int totalPages;

    @ValueMapValue
    private String baseUrl;

    @ValueMapValue
    @Default(values = "page")
    private String pageParam;

    @ValueMapValue
    @Default(intValues = 5)
    private int visiblePages;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showFirstLast;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showPrevNext;

    private List<PageItem> pages;

    @PostConstruct
    protected void init() {
        pages = new ArrayList<>();

        if (totalPages <= 1) return;

        int startPage = Math.max(1, currentPage - visiblePages / 2);
        int endPage = Math.min(totalPages, startPage + visiblePages - 1);

        // Adjust start if we're near the end
        if (endPage - startPage < visiblePages - 1) {
            startPage = Math.max(1, endPage - visiblePages + 1);
        }

        // Add ellipsis at start if needed
        if (startPage > 1 && showFirstLast) {
            pages.add(new PageItem(1, buildUrl(1), false, false));
            if (startPage > 2) {
                pages.add(new PageItem(-1, null, false, true)); // Ellipsis
            }
        }

        // Add page numbers
        for (int i = startPage; i <= endPage; i++) {
            pages.add(new PageItem(i, buildUrl(i), i == currentPage, false));
        }

        // Add ellipsis at end if needed
        if (endPage < totalPages && showFirstLast) {
            if (endPage < totalPages - 1) {
                pages.add(new PageItem(-1, null, false, true)); // Ellipsis
            }
            pages.add(new PageItem(totalPages, buildUrl(totalPages), false, false));
        }
    }

    private String buildUrl(int page) {
        if (baseUrl == null) return "?" + pageParam + "=" + page;

        if (baseUrl.contains("?")) {
            return baseUrl + "&" + pageParam + "=" + page;
        }
        return baseUrl + "?" + pageParam + "=" + page;
    }

    public List<PageItem> getPages() {
        return pages;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public boolean getHasPrevious() {
        return currentPage > 1;
    }

    public boolean getHasNext() {
        return currentPage < totalPages;
    }

    public String getPreviousUrl() {
        return buildUrl(currentPage - 1);
    }

    public String getNextUrl() {
        return buildUrl(currentPage + 1);
    }

    public String getFirstUrl() {
        return buildUrl(1);
    }

    public String getLastUrl() {
        return buildUrl(totalPages);
    }

    public boolean isShowFirstLast() {
        return showFirstLast;
    }

    public boolean isShowPrevNext() {
        return showPrevNext;
    }

    public boolean hasPagination() {
        return totalPages > 1;
    }

    /**
     * Page item class.
     */
    public static class PageItem {
        private final int number;
        private final String url;
        private final boolean active;
        private final boolean ellipsis;

        public PageItem(int number, String url, boolean active, boolean ellipsis) {
            this.number = number;
            this.url = url;
            this.active = active;
            this.ellipsis = ellipsis;
        }

        public int getNumber() { return number; }
        public String getUrl() { return url; }
        public boolean isActive() { return active; }
        public boolean isEllipsis() { return ellipsis; }
    }
}
