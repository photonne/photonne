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
        // Remove previous listener
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
        let rafPending = false;

        window._timelineGroupIds = groupIds;

        const handleScroll = () => {
            // Throttle to one update per animation frame (P0)
            if (rafPending) return;
            rafPending = true;
            requestAnimationFrame(() => {
                rafPending = false;

                // Update scrubber thumb position from scroll % (no DOM queries needed)
                if (window.scrubberHelpers) {
                    window.scrubberHelpers.updateThumb(scrollContainer);
                }

                // Debounce Blazor notification (only for _activeGroup sync, no StateHasChanged)
                clearTimeout(scrollDebounceTimer);
                scrollDebounceTimer = setTimeout(() => {
                    // Determine active group using scrubber's _groupMap (binary search, no DOM queries)
                    var activeId = '';
                    if (window.scrubberHelpers && window.scrubberHelpers._groupMap.length > 0) {
                        var sc = scrollContainer === window ? document.documentElement : scrollContainer;
                        var scrollable = sc.scrollHeight - sc.clientHeight;
                        var pct = scrollable > 0 ? sc.scrollTop / scrollable : 0;
                        for (var i = 0; i < window.scrubberHelpers._groupMap.length; i++) {
                            if (window.scrubberHelpers._groupMap[i].position <= pct + 0.001) {
                                activeId = window.scrubberHelpers._groupMap[i].id;
                            } else {
                                break;
                            }
                        }
                    }
                    if (window.scrubberHelpers) {
                        window.scrubberHelpers.updateActiveMarker(activeId);
                    }
                    dotnetHelper.invokeMethodAsync('OnScrollUpdated', activeId)
                        .catch(function () {});
                }, 200);
            });
        };

        if (scrollContainer === window) {
            window.addEventListener('scroll', handleScroll, { passive: true });
        } else {
            scrollContainer.addEventListener('scroll', handleScroll, { passive: true });
        }
        window._timelineScrollHandler = handleScroll;

        // Initial state
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

// Multi-instance load-more observer registry, keyed by sentinel ID.
// Backwards-compatible with the legacy single-observer API.
window._loadMoreObservers = window._loadMoreObservers || {};

/**
 * Sets up an IntersectionObserver that fires `methodName` on the given .NET ref
 * whenever `sentinelId` enters the viewport.
 *
 * @param {string} sentinelId  ID of the sentinel element to observe.
 * @param {object} dotnetHelper  DotNetObjectReference.
 * @param {string} methodName  JSInvokable method name to call.
 * @param {string} [rootId]  Optional ID of the scroll container. Defaults to
 *   `timeline-scroll-container` if present, otherwise the viewport.
 * @param {string} [rootMargin]  Optional rootMargin (default '200px').
 */
window.setupLoadMoreObserver = function (sentinelId, dotnetHelper, methodName, rootId, rootMargin) {
    // Disconnect any previous observer for the same sentinel
    var existing = window._loadMoreObservers[sentinelId];
    if (existing) {
        try { existing.disconnect(); } catch (e) { }
        delete window._loadMoreObservers[sentinelId];
    }

    var sentinel = document.getElementById(sentinelId);
    if (!sentinel) return;

    var rootEl = null;
    if (rootId) {
        rootEl = document.getElementById(rootId);
    } else {
        // Backward compat: legacy callers expect timeline-scroll-container as default root
        rootEl = document.getElementById('timeline-scroll-container');
    }

    var observer = new IntersectionObserver(function (entries) {
        if (entries[0].isIntersecting) {
            dotnetHelper.invokeMethodAsync(methodName);
        }
    }, {
        root: rootEl || null,
        rootMargin: rootMargin || '200px',
        threshold: 0
    });
    observer.observe(sentinel);
    window._loadMoreObservers[sentinelId] = observer;

    // Keep legacy single-observer reference in sync so disconnectLoadMoreObserver()
    // (no-args) keeps working for the original Favorites caller.
    window._loadMoreObserver = observer;
};

/**
 * Disconnects a previously-registered load-more observer.
 * If `sentinelId` is provided, only that observer is removed; otherwise all are removed.
 */
