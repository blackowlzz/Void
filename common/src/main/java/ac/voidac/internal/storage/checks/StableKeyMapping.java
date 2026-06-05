package ac.voidac.internal.storage.checks;

import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Bridges legacy {@code void_history_check_names} display strings to the v1
 * schema's {@code stable_key} column.
 * <p>
 * Two consumers:
 * <ul>
 *   <li>v0 → v1 migration: {@code LegacyMigrator} resolves every distinct
 *       {@code check_name_string} through this map so historical violations
 *       land on the new schema with a meaningful stable_key.</li>
 *   <li>Live-write fallback: {@code LiveWriteHooks} consults this map when a
 *       Check hasn't declared a {@code stableKey} on its {@code @CheckData}
 *       / {@code CheckInfo}.</li>
 * </ul>
 * <p>
 * Unknown names hit the {@link #legacyFallback} path so migration always
 * completes; operators can rename the fallback keys later through the check
 * registry if they want.
 */
@ApiStatus.Internal
public final class StableKeyMapping {

    private static final Map<String, String> MAPPINGS = buildMappings();

    private StableKeyMapping() {}

    public static Optional<String> stableKeyFor(String legacyDisplayName) {
        if (legacyDisplayName == null) return Optional.empty();
        return Optional.ofNullable(MAPPINGS.get(legacyDisplayName.toLowerCase(Locale.ROOT)));
    }

    public static String legacyFallback(String legacyDisplayName) {
        return "void.legacy." + legacyDisplayName.toLowerCase(Locale.ROOT);
    }

    private static Map<String, String> buildMappings() {
        // Keys: lowercased legacy display names (the {@code check_name_string}
        // column in the v0 schema, plus any {@code Check.getCheckName()} a
        // live writer might emit).
        // Values: stable_keys declared via @CheckData.stableKey on the
        // matching Check class. Entries for retired check classes that have
        // no live source-of-truth keep their {@code void.legacy.*} key so
        // historical violations stay addressable.
        Map<String, String> m = new HashMap<>(160);

        // ---------- badpackets/ ----------
        m.put("badpacketsa", "void.badpackets.duplicate_slot");
        m.put("badpacketsd", "void.badpackets.invalid_pitch");
        m.put("badpacketse", "void.badpackets.invalid_position");
        m.put("badpacketsf", "void.badpackets.duplicate_sprint");
        m.put("badpacketsg", "void.badpackets.duplicate_sneak");
        m.put("badpacketsi", "void.badpackets.spoofed_abilities");
        m.put("badpacketsk", "void.badpackets.invalid_spectate");
        m.put("badpacketsl", "void.badpackets.invalid_dig");
        m.put("badpacketsm", "void.badpackets.respawn_alive");
        m.put("badpacketsn", "void.badpackets.invalid_teleport");
        m.put("badpacketso", "void.badpackets.invalid_keepalive");
        m.put("badpacketsp", "void.badpackets.invalid_click");
        m.put("badpacketsq", "void.badpackets.invalid_horse_jump");
        m.put("badpacketsu", "void.badpackets.invalid_block_placement");
        m.put("badpacketsv", "void.badpackets.slow_move");
        m.put("badpacketsy", "void.badpackets.oob_slot");
        m.put("badpacketsb", "void.badpackets.ignored_rotation");
        m.put("badpacketsc", "void.badpackets.wake_not_sleeping");
        m.put("badpacketsh", "void.badpackets.unexpected_sequence");
        m.put("badpacketsj", "void.badpackets.use_item_rotation_mismatch");
        m.put("badpacketsr", "void.badpackets.position_starvation");
        m.put("badpacketss", "void.badpackets.window_confirmation_not_accepted");
        m.put("badpacketst", "void.badpackets.invalid_interact_vector");
        m.put("badpacketsw", "void.badpackets.invalid_entity_target");
        m.put("badpacketsx", "void.badpackets.extra_input_actions");
        m.put("badpacketsz", "void.badpackets.duplicate_player_input");
        m.put("selfinteract", "void.badpackets.self_hit");

        // ---------- crash/ ----------
        m.put("crasha", "void.crash.large_position");
        m.put("crashb", "void.crash.creative_while_not_creative");
        m.put("crashc", "void.crash.nan_position");
        m.put("crashd", "void.crash.lectern");
        m.put("crashe", "void.crash.low_view_distance");
        m.put("crashf", "void.crash.button_crash");
        m.put("crashg", "void.crash.negative_sequence");
        m.put("crashh", "void.crash.invalid_tab_complete");
        m.put("crashi", "void.crash.invalid_bundle_slot");

        // ---------- combat/ ----------
        m.put("hitboxes", "void.combat.hitboxes");
        m.put("reach", "void.combat.reach");

        // ---------- aim/ ----------
        m.put("aimduplicatelook", "void.aim.duplicate_look");
        m.put("aimmodulo360", "void.aim.modulo_360");
        m.put("aimfold", "void.legacy.aimfold");
        m.put("aimgold", "void.legacy.aimgold");
        m.put("aimhold", "void.legacy.aimhold");

        // ---------- breaking/ ----------
        m.put("airliquidbreak", "void.breaking.air_liquid_break");
        m.put("farbreak", "void.breaking.far_break");
        m.put("fastbreak", "void.breaking.fast_break");
        m.put("invalidbreak", "void.breaking.invalid_break");
        m.put("multibreak", "void.breaking.multi_break");
        m.put("noswingbreak", "void.breaking.no_swing_break");
        m.put("positionbreaka", "void.breaking.position_break_a");
        m.put("positionbreakb", "void.breaking.position_break_b");
        m.put("rotationbreak", "void.breaking.rotation_break");
        m.put("wrongbreak", "void.breaking.wrong_break");

        // ---------- scaffolding/ ----------
        m.put("airliquidplace", "void.scaffolding.air_liquid_place");
        m.put("duplicaterotplace", "void.scaffolding.duplicate_rot_place");
        m.put("fabricatedplace", "void.scaffolding.fabricated_place");
        m.put("farplace", "void.scaffolding.far_place");
        m.put("invalidplacea", "void.scaffolding.invalid_place_a");
        m.put("invalidplaceb", "void.scaffolding.invalid_place_b");
        m.put("multiplace", "void.scaffolding.multi_place");
        m.put("positionplace", "void.scaffolding.position_place");
        m.put("rotationplace", "void.scaffolding.rotation_place");

        // ---------- chat/ ----------
        m.put("chatc", "void.chat.moving_while_chatting");

        // ---------- exploit/ ----------
        m.put("chata", "void.exploit.blank_tab_complete");
        m.put("chatb", "void.exploit.spigot_antispam_bypass");
        m.put("chatd", "void.exploit.chat_while_hidden");
        m.put("exploita", "void.exploit.anvil_name_length");
        m.put("exploitb", "void.exploit.invalid_book_edit");
        m.put("exploitc", "void.legacy.exploitc");

        // ---------- prediction/ ----------
        m.put("phase", "void.prediction.phase");

        // ---------- movement/ ----------
        m.put("noslow", "void.movement.noslow");

        // ---------- groundspoof/ ----------
        m.put("groundspoof", "void.groundspoof.fake");
        m.put("nofall", "void.groundspoof.no_fall");

        // ---------- post/ ----------
        m.put("post", "void.post.invalid_order");

        // ---------- ping/ ----------
        m.put("transactionorder", "void.ping.invalid_transaction_order");

        // ---------- baritone/ ----------
        m.put("baritone", "void.baritone.baritone");

        // ---------- timer/ ----------
        m.put("negativetimer", "void.timer.negative");
        m.put("ticktimer", "void.timer.tick");
        m.put("timer", "void.timer.timer");
        m.put("timerlimit", "void.timer.limit");
        m.put("vehicletimer", "void.timer.vehicle");

        // ---------- elytra/ ----------
        m.put("elytraa", "void.elytra.already_gliding");
        m.put("elytrab", "void.elytra.no_jump");
        m.put("elytrac", "void.elytra.too_frequent");
        m.put("elytrad", "void.elytra.no_elytra");
        m.put("elytrae", "void.elytra.flying");
        m.put("elytraf", "void.elytra.grounded");
        m.put("elytrag", "void.elytra.levitation");
        m.put("elytrah", "void.elytra.vehicle");
        m.put("elytrai", "void.elytra.water");

        // ---------- sprint/ ----------
        m.put("sprinta", "void.sprint.hunger");
        m.put("sprintb", "void.sprint.sneaking");
        m.put("sprintc", "void.sprint.using_item");
        m.put("sprintd", "void.sprint.blindness");
        m.put("sprinte", "void.sprint.wall");
        m.put("sprintf", "void.sprint.gliding");
        m.put("sprintg", "void.sprint.water");

        // ---------- vehicle/ ----------
        m.put("vehiclea", "void.vehicle.impossible_input");
        m.put("vehicleb", "void.vehicle.spoofed_vehicle");
        m.put("vehiclec", "void.vehicle.vehicle_control");
        m.put("vehicled", "void.vehicle.spoofed_jump");
        m.put("vehiclee", "void.vehicle.spoofed_boat");
        m.put("vehiclef", "void.vehicle.boat_input_mismatch");

        // ---------- multiactions/ ----------
        m.put("multiactionsa", "void.multiactions.attack_while_using");
        m.put("multiactionsb", "void.multiactions.break_while_using");
        m.put("multiactionsc", "void.multiactions.inventory_click_while_moving");
        m.put("multiactionsd", "void.multiactions.inventory_close_while_moving");
        m.put("multiactionse", "void.multiactions.swing_while_using");
        m.put("multiactionsf", "void.multiactions.block_and_entity_interact");
        m.put("multiactionsg", "void.multiactions.action_while_rowing");

        // ---------- multiinteract/ ----------
        m.put("multiinteracta", "void.multiinteract.multiple_targets");
        m.put("multiinteractb", "void.multiinteract.interact_at_position_changed");

        // ---------- packetorder/ ----------
        m.put("packetordera", "void.packetorder.window_click_order");
        m.put("packetorderb", "void.packetorder.noswing");
        m.put("packetorderc", "void.packetorder.interact_order");
        m.put("packetorderd", "void.packetorder.interact_hand_order");
        m.put("packetordere", "void.packetorder.slot_order");
        m.put("packetorderf", "void.packetorder.input_tick_to_sneak_sprint_order");
        m.put("packetorderg", "void.packetorder.hotbar_inventory_manage_order");
        m.put("packetorderh", "void.packetorder.sneak_sprint_order");
        m.put("packetorderi", "void.packetorder.input_tick_order");
        m.put("packetorderj", "void.packetorder.attack_interact_use_order");
        m.put("packetorderk", "void.packetorder.inventory_open_order");
        m.put("packetorderl", "void.packetorder.drop_item_order");
        m.put("packetorderm", "void.packetorder.interact_use_order");
        m.put("packetordern", "void.packetorder.place_use_order");
        m.put("packetordero", "void.packetorder.tick_end_order");
        m.put("packetorderp", "void.packetorder.transaction_response_order");

        // ---------- misc-legacy/ ----------
        m.put("looka", "void.legacy.looka");
        m.put("clientbrand", "void.legacy.clientbrand");
        m.put("inventorya", "void.legacy.inventorya");
        m.put("inventoryb", "void.legacy.inventoryb");
        m.put("inventoryc", "void.legacy.inventoryc");
        m.put("inventoryd", "void.legacy.inventoryd");
        m.put("inventorye", "void.legacy.inventorye");
        m.put("inventoryf", "void.legacy.inventoryf");
        m.put("inventoryg", "void.legacy.inventoryg");

        return Map.copyOf(m);
    }
}
