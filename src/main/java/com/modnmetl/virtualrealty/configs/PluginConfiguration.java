package com.modnmetl.virtualrealty.configs;

import com.modnmetl.virtualrealty.model.other.HighlightType;
import com.modnmetl.virtualrealty.model.other.WorldSetting;
import com.modnmetl.virtualrealty.model.other.ServerVersion;
import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.*;
import com.modnmetl.virtualrealty.VirtualRealty;
import lombok.NoArgsConstructor;
import org.bukkit.GameMode;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

@Header("-------------------------------------------------------------- #")
@Header("                                                               #")
@Header("                        Virtual Realty                         #")
@Header("                                                               #")
@Header("-------------------------------------------------------------- #")
@Names(strategy = NameStrategy.HYPHEN_CASE, modifier = NameModifier.TO_LOWER_CASE)
public class PluginConfiguration extends OkaeriConfig {

	@Comment("Changing this value might break your plugin!")
	@CustomKey("initial-version")
	public String initServerVersion = ServerVersion.MODERN.toString();

	@Comment("Debug mode (Displays more detailed info about plugin executions)")
	public boolean debugMode = false;

	@Comment("Local loader mode (for devs)")
	public boolean loaderDebugMode = false;

	@Comment("Set player gamemode to change when they enter their plot")
	public boolean enablePlotGamemode = false;

	@Comment("Set your wanted language (locale)")
	@Comment("You can create your own configuration for your language")
	@Comment("To do so: go into the `messages` folder and create new file called `messages_%locale%.yml`")
	@Comment("Replace `%locale%` with your locale (It doesn't matter if it conforms to the standard, it should just match the locale here in `config.yml`)")
	public String locale = "en_GB";

	@Comment("Set which gamemode players change to when they enter their plot")
	@CustomKey("default-plot-gamemode")
	public String plotGamemode = "SURVIVAL";

	@Comment("The number of days before plot expiration when players should be notified")
	public int daysUntilExpirationThreshold = 2;

	@Comment("Disable natural spawning of all mobs in plots/areas")
	public boolean disablePlotMobsSpawn = false;

	@Comment("Disable natural spawning of monster mobs in plots/areas")
	public boolean disablePlotMonsterSpawn = false;

	@Comment("Worlds restrictions setting ( ALL | INCLUDED | EXCLUDED )")
	@Comment("ALL - all worlds are capable of creating plots with plot claim items and 'worlds-list' setting is skipped")
	@Comment("INCLUDED - only worlds included in 'worlds-list' setting are capable of creating plots with plot claim items")
	@Comment("EXCLUDED - all worlds mentioned in 'worlds-list' setting won't be capable of creating plots with plot claim items")
	public String worldsSetting = WorldSetting.ALL.name();
	@Comment("List of worlds")
	public List<String> worldsList = Arrays.asList("%world%", "%world%_nether", "%world%_the_end");

	public List<String> getWorldsList() {
		ArrayList<String> strings = new ArrayList<>();
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream("server.properties"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		for (String s : worldsList) {
			strings.add(s.replaceAll("%world%", prop.getProperty("level-name")));
		}
		return strings;
	}

	@Comment("Lock gamemode to plot default when player enters their plot (disables '/plot gm' command)")
	public boolean lockPlotGamemode = false;

	public GameMode getDefaultPlotGamemode() {
		try {
			return GameMode.valueOf(plotGamemode);
		} catch (Exception e) {
			VirtualRealty.getInstance().getLogger().warning("Couldn't parse plot-gamemode from config.yml");
			VirtualRealty.getInstance().getLogger().warning("Using default: SURVIVAL");
			return GameMode.SURVIVAL;
		}
	}

	@Comment("Enables dynmap plots highlighting")
	@CustomKey("enable-dynmap-markers")
	public boolean dynmapMarkers = false;

	@Comment("The minimum number of blocks required between plots")
	@CustomKey("enforce-plot-separation")
	public int plotSpacing = 1;

	@Comment("Choose which type of plots should be highlighted on Dynmap page | Choose from: { ALL, AVAILABLE, OWNED }")
	public HighlightType dynmapType = HighlightType.ALL;

	@CustomKey("dynmap-markers")
	public MarkerColor dynmapMarkersColor = new MarkerColor();

	@Comment("Enables pl3xmap plots highlighting")
	@CustomKey("enable-pl3xmap-markers")
	public boolean pl3xmapMarkers = false;

	@Comment("Choose which type of plots should be highlighted on Pl3xMap page | Choose from: { ALL, AVAILABLE, OWNED }")
	public HighlightType pl3xmapType = HighlightType.ALL;

	@CustomKey("pl3xmap-markers")
	public Pl3xMapMarkerColor pl3xmapMarkersColor = new Pl3xMapMarkerColor();

	@Names(strategy = NameStrategy.IDENTITY)
	public static class MarkerColor extends OkaeriConfig {

		public Available available = new Available();
		public Owned owned = new MarkerColor.Owned();

		@Names(strategy = NameStrategy.IDENTITY)
		public static class Available extends OkaeriConfig {

			public String color = "#80eb34";
			public double opacity = .3;

			public int getHexColor() {
				return Integer.decode("0x" + color.replaceAll("#", ""));
			}

		}

		@Names(strategy = NameStrategy.IDENTITY)
		public static class Owned extends OkaeriConfig {

			public String color = "#ffbf00";
			public double opacity = .45;

			public int getHexColor() {
				return Integer.decode("0x" + color.replaceAll("#", ""));
			}

		}

	}

	@Names(strategy = NameStrategy.IDENTITY)
	public static class Pl3xMapMarkerColor extends OkaeriConfig {

		public Pl3xMapAvailable available = new Pl3xMapAvailable();
		public Pl3xMapOwned owned = new Pl3xMapOwned();

		@Names(strategy = NameStrategy.IDENTITY)
		public static class Pl3xMapAvailable extends OkaeriConfig {

			public String color = "#80eb34";
			public double opacity = 0.3;

		}

		@Names(strategy = NameStrategy.IDENTITY)
		public static class Pl3xMapOwned extends OkaeriConfig {

			public String color = "#ffbf00";
			public double opacity = 0.45;

		}

	}

	@Comment("Enables plots enter/leave sounds")
	@CustomKey("plot-sounds")
	public boolean plotSound = true;

	@Comment("Type of data recording")
	@Comment("SQLITE - Local database")
	@Comment("MYSQL - External database")
	@CustomKey("data-model")
	public DataModel dataModel = DataModel.SQLITE;

	@Comment("Data required to connect to the database")
	@Comment("The plotsTableName section is the name of the VR data table in the database")
	@Comment("It is best to change these names only if you really need to (e.g. there is a conflict with another plugin)")
	@Comment("To rename tables when you already have some VR data in the database:")
	@Comment("1. Turn off the server")
	@Comment("2. Change data in VR config")
	@Comment("3. Rename database tables using phpMyAdmin for example")
	@CustomKey("mysql")
	public MySQL mysql = new MySQL();

	public enum DataModel {
		SQLITE, MYSQL
	}

	@Names(strategy = NameStrategy.IDENTITY)
	@NoArgsConstructor
	public static class MySQL extends OkaeriConfig {

		public String hostname = "localhost";
		public int port = 3306;
		public String database = "db";
		public String user = "root";
		public String password = "passwd";
		public String plotsTableName = "vr_plots";
		public String plotMembersTableName = "vr_plot_members";

	}

}
