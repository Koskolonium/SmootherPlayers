package IudexIndustries.player;

import IudexIndustries.config.PluginConfig;
import com.github.retrooper.packetevents.protocol.player.User;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One connected player. Every player is both a mover (others watch them) and a viewer (they watch
 * others).
 *
 * Mover state is written from this player's netty thread and read from other players' threads,
 * so it's volatile. Viewer state is only ever touched on this player's own netty event loop
 * (their inbound and outbound packets both run there), so it needs no synchronisation at all.
 */
@Getter
public final class PlayerSession {

    /** Recount the crowd around this viewer every this many of their own movement packets (~250ms). */
    private static final int MOVES_PER_RADIUS_REFRESH = 5;

    private final User user;
    private final int entityId;

    // ---- as a mover
    private volatile PlayerPosition position = PlayerPosition.UNKNOWN;
    private volatile boolean positionKnown;
    @Setter
    private volatile int vehicleId = -1;
    @Getter(AccessLevel.NONE)
    private volatile int pendingTeleportId = -1;
    private volatile boolean disconnected;
    /** Viewers watching this player. Iterated on every move, changed only on spawn / despawn. */
    private final List<VisiblePlayer> watchers = new CopyOnWriteArrayList<>();

    // ---- as a viewer (own event loop only)
    @Getter(AccessLevel.NONE)
    private final Int2ObjectOpenHashMap<VisiblePlayer> visiblePlayers = new Int2ObjectOpenHashMap<>();
    /** Read by movers' threads as a cheap pre-check, hence volatile. */
    @Getter(AccessLevel.PACKAGE)
    private volatile double enhancedRadiusSquared;
    @Getter(AccessLevel.NONE)
    private int movesUntilRadiusRefresh;

    PlayerSession(User user, int entityId, PluginConfig config) {
        this.user = user;
        this.entityId = entityId;
        this.enhancedRadiusSquared = config.enhancedRadiusSquared(0);
    }

    // ------------------------------------------------------------ mover

    /** Called with each movement packet this player's client sends. */
    public void updatePosition(PlayerPosition position, boolean includesPosition) {
        this.position = position;
        if (includesPosition) positionKnown = true;
    }

    public boolean isRiding() {
        return vehicleId != -1;
    }

    /** True between the server teleporting this player and their client confirming it. */
    public boolean awaitingTeleport() {
        return pendingTeleportId != -1;
    }

    public void teleportSent(int teleportId) {
        pendingTeleportId = teleportId;
    }

    public void teleportConfirmed(int teleportId) {
        if (teleportId == pendingTeleportId) pendingTeleportId = -1;
    }

    void markDisconnected() {
        disconnected = true;
    }

    // ------------------------------------------------------------ viewer (own event loop only)

    public VisiblePlayer visiblePlayer(int entityId) {
        return visiblePlayers.get(entityId);
    }

    /** The target was spawned on this player's client at the given position. */
    void startWatching(PlayerSession target, double x, double y, double z) {
        VisiblePlayer visible = new VisiblePlayer(this, target);
        visible.setBase(x, y, z);
        VisiblePlayer previous = visiblePlayers.put(target.entityId, visible);
        if (previous != null) previous.remove();
        target.watchers.add(visible);
    }

    void stopWatching(int entityId) {
        VisiblePlayer visible = visiblePlayers.remove(entityId);
        if (visible != null) visible.remove();
    }

    /** The client dropped every entity (respawn, world change, rejoin). */
    void stopWatchingAll() {
        for (VisiblePlayer visible : visiblePlayers.values()) visible.remove();
        visiblePlayers.clear();
    }

    /**
     * Called with each of this player's own movement packets: every few of them, recount the
     * players around this viewer and shrink or grow their enhanced radius to match.
     */
    public void tickEnhancedRadius(PluginConfig config) {
        if (--movesUntilRadiusRefresh > 0 || !positionKnown) return;
        movesUntilRadiusRefresh = MOVES_PER_RADIUS_REFRESH;

        PlayerPosition self = position;
        double crowdRadiusSquared = config.crowdRadiusSquared();
        int nearby = 0;
        for (VisiblePlayer visible : visiblePlayers.values()) {
            PlayerSession other = visible.target();
            if (other.positionKnown && self.distanceSquared(other.position) <= crowdRadiusSquared) nearby++;
        }
        enhancedRadiusSquared = config.enhancedRadiusSquared(nearby);
    }
}
