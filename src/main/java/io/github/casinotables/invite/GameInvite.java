package io.github.casinotables.invite;

import io.github.casinotables.GameType;

import java.util.UUID;

public record GameInvite(UUID sender, String senderName, UUID target, GameType type,
                         double bet, long createdAt, long expiresAt) {
    public boolean expired() {
        return System.currentTimeMillis() >= expiresAt;
    }
}

