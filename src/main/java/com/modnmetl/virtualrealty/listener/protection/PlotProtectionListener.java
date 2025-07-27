package com.modnmetl.virtualrealty.listener.protection;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Objects;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Switch;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Creature;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.permissions.Permission;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.listener.VirtualListener;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.material.InteractMaterial;
import com.modnmetl.virtualrealty.model.material.StorageMaterial;
import com.modnmetl.virtualrealty.model.material.SwitchMaterial;
import com.modnmetl.virtualrealty.model.permission.RegionPermission;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.plot.PlotMember;

public class PlotProtectionListener extends VirtualListener {

	public static final Permission PLOT_BUILD = new Permission("virtualrealty.build.plot");

	public static final LinkedList<Material> INTERACT = new LinkedList<>();
	public static final LinkedList<Material> SWITCHES = new LinkedList<>();
	public static final LinkedList<Material> STORAGES = new LinkedList<>();

	public boolean isDoor(Material type) {
		if (type.name().endsWith("DOOR"))
			return true;
		return false;
	}

	public boolean isInteractable(Material type) {
		if (INTERACT.contains(type)) {
			return true;
		}

		if (type.name().endsWith("PRESSURE_PLATE")) {
			return true;
		}

		if (type.name().endsWith("BUTTON")) {
			return true;
		}

		if (type.name().endsWith("FENCE_GATE")) {
			return true;
		}

		if (type.name().endsWith("TRAPDOOR")) {
			return true;
		}

		return false;

	}

	static {
		for (InteractMaterial value : InteractMaterial.values()) {
			Material material = Material.getMaterial(value.toString());
			if (Objects.nonNull(material))
				INTERACT.add(material);
		}
		for (SwitchMaterial value : SwitchMaterial.values()) {
			Material material = Material.getMaterial(value.toString());
			if (Objects.nonNull(material))
				SWITCHES.add(material);
		}
		for (StorageMaterial value : StorageMaterial.values()) {
			Material material = Material.getMaterial(value.toString());
			if (Objects.nonNull(material))
				STORAGES.add(material);
		}
	}

	public PlotProtectionListener(VirtualRealty plugin) {
		super(plugin);
	}

