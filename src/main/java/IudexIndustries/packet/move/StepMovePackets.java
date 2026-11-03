package IudexIndustries.packet.move;

import IudexIndustries.player.VisiblePlayer;
import com.github.retrooper.packetevents.protocol.vector.vecdelta.LinearVecDelta;
import com.github.retrooper.packetevents.protocol.vector.vecdelta.SteppedVecDelta;
import com.github.retrooper.packetevents.protocol.vector.vecdelta.VecDelta;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;

/**
 * 26.3+. Relative moves carry their steps directly: a {@link LinearVecDelta} is written as three
 * raw shorts with no conversion. Vanilla may also send multi-step moves ({@link SteppedVecDelta});
 * the client applies each step in turn, so the base moves by their sum.
 */
final class StepMovePackets implements MovePackets {

    @Override
    public PacketWrapper<?> move(int entityId, long dx, long dy, long dz, boolean onGround) {
        return new WrapperPlayServerEntityRelativeMove(entityId,
                new LinearVecDelta((short) dx, (short) dy, (short) dz), onGround);
    }

    @Override
    public PacketWrapper<?> moveAndRotate(int entityId, long dx, long dy, long dz,
                                          float yaw, float pitch, boolean onGround) {
        return new WrapperPlayServerEntityRelativeMoveAndRotation(entityId,
                new LinearVecDelta((short) dx, (short) dy, (short) dz), yaw, pitch, onGround);
    }

    @Override
    public void applyVanillaMove(VisiblePlayer visible, WrapperPlayServerEntityRelativeMove packet) {
        apply(visible, packet.getDelta());
    }

    @Override
    public void applyVanillaMove(VisiblePlayer visible, WrapperPlayServerEntityRelativeMoveAndRotation packet) {
        apply(visible, packet.getDelta());
    }

    private static void apply(VisiblePlayer visible, VecDelta delta) {
        if (delta instanceof LinearVecDelta linear) {
            visible.applyVanillaMove(linear.dxRaw(), linear.dyRaw(), linear.dzRaw());
            return;
        }
        if (delta instanceof SteppedVecDelta stepped) {
            long dx = 0, dy = 0, dz = 0;
            for (SteppedVecDelta.DeltaStep step : stepped.steps()) {
                dx += step.dxRaw();
                dy += step.dyRaw();
                dz += step.dzRaw();
            }
            visible.applyVanillaMove(dx, dy, dz);
            return;
        }
        // Unknown encoding from a future version: stop trusting the base, next move goes out absolute.
        visible.forgetBase();
        visible.vanillaMoveSent();
    }
}
