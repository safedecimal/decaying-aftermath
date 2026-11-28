package io.github.safedecimal.decayingaftermath.network;

import io.github.safedecimal.decayingaftermath.DecayingAftermath;
import io.github.safedecimal.decayingaftermath.medical.*;
import io.github.safedecimal.decayingaftermath.server.ServerAftermathSystem;
import io.github.safedecimal.decayingaftermath.server.ServerMedicalSystem;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned self-harm urges and unclosable weapon-in-hand minigame during severe depression. */
public final class SelfHarmNetwork {
    public static final int OPEN = 0, HESITATE = 1, CUT = 2, DONE = 3, ABORT = 4;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, PlayerUrge> URGES = new HashMap<>();
    private static int nextSessionId;
    private static boolean registered;

    private SelfHarmNetwork() { }

    public static void register() {
        if (registered) return;
        registered = true;
        PayloadTypeRegistry.clientboundPlay().register(State.TYPE, State.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Click.TYPE, Click.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Click.TYPE, (payload, context) ->
                context.server().execute(() -> handleClick(context.player(), payload)));
        ServerTickEvents.END_SERVER_TICK.register(SelfHarmNetwork::tickServer);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.getPlayer().getUUID();
            SESSIONS.remove(id);
            URGES.remove(id);
        });
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            SESSIONS.remove(oldPlayer.getUUID());
            URGES.remove(oldPlayer.getUUID());
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SESSIONS.clear();
            URGES.clear();
        });
    }

    public static boolean isActive(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static boolean triggerNow(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) return true;
        BodySnapshot body = ServerMedicalSystem.get(player).snapshot();
        if (body.mode() != BodySnapshot.Mode.CONSCIOUS) return false;
        WeaponMatch weapon = findWeapon(player);
        if (weapon == null) return false;
        beginSession(player, body, weapon);
        return SESSIONS.containsKey(player.getUUID());
    }

    public static void cancelFor(ServerPlayer player) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null) {
            send(player, false, session.id, ABORT, ItemStack.EMPTY, session.targetPart, 0, session.cutsDone, 0, session.maxHesitations);
        }
    }

    private static void tickServer(MinecraftServer server) {
        for (ServerPlayer player : new ArrayList<>(server.getPlayerList().getPlayers())) {
            Session session = SESSIONS.get(player.getUUID());
            if (session != null) {
                if (!validSession(player, session)) {
                    cancelFor(player);
                }
                continue;
            }
            PlayerUrge urge = URGES.computeIfAbsent(player.getUUID(), ignored -> new PlayerUrge());
            if (urge.cooldownTicks > 0) urge.cooldownTicks--;
            if (!player.isAlive() || player.isCreative() || player.isSpectator()
                    || player.isSleeping() || ServerAftermathSystem.active(player)) {
                urge.urgeTicks = 0;
                urge.warned = false;
                continue;
            }
            BodySnapshot body = ServerMedicalSystem.get(player).snapshot();
            if (!SelfHarmPolicy.eligible(body) || urge.cooldownTicks > 0) {
                urge.urgeTicks = Math.max(0, urge.urgeTicks - 4);
                if (urge.urgeTicks == 0) urge.warned = false;
                continue;
            }
            WeaponMatch weapon = findWeapon(player);
            if (weapon == null) {
                urge.urgeTicks = Math.max(0, urge.urgeTicks - 2);
                if (urge.urgeTicks == 0) urge.warned = false;
                continue;
            }
            double horizSpeed = player.position().subtract(player.oldPosition()).horizontalDistance();
            if (player.isSprinting() || horizSpeed > 0.14) {
                urge.urgeTicks = Math.max(0, urge.urgeTicks - 3);
            } else if (horizSpeed < 0.02) {
                urge.urgeTicks += 2;
            } else {
                urge.urgeTicks += 1;
            }
            if (urge.urgeTicks >= SelfHarmPolicy.URGE_WARN_TICKS && !urge.warned) {
                urge.warned = true;
                MedicalNetwork.thought(player, "abjection.thought.self_harm.urge");
            }
            if (urge.urgeTicks >= SelfHarmPolicy.TRIGGER_TICKS) {
                urge.urgeTicks = 0;
                urge.warned = false;
                beginSession(player, body, weapon);
            }
        }
    }

    private static void beginSession(ServerPlayer player, BodySnapshot body, WeaponMatch weapon) {
        if (!ServerPlayNetworking.canSend(player, State.TYPE)) return;
        MedicalNetwork.cancelProcedures(player);
        DefibNetwork.cancelFor(player);
        player.closeContainer();
        if (weapon.hotbarSlot >= 0) {
            player.getInventory().setSelectedSlot(weapon.hotbarSlot);
        }
        int totalCuts = SelfHarmPolicy.requiredCuts(body);
        int hesitations = SelfHarmPolicy.hesitationsForCut(body, 0);
        BodyPart part = chooseTargetPart(body, weapon.offhand);
        String itemPath = BuiltInRegistries.ITEM.getKey(weapon.stack.getItem()).getPath();
        float damage = SelfHarmPolicy.weaponDamage(isHeavyWeapon(weapon.stack, itemPath));
        float relief = SelfHarmPolicy.mentalReliefPerCut(totalCuts);
        int id = ++nextSessionId;
        Session session = new Session(id, weapon.stack.copy(), weapon.slotIndex, weapon.offhand, part,
                totalCuts, 0, hesitations, hesitations, damage, relief, player.level().getGameTime() - 5);
        SESSIONS.put(player.getUUID(), session);
        send(player, true, id, OPEN, session.weaponSnapshot, part,
                session.cutsLeft, session.cutsDone, session.hesitationsLeft, session.maxHesitations);
    }

    private static void handleClick(ServerPlayer player, Click click) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || session.id != click.sessionId()) return;
        if (!validSession(player, session)) {
            cancelFor(player);
            return;
        }
        long now = player.level().getGameTime();
        if (now - session.lastClickTick < 2) return;
        session.lastClickTick = now;

        if (session.hesitationsLeft > 0) {
            session.hesitationsLeft--;
            send(player, true, session.id, HESITATE, session.weaponSnapshot, session.targetPart,
                    session.cutsLeft, session.cutsDone, session.hesitationsLeft, session.maxHesitations);
            return;
        }

        if (session.cutsLeft > 0) {
            session.cutsLeft--;
            session.cutsDone++;
            ItemStack liveWeapon = currentWeapon(player, session);
            if (!liveWeapon.isEmpty() && liveWeapon.isDamageableItem() && !player.getAbilities().instabuild) {
                liveWeapon.hurtAndBreak(1, (ServerLevel) player.level(), player, ignored -> { });
                player.inventoryMenu.broadcastChanges();
            }
            BodyModel body = ServerMedicalSystem.get(player);
            body.applySelfHarm(session.targetPart, session.cutDamage, session.mentalRelief);
            ServerMedicalSystem.mirrorHealth(player);
            MedicalNetwork.sync(player, body.snapshot());

            if (session.cutsLeft <= 0 || body.mode() == BodySnapshot.Mode.DEAD || body.mode() == BodySnapshot.Mode.UNCONSCIOUS) {
                SESSIONS.remove(player.getUUID());
                PlayerUrge urge = URGES.computeIfAbsent(player.getUUID(), ignored -> new PlayerUrge());
                urge.cooldownTicks = SelfHarmPolicy.COOLDOWN_TICKS;
                urge.urgeTicks = 0;
                urge.warned = false;
                send(player, false, session.id, DONE, session.weaponSnapshot, session.targetPart,
                        0, session.cutsDone, 0, session.maxHesitations);
                if (body.mode() == BodySnapshot.Mode.CONSCIOUS) {
                    MedicalNetwork.thought(player, "abjection.thought.self_harm.after");
                }
            } else {
                session.hesitationsLeft = SelfHarmPolicy.hesitationsForCut(body.snapshot(), session.cutsDone);
                session.maxHesitations = session.hesitationsLeft;
                session.weaponSnapshot = liveWeapon.isEmpty() ? session.weaponSnapshot : liveWeapon.copy();
                send(player, true, session.id, CUT, session.weaponSnapshot, session.targetPart,
                        session.cutsLeft, session.cutsDone, session.hesitationsLeft, session.maxHesitations);
            }
        }
    }

    private static boolean validSession(ServerPlayer player, Session session) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator() || ServerAftermathSystem.active(player)) {
            return false;
        }
        BodySnapshot body = ServerMedicalSystem.get(player).snapshot();
        if (body.mode() != BodySnapshot.Mode.CONSCIOUS) return false;
        return !currentWeapon(player, session).isEmpty();
    }

    private static ItemStack currentWeapon(ServerPlayer player, Session session) {
        ItemStack stack = session.offhand
                ? player.getOffhandItem()
                : player.getInventory().getItem(session.inventorySlot);
        if (isWeapon(stack)) return stack;
        WeaponMatch fallback = findWeapon(player);
        return fallback == null ? ItemStack.EMPTY : fallback.stack;
    }

    private static BodyPart chooseTargetPart(BodySnapshot body, boolean offhand) {
        BodyPart preferred = offhand ? BodyPart.RIGHT_ARM : BodyPart.LEFT_ARM;
        if (body.part(preferred).integrity() >= 0.25f) return preferred;
        BodyPart otherArm = offhand ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
        if (body.part(otherArm).integrity() >= 0.25f) return otherArm;
        return BodyPart.LEFT_LEG;
    }

    private static WeaponMatch findWeapon(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (isWeapon(main)) {
            return new WeaponMatch(main, player.getInventory().getSelectedSlot(), player.getInventory().getSelectedSlot(), false);
        }
        ItemStack off = player.getOffhandItem();
        if (isWeapon(off)) {
            return new WeaponMatch(off, -1, -1, true);
        }
        WeaponMatch best = null;
        for (int i = 0; i < 36; i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (!isWeapon(candidate)) continue;
            int hotbar = i < 9 ? i : -1;
            boolean heavy = isHeavyWeapon(candidate, BuiltInRegistries.ITEM.getKey(candidate.getItem()).getPath());
            if (best == null || (heavy && !best.heavy) || (hotbar >= 0 && best.hotbarSlot < 0)) {
                best = new WeaponMatch(candidate, i, hotbar, false, heavy);
                if (heavy && hotbar >= 0) break;
            }
        }
        return best;
    }

    public static boolean isWeapon(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.HOES)
                || stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS)
                || stack.is(Items.SHEARS) || stack.is(Items.TRIDENT) || stack.is(Items.MACE)
                || stack.is(Items.FLINT) || stack.is(Items.ARROW) || stack.is(Items.SPECTRAL_ARROW)
                || stack.is(Items.TIPPED_ARROW) || stack.is(Items.PRISMARINE_SHARD)
                || stack.is(Items.AMETHYST_SHARD) || stack.is(SurvivalItems.TWEEZERS)) {
            return true;
        }
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (SelfHarmPolicy.isWeaponId(path)) return true;
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return modifiers != null && modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND) > 1.0;
    }

    private static boolean isHeavyWeapon(ItemStack stack, String path) {
        if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.TRIDENT) || stack.is(Items.MACE)) {
            return true;
        }
        if (SelfHarmPolicy.isHeavyBladeId(path)) return true;
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return modifiers != null && modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0, EquipmentSlot.MAINHAND) >= 4.0;
    }

    private static void send(ServerPlayer player, boolean active, int id, int action, ItemStack weapon,
            BodyPart part, int cutsLeft, int cutsDone, int hesitationsLeft, int maxHesitations) {
        if (player.isRemoved() || !ServerPlayNetworking.canSend(player, State.TYPE)) return;
        ServerPlayNetworking.send(player, new State(active, id, action, weapon, part.ordinal(),
                cutsLeft, cutsDone, hesitationsLeft, maxHesitations));
    }

    private record WeaponMatch(ItemStack stack, int slotIndex, int hotbarSlot, boolean offhand, boolean heavy) {
        WeaponMatch(ItemStack stack, int slotIndex, int hotbarSlot, boolean offhand) {
            this(stack, slotIndex, hotbarSlot, offhand, false);
        }
    }

    private static final class PlayerUrge {
        int urgeTicks;
        int cooldownTicks;
        boolean warned;
    }

    private static final class Session {
        final int id;
        final int inventorySlot;
        final boolean offhand;
        final BodyPart targetPart;
        final float cutDamage;
        final float mentalRelief;
        ItemStack weaponSnapshot;
        int cutsLeft;
        int cutsDone;
        int hesitationsLeft;
        int maxHesitations;
        long lastClickTick;

        Session(int id, ItemStack weaponSnapshot, int inventorySlot, boolean offhand, BodyPart targetPart,
                int cutsLeft, int cutsDone, int hesitationsLeft, int maxHesitations,
                float cutDamage, float mentalRelief, long lastClickTick) {
            this.id = id;
            this.weaponSnapshot = weaponSnapshot;
            this.inventorySlot = inventorySlot;
            this.offhand = offhand;
            this.targetPart = targetPart;
            this.cutsLeft = cutsLeft;
            this.cutsDone = cutsDone;
            this.hesitationsLeft = hesitationsLeft;
            this.maxHesitations = maxHesitations;
            this.cutDamage = cutDamage;
            this.mentalRelief = mentalRelief;
            this.lastClickTick = lastClickTick;
        }
    }

    public record State(boolean active, int sessionId, int action, ItemStack weapon, int targetPartOrdinal,
                        int cutsLeft, int cutsDone, int hesitationsLeft, int maxHesitations) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID, "self_harm_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = CustomPacketPayload.codec(State::write, State::read);

        @Override public Type<State> type() { return TYPE; }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeInt(sessionId);
            buf.writeByte(action);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, weapon);
            buf.writeByte(targetPartOrdinal);
            buf.writeVarInt(cutsLeft);
            buf.writeVarInt(cutsDone);
            buf.writeVarInt(hesitationsLeft);
            buf.writeVarInt(maxHesitations);
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            boolean active = buf.readBoolean();
            int sessionId = buf.readInt();
            int action = buf.readUnsignedByte();
            ItemStack weapon = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int part = buf.readUnsignedByte();
            int cutsLeft = buf.readVarInt();
            int cutsDone = buf.readVarInt();
            int hesitationsLeft = buf.readVarInt();
            int maxHesitations = buf.readVarInt();
            return new State(active, sessionId, action, weapon, part, cutsLeft, cutsDone, hesitationsLeft, maxHesitations);
        }
    }

    public record Click(int sessionId) implements CustomPacketPayload {
        public static final Type<Click> TYPE = new Type<>(Identifier.fromNamespaceAndPath(DecayingAftermath.MOD_ID, "self_harm_click"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Click> CODEC = CustomPacketPayload.codec(Click::write, Click::read);

        @Override public Type<Click> type() { return TYPE; }

        private void write(RegistryFriendlyByteBuf buf) { buf.writeInt(sessionId); }
        private static Click read(RegistryFriendlyByteBuf buf) { return new Click(buf.readInt()); }
    }
}
