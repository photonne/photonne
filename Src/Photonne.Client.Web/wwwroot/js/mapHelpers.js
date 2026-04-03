window.mapHelpers = {
    _mapInstance: null,
    _currentStyle: 'dark',
    _clusterGroup: null,
    _stateRestored: false,

    initMap: function (elementId, centerLat, centerLng, zoom, style) {
        if (typeof L === 'undefined') {
            console.error('Leaflet library not loaded');
            return null;
        }

        const element = document.getElementById(elementId);
        if (!element) {
            console.error(`Element with id '${elementId}' not found`);
            return null;
        }

        try {
            const map = L.map(elementId, {
                center: [centerLat, centerLng],
                zoom: zoom,
                zoomControl: true,
                attributionControl: true
            });

            let tileUrl, attribution;

            if (style === 'light') {
                tileUrl = 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png';
                attribution = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>';
            } else {
                tileUrl = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png';
                attribution = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>';
            }

            L.tileLayer(tileUrl, {
                attribution: attribution,
                subdomains: 'abcd',
                maxZoom: 19
            }).addTo(map);

            L.control.scale({
                position: 'bottomleft',
                metric: true,
                imperial: false
            }).addTo(map);

            if (navigator.geolocation) {
                const locateControl = L.control({ position: 'topleft' });

                locateControl.onAdd = function() {
                    const container = L.DomUtil.create('div', 'leaflet-bar leaflet-control');
                    const button = L.DomUtil.create('a', 'leaflet-control-locate', container);
                    button.href = '#';
                    button.title = 'Mostrar mi ubicación';
                    button.innerHTML = '📍';
                    button.style.cssText = 'line-height: 30px; text-align: center; font-size: 16px; width: 30px; height: 30px; display: block;';

                    L.DomEvent.disableClickPropagation(button);
                    L.DomEvent.on(button, 'click', function(e) {
                        L.DomEvent.stopPropagation(e);
                        L.DomEvent.preventDefault(e);

                        navigator.geolocation.getCurrentPosition(function(position) {
                            map.setView([position.coords.latitude, position.coords.longitude], 13);
                        }, function(error) {
                            console.error('Error getting location:', error);
                            alert('No se pudo obtener tu ubicación');
                        });
                    });

                    return container;
                };

                locateControl.addTo(map);
            }

            const fullscreenControl = L.control({ position: 'topleft' });

            fullscreenControl.onAdd = function() {
                const container = L.DomUtil.create('div', 'leaflet-bar leaflet-control');
                const button = L.DomUtil.create('a', 'leaflet-control-fullscreen', container);
                button.href = '#';
                button.title = 'Pantalla completa';
                button.innerHTML = '⛶';
                button.style.cssText = 'line-height: 30px; text-align: center; font-size: 18px; width: 30px; height: 30px; display: block;';

                L.DomEvent.disableClickPropagation(button);
                L.DomEvent.on(button, 'click', function(e) {
                    L.DomEvent.stopPropagation(e);
                    L.DomEvent.preventDefault(e);

                    const isFullscreen = document.fullscreenElement ||
                                        document.webkitFullscreenElement ||
                                        document.mozFullScreenElement ||
                                        document.msFullscreenElement;

                    if (!isFullscreen) {
                        const el = document.documentElement;
                        if (el.requestFullscreen) el.requestFullscreen();
                        else if (el.webkitRequestFullscreen) el.webkitRequestFullscreen();
                        else if (el.mozRequestFullScreen) el.mozRequestFullScreen();
                        else if (el.msRequestFullscreen) el.msRequestFullscreen();
                    } else {
                        if (document.exitFullscreen) document.exitFullscreen();
                        else if (document.webkitExitFullscreen) document.webkitExitFullscreen();
                        else if (document.mozCancelFullScreen) document.mozCancelFullScreen();
                        else if (document.msExitFullscreen) document.msExitFullscreen();
                    }
                });

                return container;
            };

            fullscreenControl.addTo(map);

            window.mapHelpers._mapInstance = map;
            window.mapHelpers._currentStyle = style || 'dark';
            window.mapHelpers._stateRestored = false;

            // Restore previous map position if available
            try {
                const saved = sessionStorage.getItem('ph_mapState');
                if (saved) {
                    const state = JSON.parse(saved);
                    map.setView([state.lat, state.lng], state.zoom);
                    sessionStorage.removeItem('ph_mapState');
                    window.mapHelpers._stateRestored = true;
                }
            } catch (e) { }

            console.log('Map initialized successfully with style:', style);
            return map;
        } catch (error) {
            console.error('Error initializing map:', error);
            return null;
        }
    },

    getMap: function () {
        return window.mapHelpers._mapInstance;
    },

    setStyle: function (style) {
        const map = window.mapHelpers._mapInstance;
        if (!map) return;

        map.eachLayer(function(layer) {
            if (layer instanceof L.TileLayer) {
                map.removeLayer(layer);
            }
        });

        let tileUrl, attribution;

        if (style === 'light') {
            tileUrl = 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png';
            attribution = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>';
        } else {
            tileUrl = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png';
            attribution = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>';
        }

        L.tileLayer(tileUrl, {
            attribution: attribution,
            subdomains: 'abcd',
            maxZoom: 19
        }).addTo(map);

        window.mapHelpers._currentStyle = style;
    },

    loadAllPoints: function (points, dotNetRef) {
        const map = window.mapHelpers._mapInstance;
        if (!map) {
            console.error('[JS-MAP] Map not initialized');
            return;
        }
        if (typeof L.markerClusterGroup === 'undefined') {
            console.error('[JS-MAP] Leaflet.markercluster not loaded');
            return;
        }

        // Remove existing cluster group
        if (window.mapHelpers._clusterGroup) {
            map.removeLayer(window.mapHelpers._clusterGroup);
            window.mapHelpers._clusterGroup = null;
        }

        const isDark = window.mapHelpers._currentStyle !== 'light';
        const borderColor = isDark ? '#FFD166' : '#C8960A';

        const clusterGroup = L.markerClusterGroup({
            maxClusterRadius: 80,
            showCoverageOnHover: false,
            zoomToBoundsOnClick: false,
            animate: true,
            animateAddingMarkers: false,
            disableClusteringAtZoom: 18,
            chunkedLoading: true,
            iconCreateFunction: function(cluster) {
                const count = cluster.getChildCount();
                const firstMarker = cluster.getAllChildMarkers()[0];
                const thumbnailUrl = firstMarker ? firstMarker.options.thumbnailUrl : '';
                const size = Math.max(40, Math.min(80, 35 + count * 2));
                // Badge sits outside the circle, so wrapper must be larger
                const badgeOffset = 12;
                const totalSize = size + badgeOffset;

                let innerHtml = '';
                if (thumbnailUrl) {
                    innerHtml = `<img src="${thumbnailUrl}" style="width:100%;height:100%;object-fit:cover;" onerror="this.style.display='none'"/>`;
                }

                const html = `
                    <div style="
                        width:${totalSize}px;
                        height:${totalSize}px;
                        position:relative;
                        display:flex;
                        align-items:center;
                        justify-content:center;
                    ">
                        <div style="
                            width:${size}px;
                            height:${size}px;
                            border-radius:50%;
                            overflow:hidden;
                            border:2px solid ${borderColor};
                            background:${borderColor};
                            flex-shrink:0;
                        ">
                            ${innerHtml}
                        </div>
                        <div style="
                            position:absolute;
                            top:0;
                            right:0;
                            background:#f44336;
                            color:white;
                            font-weight:700;
                            font-size:11px;
                            padding:2px 5px;
                            border-radius:10px;
                            min-width:20px;
                            text-align:center;
                            border:1.5px solid white;
                            line-height:1.4;
                            box-shadow:0 1px 4px rgba(0,0,0,0.35);
                            pointer-events:none;
                        ">${count}</div>
                    </div>`;

                return L.divIcon({
                    className: 'map-cluster-icon',
                    html: html,
                    iconSize: [totalSize, totalSize],
                    iconAnchor: [totalSize / 2, totalSize / 2]
                });
            }
        });

        clusterGroup.on('clusterclick', function(e) {
            const childMarkers = e.layer.getAllChildMarkers();
            if (childMarkers.length === 1) {
                const assetId = childMarkers[0].options.assetId;
                if (dotNetRef) {
                    dotNetRef.invokeMethodAsync('OnAssetClick', assetId)
                        .catch(err => console.error('[JS-MAP] Error calling OnAssetClick:', err));
                }
            } else {
                const ids = childMarkers.map(m => m.options.assetId);
                if (dotNetRef) {
                    dotNetRef.invokeMethodAsync('OnClusterClick', ids)
                        .catch(err => console.error('[JS-MAP] Error calling OnClusterClick:', err));
                }
            }
            L.DomEvent.stopPropagation(e);
        });

        const markerSize = 44;
        points.forEach(function(point) {
            let markerHtml;
            if (point.thumbnailUrl) {
                markerHtml = `
                    <div style="
                        width:${markerSize}px;
                        height:${markerSize}px;
                        border-radius:50%;
                        overflow:hidden;
                        border:2px solid ${borderColor};
                    ">
                        <img src="${point.thumbnailUrl}"
                             style="width:100%;height:100%;object-fit:cover;"
                             onerror="this.style.display='none'"/>
                    </div>`;
            } else {
                markerHtml = `
                    <div style="
                        width:${markerSize}px;
                        height:${markerSize}px;
                        border-radius:50%;
                        background:${borderColor};
                        border:2px solid ${borderColor};
                    "></div>`;
            }

            const icon = L.divIcon({
                className: 'map-cluster-icon',
                html: markerHtml,
                iconSize: [markerSize, markerSize],
                iconAnchor: [markerSize / 2, markerSize / 2]
            });

            const marker = L.marker([point.latitude, point.longitude], {
                icon: icon,
                thumbnailUrl: point.thumbnailUrl,
                assetId: point.id
            });

            marker.on('click', function(e) {
                if (e.originalEvent) {
                    if (e.originalEvent.stopPropagation) e.originalEvent.stopPropagation();
                    if (e.originalEvent.preventDefault) e.originalEvent.preventDefault();
                }
                if (dotNetRef) {
                    setTimeout(() => {
                        dotNetRef.invokeMethodAsync('OnAssetClick', point.id)
                            .catch(err => console.error('[JS-MAP] Error calling OnAssetClick:', err));
                    }, 50);
                }
            });

            clusterGroup.addLayer(marker);
        });

        map.addLayer(clusterGroup);
        window.mapHelpers._clusterGroup = clusterGroup;
        console.log('[JS-MAP] Loaded', points.length, 'markers into cluster group');
    },

    fitBounds: function (minLat, minLng, maxLat, maxLng) {
        const map = window.mapHelpers._mapInstance;
        if (!map) return;
        map.fitBounds([[minLat, minLng], [maxLat, maxLng]], {
            padding: [50, 50]
        });
    },

    getMapBounds: function () {
        const map = window.mapHelpers._mapInstance;
        if (!map) return null;
        const bounds = map.getBounds();
        return {
            minLat: bounds.getSouth(),
            minLng: bounds.getWest(),
            maxLat: bounds.getNorth(),
            maxLng: bounds.getEast()
        };
    },

    getZoom: function () {
        const map = window.mapHelpers._mapInstance;
        if (!map) return 2;
        return map.getZoom();
    },

    removeAllMarkers: function () {
        const map = window.mapHelpers._mapInstance;
        if (!map) return;

        if (window.mapHelpers._clusterGroup) {
            try {
                map.removeLayer(window.mapHelpers._clusterGroup);
                console.log('[JS-MAP] Removed cluster group');
            } catch (e) {
                console.error('[JS-MAP] Error removing cluster group:', e);
            }
            window.mapHelpers._clusterGroup = null;
        }
    },

    wasStateRestored: function () {
        return window.mapHelpers._stateRestored;
    },

    destroyMap: function () {
        window.mapHelpers._clusterGroup = null;
        if (window.mapHelpers._mapInstance) {
            try {
                const center = window.mapHelpers._mapInstance.getCenter();
                const zoom = window.mapHelpers._mapInstance.getZoom();
                sessionStorage.setItem('ph_mapState', JSON.stringify({ lat: center.lat, lng: center.lng, zoom: zoom }));
            } catch (e) { }
            try {
                window.mapHelpers._mapInstance.remove();
                console.log('[JS-MAP] Map destroyed');
            } catch (e) {
                console.error('[JS-MAP] Error destroying map:', e);
            }
            window.mapHelpers._mapInstance = null;
        }
    }
};

