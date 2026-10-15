package com.treasurehunter.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** javap 确认的原版预测处理器只读入口，用于关联客户端放置交互与服务端确认序号。 */
@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
    @Accessor("blockStatePredictionHandler")
    BlockStatePredictionHandler treasurehunter$getPredictionHandler();
}
