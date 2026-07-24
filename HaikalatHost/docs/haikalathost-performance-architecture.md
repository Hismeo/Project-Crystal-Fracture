# HaikalatHost performance architecture

> **Status:** superseded by `haikalathost-full-takeover-architecture.md`.
> The compatibility switches below document the retired prototype and are not part of the
> full-takeover execution path.

## Production default

HaikalatHost starts in `PASSIVE` mode. It keeps lifecycle integration available but does not
transform Minecraft's immediate-draw or chunk-render hot paths. This is deliberate: the current
compatibility renderer validates ownership and GL interoperability, but it does not yet replace
Minecraft's shader/uniform model with a pipeline that can issue a genuinely batched chunk draw.

The experimental interceptors are selected by the Mixin configuration plugin at startup, so a
disabled feature costs no branch, allocation, registry lookup, or state invalidation in a frame.

## Startup switches

- `-Dhaikalathost.enabled=false` disables all interception.
- `-Dhaikalathost.compatDraws=true` enables immediate `MeshData` compatibility replay.
- `-Dhaikalathost.chunkMeshes=true` enables experimental mirrored chunk meshes.

Both render switches default to `false` and require a game restart because Mixin selection happens
before Minecraft classes are transformed.

## Immediate compatibility path

When explicitly enabled, the compatibility path follows four latency rules:

1. A busy persistent-mapped frame slot never waits on the render thread. The draw falls back to
   vanilla for that frame.
2. VAOs are cached by immutable Minecraft vertex layout. Ring offsets use `baseVertex`, so vertex
   attributes are not rebuilt per draw.
3. Minecraft sequential indices remain in `SequentialIndexCache` GPU buffers; QUADS and LINES no
   longer copy the same generated indices into the transient ring for every draw.
4. FractureHost emits the single indexed draw directly at its compatibility boundary. Haikalat
   still owns the buffers and VAOs, while both Haikalat and Minecraft state caches are invalidated
   at the boundary.

## Chunk interception

Mirrored chunk meshes remain experimental and off by default. They duplicate Minecraft GPU
storage and still issue one draw per visible section. Enabling them is useful for correctness and
profiling, not as a performance configuration. Production chunk interception should only become
the default after Host owns shared geometry storage plus a shader-visible per-draw transform path
that makes multi-draw submission real.

## Validation

JUnit covers configuration defaults, packaged Mixin-plugin wiring, allocator alignment and
multi-frame slot isolation. GPU behavior must additionally be A/B tested in the same world,
resolution, view distance and frame cap because unit tests cannot create Minecraft's live GL
context or measure presentation latency.
