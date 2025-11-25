package com.modnmetl.virtualrealty.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.other.PanelType;
import com.modnmetl.virtualrealty.model.permission.ManagementPermission;
import com.modnmetl.virtualrealty.model.permission.RegionPermission;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.plot.PlotMember;
import com.modnmetl.virtualrealty.util.data.ItemBuilder;
import com.modnmetl.virtualrealty.util.data.SkullUtil;

import de.tr7zw.changeme.nbtapi.NBT;

public final class PanelUtil {

    public static final HashMap<UUID, Integer> SELECTED_PLOT_PAGE = new HashMap<>();
    public static final HashMap<UUID, Integer> SELECTED_MEMBERS_PAGE = new HashMap<>();
    public static final HashMap<UUID, PanelType> SELECTED_PANEL = new HashMap<>();
    public static final HashMap<UUID, Plot> SELECTED_PLOT = new HashMap<>();
    public static final HashMap<UUID, OfflinePlayer> SELECTED_MEMBER = new HashMap<>();

    public static final ItemStack blank = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE, 1, (byte) 0)
            .setName(" ")
            .toItemStack();
    public static final ItemStack lightBlank = new ItemBuilder(Material.LIGHT_GRAY_STAINED_GLASS_PANE, 1, (byte) 0)
            .setName(" ")
            .toItemStack();
    public static final ItemStack pageUp = new ItemBuilder(
            SkullUtil.getSkull("3040fe836a6c2fbd2c7a9c8ec6be5174fddf1ac20f55e366156fa5f712e10"))
            .setName("§7↑ Previous Page")
            .toItemStack();
    public static final ItemStack pageDown = new ItemBuilder(
            SkullUtil.getSkull("7437346d8bda78d525d19f540a95e4e79daeda795cbc5a13256236312cf"))
            .setName("§7↓ Next Page")
            .toItemStack();
    public static final ItemStack goBackward = new ItemBuilder(Material.REPEATER)
            .setName("§c← Go Back")
            .toItemStack();

    private PanelUtil() {
    }

    public static void openPlotsPage(Player player, int page) {
        if (page < 1)
            return;

        Plot[] plots = PlotManager.getInstance().getAccessPlots(player.getUniqueId()).values().stream()
                .sorted(Comparator.comparingInt(Plot::getID))
                .skip(21L * (page - 1))
                .limit(21)
                .toArray(Plot[]::new);

        int[] plotSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        Inventory inventory = Bukkit.createInventory(
                null,
                45,
                PanelType.PLOTS.getInventoryName() + (hasNextPlotsPage(player, 1) ? " | Page " + page : "")
        );

        setBlank(inventory);
        setLightBlank(inventory);

        for (int slot = 0; slot < plots.length; slot++) {
            Plot plot = plots[slot];
            inventory.setItem(plotSlots[slot], getPlotItem(plot, player));
        }

        if (hasNextPlotsPage(player, page)) {
            inventory.setItem(44, pageDown);
        }
        if (page > 1) {
            inventory.setItem(8, pageUp);
        }

        player.openInventory(inventory);
        SELECTED_PANEL.put(player.getUniqueId(), PanelType.PLOTS);
        SELECTED_PLOT_PAGE.put(player.getUniqueId(), page);
    }

    public static void openPlotPage(Player player, Plot plot) {
        Inventory inventory = Bukkit.createInventory(player, 27, PanelType.PLOT.getInventoryName());

        ItemStack membersItem = new ItemBuilder(
                SkullUtil.getSkull("b8e302cd0531be62a16757222b55202231ed40e2b5c2907ce7914a321b5287c2"))
                .setName("§2Members")
                .toItemStack();

        ItemStack settingsItem = new ItemBuilder(Material.REDSTONE)
                .setName("§2Non-Member Permissions")
                .toItemStack();

        ItemStack visualizationItem = new ItemBuilder(
                SkullUtil.getSkull("775bca1af5cb1557c1794d23d907df1159320e14eba0581c25187dbeb22ba2cc"))
                .setName("§2Visual Boundary")
                .toItemStack();

        ItemStack teleportItem = new ItemBuilder(Material.ENDER_PEARL)
                .setName("§2Teleport to the Plot")
                .toItemStack();

        setBlank(inventory);

        inventory.setItem(0, getPlotItem(plot, player));
        inventory.setItem(11, membersItem);
        inventory.setItem(12, settingsItem);
        inventory.setItem(14, visualizationItem);
        inventory.setItem(15, teleportItem);
        inventory.setItem(PanelType.PLOT.getBackwardsButtonIndex(), goBackward);

        player.openInventory(inventory);
        SELECTED_PANEL.put(player.getUniqueId(), PanelType.PLOT);
        SELECTED_PLOT.put(player.getUniqueId(), plot);
    }

    public static void openMembersPage(Player player, Plot plot, int pageIndex) {
        if (pageIndex < 1)
            return;

        // Sort by safe display name (works for Bedrock / Floodgate too)
        OfflinePlayer[] members = plot.getPlayerMembers().stream()
                .sorted(Comparator.comparing(p -> {
                    String name = PlayerLookupUtil.getBestName(p);
                    return name == null ? "" : name.toLowerCase();
                }))
                .skip(21L * (pageIndex - 1))
                .limit(21)
                .toArray(OfflinePlayer[]::new);

        int[] memberSlots = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        Inventory inventory = Bukkit.createInventory(
                null,
                45,
                PanelType.MEMBERS.getInventoryName() + (hasNextMembersPage(plot, 1) ? " | Page " + pageIndex : "")
        );

        setBlank(inventory);
        setLightBlank(inventory);

        inventory.setItem(0, getPlotItem(plot, player));

        for (int slot = 0; slot < members.length; slot++) {
            OfflinePlayer member = members[slot];
            ItemBuilder memberItemBuilder = getMemberItem(member);
            ItemStack memberItem = memberItemBuilder.toItemStack();

            // Store UUID on the item for later lookup (use original NBT-API style)
            NBT.modify(memberItem, nbt -> {
                nbt.setString("vrplot_member_uuid", member.getUniqueId().toString());
            });

            inventory.setItem(memberSlots[slot], memberItem);
        }

        if (hasNextMembersPage(plot, pageIndex)) {
            inventory.setItem(44, pageDown);
        }
        if (pageIndex > 1) {
            inventory.setItem(8, pageUp);
        }

        inventory.setItem(PanelType.MEMBERS.getBackwardsButtonIndex(), goBackward);

        player.openInventory(inventory);
        SELECTED_PANEL.put(player.getUniqueId(), PanelType.MEMBERS);
        SELECTED_MEMBERS_PAGE.put(player.getUniqueId(), pageIndex);
        SELECTED_PLOT.put(player.getUniqueId(), plot);
    }

    public static void openPlotSettings(Player player, Plot plot) {
        int invSize = RegionPermission.values().length / 7
                + (RegionPermission.values().length % 7 != 0 ? 1 : 0);
        Inventory inventory = Bukkit.createInventory(player, (invSize * 9) + (2 * 9),
                PanelType.PLOT_SETTINGS.getInventoryName());

        setBlank(inventory);
        inventory.setItem(0, getPlotItem(plot, player));

        int currentSlot = 0;
        int endings = 0;

        for (RegionPermission permission : RegionPermission.values()) {
            ItemBuilder itemBuilder = permission.getItem();
            itemBuilder.setName((plot.hasPermission(permission) ? "§a" : "§c") + permission.getName());

            if ((currentSlot - endings) % 7 == 0 && currentSlot != 0) {
                currentSlot += 2;
                endings += 2;
            }

            inventory.setItem(currentSlot + 10, itemBuilder.toItemStack());
            currentSlot++;
        }

        inventory.setItem(inventory.getSize() - 9, goBackward);

        player.openInventory(inventory);
        SELECTED_PANEL.put(player.getUniqueId(), PanelType.PLOT_SETTINGS);
        SELECTED_PLOT.put(player.getUniqueId(), plot);
    }

    public static void openMember(Player player, Plot plot, int pageIndex, OfflinePlayer member) {
        if (pageIndex < 1)
            return;

        Inventory inventory = Bukkit.createInventory(player, 27, PanelType.MEMBER.getInventoryName());

        setBlank(inventory);
        inventory.setItem(0, getPlotItem(plot, player));
        inventory.setItem(8, getMemberItem(member).toItemStack());

        inventory.setItem(12, new ItemBuilder(Material.BOOK)
                .setName("§2Management Permissions")
                .toItemStack());

        inventory.setItem(14, new ItemBuilder(Material.MAP)
                .setName("§2Plot Permissions")
                .setLore(new ArrayList<>())
                .toItemStack());

        inventory.setItem(26, new ItemBuilder(Material.OAK_DOOR)
                .setName("§cKick Member")
                .setLore(new ArrayList<>())
                .toItemStack());

        inventory.setItem(PanelType.MEMBER.getBackwardsButtonIndex(), goBackward);

        player.openInventory(inventory);
        SELECTED_PANEL.put(player.getUniqueId(), PanelType.MEMBER);
        SELECTED_MEMBER.put(player.getUniqueId(), member);
        SELECTED_MEMBERS_PAGE.put(player.getUniqueId(), pageIndex);
        SELECTED_PLOT.put(player.getUniqueId(), plot);
    }

    public static void openMemberPlotPermissions(Player player, Plot plot, int pageIndex, OfflinePlayer member) {
        if (pageIndex < 1)
            return;

        PlotMember plotMember = plot.getMember(member.getUniqueId());

        int invSize = RegionPermission.values().length / 7
                + (RegionPermission.values().length % 7 != 0 ? 1 : 0);

        Inventory inventory = Bukkit.createInventory(player, (invSize * 9) + (2 * 9),
                PanelType.PLOT_PERMISSIONS.getInventoryName());

        setBlank(inventory);

        inventory.setItem(0, getPlotItem(plot, player));
        inventory.setItem(8, getMemberItem(member).toItemStack());

        int currentSlot = 0;
        int endings = 0;

        for (RegionPermission permission : RegionPermission.values()) {
            ItemBuilder itemBuilder = permission.getItem();
            itemBuilder.setName((plotMember.hasPermission(permission) ? "§a" : "§c") + permission.getName());

            if ((currentSlot - endings) % 7 == 0 && currentSlot != 0) {
                currentSlot += 2;
                endings += 2;
            }

            inventory.setItem(currentSlot + 10, itemBuilder.toItemStack());
            currentSlot++;
        }

        inventory.setItem(inventory.getSize() - 9, goBackward);

        player.openInventory(inventory);
        SELECTED_PANEL.put(player.getUniqueId(), PanelType.PLOT_PERMISSIONS);
        SELECTED_MEMBER.put(player.getUniqueId(), member);
        SELECTED_MEMBERS_PAGE.put(player.getUniqueId(), pageIndex);
        SELECTED_PLOT.put(player.getUniqueId(), plot);
    }

    public static void openMemberManagementPermissions(Player player, Plot plot, int pageIndex, OfflinePlayer member) {
        if (pageIndex < 1)
            return;

        PlotMember plotMember = plot.getMember(member.getUniqueId());
        Inventory inventory = Bukkit.createInventory(player, 27,
                PanelType.MANAGEMENT_PERMISSIONS.getInventoryName());

        setBlank(inventory);

        inventory.setItem(0, getPlotItem(plot, player));
        inventory.setItem(8, getMemberItem(member).toItemStack());

        for (int i = 0; i < ManagementPermission.values().length; i++) {
            ManagementPermission managementPermission = ManagementPermission.values()[i];
            ItemBuilder itemBuilder = managementPermission.getItem();
            itemBuilder.setName((plotMember.hasManagementPermission(managementPermission) ? "§a" : "§c")
                    + managementPermission.getName());
            inventory.setItem(i + 12, itemBuilder.toItemStack());
        }

        inventory.setItem(PanelType.MANAGEMENT_PERMISSIONS.getBackwardsButtonIndex(), goBackward);

        player.openInventory(inventory);
        SELECTED_PANEL.put(player.getUniqueId(), PanelType.MANAGEMENT_PERMISSIONS);
        SELECTED_MEMBER.put(player.getUniqueId(), member);
        SELECTED_MEMBERS_PAGE.put(player.getUniqueId(), pageIndex);
        SELECTED_PLOT.put(player.getUniqueId(), plot);
    }

    public static void openPreviousPage(Player player) {
        UUID uuid = player.getUniqueId();
        switch (SELECTED_PANEL.get(uuid)) {
            case PLOT: {
                openPlotsPage(player, 1);
                break;
            }
            case MEMBERS:
            case PLOT_SETTINGS: {
                openPlotPage(player, SELECTED_PLOT.get(uuid));
                break;
            }
            case MEMBER: {
                openMembersPage(player, SELECTED_PLOT.get(uuid), SELECTED_MEMBERS_PAGE.get(uuid));
                break;
            }
            case PLOT_PERMISSIONS:
            case MANAGEMENT_PERMISSIONS: {
                openMember(player, SELECTED_PLOT.get(uuid),
                        SELECTED_MEMBERS_PAGE.get(uuid), SELECTED_MEMBER.get(uuid));
                break;
            }
            default: {
                break;
            }
        }
    }

    public static Plot getPlot(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR)
            return null;

        boolean hasTag = NBT.get(itemStack, nbt -> {
            return nbt.hasTag("vrplot_plot_id");
        });

        if (hasTag) {
            Integer plotId = NBT.get(itemStack, nbt -> {
                return nbt.getInteger("vrplot_plot_id");
            });
            return PlotManager.getInstance().getPlot(plotId);
        }
        return null;
    }

    public static ItemBuilder getMemberItem(OfflinePlayer member) {
        String lastPlayed = (member.isOnline() ? "§a" : "§c")
                + (member.getLastPlayed() == 0
                ? "Playing"
                : Plot.SHORT_PLOT_DATE_FORMAT.format(
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(member.getLastPlayed()),
                        ZoneId.systemDefault()
                )
        ));

        String displayName = PlayerLookupUtil.getBestName(member);
        if (displayName == null || displayName.isEmpty()) {
            UUID id = member.getUniqueId();
            displayName = id == null ? "Unknown" : id.toString().substring(0, 8);
        }

        // Skull owner name – try real Bukkit name first, then fallback to displayName
        String skullOwnerName = member.getName();
        if (skullOwnerName == null || skullOwnerName.isEmpty()) {
            skullOwnerName = displayName;
        }

        return new ItemBuilder(Material.PLAYER_HEAD, 1, (byte) 3)
                .setName((member.isOnline() ? "§a" : "§c") + displayName)
                .addLoreLine(" §8┏ §fOnline: " + (member.isOnline() ? "§a✔" : "§c✘"))
                .addLoreLine(" §8┗ §fLast played: §a" + lastPlayed)
                .setSkullOwner(skullOwnerName);
    }

    public static ItemStack getPlotItem(Plot plot, Player player) {
        LocalDateTime ownedUntilDate = plot.getOwnedUntilDate();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        char color;
        String skullURL;

        int daysUntilExpirationThreshold = VirtualRealty.getPluginConfiguration().daysUntilExpirationThreshold;

        if (plot.isOwnershipExpired()) {
            color = 'c';
            skullURL = "fbeb2546564af4df7f7f589423f68102dea69cd4466b0583c474e5ac693b2b99";
        } else {
            LocalDateTime now = LocalDateTime.now();
            long daysUntilExpiration = ChronoUnit.DAYS.between(now, ownedUntilDate);

            if (daysUntilExpiration <= daysUntilExpirationThreshold) {
                color = 'e';
                skullURL = "66245fb397b7c2b3a36e2a24d496be258f1cdf41054f99e9c65e1a673add7b4";
            } else {
                color = 'a';
                skullURL = "16bb9fb97ba87cb727cd0ff477f769370bea19ccbfafb581629cd5639f2fec2b";
            }
        }

        ItemStack plotItemStack = new ItemBuilder(SkullUtil.getSkull(skullURL))
                .setName("§" + color + "Plot")
                .addLoreLine(" §8┏ §fID: §8#§7" + plot.getID())
                .addLoreLine(" §8┣ §fMembership: §" + color
                        + (plot.getPlotOwner().getUniqueId().equals(player.getUniqueId()) ? "OWNER" : "MEMBER"))
                .addLoreLine(" §8┣ §fSize: §" + color + plot.getPlotSize().name())
                .addLoreLine(" §8┣ §fCreated: §" + color + plot.getCreatedAt().format(Plot.SHORT_PLOT_DATE_FORMAT))
                .addLoreLine(" §8┗ §fOwned Until: §" + color + dateTimeFormatter.format(ownedUntilDate))
                .toItemStack();

        // Original NBT-API style
        NBT.modify(plotItemStack, nbt -> {
            nbt.setInteger("vrplot_plot_id", plot.getID());
        });

        return plotItemStack;
    }

    public static OfflinePlayer getMember(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() == Material.AIR)
            return null;

        boolean hasMemberUuid = NBT.get(itemStack, nbt -> {
            return nbt.hasTag("vrplot_member_uuid");
        });

        if (hasMemberUuid) {
            String memberUuid = NBT.get(itemStack, nbt -> {
                return nbt.getString("vrplot_member_uuid");
            });
            return Bukkit.getOfflinePlayer(UUID.fromString(memberUuid));
        }
        return null;
    }

    private static void setLightBlank(Inventory inventory, int range) {
        int[] dataSlot = new int[]{
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        for (int i = 0; i < range && i < dataSlot.length; i++) {
            inventory.setItem(dataSlot[i], lightBlank);
        }
    }

    private static void setLightBlank(Inventory inventory) {
        setLightBlank(inventory, 21);
    }

    private static void setBlank(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, blank);
        }
    }

    public static boolean hasNextPlotsPage(Player player, int page) {
        HashMap<Integer, Plot> plots = PlotManager.getInstance().getAccessPlots(player.getUniqueId());
        int length = plots.values().stream().skip(21L * page).toArray().length;
        return length > 0;
    }

    public static boolean hasNextMembersPage(Plot plot, int page) {
        return plot.getMembers().stream().skip(21L * page).toArray().length > 0;
    }

}
