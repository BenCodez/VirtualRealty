package com.modnmetl.virtualrealty;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.modnmetl.virtualrealty.commands.CommandManager;
import com.modnmetl.virtualrealty.commands.CommandRegistry;
import com.modnmetl.virtualrealty.commands.SubCommand;
import com.modnmetl.virtualrealty.commands.plot.PlotCommand;
import com.modnmetl.virtualrealty.commands.vrplot.VirtualRealtyCommand;
import com.modnmetl.virtualrealty.configs.CommandsConfiguration;
import com.modnmetl.virtualrealty.configs.MessagesConfiguration;
import com.modnmetl.virtualrealty.configs.PermissionsConfiguration;
import com.modnmetl.virtualrealty.configs.PluginConfiguration;
import com.modnmetl.virtualrealty.configs.SizesConfiguration;
import com.modnmetl.virtualrealty.listener.PlotEntranceListener;
import com.modnmetl.virtualrealty.listener.player.PlayerActionListener;
import com.modnmetl.virtualrealty.listener.player.PlayerListener;
import com.modnmetl.virtualrealty.listener.premium.PanelListener;
import com.modnmetl.virtualrealty.listener.protection.BorderProtectionListener;
import com.modnmetl.virtualrealty.listener.protection.PlotProtectionListener;
import com.modnmetl.virtualrealty.listener.protection.WorldProtectionListener;
import com.modnmetl.virtualrealty.listener.stake.ConfirmationListener;
import com.modnmetl.virtualrealty.listener.stake.DraftListener;
import com.modnmetl.virtualrealty.manager.DynmapManager;
import com.modnmetl.virtualrealty.manager.MetricsManager;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.manager.Pl3xMapManager;
import com.modnmetl.virtualrealty.model.other.ServerVersion;
import com.modnmetl.virtualrealty.model.plot.Plot;
import com.modnmetl.virtualrealty.model.plot.PlotSize;
import com.modnmetl.virtualrealty.registry.VirtualPlaceholders;
import com.modnmetl.virtualrealty.sql.Database;
import com.modnmetl.virtualrealty.util.PanelUtil;
import com.modnmetl.virtualrealty.util.UpdateChecker;
import com.modnmetl.virtualrealty.util.configuration.ConfigurationFactory;
import com.zaxxer.hikari.HikariDataSource;

import lombok.Getter;
import lombok.Setter;

public final class VirtualRealty extends JavaPlugin {

	// CORE
	public List<JarFile> jarFiles = new ArrayList<>();
	public DynmapManager dynmapManager;
	public Pl3xMapManager pl3xMapManager;
	public Locale locale;
	@Getter
	private static VirtualRealty instance;
	@Getter
	private MetricsManager metricsManager;
	@Getter
	private PlotManager plotManager;

	@Getter
	public ConfigurationFactory configFactory = new ConfigurationFactory();
	public PluginConfiguration pluginConfiguration;
	public SizesConfiguration sizesConfiguration;
	public MessagesConfiguration messagesConfiguration;
	public PermissionsConfiguration permissionsConfiguration;
	public CommandsConfiguration commandsConfiguration;
	private static ClassLoader classLoader;
	public static final String PREFIX = "§a§lVR §8§l» §7";
	public static List<BukkitTask> tasks = new ArrayList<>();
	public static ServerVersion currentServerVersion = ServerVersion.MODERN;
	public static final Permission GLOBAL_PERMISSION = new Permission("virtualrealty");

	public static boolean upToDate;
	public static String latestVersion;
	public static boolean developmentBuild;

	// FILES
	@Getter
	@Setter
	public static File loaderFile;
	public static File plotsFolder;
	public static File plotsSchemaFolder;
	private final File pluginConfigurationFile = new File(this.getDataFolder(), "config.yml");
	private final File sizesConfigurationFile = new File(this.getDataFolder(), "sizes.yml");
	private final File permissionsConfigurationFile = new File(this.getDataFolder(), "permissions.yml");
	private final File commandsConfigurationFile = new File(this.getDataFolder(), "commands.yml");
	private final File languagesDirectory = new File(this.getDataFolder(), "messages");
	private final File databaseFolder = new File(this.getDataFolder().getAbsolutePath(),
			File.separator + "data" + File.separator);
	private final File databaseFile = new File(databaseFolder, "data.db");

