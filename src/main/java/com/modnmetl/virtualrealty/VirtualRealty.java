package com.modnmetl.virtualrealty;

import com.modnmetl.virtualrealty.commands.PlotCommand;
import com.modnmetl.virtualrealty.commands.VirtualRealtyCommand;
import com.modnmetl.virtualrealty.configs.MessagesConfiguration;
import com.modnmetl.virtualrealty.configs.PluginConfiguration;
import com.modnmetl.virtualrealty.configs.SizesConfiguration;
import com.modnmetl.virtualrealty.enums.Flag;
import com.modnmetl.virtualrealty.enums.PlotSize;
import com.modnmetl.virtualrealty.exceptions.MaterialMatchException;
import com.modnmetl.virtualrealty.listeners.plot.BorderListener;
import com.modnmetl.virtualrealty.listeners.plot.PlotListener;
import com.modnmetl.virtualrealty.listeners.world.WorldListener;
import com.modnmetl.virtualrealty.managers.PlotManager;
import com.modnmetl.virtualrealty.objects.Plot;
import com.modnmetl.virtualrealty.premiumloader.PremiumLoader;
import com.modnmetl.virtualrealty.registry.VirtualPlaceholders;
import com.modnmetl.virtualrealty.sql.SQL;
import com.modnmetl.virtualrealty.utils.ConfigurationFactory;
import com.modnmetl.virtualrealty.utils.SchematicUtil;
import com.modnmetl.virtualrealty.utils.multiversion.VMaterial;
import com.modnmetl.virtualrealty.listeners.ProtectionListener;
import com.modnmetl.virtualrealty.utils.UpdateChecker;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerIcon;
import org.dynmap.markers.MarkerSet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.Callable;

public final class VirtualRealty extends JavaPlugin {

    public final Locale locale = Locale.getDefault();
    public final List<Locale> availableLocales = new ArrayList<>(Arrays.asList(new Locale("en", "GB"), new Locale("es", "ES"), new Locale("pl", "PL")));

    //CORE
    private static VirtualRealty instance;
    public static final String PREFIX = "§a§lVR §8§l» §7";
    public static ArrayList<BukkitTask> tasks = new ArrayList<>();
    private static final ArrayList<String> preVersions = new ArrayList<>();
    public static boolean isLegacy = false;
    public static final Permission GLOBAL_PERMISSION = new Permission("virtualrealty");

    //FILES
    public static File plotsFolder;
    public static File plotsSchemaFolder;
    public PluginConfiguration pluginConfiguration;
    public SizesConfiguration sizesConfiguration;
    public MessagesConfiguration messagesConfiguration;
    private final File pluginConfigurationFile = new File(this.getDataFolder(), "config.yml");
    private final File sizesConfigurationFile = new File(this.getDataFolder(), "sizes.yml");
    private final File languagesDirectory = new File(this.getDataFolder(), "messages");

    //DYNMAP API
    public static boolean isDynmapPresent = false;
    public static DynmapAPI dapi = null;
    public static MarkerSet markerset = null;
    public static MarkerIcon markerIcon = null;

