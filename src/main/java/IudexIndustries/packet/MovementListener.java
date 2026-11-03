package IudexIndustries.packet;

import IudexIndustries.config.PluginConfig;
import IudexIndustries.packet.move.MovePackets;
import IudexIndustries.player.PlayerPosition;
import IudexIndustries.player.PlayerSession;
import IudexIndustries.player.SessionManager;
import IudexIndustries.player.VisiblePlayer;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.event.UserDisconnectEvent;
import com.github.retrooper.packetevents.netty.channel.ChannelHelper;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientPlayerFlying;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientTeleportConfirm;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityHeadLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityPositionSync;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRelativeMoveAndRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityRotation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerJoinGame;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnPlayer;
import lombok.RequiredArgsConstructor;

/**
 * Relays player movement to nearby players the moment it arrives, instead of waiting for
 * vanilla's 2-tick entity tracker. Players outside a viewer's enhanced radius are left to vanilla.
 *
 * Inbound movement runs on the mover's netty thread; everything that touches a viewer's state is
 * moved onto that viewer's event loop, where vanilla's packets to them are encoded too.
 */
@RequiredArgsConstructor
public final class MovementListener implements PacketListener {

    private final PluginConfig config;
    private final SessionManager sessions;
    private final MovePackets movePackets = MovePackets.forServer();
    private final MovementSender sender = new MovementSender(movePackets);

