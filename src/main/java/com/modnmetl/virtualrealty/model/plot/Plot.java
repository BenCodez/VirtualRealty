package com.modnmetl.virtualrealty.model.plot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.configs.PluginConfiguration;
import com.modnmetl.virtualrealty.manager.DynmapManager;
import com.modnmetl.virtualrealty.manager.PlotManager;
import com.modnmetl.virtualrealty.model.math.BlockVector3;
import com.modnmetl.virtualrealty.model.math.Direction;
import com.modnmetl.virtualrealty.model.permission.RegionPermission;
import com.modnmetl.virtualrealty.model.region.Cuboid;
import com.modnmetl.virtualrealty.sql.Database;
import com.modnmetl.virtualrealty.util.EnumUtils;
import com.modnmetl.virtualrealty.util.data.OldSchematicUtil;
import com.modnmetl.virtualrealty.util.data.SchematicUtil;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

@Getter
@Setter
public final class Plot {

	public static final LocalDateTime MAX_DATE = LocalDateTime.of(2999, 12, 31, 0, 0);
	public static final DateTimeFormatter PLOT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
	public static final DateTimeFormatter SHORT_PLOT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

	private int ID;
	private UUID ownedBy;
	public final LinkedList<PlotMember> members;
	public final Set<RegionPermission> nonMemberPermissions;
	private String assignedBy;
	private LocalDateTime ownedUntilDate;
	private PlotSize plotSize;
	private final int length, width, height;
	private Material floorMaterial, borderMaterial;
	private final Location createdLocation;
	private Direction createdDirection;
	private BlockVector3 bottomLeftCorner, topRightCorner, borderBottomLeftCorner, borderTopRightCorner;
	private GameMode selectedGameMode;
	private final String createdWorld;
	private Instant modified;
	private LocalDateTime createdAt;

	private transient Cuboid cachedCuboid;
	private transient Cuboid cachedBorderedCuboid;

	public Plot(Location location, Material floorMaterial, Material borderMaterial, PlotSize plotSize, int length,
			int width, int height, boolean natural) {
		this.ID = PlotManager.getInstance().getPlots().isEmpty() ? 10000 : PlotManager.getInstance().getPlotMaxID() + 1;
		this.ownedBy = null;
		this.members = new LinkedList<>();
		this.nonMemberPermissions = new HashSet<>(VirtualRealty.getPermissions().getDefaultNonMemberPlotPerms());
		this.assignedBy = null;
		this.ownedUntilDate = MAX_DATE;
		if (natural) {
			this.floorMaterial = Material.AIR;
			this.borderMaterial = Material.AIR;
		} else {
			this.floorMaterial = floorMaterial;
			this.borderMaterial = borderMaterial;
		}
		this.createdLocation = location;
		this.createdDirection = Direction.byYaw(location.getYaw());
		this.selectedGameMode = VirtualRealty.getPluginConfiguration().getDefaultPlotGamemode();
		this.createdWorld = Objects.requireNonNull(location.getWorld()).getName();
		this.modified = Instant.now();
		this.createdAt = LocalDateTime.now();
		this.plotSize = plotSize;
		this.length = length;
		this.width = width;
		this.height = height;
		initialize(natural);
		if (VirtualRealty.getDynmapManager() != null && VirtualRealty.getDynmapManager().markerset != null)
			DynmapManager.resetPlotMarker(this);
	}

