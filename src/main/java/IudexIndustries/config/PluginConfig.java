package IudexIndustries.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Values from config.yml. Radii are kept squared so range checks never need a square root.
 */
public final class PluginConfig {

    private final double enhancedRadius;
    private final boolean dynamicRadius;
    @Getter
    private final double crowdRadiusSquared;
    private final double minRadius;
    private final int shrinkStart;
    private final int shrinkEnd;
    private final int playerCap;

    public PluginConfig(FileConfiguration config) {
        enhancedRadius = Math.max(0, config.getDouble("enhanced-radius", 12));
        dynamicRadius = config.getBoolean("dynamic-radius.enabled", true);
        double crowdRadius = Math.max(0, config.getDouble("dynamic-radius.crowd-radius", 24));
        crowdRadiusSquared = crowdRadius * crowdRadius;
        minRadius = Math.max(0, Math.min(enhancedRadius, config.getDouble("dynamic-radius.min-radius", 6)));
        shrinkStart = Math.max(0, config.getInt("dynamic-radius.shrink-start", 5));
        shrinkEnd = Math.max(shrinkStart + 1, config.getInt("dynamic-radius.shrink-end", 15));
        playerCap = config.getInt("dynamic-radius.player-cap", 25);
    }

    /** Squared enhanced radius for a viewer with {@code nearbyPlayers} players within the crowd radius. */
    public double enhancedRadiusSquared(int nearbyPlayers) {
        double radius = enhancedRadius;
        if (dynamicRadius) {
            if (playerCap >= 0 && nearbyPlayers > playerCap) return 0;
            if (nearbyPlayers > shrinkStart) {
                double shrink = Math.min(1.0, (nearbyPlayers - shrinkStart) / (double) (shrinkEnd - shrinkStart));
                radius -= (enhancedRadius - minRadius) * shrink;
            }
        }
        return radius * radius;
    }

    public String describe() {
        if (!dynamicRadius) return "Enhanced radius " + enhancedRadius + " blocks (dynamic radius off).";
        return "Enhanced radius " + enhancedRadius + " blocks, shrinking to " + minRadius + " with "
                + shrinkStart + "-" + shrinkEnd + " players within " + Math.sqrt(crowdRadiusSquared)
                + " blocks, off above " + playerCap + ".";
    }
}
