window.scrollHelpers = {
    enableTimelineScroll: function() {
        // Agregar clase al body para aplicar estilos de scroll unificado
        document.body.classList.add('timeline-page');
        // Desactivar scroll en html y body
        document.documentElement.style.overflow = 'hidden';
        document.documentElement.style.height = '100vh';
        document.body.style.overflow = 'hidden';
        document.body.style.height = '100vh';
    },
    disableTimelineScroll: function() {
        // Remover clase del body al salir de la página
        document.body.classList.remove('timeline-page');
        // Restaurar estilos por defecto
        document.documentElement.style.overflow = '';
        document.documentElement.style.height = '';
        document.body.style.overflow = '';
        document.body.style.height = '';
    },
    getScrollContainer: function() {
        // En la página de Timeline, usar el contenedor del timeline como único scroll
        const timelineContainer = document.getElementById('timeline-scroll-container');
        if (timelineContainer) {
            return timelineContainer;
        }
        
        // Para otras páginas, mantener el comportamiento original
        const mainContent = document.querySelector('.mud-main-content');
        if (mainContent && mainContent.scrollHeight > mainContent.clientHeight) {
            const style = window.getComputedStyle(mainContent);
            if (style.overflowY !== 'hidden' && style.display !== 'none' && style.overflowY !== 'visible') {
                return mainContent;
            }
        }
        
        const layout = document.querySelector('.mud-layout');
        if (layout && layout.scrollHeight > layout.clientHeight) {
            const style = window.getComputedStyle(layout);
            if (style.overflowY !== 'hidden' && style.display !== 'none' && style.overflowY !== 'visible') {
                return layout;
            }
        }

        return window;
    },
    scrollToElement: function (id) {
        const element = document.getElementById(id);
        const scrollContainer = this.getScrollContainer();
        if (element && scrollContainer) {
            // Para el contenedor del timeline, calculamos la posición relativa
            let elementPosition;
            if (scrollContainer === window) {
                // Para window, usamos getBoundingClientRect + scrollY
                const rect = element.getBoundingClientRect();
                const appBarHeight = document.querySelector('.mud-appbar')?.offsetHeight || 0;
                elementPosition = rect.top + window.scrollY - appBarHeight - 10;
            } else {
                // Para contenedores internos (como timeline-container), usamos offsetTop
                // El offsetTop es relativo al padre posicionado
                elementPosition = element.offsetTop - 10;
            }
            
            const offsetPosition = Math.max(0, elementPosition);

            if (scrollContainer === window) {
                window.scrollTo({
                    top: offsetPosition,
                    behavior: 'smooth'
                });
            } else {
                scrollContainer.scrollTo({
                    top: offsetPosition,
                    behavior: 'smooth'
                });
            }
        }
    },
    onWindowScroll: function (dotnetHelper, groupIds) {
        // Remover listener anterior si existe
        if (window._timelineScrollHandler) {
            const oldContainer = window._timelineScrollContainer || window;
            if (oldContainer !== window) {
                oldContainer.removeEventListener('scroll', window._timelineScrollHandler, { passive: true });
            } else {
                window.removeEventListener('scroll', window._timelineScrollHandler, { passive: true });
            }
        }

        const scrollContainer = this.getScrollContainer();
        window._timelineScrollContainer = scrollContainer;

        let scrollDebounceTimer = null;

        window._timelineGroupIds = groupIds;
        const handleScroll = () => {
            let activeId = "";

            for (const id of window._timelineGroupIds) {
                const element = document.getElementById(id);
                if (element) {
                    const rect = element.getBoundingClientRect();
                    if (scrollContainer === window) {
                        if (rect.top <= 150) activeId = id;
                    } else {
                        const containerRect = scrollContainer.getBoundingClientRect();
                        if (rect.top - containerRect.top <= 150) activeId = id;
                    }
                }
            }

            // Actualizar thumb y year markers del scrubber directamente en JS (sin round-trip a Blazor)
            if (window.scrubberHelpers) {
                window.scrubberHelpers.updateThumb(scrollContainer);
                window.scrubberHelpers.updateActiveMarker(activeId);
            }

            // Notificar a Blazor solo para mantener _activeGroup sincronizado (sin StateHasChanged)
            clearTimeout(scrollDebounceTimer);
            scrollDebounceTimer = setTimeout(() => {
                dotnetHelper.invokeMethodAsync('OnScrollUpdated', activeId)
                    .catch(err => console.error('Error updating scroll:', err));
            }, 200);
        };

        if (scrollContainer === window) {
            window.addEventListener('scroll', handleScroll, { passive: true });
        } else {
            scrollContainer.addEventListener('scroll', handleScroll, { passive: true });
        }
        window._timelineScrollHandler = handleScroll;

        // Estado inicial
        handleScroll();
    },
    updateScrollProgress: function () {},
    setupTimelineHover: function () {},
    setupDraggableMarker: function () {},
    getTimelineHoverPosition: function () {},
    updateGroupIds: function (groupIds) {
        if (window._timelineGroupIds) {
            window._timelineGroupIds = groupIds;
        }
    }
};

