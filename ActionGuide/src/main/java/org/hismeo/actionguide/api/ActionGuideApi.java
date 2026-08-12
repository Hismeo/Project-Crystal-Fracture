package org.hismeo.actionguide.api;

import net.minecraft.resources.ResourceLocation;
import org.hismeo.actionguide.api.action.ActionAdmission;
import org.hismeo.actionguide.api.action.ActionIntentRequest;
import org.hismeo.actionguide.api.action.ActionOwner;
import org.hismeo.actionguide.api.action.ActionResolver;
import org.hismeo.actionguide.api.event.ActionLifecycleListener;
import org.hismeo.actionguide.api.event.CueEventHandler;
import org.hismeo.actionguide.api.event.CueStateHandler;
import org.hismeo.actionguide.api.runtime.ActionCommand;
import org.hismeo.actionguide.api.runtime.ActionRequestResult;
import org.hismeo.actionguide.api.runtime.ActionRuntimeView;
import org.hismeo.actionguide.api.network.ActionSyncListener;

import java.util.Optional;

public interface ActionGuideApi {
    void registerResolver(ActionResolver resolver);

    void registerAdmission(ActionAdmission admission);

    void registerEventHandler(ResourceLocation type, CueEventHandler handler);

    void registerStateHandler(ResourceLocation type, CueStateHandler handler);

    void registerLifecycleListener(ActionLifecycleListener listener);

    void registerSyncListener(ActionSyncListener listener);

    void sendIntentToServer(ActionIntentRequest request);

    ActionRequestResult submitIntent(ActionOwner owner, ActionIntentRequest request);

    void submitCommand(ActionOwner owner, ActionCommand command);

    Optional<ActionRuntimeView> runtime(ActionOwner owner);
}
