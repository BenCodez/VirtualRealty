package com.modnmetl.virtualrealty.listener.premium;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.listener.VirtualListener;
import com.modnmetl.virtualrealty.model.other.ChatMessage;
import com.modnmetl.virtualrealty.model.other.PanelType;
import com.modnmetl.virtualrealty.model.permission.ManagementPermission;
import com.modnmetl.virtualrealty.model.permission.RegionPermission;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.plot.PlotMember;
import com.modnmetl.virtualrealty.model.region.GridStructure;
import com.modnmetl.virtualrealty.util.PanelUtil;
import com.modnmetl.virtualrealty.util.PlayerLookupUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Objects;
import java.util.UUID;

public class PanelListener extends VirtualListener {

    public PanelListener(VirtualRealty plugin) {
        super(plugin);
    }

    @EventHandler
    public void onPanelClicked(InventoryClickEvent e) {
        UUID uuid = e.getWhoClicked().getUniqueId();
        if (!PanelUtil.SELECTED_PANEL.containsKey(uuid)) return;

        e.setCancelled(true);
        Player player = (Player) e.getWhoClicked();

        if (PanelUtil.SELECTED_PANEL.get(player.getUniqueId()) != PanelType.PLOTS) {
            if (e.getInventory().getSize() - 9 == e.getSlot()) {
                PanelUtil.openPreviousPage(player);
                return;
            }
        }

        switch (PanelUtil.SELECTED_PANEL.get(uuid)) {

            case PLOTS: {
                if (PanelUtil.SELECTED_PLOT_PAGE.containsKey(player.getUniqueId())) {
                    int currentPageIndex = PanelUtil.SELECTED_PLOT_PAGE.get(player.getUniqueId());

                    if (e.getSlot() == 8) {
                        if (currentPageIndex - 1 > 0) {
                            PanelUtil.openPlotsPage(player, currentPageIndex - 1);
                        }
                    }

                    if (e.getSlot() == 44) {
                        VirtualRealty.debug(PanelUtil.hasNextPlotsPage(player, currentPageIndex) + " ");
                        if (PanelUtil.hasNextPlotsPage(player, currentPageIndex)) {
                            PanelUtil.openPlotsPage(player, currentPageIndex + 1);
                        }
                    }

                    if (e.getSlot() > 9 && e.getSlot() < 35) {
                        Plot plot = PanelUtil.getPlot(e.getCurrentItem());
                        if (plot != null) {
                            PanelUtil.openPlotPage(player, plot);
                        }
                    }
                }
                break;
            }

            case PLOT: {
                if (PanelUtil.SELECTED_PLOT.containsKey(player.getUniqueId())) {
                    Plot plot = PanelUtil.SELECTED_PLOT.get(player.getUniqueId());

                    if (e.getSlot() == 15) {
                        // TELEPORT
                        player.closeInventory();
                        plot.teleportPlayer(player);
                        ChatMessage.of(VirtualRealty.getMessages().teleportedToPlot).sendWithPrefix(player);
                        return;
                    }

                    if (plot.isOwnershipExpired()) {
                        ChatMessage.of(VirtualRealty.getMessages().ownershipExpired).sendWithPrefix(player);
                        return;
                    }

                    if (e.getSlot() == 11) {
                        if (plot.getMembers().isEmpty()) {
                            ChatMessage.of(VirtualRealty.getMessages().noPlotMembers).sendWithPrefix(player);
                            return;
                        }
                        PanelUtil.openMembersPage(player, plot, 1);
                    }

                    if (e.getSlot() == 12) {
                        // SETTINGS
                        PanelUtil.openPlotSettings(player, plot);
                    }

                    if (e.getSlot() == 14) {
                        // VISUAL BOUNDARY
                        if (GridStructure.isCuboidGridDisplaying(player, plot.getID())) {
                            ChatMessage.of(VirtualRealty.getMessages().visualBoundaryActive).sendWithPrefix(player);
                            return;
                        }

                        player.closeInventory();
                        ChatMessage.of(VirtualRealty.getMessages().visualBoundaryDisplayed).sendWithPrefix(player);

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                GridStructure previewStructure = new GridStructure(
                                        player,
                                        plot.getLength(),
                                        plot.getHeight(),
                                        plot.getWidth(),
                                        plot.getID(),
                                        plot.getCreatedWorld(),
                                        GridStructure.DISPLAY_TICKS,
                                        plot.getCreatedLocation()
                                );
                                previewStructure.preview(true, false);
                            }
                        }.runTaskLater(VirtualRealty.getInstance(), 10);
                    }
                }
                break;
            }

