package com.aem.oak.models.media;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;

/**
 * Sling Model for the VENTURE Embed Component.
 * External content embed (iframes, social posts, maps).
 */
@Model(adaptables = Resource.class)
public class EmbedModel {

    @ValueMapValue
    private String embedCode;

    @ValueMapValue
    private String embedUrl;

    @ValueMapValue
    private String embedType;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String caption;

    @ValueMapValue
    @Default(values = "16:9")
    private String aspectRatio;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean responsive;

    @ValueMapValue
    @Default(values = "600")
    private String maxWidth;

    private boolean isIframe;

    @PostConstruct
    protected void init() {
        // Detect if embed code contains iframe
        if (embedCode != null) {
            isIframe = embedCode.toLowerCase().contains("<iframe");
        }

        // Auto-detect embed type from URL
        if (embedType == null && embedUrl != null) {
            detectEmbedType();
        }
    }

    private void detectEmbedType() {
        String url = embedUrl.toLowerCase();
        if (url.contains("twitter.com") || url.contains("x.com")) {
            embedType = "twitter";
        } else if (url.contains("instagram.com")) {
            embedType = "instagram";
        } else if (url.contains("maps.google") || url.contains("google.com/maps")) {
            embedType = "google-maps";
        } else if (url.contains("codepen.io")) {
            embedType = "codepen";
        } else if (url.contains("spotify.com")) {
            embedType = "spotify";
        } else if (url.contains("soundcloud.com")) {
            embedType = "soundcloud";
        } else {
            embedType = "generic";
        }
    }

    public String getEmbedCode() {
        return embedCode;
    }

    public String getEmbedUrl() {
        return embedUrl;
    }

    public String getEmbedType() {
        return embedType;
    }

    public String getTitle() {
        return title;
    }

    public String getCaption() {
        return caption;
    }

    public String getAspectRatio() {
        return aspectRatio;
    }

    public String getAspectRatioClass() {
        switch (aspectRatio) {
            case "4:3":
                return "venture-embed--4-3";
            case "1:1":
                return "venture-embed--1-1";
            case "9:16":
                return "venture-embed--9-16";
            case "auto":
                return "venture-embed--auto";
            default:
                return "venture-embed--16-9";
        }
    }

    public boolean isResponsive() {
        return responsive;
    }

    public String getMaxWidth() {
        return maxWidth;
    }

    public String getMaxWidthStyle() {
        if (maxWidth != null && !maxWidth.isEmpty()) {
            try {
                Integer.parseInt(maxWidth);
                return "max-width: " + maxWidth + "px;";
            } catch (NumberFormatException e) {
                return "max-width: " + maxWidth + ";";
            }
        }
        return "";
    }

    public boolean isIframe() {
        return isIframe;
    }

    public boolean isTwitter() {
        return "twitter".equals(embedType);
    }

    public boolean isInstagram() {
        return "instagram".equals(embedType);
    }

    public boolean isGoogleMaps() {
        return "google-maps".equals(embedType);
    }

    public boolean hasEmbedCode() {
        return embedCode != null && !embedCode.isEmpty();
    }

    public boolean hasEmbedUrl() {
        return embedUrl != null && !embedUrl.isEmpty();
    }

    public boolean hasContent() {
        return hasEmbedCode() || hasEmbedUrl();
    }

    public boolean hasCaption() {
        return caption != null && !caption.isEmpty();
    }
}
