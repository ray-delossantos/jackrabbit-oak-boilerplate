/**
 * AEM Oak - Component JavaScript
 * Interactive functionality for individual components
 */

(function() {
    'use strict';

    /**
     * Image Component - Lightbox Support
     */
    function initImageLightbox() {
        const images = document.querySelectorAll('.image-component img[data-lightbox]');

        if (images.length === 0) return;

        // Create lightbox container
        const lightbox = document.createElement('div');
        lightbox.className = 'lightbox';
        lightbox.innerHTML = `
            <div class="lightbox-overlay"></div>
            <div class="lightbox-content">
                <button class="lightbox-close" aria-label="Close">&times;</button>
                <img class="lightbox-image" src="" alt="">
                <div class="lightbox-caption"></div>
            </div>
        `;
        lightbox.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            display: none;
            z-index: 10000;
        `;
        document.body.appendChild(lightbox);

        const overlay = lightbox.querySelector('.lightbox-overlay');
        overlay.style.cssText = `
            position: absolute;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.9);
        `;

        const content = lightbox.querySelector('.lightbox-content');
        content.style.cssText = `
            position: absolute;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            max-width: 90%;
            max-height: 90%;
            text-align: center;
        `;

        const closeBtn = lightbox.querySelector('.lightbox-close');
        closeBtn.style.cssText = `
            position: absolute;
            top: -40px;
            right: 0;
            background: none;
            border: none;
            color: #fff;
            font-size: 2rem;
            cursor: pointer;
        `;

        const lightboxImg = lightbox.querySelector('.lightbox-image');
        lightboxImg.style.cssText = `
            max-width: 100%;
            max-height: 80vh;
            object-fit: contain;
        `;

        const caption = lightbox.querySelector('.lightbox-caption');
        caption.style.cssText = `
            color: #fff;
            margin-top: 1rem;
            font-size: 0.875rem;
        `;

        // Open lightbox
        images.forEach(function(img) {
            img.style.cursor = 'pointer';
            img.addEventListener('click', function() {
                lightboxImg.src = this.src;
                lightboxImg.alt = this.alt;
                caption.textContent = this.getAttribute('data-caption') || this.alt || '';
                lightbox.style.display = 'block';
                document.body.style.overflow = 'hidden';
            });
        });

        // Close lightbox
        function closeLightbox() {
            lightbox.style.display = 'none';
            document.body.style.overflow = '';
        }

        closeBtn.addEventListener('click', closeLightbox);
        overlay.addEventListener('click', closeLightbox);
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape' && lightbox.style.display === 'block') {
                closeLightbox();
            }
        });
    }

    /**
     * Text Component - Copy to Clipboard for Code Blocks
     */
    function initCodeCopy() {
        const codeBlocks = document.querySelectorAll('.text-richtext pre');

        codeBlocks.forEach(function(block) {
            const copyBtn = document.createElement('button');
            copyBtn.className = 'code-copy-btn';
            copyBtn.textContent = 'Copy';
            copyBtn.style.cssText = `
                position: absolute;
                top: 0.5rem;
                right: 0.5rem;
                padding: 0.25rem 0.5rem;
                font-size: 0.75rem;
                background: rgba(255, 255, 255, 0.1);
                color: #fff;
                border: none;
                border-radius: 4px;
                cursor: pointer;
            `;

            block.style.position = 'relative';
            block.appendChild(copyBtn);

            copyBtn.addEventListener('click', function() {
                const code = block.querySelector('code');
                const text = code ? code.textContent : block.textContent;

                navigator.clipboard.writeText(text).then(function() {
                    copyBtn.textContent = 'Copied!';
                    setTimeout(function() {
                        copyBtn.textContent = 'Copy';
                    }, 2000);
                }).catch(function(err) {
                    console.error('Copy failed:', err);
                });
            });
        });
    }

    /**
     * Accordion Component
     */
    function initAccordion() {
        const accordions = document.querySelectorAll('.accordion');

        accordions.forEach(function(accordion) {
            const items = accordion.querySelectorAll('.accordion-item');

            items.forEach(function(item) {
                const header = item.querySelector('.accordion-header');
                const content = item.querySelector('.accordion-content');

                if (!header || !content) return;

                header.setAttribute('role', 'button');
                header.setAttribute('aria-expanded', 'false');
                header.setAttribute('tabindex', '0');
                content.style.display = 'none';

                function toggle() {
                    const isExpanded = header.getAttribute('aria-expanded') === 'true';
                    header.setAttribute('aria-expanded', !isExpanded);
                    content.style.display = isExpanded ? 'none' : 'block';
                }

                header.addEventListener('click', toggle);
                header.addEventListener('keydown', function(e) {
                    if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        toggle();
                    }
                });
            });
        });
    }

    /**
     * Tab Component
     */
    function initTabs() {
        const tabContainers = document.querySelectorAll('.tabs');

        tabContainers.forEach(function(container) {
            const tabs = container.querySelectorAll('.tab-button');
            const panels = container.querySelectorAll('.tab-panel');

            tabs.forEach(function(tab, index) {
                tab.setAttribute('role', 'tab');
                tab.setAttribute('aria-selected', index === 0 ? 'true' : 'false');
                tab.setAttribute('tabindex', index === 0 ? '0' : '-1');

                if (panels[index]) {
                    panels[index].setAttribute('role', 'tabpanel');
                    panels[index].style.display = index === 0 ? 'block' : 'none';
                }

                tab.addEventListener('click', function() {
                    // Update tabs
                    tabs.forEach(function(t) {
                        t.setAttribute('aria-selected', 'false');
                        t.setAttribute('tabindex', '-1');
                        t.classList.remove('active');
                    });
                    this.setAttribute('aria-selected', 'true');
                    this.setAttribute('tabindex', '0');
                    this.classList.add('active');

                    // Update panels
                    panels.forEach(function(p) {
                        p.style.display = 'none';
                    });
                    if (panels[index]) {
                        panels[index].style.display = 'block';
                    }
                });

                // Keyboard navigation
                tab.addEventListener('keydown', function(e) {
                    let targetIndex = index;

                    if (e.key === 'ArrowRight') {
                        targetIndex = (index + 1) % tabs.length;
                    } else if (e.key === 'ArrowLeft') {
                        targetIndex = (index - 1 + tabs.length) % tabs.length;
                    } else if (e.key === 'Home') {
                        targetIndex = 0;
                    } else if (e.key === 'End') {
                        targetIndex = tabs.length - 1;
                    } else {
                        return;
                    }

                    e.preventDefault();
                    tabs[targetIndex].click();
                    tabs[targetIndex].focus();
                });
            });
        });
    }

    /**
     * Carousel Component
     */
    function initCarousel() {
        const carousels = document.querySelectorAll('.carousel');

        carousels.forEach(function(carousel) {
            const slides = carousel.querySelectorAll('.carousel-slide');
            const prevBtn = carousel.querySelector('.carousel-prev');
            const nextBtn = carousel.querySelector('.carousel-next');
            const dotsContainer = carousel.querySelector('.carousel-dots');

            if (slides.length === 0) return;

            let currentIndex = 0;

            function showSlide(index) {
                slides.forEach(function(slide, i) {
                    slide.style.display = i === index ? 'block' : 'none';
                });

                if (dotsContainer) {
                    const dots = dotsContainer.querySelectorAll('.carousel-dot');
                    dots.forEach(function(dot, i) {
                        dot.classList.toggle('active', i === index);
                    });
                }

                currentIndex = index;
            }

            function nextSlide() {
                showSlide((currentIndex + 1) % slides.length);
            }

            function prevSlide() {
                showSlide((currentIndex - 1 + slides.length) % slides.length);
            }

            if (prevBtn) prevBtn.addEventListener('click', prevSlide);
            if (nextBtn) nextBtn.addEventListener('click', nextSlide);

            // Create dots
            if (dotsContainer && slides.length > 1) {
                slides.forEach(function(_, i) {
                    const dot = document.createElement('button');
                    dot.className = 'carousel-dot' + (i === 0 ? ' active' : '');
                    dot.setAttribute('aria-label', 'Go to slide ' + (i + 1));
                    dot.addEventListener('click', function() {
                        showSlide(i);
                    });
                    dotsContainer.appendChild(dot);
                });
            }

            // Initialize
            showSlide(0);

            // Auto-advance (optional)
            if (carousel.dataset.autoplay) {
                const interval = parseInt(carousel.dataset.autoplay) || 5000;
                setInterval(nextSlide, interval);
            }
        });
    }

    /**
     * Initialize all component functionality
     */
    function init() {
        initImageLightbox();
        initCodeCopy();
        initAccordion();
        initTabs();
        initCarousel();

        console.log('AEM Oak - Components JavaScript initialized');
    }

    // Initialize when DOM is ready
    if (window.AEMOak && window.AEMOak.ready) {
        window.AEMOak.ready(init);
    } else if (document.readyState !== 'loading') {
        init();
    } else {
        document.addEventListener('DOMContentLoaded', init);
    }

    // Expose component initializers globally
    window.AEMOak = window.AEMOak || {};
    window.AEMOak.components = {
        initImageLightbox: initImageLightbox,
        initCodeCopy: initCodeCopy,
        initAccordion: initAccordion,
        initTabs: initTabs,
        initCarousel: initCarousel
    };

})();
