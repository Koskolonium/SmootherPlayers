package IudexIndustries.player;

import IudexIndustries.util.PositionCodec;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * One viewer watching one other player.
 *
 * Holds direct references to both sessions, so the range check is just two field reads and a
 * distance, with no map lookups. Apart from {@link #removed()} and {@link #enhanced()}, all
 * state is only touched on the viewer's netty event loop.
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class VisiblePlayer {

    /** Window in which a vanilla move counts as "already sent this tick". */
    private static final long TICK_NANOS = 50_000_000L;

    private final PlayerSession viewer;
    private final PlayerSession target;

    // Where the viewer's client currently has the target, in 1/4096 block steps.
    private long baseX;
    private long baseY;
    private long baseZ;
    private boolean baseKnown;

    private volatile boolean enhanced;
    @Getter(AccessLevel.NONE)
    private boolean handoffPending;
    @Getter(AccessLevel.NONE)
    private long lastVanillaMove = Long.MIN_VALUE / 2;
    private volatile boolean removed;

    // ------------------------------------------------------------ range

    /** Whether the target is inside the viewer's enhanced radius. Safe from any thread. */
    public boolean isInRange() {
        if (!viewer.positionKnown() || !target.positionKnown()) return false;
        double radiusSquared = viewer.enhancedRadiusSquared();
        return radiusSquared > 0 && viewer.position().distanceSquared(target.position()) <= radiusSquared;
    }

    /**
     * Re-checks the range and records switches between enhanced and vanilla. Leaving the radius
     * raises the handoff flag; coming back clears it. Viewer's event loop only.
     */
    public boolean updateEnhanced() {
        boolean inRange = isInRange();
        if (inRange != enhanced) {
            handoffPending = !inRange;
            enhanced = inRange;
        }
        return inRange;
    }

    /** The target has just left the radius and vanilla hasn't taken over yet. */
    public boolean takeHandoff() {
        boolean pending = handoffPending;
        handoffPending = false;
        return pending;
    }

    public void vanillaMoveSent() {
        lastVanillaMove = System.nanoTime();
    }

    /** Vanilla already updated this pair within the last tick. */
    public boolean vanillaSentThisTick() {
        return System.nanoTime() - lastVanillaMove < TICK_NANOS;
    }

    // ------------------------------------------------------------ client base position

    public void setBase(double x, double y, double z) {
        setBaseSteps(PositionCodec.encode(x), PositionCodec.encode(y), PositionCodec.encode(z));
    }

    public void setBaseSteps(long x, long y, long z) {
        baseX = x;
        baseY = y;
        baseZ = z;
        baseKnown = true;
    }

    /** The client's base became something we can't reproduce; the next move must be absolute. */
    public void forgetBase() {
        baseKnown = false;
    }

    /** An absolute vanilla packet put the client exactly in sync. */
    public void syncedTo(double x, double y, double z) {
        setBase(x, y, z);
        handoffPending = false;
    }

    /** A vanilla relative move (in steps) went through; the client added it to its base, so do the same. */
    public void applyVanillaMove(long dx, long dy, long dz) {
        if (baseKnown) {
            baseX += dx;
            baseY += dy;
            baseZ += dz;
        }
        vanillaMoveSent();
    }

    // ------------------------------------------------------------ lifecycle

    void remove() {
        removed = true;
        target.watchers().remove(this);
    }
}
