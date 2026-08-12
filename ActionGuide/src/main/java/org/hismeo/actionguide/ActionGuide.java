package org.hismeo.actionguide;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hismeo.actionguide.api.ActionGuideApi;
import org.hismeo.actionguide.internal.ActionGuideService;
import org.hismeo.actionguide.api.network.ActionSyncMessage;
import org.hismeo.actionguide.internal.network.ActionNetworkPayloads;



@Mod(ActionGuide.MODID)
public class ActionGuide {
    public static final String MODID = "action_guide";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    private static final ActionGuideService SERVICE = new ActionGuideService();

    public ActionGuide(IEventBus iEventBus, ModContainer modContainer){
        ActionNetwork.install(SERVICE);
        iEventBus.addListener(ActionNetworkPayloads::register);
    }

    public static ActionGuideApi api() {
        return SERVICE;
    }

    static ActionGuideService service() {
        return SERVICE;
    }

    public static void dispatchSync(ActionSyncMessage message) {
        SERVICE.receiveSync(message);
    }
}
