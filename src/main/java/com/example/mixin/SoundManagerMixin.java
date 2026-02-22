package com.example.mixin;

import com.example.util.AutoTaskManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import net.minecraft.sound.SoundEvents;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public class SoundManagerMixin {
    private static final Logger LOGGER = LogManager.getLogger("master-control-sound");

    @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
    private void onPlaySound(SoundInstance soundInstance, CallbackInfo ci) {
        if (soundInstance == null) return;
        var sound = soundInstance.getSound();
        if (sound == null) {
            String str = soundInstance.toString();
            int start = str.indexOf('[');
            int end = str.indexOf(']', start);
            if (start != -1 && end != -1) {
                String content = str.substring(start + 1, end);
                String idStr = content.startsWith("sound:") ? content.substring(6) : content;
                if (idStr.equals(SoundEvents.ENTITY_ITEM_BREAK.getId().toString())) {
                    LOGGER.info("Item break sound detected via toString!");
                    AutoTaskManager.getInstance().onSoundDetected(); // 改为等待聊天
                }
            }
        } else {
            var id = sound.getLocation();
            if (id.equals(SoundEvents.ENTITY_ITEM_BREAK.getId())) {
                LOGGER.info("Item break sound detected!");
                AutoTaskManager.getInstance().onSoundDetected(); // 改为等待聊天
            }
        }
    }
}