	@Override
	public void onEnable() {
		instance = this;
		classLoader = getClassLoader();
		try {
			jarFiles.add(new JarFile(getFile()));
		} catch (IOException e) {
			e.printStackTrace();
		}
		databaseFolder.mkdirs();
		plotsFolder = new File(getInstance().getDataFolder().getAbsolutePath(), "plots");
		plotsFolder.mkdirs();
		plotsSchemaFolder = new File(plotsFolder.getAbsolutePath(), "primary-terrain");
		plotsSchemaFolder.mkdirs();
		reloadConfigs();
		this.locale = new Locale(getPluginConfiguration().locale.split("_")[0],
				getPluginConfiguration().locale.split("_")[1]);
		configureMessages();
		checkUpdates();

		loadMetrics();
		loadSizesConfiguration();
		try {
			Database.connectToDatabase(databaseFile);
		} catch (SQLException e) {
			getLogger().log(Level.WARNING, "Failed to connect to the database.");
			this.getPluginLoader().disablePlugin(this);
			return;
		}
		loadPlotsData();
		loadDynMapHook();
		loadPl3xMapHook();
		registerCommands();
		loadCommandsConfiguration();
		updateCommandsConfig();
		registerListeners();
		registerPlaceholders();
		debug("Server version: " + this.getServer().getBukkitVersion() + " | " + this.getServer().getVersion());
		getLogger().info("Virtual Realty has been enabled!");
	}

	@Override
	public void onDisable() {
		PanelUtil.SELECTED_PANEL.forEach((uuid, panelType) -> {
			Player player = Bukkit.getPlayer(uuid);
			assert player != null;
			player.closeInventory();
		});
		DraftListener.DRAFT_MAP.forEach((player, gridStructureEntryEntry) -> {
			player.getInventory().remove(gridStructureEntryEntry.getValue().getValue().getItemStack());
			player.getInventory().addItem(gridStructureEntryEntry.getValue().getKey().getItemStack());
			gridStructureEntryEntry.getKey().removeGrid();
		});
		DraftListener.DRAFT_MAP.clear();
		plotManager.getPlots().forEach(Plot::update);
		tasks.forEach(BukkitTask::cancel);
		try {
			DataSource dataSource;
			if (getDatabase() != null && (dataSource = getDatabase().getDataSource()) != null
					&& dataSource.getConnection() != null) {
				if (VirtualRealty.getPluginConfiguration().dataModel == PluginConfiguration.DataModel.MYSQL) {
					((HikariDataSource) dataSource).close();
				} else {
					dataSource.getConnection().close();
				}
			}
		} catch (SQLException ignored) {
		}
		ConfigurationFactory configurationFactory = new ConfigurationFactory();
		configurationFactory.updatePluginConfiguration(pluginConfigurationFile);
		FileUtils.deleteQuietly(loaderFile);
	}

	public void registerPlaceholders() {
		if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI"))
			return;
		new VirtualPlaceholders(this).register();
		debug("Registered new placeholders");
	}

	private void updateCommandsConfig() {
		{
			List<String> messages = VirtualRealty.getCommands().plotCommandsHelp.get("plot");
			Set<String> subCommands = VirtualRealty.getCommands().plotAliases.getAliasesMap().keySet();
			List<String> missingSubCommands = subCommands.stream()
					.filter(subCommand -> messages.stream()
							.noneMatch(helpMessage -> helpMessage.contains("%" + subCommand + "_command%")))
					.collect(Collectors.toList());
			for (String subCommand : missingSubCommands) {
				PlotCommand.HELP_LIST.stream()
						.filter(helpMessage -> helpMessage.contains("%" + subCommand + "_command%"))
						.forEach(messages::add);
			}
			if (!missingSubCommands.isEmpty()) {
				VirtualRealty.getCommands().save();
			}
		}
		{
			List<String> messages = VirtualRealty.getCommands().vrplotCommandsHelp.get("vrplot");
			Set<String> subCommands = VirtualRealty.getCommands().vrplotAliases.keySet();
			List<String> missingSubCommands = subCommands.stream()
					.filter(subCommand -> messages.stream()
							.noneMatch(helpMessage -> helpMessage.contains("%" + subCommand + "_command%")))
					.collect(Collectors.toList());
			for (String subCommand : missingSubCommands) {
				VirtualRealtyCommand.HELP_LIST.stream()
						.filter(helpMessage -> helpMessage.contains("%" + subCommand + "_command%"))
						.forEach(messages::add);
			}
			if (!missingSubCommands.isEmpty()) {
				VirtualRealty.getCommands().save();
			}
		}
	}

