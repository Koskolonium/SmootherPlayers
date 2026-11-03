package IudexIndustries.packet;

import IudexIndustries.packet.move.MovePackets;
import IudexIndustries.player.PlayerPosition;
import IudexIndustries.player.VisiblePlayer;
import IudexIndustries.util.PositionCodec;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.entity.EntityPositionData;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import lombok.RequiredArgsConstructor;

/**
 * Builds and sends the same movement packets vanilla would, keeping each viewer's base position
 * in step. Packets go out "silently" so our own listener doesn't see (and cancel) them.
 *
 * Every method must run on the viewer's netty event loop.
 */
@RequiredArgsConstructor
final class MovementSender {

    private static final Vector3d NO_VELOCITY = new Vector3d(0, 0, 0);

    private final boolean usePositionSync =
            PacketEvents.getAPI().getServerManager().getVersion().isNewerThanOrEquals(ServerVersion.V_1_21_2);
    private final MovePackets movePackets;


    /** Sends a movement update for the target to this viewer. */
    void sendMove(VisiblePlayer visible, PlayerPosition position, boolean moved, boolean rotated) {
        User viewer = visible.viewer().user();
        int entityId = visible.target().entityId();

        if (moved) {
            long x = PositionCodec.encode(position.x());
            long y = PositionCodec.encode(position.y());
            long z = PositionCodec.encode(position.z());
            long dx = x - visible.baseX();
            long dy = y - visible.baseY();
            long dz = z - visible.baseZ();

            if (!visible.baseKnown()
                    || !PositionCodec.fitsInMove(dx) || !PositionCodec.fitsInMove(dy) || !PositionCodec.fitsInMove(dz)) {
                sendAbsolute(viewer, entityId, position);
            } else if (rotated) {
                viewer.sendPacketSilently(movePackets.moveAndRotate(entityId, dx, dy, dz,
                        position.yaw(), position.pitch(), position.onGround()));
            } else {
                viewer.sendPacketSilently(movePackets.move(entityId, dx, dy, dz, position.onGround()));
            }
            visible.setBaseSteps(x, y, z);
        } else if (rotated) {
            viewer.sendPacketSilently(new WrapperPlayServerEntityRotation(entityId,
                    position.yaw(), position.pitch(), position.onGround()));
        }

        if (rotated) {
            viewer.sendPacketSilently(new WrapperPlayServerEntityHeadLook(entityId, position.yaw()));
        }
    }

    /** Puts the target exactly at this position on the viewer's client. */
    void sendExactPosition(VisiblePlayer visible, PlayerPosition position) {
        sendAbsolute(visible.viewer().user(), visible.target().entityId(), position);
        visible.setBase(position.x(), position.y(), position.z());
    }

    private void sendAbsolute(User viewer, int entityId, PlayerPosition position) {
        Vector3d pos = new Vector3d(position.x(), position.y(), position.z());
        if (usePositionSync) {
            viewer.sendPacketSilently(new WrapperPlayServerEntityPositionSync(entityId,
                    new EntityPositionData(pos, NO_VELOCITY, position.yaw(), position.pitch()), position.onGround()));
        } else {
            viewer.sendPacketSilently(new WrapperPlayServerEntityTeleport(entityId, pos,
                    position.yaw(), position.pitch(), position.onGround()));
        }
    }
}
