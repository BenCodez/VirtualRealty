package com.modnmetl.virtualrealty.commands.vrplot.subcommand;

import static com.modnmetl.virtualrealty.commands.vrplot.VirtualRealtyCommand.COMMAND_PERMISSION;

import java.time.LocalDateTime;
import java.util.LinkedList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.commands.SubCommand;
import com.modnmetl.virtualrealty.exception.FailedCommandException;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.other.CommandType;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.util.PlayerLookupUtil;
import com.modnmetl.virtualrealty.util.UUIDUtils;

public class SetSubCommand extends SubCommand {

    public static LinkedList<String> HELP = new LinkedList<>();

    static {
        HELP.add(" ");
        HELP.add(" §8§l«§8§m                    §8[§aVirtualRealty§8]§m                    §8§l»");
        HELP.add(" §a/vrplot %command% §8<§7plot§8> §aowner §8<§7username§8>");
        HELP.add(" §a/vrplot %command% §8<§7plot§8> §afloor §8<§7material§8>");
        HELP.add(" §a/vrplot %command% §8<§7plot§8> §aborder §8<§7material§8>");
        HELP.add(" §a/vrplot %command% §8<§7plot§8> §aexpiry §8<§7dd/mm/YYYY§8> §8<§7HH:mm (optional)§8>");
    }

    public SetSubCommand() {
    }

    public SetSubCommand(CommandSender sender, Command command, String label, String[] args)
            throws FailedCommandException {
        super(sender, command, label, args, HELP);
    }

