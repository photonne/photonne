window.mapHelpers = {
    _mapInstance: null,
    _currentStyle: 'dark',
    _markerLayerGroup: null,
    
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
            
            // Usar tiles con estilo similar a Immich
            // Para dark: usar CartoDB Dark Matter
            // Para light: usar CartoDB Positron
            let tileUrl, attribution;
            
            if (style === 'light') {
                // Estilo claro y simple
                tileUrl = 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png';
                attribution = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>';
            } else {
                // Estilo oscuro y simple
                tileUrl = 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png';
                attribution = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>';
            }
            
            L.tileLayer(tileUrl, {
                attribution: attribution,
                subdomains: 'abcd',
                maxZoom: 19
            }).addTo(map);
            
            // Agregar control de escala
            L.control.scale({
                position: 'bottomleft',
                metric: true,
                imperial: false
            }).addTo(map);
            
            // Agregar control de ubicación
            if (navigator.geolocation) {
                const locateControl = L.control({
                    position: 'topleft'
                });
                
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
            
            // Agregar control de pantalla completa
            const fullscreenControl = L.control({
                position: 'topleft'
            });
            
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
                        const element = document.documentElement;
                        if (element.requestFullscreen) {
                            element.requestFullscreen();
                        } else if (element.webkitRequestFullscreen) {
                            element.webkitRequestFullscreen();
                        } else if (element.mozRequestFullScreen) {
                            element.mozRequestFullScreen();
                        } else if (element.msRequestFullscreen) {
                            element.msRequestFullscreen();
                        }
                    } else {
                        if (document.exitFullscreen) {
                            document.exitFullscreen();
                        } else if (document.webkitExitFullscreen) {
                            document.webkitExitFullscreen();
                        } else if (document.mozCancelFullScreen) {
                            document.mozCancelFullScreen();
                        } else if (document.msExitFullscreen) {
                            document.msExitFullscreen();
                        }
                    }
                });
                
                return container;
            };
            
            fullscreenControl.addTo(map);
            
            // Crear un LayerGroup para manejar todos los marcadores
            const markerLayerGroup = L.layerGroup().addTo(map);
            
            window.mapHelpers._mapInstance = map;
            window.mapHelpers._markerLayerGroup = markerLayerGroup;
            window.mapHelpers._currentStyle = style || 'dark';
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
        
        // Remover todas las capas de tiles existentes (pero mantener el LayerGroup de marcadores)
        map.eachLayer(function(layer) {
            if (layer instanceof L.TileLayer) {
                map.removeLayer(layer);
            }
        });
        
        // Asegurar que el LayerGroup de marcadores esté en el mapa
        if (window.mapHelpers._markerLayerGroup && !map.hasLayer(window.mapHelpers._markerLayerGroup)) {
            window.mapHelpers._markerLayerGroup.addTo(map);
        }
        
        // Agregar nueva capa con el estilo seleccionado
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
    
    addClusterMarker: function (lat, lng, count, thumbnailUrl, dotNetRef, clusterId) {
        const map = window.mapHelpers._mapInstance;
        if (!map) return null;
        
        // Círculos más pequeños: mínimo 40px, máximo 80px
        const radius = Math.max(40, Math.min(80, 35 + count * 2));
        const size = radius * 2;
        
        // Crear el contenido del marcador con la miniatura - diseño flat
        let htmlContent = '';
        if (thumbnailUrl && thumbnailUrl.trim() !== '') {
            htmlContent = `
                <div class="map-marker-container" style="
                    width: ${size}px;
                    height: ${size}px;
                    border-radius: 50%;
                    overflow: visible;
                    border: 2px solid #1976d2;
                    position: relative;
                    background: #1976d2;
                ">
                    <img src="${thumbnailUrl}" 
                         alt="Cluster ${count}" 
                         style="
                             width: 100%;
                             height: 100%;
                             object-fit: cover;
                             border-radius: 50%;
                         "
                         onerror="this.style.display='none';"
                    />
                    <div style="
                        position: absolute;
                        top: -8px;
                        right: -8px;
                        background: #ff5722;
                        color: white;
                        font-weight: bold;
                        font-size: 12px;
                        padding: 3px 7px;
                        border-radius: 12px;
                        min-width: 22px;
                        text-align: center;
                        border: 2px solid white;
                        line-height: 1.2;
                        z-index: 1000;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.2);
                    ">${count}</div>
                </div>
            `;
        } else {
            // Si no hay miniatura, mostrar solo el número en un círculo flat con badge
            htmlContent = `
                <div class="map-marker-container" style="
                    width: ${size}px;
                    height: ${size}px;
                    border-radius: 50%;
                    background: #1976d2;
                    border: 2px solid #1976d2;
                    display: flex;
                    align-items: center;
                    justify-content: center;
                    color: white;
                    font-weight: bold;
                    font-size: 14px;
                    position: relative;
                ">
                    <div style="
                        position: absolute;
                        top: -8px;
                        right: -8px;
                        background: #ff5722;
                        color: white;
                        font-weight: bold;
                        font-size: 12px;
                        padding: 3px 7px;
                        border-radius: 12px;
                        min-width: 22px;
                        text-align: center;
                        border: 2px solid white;
                        line-height: 1.2;
                        z-index: 1000;
                        box-shadow: 0 1px 3px rgba(0,0,0,0.2);
                    ">${count}</div>
                </div>
            `;
        }
        
        const icon = L.divIcon({
            className: 'map-cluster-icon',
            html: htmlContent,
            iconSize: [size, size],
            iconAnchor: [size / 2, size / 2]
        });
        
        const marker = L.marker([lat, lng], { 
            icon: icon,
            interactive: true
        });
        
        // Agregar al LayerGroup en lugar de directamente al mapa
        if (window.mapHelpers._markerLayerGroup) {
            marker.addTo(window.mapHelpers._markerLayerGroup);
        } else {
            marker.addTo(map);
        }
        
        // Usar una closure para capturar el clusterId actual
        const currentId = clusterId;
        marker.on('click', function(e) {
            console.log('[JS-MAP] Marker Leaflet event clicked, clusterId:', currentId);
            console.log('[JS-MAP] Current map zoom:', map.getZoom());
            
            // Usar stopPropagation de Leaflet para evitar que el click llegue al mapa
            if (e.originalEvent) {
                if (e.originalEvent.stopPropagation) e.originalEvent.stopPropagation();
                if (e.originalEvent.preventDefault) e.originalEvent.preventDefault();
            }
            
            if (dotNetRef) {
                console.log('[JS-MAP] Invoking OnClusterClick in Blazor for ID:', currentId);
                
                // Añadir un pequeño retraso para asegurar que si hay una recarga en curso,
                // Blazor tenga tiempo de actualizar su estado de _loading.
                setTimeout(() => {
                    dotNetRef.invokeMethodAsync('OnClusterClick', currentId)
                        .then(() => console.log('[JS-MAP] OnClusterClick invoked successfully'))
                        .catch(err => console.error('[JS-MAP] Error calling OnClusterClick:', err));
                }, 50);
            } else {
                console.error('[JS-MAP] dotNetRef is null, cannot call OnClusterClick');
            }
        });
        
        return { marker };
    },
    
    fitBounds: function (minLat, minLng, maxLat, maxLng) {
        const map = window.mapHelpers._mapInstance;
        if (!map) return;
        map.fitBounds([[minLat, minLng], [maxLat, maxLng]], {
            padding: [50, 50]
        });
    },
    
    onMapMoveEnd: function (dotNetRef) {
        const map = window.mapHelpers._mapInstance;
        if (!map) return;
        
        const handleMoveEnd = function() {
            const bounds = map.getBounds();
            const zoom = map.getZoom();
            console.log('Map moveend event triggered', { zoom: zoom });
            dotNetRef.invokeMethodAsync('OnMapMoveEnd', 
                bounds.getSouth(),
                bounds.getWest(),
                bounds.getNorth(),
                bounds.getEast(),
                zoom
            );
        };
        
        map.on('moveend', handleMoveEnd);
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
    
    removeAllMarkers: function (markers) {
        console.log('[JS-MAP] removeAllMarkers called');
        const map = window.mapHelpers._mapInstance;
        if (!map) return;

        if (window.mapHelpers._markerLayerGroup) {
            try {
                // Eliminar todos los marcadores del LayerGroup
                window.mapHelpers._markerLayerGroup.clearLayers();
                console.log('[JS-MAP] Cleared all markers from layer group');
            } catch (e) {
                console.error('[JS-MAP] Error clearing marker layer group:', e);
            }
        }

        // También nos aseguramos de que no haya marcadores sueltos en el mapa
        // que no estén en el layer group por alguna razón
        map.eachLayer(function(layer) {
            if (layer instanceof L.Marker && layer.options && layer.options.className === 'map-cluster-icon') {
                map.removeLayer(layer);
            }
        });
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
            background: #818cf8;
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
