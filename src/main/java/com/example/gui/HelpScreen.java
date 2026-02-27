package com.example.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class HelpScreen extends Screen {
    private final Screen parent;

    public HelpScreen(Screen parent) {
        super(Text.literal("使用说明"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        // 返回按钮
        addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"),
                btn -> client.setScreen(parent)
        ).dimensions(width / 2 - 50, height - 30, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        // 先绘制所有子元素（按钮）
        super.render(context, mouseX, mouseY, delta);
        // 最后绘制帮助文字，确保在最上层
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("加号 (+) : 让这个物品更优先被选择"), width / 2, height / 2 - 20, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("减号 (-) : 让这个物品更靠后被选择"), width / 2, height / 2 - 5, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("数字相同则随机选一个,数字越大越优先"), width / 2, height / 2 + 10, 0xFFFFFF);
    }
}
