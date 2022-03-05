package com.modnmetl.virtualrealty.commands.plot.subcommand;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.commands.SubCommand;
import com.modnmetl.virtualrealty.exceptions.FailedCommandExecution;
import com.modnmetl.virtualrealty.managers.PlotManager;
import com.modnmetl.virtualrealty.objects.Plot;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Objects;

public class TpSubCommand extends SubCommand {

    public static final LinkedList<String> HELP = new LinkedList<>();

    static {
        HELP.add(" ");
        HELP.add(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»");
        HELP.add(" §a/plot tp §8<§7plot§8>");
    }

    public TpSubCommand(CommandSender sender, Command command, String label, String[] args) throws FailedCommandExecution {
        super(sender, command, label, args, HELP);
    }

    @Override
    public void exec(CommandSender sender, Command command, String label, String[] args) throws FailedCommandExecution {
        assertPlayer();
        Player player = ((Player) sender);
        if (args.length < 2) {
            printHelp();
            return;
        }
        int plotID;
        try {
            plotID = Integer.parseInt(args[1]);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().useNaturalNumbersOnly);
            return;
        }
        Plot plot = PlotManager.getPlot(plotID);
        if (plot == null) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().noPlotFound);
            return;
        }
        if (!plot.hasMembershipAccess(player.getUniqueId())) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().notYourPlot);
            return;
        }
        if (plot.isOwnershipExpired()) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
            return;
        }
        Location loc = new Location(plot.getCreatedLocation().getWorld(), plot.getCenter().getBlockX(), plot.getCenter().getBlockY() + 1, plot.getCenter().getBlockZ());
        loc.setY(Objects.requireNonNull(loc.getWorld()).getHighestBlockAt(loc.getBlockX(), loc.getBlockZ()).getY() + 1);
        player.teleport(loc);
        sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().teleportedToPlot);
    }
    
}