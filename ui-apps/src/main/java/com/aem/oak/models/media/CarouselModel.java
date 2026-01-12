package com.aem.oak.models.media;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.Default;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.apache.sling.models.annotations.injectorspecific.SlingObject;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Sling Model for the VENTURE Carousel Component.
 * Content slider with navigation and autoplay.
 */
@Model(adaptables = Resource.class)
public class CarouselModel {

    @SlingObject
    private Resource resource;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showArrows;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean showDots;

    @ValueMapValue
    @Default(booleanValues = false)
    private boolean autoplay;

    @ValueMapValue
    @Default(intValues = 5000)
    private int autoplaySpeed;

    @ValueMapValue
    @Default(booleanValues = true)
    private boolean loop;

    @ValueMapValue
    @Default(intValues = 1)
    private int slidesToShow;

    @ValueMapValue
    @Default(values = "slide")
    private String transition;

    @ChildResource(name = "slides")
    private List<Resource> slideResources;

    private List<CarouselSlide> slides;

    @PostConstruct
    protected void init() {
        slides = new ArrayList<>();

        if (slideResources != null) {
            int index = 0;
            for (Resource slideResource : slideResources) {
                CarouselSlide slide = new CarouselSlide();
                slide.setIndex(index++);
                slide.setImage(slideResource.getValueMap().get("image", String.class));
                slide.setImageAlt(slideResource.getValueMap().get("imageAlt", ""));
                slide.setTitle(slideResource.getValueMap().get("title", String.class));
                slide.setSubtitle(slideResource.getValueMap().get("subtitle", String.class));
                slide.setCtaText(slideResource.getValueMap().get("ctaText", String.class));
                slide.setCtaLink(slideResource.getValueMap().get("ctaLink", String.class));
                slides.add(slide);
            }
        }
    }

    public boolean isShowArrows() {
        return showArrows;
    }

    public boolean isShowDots() {
        return showDots;
    }

    public boolean isAutoplay() {
        return autoplay;
    }

    public int getAutoplaySpeed() {
        return autoplaySpeed;
    }

    public boolean isLoop() {
        return loop;
    }

    public int getSlidesToShow() {
        return slidesToShow;
    }

    public String getTransition() {
        return transition;
    }

    public String getTransitionClass() {
        return "fade".equals(transition) ? "venture-carousel--fade" : "venture-carousel--slide";
    }

    public List<CarouselSlide> getSlides() {
        return slides;
    }

    public int getSlideCount() {
        return slides.size();
    }

    public boolean hasSlides() {
        return slides != null && !slides.isEmpty();
    }

    public String getDataAttributes() {
        return String.format(
            "data-autoplay=\"%s\" data-speed=\"%d\" data-loop=\"%s\" data-slides=\"%d\"",
            autoplay, autoplaySpeed, loop, slidesToShow
        );
    }

    /**
     * Inner class representing a carousel slide.
     */
    public static class CarouselSlide {
        private int index;
        private String image;
        private String imageAlt;
        private String title;
        private String subtitle;
        private String ctaText;
        private String ctaLink;

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public String getImage() { return image; }
        public void setImage(String image) { this.image = image; }

        public String getImageAlt() { return imageAlt != null ? imageAlt : title; }
        public void setImageAlt(String imageAlt) { this.imageAlt = imageAlt; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

        public String getCtaText() { return ctaText; }
        public void setCtaText(String ctaText) { this.ctaText = ctaText; }

        public String getCtaLink() { return ctaLink; }
        public void setCtaLink(String ctaLink) { this.ctaLink = ctaLink; }

        public boolean hasCta() { return ctaText != null && ctaLink != null; }
        public boolean hasContent() { return title != null || subtitle != null; }
    }
}
