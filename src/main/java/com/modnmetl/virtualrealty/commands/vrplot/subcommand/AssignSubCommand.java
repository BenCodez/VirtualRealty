package com.modnmetl.virtualrealty.commands.vrplot.subcommand;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.commands.SubCommand;
import com.modnmetl.virtualrealty.exception.FailedCommandException;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.other.CommandType;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.other.ChatMessage;
import com.modnmetl.virtualrealty.util.PlayerLookupUtil;
import com.modnmetl.virtualrealty.util.UUIDUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.LinkedList;
import java.util.UUID;

public class AssignSubCommand extends SubCommand {

    public static LinkedList<String> HELP = new LinkedList<>();

    static {
        HELP.add(" ");
        HELP.add(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»");
        HELP.add(" §a/vrplot %command% §8<§7plot§8> §8<§7username§8>");
    }

    public AssignSubCommand() {}

    public AssignSubCommand(CommandSender sender, Command command, String label, String[] args) throws FailedCommandException {
        super(sender, command, label, args, HELP);
    }

    @Override
    public void exec(CommandSender sender, Command command, String label, String[] args) throws Exception {
        assertPermission();

        // Need: /vrplot assign <plotId> <username|uuid>
        if (args.length < 3) {
            printHelp(CommandType.VRPLOT);
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

        // Resolve target player (by UUID or name)
        String targetArg = args[2];
        OfflinePlayer offlinePlayer;
        try {
            if (UUIDUtils.isValidUUID(targetArg)) {
                UUID uuid = UUID.fromString(targetArg);
                offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            } else {
                offlinePlayer = Bukkit.getOfflinePlayer(targetArg);
            }
        } catch (Exception ex) {
            // Very defensive: if anything goes wrong resolving the player, treat as "not found"
            ChatMessage.of(VirtualRealty.getMessages().playerNotFoundWithUsername).sendWithPrefix(sender);
            return;
        }

        // If Bukkit somehow gave us a null name, we still allow the assignment, but
        // we will fall back to the argument or a short UUID for the display name.
        UUID targetUUID = offlinePlayer.getUniqueId();
        if (targetUUID == null) {
            ChatMessage.of(VirtualRealty.getMessages().playerNotFoundWithUsername).sendWithPrefix(sender);
            return;
        }

        Plot plot = PlotManager.getInstance().getPlot(plotID);
        if (plot == null) {
            ChatMessage.of(VirtualRealty.getMessages().noPlotFound).sendWithPrefix(sender);
            return;
        }

        // Record who assigned it
        if (sender instanceof Player) {
            plot.setAssignedBy(((Player) sender).getUniqueId().toString());
        } else if (sender instanceof ConsoleCommandSender) {
            plot.setAssignedBy("CONSOLE");
        } else {
            plot.setAssignedBy("SHOP_PURCHASE");
        }

        // Set ownership by UUID (works for Java + Floodgate)
        plot.setOwnedBy(targetUUID);

        // Get a safe display name for messages
        String displayName = PlayerLookupUtil.getBestName(offlinePlayer);
        if (displayName == null || displayName.isEmpty()) {
            // Fall back to what the admin typed, or a short UUID
            if (!targetArg.isEmpty()) {
                displayName = targetArg;
            } else {
                displayName = targetUUID.toString().substring(0, 8);
            }
        }

        String text = VirtualRealty.getMessages().assignedToBy
                .replaceAll("%assigned_to%", displayName)
                .replaceAll("%assigned_by%", sender.getName());

        ChatMessage.of(text).sendWithPrefix(sender);
        plot.update();
    }

}
