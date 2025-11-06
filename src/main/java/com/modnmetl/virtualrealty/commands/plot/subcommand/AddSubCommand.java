package com.modnmetl.virtualrealty.commands.plot.subcommand;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.commands.SubCommand;
import com.modnmetl.virtualrealty.exception.FailedCommandException;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.other.ChatMessage;
import com.modnmetl.virtualrealty.model.other.CommandType;
import com.modnmetl.virtualrealty.model.permission.ManagementPermission;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.plot.PlotMember;

public class AddSubCommand extends SubCommand {

	public static LinkedList<String> HELP = new LinkedList<>();

	static {
		HELP.add(" ");
		HELP.add(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»");
		HELP.add(" §a/plot %command% §8<§7plot§8> §8<§7player§8>");
	}

	public AddSubCommand() {
	}

	public AddSubCommand(CommandSender sender, Command command, String label, String[] args)
			throws FailedCommandException {
		super(sender, command, label, args, false, HELP);
	}

	@Override
	public void exec(CommandSender sender, Command command, String label, String[] args) throws FailedCommandException {
		assertPlayer();
		Player player = ((Player) sender);
		if (args.length < 3) {
			printHelp(CommandType.PLOT);
			return;
		}
		int plotID;
		try {
			plotID = Integer.parseInt(args[1]);
		} catch (IllegalArgumentException e) {
			ChatMessage.of(VirtualRealty.getMessages().useNaturalNumbersOnly).sendWithPrefix(player);
			return;
		}

		Player playerToAdd = Bukkit.getPlayer(args[2]);
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[2]);

		if (playerToAdd != null && offlinePlayer.getFirstPlayed() == 0) {
			ChatMessage.of(VirtualRealty.getMessages().playerNotFoundWithUsername).sendWithPrefix(player);
			return;
		}

		UUID toAdd = offlinePlayer.getUniqueId();
		String name = offlinePlayer.getName();
		if (playerToAdd != null) {
			toAdd = playerToAdd.getUniqueId();
			name = playerToAdd.getName();
		}
		Plot plot = PlotManager.getInstance().getPlot(plotID);
		if (plot == null) {
			ChatMessage.of(VirtualRealty.getMessages().noPlotFound).sendWithPrefix(sender);
			return;
		}
		if (!plot.hasMembershipAccess(player.getUniqueId())) {
			ChatMessage.of(VirtualRealty.getMessages().notYourPlot).sendWithPrefix(sender);
			return;
		}
		PlotMember plotMember = plot.getMember(player.getUniqueId());
		if (plotMember != null) {
			if (!plotMember.hasManagementPermission(ManagementPermission.ADD_MEMBER)) {
				ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(sender);
				return;
			}
		} else {
			if (!plot.getOwnedBy().equals(player.getUniqueId())) {
				ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(sender);
				return;
			}
		}
		if (plot.getOwnedUntilDate().isBefore(LocalDateTime.now())) {
			ChatMessage.of(VirtualRealty.getMessages().ownershipExpired).sendWithPrefix(sender);
			return;
		}
		if (plot.getOwnedBy().equals(toAdd)) {
			boolean equals = plot.getOwnedBy().equals(player.getUniqueId());
			ChatMessage.of(
					equals ? VirtualRealty.getMessages().cantAddYourself : VirtualRealty.getMessages().alreadyInMembers)
					.sendWithPrefix(sender);
			return;
		}
		if (plot.getMember(toAdd) != null) {
			ChatMessage.of(VirtualRealty.getMessages().alreadyInMembers).sendWithPrefix(sender);
			return;
		}
		plot.addMember(toAdd);
		ChatMessage.of(VirtualRealty.getMessages().playerAdd.replaceAll("%player%", name)).sendWithPrefix(sender);
	}

}
