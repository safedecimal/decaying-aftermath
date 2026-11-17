# Medical vitals, layered care and optional effects —0.3.9

This is fictional game physiology, not medical guidance. All source/build work stays in the actual decaying-aftermath project.

## Corrected source mechanisms

- `MedicalNetwork.unused`: native26.3 `remove(MAX_STACK_SIZE)` creates a deletion tombstone. Restore the item prototype with `set(MAX_STACK_SIZE,item.getDefaultMaxStackSize())`. Partial counted units retain their fill. Server consolidation repacks only compatible medical material into full units plus a residual; it does not refill, erase foreign custom data, touch active/carried slots, or consolidate vanilla potions.
- `BodyModel.evaluate`: LastStand is restorative, not lethal on timer expiry. Deterministic75% head/50% torso minima, blood>=50%, oxygen/energy100%, remaining bleeding/infection×.05 adapt CU TryLastStand into aggregate Minecraft anatomy.12s presentation window covers the native10.7s intro;300s adrenaline/re-arm protection persists. The boost expiring never kills the player.
- Already-spent critical deterioration enters a separately saved `DYING` phase.60 seconds at20TPS allows treatment; stable bleeding cessation can leave that phase. Reloads and state hopping do not refill its unused grace. Explicit void/kill still bypass rescue, and final irreversible death/totem handling uses the normal guarded vanilla adapter.
- Native ordinary bandage callbacks add reservoirs: one nominal full turn costs1/12 roll, adds.025 normalized skin care and.0375 normalized bleed care, reduces local pain.05. Tick spends.00025 skin care/.000625 bleed care. Bandaging does not instantly reduce bleeding or finish the procedure; successive turns keep consuming actual inventory supply. Normal-viscosity/no-venom clot baseline is.0000125 per tick. CU viscosity/venom/run skills are not fabricated. Covered bleeding icons require remaining bandageCare>.005, not a permanent applied-item flag. `PRESSURE_DRESSING` remains an explicitly stronger Minecraft adaptation, not a claimed original CU registry ID.
- Energy is a server quantity, consumed by movement/exertion and affected by hypoxia/sedation/infection; resting restores energy but not hunger-driven tissue HP. Consciousness combines actual oxygen/blood/vital integrity/pain/sedation/energy. Hand sensitivity/responsiveness follow consciousness; energy movement-speed scaling and bounded deterministic pain/tremor harmonics are explicit adaptations (no fictitious CU skills or claim of exact Perlin noise).
- Infection requires wounded skin and the native40s incubation check; default7.2points/min growth, visible at25%, severe damage beyond75%, cumulative sepsis penalty. Antiseptic marks timed local disinfection and reduces infection gradually. Local anesthesia is selected-part only, timed60s per full game fill, reduces perceived pain, weakens limb force until the final2s, and does not heal or add global opiate burden. Raw pain and all timer/care reservoirs are saved separately so reload cannot erase hidden injury.
- Save schema2 and S2C body payload include energy, consciousness, infection, timed states and bandage care. LegacyDEAD ordinal3 is retained; old active LastStand saves migrate away from the lethal-timer bug. Install the same mod version on client/server.

## Decompiled CU provenance

Original Assembly-CSharp SHA-256:48c7f2296fc4a659faae3b1822884ca5b7ff73d6767ef159e6a6c2bc0b2d38f1.
Fresh focused C# reconstruction and authenticated CIL anchors: verification/vitals-infection-0.3.9/cu-evidence. Reports record tokens/MVID/decompiler identity and serialized Unity pointer bindings, not just asset filenames.
`Body.TryLastStand`0x06000092; `Body.HandleCirculation`0x06000090; `MusicManager.Update`0x060004D1; `BandageMinigame.DoBandageAction`0x06000441; `Item.SetupItems`0x0600032A; `Limb.Update`0x060003DF.
Dying track `DrawYourLastBreath1` (19.928571s) and one-shot death track `DrawYourLastBreath2` (34.285714s) come from the actual MusicManager dyingClip/deadClip bindings. Native vignette and infection sprites are retained. Anesthetic artwork is a documented saline-container/localanesthetic-color adaptation; native saline does use SyringeMinigame. Antiseptic uses the actual disinfectant sprite. Minecraft padding changes no native pixels; no AI replacements.

