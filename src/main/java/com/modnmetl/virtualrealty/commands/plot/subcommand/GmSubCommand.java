package com.modnmetl.virtualrealty.commands.plot.subcommand;

import java.util.LinkedList;

import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.commands.SubCommand;
import com.modnmetl.virtualrealty.exception.FailedCommandException;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.other.CommandType;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.plot.PlotMember;

public class GmSubCommand extends SubCommand {

    public static LinkedList<String> HELP = new LinkedList<>();

    static {
        HELP.add(" ");
        HELP.add(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»");
        HELP.add(" §a/plot %command% §8<§7gamemode§8>");
    }

    public GmSubCommand() {}
    
    public GmSubCommand(CommandSender sender, Command command, String label, String[] args) throws FailedCommandException {
        super(sender, command, label, args, HELP);
    }

    @Override
    public void exec(CommandSender sender, Command command, String label, String[] args) throws FailedCommandException {
        assertPlayer();
        Player player = ((Player) sender);
        if (args.length < 2) {
            printHelp(CommandType.PLOT);
            return;
        }
        if (VirtualRealty.getPluginConfiguration().lockPlotGamemode) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().gamemodeFeatureDisabled);
            return;
        }
        GameMode gameMode = null;
        try {
            gameMode = GameMode.valueOf(args[1].toUpperCase());
        } catch (Exception ignored) {}
        if (gameMode == null) {
            try {
                int gameModeInt = Integer.parseInt(args[1]);
                gameMode = GameMode.getByValue(gameModeInt);
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().incorrectGamemode);
                return;
            }
        }
        int gameModeID = gameMode.getValue();
        GameMode defaultGamemode = VirtualRealty.getInstance().getServer().getDefaultGameMode();
        GameMode configGamemode = VirtualRealty.getPluginConfiguration().getDefaultPlotGamemode();
        if (!(gameModeID != configGamemode.getValue() && gameModeID != defaultGamemode.getValue())) {
            gameMode = GameMode.getByValue(gameModeID);
        } else {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().gamemodeDisabled);
            return;
        }
        Plot plot = PlotManager.getInstance().getPlot(player.getLocation());
        if (plot == null) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantSwitchGamemode);
            return;
        }
        if (!plot.hasMembershipAccess(player.getUniqueId())) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantSwitchGamemode);
            return;
        }
        if (plot.isOwnershipExpired()) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownershipExpired);
            return;
        }
        if (player.getGameMode().equals(gameMode)) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().gamemodeAlreadySelected);
            return;
        }
        if (plot.getOwnedBy() != null && plot.getOwnedBy().equals(player.getUniqueId())) {
            plot.setSelectedGameMode(gameMode);
        } else {
            PlotMember plotMember = plot.getMember(player.getUniqueId());
            plotMember.setSelectedGameMode(gameMode);
        }
        player.setGameMode(gameMode);
        sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().gamemodeSwitched);
    }
    
}