    // ================================================================ client -> server

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled()) return; // e.g. an anticheat rejected it

        PacketTypeCommon type = event.getPacketType();
        if (WrapperPlayClientPlayerFlying.isFlying(type)) {
            handleClientMovement(event);
        } else if (type == PacketType.Play.Client.TELEPORT_CONFIRM) {
            PlayerSession session = sessions.get(event.getUser());
            if (session != null) session.teleportConfirmed(new WrapperPlayClientTeleportConfirm(event).getTeleportId());
        }
    }

    private void handleClientMovement(PacketReceiveEvent event) {
        PlayerSession mover = sessions.get(event.getUser());
        // Until the client confirms a server teleport, its positions are stale and get discarded.
        if (mover == null || mover.awaitingTeleport()) return;

        WrapperPlayClientPlayerFlying packet = new WrapperPlayClientPlayerFlying(event);
        boolean moved = packet.hasPositionChanged() && !mover.isRiding();
        boolean rotated = packet.hasRotationChanged();
        if (!moved && !rotated) return;

        Location location = packet.getLocation();
        PlayerPosition previous = mover.position();
        PlayerPosition position;
        if (moved && rotated) {
            position = new PlayerPosition(location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch(), packet.isOnGround());
        } else if (moved) {
            position = previous.withPosition(location.getX(), location.getY(), location.getZ(), packet.isOnGround());
        } else {
            position = previous.withRotation(location.getYaw(), location.getPitch(), packet.isOnGround());
        }
        mover.updatePosition(position, moved);
        if (moved) mover.tickEnhancedRadius(config); // we're on the mover's own event loop here

        for (VisiblePlayer watcher : mover.watchers()) {
            // Cheap check on this thread first: viewers who are out of range and already on
            // vanilla need nothing, so don't even schedule a task for them.
            if (!watcher.enhanced() && !watcher.isInRange()) continue;
            Object channel = watcher.viewer().user().getChannel();
            if (channel != null) ChannelHelper.runInEventLoop(channel, () -> relay(watcher, position, moved, rotated));
        }
    }

    /** Viewer's event loop. */
    private void relay(VisiblePlayer watcher, PlayerPosition position, boolean moved, boolean rotated) {
        if (watcher.removed()) return;
        boolean wasEnhanced = watcher.enhanced();
        if (!watcher.updateEnhanced()) return; // just left the radius: vanilla takes over
        // Just came into range in the same tick vanilla already updated them: that update is
        // current, so start relaying from the next one.
        if (!wasEnhanced && watcher.vanillaSentThisTick()) return;
        sender.sendMove(watcher, position, moved, rotated);
    }

    // ================================================================ server -> client

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;
        PacketTypeCommon type = event.getPacketType();

        if (type == PacketType.Play.Server.JOIN_GAME) {
            sessions.register(event.getUser(), new WrapperPlayServerJoinGame(event).getEntityId());
            return;
        }
        PlayerSession viewer = sessions.get(event.getUser());
        if (viewer == null) return;

        if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE) {
            WrapperPlayServerEntityRelativeMove move = new WrapperPlayServerEntityRelativeMove(event);
            VisiblePlayer passedThrough = handleVanillaMove(event, viewer, move.getEntityId());
            if (passedThrough != null) movePackets.applyVanillaMove(passedThrough, move);
        } else if (type == PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION) {
            WrapperPlayServerEntityRelativeMoveAndRotation move = new WrapperPlayServerEntityRelativeMoveAndRotation(event);
            VisiblePlayer passedThrough = handleVanillaMove(event, viewer, move.getEntityId());
            if (passedThrough != null) movePackets.applyVanillaMove(passedThrough, move);
        } else if (type == PacketType.Play.Server.ENTITY_ROTATION) {
            handleVanillaLook(event, viewer, new WrapperPlayServerEntityRotation(event).getEntityId());
        } else if (type == PacketType.Play.Server.ENTITY_HEAD_LOOK) {
            handleVanillaLook(event, viewer, new WrapperPlayServerEntityHeadLook(event).getEntityId());
        } else if (type == PacketType.Play.Server.ENTITY_TELEPORT) {
            WrapperPlayServerEntityTeleport teleport = new WrapperPlayServerEntityTeleport(event);
            RelativeFlag flags = teleport.getRelativeFlags();
            handleVanillaTeleport(viewer, teleport.getEntityId(), teleport.getPosition(),
                    flags != null && flags.getFullMask() != 0);
        } else if (type == PacketType.Play.Server.ENTITY_POSITION_SYNC) {
            WrapperPlayServerEntityPositionSync sync = new WrapperPlayServerEntityPositionSync(event);
            handleVanillaTeleport(viewer, sync.getId(), sync.getValues().getPosition(), false);
        } else if (type == PacketType.Play.Server.SPAWN_ENTITY) {
            WrapperPlayServerSpawnEntity spawn = new WrapperPlayServerSpawnEntity(event);
            if (spawn.getEntityType() == EntityTypes.PLAYER) {
                Vector3d pos = spawn.getPosition();
                sessions.onSpawn(viewer, spawn.getEntityId(), pos.getX(), pos.getY(), pos.getZ());
            }
        } else if (type == PacketType.Play.Server.SPAWN_PLAYER) {
            WrapperPlayServerSpawnPlayer spawn = new WrapperPlayServerSpawnPlayer(event);
            Vector3d pos = spawn.getPosition();
            sessions.onSpawn(viewer, spawn.getEntityId(), pos.getX(), pos.getY(), pos.getZ());
        } else if (type == PacketType.Play.Server.DESTROY_ENTITIES) {
            for (int entityId : new WrapperPlayServerDestroyEntities(event).getEntityIds()) {
                sessions.onDespawn(viewer, entityId);
                if (entityId == viewer.vehicleId()) viewer.vehicleId(-1);
            }
        } else if (type == PacketType.Play.Server.SET_PASSENGERS) {
            handlePassengers(viewer, new WrapperPlayServerSetPassengers(event));
        } else if (type == PacketType.Play.Server.PLAYER_POSITION_AND_LOOK) {
            viewer.teleportSent(new WrapperPlayServerPlayerPositionAndLook(event).getTeleportId());
        } else if (type == PacketType.Play.Server.RESPAWN || type == PacketType.Play.Server.CONFIGURATION_START) {
            sessions.onClientReset(viewer); // the client drops every entity here
        }
    }

    /**
     * Vanilla's 2-tick relative move for a player. Returns the pair if the move goes through to
     * the client (so its base must follow it), or null if it was replaced or isn't ours.
     */
    private VisiblePlayer handleVanillaMove(PacketSendEvent event, PlayerSession viewer, int entityId) {
        VisiblePlayer visible = viewer.visiblePlayer(entityId);
        if (visible == null) return null; // not one of our players (NPC, mob, ...): leave it alone

        if (visible.updateEnhanced()) {
            event.setCancelled(true); // we relay this player to this viewer ourselves
            return null;
        }

        // The target just left the radius. Vanilla's delta is based on the position *it* last
        // sent, not on the newer ones we relayed, so send one exact position instead and let
        // vanilla carry on from there.
        if (visible.takeHandoff() && visible.target().positionKnown()) {
            event.setCancelled(true);
            sender.sendExactPosition(visible, visible.target().position());
            visible.vanillaMoveSent();
            return null;
        }
        return visible;
    }

    /** Vanilla rotation / head look carry absolute angles, so just drop them when we relay the pair. */
    private void handleVanillaLook(PacketSendEvent event, PlayerSession viewer, int entityId) {
        VisiblePlayer visible = viewer.visiblePlayer(entityId);
        if (visible != null && visible.updateEnhanced()) event.setCancelled(true);
    }

    /** Vanilla's absolute teleport / position sync always goes through; it resets the client's base. */
    private void handleVanillaTeleport(PlayerSession viewer, int entityId, Vector3d position, boolean relative) {
        VisiblePlayer visible = viewer.visiblePlayer(entityId);
        if (visible == null) return;
        if (relative) {
            visible.forgetBase();
        } else {
            visible.syncedTo(position.getX(), position.getY(), position.getZ());
        }
    }

    /** A player's own client is told which vehicle they sit in; mirror that as their riding state. */
    private void handlePassengers(PlayerSession viewer, WrapperPlayServerSetPassengers packet) {
        for (int passenger : packet.getPassengers()) {
            if (passenger == viewer.entityId()) {
                viewer.vehicleId(packet.getEntityId());
                return;
            }
        }
        if (viewer.vehicleId() == packet.getEntityId()) viewer.vehicleId(-1);
    }

    @Override
    public void onUserDisconnect(UserDisconnectEvent event) {
        sessions.unregister(event.getUser());
    }
}