window.disconnectLoadMoreObserver = function (sentinelId) {
    if (sentinelId) {
        var obs = window._loadMoreObservers[sentinelId];
        if (obs) {
            try { obs.disconnect(); } catch (e) { }
            delete window._loadMoreObservers[sentinelId];
        }
        return;
    }
    // No id: disconnect everything (legacy behaviour)
    Object.keys(window._loadMoreObservers).forEach(function (key) {
        try { window._loadMoreObservers[key].disconnect(); } catch (e) { }
    });
    window._loadMoreObservers = {};
    window._loadMoreObserver = null;
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
                if (!gid) return;
                // If element exists in DOM, scroll directly; otherwise ask .NET to render it first
                var el = document.getElementById(gid);
                if (el) {
                    window.scrollHelpers.scrollToElement(gid);
                } else {
                    var dateStr = gid.replace(/^group-/, '');
                    window.timelineVirtualScroll.navigateToGroup(dateStr);
                }
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
        // Find the group closest to this percentage and navigate to it
        if (this._groupMap.length > 0) {
            var targetGroup = this._groupMap[0];
            for (var i = 0; i < this._groupMap.length; i++) {
                if (this._groupMap[i].position <= pct + 0.001) {
                    targetGroup = this._groupMap[i];
                } else {
                    break;
                }
            }
            var el = document.getElementById(targetGroup.id);
            if (el) {
                // Group is in the DOM — scroll directly to it
                el.scrollIntoView({ behavior: 'instant', block: 'start' });
                return;
            }
            // Group not rendered — debounced navigation via .NET
            var dateStr = targetGroup.id.replace(/^group-/, '');
            window.timelineVirtualScroll.navigateToGroup(dateStr);
            return;
        }
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

        // Agrupar items por data-day preservando el orden del DOM
        const dayMap = new Map();
        grid.querySelectorAll('.masonry-item').forEach(item => {
            const day = item.getAttribute('data-day');
            if (!dayMap.has(day)) dayMap.set(day, []);
            dayMap.get(day).push(item);
        });

        // Leer todo lo que necesitamos en un solo batch (1 reflow),
        // luego escribir en otro batch (1 reflow) para evitar layout thrash.
        const containerWidth = grid.offsetWidth; // único read de layout antes de escribir
        const dayHeights = new Map();
        for (const [day, items] of dayMap) {
            dayHeights.set(day, items[0].offsetHeight || parseInt(getComputedStyle(items[0]).height) || 180);
        }
        // A partir de aquí solo hay writes (style.width)
        for (const [day, items] of dayMap) {
            this.justifyDayItems(items, containerWidth, dayHeights.get(day));
        }
    },

    justifyDayItems: function (items, containerWidth, itemHeight) {
        if (items.length === 0) return;

        const gap = 2;
        // itemHeight viene pre-leído por justifyAllGroups para evitar reflows intercalados
        if (itemHeight === undefined) itemHeight = items[0].offsetHeight || parseInt(getComputedStyle(items[0]).height) || 180;

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
        if (items.length > 0) {
            const itemHeight = items[0].offsetHeight || parseInt(getComputedStyle(items[0]).height) || 180;
            this.justifyDayItems(items, containerWidth, itemHeight);
        }
    },

    updateMasonryForGroups: function (groupSelectors) {
        const grid = document.querySelector('.timeline-flat-grid');
        const sc = document.getElementById('timeline-scroll-container');
        if (!grid) return;

        // Preserve scroll position — masonry width changes can cause layout reflow
        // that shifts the visible area, especially for boundary day groups.
        const scrollTopBefore = sc ? sc.scrollTop : 0;

        // Batch reads before writes
        const containerWidth = grid.offsetWidth;
        const groups = groupSelectors.map(sel => {
            const dayKey = sel.replace(/^#?group-/, '');
            const items = Array.from(grid.querySelectorAll(`.masonry-item[data-day="${dayKey}"]`));
            const itemHeight = items.length > 0
                ? (items[0].offsetHeight || parseInt(getComputedStyle(items[0]).height) || 180)
                : 180;
            return { items, itemHeight };
        });
        for (const { items, itemHeight } of groups) {
            if (items.length > 0) this.justifyDayItems(items, containerWidth, itemHeight);
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

window.timelineHelpers = {};

window.timelineVirtualScroll = {
    _dotNetRef: null,
    _lastScrollTop: 0,
    _lastScrollTime: 0,
    _idleTimer: null,
    _scrollDirection: 0,
    _loadMorePending: false,
    _loadPrevPending: false,
    _scrubberNavTimer: null,
    VELOCITY_THRESHOLD: 600,
    LOAD_MORE_THRESHOLD: 1500, // px from edge to trigger loading

    init: function (dotNetRef) {
        this._dotNetRef = dotNetRef;
        this._loadMorePending = false;
        this._loadPrevPending = false;
        var sc = document.getElementById('timeline-scroll-container');
        if (!sc) return;
        this._lastScrollTop = sc.scrollTop;
        this._lastScrollTime = performance.now();
        var self = this;
        sc.addEventListener('scroll', function () { self._onScroll(); }, { passive: true });
    },

    _onScroll: function () {
        var sc = document.getElementById('timeline-scroll-container');
        if (!sc) return;
        var now = performance.now();
        var dt = now - this._lastScrollTime;
        var delta = sc.scrollTop - this._lastScrollTop;
        var velocity = dt > 0 ? (Math.abs(delta) / dt) * 1000 : 0;

        if (delta !== 0) this._scrollDirection = delta > 0 ? 1 : -1;

        this._lastScrollTop = sc.scrollTop;
        this._lastScrollTime = now;

        // Pause thumbnail loading during scroll
        if (window.imageLoadManager) window.imageLoadManager.onScrollStart();

        if (this._dotNetRef) {
            // Near bottom — load more items forward
            var distanceFromBottom = sc.scrollHeight - sc.scrollTop - sc.clientHeight;
            if (distanceFromBottom < this.LOAD_MORE_THRESHOLD && !this._loadMorePending) {
                this._loadMorePending = true;
                var self = this;
                this._dotNetRef.invokeMethodAsync('LoadMoreItems').then(function () {
                    self._loadMorePending = false;
                }).catch(function () {
                    self._loadMorePending = false;
                });
            }

            // Near top — load previous items backward
            if (sc.scrollTop < this.LOAD_MORE_THRESHOLD && !this._loadPrevPending) {
                this._loadPrevPending = true;
                var self = this;
                this._dotNetRef.invokeMethodAsync('LoadPreviousItems').then(function () {
                    self._loadPrevPending = false;
                }).catch(function () {
                    self._loadPrevPending = false;
                });
            }
        }

        clearTimeout(this._idleTimer);
        var self = this;
        var delay = velocity < this.VELOCITY_THRESHOLD ? 150 : 300;
        this._idleTimer = setTimeout(function () {
            // Resume image loading on scroll idle
            if (window.imageLoadManager) window.imageLoadManager.onScrollIdle(self._scrollDirection);
        }, delay);
    },

    // Debounced scrubber navigation: waits for the user to settle on a position
    // before asking .NET to render that section (avoids spamming during fast drag)
    navigateToGroup: function (dateStr) {
        clearTimeout(this._scrubberNavTimer);
        var self = this;
        this._scrubberNavTimer = setTimeout(function () {
            if (self._dotNetRef) {
                self._dotNetRef.invokeMethodAsync('ScrollToGroupFromJS', dateStr);
            }
        }, 150);
    },

    cleanup: function () {
        clearTimeout(this._idleTimer);
        clearTimeout(this._scrubberNavTimer);
        this._dotNetRef = null;
    }
};

// Scroll-velocity-aware image loader.
// During fast scroll: queues intersection entries without loading.
// On scroll idle: flushes queue loading center-viewport images first.
window.imageLoadManager = {
    _observer: null,
    _isScrolling: false,
    _pendingEntries: [],

    init: function () {
        if (this._observer) return; // already initialised
        var self = this;
        var sc = document.getElementById('timeline-scroll-container');
        this._observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting) return;
                var img = entry.target;
                if (!img.getAttribute('data-src')) {
                    // Already loaded (data-src removed), nothing to do
                    self._observer.unobserve(img);
                    return;
                }
                if (self._isScrolling) {
                    // Queue the entry — don't fire an HTTP request mid-scroll
                    self._pendingEntries.push(img);
                } else {
                    self._loadImage(img);
                }
            });
        }, {
            root: sc || null,
            rootMargin: '150px 0px', // pre-load a bit ahead/behind viewport
            threshold: 0
        });
    },

    // Register all img[data-src] elements inside the scroll container.
    // Safe to call multiple times — IntersectionObserver.observe() is idempotent.
    observeAll: function () {
        if (!this._observer) this.init();
        var sc = document.getElementById('timeline-scroll-container');
        if (!sc || !this._observer) return;
        sc.querySelectorAll('img[data-src]').forEach(function (img) {
            window.imageLoadManager._observer.observe(img);
        });
    },

    // Called by timelineVirtualScroll on every scroll event
    onScrollStart: function () {
        this._isScrolling = true;
    },

    // Called by timelineVirtualScroll after debounce (scroll stopped).
    // direction: +1 = scrolled down, -1 = scrolled up, 0 = unknown
    onScrollIdle: function (direction) {
        this._isScrolling = false;
        this._flushPending(direction || 0);
        this._scanViewport(direction || 0);
    },

    // Sort comparator for directional prefetch.
    // Images in the direction of travel load first; center-of-viewport loads first overall.
    _sortByPriority: function (imgs, scRect, direction) {
        var centerY = scRect.top + (scRect.height || 0) / 2;
        imgs.sort(function (a, b) {
            var ra = a.getBoundingClientRect();
            var rb = b.getBoundingClientRect();
            var aMidY = ra.top + ra.height / 2;
            var bMidY = rb.top + rb.height / 2;

            if (direction !== 0) {
                // Split screen into "ahead" (direction of travel) and "behind"
                var aAhead = direction > 0 ? aMidY >= centerY : aMidY <= centerY;
                var bAhead = direction > 0 ? bMidY >= centerY : bMidY <= centerY;
                if (aAhead !== bAhead) return aAhead ? -1 : 1;
            }

            // Within the same zone, prioritise by distance from center
            return Math.abs(aMidY - centerY) - Math.abs(bMidY - centerY);
        });
    },

    // Process queued entries — only those still in the viewport, direction-first
    _flushPending: function (direction) {
        if (this._pendingEntries.length === 0) return;
        var sc = document.getElementById('timeline-scroll-container');
        var self = this;
        var scRect = sc ? sc.getBoundingClientRect() : { top: 0, bottom: window.innerHeight, height: window.innerHeight };

        var still_visible = this._pendingEntries.filter(function (img) {
            if (!img.isConnected || !img.getAttribute('data-src')) return false;
            var r = img.getBoundingClientRect();
            return r.bottom > scRect.top - 150 && r.top < scRect.bottom + 150;
        });

        this._sortByPriority(still_visible, scRect, direction);
        still_visible.forEach(function (img) { self._loadImage(img); });
        this._pendingEntries = [];
    },

    // Direct viewport scan — catches images that the observer may have missed
    // (e.g. newly rendered sections that entered the viewport during scroll)
    _scanViewport: function (direction) {
        if (!this._observer) return;
        var sc = document.getElementById('timeline-scroll-container');
        if (!sc) return;
        var scRect = sc.getBoundingClientRect();
        var self = this;

        var imgs = Array.from(sc.querySelectorAll('img[data-src]')).filter(function (img) {
            var r = img.getBoundingClientRect();
            return r.bottom > scRect.top - 150 && r.top < scRect.bottom + 150;
        });

        this._sortByPriority(imgs, scRect, direction);
        imgs.forEach(function (img) { self._loadImage(img); });
    },

    _loadImage: function (img) {
        var src = img.getAttribute('data-src');
        if (!src) return;
        if (this._observer) this._observer.unobserve(img);
        img.removeAttribute('data-src');
        img.src = src;
        var markLoaded = function () {
            var container = img.closest('.asset-card-container');
            if (container) container.classList.add('img-loaded');
        };
        img.addEventListener('load', markLoaded, { once: true });
        // Mark as loaded on error too — avoid infinite shimmer on broken images
        img.addEventListener('error', markLoaded, { once: true });
    },

    cleanup: function () {
        if (this._observer) {
            this._observer.disconnect();
            this._observer = null;
        }
        this._pendingEntries = [];
        this._isScrolling = false;
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