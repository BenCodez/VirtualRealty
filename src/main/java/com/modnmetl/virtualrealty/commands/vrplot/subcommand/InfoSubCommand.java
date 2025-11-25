package com.modnmetl.virtualrealty.commands.vrplot.subcommand;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.commands.SubCommand;
import com.modnmetl.virtualrealty.exception.FailedCommandException;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.other.ChatMessage;
import com.modnmetl.virtualrealty.util.PlayerLookupUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.UUID;

public class InfoSubCommand extends SubCommand {

    public InfoSubCommand() {}

    public InfoSubCommand(CommandSender sender, Command command, String label, String[] args)
            throws FailedCommandException {
        super(sender, command, label, args, new LinkedList<>());
    }

    @Override
    public void exec(CommandSender sender, Command command, String label, String[] args) throws Exception {
        assertPermission();

        // /vrplot info  OR  /vrplot info <id>
        if (args.length < 2) {
            assertPlayer();
            Player player = (Player) sender;
            Plot plot = PlotManager.getInstance().getPlot(player.getLocation());
            if (plot == null) {
                ChatMessage.of(VirtualRealty.getMessages().notStandingOnPlot).sendWithPrefix(sender);
                return;
            }
            printInfo(sender, plot);
            return;
        }

        int plotID;
        try {
            plotID = Integer.parseInt(args[1]);
        } catch (IllegalArgumentException e) {
            ChatMessage.of(VirtualRealty.getMessages().useNaturalNumbersOnly).sendWithPrefix(sender);
            return;
        }

        if (PlotManager.getInstance().getPlots().isEmpty()) {
            ChatMessage.of(VirtualRealty.getMessages().noPlots).sendWithPrefix(sender);
            return;
        }

        if (plotID < PlotManager.getInstance().getPlotMinID()) {
            String msg = VirtualRealty.getMessages().minPlotID
                    .replace("%min_id%", String.valueOf(PlotManager.getInstance().getPlotMinID()));
            ChatMessage.of(msg).sendWithPrefix(sender);
            return;
        }

        if (plotID > PlotManager.getInstance().getPlotMaxID()) {
            String msg = VirtualRealty.getMessages().maxPlotID
                    .replace("%max_id%", String.valueOf(PlotManager.getInstance().getPlotMaxID()));
            ChatMessage.of(msg).sendWithPrefix(sender);
            return;
        }

        Plot plot = PlotManager.getInstance().getPlot(plotID);
        if (plot == null) {
            ChatMessage.of(VirtualRealty.getMessages().noPlotFound).sendWithPrefix(sender);
            return;
        }

        printInfo(sender, plot);
    }

    private void printInfo(CommandSender sender, Plot plot) {

        LocalDateTime until = plot.getOwnedUntilDate();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        // Assigned By
        String assignedBy = VirtualRealty.getMessages().notAssigned;

        if (plot.getAssignedBy() != null) {
            String assignedRaw = plot.getAssignedBy();

            switch (assignedRaw.toUpperCase()) {
                case "CONSOLE":
                    assignedBy = VirtualRealty.getMessages().assignedByConsole;
                    break;

                case "SHOP_PURCHASE":
                    assignedBy = VirtualRealty.getMessages().assignedByShopPurchase;
                    break;

                default:
                    // UUID of player who assigned it
                    try {
                        UUID assignerUUID = UUID.fromString(assignedRaw);
                        OfflinePlayer p = Bukkit.getOfflinePlayer(assignerUUID);
                        boolean online = p.isOnline();
                        String name = PlayerLookupUtil.getBestName(p);
                        assignedBy = (online ? "§a" : "§c") + name;
                    } catch (Exception ignored) {
                        assignedBy = assignedRaw;
                    }
                    break;
            }
        }

        // Owner display
        String ownerDisplay;
        if (plot.getOwnedBy() == null) {
            ownerDisplay = "§cAvailable";
        } else {
            UUID ownerUUID = plot.getOwnedBy();
            OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);
            boolean online = owner.isOnline();
            String name = PlayerLookupUtil.getBestName(owner);
            ownerDisplay = (online ? "§a" : "§c") + name;
        }

        ChatMessage.of(" ").sendWithPrefix(sender);
        ChatMessage.of(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»")
                .sendWithPrefix(sender);

        ChatMessage.of(" §7Plot ID §8§l‣ §f" + plot.getID()).sendWithPrefix(sender);
        ChatMessage.of(" §7Owned By §8§l‣ " + ownerDisplay).sendWithPrefix(sender);

        // Members
        if (!plot.getMembers().isEmpty()) {
            ChatMessage.of(" §7Members §8§l↴").send(sender);

            for (OfflinePlayer member : plot.getPlayerMembers()) {
                boolean online = member.isOnline();
                String name = PlayerLookupUtil.getBestName(member);
                ChatMessage.of(" §8§l⁍ §" + (online ? "a" : "c") + name).send(sender);
            }
        }

        ChatMessage.of(" §7Assigned By §8§l‣ §a" + assignedBy).send(sender);
        ChatMessage.of(" §7Owned Until §8§l‣ §f" + fmt.format(until)).send(sender);
        ChatMessage.of(" §7Size §8§l‣ §f" + plot.getPlotSize()).send(sender);
        ChatMessage.of(" §7Length §8§l‣ §f" + plot.getLength()).send(sender);
        ChatMessage.of(" §7Height §8§l‣ §f" + plot.getHeight()).send(sender);
        ChatMessage.of(" §7Width §8§l‣ §f" + plot.getWidth()).send(sender);
        ChatMessage.of(" §7Floor Material §8§l‣ §f" + plot.getFloorMaterialName()).send(sender);
        ChatMessage.of(" §7Border Material §8§l‣ §f" + plot.getBorderMaterialName()).send(sender);
        ChatMessage.of(" §7Pos 1 §8( §7X §8| §7Y §8| §7Z §8) §8§l‣ §f" + plot.getBottomLeftCorner()).send(sender);
        ChatMessage.of(" §7Pos 2 §8( §7X §8| §7Y §8| §7Z §8) §8§l‣ §f" + plot.getTopRightCorner()).send(sender);
        ChatMessage.of(" §7Created Direction §8§l‣ §f" + plot.getCreatedDirection().name()).send(sender);
    }
}
