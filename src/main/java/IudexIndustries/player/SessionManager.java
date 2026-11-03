package IudexIndustries.player;

import IudexIndustries.config.PluginConfig;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.User;
import lombok.RequiredArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of every connected player's session.
 */
@RequiredArgsConstructor
public final class SessionManager {

    private final PluginConfig config;
    private final Map<User, PlayerSession> sessionsByUser = new ConcurrentHashMap<>();
    /** Only used when something spawns, to find out whether it's one of our players. */
    private final Map<Integer, PlayerSession> sessionsByEntityId = new ConcurrentHashMap<>();

    /** Picks up players already in game (plugin enabled late or reloaded). */
    public void registerOnlinePlayers() {
        for (User user : PacketEvents.getAPI().getProtocolManager().getUsers()) {
            if (user.getConnectionState() == ConnectionState.PLAY && user.getEntityId() != -1) {
                register(user, user.getEntityId());
            }
        }
    }

    public PlayerSession get(User user) {
        return sessionsByUser.get(user);
    }

    /** The player joined (or rejoined after reconfiguration) with this entity id. */
    public PlayerSession register(User user, int entityId) {
        PlayerSession existing = sessionsByUser.get(user);
        if (existing != null && existing.entityId() == entityId) {
            existing.stopWatchingAll();
            return existing;
        }
        if (existing != null) unregister(existing);

        PlayerSession session = new PlayerSession(user, entityId, config);
        sessionsByUser.put(user, session);
        sessionsByEntityId.put(entityId, session);
        return session;
    }

    public void unregister(User user) {
        PlayerSession session = sessionsByUser.get(user);
        if (session != null) unregister(session);
    }

    private void unregister(PlayerSession session) {
        sessionsByUser.remove(session.user(), session);
        sessionsByEntityId.remove(session.entityId(), session);
        session.markDisconnected();
        for (VisiblePlayer watcher : session.watchers()) watcher.remove();

        // Viewer state belongs to the player's own event loop.
        Object channel = session.user().getChannel();
        if (channel != null) ChannelHelper.runInEventLoop(channel, session::stopWatchingAll);
    }

    // ------------------------------------------------------------ viewer events (viewer's event loop)

    /** A player entity was spawned on the viewer's client. Ignores NPCs and anything else that isn't a real player. */
    public void onSpawn(PlayerSession viewer, int entityId, double x, double y, double z) {
        PlayerSession target = sessionsByEntityId.get(entityId);
        if (target != null && target != viewer && !target.disconnected()) {
            viewer.startWatching(target, x, y, z);
        }
    }

    public void onDespawn(PlayerSession viewer, int entityId) {
        viewer.stopWatching(entityId);
    }

    public void onClientReset(PlayerSession viewer) {
        viewer.stopWatchingAll();
        viewer.vehicleId(-1);
    }
}