	@SneakyThrows
	public Plot(ResultSet rs) {
		this.ID = rs.getInt("ID");
		this.ownedBy = rs.getString("ownedBy").isEmpty() ? null : UUID.fromString(rs.getString("ownedBy"));
		this.members = new LinkedList<>();
		Set<RegionPermission> plotPermissions = new HashSet<>();
		if (!rs.getString("nonMemberPermissions").isEmpty()) {
			for (String s : rs.getString("nonMemberPermissions").split("¦")) {
				plotPermissions.add(RegionPermission.valueOf(s.toUpperCase()));
			}
		}
		this.nonMemberPermissions = plotPermissions;
		this.assignedBy = rs.getString("assignedBy").equalsIgnoreCase("null") ? null : rs.getString("assignedBy");
		DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder().parseCaseInsensitive()
				.append(DateTimeFormatter.ISO_LOCAL_DATE).appendLiteral(' ').append(DateTimeFormatter.ISO_LOCAL_TIME)
				.toFormatter();
		if (VirtualRealty.getPluginConfiguration().dataModel == PluginConfiguration.DataModel.SQLITE) {
			this.ownedUntilDate = rs.getTimestamp("ownedUntilDate").toLocalDateTime();
			if (rs.getString("created") != null) {
				try {
					this.createdAt = rs.getTimestamp("created").toLocalDateTime();
				} catch (Exception ignored) {
					this.createdAt = LocalDateTime.parse(rs.getString("created"), dateTimeFormatter);
				}
			}
		} else {
			this.ownedUntilDate = rs.getTimestamp("ownedUntilDate").toLocalDateTime();
			if (rs.getTimestamp("created") != null)
				this.createdAt = rs.getTimestamp("created").toLocalDateTime();
		}
		this.floorMaterial = Material.getMaterial(rs.getString("floorMaterial").split(":")[0]);
		if (rs.getString("borderMaterial") != null) {
			this.borderMaterial = Material.getMaterial(rs.getString("borderMaterial").split(":")[0]);
		}
		this.plotSize = PlotSize.valueOf(rs.getString("plotSize"));
		this.length = rs.getInt("length");
		this.width = rs.getInt("width");
		this.height = rs.getInt("height");
		ArrayList<String> location = new ArrayList<>(Arrays.asList(rs.getString("createdLocation")
				.subSequence(0, rs.getString("createdLocation").length() - 1).toString().split(";")));
		Location createLocation = new Location(Bukkit.getWorld(location.get(0)), Double.parseDouble(location.get(1)),
				Double.parseDouble(location.get(2)), Double.parseDouble(location.get(3)),
				Float.parseFloat(location.get(4)), Float.parseFloat(location.get(5)));
		this.createdLocation = rs.getString("createdLocation").isEmpty() ? null : createLocation;
		if (this.createdLocation != null) {
			this.createdDirection = Direction.byYaw(createdLocation.getYaw());
		}
		if (!rs.getString("selectedGameMode").isEmpty()
				&& EnumUtils.isValidEnum(GameMode.class, rs.getString("selectedGameMode"))) {
			this.selectedGameMode = GameMode.valueOf(rs.getString("selectedGameMode"));
		} else {
			this.selectedGameMode = VirtualRealty.getPluginConfiguration().getDefaultPlotGamemode();
		}
		this.createdWorld = location.get(0);
		if (floorMaterial == null) {
			floorMaterial = plotSize.getFloorMaterial();
		}
		if (borderMaterial == null) {
			borderMaterial = plotSize.getBorderMaterial();
		}
		prepareCorners();
	}

	public String getFloorMaterialName() {
		if (this.floorMaterial == Material.AIR)
			return "NONE";
		return this.floorMaterial.name();
	}

	public String getBorderMaterialName() {
		if (this.borderMaterial == Material.AIR)
			return "NONE";
		return this.borderMaterial.name();
	}

	public void teleportPlayer(Player player) {
		World world = Bukkit.getWorld(createdWorld);
		if (world == null)
			return;
		Location location = new Location(world, getCenter().getBlockX(), getCenter().getBlockY() + 1,
				getCenter().getBlockZ());
		if (!world.getName().endsWith("_nether")) {
			location.setY(Objects.requireNonNull(location.getWorld())
					.getHighestBlockAt(location.getBlockX(), location.getBlockZ()).getY() + 1);
		}
		player.teleport(location);
	}

	public boolean hasMembershipAccess(UUID uuid) {
		PlotMember member = getMember(uuid);
		return member != null || (ownedBy != null && getPlotOwner().getUniqueId().equals(uuid));
	}

	public void togglePermission(RegionPermission plotPermission) {
		modified();
		if (nonMemberPermissions.contains(plotPermission))
			nonMemberPermissions.remove(plotPermission);
		else
			nonMemberPermissions.add(plotPermission);
	}

	public boolean hasPermission(RegionPermission plotPermission) {
		return nonMemberPermissions.contains(plotPermission);
	}

