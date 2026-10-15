package com.treasurehunter.mixin;

import com.treasurehunter.TreasureHunterMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/** 原版增量包的只读观察；网络线程调用时跳过，原版转到主线程应用后才通知扫描器。 */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Unique private BlockState treasurehunter$before;
    @Unique private final Map<BlockPos, BlockState> treasurehunter$sectionBefore = new HashMap<>();

    @Inject(method = "handleBlockUpdate", at = @At("HEAD"))
    private void treasurehunter$beforeBlock(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.isSameThread() && client.level != null) treasurehunter$before = client.level.getBlockState(packet.getPos());
    }

    @Inject(method = "handleBlockUpdate", at = @At("RETURN"))
    private void treasurehunter$afterBlock(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.level != null && TreasureHunterMod.scanner() != null && treasurehunter$before != null) {
            TreasureHunterMod.scanner().onServerBlockUpdate(client.level, packet.getPos(), treasurehunter$before, packet.getBlockState());
        }
        treasurehunter$before = null;
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
    private void treasurehunter$beforeSection(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (!client.isSameThread() || client.level == null) return;
        treasurehunter$sectionBefore.clear();
        packet.runUpdates((pos, state) -> treasurehunter$sectionBefore.put(pos.immutable(), client.level.getBlockState(pos)));
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("RETURN"))
    private void treasurehunter$afterSection(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.level != null && TreasureHunterMod.scanner() != null) packet.runUpdates((pos, state) -> {
            var before = treasurehunter$sectionBefore.get(pos);
            if (before != null) TreasureHunterMod.scanner().onServerBlockUpdate(client.level, pos, before, state);
        });
        treasurehunter$sectionBefore.clear();
    }

    @Inject(method = "handleBlockChangedAck", at = @At("RETURN"))
    private void treasurehunter$afterAck(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.level != null && TreasureHunterMod.scanner() != null) {
            TreasureHunterMod.scanner().onPredictionAcknowledged(client.level, packet.sequence());
        }
    }
}
