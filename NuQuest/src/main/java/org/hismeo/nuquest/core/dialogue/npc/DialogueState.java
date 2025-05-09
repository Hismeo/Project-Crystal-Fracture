package org.hismeo.nuquest.core.dialogue.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.hismeo.nuquest.NuQuest;

public class DialogueState {
    private String state;

    public DialogueState(String state) {
        this.state = state;
    }

    public DialogueState empty() {
        return new DialogueState(null);
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        if (state == null) {
            return;
        }
        this.state = state;
    }

    public void saveData(CompoundTag compoundTag) {
        if (this.state == null) {
            NuQuest.LOGGER.error("The dialogueState is null!!!");
            return;
        }
        compoundTag.putString("dialogueId", this.state);
    }

    public void syncData(EntityDataAccessor<String> dialogueId, SynchedEntityData entityData) {
        entityData.define(dialogueId, state);
    }
}
