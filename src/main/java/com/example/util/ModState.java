package com.example.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class ModState {
    private static ModState instance;
    private final Map<String, Boolean> buttonStates = new LinkedHashMap<>();
    private final Map<String, Integer> priorities = new LinkedHashMap<>();
    private boolean masterEnabled = false; // 总开关，默认关闭

    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("master-control.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ModState() {
        load(); // 加载配置
        // 如果配置文件中没有完整初始化，则用默认值填充
        initializeDefaultsIfNeeded();
    }

    private void initializeDefaultsIfNeeded() {
        // 所有按钮名称列表（应与 GUI 中一致）
        String[] allButtons = {
                // 原有按钮（23个）
                "普通鱼饵", "罕见鱼饵", "稀有鱼饵", "传奇鱼饵",
                "普通鱼线", "罕见鱼线", "稀有鱼线", "传奇鱼线",
                "强力鱼钩增强器", "智力鱼钩增强器", "闪光鱼钩增强器", "冒险鱼钩增强器", "幸运鱼钩增强器",
                "经验磁铁增强器", "鱼群磁铁增强器", "宝箱磁铁增强器", "珍珠磁铁增强器", "灵魂磁铁增强器",
                "强力鱼竿增强器", "智力鱼竿增强器", "闪光鱼竿增强器", "冒险鱼竿增强器", "幸运鱼竿增强器",
                // 新增按钮（19个）
                "隐秘之饵", "点数之饵", "珍珠之饵", "宝藏之饵", "灵魂之饵",
                "强力隐秘之饵", "强力点数之饵", "强力珍珠之饵", "强力宝藏之饵", "强力灵魂之饵",
                "强力护符", "智力护符", "闪光护符", "冒险护符", "幸运护符",
                "能量饮料", "超级鱼竿", "库存补充器", "纯净灯笼"
        };
        for (String name : allButtons) {
            buttonStates.putIfAbsent(name, false);
            priorities.putIfAbsent(name, 0);
        }
    }

    public static ModState getInstance() {
        if (instance == null) {
            instance = new ModState();
        }
        return instance;
    }

    public boolean getState(String buttonName) {
        return buttonStates.getOrDefault(buttonName, false);
    }

    public void toggleState(String buttonName) {
        buttonStates.computeIfPresent(buttonName, (k, v) -> !v);
        save();
    }

    public Map<String, Boolean> getAllStates() {
        return new LinkedHashMap<>(buttonStates);
    }

    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    public void toggleMaster() {
        masterEnabled = !masterEnabled;
        save();
    }

    public int getPriority(String buttonName) {
        return priorities.getOrDefault(buttonName, 0);
    }

    public void setPriority(String buttonName, int priority) {
        priorities.put(buttonName, Math.max(0, Math.min(100, priority)));
        save();
    }

    public void increasePriority(String buttonName) {
        setPriority(buttonName, getPriority(buttonName) + 1);
    }

    public void decreasePriority(String buttonName) {
        setPriority(buttonName, getPriority(buttonName) - 1);
    }

    public Map<String, Integer> getAllPriorities() {
        return new LinkedHashMap<>(priorities);
    }

    // ========== 持久化 ==========
    private static class StateData {
        boolean masterEnabled;
        Map<String, Boolean> buttonStates;
        Map<String, Integer> priorities;
    }

    private void load() {
        if (!CONFIG_PATH.toFile().exists()) {
            return; // 使用默认值
        }
        try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
            StateData data = GSON.fromJson(reader, StateData.class);
            if (data != null) {
                this.masterEnabled = data.masterEnabled;
                if (data.buttonStates != null) {
                    buttonStates.clear();
                    buttonStates.putAll(data.buttonStates);
                }
                if (data.priorities != null) {
                    priorities.clear();
                    priorities.putAll(data.priorities);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void save() {
        StateData data = new StateData();
        data.masterEnabled = this.masterEnabled;
        data.buttonStates = new LinkedHashMap<>(this.buttonStates);
        data.priorities = new LinkedHashMap<>(this.priorities);

        try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

