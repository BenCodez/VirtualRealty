package com.modnmetl.virtualrealty.manager;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.model.other.HighlightType;
import com.modnmetl.virtualrealty.model.plot.Plot;
import lombok.Getter;
import net.pl3x.map.core.Pl3xMap;
import net.pl3x.map.core.markers.Point;
import net.pl3x.map.core.markers.layer.SimpleLayer;
import net.pl3x.map.core.markers.marker.Marker;
import net.pl3x.map.core.markers.marker.Rectangle;
import net.pl3x.map.core.markers.option.Fill;
import net.pl3x.map.core.markers.option.Options;
import net.pl3x.map.core.markers.option.Stroke;
import net.pl3x.map.core.markers.option.Tooltip;
import net.pl3x.map.core.world.World;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class Pl3xMapManager {

    private final VirtualRealty instance;

    @Getter
    private boolean pl3xMapPresent = false;
    private Pl3xMap pl3xMapAPI = null;
    private final Map<String, SimpleLayer> worldLayers = new HashMap<>();
    private static final String MARKER_STRING = "<h3>Plot #%s</h3><b>Owned By: </b>Available";
    private static final String MARKER_OWNED_STRING = "<h3>Plot #%s</h3><b>Owned By: </b>%s<br><b>Owned Until: </b>%s";

    public Pl3xMapManager(VirtualRealty instance) {
        this.instance = instance;
    }

    public void registerPl3xMap() {
        VirtualRealty.debug("Attempting to register Pl3xMap integration...");
        new BukkitRunnable() {
            @Override
            public void run() {
                Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("Pl3xMap");
                if (plugin == null) {
                    VirtualRealty.debug("Pl3xMap plugin not found, skipping integration");
                    return;
                }
                
                VirtualRealty.debug("Pl3xMap plugin found, setting up integration...");
                pl3xMapPresent = true;
                if (!plugin.isEnabled()) {
                    VirtualRealty.debug("Pl3xMap plugin is not enabled, skipping integration");
                    return;
                }
                
                try {
                    pl3xMapAPI = Pl3xMap.api();
                    if (pl3xMapAPI == null) {
                        VirtualRealty.debug("Pl3xMap API is null, retrying...");
                        return;
                    }
                    
                    VirtualRealty.debug("Pl3xMap API initialized successfully");
                    VirtualRealty.debug("Registering pl3xmap plot markers for " + PlotManager.getInstance().getPlots().size() + " plots...");
                    for (Plot plot : PlotManager.getInstance().getPlots()) {
                        resetPlotMarker(plot);
                    }
                    VirtualRealty.debug("Registered pl3xmap plot markers successfully");
                    this.cancel();
                } catch (Exception e) {
                    VirtualRealty.debug("Failed to initialize Pl3xMap API: " + e.getMessage());
                    this.cancel();
                }
            }
        }.runTaskTimer(instance, 20, 20 * 5);
    }

    private SimpleLayer getOrCreateLayer(String worldName) {
        if (!worldLayers.containsKey(worldName)) {
            try {
                World pl3xWorld = pl3xMapAPI.getWorldRegistry().get(worldName);
                if (pl3xWorld == null) {
                    VirtualRealty.debug("Pl3xMap world not found: " + worldName);
                    return null;
                }
                
                SimpleLayer layer = new SimpleLayer("virtualrealty_plots", () -> "VirtualRealty Plots");
                layer.setLabel("Plots");
                layer.setDefaultHidden(false);
                layer.setPriority(100);
                layer.setZIndex(100);
                
                pl3xWorld.getLayerRegistry().register(layer);
                worldLayers.put(worldName, layer);
                VirtualRealty.debug("Created Pl3xMap layer for world: " + worldName);
            } catch (Exception e) {
                VirtualRealty.debug("Failed to create Pl3xMap layer for world " + worldName + ": " + e.getMessage());
                return null;
            }
        }
        return worldLayers.get(worldName);
    }

    public static void resetPlotMarker(Plot plot) {
        VirtualRealty vrInstance = VirtualRealty.getInstance();
        if (vrInstance.pl3xMapManager == null || !vrInstance.pl3xMapManager.isPl3xMapPresent()) return;
        
        vrInstance.pl3xMapManager.updatePlotMarker(plot);
    }

    private void updatePlotMarker(Plot plot) {
        try {
            VirtualRealty.debug("Creating Pl3xMap marker for plot #" + plot.getID() + " in world " + plot.getCreatedWorldRaw());
            LocalDateTime localDateTime = plot.getOwnedUntilDate();
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
            String ownedBy;
            String fillColorHex;
            double opacity;
            
            if (plot.getOwnedBy() == null) {
                ownedBy = "Available";
                fillColorHex = VirtualRealty.getPluginConfiguration().pl3xmapMarkersColor.available.color;
                opacity = VirtualRealty.getPluginConfiguration().pl3xmapMarkersColor.available.opacity;
            } else {
                ownedBy = plot.getPlotOwner().getName();
                fillColorHex = VirtualRealty.getPluginConfiguration().pl3xmapMarkersColor.owned.color;
                opacity = VirtualRealty.getPluginConfiguration().pl3xmapMarkersColor.owned.opacity;
            }
            
            if (VirtualRealty.getPluginConfiguration().pl3xmapType == HighlightType.OWNED && plot.getOwnedBy() == null) {
                VirtualRealty.debug("Skipping plot #" + plot.getID() + " - configured to show only OWNED plots, but plot is available");
                return;
            }
            if (VirtualRealty.getPluginConfiguration().pl3xmapType == HighlightType.AVAILABLE && plot.getOwnedBy() != null) {
                VirtualRealty.debug("Skipping plot #" + plot.getID() + " - configured to show only AVAILABLE plots, but plot is owned");
                return;
            }
            
            SimpleLayer layer = getOrCreateLayer(plot.getCreatedWorldRaw());
            if (layer == null) {
                VirtualRealty.debug("Failed to get or create layer for world: " + plot.getCreatedWorldRaw());
                return;
            }
            
            String markerId = "virtualrealty_plot_" + plot.getID();
            
            // Create rectangle marker
            Rectangle rectangle = Rectangle.of(
                markerId,
                plot.getXMin(),
                plot.getZMin(),
                plot.getXMax(),
                plot.getZMax()
            );
            
            // Parse color from hex string
            Color color = Color.decode(fillColorHex.startsWith("#") ? fillColorHex : "#" + fillColorHex);
            
            // Convert Color to ARGB integer
            int fillColor = (int) (opacity * 255) << 24 | color.getRGB() & 0x00FFFFFF;
            int strokeColor = Color.GRAY.getRGB() | 0xFF000000; // Full opacity gray
            
            // Create fill and stroke options
            Fill fill = new Fill(fillColor).setType(Fill.Type.EVENODD);
            Stroke stroke = new Stroke(2, strokeColor);
            
            // Create tooltip with popup content
            String popupContent = plot.getOwnedBy() == null 
                ? String.format(MARKER_STRING, plot.getID())
                : String.format(MARKER_OWNED_STRING, plot.getID(), ownedBy, dateTimeFormatter.format(localDateTime));
            
            Tooltip tooltip = new Tooltip(popupContent);
            
            // Set marker options
            Options options = Options.builder()
                .fill(fill)
                .stroke(stroke)
                .tooltip(tooltip)
                .build();
            rectangle.setOptions(options);
            
            // Add or update marker
            layer.addMarker(rectangle);
            VirtualRealty.debug("Successfully created Pl3xMap marker for plot #" + plot.getID() + " at (" + 
                plot.getXMin() + "," + plot.getZMin() + ") to (" + plot.getXMax() + "," + plot.getZMax() + ")");
            
        } catch (Exception e) {
            VirtualRealty.debug("Failed to update Pl3xMap marker for plot " + plot.getID() + ": " + e.getMessage());
        }
    }

    public static void removePl3xMapMarker(Plot plot) {
        VirtualRealty vrInstance = VirtualRealty.getInstance();
        if (vrInstance.pl3xMapManager == null || !vrInstance.pl3xMapManager.isPl3xMapPresent()) return;
        
        vrInstance.pl3xMapManager.removeMarker(plot);
    }

    private void removeMarker(Plot plot) {
        try {
            SimpleLayer layer = worldLayers.get(plot.getCreatedWorldRaw());
            if (layer == null) return;
            
            String markerId = "virtualrealty_plot_" + plot.getID();
            layer.removeMarker(markerId);
            
        } catch (Exception e) {
            VirtualRealty.debug("Failed to remove Pl3xMap marker for plot " + plot.getID() + ": " + e.getMessage());
        }
    }
}