    @Override
    public void exec(CommandSender sender, Command command, String label, String[] args) throws Exception {
        assertPermission();
        if (args.length < 3) {
            printHelp(CommandType.VRPLOT);
            return;
        }
        switch (args[2].toUpperCase()) {
            case "OWNER": {
                assertPermission(COMMAND_PERMISSION.getName() + args[0].toLowerCase() + "." + args[2].toLowerCase());
                if (args.length < 4) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().specifyUsername);
                    return;
                }

                int plotID;
                try {
                    plotID = Integer.parseInt(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().useNaturalNumbersOnly);
                    return;
                }

                String targetArg = args[3];
                OfflinePlayer offlinePlayer;
                UUID targetUUID;

                try {
                    if (UUIDUtils.isValidUUID(targetArg)) {
                        targetUUID = UUID.fromString(targetArg);
                        offlinePlayer = Bukkit.getOfflinePlayer(targetUUID);
                    } else {
                        offlinePlayer = Bukkit.getOfflinePlayer(targetArg);
                        targetUUID = offlinePlayer.getUniqueId();
                    }
                } catch (Exception ex) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().playerNotFoundWithUsername);
                    return;
                }

                if (targetUUID == null) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().playerNotFoundWithUsername);
                    return;
                }

                Plot plot = PlotManager.getInstance().getPlot(plotID);
                if (plot == null) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().noPlotFound);
                    return;
                }

                // Optional: set expiry date/time if provided
                if (args.length >= 5) {
                    String dateFormat = args[4];
                    String timeFormat;
                    int year;
                    int month;
                    int dayOfMonth;
                    int hour = 0;
                    int minute = 0;
                    LocalDateTime localDateTime;
                    try {
                        String[] dateParts = dateFormat.split("/");
                        year = Integer.parseInt(dateParts[2]);
                        month = Integer.parseInt(dateParts[1]);
                        dayOfMonth = Integer.parseInt(dateParts[0]);
                        if (args.length >= 6) {
                            timeFormat = args[5];
                            String[] timeParts = timeFormat.split(":");
                            hour = Integer.parseInt(timeParts[0]);
                            minute = Integer.parseInt(timeParts[1]);
                        }
                        localDateTime = LocalDateTime.of(year, month, dayOfMonth, hour, minute);
                    } catch (Exception e) {
                        sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().invalidDateProvided);
                        return;
                    }
                    plot.setOwnedUntilDate(localDateTime);
                }

                // Special case: remote console + "assign" flag
                if (sender instanceof RemoteConsoleCommandSender && args.length >= 7
                        && args[6].equalsIgnoreCase("assign")) {
                    plot.setAssignedBy("SHOP_PURCHASE");
                }

                plot.setOwnedBy(targetUUID);

                // Safe display name for messages
                String displayName = PlayerLookupUtil.getBestName(offlinePlayer);
                if (displayName == null || displayName.isEmpty()) {
                    if (!targetArg.isEmpty()) {
                        displayName = targetArg;
                    } else {
                        displayName = targetUUID.toString().substring(0, 8);
                    }
                }

                sender.sendMessage(
                        VirtualRealty.PREFIX +
                                VirtualRealty.getMessages().assignedTo.replaceAll("%assigned_to%", displayName)
                );
                plot.update();
                return;
            }
            case "FLOOR": {
                assertPermission(COMMAND_PERMISSION.getName() + args[0].toLowerCase() + "." + args[2].toLowerCase());
                if (args.length < 4) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().specifyMaterialName);
                    return;
                }
                int plotID;
                try {
                    plotID = Integer.parseInt(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().useNaturalNumbersOnly);
                    return;
                }
                Material material;
                try {
                    material = Material.matchMaterial(args[3].split(":")[0].toUpperCase());
                    if (material == null) {
                        sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantGetMaterial);
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantGetMaterial);
                    return;
                }
                Plot plot = PlotManager.getInstance().getPlot(plotID);
                if (plot == null) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().noPlotFound);
                    return;
                }

                plot.setFloorMaterial(material);
                sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().newFloorMaterialSet);
                plot.update();
                return;
            }
            case "BORDER": {
                assertPermission(COMMAND_PERMISSION.getName() + args[0].toLowerCase() + "." + args[2].toLowerCase());
                if (args.length < 4) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().specifyMaterialName);
                    return;
                }
                int plotID;
                try {
                    plotID = Integer.parseInt(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().useNaturalNumbersOnly);
                    return;
                }
                Material material;
                try {
                    material = Material.getMaterial(args[3].split(":")[0].toUpperCase());
                    if (material == null) {
                        sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantGetMaterial);
                        return;
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().cantGetMaterial);
                    return;
                }
                Plot plot = PlotManager.getInstance().getPlot(plotID);
                if (plot == null) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().noPlotFound);
                    return;
                }

                plot.setBorderMaterial(material);
                sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().newBorderMaterialSet);
                plot.update();
                return;
            }
            case "EXPIRY": {
                assertPermission(COMMAND_PERMISSION.getName() + args[0].toLowerCase() + "." + args[2].toLowerCase());
                if (args.length < 4) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().specifyExpiryDate);
                    return;
                }
                int plotID;
                try {
                    plotID = Integer.parseInt(args[1]);
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().useNaturalNumbersOnly);
                    return;
                }
                String dateFormat = args[3];
                String timeFormat;
                int year;
                int month;
                int dayOfMonth;
                int hour = 0;
                int minute = 0;
                LocalDateTime localDateTime;
                try {
                    String[] dateParts = dateFormat.split("/");
                    year = Integer.parseInt(dateParts[2]);
                    month = Integer.parseInt(dateParts[1]);
                    dayOfMonth = Integer.parseInt(dateParts[0]);
                    if (args.length == 5) {
                        timeFormat = args[4];
                        String[] timeParts = timeFormat.split(":");
                        hour = Integer.parseInt(timeParts[0]);
                        minute = Integer.parseInt(timeParts[1]);
                    }
                    localDateTime = LocalDateTime.of(year, month, dayOfMonth, hour, minute);
                } catch (Exception e) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().invalidDateProvided);
                    return;
                }
                Plot plot = PlotManager.getInstance().getPlot(plotID);
                if (plot == null) {
                    sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().noPlotFound);
                    return;
                }
                plot.setOwnedUntilDate(localDateTime);
                sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().ownedUntilUpdated);
                plot.update();
                return;
            }
            default: {
                for (String helpMessage : HELP) {
                    sender.sendMessage(helpMessage);
                }
            }
        }
    }

}
