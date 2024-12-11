package com.modnmetl.virtualrealty.model.plot;

import java.util.AbstractMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import com.modnmetl.virtualrealty.model.other.VItem;
import com.modnmetl.virtualrealty.util.data.ItemBuilder;
import com.modnmetl.virtualrealty.util.data.SkullUtil;

import de.tr7zw.changeme.nbtapi.NBT;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class PlotItem {

	public static final String NBT_PREFIX = "vrplot_";

	private final VItem itemType;
	private final PlotSize plotSize;
	private final int length;
	private final int height;
	private final int width;
	private final Map.Entry<String, Byte> floorData;
	private final Map.Entry<String, Byte> borderData;
	private final boolean natural;
	private int additionalDays;
	private UUID uuid;

	public ItemStack getItemStack() {
		ItemBuilder itemBuilder = (uuid == null)
				? new ItemBuilder(
						SkullUtil.getSkull("16bb9fb97ba87cb727cd0ff477f769370bea19ccbfafb581629cd5639f2fec2b"))
				: new ItemBuilder(
						SkullUtil.getSkull("16bb9fb97ba87cb727cd0ff477f769370bea19ccbfafb581629cd5639f2fec2b", uuid));
		switch (itemType) {
		case CLAIM: {
			itemBuilder.setName("§a" + plotSize.name().toCharArray()[0] + plotSize.name().substring(1).toLowerCase()
					+ " Plot Claim");
			break;
		}
		case DRAFT: {
			itemBuilder.setName("§a" + plotSize.name().toCharArray()[0] + plotSize.name().substring(1).toLowerCase()
					+ " Plot Draft Claim").addEnchant(Enchantment.INFINITY, 10);
			break;
		}
		}
		Material floor;
		Material border;

		floor = Bukkit.createBlockData(floorData.getKey()).getMaterial();
		border = Bukkit.createBlockData(borderData.getKey()).getMaterial();

		itemBuilder.addLoreLine(" §8┏ §fSize: §7" + plotSize.name())
				.addLoreLine(" §8┣ §fNatural: §7" + (natural ? "Yes" : "No")).addLoreLine(" §8┣ §fLength: §7" + length)
				.addLoreLine(" §8┣ §fHeight: §7" + height).addLoreLine(" §8┣ §fWidth: §7" + width)
				.addLoreLine(" §8┣ §fFloor: §7" + (floor == Material.AIR ? "NONE" : floor.name()))
				.addLoreLine(" §8┣ §fBorder: §7" + (border == Material.AIR ? "NONE" : border.name()))
				.addLoreLine(" §8┗ §fLease days: §7" + (additionalDays == 0 ? "No Expiry" : additionalDays));
		ItemStack itemStack = itemBuilder.toItemStack();

		NBT.modify(itemStack, nbt -> {
			nbt.setString(NBT_PREFIX + "item", itemType.name());
			nbt.setString(NBT_PREFIX + "size", plotSize.name());
			nbt.setInteger(NBT_PREFIX + "length", length);
			nbt.setInteger(NBT_PREFIX + "height", height);
			nbt.setInteger(NBT_PREFIX + "width", width);
			nbt.setString(NBT_PREFIX + "floor_material", floorData.getKey());
			nbt.setByte(NBT_PREFIX + "floor_data", floorData.getValue());
			nbt.setString(NBT_PREFIX + "border_material", borderData.getKey());
			nbt.setByte(NBT_PREFIX + "border_data", borderData.getValue());
			nbt.setBoolean(NBT_PREFIX + "natural", natural);
			nbt.setInteger(NBT_PREFIX + "additional_days", additionalDays);
			nbt.setString(NBT_PREFIX + "stack_uuid", uuid == null ? UUID.randomUUID().toString() : uuid.toString());
		});
		return itemStack;
	}

	public Map.Entry<String, Byte> getLegacyFloorData() {
		return floorData;
	}

	public BlockData getFloorData() {
		return Bukkit.createBlockData(floorData.getKey());
	}

	public Map.Entry<String, Byte> getLegacyBorderData() {
		return borderData;
	}

	public BlockData getBorderData() {
		return Bukkit.createBlockData(borderData.getKey());
	}

	public static PlotItem fromItemStack(ItemStack itemStack) {
		String floorMaterial = NBT.get(itemStack, nbt -> {
			return nbt.getString("vrplot_floor_material");
		});
		byte floorData = NBT.get(itemStack, nbt -> {
			return nbt.getByte("vrplot_floor_data");
		});
		Map.Entry<String, Byte> floorDataEntry = new AbstractMap.SimpleEntry<>(floorMaterial, floorData);
		String borderMaterial = NBT.get(itemStack, nbt -> {
			return nbt.getString("vrplot_border_material");
		});
		byte borderData = NBT.get(itemStack, nbt -> {
			return nbt.getByte("vrplot_border_data");
		});
		Map.Entry<String, Byte> borderDataEntry = new AbstractMap.SimpleEntry<>(borderMaterial, borderData);
		PlotSize plotSize = PlotSize.valueOf(NBT.get(itemStack, nbt -> {
			return nbt.getString("vrplot_size");
		}));
		String item = NBT.get(itemStack, nbt -> {
			return nbt.getString(NBT_PREFIX + "item");
		});
		Integer length = NBT.get(itemStack, nbt -> {
			return nbt.getInteger(NBT_PREFIX + "length");
		});
		Integer height = NBT.get(itemStack, nbt -> {
			return nbt.getInteger(NBT_PREFIX + "height");
		});
		Integer width = NBT.get(itemStack, nbt -> {
			return nbt.getInteger(NBT_PREFIX + "width");
		});
		boolean natural = NBT.get(itemStack, nbt -> {
			return nbt.getBoolean(NBT_PREFIX + "natural");
		});
		Integer additionalDays = NBT.get(itemStack, nbt -> {
			return nbt.getInteger(NBT_PREFIX + "additional_days");
		});
		String uuid = NBT.get(itemStack, nbt -> {
			return nbt.getString(NBT_PREFIX + "stack_uuid");
		});
		return new PlotItem(VItem.valueOf(item), plotSize, length, height, width, floorDataEntry, borderDataEntry,
				natural, additionalDays, UUID.fromString(uuid));
	}

	public static UUID getPlotItemUuid(ItemStack itemStack) {
		String string = NBT.get(itemStack, nbt -> {
			return nbt.getString(NBT_PREFIX + "stack_uuid");
		});
		if (string == null || string.isEmpty())
			return UUID.randomUUID();
		return UUID.fromString(string);
	}

	public static PlotItem fromItemStack(ItemStack itemStack, VItem itemType) {
		PlotItem plotItem = fromItemStack(itemStack);
		return new PlotItem(itemType, plotItem.getPlotSize(), plotItem.getLength(), plotItem.getHeight(),
				plotItem.getWidth(), plotItem.floorData, plotItem.borderData, plotItem.isNatural(),
				plotItem.getAdditionalDays(), plotItem.getUuid());
	}

	public int getLength() {
		return ((plotSize == PlotSize.AREA || plotSize == PlotSize.CUSTOM) ? length : plotSize.getLength());
	}

	public int getHeight() {
		return ((plotSize == PlotSize.AREA || plotSize == PlotSize.CUSTOM) ? height : plotSize.getHeight());
	}

	public int getWidth() {
		return ((plotSize == PlotSize.AREA || plotSize == PlotSize.CUSTOM) ? width : plotSize.getWidth());
	}

}
