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
import com.modnmetl.virtualrealty.util.PlayerLookupUtil;

public class KickSubCommand extends SubCommand {

    public static LinkedList<String> HELP = new LinkedList<>();

    static {
        HELP.add(" ");
        HELP.add(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»");
        HELP.add(" §a/plot %command% §8<§7plot§8> §8<§7player§8>");
    }

    public KickSubCommand() {}

    public KickSubCommand(CommandSender sender, Command command, String label, String[] args) throws FailedCommandException {
        super(sender, command, label, args, HELP);
    }

    @Override
    public void exec(CommandSender sender, Command command, String label, String[] args) throws FailedCommandException {
        assertPlayer();
        Player player = (Player) sender;

        if (args.length < 3) {
            printHelp(CommandType.PLOT);
            return;
        }

        // Parse plot ID
        int plotID;
        try {
            plotID = Integer.parseInt(args[1]);
        } catch (IllegalArgumentException e) {
            ChatMessage.of(VirtualRealty.getMessages().useNaturalNumbersOnly).sendWithPrefix(sender);
            return;
        }

        // Resolve target player by name (works for Java + Floodgate names)
        String targetNameArg = args[2];
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetNameArg);
        UUID targetUUID = offlinePlayer.getUniqueId();

        Plot plot = PlotManager.getInstance().getPlot(plotID);
        if (plot == null) {
            ChatMessage.of(VirtualRealty.getMessages().noPlotFound).sendWithPrefix(sender);
            return;
        }

        // Plot must be owned
        UUID ownerId = plot.getOwnedBy();
        if (ownerId == null) {
            ChatMessage.of(VirtualRealty.getMessages().noPlotFound).sendWithPrefix(sender);
            return;
        }

        // Sender must have access to this plot
        if (!plot.hasMembershipAccess(player.getUniqueId())) {
            ChatMessage.of(VirtualRealty.getMessages().notYourPlot).sendWithPrefix(sender);
            return;
        }

        // Check management permissions (either owner or member with KICK_MEMBER)
        PlotMember senderMember = plot.getMember(player.getUniqueId());
        if (senderMember != null) {
            if (!senderMember.hasManagementPermission(ManagementPermission.KICK_MEMBER)) {
                ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(sender);
                return;
            }
        } else {
            if (!ownerId.equals(player.getUniqueId())) {
                ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(sender);
                return;
            }
        }

        // Ownership expired?
        if (plot.getOwnedUntilDate().isBefore(LocalDateTime.now())) {
            ChatMessage.of(VirtualRealty.getMessages().ownershipExpired).sendWithPrefix(sender);
            return;
        }

        // Can't kick owner (or yourself if you are the owner)
        if (ownerId.equals(targetUUID)) {
            boolean equals = ownerId.equals(player.getUniqueId());
            ChatMessage.of(equals
                    ? VirtualRealty.getMessages().cantKickYourself
                    : VirtualRealty.getMessages().cantKickOwner).sendWithPrefix(sender);
            return;
        }

        // Get member by UUID; if not present, report not found
        PlotMember member = plot.getMember(targetUUID);
        if (member == null) {
            ChatMessage.of(VirtualRealty.getMessages().playerNotFoundWithUsername).sendWithPrefix(sender);
            return;
        }

        // Remove member and send confirmation message
        plot.removeMember(member);

        String displayName = PlayerLookupUtil.getBestName(offlinePlayer);
        if (displayName == null || displayName.isEmpty()) {
            displayName = targetNameArg;
        }

        ChatMessage.of(
                VirtualRealty.getMessages().playerKick.replaceAll("%player%", displayName)
        ).sendWithPrefix(sender);
    }

}