	public void checkUpdates() {
		String[] currentVersionNumbers = this.getDescription().getVersion().split("\\.");
		String[] updateCheck = UpdateChecker.getUpdate();
		if (updateCheck != null) {
			String updateVersion = updateCheck[0];
			String[] cloudVersionNumbers = updateVersion.split("\\.");
			int currentMajor = Integer.parseInt(currentVersionNumbers[0]);
			int currentMinor = Integer.parseInt(currentVersionNumbers[1]);
			int currentPatch = Integer.parseInt(currentVersionNumbers[2]);

			int updateMajor = Integer.parseInt(cloudVersionNumbers[0]);
			int updateMinor = Integer.parseInt(cloudVersionNumbers[1]);
			int updatePatch = Integer.parseInt(cloudVersionNumbers[2]);

			boolean patchUpdate = updatePatch > currentPatch;
			boolean minorUpdate = updateMinor > currentMinor;
			boolean majorUpdate = updateMajor > currentMajor;

			boolean majorDevelopment = currentMajor > updateMajor;
			boolean minorDevelopment = currentMinor > updateMinor;

			if (currentMajor == updateMajor && currentMinor == updateMinor && currentPatch == updatePatch) {
				upToDate = true;
				latestVersion = this.getDescription().getVersion();
				this.getLogger().info("Plugin is up to date!");
				return;
			}
			if (majorUpdate) {
				registerUpdate(updateVersion, false);
			} else {
				if (minorUpdate) {
					if (majorDevelopment) {
						registerUpdate(updateVersion, true);
						return;
					}
					registerUpdate(updateVersion, false);
				} else {
					if (patchUpdate) {
						if (majorDevelopment || minorDevelopment) {
							registerUpdate(updateVersion, true);
							return;
						}
						registerUpdate(updateVersion, false);
					} else {
						registerUpdate(updateVersion, true);
					}
				}
			}
		}
	}

	private void registerUpdate(String version, boolean development) {
		upToDate = false;
		latestVersion = version;
		if (development) {
			developmentBuild = true;
			this.getLogger().warning("You are running a development build!");
			return;
		}
		this.getLogger().info("A newer version is available!");
		this.getLogger().info("The current version you use: " + this.getDescription().getVersion());
		this.getLogger().info("Latest version available: " + version);
		this.getLogger().info("Download link: https://www.spigotmc.org/resources/virtual-realty.95599/");
	}

	public void loadPlotsData() {
		plotManager = new PlotManager(this);
		plotManager.loadPlots();
		plotManager.loadMembers();
	}

	public void loadDynMapHook() {
		if (getPluginConfiguration().dynmapMarkers) {
			dynmapManager = new DynmapManager(this);
			dynmapManager.registerDynmap();
		}
	}

	public void loadPl3xMapHook() {
		if (getPluginConfiguration().pl3xmapMarkers) {
			pl3xMapManager = new Pl3xMapManager(this);
			pl3xMapManager.registerPl3xMap();
		}
	}

	public void loadMetrics() {
		metricsManager = new MetricsManager(this, 14066);
		metricsManager.registerMetrics();
	}

	public void configureMessages() {
		File messagesConfigurationFile = new File(languagesDirectory, "messages_" + locale.toString() + ".yml");
		ConfigurationFactory configFactory = new ConfigurationFactory();
		configFactory.loadMessagesConfiguration(messagesConfigurationFile);
	}

	public void loadCommandsConfiguration() {
		commandsConfiguration = configFactory.loadCommandsConfiguration(commandsConfigurationFile);
		commandsConfiguration.assignAliases();
		CommandRegistry.setupPlaceholders();
	}

	public void reloadConfigs() {
		try {
			pluginConfiguration = configFactory.loadPluginConfiguration(pluginConfigurationFile);
			File messagesConfigurationFile = new File(languagesDirectory,
					"messages_" + pluginConfiguration.locale + ".yml");
			sizesConfiguration = configFactory.loadSizesConfiguration(sizesConfigurationFile);
			permissionsConfiguration = configFactory.loadPermissionsConfiguration(permissionsConfigurationFile);
			messagesConfiguration = configFactory.loadMessagesConfiguration(messagesConfigurationFile);
		} catch (Exception exception) {
			exception.printStackTrace();
		}
	}

