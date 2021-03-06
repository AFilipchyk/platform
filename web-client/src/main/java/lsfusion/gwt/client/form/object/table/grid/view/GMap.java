package lsfusion.gwt.client.form.object.table.grid.view;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.StyleElement;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.ui.RequiresResize;
import lsfusion.gwt.client.base.GwtSharedUtils;
import lsfusion.gwt.client.form.controller.GFormController;
import lsfusion.gwt.client.form.object.GGroupObjectValue;
import lsfusion.gwt.client.form.object.table.grid.controller.GGridController;
import org.vectomatic.dom.svg.OMSVGDocument;
import org.vectomatic.dom.svg.OMSVGFEColorMatrixElement;
import org.vectomatic.dom.svg.OMSVGFilterElement;
import org.vectomatic.dom.svg.OMSVGSVGElement;
import org.vectomatic.dom.svg.utils.OMSVGParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class GMap extends GSimpleStateTableView implements RequiresResize {

    public GMap(GFormController form, GGridController grid) {
        super(form, grid);
    }

    private JavaScriptObject map;
    private JavaScriptObject markerClusters;
    private Map<GGroupObjectValue, JavaScriptObject> markers = new HashMap<>();
    private ArrayList<JavaScriptObject> lines = new ArrayList<>();
    @Override
    protected void render(Element renderElement, com.google.gwt.dom.client.Element recordElement, JsArray<JavaScriptObject> listObjects) {
        if(map == null) {
            markerClusters = createMarkerClusters();
            map = createMap(renderElement, markerClusters);
            renderElement.getStyle().setProperty("zIndex", "0"); // need this because leaflet uses z-indexes and therefore dialogs for example are shown below layers
        }

        Map<Object, JsArray<JavaScriptObject>> routes = new HashMap<>();

        Map<GGroupObjectValue, JavaScriptObject> oldMarkers = new HashMap<>(markers);
        for(int i=0,size=listObjects.length();i<size;i++) {
            JavaScriptObject object = listObjects.get(i);
            GGroupObjectValue key = getKey(object);

            JavaScriptObject marker = oldMarkers.remove(key);
            if(marker == null) {
                marker = createMarker(map, recordElement, fromObject(key), markerClusters);
                markers.put(key, marker);
            }

            String filterStyle = createFilter(getMarkerColor(object));
            updateMarker(map, marker, object, filterStyle);

            Object line = getLine(object);
            if(line != null)
                routes.computeIfAbsent(line, o -> JavaScriptObject.createArray().cast()).push(marker);
        }
        for(Map.Entry<GGroupObjectValue, JavaScriptObject> oldMarker : oldMarkers.entrySet()) {
            removeMarker(oldMarker.getValue(), markerClusters);
            markers.remove(oldMarker.getKey());
        }

        for(JavaScriptObject line : lines)
            removeLine(map, line);
        lines.clear();
        for(JsArray<JavaScriptObject> route : routes.values())
            if(route.length() > 1)
                lines.add(createLine(map, route));
        
        fitBounds(map, GwtSharedUtils.toArray(markers.values()));
    }

    protected native JavaScriptObject createMap(com.google.gwt.dom.client.Element element, JavaScriptObject markerClusters)/*-{
        var L = $wnd.L;
        var map = L.map(element);

        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        }).addTo(map);

        map.addLayer(markerClusters);
        
        return map;            
    }-*/;

    protected native JavaScriptObject createMarkerClusters()/*-{
        var L = $wnd.L;
        var browser = navigator.userAgent.toLowerCase();
        if (browser.indexOf('firefox') > -1) {
            return L.markerClusterGroup();
        }
        return L.markerClusterGroup({
            iconCreateFunction: function (cluster) {
                var colors = [];
                cluster.getAllChildMarkers().forEach(function (marker) {
                    colors.push(marker.color ? marker.color : "var(--focus-color)");
                });
                var colorsSetArray = Array.from(new Set(colors));

                var backgroundColor = ""
                if (colorsSetArray.length === 1) {
                    backgroundColor = colorsSetArray[0];
                } else {
                    backgroundColor = "conic-gradient("
                    var prevPercent = 0;
                    colorsSetArray.forEach(function (color) {
                        backgroundColor += color;
                        if (colorsSetArray.indexOf(color) !== 0) {
                            backgroundColor += " 0"
                        }

                        if (colorsSetArray.indexOf(color) < colorsSetArray.length - 1) {
                            var newPercent = prevPercent + (colors.filter(function (c) {
                                return c === color;
                            }).length / colors.length * 100);
                            backgroundColor += " " + newPercent + "%,";
                            prevPercent = newPercent;
                        }
                    });
                    backgroundColor += ")"
                }

                return L.divIcon({
                    html: "<div class=\"leaflet-marker-cluster\" style=\"background:" + backgroundColor + ";\">" +
                        "<div class=\"leaflet-marker-cluster-text\">" + cluster.getChildCount() + "</div>" +
                        "</div>"
                });
            }
        });
    }-*/;

    protected native JavaScriptObject createMarker(JavaScriptObject map, com.google.gwt.dom.client.Element popupElement, JavaScriptObject key, JavaScriptObject markerClusters)/*-{
        var L = $wnd.L;

        var marker = L.marker([0, 0]);
        
        if (popupElement !== null)
            marker.bindPopup(popupElement, {maxWidth: Number.MAX_SAFE_INTEGER});
        var thisObject = this;
        marker.on('click', function (e) {
            thisObject.@GMap::changeSimpleGroupObject(*)(key);
        });
        
        markerClusters.addLayer(marker);
        
        return marker;
    }-*/;

    protected native void removeMarker(JavaScriptObject marker, JavaScriptObject markerClusters)/*-{
        markerClusters.removeLayer(marker);
    }-*/;

    protected native void appendSVG(JavaScriptObject map, com.google.gwt.dom.client.Element svg)/*-{
        map._container.appendChild(svg)
    }-*/;

    protected native String getMarkerColor(JavaScriptObject element)/*-{
        return element.color;
    }-*/;

    protected native Object getLine(JavaScriptObject element)/*-{
        return element.line;
    }-*/;

    protected native JavaScriptObject createLine(JavaScriptObject map, JsArray<JavaScriptObject> markers)/*-{
        var L = $wnd.L;

        var points = [];
        markers.forEach(function (marker) {
            var latlng = marker.getLatLng();
            points.push([latlng.lat, latlng.lng]);
        });
        var line = L.polyline(points).addTo(map);
        var lineArrow = L.polylineDecorator(line, {
            patterns: [
                { offset: '100%', repeat: 0, symbol: L.Symbol.arrowHead({pathOptions: {stroke: true}}) }
            ]
        }).addTo(map);
        return {line : line, lineArrow : lineArrow};
    }-*/;

    protected native void removeLine(JavaScriptObject map, JavaScriptObject line)/*-{
        map.removeLayer(line.line);
        map.removeLayer(line.lineArrow);
    }-*/;

    protected String createFilter(String colorStr) {
        String svgStyle = null;
        if (colorStr != null) {
            int red = Integer.valueOf(colorStr.substring(1, 3), 16);
            int green = Integer.valueOf(colorStr.substring(3, 5), 16);
            int blue = Integer.valueOf(colorStr.substring(5, 7), 16);
            String svgID = "svg_" + red + "_" + green + "_" + blue;
            svgStyle = svgID + "-style";

            com.google.gwt.dom.client.Element svgEl = Document.get().getElementById(svgID);
            if (svgEl == null) {
                OMSVGDocument doc = OMSVGParser.currentDocument();

                OMSVGSVGElement svgElement = doc.createSVGSVGElement();
                OMSVGFilterElement svgFilterElement = doc.createSVGFilterElement();
                svgFilterElement.setId(svgID);
                svgFilterElement.setAttribute("color-interpolation-filters", "sRGB");

                OMSVGFEColorMatrixElement svgfeColorMatrixElement = doc.createSVGFEColorMatrixElement();
                svgfeColorMatrixElement.setAttribute("type", "matrix");
                svgfeColorMatrixElement.setAttribute("values", (float) red / 256 + " 0 0 0  0 \n" +
                        (float) green / 256 + " 0 0 0  0  \n" +
                        (float) blue / 256 + " 0 0 0  0 \n" +
                        "0 0 0 1  0");
                svgFilterElement.appendChild(svgfeColorMatrixElement);
                svgElement.appendChild(svgFilterElement);

                appendSVG(map, svgElement.getElement());

                StyleElement styleElement = Document.get().createStyleElement();
                styleElement.setType("text/css");
                styleElement.setInnerHTML("." + svgStyle + " { filter: url(#" + svgID + ") }");
                Document.get().getElementsByTagName("head").getItem(0).appendChild(styleElement);
            }
        }
        return svgStyle;
    }
    
    protected native void updateMarker(JavaScriptObject map, JavaScriptObject marker, JavaScriptObject element, String filterStyle)/*-{
        var L = $wnd.L;

        marker.setLatLng([element.latitude != null ? element.latitude : 0, element.longitude != null ? element.longitude : 0]);
        var iconUrl = element.icon != null ? element.icon : L.Icon.Default.prototype._getIconUrl('icon');

        var myIcon = L.divIcon({html: "<img src=" + iconUrl + " alt=\"\" tabindex=\"0\">", className: filterStyle ? filterStyle : ''});
        
        marker.setIcon(myIcon);
        marker.color = element.color;
    }-*/;

    protected native void fitBounds(JavaScriptObject map, JsArray<JavaScriptObject> markers)/*-{
        var L = $wnd.L;

        if(markers.length > 0)
            map.fitBounds(new L.featureGroup(markers).getBounds());
    }-*/;

    @Override
    public void onResize() {
        resized(map);
    }

    protected native void resized(JavaScriptObject map)/*-{
        if (map) {
            map._onResize();
        }
    }-*/;
}
