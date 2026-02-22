package com.example.gui;

import com.example.util.ModState;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;

public class MasterControlScreen extends Screen {
    private static final int BUTTON_WIDTH = 120;
    private static final int BUTTON_HEIGHT = 20;
    private static final int COLUMNS = 4; // 每行显示4个按钮
    private final ModState modState = ModState.getInstance();

    public MasterControlScreen() {
        super(Text.literal("Master Control Panel"));
    }

    @Override
    protected void init() {
        super.init();

        // 总开关按钮（位于屏幕顶部居中）
        ButtonWidget masterButton = ButtonWidget.builder(
                getMasterButtonText(),
                btn -> {
                    modState.toggleMaster();
                    btn.setMessage(getMasterButtonText());
                }
        ).dimensions(width / 2 - 60, 10, 120, 20).build();
        addDrawableChild(masterButton);

        // 原有物品按钮列表（起始Y坐标下移，避免遮挡总开关）
        int startX = (width - (COLUMNS * BUTTON_WIDTH + (COLUMNS - 1) * 5)) / 2;
        int startY = 40; // 从纵坐标40开始

        Map<String, Boolean> states = modState.getAllStates();
        int index = 0;
        for (Map.Entry<String, Boolean> entry : states.entrySet()) {
            String name = entry.getKey();
            boolean enabled = entry.getValue();

            int row = index / COLUMNS;
            int col = index % COLUMNS;
            int x = startX + col * (BUTTON_WIDTH + 5);
            int y = startY + row * (BUTTON_HEIGHT + 5);

            ButtonWidget button = ButtonWidget.builder(
                    getButtonText(name, enabled),
                    btn -> {
                        modState.toggleState(name);
                        btn.setMessage(getButtonText(name, modState.getState(name)));
                    }
            ).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();

            addDrawableChild(button);
            index++;
        }
    }

    private Text getMasterButtonText() {
        String status = modState.isMasterEnabled() ? "开启" : "关闭";
        return Text.literal("总开关: ").append(
                Text.literal(status).formatted(modState.isMasterEnabled() ? Formatting.GREEN : Formatting.RED)
        );
    }

    private Text getButtonText(String name, boolean enabled) {
        String status = enabled ? "是" : "否";
        return Text.literal(name + ": ").append(Text.literal(status).formatted(enabled ? Formatting.GREEN : Formatting.RED));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}