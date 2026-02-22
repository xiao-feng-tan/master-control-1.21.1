package com.example.util;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModState {
    private static ModState instance;
    private final Map<String, Boolean> buttonStates = new LinkedHashMap<>();
    private boolean masterEnabled = false; // 默认关闭
    private ModState() {
        // 初始化所有按钮状态为 false（否）
        String[] buttonNames = {
                "普通鱼饵", "罕见鱼饵", "稀有鱼饵", "传奇鱼饵",
                "普通鱼线", "罕见鱼线", "稀有鱼线", "传奇鱼线",
                "强力鱼钩增强器", "智力鱼钩增强器", "闪光鱼钩增强器", "冒险鱼钩增强器", "幸运鱼钩增强器",
                "经验磁铁增强器", "鱼类磁铁增强器", "宝箱磁铁增强器", "珍珠磁铁增强器", "灵魂磁铁增强器",
                "强力鱼竿增强器", "智力鱼竿增强器", "闪光鱼竿增强器", "冒险鱼竿增强器", "幸运鱼竿增强器"
        };
        for (String name : buttonNames) {
            buttonStates.put(name, false);
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
    }

    public Map<String, Boolean> getAllStates() {
        return new LinkedHashMap<>(buttonStates);
    }
    public boolean isMasterEnabled() {
        return masterEnabled;
    }

    public void toggleMaster() {
        masterEnabled = !masterEnabled;
    }



}

