package com.example.util;

import com.example.MasterControl;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AutoTaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("master-control-task");
    private static AutoTaskManager instance;

    private final MinecraftClient client;
    private TaskStage currentStage = TaskStage.IDLE;
    private int tickCounter = 0;
    private int waitTicks = 5;

    // 模式区分
    private TaskMode currentMode = TaskMode.NORMAL;
    // 补给模式相关
    private List<Integer> supplySlots = null;
    private int supplySlotIndex = 0;

    // 聊天等待相关
    private boolean awaitingChat = false;
    private long soundDetectedTime = 0;

    // 第二界面点击记录（用于正常模式）
    private int lastClickedSecondScreenSlot = -1;

    private AutoTaskManager() {
        this.client = MinecraftClient.getInstance();
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    public static AutoTaskManager getInstance() {
        if (instance == null) {
            instance = new AutoTaskManager();
        }
        return instance;
    }

    // 音效触发时调用（由 Mixin 触发）
    public void onSoundDetected() {
        if (!ModState.getInstance().isMasterEnabled()) {
            LOGGER.info("Master switch is off, ignoring sound.");
            return;
        }
        if (currentStage != TaskStage.IDLE) {
            LOGGER.info("Task already running, ignoring sound.");
            return;
        }
        awaitingChat = true;
        soundDetectedTime = System.currentTimeMillis();
        LOGGER.info("Sound detected, waiting for chat message '您获得' or '用完了'...");
    }

    // 聊天消息到达时调用（由 MasterControl 注册的事件触发）
    public void onChatMessageReceived(String message) {
        if (!awaitingChat) return;
        if (message.contains("您获得")) {
            LOGGER.info("Chat message contains '您获得', starting normal task.");
            awaitingChat = false;
            currentMode = TaskMode.NORMAL;
            startTask();
        } else if (message.contains("用完了")) {
            LOGGER.info("Chat message contains '用完了', starting supply task.");
            awaitingChat = false;
            currentMode = TaskMode.SUPPLY;
            startTask();
        }
        // 超时（5秒）后自动取消等待
        if (System.currentTimeMillis() - soundDetectedTime > 5000) {
            LOGGER.info("Chat wait timeout, resetting.");
            awaitingChat = false;
        }
    }

    public void startTask() {
        if (currentStage == TaskStage.IDLE) {
            lastClickedSecondScreenSlot = -1;
            supplySlots = null;
            supplySlotIndex = 0;
            currentStage = TaskStage.SELECT_HOTBAR_SLOT_2;
            tickCounter = 0;
            LOGGER.info("Auto task started in mode: {}", currentMode);
        } else {
            LOGGER.info("Task already running, ignoring start request.");
        }
    }

    private void tick() {
        if (currentStage == TaskStage.IDLE || client.player == null || client.interactionManager == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < waitTicks) {
            return;
        }
        tickCounter = 0;

        LOGGER.info("Task stage: {}", currentStage);

        switch (currentStage) {
            case SELECT_HOTBAR_SLOT_2:
                selectHotbarSlot(1);
                currentStage = TaskStage.RIGHT_CLICK_ITEM;
                LOGGER.info("Stage: SELECT_HOTBAR_SLOT_2");
                break;

            case RIGHT_CLICK_ITEM:
                rightClickItem();
                currentStage = TaskStage.WAIT_FIRST_SCREEN;
                LOGGER.info("Stage: RIGHT_CLICK_ITEM");
                break;

            case WAIT_FIRST_SCREEN:
                if (client.currentScreen instanceof GenericContainerScreen) {
                    currentStage = TaskStage.CLICK_BACKPACK_SLOT;
                }
                break;

            case CLICK_BACKPACK_SLOT:
                clickBackpackSlot();
                currentStage = TaskStage.WAIT_SECOND_SCREEN;
                LOGGER.info("Stage: CLICK_BACKPACK_SLOT");
                break;

            case WAIT_SECOND_SCREEN:
                if (client.currentScreen instanceof GenericContainerScreen) {
                    currentStage = TaskStage.CHECK_REMAINING;
                }
                break;

            case CHECK_REMAINING:
                if (currentMode == TaskMode.NORMAL) {
                    if (checkRemainingAndRightClick()) {
                        currentStage = TaskStage.WAIT_THIRD_SCREEN;
                        LOGGER.info("Stage: CHECK_REMAINING - remaining found, right clicked");
                    } else {
                        currentStage = TaskStage.IDLE;
                        LOGGER.info("Stage: CHECK_REMAINING - no remaining, task finished");
                    }
                } else { // SUPPLY
                    // 初始化补给槽位列表
                    supplySlots = List.of(31, 32, 33, 34, 35, 40, 41, 42, 43, 44);
                    supplySlotIndex = 0;
                    currentStage = TaskStage.SUPPLY_CHECK_LOOP;
                    LOGGER.info("Stage: CHECK_REMAINING -> SUPPLY_CHECK_LOOP");
                }
                break;

            case WAIT_THIRD_SCREEN:
                if (client.currentScreen instanceof GenericContainerScreen) {
                    currentStage = TaskStage.SELECT_ITEM_BY_STATE;
                }
                break;

            case SELECT_ITEM_BY_STATE:
                selectItemByState(); // 内部会设置下一个阶段（CLOSE_SCREEN 或 IDLE）
                LOGGER.info("Stage: SELECT_ITEM_BY_STATE - item selected: {}", currentStage != TaskStage.IDLE);
                break;

            case CLOSE_SCREEN:
                if (client.currentScreen != null) {
                    // 模拟按下 ESC 键
                    int keyCode = GLFW.GLFW_KEY_ESCAPE;
                    int scanCode = 0;
                    int modifiers = 0;
                    boolean handled = client.currentScreen.keyPressed(keyCode, scanCode, modifiers);
                    LOGGER.info("Simulated ESC key press, handled: {}", handled);
                } else {
                    LOGGER.info("No screen to close.");
                }
                currentStage = TaskStage.WAIT_SCREEN_CLOSE;
                tickCounter = 0;
                break;

            case WAIT_SCREEN_CLOSE:
                if (client.currentScreen == null) {
                    LOGGER.info("Screen closed, proceeding to hotbar selection.");
                    currentStage = TaskStage.SELECT_HOTBAR_6;
                    tickCounter = 0;
                } else {
                    if (tickCounter >= 10) {
                        LOGGER.warn("Screen did not close after 10 ticks, forcing close.");
                        client.setScreen(null);
                        currentStage = TaskStage.SELECT_HOTBAR_6;
                        tickCounter = 0;
                    }
                }
                break;

            case SELECT_HOTBAR_6:
                if (client.player != null) {
                    client.player.getInventory().selectedSlot = 5; // 快捷栏第6格
                    LOGGER.info("Selected hotbar slot 6.");
                }
                currentStage = TaskStage.RIGHT_CLICK_HOTBAR_6;
                tickCounter = 0;
                break;

            case RIGHT_CLICK_HOTBAR_6:
                if (client.player != null && client.interactionManager != null) {
                    client.interactionManager.interactItem(client.player, client.player.getActiveHand());
                    LOGGER.info("Right clicked with hotbar slot 6.");
                }
                currentStage = TaskStage.IDLE;
                tickCounter = 0;
                LOGGER.info("Task completed.");
                break;

            // 补给模式专用阶段
            case SUPPLY_CHECK_LOOP:
                if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
                    if (supplySlotIndex >= supplySlots.size()) {
                        currentStage = TaskStage.CLOSE_SCREEN;
                        LOGGER.info("Supply loop finished, proceeding to close screen.");
                        break;
                    }
                    int slotGlobal = supplySlots.get(supplySlotIndex);
                    int playerInvStart = getPlayerInventoryStartSlot(containerScreen);
                    if (slotGlobal >= playerInvStart) {
                        LOGGER.warn("Slot {} is beyond container range, skipping.", slotGlobal);
                        supplySlotIndex++;
                        break;
                    }
                    Slot slot = containerScreen.getScreenHandler().getSlot(slotGlobal);
                    ItemStack stack = slot.getStack();
                    if (!stack.isEmpty() && hasSupplyText(stack)) {
                        LOGGER.info("Found supply slot at global index {}, clicking.", slotGlobal);
                        clickSlot(containerScreen, slotGlobal, 0, SlotActionType.PICKUP);
                        supplySlotIndex++; // 已处理，下一个
                        currentStage = TaskStage.SUPPLY_ENTER_PANEL;
                        break;
                    }
                    // 没有补给字样，继续下一个
                    supplySlotIndex++;
                } else {
                    LOGGER.error("Current screen is not a container in SUPPLY_CHECK_LOOP");
                    currentStage = TaskStage.IDLE;
                }
                break;

            case SUPPLY_ENTER_PANEL:
                if (client.currentScreen instanceof GenericContainerScreen) {
                    LOGGER.info("Entered supply panel, now selecting item.");
                    currentStage = TaskStage.SUPPLY_SELECT_ITEM;
                }
                break;

            case SUPPLY_SELECT_ITEM:
                if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
                    boolean selected = selectSupplyItem(containerScreen);
                    if (!selected) {
                        LOGGER.info("No enabled item found in supply panel, closing screen.");
                        client.setScreen(null); // 强制关闭
                    }
                    currentStage = TaskStage.SUPPLY_RETURN_TO_LOOP;
                } else {
                    LOGGER.error("Expected container screen in SUPPLY_SELECT_ITEM");
                    currentStage = TaskStage.SUPPLY_RETURN_TO_LOOP;
                }
                break;

            case SUPPLY_RETURN_TO_LOOP:
                if (client.currentScreen instanceof GenericContainerScreen) {
                    LOGGER.info("Returned to second screen, continuing supply loop.");
                    currentStage = TaskStage.SUPPLY_CHECK_LOOP;
                } else if (client.currentScreen == null) {
                    // 等待屏幕出现
                }
                break;
        }
    }

    // ========== 辅助方法 ==========

    private void selectHotbarSlot(int slot) {
        if (client.player != null) {
            client.player.getInventory().selectedSlot = slot;
        }
    }

    private void rightClickItem() {
        if (client.interactionManager != null && client.player != null) {
            client.interactionManager.interactItem(client.player, client.player.getActiveHand());
        }
    }

    private void clickBackpackSlot() {
        if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
            int playerInvStart = getPlayerInventoryStartSlot(containerScreen);
            if (playerInvStart == -1) {
                LOGGER.error("Cannot find player inventory start slot!");
                currentStage = TaskStage.IDLE;
                return;
            }
            // 第2行第7列 → 相对偏移15
            int slotIndex = playerInvStart + 15;
            LOGGER.info("Clicking backpack slot: global index {}, player inventory start at {}", slotIndex, playerInvStart);
            clickSlot(containerScreen, slotIndex, 1, SlotActionType.PICKUP);
        }
    }

    private int getPlayerInventoryStartSlot(GenericContainerScreen screen) {
        for (int i = 0; i < screen.getScreenHandler().slots.size(); i++) {
            if (screen.getScreenHandler().slots.get(i).inventory == client.player.getInventory()) {
                return i;
            }
        }
        return -1;
    }

    private boolean checkRemainingAndRightClick() {
        if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
            int playerInvStart = getPlayerInventoryStartSlot(containerScreen);
            if (playerInvStart == -1) return false;

            int containerSize = playerInvStart;
            LOGGER.info("Second screen: container size = {}, total slots = {}", containerSize, containerScreen.getScreenHandler().slots.size());

            int[] slotsToCheck = {28, 37};
            for (int slotIndex : slotsToCheck) {
                if (slotIndex >= containerSize) {
                    LOGGER.warn("Slot {} is beyond container size {}, skipping", slotIndex, containerSize);
                    continue;
                }
                Slot slot = containerScreen.getScreenHandler().getSlot(slotIndex);
                ItemStack stack = slot.getStack();
                if (!stack.isEmpty()) {
                    boolean hasRemaining = hasRemainingText(stack);
                    LOGGER.info("Slot {} has remaining: {}", slotIndex, hasRemaining);
                    if (!hasRemaining) {
                        clickSlot(containerScreen, slotIndex, 1, SlotActionType.PICKUP);
                        lastClickedSecondScreenSlot = slotIndex;
                        LOGGER.info("Clicked slot {} (no remaining) to proceed to third screen.", slotIndex);
                        return true;
                    }
                }
            }
            LOGGER.info("No suitable slot (without remaining) found.");
        }
        return false;
    }

    private boolean hasRemainingText(ItemStack stack) {
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.ADVANCED);
        for (Text text : tooltip) {
            if (text.getString().contains("剩余")) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSupplyText(ItemStack stack) {
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.ADVANCED);
        for (Text text : tooltip) {
            if (text.getString().contains("补给槽位")) {
                return true;
            }
        }
        return false;
    }

    private void selectItemByState() {
        if (!(client.currentScreen instanceof GenericContainerScreen containerScreen)) {
            LOGGER.error("Current screen is not a container, cannot select item.");
            currentStage = TaskStage.IDLE;
            return;
        }

        int playerInvStart = getPlayerInventoryStartSlot(containerScreen);
        if (playerInvStart == -1) {
            LOGGER.error("Cannot find player inventory start slot in third screen!");
            currentStage = TaskStage.IDLE;
            return;
        }
        LOGGER.info("Third screen: player inventory starts at slot {}", playerInvStart);

        Map<String, String> valueToButtonMap;
        if (lastClickedSecondScreenSlot == 28) {
            valueToButtonMap = Map.of(
                    "+2", "普通鱼饵",
                    "+4", "罕见鱼饵",
                    "+7", "稀有鱼饵",
                    "+10", "传奇鱼饵"
            );
            LOGGER.info("Using bait mapping (slot 28)");
        } else if (lastClickedSecondScreenSlot == 37) {
            valueToButtonMap = Map.of(
                    "+4", "普通鱼线",
                    "+7", "罕见鱼线",
                    "+10", "稀有鱼线",
                    "+15", "传奇鱼线"
            );
            LOGGER.info("Using line mapping (slot 37)");
        } else {
            LOGGER.error("Unknown last clicked slot: {}, cannot proceed", lastClickedSecondScreenSlot);
            currentStage = TaskStage.IDLE;
            return;
        }

        DefaultedList<Slot> slots = containerScreen.getScreenHandler().slots;
        Map<String, Boolean> states = ModState.getInstance().getAllStates();

        for (int i = 0; i < playerInvStart; i++) {
            if (i >= slots.size()) break;
            Slot slot = slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.ADVANCED);
            String foundValue = null;
            for (Text text : tooltip) {
                String line = text.getString();
                for (String value : valueToButtonMap.keySet()) {
                    if (line.contains(value)) {
                        foundValue = value;
                        break;
                    }
                }
                if (foundValue != null) break;
            }

            if (foundValue != null) {
                String buttonName = valueToButtonMap.get(foundValue);
                Boolean enabled = states.get(buttonName);
                LOGGER.info("Slot {}: found value '{}' -> button '{}', enabled: {}", i, foundValue, buttonName, enabled);
                if (enabled != null && enabled) {
                    LOGGER.info("Selected enabled item: {} (slot {})", buttonName, i);
                    clickSlot(containerScreen, i, 0, SlotActionType.PICKUP);
                    currentStage = TaskStage.CLOSE_SCREEN;
                    tickCounter = 0;
                    return;
                }
            } else {
                LOGGER.debug("Slot {}: no matching value found in tooltip", i);
            }
        }

        LOGGER.info("No enabled item found in container.");
        currentStage = TaskStage.IDLE;
        tickCounter = 0;
    }

    private boolean selectSupplyItem(GenericContainerScreen containerScreen) {
        int playerInvStart = getPlayerInventoryStartSlot(containerScreen);
        if (playerInvStart == -1) return false;

        Map<String, Boolean> states = ModState.getInstance().getAllStates();
        // 合并鱼饵和鱼线的数值映射
        Map<String, List<String>> valueToButtons = new HashMap<>();
        valueToButtons.put("+2", List.of("普通鱼饵"));
        valueToButtons.put("+4", List.of("罕见鱼饵", "普通鱼线"));
        valueToButtons.put("+7", List.of("稀有鱼饵", "罕见鱼线"));
        valueToButtons.put("+10", List.of("传奇鱼饵", "稀有鱼线"));
        valueToButtons.put("+15", List.of("传奇鱼线"));

        for (int i = 0; i < playerInvStart; i++) {
            Slot slot = containerScreen.getScreenHandler().slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.ADVANCED);
            for (Text text : tooltip) {
                String line = text.getString();
                for (Map.Entry<String, List<String>> entry : valueToButtons.entrySet()) {
                    if (line.contains(entry.getKey())) {
                        for (String button : entry.getValue()) {
                            if (states.getOrDefault(button, false)) {
                                LOGGER.info("Selected supply item: {} (slot {}) based on value {}", button, i, entry.getKey());
                                clickSlot(containerScreen, i, 0, SlotActionType.PICKUP);
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private void clickSlot(GenericContainerScreen screen, int slotIndex, int button, SlotActionType action) {
        if (client.interactionManager != null) {
            client.interactionManager.clickSlot(
                    screen.getScreenHandler().syncId,
                    slotIndex,
                    button,
                    action,
                    client.player
            );
        }
    }

    private enum TaskStage {
        IDLE,
        SELECT_HOTBAR_SLOT_2,
        RIGHT_CLICK_ITEM,
        WAIT_FIRST_SCREEN,
        CLICK_BACKPACK_SLOT,
        WAIT_SECOND_SCREEN,
        CHECK_REMAINING,
        WAIT_THIRD_SCREEN,
        SELECT_ITEM_BY_STATE,
        CLOSE_SCREEN,
        WAIT_SCREEN_CLOSE,
        SELECT_HOTBAR_6,
        RIGHT_CLICK_HOTBAR_6,
        // 补给模式专用
        SUPPLY_CHECK_LOOP,
        SUPPLY_ENTER_PANEL,
        SUPPLY_SELECT_ITEM,
        SUPPLY_RETURN_TO_LOOP
    }

    private enum TaskMode {
        NORMAL,
        SUPPLY
    }
}