	public void addPermission(RegionPermission plotPermission) {
		nonMemberPermissions.add(plotPermission);
	}

	public void removePermission(RegionPermission plotPermission) {
		nonMemberPermissions.remove(plotPermission);
	}

	public PlotMember getMember(UUID uuid) {
		for (PlotMember member : members) {
			if (member.getUuid().equals(uuid))
				return member;
		}
		return null;
	}

	public void addMember(UUID uuid) {
		PlotMember plotMember = new PlotMember(uuid, this.getID());
		plotMember.insert();
		members.add(plotMember);
	}

	public void removeMember(PlotMember plotMember) {
		members.remove(plotMember);
		plotMember.delete();
	}

	public void removeAllMembers() {
		for (PlotMember member : new LinkedList<>(members)) {
			removeMember(member);
		}
	}

	public boolean isOwnershipExpired() {
		return ownedUntilDate.isBefore(LocalDateTime.now());
	}

	public int getXMin() {
		return Math.min(this.borderBottomLeftCorner.getBlockX(), this.borderTopRightCorner.getBlockX());
	}

	public int getXMax() {
		return Math.max(this.borderBottomLeftCorner.getBlockX(), this.borderTopRightCorner.getBlockX());
	}

	public int getZMin() {
		return Math.min(this.borderBottomLeftCorner.getBlockZ(), this.borderTopRightCorner.getBlockZ());
	}

	public int getZMax() {
		return Math.max(this.borderBottomLeftCorner.getBlockZ(), this.borderTopRightCorner.getBlockZ());
	}

	public void setOwnedBy(UUID ownedBy) {
		modified();
		this.ownedBy = ownedBy;
		removeAllMembers();
		updateMarker();
	}

	public void setOwnedUntilDate(LocalDateTime ownedUntilDate) {
		modified();
		this.ownedUntilDate = ownedUntilDate;
		updateMarker();
	}

	public void setFloorMaterial(Material floorMaterial) {
		modified();
		this.floorMaterial = floorMaterial;
		initializeFloor();
	}

	public void setBorderMaterial(Material borderMaterial) {
		modified();
		this.borderMaterial = borderMaterial;
		for (Block borderBlock : getBorderBlocks()) {
			borderBlock.setType(borderMaterial);
		}
	}

	public BlockVector3 getCenter() {
		return new Cuboid(bottomLeftCorner, topRightCorner, getCreatedWorld()).getCenterVector();
	}

	public Cuboid getCuboid() {
		if (cachedCuboid == null) {
			cachedCuboid = new Cuboid(bottomLeftCorner, topRightCorner, getCreatedWorld());
		}
		return cachedCuboid;
	}

	public Cuboid getBorderedCuboid() {
		if (cachedBorderedCuboid == null) {
			cachedBorderedCuboid = new Cuboid(borderBottomLeftCorner, borderTopRightCorner, getCreatedWorld());
		}
		return cachedBorderedCuboid;
	}

	public org.bukkit.World getCreatedWorld() {
		return Bukkit.getWorld(createdWorld);
	}

	public String getCreatedWorldRaw() {
		return createdWorld;
	}

	public boolean isBorderLess() {
		return this.borderMaterial == Material.AIR || this.plotSize == PlotSize.AREA;
	}

	public OfflinePlayer getPlotOwner() {
		return ownedBy == null ? null : Bukkit.getOfflinePlayer(ownedBy);
	}

	public Set<OfflinePlayer> getPlayerMembers() {
		Set<OfflinePlayer> offlinePlayers = new HashSet<>();
		for (PlotMember member : members) {
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(member.getUuid());
			offlinePlayers.add(offlinePlayer);
		}
		return offlinePlayers;
	}

