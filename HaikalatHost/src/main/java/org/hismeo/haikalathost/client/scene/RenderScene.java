package org.hismeo.haikalathost.client.scene;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Render-thread-owned generational object database. */
public final class RenderScene {
    private final List<Slot> slots = new ArrayList<>();
    private final ArrayDeque<Integer> freeSlots = new ArrayDeque<>();
    private final Map<Long, RenderObjectId> sourceObjects = new HashMap<>();
    private int size;

    public RenderObjectId create(RenderObject object) {
        int index;
        Slot slot;
        if (freeSlots.isEmpty()) {
            index = slots.size();
            slot = new Slot();
            slots.add(slot);
        } else {
            index = freeSlots.removeFirst();
            slot = slots.get(index);
        }
        slot.value = object;
        size++;
        return new RenderObjectId(index, slot.generation);
    }

    public void update(RenderObjectId id, RenderObject object) {
        requireLive(id).value = object;
    }

    public RenderObject remove(RenderObjectId id) {
        Slot slot = requireLive(id);
        RenderObject removed = slot.value;
        slot.value = null;
        slot.generation = nextGeneration(slot.generation);
        freeSlots.addLast(id.index());
        if (slot.hasSourceKey) {
            sourceObjects.remove(slot.sourceKey, id);
            slot.hasSourceKey = false;
            slot.sourceKey = 0L;
        }
        size--;
        return removed;
    }

    public RenderObject get(RenderObjectId id) {
        return requireLive(id).value;
    }

    public Map<Long, RenderObjectId> apply(RenderSceneDelta delta) {
        Map<Long, RenderObjectId> added = new HashMap<>();
        for (RenderSceneDelta.Operation operation : delta.operations()) {
            switch (operation) {
                case RenderSceneDelta.Add add -> {
                    RenderObjectId previous = sourceObjects.remove(add.sourceKey());
                    if (previous != null && isLive(previous)) remove(previous);
                    RenderObjectId created = create(add.object());
                    Slot createdSlot = slots.get(created.index());
                    createdSlot.sourceKey = add.sourceKey();
                    createdSlot.hasSourceKey = true;
                    sourceObjects.put(add.sourceKey(), created);
                    added.put(add.sourceKey(), created);
                }
                case RenderSceneDelta.Update update -> update(update.id(), update.object());
                case RenderSceneDelta.Remove remove -> remove(remove.id());
            }
        }
        return Map.copyOf(added);
    }

    public int size() {
        return size;
    }

    public boolean isLive(RenderObjectId id) {
        return id.index() < slots.size()
                && slots.get(id.index()).generation == id.generation()
                && slots.get(id.index()).value != null;
    }

    private Slot requireLive(RenderObjectId id) {
        if (!isLive(id)) throw new StaleRenderObjectException(id);
        return slots.get(id.index());
    }

    private static int nextGeneration(int generation) {
        return generation == Integer.MAX_VALUE ? 1 : generation + 1;
    }

    private static final class Slot {
        private int generation = 1;
        private RenderObject value;
        private long sourceKey;
        private boolean hasSourceKey;
    }
}
