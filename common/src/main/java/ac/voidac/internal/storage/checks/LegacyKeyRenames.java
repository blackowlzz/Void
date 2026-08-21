package ac.voidac.internal.storage.checks;

import org.jetbrains.annotations.ApiStatus;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Canonical {@code void.legacy.* → void.<category>.<descriptive>} rename map.
 * Consumed by:
 *
 * <ul>
 *   <li>{@link StableKeyMapping} when assigning stable_keys during V0→V1 migration.</li>
 *   <li>Each backend's schema migration step that runs an
 *       {@code UPDATE void_checks SET stable_key = ? WHERE stable_key = ?} pass
 *       to rewrite already-persisted rows on existing operator installs.</li>
 * </ul>
 *
 * <p>Order matters only for readability, every entry's old key is unique and
 * the migration applies them all idempotently. Entries omitted here keep their
 * {@code void.legacy.*} key forever (V0-only historical checks with no live V2
 * class: aim-fold/gold/hold, looka, clientbrand, stay legacy because there
 * is no source-of-truth class to rename).
 */
@ApiStatus.Internal
public final class LegacyKeyRenames {

    private LegacyKeyRenames() {}

    public static final Map<String, String> OLD_TO_NEW;

    static {
        Map<String, String> m = new LinkedHashMap<>();

        // BadPackets: letter-keyed checks where V2 and V3 diverged in semantics.
        m.put("void.legacy.badpacketsb", "void.badpackets.ignored_rotation");
        m.put("void.legacy.badpacketsc", "void.badpackets.wake_not_sleeping");
        m.put("void.legacy.badpacketsh", "void.badpackets.unexpected_sequence");
        m.put("void.legacy.badpacketsj", "void.badpackets.use_item_rotation_mismatch");
        m.put("void.legacy.badpacketsr", "void.badpackets.position_starvation");
        m.put("void.legacy.badpacketss", "void.badpackets.window_confirmation_not_accepted");
        m.put("void.legacy.badpacketst", "void.badpackets.invalid_interact_vector");
        m.put("void.legacy.badpacketsw", "void.badpackets.invalid_entity_target");
        m.put("void.legacy.badpacketsx", "void.badpackets.extra_input_actions");
        m.put("void.legacy.badpacketsz", "void.badpackets.duplicate_player_input");

        // Single-check categories.
        m.put("void.legacy.chatc", "void.chat.moving_while_chatting");
        m.put("void.legacy.exploita", "void.exploit.anvil_name_length");
        m.put("void.legacy.groundspoof", "void.groundspoof.fake");
        m.put("void.legacy.timerlimit", "void.timer.limit");

        // Elytra: every check fires when the player STARTS gliding under some
        // disallowed condition; the category implies the verb, the suffix is
        // just the condition.
        m.put("void.legacy.elytraa", "void.elytra.already_gliding");
        m.put("void.legacy.elytrab", "void.elytra.no_jump");
        m.put("void.legacy.elytrac", "void.elytra.too_frequent");
        m.put("void.legacy.elytrad", "void.elytra.no_elytra");
        m.put("void.legacy.elytrae", "void.elytra.flying");
        m.put("void.legacy.elytraf", "void.elytra.grounded");
        m.put("void.legacy.elytrag", "void.elytra.levitation");
        m.put("void.legacy.elytrah", "void.elytra.vehicle");
        m.put("void.legacy.elytrai", "void.elytra.water");

        // MultiActions: two simultaneous actions, named <verb>_while_<context>.
        m.put("void.legacy.multiactionsa", "void.multiactions.attack_while_using");
        m.put("void.legacy.multiactionsb", "void.multiactions.break_while_using");
        m.put("void.legacy.multiactionsc", "void.multiactions.inventory_click_while_moving");
        m.put("void.legacy.multiactionsd", "void.multiactions.inventory_close_while_moving");
        m.put("void.legacy.multiactionse", "void.multiactions.swing_while_using");
        m.put("void.legacy.multiactionsf", "void.multiactions.block_and_entity_interact");
        m.put("void.legacy.multiactionsg", "void.multiactions.action_while_rowing");

        // MultiInteract.
        m.put("void.legacy.multiinteracta", "void.multiinteract.multiple_targets");
        m.put("void.legacy.multiinteractb", "void.multiinteract.interact_at_position_changed");

        // PacketOrder: every check is "X happened in the wrong order"; the
        // <thing>_order suffix matches the colleague's naming style.
        m.put("void.legacy.packetordera", "void.packetorder.window_click_order");
        m.put("void.legacy.packetorderb", "void.packetorder.noswing");
        m.put("void.legacy.packetorderc", "void.packetorder.interact_order");
        m.put("void.legacy.packetorderd", "void.packetorder.interact_hand_order");
        m.put("void.legacy.packetordere", "void.packetorder.slot_order");
        m.put("void.legacy.packetorderf", "void.packetorder.input_tick_to_sneak_sprint_order");
        m.put("void.legacy.packetorderg", "void.packetorder.hotbar_inventory_manage_order");
        m.put("void.legacy.packetorderh", "void.packetorder.sneak_sprint_order");
        m.put("void.legacy.packetorderi", "void.packetorder.input_tick_order");
        m.put("void.legacy.packetorderj", "void.packetorder.attack_interact_use_order");
        m.put("void.legacy.packetorderk", "void.packetorder.inventory_open_order");
        m.put("void.legacy.packetorderl", "void.packetorder.drop_item_order");
        m.put("void.legacy.packetorderm", "void.packetorder.interact_use_order");
        m.put("void.legacy.packetordern", "void.packetorder.place_use_order");
        m.put("void.legacy.packetordero", "void.packetorder.tick_end_order");
        m.put("void.legacy.packetorderp", "void.packetorder.transaction_response_order");

        // Sprint: terse condition names; category implies "started sprinting".
        m.put("void.legacy.sprinta", "void.sprint.hunger");
        m.put("void.legacy.sprintb", "void.sprint.sneaking");
        m.put("void.legacy.sprintc", "void.sprint.using_item");
        m.put("void.legacy.sprintd", "void.sprint.blindness");
        m.put("void.legacy.sprinte", "void.sprint.wall");
        m.put("void.legacy.sprintf", "void.sprint.gliding");
        m.put("void.legacy.sprintg", "void.sprint.water");

        // Vehicle.
        m.put("void.legacy.vehiclea", "void.vehicle.impossible_input");
        m.put("void.legacy.vehicleb", "void.vehicle.spoofed_vehicle");
        m.put("void.legacy.vehiclec", "void.vehicle.vehicle_control");
        m.put("void.legacy.vehicled", "void.vehicle.spoofed_jump");
        m.put("void.legacy.vehiclee", "void.vehicle.spoofed_boat");
        m.put("void.legacy.vehiclef", "void.vehicle.boat_input_mismatch");

        OLD_TO_NEW = Collections.unmodifiableMap(m);
    }
}
