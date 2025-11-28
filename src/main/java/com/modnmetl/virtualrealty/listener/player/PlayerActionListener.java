package com.modnmetl.virtualrealty.listener.player;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.model.other.ConfirmationType;
import com.modnmetl.virtualrealty.model.math.Direction;
import com.modnmetl.virtualrealty.model.plot.PlotSize;
import com.modnmetl.virtualrealty.model.other.WorldSetting;
import com.modnmetl.virtualrealty.model.other.VItem;
import com.modnmetl.virtualrealty.listener.VirtualListener;
import com.modnmetl.virtualrealty.listener.stake.DraftListener;
import com.modnmetl.virtualrealty.manager.ConfirmationManager;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.other.Confirmation;
import com.modnmetl.virtualrealty.model.plot.PlotItem;
import com.modnmetl.virtualrealty.model.region.Cuboid;
import com.modnmetl.virtualrealty.model.region.GridStructure;
import com.modnmetl.virtualrealty.util.RegionUtil;
import com.modnmetl.virtualrealty.model.other.ChatMessage;
import de.tr7zw.changeme.nbtapi.NBT;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.Map;

public class PlayerActionListener extends VirtualListener {

	public PlayerActionListener(VirtualRealty plugin) {
		super(plugin);
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e) {
		Player player = e.getPlayer();
		if (!player.isOp())
			return;
		if (VirtualRealty.upToDate)
			return;
		new BukkitRunnable() {
			@Override
			public void run() {
				if (VirtualRealty.developmentBuild) {
					player.sendMessage(
							VirtualRealty.PREFIX + "§6You are running a development build of VirtualRealty plugin. §7["
									+ VirtualRealty.getInstance().getDescription().getVersion() + "]");
				} else {
					player.sendMessage(
							VirtualRealty.PREFIX + "§7A new version of VirtualRealty plugin is available. §a["
									+ VirtualRealty.latestVersion + "]");
					TextComponent textComponent = new TextComponent(
							VirtualRealty.PREFIX + "§aDownload the new version of the plugin here!");
					textComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
							new TextComponent[] { new TextComponent("§a§oClick here to download the update!") }));
					textComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL,
							"https://www.spigotmc.org/resources/virtual-realty.95599/"));
					player.spigot().sendMessage(textComponent);
				}
			}
		}.runTaskLater(VirtualRealty.getInstance(), 5);
	}

	@EventHandler
	public void onPlotItemStake(PlayerInteractEvent e) {
		Player player = e.getPlayer();
		if (e.getHand() == EquipmentSlot.OFF_HAND)
			return;
		if (!(e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK))
			return;
		if (!DraftListener.DRAFT_MAP.containsKey(player))
			return;
		e.setCancelled(true);
		GridStructure gridStructure = DraftListener.DRAFT_MAP.get(player).getKey();
		PlotItem plotItem = DraftListener.DRAFT_MAP.get(player).getValue().getKey();
		Cuboid cuboid = RegionUtil.getRegion(gridStructure.getPreviewLocation(),
				Direction.byYaw(gridStructure.getPreviewLocation().getYaw()), plotItem.getLength(),
				plotItem.getHeight(), plotItem.getWidth());
		Plot plot = PlotManager.getInstance().getPlot(gridStructure.getPreviewLocation());
		if (plot != null) {
			if (plotItem.getPlotSize().equals(plot.getPlotSize())) {
				if (((plot.isOwnershipExpired() && plot.getPlotOwner() != null
						&& !plot.getPlotOwner().getUniqueId().equals(player.getUniqueId()))
						|| plot.getPlotOwner() == null)) {
					if (ConfirmationManager.doesConfirmationExist(ConfirmationType.CLAIM, player.getUniqueId())) {
						player.sendMessage(VirtualRealty.PREFIX + "§cYou already have a confirmation to confirm!");
						return;
					}
					for (String s : VirtualRealty.getMessages().claimConfirmation) {
						player.sendMessage(VirtualRealty.PREFIX + s);
					}
					Confirmation confirmation = new Confirmation(ConfirmationType.CLAIM, player, "YES") {
						@Override
						public void success() {
							ItemStack plotItemStack = DraftListener.DRAFT_MAP.get(this.getSender()).getValue()
									.getValue().getItemStack();
							int firstPlotItemStack = this.getSender().getInventory().first(plotItemStack);
							boolean foundItemStack = firstPlotItemStack != -1;
							if (foundItemStack) {
								this.getSender().getInventory().clear(firstPlotItemStack);
							}
							plot.setOwnedBy(this.getSender().getUniqueId());
							plot.setOwnedUntilDate(LocalDateTime.now().plusDays(plotItem.getAdditionalDays()));
							gridStructure.removeGrid();
							DraftListener.DRAFT_MAP.remove(this.getSender());
							ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
									this.getSender().getUniqueId());
							plot.update();
							this.getSender()
									.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().plotClaimed);
						}

						@Override
                        public void failed() {
                            // The draft may already have been cleared (timeout, re-entering stake mode, etc.).
                            if (!DraftListener.DRAFT_MAP.containsKey(this.getSender())) {
                                this.getSender()
                                        .sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().stakeCancelled);
                                ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
                                        this.getSender().getUniqueId());
                                return;
                            }

                            this.getSender().getInventory().removeItem(
                                    DraftListener.DRAFT_MAP.get(this.getSender()).getValue().getValue().getItemStack());
                            this.getSender().getInventory().remove(
                                    DraftListener.DRAFT_MAP.get(this.getSender()).getValue().getValue().getItemStack());
                            this.getSender().getInventory().addItem(
                                    DraftListener.DRAFT_MAP.get(this.getSender()).getValue().getKey().getItemStack());
                            DraftListener.DRAFT_MAP.get(this.getSender()).getKey().removeGrid();
                            DraftListener.DRAFT_MAP.remove(this.getSender());
                            this.getSender()
                                    .sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().stakeCancelled);
                            DraftListener.DRAFT_MAP.remove(this.getSender());
                            ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
                                    this.getSender().getUniqueId());
                        }
						@Override
						public void expiry() {
							ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
									this.getSender().getUniqueId());
						}
					};
					ConfirmationManager.addConfirmation(confirmation);
					return;
				} else if (plot.getPlotOwner() != null
						&& plot.getPlotOwner().getUniqueId().equals(player.getUniqueId())) {
					if (ConfirmationManager.doesConfirmationExist(ConfirmationType.EXTEND, player.getUniqueId())) {
						player.sendMessage(VirtualRealty.PREFIX + "§cYou already have a confirmation to confirm!");
						return;
					}
					for (String s : VirtualRealty.getMessages().extendConfirmation) {
						player.sendMessage(VirtualRealty.PREFIX + s);
					}
					Confirmation confirmation = new Confirmation(ConfirmationType.EXTEND, player, "YES") {
						@Override
						public void success() {
							PlotItem plotItem = DraftListener.DRAFT_MAP.get(this.getSender()).getValue().getKey();
							ItemStack plotItemStack = DraftListener.DRAFT_MAP.get(this.getSender()).getValue()
									.getValue().getItemStack();
							for (ItemStack content : this.getSender().getInventory().getContents()) {
								if (content == null || content.getType() == Material.AIR)
									continue;
								if (PlotItem.getPlotItemUuid(content).equals(PlotItem.getPlotItemUuid(plotItemStack))) {
									this.getSender().getInventory().removeItem(content);
								}
							}
							if (plot.isOwnershipExpired())
								plot.setOwnedUntilDate(LocalDateTime.now().plusDays(plotItem.getAdditionalDays()));
							else
								plot.setOwnedUntilDate(plot.getOwnedUntilDate().plusDays(plotItem.getAdditionalDays()));
							gridStructure.removeGrid();
							DraftListener.DRAFT_MAP.remove(this.getSender());
							ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
									this.getSender().getUniqueId());
							plot.update();
							this.getSender()
									.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().leaseExtended
											.replaceAll("%plot_id%", String.valueOf(plot.getID())).replaceAll("%date%",
													Plot.SHORT_PLOT_DATE_FORMAT.format(plot.getOwnedUntilDate())));
						}

						@Override
                        public void failed() {
                            if (!DraftListener.DRAFT_MAP.containsKey(this.getSender())) {
                                this.getSender()
                                        .sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().stakeCancelled);
                                ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
                                        this.getSender().getUniqueId());
                                return;
                            }

                            ItemStack plotItemStack = DraftListener.DRAFT_MAP.get(this.getSender()).getValue()
                                    .getValue().getItemStack();
                            for (ItemStack content : this.getSender().getInventory().getContents()) {
                                if (content == null || content.getType() == Material.AIR)
                                    continue;
                                if (PlotItem.getPlotItemUuid(content).equals(PlotItem.getPlotItemUuid(plotItemStack))) {
                                    this.getSender().getInventory().removeItem(content);
                                }
                            }
                            this.getSender().getInventory().addItem(
                                    DraftListener.DRAFT_MAP.get(this.getSender()).getValue().getKey().getItemStack());
                            DraftListener.DRAFT_MAP.get(this.getSender()).getKey().removeGrid();
                            DraftListener.DRAFT_MAP.remove(this.getSender());
                            ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
                                    this.getSender().getUniqueId());
                        }
						@Override
						public void expiry() {
							ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
									this.getSender().getUniqueId());
						}
					};
					ConfirmationManager.addConfirmation(confirmation);
					return;
				}
			}
		}
		if (ConfirmationManager.doesConfirmationExist(ConfirmationType.STAKE, player.getUniqueId())) {
			player.sendMessage(VirtualRealty.PREFIX + "§cYou already have a confirmation to confirm!");
			return;
		}
		if (RegionUtil.isCollidingWithAnotherPlot(cuboid)) {
			ItemStack plotItemStack = DraftListener.DRAFT_MAP.get(player).getValue().getValue().getItemStack();
			for (ItemStack content : player.getInventory().getContents()) {
				if (content == null || content.getType() == Material.AIR)
					continue;
				if (PlotItem.getPlotItemUuid(content).equals(PlotItem.getPlotItemUuid(plotItemStack))) {
					player.getInventory().removeItem(content);
				}
			}
			player.getInventory().addItem(DraftListener.DRAFT_MAP.get(player).getValue().getKey().getItemStack());
			DraftListener.DRAFT_MAP.get(player).getKey().removeGrid();
			DraftListener.DRAFT_MAP.remove(player);
			gridStructure.removeGrid();
			gridStructure.setDisplayTicks(20L * 6);
			gridStructure.preview(true, true);
			player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().claimModeCancelledCollision);
			return;
		}
		for (String s : VirtualRealty.getMessages().stakeConfirmation) {
			player.sendMessage(VirtualRealty.PREFIX + s);
		}
		Confirmation confirmation = new Confirmation(ConfirmationType.STAKE, player, "YES") {
			@SuppressWarnings("deprecation")
			@Override
			public void success() {
				GridStructure gridStructure = DraftListener.DRAFT_MAP.get(this.getSender()).getKey();
				PlotItem plotItem = DraftListener.DRAFT_MAP.get(this.getSender()).getValue().getKey();
				PlotSize plotSize = plotItem.getPlotSize();
				int length = plotItem.getLength();
				int height = plotItem.getHeight();
				int width = plotItem.getWidth();
				ItemStack plotItemStack = DraftListener.DRAFT_MAP.get(this.getSender()).getValue().getValue()
						.getItemStack();
				gridStructure.removeGrid();
				this.getSender().sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().notCollidingCreating);
				long timeStart = System.currentTimeMillis();
				Plot plot;
				Location location = gridStructure.getPreviewLocation().subtract(0, 1, 0);
				if (plotSize == PlotSize.AREA) {
					plot = PlotManager.getInstance().createArea(location, length, height, width);
				} else if (plotSize == PlotSize.CUSTOM) {
					plot = PlotManager.getInstance().createCustomPlot(location, length, height, width,
							plotItem.isNatural());
				} else {
					plot = PlotManager.getInstance().createPlot(location, plotSize, plotItem.isNatural());
				}
				String floorMaterial = NBT.get(plotItemStack, nbt -> {
					return nbt.getString("vrplot_floor_material");
				});
				String borderMaterial = NBT.get(plotItemStack, nbt -> {
					return nbt.getString("vrplot_border_material");
				});
				if (!plotItem.isNatural()) {

					plot.setFloorMaterial(Bukkit.createBlockData(floorMaterial).getMaterial());
					plot.setBorderMaterial(Bukkit.createBlockData(borderMaterial).getMaterial());

				}
				plot.setOwnedBy(this.getSender().getUniqueId());
				if (plotItem.getAdditionalDays() == 0) {
					plot.setOwnedUntilDate(Plot.MAX_DATE);
				} else {
					plot.setOwnedUntilDate(LocalDateTime.now().plusDays(plotItem.getAdditionalDays()));
				}
				for (ItemStack content : this.getSender().getInventory().getContents()) {
					if (content == null || content.getType() == Material.AIR)
						continue;
					if (PlotItem.getPlotItemUuid(content).equals(PlotItem.getPlotItemUuid(plotItemStack))) {
						this.getSender().getInventory().removeItem(content);
					}
				}
				long timeEnd = System.currentTimeMillis();
				BaseComponent textComponent = new TextComponent(
						VirtualRealty.PREFIX + VirtualRealty.getMessages().creationPlotComponent1);
				BaseComponent textComponent2 = new TextComponent(VirtualRealty.getMessages().creationPlotComponent2
						.replaceAll("%plot_id%", String.valueOf(plot.getID())));
				BaseComponent textComponent3 = new TextComponent(VirtualRealty.getMessages().creationPlotComponent3
						.replaceAll("%creation_time%", String.valueOf(timeEnd - timeStart)));
				textComponent2.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						new TextComponent[] { new TextComponent(VirtualRealty.getMessages().clickToShowDetailedInfo
								.replaceAll("%plot_id%", String.valueOf(plot.getID()))) }));
				textComponent2
						.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vrplot info " + plot.getID()));
				textComponent.addExtra(textComponent2);
				textComponent.addExtra(textComponent3);
				ChatMessage.of(textComponent).send(this.getSender());
				new BukkitRunnable() {
					@Override
					public void run() {
						gridStructure.setCuboidId(plot.getID());
						gridStructure.setDisplayTicks(20 * 6);
						gridStructure.preview(true, false);
					}
				}.runTaskLater(VirtualRealty.getInstance(), 20);
				DraftListener.DRAFT_MAP.remove(this.getSender());
				ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
						this.getSender().getUniqueId());
				plot.update();
			}

			@Override
            public void failed() {
                if (!DraftListener.DRAFT_MAP.containsKey(this.getSender())) {
                    this.getSender()
                            .sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().stakeCancelled);
                    ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
                            this.getSender().getUniqueId());
                    return;
                }

                ItemStack plotItemStack = DraftListener.DRAFT_MAP.get(this.getSender()).getValue().getValue()
                        .getItemStack();
                for (ItemStack content : this.getSender().getInventory().getContents()) {
                    if (content == null || content.getType() == Material.AIR)
                        continue;
                    if (PlotItem.getPlotItemUuid(content).equals(PlotItem.getPlotItemUuid(plotItemStack))) {
                        this.getSender().getInventory().removeItem(content);
                    }
                }
                this.getSender().getInventory()
                        .addItem(DraftListener.DRAFT_MAP.get(this.getSender()).getValue().getKey().getItemStack());
                DraftListener.DRAFT_MAP.get(this.getSender()).getKey().removeGrid();
                DraftListener.DRAFT_MAP.remove(this.getSender());
                this.getSender().sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().stakeCancelled);
                DraftListener.DRAFT_MAP.remove(this.getSender());
                ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
                        this.getSender().getUniqueId());
            }
			@Override
			public void expiry() {
				ConfirmationManager.removeStakeConfirmations(this.getConfirmationType(),
						this.getSender().getUniqueId());
			}
		};
		ConfirmationManager.addConfirmation(confirmation);
	}

	@EventHandler
	public void onPlotItemDraft(PlayerInteractEvent e) {
		Player player = e.getPlayer();
		if (e.getHand() == EquipmentSlot.OFF_HAND)
			return;
		if (!(e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK))
			return;
		if (DraftListener.DRAFT_MAP.containsKey(player)) {
			e.setCancelled(true);

			ItemStack plotItemStack = DraftListener.DRAFT_MAP.get(player).getValue().getValue().getItemStack();
			for (ItemStack content : player.getInventory().getContents()) {
				if (content == null || content.getType() == Material.AIR)
					continue;
				if (PlotItem.getPlotItemUuid(content).equals(PlotItem.getPlotItemUuid(plotItemStack))) {
					player.getInventory().removeItem(content);
				}
			}

			player.getInventory().addItem(DraftListener.DRAFT_MAP.get(player).getValue().getKey().getItemStack());
			DraftListener.DRAFT_MAP.get(player).getKey().removeGrid();
			DraftListener.DRAFT_MAP.remove(player);
			ConfirmationManager.removeStakeConfirmations(ConfirmationType.STAKE, player.getUniqueId());
			player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().claimModeDisabled);
			return;
		}
		PlayerInventory inv = player.getInventory();
		ItemStack claimItem = inv.getItemInMainHand();
		if (claimItem == null || claimItem.getType() == Material.AIR)
			return;
		String item = NBT.get(claimItem, nbt -> {
			return nbt.getString("vrplot_item");
		});
		if (!(claimItem.getType() == (Material.PLAYER_HEAD) && item != null && item.equals("CLAIM"))) {
			return;
		}
		e.setCancelled(true);
		PlotItem plotItem = PlotItem.fromItemStack(claimItem);
		Plot plot = PlotManager.getInstance().getPlot(player.getLocation());
		String featureName;
		if (plot == null) {
			if (!canCreateInWorld(player)) {
				player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().disabledPlotCreation);
				return;
			}
			featureName = VirtualRealty.getMessages().createFeature;
		} else {
			if (plotItem.getPlotSize().equals(plot.getPlotSize())) {
				if (((plot.isOwnershipExpired() && plot.getPlotOwner() != null
						&& !plot.getPlotOwner().getUniqueId().equals(player.getUniqueId()))
						|| plot.getPlotOwner() == null)) {
					featureName = VirtualRealty.getMessages().claimFeature;
				} else if (plot.getPlotOwner() != null
						&& plot.getPlotOwner().getUniqueId().equals(player.getUniqueId())) {
					featureName = VirtualRealty.getMessages().extendFeature;
				} else
					return;
			} else {
				if (!canCreateInWorld(player)) {
					player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().disabledPlotCreation);
					return;
				}
				featureName = VirtualRealty.getMessages().createFeature;
			}
		}
		String finalFeatureName = featureName;
		if (plot != null && plotItem.getPlotSize().equals(plot.getPlotSize())
				&& plot.getPlotSize() != PlotSize.CUSTOM) {
			player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().standingOnPlot);
			GridStructure previewStructure = new GridStructure(player, plot.getLength(), plot.getHeight(),
					plot.getWidth(), plot.getID(), player.getWorld(), 0, plot.getCreatedLocation());
			previewStructure.preview(player.getLocation(), true, false);
			player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().visualBoundaryDisplayed);
			PlotItem draftItem = PlotItem.fromItemStack(claimItem, VItem.DRAFT);
			DraftListener.DRAFT_MAP.put(player, new AbstractMap.SimpleEntry<>(previewStructure,
					new AbstractMap.SimpleEntry<>(plotItem, draftItem)));
			inv.remove(claimItem);
			ItemStack draftItemStack = draftItem.getItemStack();

			inv.setItemInMainHand(draftItemStack);

			VirtualRealty.getMessages().claimEnabled.forEach((message) -> player
					.sendMessage(message.replaceAll("&", "§").replaceAll("%feature%", finalFeatureName)));
			return;
		}
		String size = NBT.get(claimItem, nbt -> {
			return nbt.getString("vrplot_size");
		});
		PlotSize plotSize = PlotSize.valueOf(size);
		Cuboid cuboid = RegionUtil.getRegion(player.getLocation(), Direction.byYaw(player.getLocation().getYaw()),
				plotSize.getLength(), plotSize.getHeight(), plotSize.getWidth());
		if (RegionUtil.isCollidingWithAnotherPlot(cuboid)) {
			player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().claimModeCancelledCollision);
			if (!GridStructure.isCuboidGridDisplaying(player, 0)) {
				new GridStructure(player, plotSize.getLength(), plotSize.getHeight(), plotSize.getWidth(), 0,
						player.getWorld(), GridStructure.DISPLAY_TICKS, player.getLocation())
						.preview(player.getLocation(), true, true);
			}
			return;
		}
		if (RegionUtil.isCollidingWithBedrock(cuboid)) {
			player.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().claimModeCancelledBedrock);
			GridStructure.isCuboidGridDisplaying(player, 0);
			if (!GridStructure.isCuboidGridDisplaying(player, 0)) {
				new GridStructure(player, plotSize.getLength(), plotSize.getHeight(), plotSize.getWidth(), 0,
						player.getWorld(), GridStructure.DISPLAY_TICKS, player.getLocation())
						.preview(player.getLocation(), true, true);
			}
			return;
		}
		PlotItem draftItem = PlotItem.fromItemStack(claimItem, VItem.DRAFT);
		GridStructure draftStructure = new GridStructure(player, plotItem.getLength(), plotItem.getHeight(),
				plotItem.getWidth(), 0, player.getWorld(), 0, player.getLocation());
		DraftListener.DRAFT_MAP.put(player,
				new AbstractMap.SimpleEntry<>(draftStructure, new AbstractMap.SimpleEntry<>(plotItem, draftItem)));
		inv.remove(claimItem);

		inv.setItemInMainHand(draftItem.getItemStack());

		draftStructure.preview(player.getLocation(), true, false);
		VirtualRealty.getMessages().claimEnabled.forEach((message) -> player
				.sendMessage(message.replaceAll("&", "§").replaceAll("%feature%", finalFeatureName)));
	}

	public boolean canCreateInWorld(Player player) {
		switch (WorldSetting.valueOf(VirtualRealty.getPluginConfiguration().worldsSetting.toUpperCase())) {
		case ALL:
			break;
		case INCLUDED:
			if (VirtualRealty.getPluginConfiguration().getWorldsList().stream()
					.noneMatch(s -> player.getWorld().getName().equalsIgnoreCase(s)))
				return false;
			break;
		case EXCLUDED:
			if (VirtualRealty.getPluginConfiguration().getWorldsList().stream()
					.anyMatch(s -> player.getWorld().getName().equalsIgnoreCase(s)))
				return false;
		}
		return true;
	}

}
