package org.hismeo.nuquest.core.dialog.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.hismeo.nuquest.NuQuest;

public class DialogState {
    private String state;

    public DialogState(String state) {
        this.state = state;
    }

    public DialogState empty() {
        return new DialogState(null);
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
            NuQuest.LOGGER.error("The dialogState is null!!!");
            return;
        }
        compoundTag.putString("dialogId", this.state);
    }

    public void syncData(EntityDataAccessor<String> dialogId, SynchedEntityData entityData) {
        entityData.set(dialogId, state);
    }
}
