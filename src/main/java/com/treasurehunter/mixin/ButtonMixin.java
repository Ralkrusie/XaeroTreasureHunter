package com.treasurehunter.mixin;

import com.treasurehunter.xaero.XaeroWaypointEditor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.InputWithModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 只监听已识别的 Xaero 确认按钮，不替换其逻辑，也不引用 Xaero 类型。 */
@Mixin(Button.class)
public abstract class ButtonMixin {
    @Unique private XaeroWaypointEditor.Commit treasurehunter$commit;

    @Inject(method = "onPress", at = @At("HEAD"))
    private void treasurehunter$before(InputWithModifiers input, CallbackInfo ci) {
        treasurehunter$commit = XaeroWaypointEditor.beforeConfirm((Button) (Object) this);
    }

    @Inject(method = "onPress", at = @At("RETURN"))
    private void treasurehunter$after(InputWithModifiers input, CallbackInfo ci) {
        var commit = treasurehunter$commit;
        treasurehunter$commit = null;
        XaeroWaypointEditor.afterConfirm(commit);
    }
}