// Mini mapa para detalle de asset — instancia independiente del mapa principal
window.miniMapHelpers = {
    _instances: {},

    init: function (elementId, lat, lng, isDark) {
        if (typeof L === 'undefined') return;

        // Destruir instancia previa si existe
        this.destroy(elementId);

        const element = document.getElementById(elementId);
        if (!element) return;

        const map = L.map(elementId, {
            center: [lat, lng],
            zoom: 14,
            zoomControl: false,
            attributionControl: false,
            dragging: false,
            scrollWheelZoom: false,
            doubleClickZoom: false,
            touchZoom: false,
            keyboard: false
        });

        const tileUrl = isDark
            ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
            : 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png';

        L.tileLayer(tileUrl, { subdomains: 'abcd', maxZoom: 19 }).addTo(map);

        const markerHtml = `<div style="
            width: 14px; height: 14px;
            background: #FFD166;
            border: 3px solid white;
            border-radius: 50%;
            box-shadow: 0 2px 8px rgba(0,0,0,0.5);
        "></div>`;

        const icon = L.divIcon({
            className: '',
            html: markerHtml,
            iconSize: [14, 14],
            iconAnchor: [7, 7]
        });

        L.marker([lat, lng], { icon }).addTo(map);

        this._instances[elementId] = map;
    },

    destroy: function (elementId) {
        if (this._instances[elementId]) {
            this._instances[elementId].remove();
            delete this._instances[elementId];
        }
    },

    initWhenVisible: function (elementId, lat, lng, isDark) {
        // Esperar a que la transición CSS termine (300ms) antes de inicializar
        setTimeout(() => {
            this.init(elementId, lat, lng, isDark);
        }, 320);
    }
};