	private void registerCommands() {
		PluginCommand plotCommand = this.getCommand("plot");
		assert plotCommand != null;
		plotCommand.setExecutor(new PlotCommand());
		plotCommand.setTabCompleter(new CommandManager());
		registerSubCommands(PlotCommand.class, "panel");

		PluginCommand vrCommand = this.getCommand("virtualrealty");
		assert vrCommand != null;
		vrCommand.setExecutor(new VirtualRealtyCommand());
		vrCommand.setTabCompleter(new CommandManager());
		registerSubCommands(VirtualRealtyCommand.class);
	}

	public void registerSubCommands(Class<?> mainCommandClass, String... names) {
		if (names.length > 0)
			SubCommand.registerSubCommands(names, mainCommandClass);
		for (JarFile jarFile : jarFiles) {
			for (Enumeration<JarEntry> entry = jarFile.entries(); entry.hasMoreElements();) {
				JarEntry jarEntry = entry.nextElement();
				String name = jarEntry.getName().replace("/", ".");
				if (name.endsWith(".class")
						&& name.startsWith(mainCommandClass.getPackage().getName() + ".subcommand.")) {
					try {
						Class<?> clazz = Class.forName(name.replaceAll("[.]class", ""), true, getClassLoader());
						String subcommand = clazz.getSimpleName().toLowerCase().replaceAll("subcommand", "");
						if (subcommand.isEmpty())
							continue;
						SubCommand.registerSubCommands(new String[] { subcommand }, mainCommandClass);
					} catch (ClassNotFoundException ignored) {
					}
				}
			}
		}
	}

	private void registerListeners() {
		new BorderProtectionListener(this);
		new PlayerListener(this);
		new PlotProtectionListener(this);
		new WorldProtectionListener(this);
		new PlotEntranceListener(this);
		new PlayerActionListener(this);
		new DraftListener(this);
		new ConfirmationListener(this);
		new PanelListener(this);
		debug("Registered listeners");
	}

	public void loadSizesConfiguration() {
		for (PlotSize plotSize : PlotSize.values()) {
			if (plotSize == PlotSize.CUSTOM)
				continue;
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
			case AREA: {
				plotSize.setLength(sizesConfiguration.plotSizes.AREA.length);
				plotSize.setWidth(sizesConfiguration.plotSizes.AREA.width);
				plotSize.setHeight(sizesConfiguration.plotSizes.AREA.height);
				return;
			}
			default:
				break;
			}

			Material floorMaterial = Material.getMaterial(classSize.floorMaterial.toUpperCase());
			if (floorMaterial == null) {
				floorMaterial = Material.GRASS_BLOCK;
			}

			Material borderMaterial = Material.getMaterial(classSize.borderMaterial.toUpperCase());
			if (borderMaterial == null) {
				borderMaterial = Material.STONE_BRICK_SLAB;
			}

			plotSize.setFloorMaterial(floorMaterial);
			plotSize.setBorderMaterial(borderMaterial);
			plotSize.setLength(classSize.length);
			plotSize.setWidth(classSize.width);
			plotSize.setHeight(classSize.height);
		}
		debug("Loaded sizes config");
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

	public static PermissionsConfiguration getPermissions() {
		return getInstance().permissionsConfiguration;
	}

	public static CommandsConfiguration getCommands() {
		return getInstance().commandsConfiguration;
	}

	public static DynmapManager getDynmapManager() {
		return getInstance().dynmapManager;
	}

	public static Pl3xMapManager getPl3xMapManager() {
		return getInstance().pl3xMapManager;
	}

	public static Database getDatabase() {
		return Database.getInstance();
	}

	public static ClassLoader getLoader() {
		return classLoader;
	}

	public void setClassLoader(ClassLoader newClassLoader) {
		classLoader = newClassLoader;
	}

	public static void debug(String message) {
		if (!VirtualRealty.getPluginConfiguration().debugMode)
			return;
		VirtualRealty.getInstance().getLogger().warning("DEBUG > " + message);
	}

}