	public void prepareCorners() {
		Location location = createdLocation;
		Direction direction = Direction.byYaw(location.getYaw());
		Location location1;
		Location location2;
		Location border1;
		Location border2;
		switch (direction) {
		case SOUTH: {
			location1 = new Location(getCreatedWorld(), location.getBlockX(), location.getBlockY() - 10,
					location.getBlockZ());
			location2 = new Location(getCreatedWorld(), location.getBlockX() - width + 1, location.getBlockY() + height,
					location.getBlockZ() + length - 1);
			border1 = new Location(getCreatedWorld(), location.getBlockX() + 1, location.getBlockY() - 10,
					location.getBlockZ() - 1);
			border2 = new Location(getCreatedWorld(), location.getBlockX() - width, location.getBlockY() + height,
					location.getBlockZ() + length);
			break;
		}
		case WEST: {
			location1 = new Location(getCreatedWorld(), location.getBlockX(), location.getBlockY() - 10,
					location.getBlockZ());
			location2 = new Location(getCreatedWorld(), location.getBlockX() - length + 1,
					location.getBlockY() + height, location.getBlockZ() - width + 1);
			border1 = new Location(getCreatedWorld(), location.getBlockX() + 1, location.getBlockY() - 10,
					location.getBlockZ() + 1);
			border2 = new Location(getCreatedWorld(), location.getBlockX() - length, location.getBlockY() + height,
					location.getBlockZ() - width);
			break;
		}
		case NORTH: {
			location1 = new Location(getCreatedWorld(), location.getBlockX(), location.getBlockY() - 10,
					location.getBlockZ());
			location2 = new Location(getCreatedWorld(), location.getBlockX() + width - 1, location.getBlockY() + height,
					location.getBlockZ() - length + 1);
			border1 = new Location(getCreatedWorld(), location.getBlockX() - 1, location.getBlockY() - 10,
					location.getBlockZ() + 1);
			border2 = new Location(getCreatedWorld(), location.getBlockX() + width, location.getBlockY() + height,
					location.getBlockZ() - length);
			break;
		}
		case EAST: {
			location1 = new Location(getCreatedWorld(), location.getBlockX() + length - 1, location.getBlockY() - 10,
					location.getBlockZ());
			location2 = new Location(getCreatedWorld(), location.getBlockX(), location.getBlockY() + height,
					location.getBlockZ() + width - 1);
			border1 = new Location(getCreatedWorld(), location.getBlockX() + length, location.getBlockY() - 10,
					location.getBlockZ() - 1);
			border2 = new Location(getCreatedWorld(), location.getBlockX() - 1, location.getBlockY() + height,
					location.getBlockZ() + width);
			break;
		}
		default:
			throw new IllegalStateException("Unexpected value: " + direction);
		}
		this.bottomLeftCorner = BlockVector3.at(location1.getBlockX(), location1.getBlockY(), location1.getBlockZ());
		this.topRightCorner = BlockVector3.at(location2.getBlockX(), location2.getBlockY(), location2.getBlockZ());
		this.borderBottomLeftCorner = BlockVector3.at(border1.getBlockX(), border1.getBlockY(), border1.getBlockZ());
		this.borderTopRightCorner = BlockVector3.at(border2.getBlockX(), border2.getBlockY(), border2.getBlockZ());
	}

	public void initialize(boolean natural) {
		long time = System.currentTimeMillis();
		prepareCorners();
		if (plotSize != PlotSize.AREA)
			prepareRegion(createdLocation, natural);
		VirtualRealty.debug("Plot initialization time: " + (System.currentTimeMillis() - time) + " ms");
	}

	public Set<Block> getBorderBlocks() {
		Set<Block> blocks = new HashSet<>();
		Location location = this.getCreatedLocation();
		Direction direction = Direction.byYaw(location.getYaw());
		int maxX;
		int maxZ;
		int minX;
		int minZ;
		switch (direction) {
		case SOUTH: {
			maxX = location.getBlockX() + 1 + 1;
			maxZ = location.getBlockZ() + length + 1;
			minX = location.getBlockX() - width + 1;
			minZ = location.getBlockZ() - 1;
			break;
		}
		case WEST: {
			maxX = location.getBlockX() + 1 + 1;
			maxZ = location.getBlockZ() + 1 + 1;
			minX = location.getBlockX() - length + 1;
			minZ = location.getBlockZ() - width;
			break;
		}
		case NORTH: {
			maxX = location.getBlockX() + width + 1;
			maxZ = location.getBlockZ() + 1 + 1;
			minX = location.getBlockX();
			minZ = location.getBlockZ() - length;
			break;
		}
		case EAST: {
			maxX = location.getBlockX() + length + 1;
			maxZ = location.getBlockZ() + width + 1;
			minX = location.getBlockX();
			minZ = location.getBlockZ() - 1;
			break;
		}
		default:
			throw new IllegalStateException("Unexpected value: " + direction);
		}
		for (int x = minX - 1; x < maxX; x++) {
			for (int z = minZ; z < maxZ; z++) {
				if (x == minX - 1 || z == minZ || x == maxX - 1 || z == maxZ - 1) {
					blocks.add(Objects.requireNonNull(getCreatedWorld()).getBlockAt(x, location.getBlockY() + 1, z));
				}
			}
		}
		return blocks;
	}

