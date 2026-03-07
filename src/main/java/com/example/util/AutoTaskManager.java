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

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoTaskManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("master-control-task");
    private static AutoTaskManager instance;

    private final MinecraftClient client;
    private TaskStage currentStage = TaskStage.IDLE;
    private int tickCounter = 0;
    private int waitTicks = 5;
    private TaskMode currentMode = TaskMode.NORMAL;

    private long stageStartTime = 0;

    private boolean awaitingSoundChat = false; // 声音触发后等待聊天
    private long soundDetectedTime = 0;
    // 任务队列，存储待执行的任务及其触发时间
    private final Queue<PendingTask> pendingTasks = new LinkedList<>();
    private boolean taskRunning = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    // 正常模式要检查的两个格子
    private final int[] normalSlotsToCheck = {28, 37};
    private int normalSlotIndex = 0; // 当前正在检查的格子索引（0 表示第一个）

    // 补给模式相关
    private List<Integer> supplySlots = null;
    private int supplySlotIndex = 0;

    // 第二界面点击记录（用于正常模式）
    private int lastClickedSecondScreenSlot = -1;
    // 新增：第二界面验证重试计数
    private int secondScreenRetries = 0;
    private static final int MAX_SECOND_SCREEN_RETRIES = 3;

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

    // 音效触发
    public void onSoundDetected() {
        if (!ModState.getInstance().isSoundEnabled()) {
            LOGGER.info("Sound switch is off, ignoring sound.");
            return;
        }
        if (awaitingSoundChat) {
            LOGGER.info("Already awaiting chat for sound, ignoring duplicate sound.");
            return;
        }
        LOGGER.info("Sound detected, now awaiting chat message '您获得'.");
        awaitingSoundChat = true;
        soundDetectedTime = System.currentTimeMillis();
    }

    // 聊天消息到达
    public void onChatMessageReceived(String message) {
        if (message.contains("用完了")) {
            if (!ModState.getInstance().isSupplyEnabled()) {
                LOGGER.info("Supply switch is off, ignoring '用完了'.");
                return;
            }
            boolean hasPendingSupply = false;
            for (PendingTask task : pendingTasks) {
                if (task.mode == TaskMode.SUPPLY) {
                    hasPendingSupply = true;
                    break;
                }
            }
            if (!taskRunning && !hasPendingSupply) {
                LOGGER.info("Chat contains '用完了', queuing SUPPLY task.");
                pendingTasks.offer(new PendingTask(TaskMode.SUPPLY, System.currentTimeMillis()));
            } else {
                LOGGER.info("Ignoring '用完了' because task is running or supply already pending.");
            }
            return;
        }

        if (message.contains("您获得")) {
            if (awaitingSoundChat) {
                LOGGER.info("Chat contains '您获得', queuing NORMAL task (from sound).");
                awaitingSoundChat = false;
                pendingTasks.offer(new PendingTask(TaskMode.NORMAL, System.currentTimeMillis()));
                tryStartNextTask();
            } else {
                LOGGER.info("Chat contains '您获得', attempting to start next pending task.");
                tryStartNextTask();
            }
        }
    }

    private void tryStartNextTask() {
        if (taskRunning) return;
        PendingTask next = pendingTasks.poll();
        if (next != null) {
            startTask(next.mode);
        }
    }

    private void startTask(TaskMode mode) {
        LOGGER.info("Starting task in mode: {}", mode);
        taskRunning = true;
        currentMode = mode;
        lastClickedSecondScreenSlot = -1;
        supplySlots = null;
        supplySlotIndex = 0;
        currentStage = TaskStage.SELECT_HOTBAR_SLOT_2;
        tickCounter = 0;
        normalSlotIndex = 0;
        secondScreenRetries = 0; // 重置重试计数
    }

    private void finishTask() {
        LOGGER.info("Task finished.");
        taskRunning = false;
        currentStage = TaskStage.IDLE;
        tryStartNextTask(); // 启动队列中的下一个任务
    }

    private void tick() {
        // ----- 超时检查：声音触发后等待“您获得”超过20秒则取消等待 -----
        if (awaitingSoundChat && System.currentTimeMillis() - soundDetectedTime > 20000) {
            LOGGER.info("Awaiting chat for sound timed out after 20 seconds, resetting.");
            awaitingSoundChat = false;
        }

        // 如果当前没有任务运行，或者游戏状态不满足条件，则跳过
        if (!taskRunning || currentStage == TaskStage.IDLE || client.player == null || client.interactionManager == null) {
            return;
        }

        tickCounter++;
        if (tickCounter < waitTicks) {
            return;
        }
        tickCounter = 0;

        LOGGER.info("Task stage: {} (mode: {})", currentStage, currentMode);

        // ----- 任务状态机 -----
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
                if (stageStartTime == 0) stageStartTime = System.currentTimeMillis();
                if (client.currentScreen instanceof GenericContainerScreen) {
                    stageStartTime = 0;
                    currentStage = TaskStage.CLICK_BACKPACK_SLOT;
                } else if (System.currentTimeMillis() - stageStartTime > 5000) { // 5秒超时
                    LOGGER.warn("Wait for first screen timed out, forcing proceed.");
                    stageStartTime = 0;
                    currentStage = TaskStage.CLICK_BACKPACK_SLOT;
                }
                break;

            case CLICK_BACKPACK_SLOT:
                clickBackpackSlot();
                currentStage = TaskStage.WAIT_SECOND_SCREEN;
                LOGGER.info("Stage: CLICK_BACKPACK_SLOT");
                break;

            case WAIT_SECOND_SCREEN:
                if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
                    if (isCorrectSecondScreen(containerScreen)) {
                        currentStage = TaskStage.CHECK_REMAINING;
                        secondScreenRetries = 0;
                    } else {
                        secondScreenRetries++;
                        LOGGER.warn("Second screen verification failed (attempt {}), retrying click backpack slot.", secondScreenRetries);
                        if (secondScreenRetries >= MAX_SECOND_SCREEN_RETRIES) {
                            LOGGER.error("Max retries reached for second screen, aborting task.");
                            finishTask();
                        } else {
                            // 回退到点击背包槽位，重新尝试
                            currentStage = TaskStage.CLICK_BACKPACK_SLOT;
                        }
                    }
                }
                break;

            case CHECK_REMAINING:
                if (currentMode == TaskMode.NORMAL) {
                    if (normalSlotIndex >= normalSlotsToCheck.length) {
                        // 两个格子都检查完毕，关闭屏幕
                        currentStage = TaskStage.CLOSE_SCREEN;
                        LOGGER.info("All normal slots checked, closing screen.");
                        break;
                    }
                    int slotIndex = normalSlotsToCheck[normalSlotIndex];
                    if (checkRemainingAndRightClick(slotIndex)) {
                        // 该格子需要补充，进入第三界面
                        currentStage = TaskStage.WAIT_THIRD_SCREEN;
                        LOGGER.info("Stage: CHECK_REMAINING - need supply for slot {}, entering third screen", slotIndex);
                    } else {
                        // 格子不需要补充或格子为空，直接检查下一个
                        normalSlotIndex++;
                    }
                } else { // SUPPLY
                    // 原有补给模式逻辑不变
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
                selectItemByState(); // 内部会设置下一阶段
                break;

            case CLOSE_SCREEN:
                if (client.currentScreen != null) {
                    int keyCode = GLFW.GLFW_KEY_ESCAPE;
                    int scanCode = 0;
                    int modifiers = 0;
                    boolean handled = client.currentScreen.keyPressed(keyCode, scanCode, modifiers);
                    LOGGER.info("Simulated ESC key press, handled: {}", handled);
                } else {
                    LOGGER.info("No screen to close.");
                }
                // 调度延迟任务，0.5秒后执行后续操作
                scheduler.schedule(() -> {
                    client.execute(() -> {
                        // 确保屏幕已关闭（如果仍开着则强制关闭）
                        if (client.currentScreen != null) {
                            LOGGER.info("Screen still open after delay, forcing close.");
                            client.setScreen(null);
                        }
                        // 选择快捷栏第6格并右键
                        if (client.player != null) {
                            client.player.getInventory().selectedSlot = 5;
                            LOGGER.info("Selected hotbar slot 6 after delay.");
                        }
                        if (client.player != null && client.interactionManager != null) {
                            client.interactionManager.interactItem(client.player, client.player.getActiveHand());
                            LOGGER.info("Right clicked with hotbar slot 6 after delay.");
                        }
                        // 结束任务
                        finishTask();
                    });
                }, 500, TimeUnit.MILLISECONDS);
                // 将当前阶段设为 IDLE，避免重复执行
                currentStage = TaskStage.IDLE;
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
                    client.player.getInventory().selectedSlot = 5;
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
                LOGGER.info("Task completed.");
                finishTask();
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
                    if (!stack.isEmpty()) {
                        if (hasUnlockedText(stack)) {
                            LOGGER.info("Slot {} contains '未解锁', skipping.", slotGlobal);
                            supplySlotIndex++;
                            break;
                        }
                        if (hasSupplyText(stack)) {
                            LOGGER.info("Found supply slot at global index {}, clicking.", slotGlobal);
                            clickSlot(containerScreen, slotGlobal, 0, SlotActionType.PICKUP);
                            supplySlotIndex++;
                            currentStage = TaskStage.SUPPLY_ENTER_PANEL;
                            break;
                        }
                    }
                    supplySlotIndex++;
                } else {
                    LOGGER.error("Current screen is not a container in SUPPLY_CHECK_LOOP");
                    finishTask();
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
                        LOGGER.info("No enabled item found in supply panel, clicking slot 0 to close.");
                        clickSlot(containerScreen, 0, 0, SlotActionType.PICKUP);
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
                }
                break;
        }
    }
    // 新增：验证第二界面是否正确（通过槽位28是否有“鱼饵”字样）
    private boolean isCorrectSecondScreen(GenericContainerScreen screen) {
        try {
            if (screen.getScreenHandler().slots.size() <= 28) {
                LOGGER.debug("Second screen has less than 29 slots, cannot verify.");
                return false;
            }
            Slot slot = screen.getScreenHandler().getSlot(28);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) {
                LOGGER.debug("Slot 28 is empty, second screen may be incorrect.");
                return false;
            }
            List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.ADVANCED);
            for (Text text : tooltip) {
                if (text.getString().contains("鱼饵")) {
                    return true;
                }
            }
            LOGGER.debug("Slot 28 does not contain '鱼饵' in tooltip.");
        } catch (Exception e) {
            LOGGER.error("Error verifying second screen", e);
        }
        return false;
    }

    private boolean hasUnlockedText(ItemStack stack) {
        List<Text> tooltip = stack.getTooltip(Item.TooltipContext.DEFAULT, client.player, TooltipType.ADVANCED);
        for (Text text : tooltip) {
            if (text.getString().contains("未解锁")) {
                return true;
            }
        }
        return false;
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
                finishTask();
                return;
            }
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

    private boolean checkRemainingAndRightClick(int slotIndex) {
        if (client.currentScreen instanceof GenericContainerScreen containerScreen) {
            int playerInvStart = getPlayerInventoryStartSlot(containerScreen);
            if (playerInvStart == -1) return false;

            int containerSize = playerInvStart;
            if (slotIndex >= containerSize) {
                LOGGER.warn("Slot {} is beyond container size {}, skipping", slotIndex, containerSize);
                return false;
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
            finishTask();
            return;
        }

        int playerInvStart = getPlayerInventoryStartSlot(containerScreen);
        if (playerInvStart == -1) {
            LOGGER.error("Cannot find player inventory start slot in third screen!");
            finishTask();
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
            finishTask();
            return;
        }

        DefaultedList<Slot> slots = containerScreen.getScreenHandler().slots;
        Map<String, Boolean> states = ModState.getInstance().getAllStates();
        Map<String, Integer> priorities = ModState.getInstance().getAllPriorities();

        List<Candidate> candidates = new ArrayList<>();

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
                if (enabled != null && enabled) {
                    int priority = priorities.getOrDefault(buttonName, 0);
                    candidates.add(new Candidate(i, buttonName, priority));
                }
            } else {
                LOGGER.debug("Slot {}: no matching value found in tooltip", i);
            }
        }

        if (candidates.isEmpty()) {
            LOGGER.info("No enabled item found for slot {}, proceeding to next slot.", lastClickedSecondScreenSlot);
            normalSlotIndex++;
            currentStage = TaskStage.CHECK_REMAINING;
            tickCounter = 0;
            return;
        }

        Random rand = new Random();
        candidates.sort((a, b) -> {
            if (a.priority != b.priority) {
                return Integer.compare(b.priority, a.priority); // 大的在前
            } else {
                return rand.nextBoolean() ? -1 : 1;
            }
        });

        Candidate chosen = candidates.get(0);
        LOGGER.info("Selected enabled item: {} (slot {}) with priority {}", chosen.buttonName, chosen.slotIndex, chosen.priority);
        clickSlot(containerScreen, chosen.slotIndex, 0, SlotActionType.PICKUP);

        normalSlotIndex++;
        currentStage = TaskStage.CHECK_REMAINING;
        tickCounter = 0;
    }

    private boolean selectSupplyItem(GenericContainerScreen containerScreen) {
        int playerInvStart = getPlayerInventoryStartSlot(containerScreen);
        if (playerInvStart == -1) return false;

        Map<String, Boolean> states = ModState.getInstance().getAllStates();
        Map<String, Integer> priorities = ModState.getInstance().getAllPriorities();

        Set<String> excludedButtons = new HashSet<>(Arrays.asList(
                "普通鱼饵", "罕见鱼饵", "稀有鱼饵", "传奇鱼饵",
                "普通鱼线", "罕见鱼线", "稀有鱼线", "传奇鱼线"
        ));

        List<Candidate> candidates = new ArrayList<>();

        for (int i = 0; i < playerInvStart; i++) {
            Slot slot = containerScreen.getScreenHandler().slots.get(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String displayName = stack.getName().getString();
            String cleanName = Formatting.strip(displayName);
            if (cleanName == null) cleanName = displayName;

            for (Map.Entry<String, Boolean> entry : states.entrySet()) {
                if (!entry.getValue()) continue;
                String buttonName = entry.getKey();
                if (excludedButtons.contains(buttonName)) continue;
                if (cleanName.equals(buttonName)) {
                    int priority = priorities.getOrDefault(buttonName, 0);
                    candidates.add(new Candidate(i, buttonName, priority));
                }
            }
        }

        if (candidates.isEmpty()) return false;

        Random rand = new Random();
        candidates.sort((a, b) -> {
            if (a.priority != b.priority) {
                return Integer.compare(b.priority, a.priority); // 大的在前
            } else {
                return rand.nextBoolean() ? -1 : 1;
            }
        });

        Candidate chosen = candidates.get(0);
        LOGGER.info("Selected supply item: {} (slot {}) with priority {}", chosen.buttonName, chosen.slotIndex, chosen.priority);
        clickSlot(containerScreen, chosen.slotIndex, 0, SlotActionType.PICKUP);
        return true;
    }

    private static class Candidate {
        int slotIndex;
        String buttonName;
        int priority;

        Candidate(int slot, String name, int pri) {
            this.slotIndex = slot;
            this.buttonName = name;
            this.priority = pri;
        }
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

    private static class PendingTask {
        final TaskMode mode;
        final long triggerTime;

        PendingTask(TaskMode mode, long triggerTime) {
            this.mode = mode;
            this.triggerTime = triggerTime;
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