## Optional mods

Artifacts were supplied under C:/Users/thuam/Desktop/testmods and are not altered or installed by this work. Cruor0.1.0-port.4 and Ragdollified1.2.2 are actual Fabric26.3 ports; Ragdollified requires Zarith. Evidence, schemas and bridge tests are under verification/vitals-infection-0.3.9/compat.
Our former redirect collided with Cruor at Player.actuallyHurt→setHealth. WrapMethod now preserves the native writer, captures the post-mitigation source/delta and flushes native Cruor effects after authoritative evaluation. Fatal effects require actualDEAD, not a low compatibilityHP mirror. Native LivingEntity.die remains the single final-death path for Ragdollified; medical unconsciousness separately starts exactly one native live ragdoll, excluding sleeping, DYING and LastStand. Recovery/leave/dimension change releases only the medical-owned live serial; real death stays on the native death-registration path.
Cruor visual bleeding is synchronized with medically owned player state. Native stains, mobs and ragdoll/corpse effects remain separate; optional snapshot/cache identity guards protect unrelated/reused entities. Missing mods and unsupported native layouts do not enable reflection bridges.

## Verification scope

Tests and recorded API/native-signature probes do not launch a Minecraft/CU window, GPU renderer or audio device. Any gameplay confirmation is the user's subsequent in-game retest. The four inherited roles remain build.gradle, verification/DIFF_FILE.patch, verification/VERIFICATION.txt and executable verification/ROLLBACK.sh; new records extend these roles, preserving prior transactions. A new version is published only after complete build, same-input baseline/modified/rollback, hash reconstruction and first-attempt release checks.

## Continuation: anatomical blood and medical collapse

Cruor BodyBlood.drip was read in the shipped port, not inferred from asset names. Its original droplets use body.wounds or a random bounding-box fallback. The optional bridge now keeps the native flow, droplet color/physics/collision/decals, but selects only parts whose authoritative PartSnapshot.bleeding is positive. A weighted selector handles multiple wounds; zero bleeding disables the medical drip selection. Body creation after reload is independent of low cosmetic HP. Body/wound/stain records and unrelated mobs remain native.

S2C medical_visual_v2 transmits six immutable anatomical bleed values, bounded sum and exact medical-owned native bodySerial. UUID/entity id/level/freshness/canSend guards remain. It is not accepted C2S. The short zero-serial fixture is 47 bytes. Server liveSerial and packet bodySerial are DIFFERENT native identifiers, verified in the decompiled RetainedRagdoll constructor; they are not guessed to be interchangeable.

RagdollifiedServerApi.startPlayerRagdoll(...,velocity,Integer.MAX_VALUE) invokes native registerLive once on medical unconsciousness. No live ragdoll is started for vanilla sleep, death, creative/spectator, LastStand or the still-treatable DYING phase. The native client manager receives persistent/hide requests only for the exact server-owned bodySerial and matching UUID/world. Recovery unhides and queues native requestRemoveRagdoll only for that owned body; replacement death corpses are preserved.

Blood on a ragdoll follows its actual getSmoothedTransform(part) world position. The standing fallback uses decompiled LimbAnchors rest-pose centres rotated by current body yaw. It is a documented anatomical-centre adaptation, not a claim of exact surface wound coordinates or animated standing-arm tracking. No new bitmap assets were guessed or invented.

Same-input tests observe model/codec/lifecycle behavior and recording adapters. Real Mixin weaving, in-game physics, animated rendering and audible playback still require the user retest; neither game is launched automatically.
