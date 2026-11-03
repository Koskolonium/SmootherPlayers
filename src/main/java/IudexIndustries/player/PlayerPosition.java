package IudexIndustries.player;

/**
 * A player's position and rotation as reported by their client. Immutable, so it can be handed
 * between threads without copying.
 */
public record PlayerPosition(double x, double y, double z, float yaw, float pitch, boolean onGround) {

    /** Placeholder until the client has sent its first position. */
    static final PlayerPosition UNKNOWN = new PlayerPosition(0, 0, 0, 0, 0, false);

    public PlayerPosition withPosition(double x, double y, double z, boolean onGround) {
        return new PlayerPosition(x, y, z, yaw, pitch, onGround);
    }

    public PlayerPosition withRotation(float yaw, float pitch, boolean onGround) {
        return new PlayerPosition(x, y, z, yaw, pitch, onGround);
    }

    public double distanceSquared(PlayerPosition other) {
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
