package com.modnmetl.virtualrealty.enums.permissions;

import com.modnmetl.virtualrealty.VirtualRealty;
import com.modnmetl.virtualrealty.utils.data.ItemBuilder;
import lombok.Getter;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;

@Getter
public enum RegionPermission {

    BREAK(0, "Break Blocks", new ItemBuilder(Material.STONE_PICKAXE).addItemFlag(ItemFlag.HIDE_ATTRIBUTES)),
    PLACE(1, "Place Blocks", new ItemBuilder(VirtualRealty.legacyVersion ? Material.GRASS : Material.GRASS_BLOCK)),
    CHEST_ACCESS(2, "Chest-Access", new ItemBuilder(Material.CHEST)),
    ARMOR_STAND(3, "Armor Stand", new ItemBuilder(Material.ARMOR_STAND)),
    ENTITY_DAMAGE(4, "Entity Damage", new ItemBuilder(Material.IRON_SWORD).addItemFlag(ItemFlag.HIDE_ATTRIBUTES)),
    SWITCH(5, "Switch", new ItemBuilder(Material.LEVER)),
    ITEM_USE(6, "Item Use", new ItemBuilder(Material.FLINT_AND_STEEL));

    private final int slot;
    private final String name;
    private final ItemBuilder item;

    RegionPermission(int slot, String name, ItemBuilder item) {
        this.slot = slot;
        this.name = name;
        this.item = item;
    }

    public static RegionPermission getPermission(int i) {
        for (RegionPermission value : values()) {
            if (value.getSlot() == i) return value;
        }
        return null;
    }

    public String getConfigName() {
        return name().replaceAll("_", " ");
    }

}