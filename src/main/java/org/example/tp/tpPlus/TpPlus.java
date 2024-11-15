package org.example.tp.tpPlus;

import net.fabricmc.api.ModInitializer;
import org.example.tp.tpPlus.commands.CommandBack;
import org.example.tp.tpPlus.commands.CommandTPA;
import org.example.tp.tpPlus.commands.CommandHome;
import org.example.tp.tpPlus.commands.CommandTPAHere;

public class TpPlus implements ModInitializer {

    @Override
    public void onInitialize() {
        CommandBack.register();
        CommandTPA.register();
        CommandHome.register();
        CommandTPAHere.register();
    }
}