window.setupLoadMoreObserver = function (sentinelId, dotnetHelper, methodName) {
    if (window._loadMoreObserver) {
        window._loadMoreObserver.disconnect();
        window._loadMoreObserver = null;
    }
    var sentinel = document.getElementById(sentinelId);
    if (!sentinel) return;

    var scrollContainer = document.getElementById('timeline-scroll-container');
    window._loadMoreObserver = new IntersectionObserver(function (entries) {
        if (entries[0].isIntersecting) {
            dotnetHelper.invokeMethodAsync(methodName);
        }
    }, {
        root: scrollContainer || null,
        threshold: 0.1
    });
    window._loadMoreObserver.observe(sentinel);
};

window.disconnectLoadMoreObserver = function () {
    if (window._loadMoreObserver) {
        window._loadMoreObserver.disconnect();
        window._loadMoreObserver = null;
    }
};

window.scrubberHelpers = {
    _sc: null,
    _track: null,
    _thumb: null,
    _label: null,
    _overlay: null,
    _groupMap: [],
    _isDragging: false,
    _isMobile: false,
    _hideTimer: null,

    _showScrubber: function () {
        if (!this._isMobile || !this._overlay) return;
        this._overlay.classList.add('scrubber-visible');
        clearTimeout(this._hideTimer);
        this._hideTimer = setTimeout(() => {
            if (!this._isDragging) this._overlay.classList.remove('scrubber-visible');
        }, 2000);
    },

    init: function (groupMap) {
        this._groupMap = groupMap || [];
        this._sc = document.getElementById('timeline-scroll-container');
        this._track = document.getElementById('scrubber-track');
        this._thumb = document.getElementById('scrubber-thumb');
        this._label = document.getElementById('scrubber-thumb-label');
        this._overlay = document.querySelector('.scrubber-overlay');

        if (!this._sc || !this._track || !this._thumb) return;

        this._isMobile = window.matchMedia('(max-width: 959.98px)').matches;

        // Mobile auto-hide: show scrubber on scroll, hide after 2s idle
        if (this._isMobile && this._overlay) {
            var self = this;
            this._sc.addEventListener('scroll', function () { self._showScrubber(); }, { passive: true });
            this._track.addEventListener('touchstart', function () {
                self._overlay.classList.add('scrubber-visible');
                clearTimeout(self._hideTimer);
            }, { passive: true });
        }

        // Track click — handles both year marker clicks and bare track clicks.
        // Using event delegation so markers added via pagination are covered automatically.
        var self = this;
        this._track.addEventListener('click', function (e) {
            if (self._isDragging) return;
            // Year marker click — jump to that group
            var marker = e.target.closest('.scrubber-year-marker');
            if (marker) {
                e.stopPropagation();
                var gid = marker.getAttribute('data-group-id');
                if (gid) window.scrollHelpers.scrollToElement(gid);
                return;
            }
            // Bare track click — jump scroll to that vertical position
            if (e.target === self._thumb || self._thumb.contains(e.target)) return;
            var rect = self._track.getBoundingClientRect();
            var pct = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));
            self._scrollToPercent(pct);
        });

        // Thumb drag — mouse
        this._thumb.addEventListener('mousedown', function (e) {
            e.preventDefault();
            self._startDrag(e.clientY);
        });

        // Thumb drag — touch
        this._thumb.addEventListener('touchstart', function (e) {
            e.preventDefault();
            self._startDrag(e.touches[0].clientY);
        }, { passive: false });

        // Forward wheel events to the scroll container (scrubber-track is not a DOM
        // ancestor of timeline-scroll-container, so wheel events won't bubble there)
        this._track.addEventListener('wheel', function (e) {
            if (self._sc) {
                self._sc.scrollTop += e.deltaY;
            }
        }, { passive: true });

        // Set initial position
        this.updateThumb(this._sc);
    },

    _startDrag: function (startClientY) {
        var self = this;
        this._isDragging = true;
        this._thumb.classList.add('dragging');

        var onMove = function (clientY) {
            var rect = self._track.getBoundingClientRect();
            var pct = Math.max(0, Math.min(1, (clientY - rect.top) / rect.height));
            self._setThumbPercent(pct);
            self._scrollToPercent(pct);
        };

        var onMouseMove = function (e) { onMove(e.clientY); };
        var onTouchMove = function (e) { e.preventDefault(); onMove(e.touches[0].clientY); };

        var stop = function () {
            self._isDragging = false;
            self._thumb.classList.remove('dragging');
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', stop);
            document.removeEventListener('touchmove', onTouchMove);
            document.removeEventListener('touchend', stop);
            // Mobile: restart hide timer after drag ends
            if (self._isMobile && self._overlay) {
                clearTimeout(self._hideTimer);
                self._hideTimer = setTimeout(() => {
                    self._overlay.classList.remove('scrubber-visible');
                }, 2000);
            }
        };

        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', stop);
        document.addEventListener('touchmove', onTouchMove, { passive: false });
        document.addEventListener('touchend', stop);
    },

    _scrollToPercent: function (pct) {
        if (!this._sc) return;
        var scrollable = this._sc.scrollHeight - this._sc.clientHeight;
        this._sc.scrollTop = pct * scrollable;
    },

    _setThumbPercent: function (pct) {
        if (!this._thumb || !this._track) return;
        var trackH = this._track.clientHeight;
        var thumbH = this._thumb.clientHeight;
        var top = Math.max(0, Math.min(trackH - thumbH, pct * trackH - thumbH / 2));
        this._thumb.style.transform = 'translateY(' + top + 'px)';

        // Update label text directly (no Blazor round-trip)
        if (this._label && this._groupMap.length > 0) {
            var label = this._groupMap[0].label;
            for (var i = 0; i < this._groupMap.length; i++) {
                if (this._groupMap[i].position <= pct + 0.001) {
                    label = this._groupMap[i].label;
                } else {
                    break;
                }
            }
            this._label.textContent = label;
        }
    },

    updateThumb: function (scrollContainer) {
        if (this._isDragging) return;
        var sc = (scrollContainer === window) ? document.documentElement : scrollContainer;
        if (!sc || !this._thumb || !this._track) return;
        var scrollable = sc.scrollHeight - sc.clientHeight;
        if (scrollable <= 0) return;
        var pct = sc.scrollTop / scrollable;
        this._setThumbPercent(pct);
    },

    updateGroupMap: function (groupData) {
        // Refresh group positions after pagination loads new data.
        // Event delegation on the track already covers new year marker clicks.
        this._groupMap = groupData || [];
    },

    updateActiveMarker: function (activeGroupId) {
        // Extraer el año del group id: "group-2024-01-15" → "2024"
        var activeYear = null;
        if (activeGroupId) {
            var match = activeGroupId.match(/^group-(\d{4})-/);
            if (match) activeYear = match[1];
        }
        document.querySelectorAll('.scrubber-year-marker').forEach(function (marker) {
            var label = marker.querySelector('.scrubber-year-label');
            var year = label ? label.textContent.trim() : null;
            if (year && year === activeYear) {
                marker.classList.add('active');
            } else {
                marker.classList.remove('active');
            }
        });
    },

    cleanup: function () {
        clearTimeout(this._hideTimer);
        this._sc = null;
        this._track = null;
        this._thumb = null;
        this._label = null;
        this._overlay = null;
        this._groupMap = [];
        this._isMobile = false;
        this._hideTimer = null;
    }
};

