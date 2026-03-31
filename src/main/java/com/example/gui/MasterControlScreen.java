package com.example.gui;

import com.example.util.ModState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class MasterControlScreen extends Screen {
    private static final int MAIN_BUTTON_WIDTH = 100;          // 主按钮宽度
    private static final int SMALL_BUTTON_WIDTH = 20;          // +/- 按钮宽度
    private static final int BUTTON_HEIGHT = 20;
    private static final int COLUMNS_PER_GROUP = 2;            // 每行2个物品
    private static final int ITEM_SPACING = 2;                 // 物品内部间距（主按钮与减号之间，减号与加号之间）
    private static final int COLUMN_SPACING = 10;              // 列与列之间的间距
    private static final int GROUP_SPACING = 30;               // 组间垂直间距
    private static final int TITLE_HEIGHT = 15;                // 标题高度
    private static final int BORDER_PADDING = 5;               // 边框内边距

    private final ModState modState = ModState.getInstance();
    private final List<ButtonGroup> groups = new ArrayList<>();
    private double scrollY = 0;
    private int contentHeight;

    public MasterControlScreen() {
        super(Text.literal("Master Control Panel"));
        buildGroups();
    }

    private void buildGroups() {
        // 新增功能开关组（两个独立开关）
        groups.add(new ButtonGroup("功能开关", Arrays.asList("换鱼饵/鱼线", "换道具")));
        groups.add(new ButtonGroup("鱼饵", Arrays.asList(
                "普通鱼饵", "罕见鱼饵", "稀有鱼饵", "传奇鱼饵"
        )));
        groups.add(new ButtonGroup("鱼线", Arrays.asList(
                "普通鱼线", "罕见鱼线", "稀有鱼线", "传奇鱼线"
        )));
        groups.add(new ButtonGroup("鱼钩增强器", Arrays.asList(
                "强力鱼钩增强器", "智力鱼钩增强器", "闪光鱼钩增强器", "冒险鱼钩增强器", "幸运鱼钩增强器"
        )));
        groups.add(new ButtonGroup("磁铁增强器", Arrays.asList(
                "经验磁铁增强器", "鱼群磁铁增强器", "宝箱磁铁增强器", "珍珠磁铁增强器", "灵魂磁铁增强器"
        )));
        groups.add(new ButtonGroup("鱼竿增强器", Arrays.asList(
                "强力鱼竿增强器", "智力鱼竿增强器", "闪光鱼竿增强器", "冒险鱼竿增强器", "幸运鱼竿增强器"
        )));
        groups.add(new ButtonGroup("xx之饵", Arrays.asList(
                "隐秘之饵", "点数之饵", "珍珠之饵", "宝藏之饵", "灵魂之饵"
        )));
        groups.add(new ButtonGroup("强力xx之饵", Arrays.asList(
                "强力隐秘之饵", "强力点数之饵", "强力珍珠之饵", "强力宝藏之饵", "强力灵魂之饵"
        )));
        groups.add(new ButtonGroup("护符", Arrays.asList(
                "强力护符", "智力护符", "闪光护符", "冒险护符", "幸运护符"
        )));
        groups.add(new ButtonGroup("其他", Arrays.asList(
                "能量饮料", "超级钓竿", "库存补充器", "纯净灯笼"
        )));
    }

    @Override
    protected void init() {
        super.init();

        // 清空所有组的按钮列表
        for (ButtonGroup group : groups) {
            group.itemWidgets.clear();
        }

        int yOffset = 10;

        for (ButtonGroup group : groups) {
            int itemsStartY = yOffset + TITLE_HEIGHT;

            // 计算起始X，使得整个组居中
            int totalWidthPerColumn = MAIN_BUTTON_WIDTH + 2 * SMALL_BUTTON_WIDTH + 2 * ITEM_SPACING;
            int totalWidthForTwoColumns = totalWidthPerColumn * 2 + COLUMN_SPACING;
            int startX = (width - totalWidthForTwoColumns) / 2;

            for (int i = 0; i < group.buttonNames.size(); i++) {
                String name = group.buttonNames.get(i);
                int col = i % COLUMNS_PER_GROUP;
                int row = i / COLUMNS_PER_GROUP;
                int baseX = startX + col * (totalWidthPerColumn + COLUMN_SPACING);
                int y = itemsStartY + row * (BUTTON_HEIGHT + 5);

                if (group.title.equals("功能开关")) {
                    // 创建两个独立开关按钮（没有加减号）
                    if (i == 0) {
                        // 换鱼饵/鱼线开关
                        ButtonWidget soundBtn = ButtonWidget.builder(
                                getSoundButtonText(),
                                btn -> {
                                    modState.toggleSound();
                                    btn.setMessage(getSoundButtonText());
                                }
                        ).dimensions(baseX, y, MAIN_BUTTON_WIDTH, BUTTON_HEIGHT).build();
                        addDrawableChild(soundBtn);
                        group.itemWidgets.add(new ItemWidget(soundBtn, null, null));
                    } else if (i == 1) {
                        // 换道具开关
                        ButtonWidget supplyBtn = ButtonWidget.builder(
                                getSupplyButtonText(),
                                btn -> {
                                    modState.toggleSupply();
                                    btn.setMessage(getSupplyButtonText());
                                }
                        ).dimensions(baseX, y, MAIN_BUTTON_WIDTH, BUTTON_HEIGHT).build();
                        addDrawableChild(supplyBtn);
                        group.itemWidgets.add(new ItemWidget(supplyBtn, null, null));
                    }
                } else {
                    // 普通物品：创建三个按钮
                    ButtonWidget mainBtn = ButtonWidget.builder(
                            getItemButtonText(name, modState.getState(name), modState.getPriority(name)),
                            btn -> {
                                modState.toggleState(name);
                                btn.setMessage(getItemButtonText(name, modState.getState(name), modState.getPriority(name)));
                            }
                    ).dimensions(baseX, y, MAIN_BUTTON_WIDTH, BUTTON_HEIGHT).build();

                    ButtonWidget minusBtn = ButtonWidget.builder(
                            Text.literal("-"),
                            btn -> {
                                modState.decreasePriority(name);
                                mainBtn.setMessage(getItemButtonText(name, modState.getState(name), modState.getPriority(name)));
                            }
                    ).dimensions(baseX + MAIN_BUTTON_WIDTH + ITEM_SPACING, y, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT).build();

                    ButtonWidget plusBtn = ButtonWidget.builder(
                            Text.literal("+"),
                            btn -> {
                                modState.increasePriority(name);
                                mainBtn.setMessage(getItemButtonText(name, modState.getState(name), modState.getPriority(name)));
                            }
                    ).dimensions(baseX + MAIN_BUTTON_WIDTH + ITEM_SPACING + SMALL_BUTTON_WIDTH + ITEM_SPACING, y, SMALL_BUTTON_WIDTH, BUTTON_HEIGHT).build();

                    addDrawableChild(mainBtn);
                    addDrawableChild(minusBtn);
                    addDrawableChild(plusBtn);

                    group.itemWidgets.add(new ItemWidget(mainBtn, minusBtn, plusBtn));
                }
            }

            int rows = (int) Math.ceil((double) group.buttonNames.size() / COLUMNS_PER_GROUP);
            yOffset += TITLE_HEIGHT + rows * (BUTTON_HEIGHT + 5) + GROUP_SPACING;
        }

        contentHeight = yOffset;
        applyScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int maxScroll = Math.max(0, contentHeight - (height - 20));
        scrollY = Math.max(0, Math.min(maxScroll, scrollY - verticalAmount * 20));
        applyScroll();
        return true;
    }

    private void applyScroll() {
        int yOffset = 10;

        for (ButtonGroup group : groups) {
            int itemsStartY = yOffset + TITLE_HEIGHT - (int) scrollY;
            int totalWidthPerColumn = MAIN_BUTTON_WIDTH + 2 * SMALL_BUTTON_WIDTH + 2 * ITEM_SPACING;
            int totalWidthForTwoColumns = totalWidthPerColumn * 2 + COLUMN_SPACING;
            int startX = (width - totalWidthForTwoColumns) / 2;

            for (int i = 0; i < group.itemWidgets.size(); i++) {
                ItemWidget widget = group.itemWidgets.get(i);
                int col = i % COLUMNS_PER_GROUP;
                int row = i / COLUMNS_PER_GROUP;
                int baseX = startX + col * (totalWidthPerColumn + COLUMN_SPACING);
                int y = itemsStartY + row * (BUTTON_HEIGHT + 5);

                if (group.title.equals("功能开关")) {
                    widget.mainBtn.setX(baseX);
                    widget.mainBtn.setY(y);
                } else {
                    widget.mainBtn.setX(baseX);
                    widget.mainBtn.setY(y);
                    if (widget.minusBtn != null) {
                        widget.minusBtn.setX(baseX + MAIN_BUTTON_WIDTH + ITEM_SPACING);
                        widget.minusBtn.setY(y);
                    }
                    if (widget.plusBtn != null) {
                        widget.plusBtn.setX(baseX + MAIN_BUTTON_WIDTH + ITEM_SPACING + SMALL_BUTTON_WIDTH + ITEM_SPACING);
                        widget.plusBtn.setY(y);
                    }
                }
            }

            int rows = (int) Math.ceil((double) group.buttonNames.size() / COLUMNS_PER_GROUP);
            yOffset += TITLE_HEIGHT + rows * (BUTTON_HEIGHT + 5) + GROUP_SPACING;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int yOffset = 10;
        for (ButtonGroup group : groups) {
            int groupTopY = yOffset - (int) scrollY;
            int rows = (int) Math.ceil((double) group.buttonNames.size() / COLUMNS_PER_GROUP);
            int groupBottomY = groupTopY + TITLE_HEIGHT + rows * (BUTTON_HEIGHT + 5);

            int totalWidthPerColumn = MAIN_BUTTON_WIDTH + 2 * SMALL_BUTTON_WIDTH + 2 * ITEM_SPACING;
            int totalWidthForTwoColumns = totalWidthPerColumn * 2 + COLUMN_SPACING;
            int left = (width - totalWidthForTwoColumns) / 2 - BORDER_PADDING;
            int right = width - left + BORDER_PADDING;
            int top = groupTopY - BORDER_PADDING;
            int bottom = groupBottomY + BORDER_PADDING;

            context.fill(left, top, right, bottom, 0x80000000);
            context.drawBorder(left, top, right - left, bottom - top, 0xFFFFFFFF);

            yOffset += TITLE_HEIGHT + rows * (BUTTON_HEIGHT + 5) + GROUP_SPACING;
        }

        super.render(context, mouseX, mouseY, delta);

        // 绘制标题（最后绘制确保在上层）
        yOffset = 10;
        for (ButtonGroup group : groups) {
            int groupTopY = yOffset - (int) scrollY;
            context.drawCenteredTextWithShadow(textRenderer, group.title, width / 2, groupTopY, 0xFFFFFF);
            int rows = (int) Math.ceil((double) group.buttonNames.size() / COLUMNS_PER_GROUP);
            yOffset += TITLE_HEIGHT + rows * (BUTTON_HEIGHT + 5) + GROUP_SPACING;
        }
    }

    private Text getSoundButtonText() {
        String status = modState.isSoundEnabled() ? "开启" : "关闭";
        return Text.literal("换鱼饵/鱼线: ").append(
                Text.literal(status).formatted(modState.isSoundEnabled() ? Formatting.GREEN : Formatting.RED)
        );
    }

    private Text getSupplyButtonText() {
        String status = modState.isSupplyEnabled() ? "开启" : "关闭";
        return Text.literal("换道具: ").append(
                Text.literal(status).formatted(modState.isSupplyEnabled() ? Formatting.GREEN : Formatting.RED)
        );
    }

    private Text getItemButtonText(String name, boolean enabled, int priority) {
        String status = enabled ? "是" : "否";
        return Text.literal(name + ": " + status + " [" + priority + "]")
                .formatted(enabled ? Formatting.GREEN : Formatting.RED);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void resize(net.minecraft.client.MinecraftClient client, int width, int height) {
        double oldScrollY = this.scrollY;
        this.clearChildren();
        this.init(client, width, height);
        int maxScroll = Math.max(0, contentHeight - (height - 20));
        this.scrollY = Math.max(0, Math.min(maxScroll, oldScrollY));
        this.applyScroll();
    }

    // 内部类：表示一个物品的控件组
    private static class ItemWidget {
        final ButtonWidget mainBtn;
        final ButtonWidget minusBtn;
        final ButtonWidget plusBtn;

        ItemWidget(ButtonWidget main, ButtonWidget minus, ButtonWidget plus) {
            this.mainBtn = main;
            this.minusBtn = minus;
            this.plusBtn = plus;
        }
    }

    // 内部类：表示一个按钮组（含标题和物品列表）
    private static class ButtonGroup {
        final String title;
        final List<String> buttonNames;
        final List<ItemWidget> itemWidgets = new ArrayList<>();

        ButtonGroup(String title, List<String> buttonNames) {
            this.title = title;
            this.buttonNames = buttonNames;
        }
    }
}