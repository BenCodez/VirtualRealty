package com.modnmetl.virtualrealty.listener.stake;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.listener.VirtualListener;
import com.modnmetl.virtualrealty.model.plot.PlotItem;
import com.modnmetl.virtualrealty.model.region.GridStructure;

import de.tr7zw.changeme.nbtapi.NBT;

public class DraftListener extends VirtualListener {

	public static final HashMap<Player, Map.Entry<GridStructure, Map.Entry<PlotItem, PlotItem>>> DRAFT_MAP = new HashMap<>();

	public DraftListener(VirtualRealty plugin) {
		super(plugin);
	}

	@EventHandler
	public void onInventoryClick(InventoryClickEvent e) {
		Player player = ((Player) e.getWhoClicked());
		if (!DRAFT_MAP.containsKey(player))
			return;
		e.setCancelled(true);
	}

	@EventHandler
	public void onInventoryClick(InventoryCreativeEvent e) {
		Player player = ((Player) e.getWhoClicked());
		if (!DRAFT_MAP.containsKey(player))
			return;
		e.setCancelled(true);
	}

	@EventHandler
	public void onItemDrop(PlayerDropItemEvent e) {
		Player player = e.getPlayer();
		if (!DRAFT_MAP.containsKey(player))
			return;
		e.setCancelled(true);
	}

	@EventHandler
	public void onSlotSwitch(PlayerItemHeldEvent e) {
		Player player = e.getPlayer();
		if (!DRAFT_MAP.containsKey(player))
			return;
		e.setCancelled(true);
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent e) {
		Player player = e.getEntity();
		if (!DRAFT_MAP.containsKey(player))
			return;
		e.getDrops().remove(DRAFT_MAP.get(player).getValue().getValue().getItemStack());
		e.getDrops().add(DRAFT_MAP.get(player).getValue().getKey().getItemStack());
		player.getInventory().remove(DRAFT_MAP.get(player).getValue().getValue().getItemStack());
		player.getInventory().addItem(DRAFT_MAP.get(player).getValue().getKey().getItemStack());
		DRAFT_MAP.get(player).getKey().removeGrid();
		DRAFT_MAP.remove(player);
		player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().claimModeDisabledDueToDeath);
	}

	@EventHandler
	public void onBlockPlace(BlockPlaceEvent e) {
		Player player = e.getPlayer();
		ItemStack itemInHand = player.getInventory().getItemInMainHand();
		if (itemInHand == null || itemInHand.getType() == Material.AIR)
			return;
		boolean hasItemKey = NBT.get(itemInHand, nbt -> {
			return nbt.hasTag("vrplot_item");
		});
		if (itemInHand.getType() == (Material.PLAYER_HEAD) && hasItemKey) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent e) {
		Player player = e.getPlayer();
		if (!DRAFT_MAP.containsKey(player))
			return;
		player.getInventory().remove(DRAFT_MAP.get(player).getValue().getValue().getItemStack());
		player.getInventory().addItem(DRAFT_MAP.get(player).getValue().getKey().getItemStack());
		DRAFT_MAP.get(player).getKey().removeGrid();
		DRAFT_MAP.remove(player);
	}

}
