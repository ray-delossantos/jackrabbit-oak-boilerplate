/**
 * AEM Oak - Base JavaScript
 * Core functionality for the content management system
 */

(function() {
    'use strict';

    /**
     * DOM Ready handler
     */
    function ready(fn) {
        if (document.readyState !== 'loading') {
            fn();
        } else {
            document.addEventListener('DOMContentLoaded', fn);
        }
    }

    /**
     * Mobile Navigation Toggle
     */
    function initMobileNav() {
        const navToggle = document.querySelector('.nav-toggle');
        const mainNav = document.querySelector('.main-nav');

        if (navToggle && mainNav) {
            navToggle.addEventListener('click', function() {
                const expanded = this.getAttribute('aria-expanded') === 'true';
                this.setAttribute('aria-expanded', !expanded);
                mainNav.classList.toggle('active');
            });

            // Close menu when clicking outside
            document.addEventListener('click', function(e) {
                if (!navToggle.contains(e.target) && !mainNav.contains(e.target)) {
                    navToggle.setAttribute('aria-expanded', 'false');
                    mainNav.classList.remove('active');
                }
            });
        }
    }

    /**
     * Smooth Scroll for Anchor Links
     */
    function initSmoothScroll() {
        document.querySelectorAll('a[href^="#"]').forEach(function(anchor) {
            anchor.addEventListener('click', function(e) {
                const targetId = this.getAttribute('href');
                if (targetId === '#') return;

                const target = document.querySelector(targetId);
                if (target) {
                    e.preventDefault();
                    target.scrollIntoView({
                        behavior: 'smooth',
                        block: 'start'
                    });

                    // Update URL without jump
                    history.pushState(null, null, targetId);
                }
            });
        });
    }

    /**
     * Lazy Loading for Images
     */
    function initLazyLoading() {
        if ('IntersectionObserver' in window) {
            const imageObserver = new IntersectionObserver(function(entries, observer) {
                entries.forEach(function(entry) {
                    if (entry.isIntersecting) {
                        const image = entry.target;
                        if (image.dataset.src) {
                            image.src = image.dataset.src;
                            image.removeAttribute('data-src');
                        }
                        if (image.dataset.srcset) {
                            image.srcset = image.dataset.srcset;
                            image.removeAttribute('data-srcset');
                        }
                        image.classList.add('loaded');
                        observer.unobserve(image);
                    }
                });
            }, {
                rootMargin: '50px 0px',
                threshold: 0.01
            });

            document.querySelectorAll('img[data-src]').forEach(function(img) {
                imageObserver.observe(img);
            });
        } else {
            // Fallback for older browsers
            document.querySelectorAll('img[data-src]').forEach(function(img) {
                img.src = img.dataset.src;
                if (img.dataset.srcset) {
                    img.srcset = img.dataset.srcset;
                }
            });
        }
    }

    /**
     * Form Validation Helper
     */
    function initFormValidation() {
        document.querySelectorAll('form[data-validate]').forEach(function(form) {
            form.addEventListener('submit', function(e) {
                let valid = true;

                // Clear previous errors
                form.querySelectorAll('.form-error').forEach(function(error) {
                    error.remove();
                });

                // Validate required fields
                form.querySelectorAll('[required]').forEach(function(field) {
                    if (!field.value.trim()) {
                        valid = false;
                        showFieldError(field, 'This field is required');
                    }
                });

                // Validate email fields
                form.querySelectorAll('input[type="email"]').forEach(function(field) {
                    if (field.value && !isValidEmail(field.value)) {
                        valid = false;
                        showFieldError(field, 'Please enter a valid email address');
                    }
                });

                if (!valid) {
                    e.preventDefault();
                }
            });
        });
    }

    function showFieldError(field, message) {
        const error = document.createElement('div');
        error.className = 'form-error';
        error.textContent = message;
        error.style.color = '#dc3545';
        error.style.fontSize = '0.875rem';
        error.style.marginTop = '0.25rem';
        field.parentNode.appendChild(error);
        field.classList.add('is-invalid');
    }

    function isValidEmail(email) {
        return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
    }

    /**
     * Initialize external links
     */
    function initExternalLinks() {
        document.querySelectorAll('a[target="_blank"]').forEach(function(link) {
            // Add security attributes
            if (!link.getAttribute('rel')) {
                link.setAttribute('rel', 'noopener noreferrer');
            }

            // Add visual indicator for screen readers
            if (!link.querySelector('.external-indicator')) {
                const indicator = document.createElement('span');
                indicator.className = 'visually-hidden';
                indicator.textContent = ' (opens in new window)';
                link.appendChild(indicator);
            }
        });
    }

    /**
     * Back to Top Button
     */
    function initBackToTop() {
        const button = document.createElement('button');
        button.className = 'back-to-top';
        button.innerHTML = '&uarr;';
        button.setAttribute('aria-label', 'Back to top');
        button.style.cssText = `
            position: fixed;
            bottom: 20px;
            right: 20px;
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background: #0066cc;
            color: #fff;
            border: none;
            cursor: pointer;
            opacity: 0;
            visibility: hidden;
            transition: opacity 0.3s, visibility 0.3s;
            z-index: 1000;
        `;

        document.body.appendChild(button);

        window.addEventListener('scroll', function() {
            if (window.scrollY > 300) {
                button.style.opacity = '1';
                button.style.visibility = 'visible';
            } else {
                button.style.opacity = '0';
                button.style.visibility = 'hidden';
            }
        });

        button.addEventListener('click', function() {
            window.scrollTo({
                top: 0,
                behavior: 'smooth'
            });
        });
    }

    /**
     * Initialize all functionality
     */
    ready(function() {
        initMobileNav();
        initSmoothScroll();
        initLazyLoading();
        initFormValidation();
        initExternalLinks();
        initBackToTop();

        // Log initialization
        console.log('AEM Oak - Base JavaScript initialized');
    });

    // Expose utility functions globally
    window.AEMOak = window.AEMOak || {};
    window.AEMOak.ready = ready;
    window.AEMOak.isValidEmail = isValidEmail;

})();