	@EventHandler
	public void onMobSpawn(CreatureSpawnEvent e) {
		if (e.isCancelled())
			return;
		if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL)
			return;
		Location location = e.getLocation();
		Plot plot = PlotManager.getInstance().getPlot(location);
		if (plot != null) {
			if (VirtualRealty.getPluginConfiguration().disablePlotMobsSpawn) {
				e.setCancelled(true);
			} else if (VirtualRealty.getPluginConfiguration().disablePlotMonsterSpawn) {
				if ((e.getEntity() instanceof Monster) || e.getEntityType() == EntityType.SLIME)
					e.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onCropInteract(PlayerInteractEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		if (e.getAction() != Action.PHYSICAL)
			return;
		if (e.getClickedBlock() == null)
			return;
		if (e.getClickedBlock().getType() != Material.FARMLAND)
			return;
		Plot plot = PlotManager.getInstance().getPlot(e.getClickedBlock().getLocation());
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.BREAK)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantDoAnyDMG);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.BREAK)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantDoAnyDMG);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onBlockInteract(PlayerInteractEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		if (e.getClickedBlock() == null)
			return;
		if (STORAGES.contains(e.getClickedBlock().getType()))
			return;
		if (!(e.getAction() == Action.PHYSICAL || e.getAction() == Action.RIGHT_CLICK_BLOCK))
			return;
		if (player.isSneaking() && e.isBlockInHand())
			return;
		if (!(isInteractable(e.getClickedBlock().getType()) || SWITCHES.contains(e.getClickedBlock().getType())
				|| isDoor(e.getClickedBlock().getType())
				|| e.getClickedBlock().getType().name().endsWith("PRESSURE_PLATE")))
			return;

		if (e.getHand() == EquipmentSlot.OFF_HAND)
			return;
		if (!e.getClickedBlock().getType().isInteractable()
				&& !(e.getClickedBlock().getType().name().endsWith("PRESSURE_PLATE")))
			return;

		Plot plot = PlotManager.getInstance().getPlot(e.getClickedBlock().getLocation());
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		boolean isModernSwitch = e.getClickedBlock().getBlockData() instanceof Switch;
		boolean isLegacySwitch = SWITCHES.contains(e.getClickedBlock().getType());
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (isModernSwitch || isLegacySwitch || e.getClickedBlock().getType().name().endsWith("PRESSURE_PLATE")) {
				if (!plotMember.hasPermission(RegionPermission.SWITCH)) {
					e.setCancelled(true);
					if (!e.getClickedBlock().getType().name().endsWith("PRESSURE_PLATE"))
						player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
				}
				return;
			}
			if (isInteractable(e.getClickedBlock().getType())) {
				if (!plotMember.hasPermission(RegionPermission.ITEM_USE)) {
					e.setCancelled(true);
					player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
					return;
				}
			}
			if (isDoor(e.getClickedBlock().getType())) {
				if (!plotMember.hasPermission(RegionPermission.DOORS)) {
					e.setCancelled(true);
					player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
					return;
				}
			}
		} else {
			if (isModernSwitch || isLegacySwitch || e.getClickedBlock().getType().name().endsWith("PRESSURE_PLATE")) {
				if (!plot.hasPermission(RegionPermission.SWITCH)) {
					e.setCancelled(true);
					if (!e.getClickedBlock().getType().name().endsWith("PRESSURE_PLATE"))
						player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
				}
				return;
			}
			if (isInteractable(e.getClickedBlock().getType())) {
				if (!plot.hasPermission(RegionPermission.ITEM_USE)) {
					e.setCancelled(true);
					player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
					return;
				}
			}
			if (isDoor(e.getClickedBlock().getType())) {
				if (!plot.hasPermission(RegionPermission.DOORS)) {
					e.setCancelled(true);
					player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
					return;
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onChestEvent(PlayerInteractEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		if (e.getClickedBlock() == null)
			return;
		if (!STORAGES.contains(e.getClickedBlock().getType()))
			return;
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
			return;
		if (player.isSneaking() && e.isBlockInHand())
			return;
		Plot plot = PlotManager.getInstance().getPlot(e.getClickedBlock().getLocation());
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.CHEST_ACCESS)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.CHEST_ACCESS)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPotInteract(PlayerInteractEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		if (e.getClickedBlock() == null)
			return;
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
			return;
		Plot plot = PlotManager.getInstance().getPlot(e.getClickedBlock().getLocation());
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (e.getClickedBlock().getType().name().startsWith("POTTED_")) {
				if (!plotMember.hasPermission(RegionPermission.BREAK)) {
					e.setCancelled(true);
					player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
				}
			}
			if (e.getClickedBlock().getType().name().startsWith("FLOWER_POT")) {
				if (!plotMember.hasPermission(RegionPermission.PLACE)) {
					e.setCancelled(true);
					player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
				}
			}
		} else {
			if (e.getClickedBlock().getType().name().startsWith("POTTED_")) {
				if (!plot.hasPermission(RegionPermission.BREAK)) {
					e.setCancelled(true);
					player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
				}
			}
			if (e.getClickedBlock().getType().name().startsWith("FLOWER_POT")) {
				if (!plot.hasPermission(RegionPermission.PLACE)) {
					e.setCancelled(true);
					player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onLiquidPlace(PlayerBucketEmptyEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		Plot plot = PlotManager.getInstance().getPlot(e.getBlockClicked().getLocation(), true);
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.ITEM_USE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.ITEM_USE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onVehicleDestroy(VehicleDestroyEvent e) {
		if (e.isCancelled())
			return;
		if (!(e.getAttacker() instanceof Player))
			return;
		Player player = (Player) e.getAttacker();
		Plot plot = PlotManager.getInstance().getPlot(e.getVehicle().getLocation(), true);
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.ENTITY_DAMAGE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantDoAnyDMG);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.ENTITY_DAMAGE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantDoAnyDMG);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onLiquidTake(PlayerBucketFillEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		Plot plot = PlotManager.getInstance().getPlot(e.getBlockClicked().getLocation(), true);
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.ITEM_USE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.ITEM_USE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onBlockPlace(BlockPlaceEvent e) {
		Player player = e.getPlayer();
		Plot plot = PlotManager.getInstance().getPlot(e.getBlockPlaced().getLocation(), true);
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.PLACE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.PLACE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onBlockBreak(BlockBreakEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		Plot plot = PlotManager.getInstance().getPlot(e.getBlock().getLocation(), true);
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.BREAK)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.BREAK)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onPistonRetract(BlockPistonRetractEvent e) {
		if (e.isCancelled())
			return;
		Block piston = e.getBlock();
		Plot fromPlot = PlotManager.getInstance().getPlot(piston.getLocation());
		e.getBlocks().forEach(block -> {
			Location toLocation = block.getLocation();
			Plot toPlot = PlotManager.getInstance().getPlot(toLocation);
			Plot toBorderedPlot = PlotManager.getInstance().getPlot(toLocation, true);
			if (fromPlot != null) {
				if (toPlot == null)
					e.setCancelled(true);
			} else {
				if (toBorderedPlot != null)
					e.setCancelled(true);
			}
		});
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onBlockMove(BlockPistonExtendEvent e) {
		if (e.isCancelled())
			return;
		for (Block block : e.getBlocks()) {
			Location blockLocation = block.getLocation();
			Plot plot = PlotManager.getInstance().getPlot(blockLocation);
			Plot borderedPlot = PlotManager.getInstance().getPlot(blockLocation, true);
			if (borderedPlot != null) {
				if (plot == null) {
					e.setCancelled(true);
				} else if (plot.getID() != borderedPlot.getID()) {
					e.setCancelled(true);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onIgniteEvent(BlockIgniteEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		if (player == null)
			return;
		if (e.getIgnitingBlock() == null)
			return;
		Plot plot = PlotManager.getInstance().getPlot(e.getIgnitingBlock().getLocation(), true);
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
			} else {
				if (plotMember == null)
					return;
				if (plotMember.hasPermission(RegionPermission.ITEM_USE))
					return;
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.ITEM_USE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		}
	}

	@EventHandler
	public void onBlockIgnite(BlockIgniteEvent e) {
		if (e.isCancelled())
			return;
		if (e.getCause() != BlockIgniteEvent.IgniteCause.SPREAD)
			return;
		Plot fromPlot = PlotManager.getInstance().getPlot(e.getBlock().getLocation());
		Plot toPlot = PlotManager.getInstance().getPlot(e.getIgnitingBlock().getLocation());
		if (toPlot != null && fromPlot == null) {
			e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onTreeGrow(StructureGrowEvent e) {
		if (e.isCancelled())
			return;
		for (BlockState block : new ArrayList<>(e.getBlocks())) {
			Location saplingLocation = e.getLocation();
			Location blockLocation = block.getLocation();
			Plot fromPlot = PlotManager.getInstance().getPlot(saplingLocation);
			Plot toPlot = PlotManager.getInstance().getPlot(blockLocation);
			if (fromPlot != null) {
				if (toPlot != null) {
					if (fromPlot.getID() == toPlot.getID())
						return;
					e.getBlocks().remove(block);
				} else {
					e.getBlocks().remove(block);
				}
			} else if (toPlot != null) {
				e.getBlocks().remove(block);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onDragonEggMove(BlockFromToEvent e) {
		if (e.isCancelled())
			return;
		Material block = e.getBlock().getType();
		if (block == Material.DRAGON_EGG || block == Material.LAVA || block == Material.WATER) {
			Location fromLocation = e.getBlock().getLocation();
			Location toLocation = e.getToBlock().getLocation();
			Plot fromPlot = PlotManager.getInstance().getPlot(fromLocation);
			Plot toPlot = PlotManager.getInstance().getPlot(toLocation);
			if (toPlot != null) {
				if (fromPlot == null) {
					e.setCancelled(true);
				} else if (fromPlot.getID() != toPlot.getID()) {
					e.setCancelled(true);
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onProjectileLaunch(ProjectileLaunchEvent e) {
		if (e.isCancelled())
			return;
		if (e.getEntity().getShooter() == null)
			return;
		if (!(e.getEntity().getShooter() instanceof Player))
			return;
		Player shooter = ((Player) e.getEntity().getShooter());
		Plot plot = PlotManager.getInstance().getPlot(e.getEntity().getLocation());
		if (plot == null)
			return;
		if (hasPermission(shooter, PLOT_BUILD))
			return;
		if (plot.getOwnedBy() != null && plot.getOwnedBy().equals(((Player) e.getEntity().getShooter()).getUniqueId()))
			return;
		if (plot.hasMembershipAccess(shooter.getUniqueId()))
			return;
		e.getEntity().remove();
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onProjectileHit(ProjectileHitEvent e) {
		if (e.getEntity().getShooter() == null)
			return;
		if (!(e.getEntity().getShooter() instanceof Player))
			return;
		Player shooter = ((Player) e.getEntity().getShooter());
		Plot plot = PlotManager.getInstance().getPlot(e.getEntity().getLocation());
		if (plot == null)
			return;
		if (hasPermission(shooter, PLOT_BUILD))
			return;
		if (plot.getOwnedBy() != null && plot.getOwnedBy().equals(((Player) e.getEntity().getShooter()).getUniqueId()))
			return;
		if (plot.hasMembershipAccess(shooter.getUniqueId()))
			return;
		e.getEntity().remove();
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onArmorStandChange(PlayerArmorStandManipulateEvent e) {
		if (e.isCancelled())
			return;
		Plot plot = PlotManager.getInstance().getPlot(e.getPlayer().getLocation());
		Player player = e.getPlayer();
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.ARMOR_STAND)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.ARMOR_STAND)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onItemFrameDamage(HangingBreakByEntityEvent e) {
		if (e.isCancelled())
			return;
		if (!(e.getRemover() instanceof Player))
			return;
		Player player = (Player) e.getRemover();
		Plot plot = PlotManager.getInstance().getPlot(player.getLocation());
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.BREAK)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.BREAK)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onItemFrameRotate(PlayerInteractEntityEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		Plot plot = PlotManager.getInstance().getPlot(player.getLocation());
		if (!(e.getRightClicked() instanceof ItemFrame))
			return;
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.ITEM_USE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.ITEM_USE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantInteract);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	private void onInteract(PlayerInteractEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		if (e.getItem() == null)
			return;
		if (e.getItem().getType() != Material.ARMOR_STAND)
			return;
		if (e.getClickedBlock() == null)
			return;
		Plot plot = PlotManager.getInstance().getPlot(e.getClickedBlock().getLocation());
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.PLACE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.PLACE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onHangingPlace(HangingPlaceEvent e) {
		if (e.isCancelled())
			return;
		Player player = e.getPlayer();
		if (player == null)
			return;
		Plot plot = PlotManager.getInstance().getPlot(e.getEntity().getLocation());
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.PLACE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.PLACE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onEntityDamage(EntityDamageByEntityEvent e) {
		if (e.isCancelled())
			return;
		if (!(e.getDamager() instanceof Player))
			return;
		if (!(e.getEntity() instanceof Creature) && !(e.getEntity() instanceof Player))
			return;
		Player player = (Player) e.getDamager();
		Plot plot = PlotManager.getInstance().getPlot(e.getEntity().getLocation());
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.ENTITY_DAMAGE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantDoAnyDMG);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.ENTITY_DAMAGE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantDoAnyDMG);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onStaticEntityDamage(EntityDamageByEntityEvent e) {
		if (e.isCancelled())
			return;
		if (e.getEntity() instanceof Creature || e.getEntity() instanceof Player)
			return;
		if (!(e.getDamager() instanceof Player))
			return;
		Player player = (Player) e.getDamager();
		Plot plot = PlotManager.getInstance().getPlot(e.getEntity().getLocation());
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.BREAK)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.BREAK)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantBuildHere);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onEntityExplode(EntityExplodeEvent e) {
		for (Block block : new ArrayList<>(e.blockList())) {
			Plot plot = PlotManager.getInstance().getPlot(block.getLocation(), true);
			if (plot != null)
				e.blockList().remove(block);
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onBlockExplode(BlockExplodeEvent e) {
		for (Block block : new ArrayList<>(e.blockList())) {
			Plot plot = PlotManager.getInstance().getPlot(block.getLocation(), true);
			if (plot != null)
				e.blockList().remove(block);
		}
	}

	@EventHandler
	public void onFireSpread(BlockSpreadEvent e) {
		Plot plot = PlotManager.getInstance().getPlot(e.getNewState().getLocation(), true);
		if (!(plot != null
				&& (e.getSource().getType() == Material.FIRE && (e.getSource().getType() == e.getNewState().getType()
						|| e.getNewState().getType() == Material.AIR))))
			return;
		e.setCancelled(true);
	}

	@EventHandler
	public void onFireBurn(BlockBurnEvent e) {

		Plot fromPlot = PlotManager.getInstance().getPlot(e.getIgnitingBlock().getLocation());
		Plot toPlot = PlotManager.getInstance().getPlot(e.getBlock().getLocation());
		if (toPlot == null)
			return;
		if (fromPlot != null) {
			if (toPlot.getID() != fromPlot.getID())
				e.setCancelled(true);
		} else {
			e.setCancelled(true);
		}

	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onEndermanChangeBlock(EntityChangeBlockEvent e) {
		if (e.getEntityType() == EntityType.ENDERMAN) {
			Plot plot = PlotManager.getInstance().getPlot(e.getBlock().getLocation());
			if (plot == null) {
				return;
			} else {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.LOW)
	public void onEntityProjectileDamage(EntityDamageByEntityEvent e) {
		if (e.isCancelled())
			return;
		if (!((e.getDamager() instanceof Projectile) && ((Projectile) e.getDamager()).getShooter() instanceof Player))
			return;
		Player player = ((Player) ((Projectile) e.getDamager()).getShooter());
		Plot plot = PlotManager.getInstance().getPlot(e.getDamager().getLocation(), true);
		if (plot == null)
			return;
		if (hasPermission(player, PLOT_BUILD))
			return;
		if (plot.hasMembershipAccess(player.getUniqueId())) {
			PlotMember plotMember = plot.getMember(player.getUniqueId());
			if (plot.isOwnershipExpired()) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
				return;
			}
			if (plotMember == null)
				return;
			if (!plotMember.hasPermission(RegionPermission.ENTITY_DAMAGE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantDoAnyDMG);
			}
		} else {
			if (!plot.hasPermission(RegionPermission.ENTITY_DAMAGE)) {
				e.setCancelled(true);
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantDoAnyDMG);
			}
		}
	}

	public boolean hasPermission(CommandSender sender, Permission permission) {
		return sender.hasPermission(permission);
	}

}