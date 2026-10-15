package com.treasurehunter.scan;

import net.minecraft.network.chat.Component;

/** 只用于服务器的玩家容器过滤力度。 */
public enum ServerFilterMode {
    OFF("off"), CONSERVATIVE("conservative"), AGGRESSIVE("aggressive");

    private final String id;

    ServerFilterMode(String id) { this.id = id; }

    public Component label() { return Component.translatable("option.treasurehunter.server_mode." + id); }

    public ServerFilterMode next() { return values()[(ordinal() + 1) % values().length]; }
}