            case MEMBERS: {
                if (PanelUtil.SELECTED_PLOT.containsKey(player.getUniqueId())) {
                    Plot plot = PanelUtil.SELECTED_PLOT.get(player.getUniqueId());
                    int currentPageIndex = PanelUtil.SELECTED_MEMBERS_PAGE.get(player.getUniqueId());
                    OfflinePlayer member = PanelUtil.getMember(e.getCurrentItem());

                    if (member != null) {
                        PanelUtil.openMember(player, plot, currentPageIndex, member);
                        return;
                    }

                    if (e.getSlot() == 8) {
                        if (currentPageIndex - 1 > 0) {
                            PanelUtil.openMembersPage(player, plot, currentPageIndex - 1);
                            return;
                        }
                    }

                    if (e.getSlot() == 44) {
                        if (PanelUtil.hasNextMembersPage(plot, currentPageIndex)) {
                            PanelUtil.openMembersPage(player, plot, currentPageIndex + 1);
                            return;
                        }
                    }
                }
                break;
            }

            case PLOT_SETTINGS: {
                if (PanelUtil.SELECTED_PLOT.containsKey(player.getUniqueId())) {
                    Plot plot = PanelUtil.SELECTED_PLOT.get(player.getUniqueId());
                    RegionPermission plotPermission = RegionPermission.getPermission(e.getSlot() - 10);
                    PlotMember plotMember = plot.getMember(player.getUniqueId());

                    if (!Objects.nonNull(plotPermission)) return;

                    if (plotMember != null) {
                        if (!plotMember.hasManagementPermission(ManagementPermission.PLOT_PERMISSIONS)) {
                            ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(player);
                            return;
                        }
                    }

                    plot.togglePermission(plotPermission);
                    String message = (plot.hasPermission(plotPermission) ? "§a" : "§c")
                            + plotPermission.getName() + " has been "
                            + (plot.hasPermission(plotPermission) ? "enabled" : "disabled")
                            + " for non-member users.";
                    ChatMessage.of(message).sendWithPrefix(player);
                    PanelUtil.openPlotSettings(player, plot);
                    plot.update();
                }
                break;
            }

            case MEMBER: {
                if (PanelUtil.SELECTED_PLOT.containsKey(player.getUniqueId())) {
                    Plot plot = PanelUtil.SELECTED_PLOT.get(player.getUniqueId());
                    int membersPageIndex = PanelUtil.SELECTED_MEMBERS_PAGE.get(player.getUniqueId());
                    OfflinePlayer offlinePlayer = PanelUtil.SELECTED_MEMBER.get(player.getUniqueId());

                    if (e.getSlot() == 12) {
                        // Management Permissions
                        PanelUtil.openMemberManagementPermissions(player, plot, membersPageIndex, offlinePlayer);
                    }

                    if (e.getSlot() == 14) {
                        // Plot Permissions
                        PanelUtil.openMemberPlotPermissions(player, plot, membersPageIndex, offlinePlayer);
                    }

                    if (e.getSlot() == 26) {
                        // Kick Member
                        PlotMember plotMember = plot.getMember(player.getUniqueId());
                        if (plotMember != null) {
                            if (!plotMember.hasManagementPermission(ManagementPermission.KICK_MEMBER)) {
                                ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(player);
                                return;
                            }
                        }

                        plot.removeMember(plot.getMember(offlinePlayer.getUniqueId()));

                        String displayName = PlayerLookupUtil.getBestName(offlinePlayer);
                        if (displayName == null || displayName.isEmpty()) {
                            UUID targetId = offlinePlayer.getUniqueId();
                            displayName = (targetId != null)
                                    ? targetId.toString().substring(0, 8)
                                    : "Unknown";
                        }

                        ChatMessage.of(
                                VirtualRealty.getMessages().playerKick
                                        .replaceAll("%player%", displayName)
                        ).sendWithPrefix(player);

                        if (PanelUtil.hasNextMembersPage(plot, membersPageIndex - 1)) {
                            PanelUtil.openMembersPage(player, plot, membersPageIndex);
                            return;
                        }

                        if (plot.getMembers().isEmpty()) {
                            PanelUtil.openPlotPage(player, plot);
                            return;
                        }

                        PanelUtil.openMembersPage(player, plot, membersPageIndex - 1);
                    }
                }
                break;
            }

