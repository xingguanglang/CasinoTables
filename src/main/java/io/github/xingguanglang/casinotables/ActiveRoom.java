package io.github.xingguanglang.casinotables;

import java.util.UUID;

/** 已经开局、仍允许中途加入或观战的房间摘要。 */
public record ActiveRoom(UUID host, String hostName, GameType type, int players,
                         int maximum, boolean playableJoin, String detail) {
    public String joinLabel() {
        return Messages.msg(playableJoin ? "lobby.active-room.take-seat" : "lobby.active-room.spectate");
    }
}
