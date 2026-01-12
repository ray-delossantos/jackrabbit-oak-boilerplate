package com.aem.oak.models.media;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sling Model for the VENTURE Video Component.
 * Supports YouTube, Vimeo, and self-hosted videos.
 */
@Model(adaptables = Resource.class)
public class VideoModel {

    private static final Pattern YOUTUBE_PATTERN = Pattern.compile(
        "(?:youtube\\.com/(?:watch\\?v=|embed/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
    );
    private static final Pattern VIMEO_PATTERN = Pattern.compile(
        "vimeo\\.com/(?:video/)?(\\d+)"
    );

    @ValueMapValue
    private String videoUrl;

    @ValueMapValue
    private String videoFile;

    @ValueMapValue
    private String posterImage;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String caption;

    @ValueMapValue
    @Default(values = "16:9")
    private String aspectRatio;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean autoplay;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean loop;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean controls;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean muted;

    private String videoType;
    private String videoId;
    private String embedUrl;

    @PostConstruct
    protected void init() {
        if (videoUrl != null && !videoUrl.isEmpty()) {
            parseVideoUrl();
        } else if (videoFile != null && !videoFile.isEmpty()) {
            videoType = "self-hosted";
        }
    }

    private void parseVideoUrl() {
        // Check YouTube
        Matcher ytMatcher = YOUTUBE_PATTERN.matcher(videoUrl);
        if (ytMatcher.find()) {
            videoType = "youtube";
            videoId = ytMatcher.group(1);
            embedUrl = buildYouTubeEmbed();
            return;
        }

        // Check Vimeo
        Matcher vimeoMatcher = VIMEO_PATTERN.matcher(videoUrl);
        if (vimeoMatcher.find()) {
            videoType = "vimeo";
            videoId = vimeoMatcher.group(1);
            embedUrl = buildVimeoEmbed();
            return;
        }

        // Assume direct video URL
        videoType = "self-hosted";
        videoFile = videoUrl;
    }

    private String buildYouTubeEmbed() {
        StringBuilder url = new StringBuilder();
        url.append("https://www.youtube-nocookie.com/embed/").append(videoId);
        url.append("?rel=0");
        if (autoplay) url.append("&autoplay=1");
        if (loop) url.append("&loop=1&playlist=").append(videoId);
        if (muted) url.append("&mute=1");
        if (!controls) url.append("&controls=0");
        return url.toString();
    }

    private String buildVimeoEmbed() {
        StringBuilder url = new StringBuilder();
        url.append("https://player.vimeo.com/video/").append(videoId);
        url.append("?dnt=1");
        if (autoplay) url.append("&autoplay=1");
        if (loop) url.append("&loop=1");
        if (muted) url.append("&muted=1");
        return url.toString();
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public String getVideoFile() {
        return videoFile;
    }

    public String getPosterImage() {
        return posterImage;
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
                return "venture-video--4-3";
            case "1:1":
                return "venture-video--1-1";
            case "21:9":
                return "venture-video--21-9";
            default:
                return "venture-video--16-9";
        }
    }

    public boolean isAutoplay() {
        return autoplay;
    }

    public boolean isLoop() {
        return loop;
    }

    public boolean isControls() {
        return controls;
    }

    public boolean isMuted() {
        return muted;
    }

    public String getVideoType() {
        return videoType;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getEmbedUrl() {
        return embedUrl;
    }

    public boolean isYouTube() {
        return "youtube".equals(videoType);
    }

    public boolean isVimeo() {
        return "vimeo".equals(videoType);
    }

    public boolean isSelfHosted() {
        return "self-hosted".equals(videoType);
    }

    public boolean hasContent() {
        return (videoUrl != null && !videoUrl.isEmpty()) ||
               (videoFile != null && !videoFile.isEmpty());
    }
}
