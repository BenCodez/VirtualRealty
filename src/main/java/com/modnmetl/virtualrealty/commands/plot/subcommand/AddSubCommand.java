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

public class AddSubCommand extends SubCommand {

    public static LinkedList<String> HELP = new LinkedList<>();

    static {
        HELP.add(" ");
        HELP.add(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»");
        HELP.add(" §a/plot %command% §8<§7plot§8> §8<§7player§8>");
    }

    public AddSubCommand() {}

    public AddSubCommand(CommandSender sender, Command command, String label, String[] args)
            throws FailedCommandException {
        super(sender, command, label, args, false, HELP);
    }

    @Override
    public void exec(CommandSender sender, Command command, String label, String[] args) throws FailedCommandException {
        assertPlayer();
        Player player = (Player) sender;

        if (args.length < 3) {
            printHelp(CommandType.PLOT);
            return;
        }

        // Parse plot ID safely
        int plotID;
        try {
            plotID = Integer.parseInt(args[1]);
        } catch (Exception ex) {
            ChatMessage.of(VirtualRealty.getMessages().useNaturalNumbersOnly).sendWithPrefix(player);
            return;
        }

        String targetArg = args[2];

        // Try online first
        Player online = Bukkit.getPlayer(targetArg);
        UUID targetUUID;
        String displayName;

        if (online != null) {
            // Cleanest case — real online player
            targetUUID = online.getUniqueId();
            displayName = online.getName();
        } else {
            // Offline or Floodgate, or unknown/uncached
            OfflinePlayer offline = Bukkit.getOfflinePlayer(targetArg);
            targetUUID = offline.getUniqueId();

            if (targetUUID == null) {
                ChatMessage.of(VirtualRealty.getMessages().playerNotFoundWithUsername).sendWithPrefix(player);
                return;
            }

            // Resolve a SAFE display name
            displayName = PlayerLookupUtil.getBestName(offline);

            if (displayName == null || displayName.isEmpty()) {
                // Fallback: use what the player typed, or short UUID
                if (!targetArg.isEmpty()) {
                    displayName = targetArg;
                } else {
                    displayName = targetUUID.toString().substring(0, 8);
                }
            }
        }

        // Fetch plot
        Plot plot = PlotManager.getInstance().getPlot(plotID);
        if (plot == null) {
            ChatMessage.of(VirtualRealty.getMessages().noPlotFound).sendWithPrefix(player);
            return;
        }

        // Access check
        if (!plot.hasMembershipAccess(player.getUniqueId())) {
            ChatMessage.of(VirtualRealty.getMessages().notYourPlot).sendWithPrefix(player);
            return;
        }

        PlotMember memberSender = plot.getMember(player.getUniqueId());
        if (memberSender != null) {
            if (!memberSender.hasManagementPermission(ManagementPermission.ADD_MEMBER)) {
                ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(player);
                return;
            }
        } else {
            if (!plot.getOwnedBy().equals(player.getUniqueId())) {
                ChatMessage.of(VirtualRealty.getMessages().noAccess).sendWithPrefix(player);
                return;
            }
        }

        // Ownership time check
        if (plot.getOwnedUntilDate().isBefore(LocalDateTime.now())) {
            ChatMessage.of(VirtualRealty.getMessages().ownershipExpired).sendWithPrefix(player);
            return;
        }

        // Cannot add yourself if you're owner
        if (plot.getOwnedBy().equals(targetUUID)) {
            boolean equals = plot.getOwnedBy().equals(player.getUniqueId());
            ChatMessage msg = ChatMessage.of(
                    equals ? VirtualRealty.getMessages().cantAddYourself : VirtualRealty.getMessages().alreadyInMembers
            );
            msg.sendWithPrefix(player);
            return;
        }

        // Already a member?
        if (plot.getMember(targetUUID) != null) {
            ChatMessage.of(VirtualRealty.getMessages().alreadyInMembers).sendWithPrefix(player);
            return;
        }

        // Add the member
        plot.addMember(targetUUID);

        // Send safe success message
        ChatMessage.of(
                VirtualRealty.getMessages().playerAdd.replace("%player%", displayName)
        ).sendWithPrefix(player);
    }
}
