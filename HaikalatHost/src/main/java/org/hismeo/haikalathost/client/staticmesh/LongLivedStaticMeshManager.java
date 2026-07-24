package org.hismeo.haikalathost.client.staticmesh;

import com.kaleblangley.haikalat.backend.sync.GpuFence;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.hismeo.haikalathost.HaikalatHost;
import org.hismeo.haikalathost.client.submission.TakeoverFrameSubmission;
import org.joml.Matrix4fc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;

/** Lazily promotes captured Minecraft static uploads into the Host shared arena. */
public final class LongLivedStaticMeshManager implements AutoCloseable {
    private final StaticMeshArena arena = new StaticMeshArena();
    private final IdentityHashMap<Object, LongLivedStaticMesh> meshes =
            new IdentityHashMap<>();
    private final List<StaticMeshArena.Allocation> stagedRetirement = new ArrayList<>();
    private final Deque<RetirementBatch> pendingRetirement = new ArrayDeque<>();
    private boolean closed;

    public boolean capture(
            Object key,
            Matrix4fc modelView,
            Matrix4fc projection,
            ShaderInstance shader,
            ResourceLocation texture,
            TakeoverFrameSubmission submission
    ) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        LongLivedStaticMesh mesh = getOrUpload(key);
        if (mesh == null) return false;
        submission.captureStatic(mesh, modelView, projection, shader, texture);
        return true;
    }

    public boolean capture(
            Object key,
            Matrix4fc modelView,
            Matrix4fc projection,
            RenderType renderType,
            TakeoverFrameSubmission submission
    ) {
        RenderSystem.assertOnRenderThread();
        ensureOpen();
        LongLivedStaticMesh mesh = getOrUpload(key);
        if (mesh == null) return false;
        submission.captureStatic(mesh, modelView, projection, renderType);
        return true;
    }

    public void invalidate(Object key) {
        RenderSystem.assertOnRenderThread();
        LongLivedStaticMesh removed = meshes.remove(key);
        if (removed != null) stagedRetirement.add(removed.allocation());
    }

    public void invalidateAll() {
        RenderSystem.assertOnRenderThread();
        meshes.values().forEach(mesh -> stagedRetirement.add(mesh.allocation()));
        meshes.clear();
    }

    public void endFrame() {
        RenderSystem.assertOnRenderThread();
        if (!stagedRetirement.isEmpty()) {
            pendingRetirement.addLast(new RetirementBatch(
                    GpuFence.insert(), List.copyOf(stagedRetirement)));
            stagedRetirement.clear();
        }
        while (!pendingRetirement.isEmpty()
                && pendingRetirement.peekFirst().fence().isSignaled()) {
            RetirementBatch batch = pendingRetirement.removeFirst();
            batch.fence().close();
            batch.allocations().forEach(arena::release);
        }
    }

    public int meshCount() {
        return meshes.size();
    }

    @Override
    public void close() {
        if (closed) return;
        meshes.clear();
        stagedRetirement.clear();
        while (!pendingRetirement.isEmpty()) {
            pendingRetirement.removeFirst().fence().close();
        }
        arena.close();
        closed = true;
    }
    private LongLivedStaticMesh getOrUpload(Object key) {
        StaticMeshPayload payload = StaticVertexBufferRegistry.get(key);
        if (payload == null) return null;

        LongLivedStaticMesh mesh = meshes.get(key);
        if (mesh != null) return mesh;

        int pagesBefore = arena.pageCount();
        StaticMeshArena.Allocation allocation = arena.upload(
                payload.vertices(), payload.indices(), payload.indexElementBytes());
        mesh = new LongLivedStaticMesh(payload, allocation);
        meshes.put(key, mesh);
        if (arena.pageCount() != pagesBefore) {
            HaikalatHost.LOGGER.info(
                    "H3/H4 static arena pages={}, meshes={}, live={}B",
                    arena.pageCount(), meshes.size(), arena.allocatedBytes());
        }
        return mesh;
    }


    private void ensureOpen() {
        if (closed) throw new IllegalStateException("static mesh manager is closed");
    }

    private record RetirementBatch(
            GpuFence fence, List<StaticMeshArena.Allocation> allocations) {
    }
}
