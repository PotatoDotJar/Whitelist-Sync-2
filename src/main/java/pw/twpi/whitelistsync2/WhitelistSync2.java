package pw.twpi.whitelistsync2;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLPaths;
import net.rmnad.minecraft.forge.whitelistsynclib.services.BaseService;
import net.rmnad.minecraft.forge.whitelistsynclib.services.MySqlService;
import net.rmnad.minecraft.forge.whitelistsynclib.services.PostgreSqlService;
import net.rmnad.minecraft.forge.whitelistsynclib.services.SqLiteService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pw.twpi.whitelistsync2.commands.op.OpCommands;
import pw.twpi.whitelistsync2.commands.whitelist.WhitelistCommands;
import pw.twpi.whitelistsync2.services.*;

@Mod(WhitelistSync2.MODID)
public class WhitelistSync2
{
    public static final String MODID = "whitelistsync2";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    public static String SERVER_FILEPATH;

    // Database Service
    public static BaseService whitelistService;

    public WhitelistSync2() {
        // Register config
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SERVER_CONFIG);
        MinecraftForge.EVENT_BUS.register(this);
        LOGGER.info("Hello from Whitelist Sync 2!");
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        //WhitelistCommands.register(event.getServer().getCommandManager().getDispatcher());
        new WhitelistCommands(event.getDispatcher());

        if(Config.SYNC_OP_LIST.get()) {
            LOGGER.info("Opped Player Sync is enabled");
            new OpCommands(event.getDispatcher());
        } else {
            LOGGER.info("Opped Player Sync is disabled");
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        boolean setupSuccessful = true;

        // Server filepath
        SERVER_FILEPATH = event.getServer().getServerDirectory().getPath();

        LOGGER.info("----------------------------------------------");
        LOGGER.info("---------------WHITELIST SYNC 2---------------");
        LOGGER.info("----------------------------------------------");

        switch (Config.DATABASE_MODE.get()) {
            case SQLITE:
                whitelistService = new SqLiteService(Config.SQLITE_DATABASE_PATH.get(), Config.SYNC_OP_LIST.get());
                break;
            case MYSQL:
                whitelistService = new MySqlService(
                        Config.MYSQL_DB_NAME.get(),
                        Config.MYSQL_IP.get(),
                        Config.MYSQL_PORT.get(),
                        Config.MYSQL_USERNAME.get(),
                        Config.MYSQL_PASSWORD.get(),
                        Config.SYNC_OP_LIST.get()
                );
                break;
            case POSTGRESQL:
                whitelistService = new PostgreSqlService(
                        Config.POSTGRESQL_DB_NAME.get(),
                        Config.POSTGRESQL_IP.get(),
                        Config.POSTGRESQL_PORT.get(),
                        Config.POSTGRESQL_USERNAME.get(),
                        Config.POSTGRESQL_PASSWORD.get(),
                        Config.SYNC_OP_LIST.get()
                );
                break;
            default:
                LOGGER.error("Please check what WHITELIST_MODE is set in the config and make sure it is set to a supported mode.");
                setupSuccessful = false;
                break;
        }

        if(!whitelistService.initializeDatabase() || !setupSuccessful) {
            LOGGER.error("Error initializing whitelist sync database. Disabling mod functionality. Please correct errors and restart.");
        } else {
            // Database is setup!

            // Check if whitelisting is enabled.
            if (!event.getServer().getPlayerList().isUsingWhitelist()) {
                LOGGER.info("Oh no! I see whitelisting isn't enabled in the server properties. "
                        + "I assume this is not intentional, I'll enable it for you!");
                event.getServer().getPlayerList().setUsingWhiteList(true);
            }

            StartSyncThread(event.getServer(), whitelistService);
        }


        LOGGER.info("----------------------------------------------");
        LOGGER.info("----------------------------------------------");
        LOGGER.info("----------------------------------------------");
    }

    public void StartSyncThread(MinecraftServer server, BaseService service) {
        Thread sync = new Thread(new SyncThread(server, service));
        sync.start();
        LOGGER.info("Sync Thread Started!");
    }
}
