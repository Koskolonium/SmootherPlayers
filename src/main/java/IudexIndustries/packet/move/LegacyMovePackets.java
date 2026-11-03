package IudexIndustries.packet.move;

import IudexIndustries.player.VisiblePlayer;
import IudexIndustries.util.PositionCodec;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;

/**
 * 1.21 - 26.2. PacketEvents takes the delta in blocks and multiplies it back into steps when
 * writing; since our steps are whole numbers, that round trip is exact.
 */
final class LegacyMovePackets implements MovePackets {

    @Override
    public PacketWrapper<?> move(int entityId, long dx, long dy, long dz, boolean onGround) {
        return new WrapperPlayServerEntityRelativeMove(entityId,
                PositionCodec.toBlocks(dx), PositionCodec.toBlocks(dy), PositionCodec.toBlocks(dz), onGround);
    }

    @Override
    public PacketWrapper<?> moveAndRotate(int entityId, long dx, long dy, long dz,
                                          float yaw, float pitch, boolean onGround) {
        return new WrapperPlayServerEntityRelativeMoveAndRotation(entityId,
                PositionCodec.toBlocks(dx), PositionCodec.toBlocks(dy), PositionCodec.toBlocks(dz),
                yaw, pitch, onGround);
    }

    @Override
    public void applyVanillaMove(VisiblePlayer visible, WrapperPlayServerEntityRelativeMove packet) {
        visible.applyVanillaMove(PositionCodec.encode(packet.getDeltaX()),
                PositionCodec.encode(packet.getDeltaY()), PositionCodec.encode(packet.getDeltaZ()));
    }

    @Override
    public void applyVanillaMove(VisiblePlayer visible, WrapperPlayServerEntityRelativeMoveAndRotation packet) {
        visible.applyVanillaMove(PositionCodec.encode(packet.getDeltaX()),
                PositionCodec.encode(packet.getDeltaY()), PositionCodec.encode(packet.getDeltaZ()));
    }
}
