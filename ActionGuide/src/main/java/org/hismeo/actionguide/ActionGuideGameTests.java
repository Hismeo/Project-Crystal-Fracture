package org.hismeo.actionguide;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.api.distmarker.Dist;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ActionGuideGameTests {
    private ActionGuideGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/mobs/empty")
    public static void dedicatedServerLoadsPublicApi(GameTestHelper helper) {
        if (ActionGuide.api() == null) {
            throw new AssertionError("ActionGuide public API was not initialized");
        }
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            throw new AssertionError("game test is not running on a dedicated server");
        }
        helper.succeed();
    }
}
