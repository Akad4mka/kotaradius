package net.arm.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OutlineConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = Paths.get("config", "arm", "kotaradius.json");

    private static OutlineConfig instance;

    public enum Profile {
        @SerializedName("custom") CUSTOM,
        @SerializedName("funtime") FUNTIME,
        @SerializedName("holyworld") HOLYWORLD,
        @SerializedName("reallyworld") REALLYWORLD
    }

    @SerializedName("globalOutlineEnabled")
    public boolean globalOutlineEnabled = true;

    @SerializedName("currentProfile")
    public Profile currentProfile = Profile.CUSTOM;

    @SerializedName("outlines_custom")
    public List<OutlineEntry> outlinesCustom = new ArrayList<>();

    @SerializedName("outlines_funtime")
    public List<OutlineEntry> outlinesFuntime = new ArrayList<>();

    @SerializedName("outlines_holyworld")
    public List<OutlineEntry> outlinesHolyworld = new ArrayList<>();

    @SerializedName("outlines_reallyworld")
    public List<OutlineEntry> outlinesReallyworld = new ArrayList<>();

    private final Map<String, OutlineEntry> entryMap = new HashMap<>();

    public static OutlineConfig getInstance() {
        if (instance == null) {
            instance = new OutlineConfig();
            instance.load();
        }
        return instance;
    }

    public List<OutlineEntry> getCurrentList() {
        return switch (currentProfile) {
            case FUNTIME -> outlinesFuntime;
            case HOLYWORLD -> outlinesHolyworld;
            case REALLYWORLD -> outlinesReallyworld; // ДОБАВЛЕНО ТУТ
            default -> outlinesCustom;
        };
    }


    public void addEntry(Item item, int radius) {
        String itemId = Registries.ITEM.getId(item).toString();
        List<OutlineEntry> currentList = getCurrentList();


        currentList.removeIf(e -> e.item.equals(itemId));


        currentList.add(new OutlineEntry(itemId, true, radius, "square"));

        rebuildEntryMap();
        save();
    }

    public void load() {
        try {
            if (CONFIG_PATH.toFile().exists()) {
                OutlineConfig loaded = GSON.fromJson(new FileReader(CONFIG_PATH.toFile()), OutlineConfig.class);
                if (loaded != null) {
                    this.globalOutlineEnabled = loaded.globalOutlineEnabled;
                    this.currentProfile = loaded.currentProfile != null ? loaded.currentProfile : Profile.CUSTOM;
                    this.outlinesCustom = loaded.outlinesCustom != null ? loaded.outlinesCustom : new ArrayList<>();
                    this.outlinesFuntime = loaded.outlinesFuntime != null ? loaded.outlinesFuntime : new ArrayList<>();
                    this.outlinesHolyworld = loaded.outlinesHolyworld != null ? loaded.outlinesHolyworld : new ArrayList<>();
                    // ДОБАВЛЕНО ТУТ:
                    this.outlinesReallyworld = loaded.outlinesReallyworld != null ? loaded.outlinesReallyworld : new ArrayList<>();
                }
            } else {
                setupDefaults();
            }
        } catch (Exception e) {
            setupDefaults();
        }
        rebuildEntryMap();
    }

    private void setupDefaults() {
        outlinesFuntime.add(new OutlineEntry("minecraft:phantom_membrane", true, 2, "circle"));
        outlinesFuntime.add(new OutlineEntry("minecraft:ender_eye", true, 10, "circle"));
        outlinesFuntime.add(new OutlineEntry("minecraft:sugar", true, 10,"circle"));
        outlinesFuntime.add(new OutlineEntry("minecraft:fire_charge", true, 10, "circle"));
        outlinesFuntime.add(new OutlineEntry("minecraft:dried_kelp", true, 2, "wall"));
        outlinesFuntime .add(new OutlineEntry("minecraft:netherite_scrap", true, 1, "square"));

        outlinesHolyworld.add(new OutlineEntry("minecraft:fire_charge", true, 3, "circle"));
        outlinesHolyworld.add(new OutlineEntry("minecraft:prismarine_shard", true, 2, "square"));
        outlinesHolyworld.add(new OutlineEntry("minecraft:jack_o_lantern", true, 14, "circle"));
        outlinesHolyworld.add(new OutlineEntry("minecraft:nether_star", true, 15, "square"));
        outlinesHolyworld.add(new OutlineEntry("minecraft:popped_chorus_fruit", true, 1, "square"));

        outlinesReallyworld.add(new OutlineEntry("minecraft:heart_of_the_sea", true, 2, "cube"));
        outlinesReallyworld.add(new OutlineEntry("minecraft:firework_star", true, 16, "circle"));

    }

    public void save() {
        try {
            CONFIG_PATH.getParent().toFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void rebuildEntryMap() {
        entryMap.clear();
        for (OutlineEntry entry : getCurrentList()) {
            entryMap.put(entry.item, entry);
        }
    }

    public OutlineEntry getEntryFor(Item item) {
        String itemId = Registries.ITEM.getId(item).toString();
        return entryMap.get(itemId);
    }

    public Map<String, OutlineEntry> getEntries() {
        return entryMap;
    }

    public void removeEntry(Item item) {
        String itemId = Registries.ITEM.getId(item).toString();
        getCurrentList().removeIf(e -> e.item.equals(itemId));
        rebuildEntryMap();
        save();
    }

    public static class OutlineEntry {
        @SerializedName("item") public String item;
        @SerializedName("outlineEnabled") public Boolean outlineEnabled;
        @SerializedName("radius") public int radius;
        @SerializedName("height") public int height;
        @SerializedName("shape") public String shape = "square";

        public OutlineEntry(String item, Boolean outlineEnabled, int radius) {
            this.item = item;
            this.outlineEnabled = outlineEnabled;
            this.radius = radius;
            this.height = height;
        }

        public OutlineEntry(String item, Boolean outlineEnabled, int radius, String shape) {
            this(item, outlineEnabled, radius);
            this.shape = shape;
        }
    }
}