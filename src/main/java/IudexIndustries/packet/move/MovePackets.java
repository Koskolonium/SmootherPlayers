package IudexIndustries.packet.move;

import IudexIndustries.player.VisiblePlayer;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;

/**
 * Builds and reads relative move packets. Deltas are in the client's 1/4096 block steps.
 *
 * There are two implementations because 26.3 changed how relative moves are encoded:
 * <ul>
 *     <li>{@link StepMovePackets} (26.3+): hands PacketEvents the raw steps, which it writes as-is.</li>
 *     <li>{@link LegacyMovePackets} (1.21 - 26.2): PacketEvents only takes deltas in blocks there.</li>
 * </ul>
 * The 26.3 implementation is never loaded on older servers, so those still work with
 * PacketEvents versions that don't have the new classes.
 */
public interface MovePackets {

    PacketWrapper<?> move(int entityId, long dx, long dy, long dz, boolean onGround);

    PacketWrapper<?> moveAndRotate(int entityId, long dx, long dy, long dz, float yaw, float pitch, boolean onGround);

    /** Mirrors a vanilla move that's going through onto the viewer's base position. */
    void applyVanillaMove(VisiblePlayer visible, WrapperPlayServerEntityRelativeMove packet);

    void applyVanillaMove(VisiblePlayer visible, WrapperPlayServerEntityRelativeMoveAndRotation packet);

    static MovePackets forServer() {
        return isAtLeast26_3() ? new StepMovePackets() : new LegacyMovePackets();
    }

    private static boolean isAtLeast26_3() {
        try {
            // Looked up by name: PacketEvents versions from before 26.3 don't have the constant.
            ServerVersion v26_3 = ServerVersion.valueOf("V_26_3");
            return PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(v26_3);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