            case PLOT_PERMISSIONS: {
                if (PanelUtil.SELECTED_PLOT.containsKey(player.getUniqueId())) {
                    Plot plot = PanelUtil.SELECTED_PLOT.get(player.getUniqueId());
                    int membersPageIndex = PanelUtil.SELECTED_MEMBERS_PAGE.get(player.getUniqueId());
                    OfflinePlayer offlinePlayer = PanelUtil.SELECTED_MEMBER.get(player.getUniqueId());
                    PlotMember plotMember = plot.getMember(offlinePlayer.getUniqueId());
                    RegionPermission plotPermission = RegionPermission.getPermission(e.getSlot() - 10);
                    PlotMember panelMember = plot.getMember(player.getUniqueId());

                    if (panelMember != null) {
                        if (!panelMember.hasManagementPermission(ManagementPermission.PLOT_PERMISSIONS)) {
                            ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(player);
                            return;
                        }
                    }

                    if (!Objects.nonNull(plotPermission)) return;

                    plotMember.togglePermission(plotPermission);

                    String targetName = PlayerLookupUtil.getBestName(offlinePlayer);
                    if (targetName == null || targetName.isEmpty()) {
                        UUID targetId = offlinePlayer.getUniqueId();
                        targetName = (targetId != null)
                                ? targetId.toString().substring(0, 8)
                                : "Unknown";
                    }

                    String message = (plotMember.hasPermission(plotPermission) ? "§a" : "§c")
                            + plotPermission.getName() + " has been "
                            + (plotMember.hasPermission(plotPermission) ? "enabled" : "disabled")
                            + " for " + targetName;
                    ChatMessage.of(message).sendWithPrefix(player);
                    PanelUtil.openMemberPlotPermissions(player, plot, membersPageIndex, offlinePlayer);
                    plotMember.update();
                }
                break;
            }

            case MANAGEMENT_PERMISSIONS: {
                if (PanelUtil.SELECTED_PLOT.containsKey(player.getUniqueId())) {
                    Plot plot = PanelUtil.SELECTED_PLOT.get(player.getUniqueId());
                    int membersPageIndex = PanelUtil.SELECTED_MEMBERS_PAGE.get(player.getUniqueId());
                    OfflinePlayer offlinePlayer = PanelUtil.SELECTED_MEMBER.get(player.getUniqueId());
                    PlotMember plotMember = plot.getMember(offlinePlayer.getUniqueId());
                    ManagementPermission managementPermission = ManagementPermission.getPermission(e.getSlot() - 12);

                    if (!plot.getPlotOwner().getUniqueId().equals(player.getUniqueId())) {
                        ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(player);
                        return;
                    }

                    if (!Objects.nonNull(managementPermission)) return;

                    plotMember.toggleManagementPermission(managementPermission);

                    String targetName = PlayerLookupUtil.getBestName(offlinePlayer);
                    if (targetName == null || targetName.isEmpty()) {
                        UUID targetId = offlinePlayer.getUniqueId();
                        targetName = (targetId != null)
                                ? targetId.toString().substring(0, 8)
                                : "Unknown";
                    }

                    String message = (plotMember.hasManagementPermission(managementPermission) ? "§a" : "§c")
                            + managementPermission.getName() + " has been "
                            + (plotMember.hasManagementPermission(managementPermission) ? "enabled" : "disabled")
                            + " for " + targetName;
                    ChatMessage.of(message).sendWithPrefix(player);
                    PanelUtil.openMemberManagementPermissions(player, plot, membersPageIndex, offlinePlayer);
                    plotMember.update();
                }
                break;
            }
        }
    }

    @EventHandler
    public void onPanelLeave(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        PanelUtil.SELECTED_PANEL.remove(uuid);
        PanelUtil.SELECTED_PLOT_PAGE.remove(uuid);
        PanelUtil.SELECTED_PLOT.remove(uuid);
        PanelUtil.SELECTED_MEMBER.remove(uuid);
    }

}
