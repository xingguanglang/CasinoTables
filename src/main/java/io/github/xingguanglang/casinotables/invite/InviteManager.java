package io.github.xingguanglang.casinotables.invite;

import io.github.xingguanglang.casinotables.CasinoTablesPlugin;
import io.github.xingguanglang.casinotables.GameType;
import io.github.xingguanglang.casinotables.Messages;
import io.github.xingguanglang.casinotables.Text;
import io.github.xingguanglang.casinotables.lobby.GameLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InviteManager {
    private record Key(UUID sender, UUID target) {
    }

    private final CasinoTablesPlugin plugin;
    private final Map<Key, GameInvite> outgoing = new HashMap<>();

    public InviteManager(CasinoTablesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean send(Player sender, Player target, GameType type, double bet) {
        if (sender.equals(target)) {
            Text.send(sender, Messages.msg("invites.self"));
            return false;
        }
        if (plugin.isActiveGame(sender.getUniqueId()) || plugin.isBusy(target)) {
            Text.send(sender, Messages.msg("invites.busy"));
            return false;
        }
        double min = plugin.getConfig().getDouble("economy.min-bet", 1.0);
        double max = plugin.getConfig().getDouble("economy.max-bet", 10000.0);
        if (!type.usesRealCurrency() && (!Double.isFinite(bet) || bet < min || bet > max)) {
            Text.send(sender, Messages.msg("economy.bet.out-of-range",
                    "min", plugin.economy().format(min), "max", plugin.economy().format(max)));
            return false;
        }
        if (type.usesRealCurrency()) bet = 0.0;
        GameLobby lobby = plugin.lobbies().ensureForInvite(sender, type, bet);
        if (lobby == null) return false;
        if (lobby.members().contains(target.getUniqueId())) {
            Text.send(sender, Messages.msg("invites.already-member"));
            return false;
        }
        if (lobby.members().size() >= lobby.maximum()) {
            Text.send(sender, Messages.msg("lobby.full", "maximum", lobby.maximum()));
            return false;
        }

        long now = System.currentTimeMillis();
        long expires = now + plugin.getConfig().getLong("request.expire-seconds", 60L) * 1000L;
        GameInvite invite = new GameInvite(sender.getUniqueId(), sender.getName(), target.getUniqueId(),
                type, bet, now, expires);
        outgoing.put(new Key(sender.getUniqueId(), target.getUniqueId()), invite);
        String amount = plugin.economy().format(bet);
        if (type.usesRealCurrency()) {
            Text.send(sender, Messages.msg("invites.sent.real",
                    "player", target.getName(), "game", type.display()));
        } else {
            Text.send(sender, Messages.msg("invites.sent.wager",
                    "player", target.getName(), "game", type.display(), "amount", amount));
        }

        Component accept = Text.parse(Messages.msg("invites.button.accept"))
                .clickEvent(ClickEvent.runCommand("/casino accept " + sender.getName()))
                .hoverEvent(HoverEvent.showText(Text.parse(Messages.msg("invites.button.accept-hover"))));
        Component deny = Text.parse(Messages.msg("invites.button.deny"))
                .clickEvent(ClickEvent.runCommand("/casino deny " + sender.getName()))
                .hoverEvent(HoverEvent.showText(Text.parse(Messages.msg("invites.button.deny-hover"))));
        String body = type.usesRealCurrency()
                ? Messages.msg("invites.received.real", "player", sender.getName(), "game", type.display())
                : Messages.msg("invites.received.wager", "player", sender.getName(),
                        "game", type.display(), "amount", amount);
        target.sendMessage(Text.prefixed(body).append(accept).append(deny));
        return true;
    }

    public boolean accept(Player target, String senderName) {
        GameInvite invite = findIncoming(target.getUniqueId(), senderName);
        if (invite == null) {
            Text.send(target, Messages.msg("invites.not-found"));
            return false;
        }
        Player sender = Bukkit.getPlayer(invite.sender());
        Key key = new Key(invite.sender(), invite.target());
        if (sender == null) {
            outgoing.remove(key);
            Text.send(target, Messages.msg("invites.sender-offline"));
            return false;
        }
        outgoing.remove(key);
        boolean joined = plugin.lobbies().join(target, invite);
        if (joined) removeAll(target.getUniqueId());
        return joined;
    }

    public void deny(Player target, String senderName) {
        GameInvite invite = findIncoming(target.getUniqueId(), senderName);
        if (invite == null) {
            Text.send(target, Messages.msg("invites.not-found"));
            return;
        }
        outgoing.remove(new Key(invite.sender(), invite.target()));
        Text.send(target, Messages.msg("invites.denied.self", "player", invite.senderName()));
        Player sender = Bukkit.getPlayer(invite.sender());
        if (sender != null) {
            Text.send(sender, Messages.msg("invites.denied.sender", "player", target.getName()));
            GameLobby lobby = plugin.lobbies().get(sender.getUniqueId());
            if (lobby != null && lobby.host().equals(sender.getUniqueId())) {
                plugin.menus().openInvitePanel(sender);
            }
        }
    }

    public void cancel(Player sender) {
        List<GameInvite> removed = outgoing.values().stream()
                .filter(invite -> invite.sender().equals(sender.getUniqueId())).toList();
        outgoing.entrySet().removeIf(entry -> entry.getKey().sender().equals(sender.getUniqueId()));
        if (removed.isEmpty()) {
            Text.send(sender, Messages.msg("invites.cancel.none"));
            return;
        }
        Text.send(sender, Messages.msg("invites.cancel.done"));
        for (GameInvite invite : removed) {
            Player target = Bukkit.getPlayer(invite.target());
            if (target != null) {
                Text.send(target, Messages.msg("invites.cancel.target", "player", sender.getName()));
            }
        }
    }

    private GameInvite findIncoming(UUID target, String senderName) {
        long now = System.currentTimeMillis();
        outgoing.values().removeIf(invite -> invite.expiresAt() <= now);
        return outgoing.values().stream()
                .filter(invite -> invite.target().equals(target))
                .filter(invite -> senderName == null || invite.senderName().equalsIgnoreCase(senderName))
                .max(Comparator.comparingLong(GameInvite::createdAt)).orElse(null);
    }

    public List<GameInvite> incoming(UUID target) {
        long now = System.currentTimeMillis();
        outgoing.values().removeIf(invite -> invite.expiresAt() <= now);
        List<GameInvite> result = new ArrayList<>();
        for (GameInvite invite : outgoing.values()) if (invite.target().equals(target)) result.add(invite);
        result.sort(Comparator.comparingLong(GameInvite::createdAt).reversed());
        return result;
    }

    public boolean hasOutgoing(UUID sender, UUID target) {
        long now = System.currentTimeMillis();
        outgoing.values().removeIf(invite -> invite.expiresAt() <= now);
        return outgoing.containsKey(new Key(sender, target));
    }

    public void removeAll(UUID player) {
        outgoing.entrySet().removeIf(entry -> entry.getKey().sender().equals(player)
                || entry.getKey().target().equals(player));
    }

    public void clear() {
        outgoing.clear();
    }
}