	public Set<Block> getFloorBlocks() {
		Set<Block> blocks = new HashSet<>();
		Location location = this.getCreatedLocation();
		Direction direction = Direction.byYaw(location.getYaw());
		int maxX;
		int maxZ;
		int minX;
		int minZ;
		switch (direction) {
		case SOUTH: {
			maxX = location.getBlockX() + 1 + 1;
			maxZ = location.getBlockZ() + length + 1;
			minX = location.getBlockX() - width + 1;
			minZ = location.getBlockZ() - 1;
			break;
		}
		case WEST: {
			maxX = location.getBlockX() + 1 + 1;
			maxZ = location.getBlockZ() + 1 + 1;
			minX = location.getBlockX() - length + 1;
			minZ = location.getBlockZ() - width;
			break;
		}
		case NORTH: {
			maxX = location.getBlockX() + width + 1;
			maxZ = location.getBlockZ() + 1 + 1;
			minX = location.getBlockX();
			minZ = location.getBlockZ() - length;
			break;
		}
		case EAST: {
			maxX = location.getBlockX() + length + 1;
			maxZ = location.getBlockZ() + width + 1;
			minX = location.getBlockX();
			minZ = location.getBlockZ() - 1;
			break;
		}
		default:
			throw new IllegalStateException("Unexpected value: " + direction);
		}
		for (int x = minX - 1; x < maxX; x++) {
			for (int z = minZ; z < maxZ; z++) {
				blocks.add(getCreatedWorld().getBlockAt(x, location.getBlockY(), z));
			}
		}
		return blocks;
	}

