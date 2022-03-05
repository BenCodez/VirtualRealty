package com.modnmetl.virtualrealty.commands.plot.subcommand;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.commands.SubCommand;
import com.modnmetl.virtualrealty.exceptions.FailedCommandExecution;
import com.modnmetl.virtualrealty.managers.PlotManager;
import com.modnmetl.virtualrealty.managers.PlotMemberManager;
import com.modnmetl.virtualrealty.objects.Plot;
import com.modnmetl.virtualrealty.objects.PlotMember;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedList;

public class GmSubCommand extends SubCommand {

    public static final LinkedList<String> HELP = new LinkedList<>();

    static {
        HELP.add(" ");
        HELP.add(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»");
        HELP.add(" §a/plot gm §8<§7gamemode§8>");
    }
    
    public GmSubCommand(CommandSender sender, Command command, String label, String[] args) throws FailedCommandExecution {
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
        if (VirtualRealty.getPluginConfiguration().lockPlotGamemode) {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().gamemodeFeatureDisabled);
            return;
        }
        GameMode gameMode;
        int gameModeID;
        try {
            gameMode = GameMode.valueOf(args[1]);
            gameModeID = gameMode.getValue();
        } catch (IllegalArgumentException e) {
            try {
                gameModeID = Integer.parseInt(args[1]);
            } catch (IllegalArgumentException ex) {
                sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().incorrectGamemode);
                return;
            }
        }
        GameMode defaultGamemode = VirtualRealty.getInstance().getServer().getDefaultGameMode();
        GameMode configGamemode = VirtualRealty.getPluginConfiguration().getDefaultPlotGamemode();
        if (!(gameModeID != configGamemode.getValue() && gameModeID != defaultGamemode.getValue())) {
            gameMode = GameMode.getByValue(Integer.parseInt(args[1]));
        } else {
            sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().gamemodeDisabled);
            return;
        }
        Plot plot = PlotManager.getBorderedPlot(player.getLocation());
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