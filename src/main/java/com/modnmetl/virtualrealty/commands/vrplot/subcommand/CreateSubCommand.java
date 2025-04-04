package com.modnmetl.virtualrealty.commands.vrplot.subcommand;

import java.util.Arrays;
import java.util.LinkedList;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.commands.SubCommand;
import com.modnmetl.virtualrealty.exception.FailedCommandException;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.math.Direction;
import com.modnmetl.virtualrealty.model.other.ChatMessage;
import com.modnmetl.virtualrealty.model.other.CommandType;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.plot.PlotSize;
import com.modnmetl.virtualrealty.model.region.Cuboid;
import com.modnmetl.virtualrealty.model.region.GridStructure;
import com.modnmetl.virtualrealty.util.RegionUtil;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;

public class CreateSubCommand extends SubCommand {

	public static LinkedList<String> HELP = new LinkedList<>();

	static {
		HELP.add(" ");
		HELP.add(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»");
		HELP.add(
				" §a/vrplot %command% §8<§7small/medium/large§8> §8<§7floor (optional)§8> §8<§7border (optional)§8> §8<§7--natural(optional)§8>");
		HELP.add(" §a/vrplot %command% area §8<§7length§8> §8<§7height§8> §8<§7width§8>");
		HELP.add(
				" §a/vrplot %command% §8<§7length§8> §8<§7height§8> §8<§7width§8> §8<§7floor (optional)§8> §8<§7border (optional)§8> §8<§7--natural(optional)§8>");
	}

	public CreateSubCommand() {
	}

	public CreateSubCommand(CommandSender sender, Command command, String label, String[] args)
			throws FailedCommandException {
		super(sender, command, label, args, HELP);
	}

	@Override
	public void exec(CommandSender sender, Command command, String label, String[] args) throws Exception {
		assertPlayer();
		assertPermission();
		if (args.length < 2) {
			printHelp(CommandType.VRPLOT);
			return;
		}
		Player player = ((Player) sender);
		Location location = player.getLocation();
		location.add(0, -1, 0);
		if (Arrays.stream(PlotSize.values()).anyMatch(plotSize -> plotSize.name().equalsIgnoreCase(args[1]))
				&& !args[1].equalsIgnoreCase(PlotSize.CUSTOM.name())) {
			PlotSize plotSize = null;
			try {
				plotSize = PlotSize.valueOf(args[1].toUpperCase());
			} catch (IllegalArgumentException ignored) {
			}
			if (plotSize == null) {
				ChatMessage.of(VirtualRealty.getMessages().sizeNotRecognised).sendWithPrefix(sender);
				return;
			}
			if (plotSize == PlotSize.AREA) {
				int length = plotSize.getLength();
				int height = plotSize.getHeight();
				int width = plotSize.getWidth();
				if (args.length > 2) {
					try {
						length = Integer.parseInt(args[2]);
						height = Integer.parseInt(args[3]);
						width = Integer.parseInt(args[4]);
					} catch (IllegalArgumentException e) {
						ChatMessage.of(VirtualRealty.getMessages().useNaturalNumbersOnly).sendWithPrefix(sender);
						return;
					}
				}
				if (length < 1 || width < 1 || height < 1) {
					ChatMessage.of(VirtualRealty.getMessages().graterThenZero).sendWithPrefix(sender);
					return;
				}
				Cuboid cuboid = RegionUtil.getRegion(location, Direction.byYaw(location.getYaw()), length, height,
						width);
				if (RegionUtil.isCollidingWithAnotherPlot(cuboid)) {
					ChatMessage.of(VirtualRealty.getMessages().cantCreateOnExisting).sendWithPrefix(sender);
					return;
				}
				ChatMessage.of(VirtualRealty.getMessages().notCollidingCreating).sendWithPrefix(sender);
				long timeStart = System.currentTimeMillis();
				Plot plot = PlotManager.getInstance().createArea(location, length, height, width);
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
				ChatMessage.of(textComponent).send(player);
				new BukkitRunnable() {
					@Override
					public void run() {
						new GridStructure(player, plot.getLength(), plot.getHeight(), plot.getWidth(), plot.getID(),
								((Player) sender).getWorld(), GridStructure.DISPLAY_TICKS, plot.getCreatedLocation())
								.preview(true, false);
					}
				}.runTaskLater(VirtualRealty.getInstance(), 20);
			} else {
				Cuboid cuboid = RegionUtil.getRegion(location, Direction.byYaw(location.getYaw()), plotSize.getLength(),
						plotSize.getHeight(), plotSize.getWidth());
				if (RegionUtil.isCollidingWithAnotherPlot(cuboid)) {
					ChatMessage.of(VirtualRealty.getMessages().cantCreateOnExisting).sendWithPrefix(sender);
					return;
				}
				boolean natural = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("--natural"));
				Material floorMaterial = null;
				byte floorData = 0;
				Material borderMaterial = null;
				byte borderData = 0;
				if (args.length >= 3 && !natural) {
					try {
						floorMaterial = Material.getMaterial(args[2].split(":")[0].toUpperCase());
						if (floorMaterial == null) {
							ChatMessage.of(VirtualRealty.getMessages().cantGetFloorMaterial).sendWithPrefix(sender);
							return;
						}
					} catch (IllegalArgumentException e) {
						ChatMessage.of(VirtualRealty.getMessages().cantGetFloorMaterial).sendWithPrefix(sender);
						return;
					}

				}
				if (args.length >= 4) {
					try {
						borderMaterial = Material.getMaterial(args[3].split(":")[0].toUpperCase());
						if (borderMaterial == null) {
							ChatMessage.of(VirtualRealty.getMessages().cantGetBorderMaterial).sendWithPrefix(sender);
							return;
						}
					} catch (IllegalArgumentException e) {
						ChatMessage.of(VirtualRealty.getMessages().cantGetBorderMaterial).sendWithPrefix(sender);
						return;
					}

				}
				ChatMessage.of(VirtualRealty.getMessages().notCollidingCreating).sendWithPrefix(sender);
				long timeStart = System.currentTimeMillis();
				Plot plot = PlotManager.getInstance().createPlot(location, plotSize, natural);
				if (!natural) {
					if (floorMaterial != null) {
						plot.setFloorMaterial(floorMaterial);
					}
					if (borderMaterial != null) {
						plot.setBorderMaterial(borderMaterial);
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
				ChatMessage.of(textComponent).send(player);
				new BukkitRunnable() {
					@Override
					public void run() {
						new GridStructure(player, plot.getPlotSize().getLength(), plot.getPlotSize().getHeight(),
								plot.getPlotSize().getWidth(), plot.getID(), ((Player) sender).getWorld(),
								GridStructure.DISPLAY_TICKS, plot.getCreatedLocation()).preview(true, false);
					}
				}.runTaskLater(VirtualRealty.getInstance(), 20);
			}
		} else {
			int length;
			int height;
			int width;
			try {
				length = Integer.parseInt(args[1]);
				height = Integer.parseInt(args[2]);
				width = Integer.parseInt(args[3]);
			} catch (IllegalArgumentException e) {
				ChatMessage.of(VirtualRealty.getMessages().useNaturalNumbersOnly).sendWithPrefix(sender);
				return;
			}
			if (length < 1 || width < 1 || height < 1) {
				ChatMessage.of(VirtualRealty.getMessages().graterThenZero).sendWithPrefix(sender);
				return;
			}
			if (length > 500 || width > 500 || height > 500) {
				ChatMessage.of(VirtualRealty.getMessages().hardLimit).sendWithPrefix(sender);
				return;
			}
			Cuboid cuboid = RegionUtil.getRegion(location, Direction.byYaw(location.getYaw()), length, height, width);
			if (RegionUtil.isCollidingWithAnotherPlot(cuboid) || RegionUtil.isCollidingWithBedrock(cuboid)) {
				ChatMessage.of(VirtualRealty.getMessages().cantCreateOnExisting).sendWithPrefix(sender);
				return;
			}
			boolean natural = Arrays.stream(args).anyMatch(s -> s.equalsIgnoreCase("--natural"));
			Material floorMaterial = null;
			byte floorData = 0;
			if (args.length >= 5 && !natural) {
				try {
					floorMaterial = Material.getMaterial(args[4].split(":")[0].toUpperCase());
					if (floorMaterial == null) {
						ChatMessage.of(VirtualRealty.getMessages().cantGetFloorMaterial).sendWithPrefix(sender);
						return;
					}
				} catch (IllegalArgumentException e) {
					ChatMessage.of(VirtualRealty.getMessages().cantGetFloorMaterial).sendWithPrefix(sender);
					return;
				}

			}
			Material borderMaterial = null;
			byte borderData = 0;
			if (args.length >= 6) {
				try {
					borderMaterial = Material.getMaterial(args[5].split(":")[0].toUpperCase());
					if (borderMaterial == null) {
						ChatMessage.of(VirtualRealty.getMessages().cantGetBorderMaterial).sendWithPrefix(sender);
						return;
					}
				} catch (IllegalArgumentException e) {
					ChatMessage.of(VirtualRealty.getMessages().cantGetBorderMaterial).sendWithPrefix(sender);
					return;
				}

			}
			ChatMessage.of(VirtualRealty.getMessages().notCollidingCreating).sendWithPrefix(sender);
			long timeStart = System.currentTimeMillis();
			Plot plot = PlotManager.getInstance().createCustomPlot(location, length, height, width, natural);
			if (!natural) {
				if (floorMaterial != null)
					plot.setFloorMaterial(floorMaterial);
				if (borderMaterial != null)
					plot.setBorderMaterial(borderMaterial);
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
			textComponent2.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/vrplot info " + plot.getID()));
			textComponent.addExtra(textComponent2);
			textComponent.addExtra(textComponent3);
			ChatMessage.of(textComponent).send(player);
			new BukkitRunnable() {
				@Override
				public void run() {
					new GridStructure(player, plot.getLength(), plot.getHeight(), plot.getWidth(), plot.getID(),
							((Player) sender).getWorld(), GridStructure.DISPLAY_TICKS, plot.getCreatedLocation())
							.preview(true, false);
				}
			}.runTaskLater(VirtualRealty.getInstance(), 20);
		}
	}

}