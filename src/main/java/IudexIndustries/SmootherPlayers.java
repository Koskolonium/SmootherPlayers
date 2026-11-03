package IudexIndustries;

import IudexIndustries.config.PluginConfig;
import IudexIndustries.packet.MovementListener;
import IudexIndustries.player.SessionManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import org.bukkit.plugin.java.JavaPlugin;

public final class SmootherPlayers extends JavaPlugin {

    private PacketListenerCommon movementListener;

    @Override
    public void onEnable() {
        ServerVersion version = PacketEvents.getAPI().getServerManager().getVersion();
        if (version == ServerVersion.ERROR || version.isOlderThan(ServerVersion.V_1_21)) {
            getLogger().severe("Unsupported server version (" + version + "). SmootherPlayers supports 1.21 - 26.3;"
                    + " if your server is in that range, update PacketEvents. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        PluginConfig config = new PluginConfig(getConfig());
        getLogger().info(config.describe());
        SessionManager sessions = new SessionManager(config);
        sessions.registerOnlinePlayers();
        movementListener = PacketEvents.getAPI().getEventManager()
                .registerListener(new MovementListener(config, sessions), PacketListenerPriority.HIGHEST);
    }

    @Override
    public void onDisable() {
        if (movementListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(movementListener);
        }
    }
}