    @Override
    public void onEnable() {
        instance = this;
        if (checkLegacyVersions()) {
            isLegacy = true;
        }
        String[] updateCheck = UpdateChecker.getUpdate();
        if (updateCheck != null) {
            if (!updateCheck[0].equals(this.getDescription().getVersion())) {
                this.getLogger().info("A newer version is available!");
                this.getLogger().info("The current version you use: " + this.getDescription().getVersion());
                this.getLogger().info("Latest version available: " + updateCheck[0]);
                this.getLogger().info("Download link: https://www.spigotmc.org/resources/virtual-realty.95599/");
            } else {
                this.getLogger().info("Plugin is up to date!");
            }
        }
        try {
            checkConfig();
            checkSizesConfig();
        } catch (IOException e) {
            e.printStackTrace();
        }
        plotsFolder = new File(getInstance().getDataFolder().getAbsolutePath(), "plots");
        plotsFolder.mkdirs();
        plotsSchemaFolder = new File(plotsFolder.getAbsolutePath(), "primary-terrain");
        plotsSchemaFolder.mkdirs();
        spawnLocales();
        reformatConfig();
        reloadConfigs();
        if (!pluginConfiguration.licenseKey.isEmpty()) {
            //LOAD premium
        }
        registerMetrics();
        loadSizesConfiguration();
        connectToDatabase();
        PlotManager.loadPlots();
        if (pluginConfiguration.dynmapMarkers) {
            registerDynmap();
        }
        reloadFlags();
        registerCommands();
        registerListeners();
        registerTasks();
        checkForOldSchemas();
        //convertOldDatabase();
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")){
            new VirtualPlaceholders(this).register();
            debug("Registered new placeholders");
        }
        debug("Server version: " + this.getServer().getBukkitVersion() + " | " + this.getServer().getVersion());
        try {
            Class.forName("com.modnmetl.virtualrealty.premiumloader.PremiumLoader");
            new PremiumLoader();
        } catch (ClassNotFoundException ignored) {

        }
    }

    @Override
    public void onDisable() {
        PlotManager.plots.forEach(Plot::update);
        tasks.forEach(BukkitTask::cancel);
        SQL.closeConnection();
        pluginConfiguration.save();
    }

    public static void debug(String debugMessage) {
        if (VirtualRealty.getPluginConfiguration().debugMode)
            VirtualRealty.getInstance().getLogger().warning("DEBUG-MODE > " + debugMessage);
    }

    public void spawnLocales() {
        for (Locale availableLocale : availableLocales) {
            if (availableLocale.toString().equalsIgnoreCase("en_GB")) {
                File messagesConfigurationFile = new File(languagesDirectory, "messages_en_GB.yml");
                ConfigurationFactory configFactory = new ConfigurationFactory();
                configFactory.createMessagesConfiguration(messagesConfigurationFile);
            } else {
                File languageConfigurationFile = new File(languagesDirectory, "messages_" + availableLocale + ".yml");
                if (!languageConfigurationFile.exists()) {
                    saveResource("messages_" + availableLocale + ".yml", true);
                    File file = new File(this.getDataFolder(), "messages_" + availableLocale + ".yml");
                    try {
                        FileUtils.moveFile(file, languageConfigurationFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

//    public void convertOldDatabase() {
//        File oldDatabase = new File(VirtualRealty.getInstance().getDataFolder().getAbsolutePath() + "\\data\\data.mv.db");
//        if (oldDatabase.exists()) {
//            try {
//                SQL.closeConnection();
//                Connection connection = DriverManager.getConnection("jdbc:sqlite:" + VirtualRealty.getInstance().getDataFolder().getAbsolutePath() + "\\data\\data.db");
//                SQL.setConnection(connection);
//                Statement statement = connection.createStatement();
//                SQL.setStatement(statement);
//                statement.execute("CREATE TABLE IF NOT EXISTS `" + VirtualRealty.getPluginConfiguration().mysql.plotsTableName + "` (`ID` INT(12) NOT NULL, `ownedBy` VARCHAR(36) NOT NULL, `members` TEXT, `assignedBy` VARCHAR(36) NOT NULL, `ownedUntilDate` DATETIME NOT NULL, `floorMaterial` VARCHAR(32) NOT NULL, `borderMaterial` VARCHAR(32) NOT NULL, `plotSize` VARCHAR(32) NOT NULL, `length` INT(24) NOT NULL, `width` INT(24) NOT NULL, `height` INT(24) NOT NULL, `createdLocation` TEXT(500) NOT NULL, `created` DATETIME, `modified` DATETIME, PRIMARY KEY(`ID`))");
//                for (Plot plot : PlotManager.plots) {
//                    plot.insert();
//                }
//                FileUtils.deleteQuietly(oldDatabase);
//                debug("H2 database converted successfully to SQLITE");
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }

    public void reloadConfigs() {
        try {
            ConfigurationFactory configFactory = new ConfigurationFactory();
            pluginConfiguration = configFactory.createPluginConfiguration(pluginConfigurationFile);
            File messagesConfigurationFile = new File(languagesDirectory, "messages_" + pluginConfiguration.locale + ".yml");
            sizesConfiguration = configFactory.createSizesConfiguration(sizesConfigurationFile);
            messagesConfiguration = configFactory.createMessagesConfiguration(messagesConfigurationFile);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    public void reloadFlags() {
        if (pluginConfiguration.allowOutPlotBuild) {
            for (Flag.World value : Flag.World.values()) {
                value.setAllowed(true);
            }
        } else {
            for (Flag.World value : Flag.World.values()) {
                value.setAllowed(false);
            }
        }
    }

    public static void checkForOldSchemas() {
        for (Plot plot : PlotManager.plots) {
            File f = new File(VirtualRealty.plotsSchemaFolder, "plot" + plot.getID() + ".schem");
            if (f.exists()) {
                List<String> data = SchematicUtil.oldLoad(plot.getID());
                FileUtils.deleteQuietly(f);
                SchematicUtil.save(plot.getID(), data.toArray(new String[0]));
                debug("Converted Plot #" + plot.getID() + " | File: " + f.getName());
            }
        }
    }

    public void registerDynmap() {
        new BukkitRunnable() {
            @Override
            public void run() {
                Plugin plugin = Bukkit.getServer().getPluginManager().getPlugin("dynmap");
                if (plugin != null) {
                    isDynmapPresent = true;
                }
                if (plugin != null && plugin.isEnabled()) {
                    dapi = (DynmapAPI) plugin;
                    if (dapi.markerAPIInitialized()) {
                        markerset = dapi.getMarkerAPI().getMarkerSet("virtualrealty.plots");
                        if (markerset == null)
                            markerset = dapi.getMarkerAPI().createMarkerSet("virutalrealty.plots", "Plots", dapi.getMarkerAPI().getMarkerIcons(), false);
                        for (MarkerSet markerSet : dapi.getMarkerAPI().getMarkerSets()) {
                            if (markerSet.getMarkerSetLabel().equalsIgnoreCase("Plots")) {
                                markerset = markerSet;
                            }
                        }
                        try {
                            if (dapi.getMarkerAPI().getMarkerIcon("virtualrealty_main_icon") == null) {
                                InputStream in = this.getClass().getResourceAsStream("/ploticon.png");
                                if (in.available() > 0) {
                                    markerIcon = dapi.getMarkerAPI().createMarkerIcon("virtualrealty_main_icon", "Plots", in);
                                }
                            }
                            else {
                                 markerIcon = dapi.getMarkerAPI().getMarkerIcon("virtualrealty_main_icon");
                            }
                        }
                        catch (IOException ex) {}
                        VirtualRealty.debug("Registering plots markers..");
                        for (Plot plot : PlotManager.plots) {
                            PlotManager.resetPlotMarker(plot);
                        }
                        VirtualRealty.debug("Registered plots markers");
                        this.cancel();
                    }
                }
            }
        }.runTaskTimer(this, 20, 20*5);
    }


    private void registerCommands() {
        this.getCommand("plot").setExecutor(new PlotCommand());
        this.getCommand("virtualrealty").setExecutor(new VirtualRealtyCommand());
    }

    private void registerListeners() {
        new BorderListener(this).registerEvents();
        new PlotListener(this).registerEvents();
        new ProtectionListener(this).registerEvents();
        new WorldListener(this).registerEvents();
        debug("Registered listeners");
    }

    private void registerTasks() {
        //debug("Registered tasks");
    }

    private void registerMetrics() {
        Metrics metrics = new Metrics(this, 14066);
        metrics.addCustomChart(new SimplePie("used_database", () -> pluginConfiguration.dataModel.name()));
        metrics.addCustomChart(new AdvancedPie("created_plots", new Callable<Map<String, Integer>>() {
            @Override
            public Map<String, Integer> call() throws Exception {
                Map<String, Integer> valueMap = new HashMap<String, Integer>();
                int smallPlots = 0;
                int mediumPlots = 0;
                int largePlots = 0;
                int customPlots = 0;
                for (Plot plot : PlotManager.plots) {
                    switch (plot.getPlotSize()) {
                        case SMALL: {
                            smallPlots++;
                            break;
                        }
                        case MEDIUM: {
                            mediumPlots++;
                            break;
                        }
                        case LARGE: {
                            largePlots++;
                            break;
                        }
                        case CUSTOM: {
                            customPlots++;
                            break;
                        }
                        default:
                            throw new IllegalStateException("Unexpected value: " + plot.getPlotSize());
                    }
                }
                valueMap.put("SMALL", smallPlots);
                valueMap.put("MEDIUM", mediumPlots);
                valueMap.put("LARGE", largePlots);
                valueMap.put("CUSTOM", customPlots);
                return valueMap;
            }
        }));
        debug("Registered metrics");
    }

    private void connectToDatabase() {
        SQL.connect();
        SQL.createTables();
        debug("Connected to database");
    }

    public void loadSizesConfiguration() {
        for (PlotSize plotSize : PlotSize.values()) {
            if (plotSize == PlotSize.CUSTOM) return;
            SizesConfiguration.PlotSizes.Size classSize = null;
            switch (plotSize) {
                case SMALL: {
                    classSize = sizesConfiguration.plotSizes.SMALL;
                    break;
                }
                case MEDIUM: {
                    classSize = sizesConfiguration.plotSizes.MEDIUM;
                    break;
                }
                case LARGE: {
                    classSize = sizesConfiguration.plotSizes.LARGE;
                    break;
                }
            }
            Material floorMaterial;
            try {
                floorMaterial = VMaterial.catchMaterial(classSize.floorMaterial.toUpperCase());
            } catch (MaterialMatchException e) {
                floorMaterial = VirtualRealty.isLegacy ? Material.GRASS : Material.GRASS_BLOCK;
                e.printStackTrace();
                //throw new MaterialMatchException("Couldn't parse floor-material from sizes.yml | Using default: " + (VirtualRealty.isLegacy ? Material.GRASS : Material.GRASS_BLOCK));
            }
            Material borderMaterial;
            try {
                borderMaterial = VMaterial.catchMaterial(classSize.borderMaterial.toUpperCase());
            } catch (MaterialMatchException e) {
                borderMaterial = VirtualRealty.isLegacy ? Material.getMaterial("STEP") : Material.STONE_BRICK_SLAB;
                e.printStackTrace();
                //throw new MaterialMatchException("Couldn't parse border-material from sizes.yml | Using default: " + (VirtualRealty.isLegacy ? Material.getMaterial("STEP") : Material.STONE_BRICK_SLAB));
            }
            plotSize.setFloorMaterial(floorMaterial);
            plotSize.setFloorData(classSize.floorData);
            plotSize.setBorderMaterial(borderMaterial);
            plotSize.setBorderData(classSize.borderData);
            plotSize.setLength(classSize.length);
            plotSize.setWidth(classSize.width);
            plotSize.setHeight(classSize.height);
        }
        debug("Loaded sizes config");
    }

    public static VirtualRealty getInstance() {
        return instance;
    }

    public static PluginConfiguration getPluginConfiguration() {
        return VirtualRealty.getInstance().pluginConfiguration;
    }

    public static File getPluginConfigurationFile() {
        return VirtualRealty.getInstance().pluginConfigurationFile;
    }

    public static SizesConfiguration getSizesConfiguration() {
        return VirtualRealty.getInstance().sizesConfiguration;
    }

    public static File getSizesConfigurationFile() {
        return VirtualRealty.getInstance().sizesConfigurationFile;
    }

    public static MessagesConfiguration getMessages() {
        return getInstance().messagesConfiguration;
    }

    public boolean checkLegacyVersions() {
        setPostVersions();
        for (String preVersion : preVersions) {
            if (Bukkit.getBukkitVersion().toLowerCase().contains(preVersion.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public static Locale getLocale() {
        return getInstance().locale;
    }

    public void setPostVersions() {
        preVersions.add("1.12");
        preVersions.add("1.11");
        preVersions.add("1.10");
        preVersions.add("1.9");
        preVersions.add("1.8");
    }

    public void reformatConfig() {
        File configFile = new File(this.getDataFolder(), "config.yml");
        File newFile = new File(this.getDataFolder(), "config.yml.new");
        if (configFile.exists()) {
            try {
                List<String> lines = FileUtils.readLines(configFile, StandardCharsets.UTF_8);
                for (int i = 0; i < lines.size(); i++) {
                    if (lines.get(i).startsWith("force-plot-gamemode:")) {
                        lines.set(i, lines.get(i).replaceAll("force-plot-gamemode:", "lock-plot-gamemode:"));
                    }
                }
                FileUtils.writeLines(newFile, lines);
                FileUtils.deleteQuietly(configFile);
                newFile.createNewFile();
                File newConfigFile = new File(this.getDataFolder(), "config.yml");
                FileUtils.copyFile(newFile, newConfigFile);
                FileUtils.deleteQuietly(newFile);
            } catch (IOException e) {

            }
        }
    }

    public void checkConfig() throws IOException {
        File oldConfigFile = new File(this.getDataFolder(), "config.yml");
        if (!oldConfigFile.exists()) return;
        String version = null;
        boolean isOldVersion = true;
        boolean updateConfigVersion = false;
        FileReader fileReader = new FileReader(this.pluginConfigurationFile);
        BufferedReader reader = new BufferedReader(fileReader);
        String latestLine;
        while((latestLine = reader.readLine()) != null) {
            if (latestLine.contains("config-version")) {
                version = latestLine.replaceAll("config-version: ", "");
                isOldVersion = false;
            }
        }
        fileReader.close();
        reader.close();
        if (version == null) {
            System.err.println(" ");
            this.getLogger().warning("Config has been reset due to major config changes!");
            this.getLogger().warning("Old config has been renamed to config.yml.old");
            this.getLogger().warning("Please update your config file!");
            System.err.println(" ");
        } else if (!version.equalsIgnoreCase(VirtualRealty.getInstance().getDescription().getVersion())) {
            updateConfigVersion = true;
            this.getLogger().info("Config has been updated!");
        }

        // save old config file
        if (isOldVersion) {
            File newConfigFile = new File(this.getDataFolder().getAbsolutePath(), "config.yml.old");
            if (newConfigFile.exists()) {
                newConfigFile.delete();
            }
            FileUtils.copyFile(oldConfigFile, newConfigFile);
            oldConfigFile.delete();
        }

//         update config version
        if (updateConfigVersion) {
            List<String> lines = new ArrayList<>();
            LineIterator iterator = FileUtils.lineIterator(oldConfigFile);
            while (iterator.hasNext()) {
                String line = iterator.next();
                lines.add(line);
            }
            for (String line : new ArrayList<>(lines)) {
                if (line.contains("config-version")) {
                    int index = lines.indexOf(line);
                    lines.set(index, "config-version: " + VirtualRealty.getInstance().getDescription().getVersion());
                }
            }
            File newConfigFile = new File(this.getDataFolder().getAbsolutePath(), "config.yml");
            FileUtils.deleteQuietly(newConfigFile);
            FileUtils.writeLines(newConfigFile, lines);
            newConfigFile.createNewFile();
        }
    }

    public void checkSizesConfig() throws IOException {
        File oldConfigFile = new File(this.getDataFolder(), "sizes.yml");
        if (!oldConfigFile.exists()) return;
        String version = null;
        boolean isOldVersion = true;
        boolean updateConfigVersion = false;
        BufferedReader reader = new BufferedReader(new FileReader(this.sizesConfigurationFile));
        String latestLine;
        while((latestLine = reader.readLine()) != null) {
            if (latestLine.contains("config-version")) {
                version = latestLine.replaceAll("config-version: ", "");
                isOldVersion = false;
            }
        }
        reader.close();
        if (version == null) {
            System.err.println(" ");
            this.getLogger().warning("Config has been reset due to major config changes!");
            this.getLogger().warning("Old config has been renamed to sizes.yml.old");
            this.getLogger().warning("Please update your config file!");
            System.err.println(" ");
        } else if (!version.equalsIgnoreCase(VirtualRealty.getInstance().getDescription().getVersion())) {
            updateConfigVersion = true;
            this.getLogger().info("Plot sizes config has been updated!");
        }

        // save old config file
        if (isOldVersion) {
            File newConfigFile = new File(this.getDataFolder().getAbsolutePath(), "sizes.yml.old");
            if (newConfigFile.exists()) {
                newConfigFile.delete();
            }
            FileUtils.copyFile(oldConfigFile, newConfigFile);
            oldConfigFile.delete();
        }

        // update config version
        if (updateConfigVersion) {
            List<String> lines = new ArrayList<>();
            LineIterator iterator = FileUtils.lineIterator(oldConfigFile);
            while (iterator.hasNext()) {
                String line = iterator.next();
                lines.add(line);
            }
            for (String line : new ArrayList<>(lines)) {
                if (line.contains("config-version")) {
                    int index = lines.indexOf(line);
                    lines.set(index, "config-version: " + VirtualRealty.getInstance().getDescription().getVersion());
                }
            }
            File newConfigFile = new File(this.getDataFolder().getAbsolutePath(), "sizes.yml");
            FileUtils.deleteQuietly(newConfigFile);
            FileUtils.writeLines(newConfigFile, lines);
            newConfigFile.createNewFile();
        }
    }

}