	public void prepareRegion(Location location, boolean natural) {
		Direction direction = Direction.byYaw(location.getYaw());
		Location location1;
		Location location2;
		switch (direction) {
		case SOUTH: {
			location1 = new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() - 10,
					location.getBlockZ() - 1);
			location2 = new Location(location.getWorld(), location.getBlockX() - width, location.getBlockY() + height,
					location.getBlockZ() + length);
			SchematicUtil.save(ID, location1.getBlock(), location2.getBlock());
			break;
		}
		case WEST: {
			location1 = new Location(location.getWorld(), location.getBlockX() + 1, location.getBlockY() - 10,
					location.getBlockZ() + 1);
			location2 = new Location(location.getWorld(), location.getBlockX() - length, location.getBlockY() + height,
					location.getBlockZ() - width);
			SchematicUtil.save(ID, location1.getBlock(), location2.getBlock());
			break;
		}
		case NORTH: {
			location1 = new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() - 10,
					location.getBlockZ() + 1);
			location2 = new Location(location.getWorld(), location.getBlockX() + width, location.getBlockY() + height,
					location.getBlockZ() - length);
			SchematicUtil.save(ID, location1.getBlock(), location2.getBlock());
			break;
		}
		case EAST: {
			location1 = new Location(location.getWorld(), location.getBlockX() + length, location.getBlockY() - 10,
					location.getBlockZ() - 1);
			location2 = new Location(location.getWorld(), location.getBlockX() - 1, location.getBlockY() + height,
					location.getBlockZ() + width);
			SchematicUtil.save(ID, location1.getBlock(), location2.getBlock());
			break;
		}
		}
		if (natural)
			return;
		for (Block floorBlock : getFloorBlocks()) {
			for (int y = location.getBlockY() + height; y > location.getBlockY() - 1; y--) {
				Block airBlock = Objects.requireNonNull(location.getWorld()).getBlockAt(floorBlock.getX(), y,
						floorBlock.getZ());
				airBlock.setType(Material.AIR, false);
			}
			floorBlock.setType(floorMaterial);
		}
		int maxX = 0;
		int maxZ = 0;
		int minX = 0;
		int minZ = 0;
		switch (direction) {
		case SOUTH: {
			maxX = location.getBlockX() + 1 + 1;
			maxZ = location.getBlockZ() + length + 1;
			minX = location.getBlockX() - width + 1;
			minZ = location.getBlockZ() - 1;
			break;
		}
		case WEST: {
			maxX = location.getBlockX() + 1 + 1;
			maxZ = location.getBlockZ() + 1 + 1;
			minX = location.getBlockX() - length + 1;
			minZ = location.getBlockZ() - width;
			break;
		}
		case NORTH: {
			maxX = location.getBlockX() + width + 1;
			maxZ = location.getBlockZ() + 1 + 1;
			minX = location.getBlockX();
			minZ = location.getBlockZ() - length;
			break;
		}
		case EAST: {
			maxX = location.getBlockX() + length + 1;
			maxZ = location.getBlockZ() + width + 1;
			minX = location.getBlockX();
			minZ = location.getBlockZ() - 1;
			break;
		}
		}
		for (int x = minX - 1; x < maxX; x++) {
			for (int z = minZ; z < maxZ; z++) {
				if (x == minX - 1 || z == minZ || x == maxX - 1 || z == maxZ - 1) {
					Block borderBlock = Objects.requireNonNull(location.getWorld()).getBlockAt(x,
							location.getBlockY() + 1, z);
					borderBlock.setType(plotSize.getBorderMaterial());
				}
			}
		}
	}

	public void unloadPlot() {
		if (!SchematicUtil.isOldSerialization(ID)) {
			SchematicUtil.paste(ID);
			return;
		}
		long time = System.currentTimeMillis();
		Location location = null;
		switch (createdDirection) {
		case SOUTH: {
			location = new Location(getCreatedWorld(), createdLocation.getBlockX() - width,
					createdLocation.getBlockY() - 10, createdLocation.getBlockZ() - 1);
			break;
		}
		case WEST: {
			location = new Location(getCreatedWorld(), createdLocation.getBlockX() - length,
					createdLocation.getBlockY() - 10, createdLocation.getBlockZ() - width);
			break;
		}
		case NORTH: {
			location = new Location(getCreatedWorld(), createdLocation.getBlockX() - 1,
					createdLocation.getBlockY() - 10, createdLocation.getBlockZ() - length);
			break;
		}
		case EAST: {
			location = new Location(getCreatedWorld(), createdLocation.getBlockX() - 1,
					createdLocation.getBlockY() - 10, createdLocation.getBlockZ() - 1);
			break;
		}
		}
		OldSchematicUtil.paste(ID, location);
		VirtualRealty.debug("Region pasted in: " + (System.currentTimeMillis() - time) + " ms [old-serialization]");
	}

	private void modified() {
		modified = Instant.now();
	}

	private void initializeFloor() {
		for (Block floorBlock : getFloorBlocks()) {
			floorBlock.setType(this.floorMaterial);
		}
	}

	private String getNonMemberPermissionsString() {
		StringBuilder permissions = new StringBuilder();
		for (RegionPermission permission : this.nonMemberPermissions) {
			permissions.append(permission.name()).append("¦");
		}
		return permissions.toString();
	}

	private String getSerializedCreatedLocation() {
		return Objects.requireNonNull(this.getCreatedWorld()).getName() + ";" + this.createdLocation.getX() + ";"
				+ this.createdLocation.getY() + ";" + this.createdLocation.getZ() + ";" + this.createdLocation.getYaw()
				+ ";" + this.createdLocation.getPitch() + ";";
	}

	@SneakyThrows
	public void insert() {
		try (Connection conn = Database.getInstance().getConnection();
				PreparedStatement ps = conn
						.prepareStatement("INSERT INTO `" + VirtualRealty.getPluginConfiguration().mysql.plotsTableName
								+ "` (`ID`, `ownedBy`, `nonMemberPermissions`, `assignedBy`, `ownedUntilDate`,"
								+ " `floorMaterial`, `borderMaterial`, `plotSize`, `length`, `width`, `height`,"
								+ " `createdLocation`, `created`, `modified`, `selectedGameMode`) "
								+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
			ps.setInt(1, this.ID);
			ps.setString(2, (this.ownedBy == null ? "" : this.ownedBy.toString()));
			ps.setString(3, getNonMemberPermissionsString());
			ps.setString(4, (this.assignedBy == null ? "null" : this.assignedBy));
			ps.setTimestamp(5, Timestamp.valueOf(this.ownedUntilDate));
			ps.setString(6, this.floorMaterial.toString());
			ps.setString(7, this.borderMaterial.toString());
			ps.setString(8, this.plotSize.toString());
			ps.setInt(9, this.length);
			ps.setInt(10, this.width);
			ps.setInt(11, this.height);
			ps.setString(12, getSerializedCreatedLocation());
			ps.setTimestamp(13, Timestamp.from(Instant.now()));
			ps.setTimestamp(14, Timestamp.from(Instant.now()));
			ps.setString(15, this.selectedGameMode.name());
			ps.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SneakyThrows
	public void update() {
		try (Connection conn = Database.getInstance().getConnection();
				PreparedStatement ps = conn
						.prepareStatement("UPDATE `" + VirtualRealty.getPluginConfiguration().mysql.plotsTableName
								+ "` SET `ownedBy`= ?," + " `nonMemberPermissions`= ?," + " `assignedBy`= ?,"
								+ " `ownedUntilDate`= ?," + " `floorMaterial`= ?," + " `borderMaterial`= ?,"
								+ " `plotSize`= ?," + " `length`= ?," + " `width`= ?," + " `height`= ?,"
								+ " `modified`= ?," + " `selectedGameMode`= ?" + " WHERE `ID`= ?")) {
			ps.setString(1, (this.ownedBy == null ? "" : this.ownedBy.toString()));
			ps.setString(2, getNonMemberPermissionsString());
			ps.setString(3, (this.assignedBy == null ? "null" : this.assignedBy));
			ps.setTimestamp(4, Timestamp.valueOf(this.ownedUntilDate));
			ps.setString(5, this.floorMaterial.toString());
			ps.setString(6, this.borderMaterial.toString());
			ps.setString(7, this.plotSize.toString());
			ps.setInt(8, this.length);
			ps.setInt(9, this.width);
			ps.setInt(10, this.height);
			ps.setTimestamp(11,
					(this.modified != null ? Timestamp.from(this.modified) : Timestamp.from(Instant.now())));
			ps.setString(12, this.selectedGameMode.name());
			ps.setInt(13, this.ID);
			ps.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void remove(CommandSender sender) {
		if (plotSize != PlotSize.AREA) {
			if (SchematicUtil.doesPlotFileExist(ID)) {
				this.unloadPlot();
				SchematicUtil.deletePlotFile(ID);
			} else {
				sender.sendMessage(VirtualRealty.PREFIX + VirtualRealty.getMessages().noRegionFileFound);
			}
		}
		removeAllMembers();
		if (VirtualRealty.getDynmapManager() != null) {
			DynmapManager.removeDynMapMarker(this);
		}
		try (Connection conn = Database.getInstance().getConnection();
				PreparedStatement ps = conn.prepareStatement("DELETE FROM `"
						+ VirtualRealty.getPluginConfiguration().mysql.plotsTableName + "` WHERE `ID` = ?")) {
			ps.setInt(1, this.ID);
			ps.execute();
		} catch (Exception e) {
			e.printStackTrace();
		}
		PlotManager.getInstance().removePlotFromCollection(this);
		VirtualRealty.debug("Removed plot #" + this.ID);
	}

	public Direction getCreatedDirection() {
		return createdDirection;
	}

	public void updateMarker() {
		DynmapManager.resetPlotMarker(this);
	}

	@Override
	public String toString() {
		return "{ ID: " + ID + ", ownedBy: " + ownedBy + "}";
	}

}