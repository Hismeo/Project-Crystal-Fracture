package org.hismeo.fractureclient.client.avatar;

/** Lifecycle of one player-to-Haikalat binding. */
public enum PlayerAvatarState {
    UNINITIALIZED,
    LOADING_MODEL,
    WAITING_FOR_SKIN,
    READY,
    FAILED,
    CLOSED
}
