package org.example.tp.tpPlus;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.example.tp.tpPlus.commands.CommandBack;
import org.example.tp.tpPlus.commands.CommandTPA;
import org.example.tp.tpPlus.commands.CommandHome;
import org.example.tp.tpPlus.commands.CommandTPAHere;
import org.example.tp.tpPlus.config.ConfigManager;
import org.example.tp.tpPlus.storage.DatabaseManager;

public class TpPlus implements ModInitializer {

    @Override
    public void onInitialize() {
        // 初始化配置和数据库
        ConfigManager.init();
        DatabaseManager.init();
        
        // 注册命令
        CommandBack.register();
        CommandTPA.register();
        CommandHome.register();
        CommandTPAHere.register();
        
        // 服务器关闭时关闭数据库连接
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            DatabaseManager.close();
        });
    }
}