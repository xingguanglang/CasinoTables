package io.github.xingguanglang.casinotables.invite;

import io.github.xingguanglang.casinotables.GameType;

import java.util.UUID;

public record GameInvite(UUID sender, String senderName, UUID target, GameType type,
                         double bet, long createdAt, long expiresAt) {
    public boolean expired() {
        return System.currentTimeMillis() >= expiresAt;
    }
}

