package org.hismeo.fractureclient.client.config;

import org.hismeo.crystallib.api.config.ConfigFormat;
import org.hismeo.crystallib.api.config.ConfigScope;
import org.hismeo.crystallib.api.config.ConfigValue;
import org.hismeo.crystallib.api.config.CrystalConfig;
import org.hismeo.fractureclient.FractureClient;

@CrystalConfig(
        modId = FractureClient.MODID,
        fileName = "fracture_client_orthographic_camera",
        scope = ConfigScope.CLIENT,
        format = ConfigFormat.TOML,
        autoLoad = true
)
public class OrthographicCameraConfig {
    @ConfigValue(comment = "Orthographic camera pitch", min = -90.0, max = 90.0)
    public static float pitch = 25.0F;

    @ConfigValue(comment = "Orthographic camera yaw", min = -180.0, max = 180.0)
    public static float yaw = 25.0F;

    @ConfigValue(comment = "Orthographic camera size")
    public static float size = 10.0F;

    @ConfigValue
    public static boolean isCull = true;
}
