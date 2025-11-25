package com.modnmetl.virtualrealty.listener;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.plot.PlotMember;
import com.modnmetl.virtualrealty.model.plot.PlotSize;
import com.modnmetl.virtualrealty.util.PlayerLookupUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlotEntranceListener extends VirtualListener {

    public HashMap<Player, Map.Entry<Plot, Boolean>> enteredPlot = new HashMap<>();

    public PlotEntranceListener(VirtualRealty plugin) {
        super(plugin);
    }

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onPlotMove(PlayerMoveEvent e) {
        if (e.isCancelled())
            return;

        Player player = e.getPlayer();
        Location to = e.getTo();
        if (to == null)
            return;

        Plot plot = PlotManager.getInstance().getPlot(to, true);
        if (plot != null) {
            String enterPlotString = VirtualRealty.getMessages().enteredAvailablePlot;

            UUID ownerId = plot.getOwnedBy();
            if (ownerId != null) {
                String ownerName = PlayerLookupUtil.getBestName(ownerId);
                if (ownerName == null || ownerName.isEmpty()) {
                    ownerName = "Unknown";
                }
                enterPlotString = VirtualRealty.getMessages().enteredOwnedPlot
                        .replaceAll("%owner%", ownerName)
                        .replaceAll("%plot_id%", String.valueOf(plot.getID()));
            }

            if (!enteredPlot.containsKey(player)) {
                enteredPlot.put(player, new AbstractMap.SimpleEntry<>(plot, true));
                Plot newPlot = enteredPlot.get(player).getKey();

                // Gamemode handling
                if (VirtualRealty.getPluginConfiguration().enablePlotGamemode) {
                    if (newPlot.hasMembershipAccess(player.getUniqueId())) {
                        if (ownerId != null && ownerId.equals(player.getUniqueId())) {
                            player.setGameMode(newPlot.getSelectedGameMode());
                        } else if (newPlot.getMember(player.getUniqueId()) != null) {
                            PlotMember plotMember = newPlot.getMember(player.getUniqueId());
                            player.setGameMode(plotMember.getSelectedGameMode());
                        }
                    }
                }

                // Sounds + action bar messages (1.9+ only)
                if (!VirtualRealty.getInstance().getServer().getBukkitVersion().startsWith("1.8")) {
                    if (VirtualRealty.getPluginConfiguration().plotSound) {
                        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_OPEN, 0.4f, 0.8f);
                    }
                    if (enteredPlot.get(player).getKey().getPlotSize() == PlotSize.AREA) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText(VirtualRealty.getMessages().enteredProtectedArea));
                    } else {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText(enterPlotString));
                    }
                }
            } else {
                if (!enteredPlot.get(player).getValue()) {
                    enteredPlot.replace(player, new AbstractMap.SimpleEntry<>(plot, true));
                    if (VirtualRealty.getPluginConfiguration().enablePlotGamemode) {
                        player.setGameMode(VirtualRealty.getPluginConfiguration().getDefaultPlotGamemode());
                    }
                }
            }
        } else {
            if (!enteredPlot.containsKey(player))
                return;

            if (enteredPlot.get(player).getValue()) {
                String leavePlotString = VirtualRealty.getMessages().leftAvailablePlot;

                Plot lastPlot = enteredPlot.get(player).getKey();
                UUID ownerId = lastPlot.getOwnedBy();
                if (ownerId != null) {
                    String ownerName = PlayerLookupUtil.getBestName(ownerId);
                    if (ownerName == null || ownerName.isEmpty()) {
                        ownerName = "Unknown";
                    }

                    leavePlotString = VirtualRealty.getMessages().leftOwnedPlot
                            .replaceAll("%owner%", ownerName)
                            .replaceAll("%plot_id%", String.valueOf(lastPlot.getID()));

                    if (VirtualRealty.getPluginConfiguration().enablePlotGamemode) {
                        if (lastPlot.hasMembershipAccess(player.getUniqueId())) {
                            player.setGameMode(Bukkit.getServer().getDefaultGameMode());
                        }
                    }
                }

                if (!VirtualRealty.getInstance().getServer().getBukkitVersion().startsWith("1.8")) {
                    if (VirtualRealty.getPluginConfiguration().plotSound) {
                        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_TRAPDOOR_CLOSE, 0.3f, 1f);
                    }
                    if (lastPlot.getPlotSize() == PlotSize.AREA) {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText(VirtualRealty.getMessages().leftProtectedArea));
                    } else {
                        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                                TextComponent.fromLegacyText(leavePlotString));
                    }
                }

                enteredPlot.remove(player);
                return;
            }

            enteredPlot.replace(player, new AbstractMap.SimpleEntry<>(enteredPlot.get(player).getKey(), false));
        }
    }

}