window.videoHelpers = {
    play: function (videoElement) {
        if (videoElement) {
            videoElement.play().catch(error => {
                // Ignore autoplay errors if any
                console.log("Autoplay prevented or video error: ", error);
            });
        }
    },
    pause: function (videoElement) {
        if (videoElement) {
            videoElement.pause();
            // Reset to beginning to show the first frame again
            videoElement.currentTime = 0;
        }
    }
};

window.masonryHelpers = {
    initializeMasonry: function () {
        this.justifyAllGroups();

        if (!window._masonryResizeHandler) {
            let resizeTimer = null;
            window._masonryResizeHandler = () => {
                clearTimeout(resizeTimer);
                resizeTimer = setTimeout(() => {
                    window.masonryHelpers.justifyAllGroups();
                }, 100);
            };
            window.addEventListener('resize', window._masonryResizeHandler, { passive: true });
        }
    },

    justifyAllGroups: function () {
        const grid = document.querySelector('.timeline-flat-grid');
        if (!grid) return;
        const containerWidth = grid.offsetWidth;

        // Agrupar items por data-day preservando el orden del DOM
        const dayMap = new Map();
        grid.querySelectorAll('.masonry-item').forEach(item => {
            const day = item.getAttribute('data-day');
            if (!dayMap.has(day)) dayMap.set(day, []);
            dayMap.get(day).push(item);
        });

        for (const items of dayMap.values()) {
            this.justifyDayItems(items, containerWidth);
        }
    },

    justifyDayItems: function (items, containerWidth) {
        if (items.length === 0) return;

        const gap = 2;
        const itemHeight = items[0].offsetHeight || parseInt(getComputedStyle(items[0]).height) || 180;

        const aspectRatios = items.map(item => {
            let ar = parseFloat(item.getAttribute('data-aspect-ratio')) || 1.0;
            const w = parseInt(item.getAttribute('data-width')) || 0;
            const h = parseInt(item.getAttribute('data-height')) || 0;
            if (w > 0 && h > 0) ar = w / h;
            return ar;
        });

        let i = 0;
        while (i < items.length) {
            const line = [];
            const lineRatios = [];
            let lineWidth = 0;

            while (i < items.length) {
                const nextRatio = aspectRatios[i];
                const nextItemWidth = itemHeight * nextRatio;
                const newLineWidth = lineWidth + nextItemWidth + (line.length > 0 ? gap : 0);

                if (newLineWidth > containerWidth && line.length > 0) break;

                line.push(items[i]);
                lineRatios.push(nextRatio);
                lineWidth = newLineWidth;
                i++;
            }

            if (line.length > 0) {
                this.justifyLine(line, lineRatios, containerWidth, itemHeight, gap);
            }
        }
    },

    justifyLine: function (items, aspectRatios, containerWidth, itemHeight, gap) {
        if (items.length === 0) return;

        const availableWidth = containerWidth - (items.length - 1) * gap;

        // Un único item siempre ocupa todo el ancho disponible
        if (items.length === 1) {
            items[0].style.width = availableWidth + 'px';
            return;
        }

        const totalAspectRatio = aspectRatios.reduce((sum, ar) => sum + ar, 0);
        const scaleFactor = availableWidth / (totalAspectRatio * itemHeight);

        items.forEach((item, index) => {
            item.style.width = (itemHeight * aspectRatios[index] * scaleFactor) + 'px';
        });
    },

    updateMasonryForGroup: function (groupSelector) {
        // groupSelector es "#group-2026-03-28" → extraer la clave "2026-03-28"
        const dayKey = groupSelector.replace(/^#?group-/, '');
        const grid = document.querySelector('.timeline-flat-grid');
        if (!grid) return;
        const containerWidth = grid.offsetWidth;
        const items = Array.from(grid.querySelectorAll(`.masonry-item[data-day="${dayKey}"]`));
        if (items.length > 0) this.justifyDayItems(items, containerWidth);
    },

    updateMasonryForGroups: function (groupSelectors) {
        const grid = document.querySelector('.timeline-flat-grid');
        const sc = document.getElementById('timeline-scroll-container');
        if (!grid) return;

        // Preserve scroll position — masonry width changes can cause layout reflow
        // that shifts the visible area, especially for boundary day groups.
        const scrollTopBefore = sc ? sc.scrollTop : 0;

        const containerWidth = grid.offsetWidth;
        for (const groupSelector of groupSelectors) {
            const dayKey = groupSelector.replace(/^#?group-/, '');
            const items = Array.from(grid.querySelectorAll(`.masonry-item[data-day="${dayKey}"]`));
            if (items.length > 0) this.justifyDayItems(items, containerWidth);
        }

        // Restore scroll position after all groups are processed
        if (sc && sc.scrollTop !== scrollTopBefore) {
            sc.scrollTop = scrollTopBefore;
        }
    }
};

window.focusElement = function (element) {
    if (element) element.focus();
};

window.timelineHelpers = {
    getVisibleSectionIds: function (sectionIds) {
        var sc = document.getElementById('timeline-scroll-container');
        if (!sc) return [];
        var buffer = 1200;
        var viewTop = sc.scrollTop - buffer;
        var viewBottom = sc.scrollTop + sc.clientHeight + buffer;
        var visible = [];
        for (var i = 0; i < sectionIds.length; i++) {
            var el = document.getElementById(sectionIds[i]);
            if (!el) continue;
            var elTop = el.offsetTop;
            var elBottom = elTop + el.offsetHeight;
            if (elBottom >= viewTop && elTop <= viewBottom) {
                visible.push(sectionIds[i]);
            }
        }
        return visible;
    }
};

window.timelineVirtualScroll = {
    _dotNetRef: null,
    _lastScrollTop: 0,
    _lastScrollTime: 0,
    _idleTimer: null,
    VELOCITY_THRESHOLD: 600, // px/s — above this = fast scroll, show skeleton

    init: function (dotNetRef) {
        this._dotNetRef = dotNetRef;
        var sc = document.getElementById('timeline-scroll-container');
        if (!sc) return;
        this._lastScrollTop = sc.scrollTop;
        this._lastScrollTime = performance.now();
        var self = this;
        sc.addEventListener('scroll', function () { self._onScroll(); }, { passive: true });
    },

    _onScroll: function () {
        var sc = document.getElementById('timeline-scroll-container');
        if (!sc || !this._dotNetRef) return;
        var now = performance.now();
        var dt = now - this._lastScrollTime;
        var dy = Math.abs(sc.scrollTop - this._lastScrollTop);
        var velocity = dt > 0 ? (dy / dt) * 1000 : 0;
        this._lastScrollTop = sc.scrollTop;
        this._lastScrollTime = now;
        clearTimeout(this._idleTimer);
        var self = this;
        if (velocity < this.VELOCITY_THRESHOLD) {
            this._dotNetRef.invokeMethodAsync('OnVirtualScrollIdle', sc.scrollTop)
                .catch(function () {});
        } else {
            this._idleTimer = setTimeout(function () {
                var sc2 = document.getElementById('timeline-scroll-container');
                if (sc2 && self._dotNetRef) {
                    self._dotNetRef.invokeMethodAsync('OnVirtualScrollIdle', sc2.scrollTop)
                        .catch(function () {});
                }
            }, 300);
        }
    },

    cleanup: function () {
        clearTimeout(this._idleTimer);
        this._dotNetRef = null;
    }
};

window.lazyImageHelpers = {
    _observer: null,

    _getOrCreateObserver: function () {
        if (this._observer) return this._observer;
        var self = this;
        this._observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting) return;
                var img = entry.target;
                var src = img.getAttribute('data-src');
                if (src) {
                    img.src = src;
                    img.removeAttribute('data-src');
                }
                self._observer.unobserve(img);
            });
        }, {
            rootMargin: '300px 0px', // pre-cargar 300px antes de entrar en vista
            threshold: 0
        });
        return this._observer;
    },

    observe: function (imgElement) {
        if (!imgElement) return;
        // Si data-src ya fue eliminado (imagen cargada), no re-observar
        if (!imgElement.getAttribute || !imgElement.getAttribute('data-src')) return;
        this._getOrCreateObserver().observe(imgElement);
    },

    unobserve: function (imgElement) {
        if (!imgElement || !this._observer) return;
        this._observer.unobserve(imgElement);
    }
};