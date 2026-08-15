package org.hismeo.fractureclient.client.avatar.skin;

import java.nio.file.Path;

/** Implemented on Minecraft's SkinManager by the client mixin. */
public interface SkinManagerCacheRoot {
    Path fractureClient$skinCacheRoot();
}
