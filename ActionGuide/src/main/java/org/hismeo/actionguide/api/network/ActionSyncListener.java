package org.hismeo.actionguide.api.network;

@FunctionalInterface
public interface ActionSyncListener {
    void onMessage(ActionSyncMessage message);
}
