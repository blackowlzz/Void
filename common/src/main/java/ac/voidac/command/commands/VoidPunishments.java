package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.manager.punishment.PunishmentDatabase;
import ac.voidac.manager.punishment.PunishmentRecord;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.StringParser;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * /void punishments <IGN | VOID-XXXXXX>
 *
 * Looks up all punishment records for a player by name, or a single record
 * by its ban ID.
 *
 * Permission: void.punishments
 */
public class VoidPunishments implements BuildableCommand {

    private static final String PREFIX = "&8[&5Void&8]";
    private static final String SEP    = "&8 &m─────────────────────────────────";

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("void", "voidac")
                        .literal("punishments")
                        .permission("void.punishments")
                        .required("query", StringParser.stringParser())
                        .handler(this::handleLookup)
        );
    }

    private void handleLookup(@NotNull CommandContext<Sender> context) {
        Sender sender = context.sender();
        String query = context.<String>get("query").trim();
        PunishmentDatabase db = VoidAPI.INSTANCE.getPunishmentDatabase();

        // Direct ban-ID lookup — no DB query on the main thread needed
        if (query.toUpperCase().startsWith("VOID-")) {
            PunishmentRecord record = db.queryByBanId(query);
            if (record == null) {
                sender.sendMessage(msg(PREFIX + " &c✖ &7No punishment found with ID &d" + query.toUpperCase() + "&7."));
                return;
            }
            sender.sendMessage(msg(PREFIX + " &5Punishment &d" + record.banId() + "&7:"));
            sender.sendMessage(msg(SEP));
            sender.sendMessage(formatRecord(record));
            sender.sendMessage(msg(SEP));
            return;
        }

        // IGN lookup — run async to avoid blocking the main thread on DB I/O
        VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(
                VoidAPI.INSTANCE.getVoidPlugin(),
                () -> {
                    List<PunishmentRecord> records = db.queryByName(query);

                    if (records.isEmpty()) {
                        sender.sendMessage(msg(PREFIX + " &c✖ &7No punishments found for &f" + query + "&7."));
                        return;
                    }

                    sender.sendMessage(msg(
                            PREFIX + " &5Punishments &7for &f" + query
                                    + " &8(&f" + records.size() + " record" + (records.size() == 1 ? "" : "s") + "&8):"));
                    sender.sendMessage(msg(SEP));
                    for (PunishmentRecord r : records) {
                        sender.sendMessage(formatRecord(r));
                    }
                    sender.sendMessage(msg(SEP));
                }
        );
    }

    private Component formatRecord(PunishmentRecord r) {
        String date = PunishmentDatabase.DATE_FORMAT.format(Instant.ofEpochMilli(r.timestamp()));

        StringBuilder line = new StringBuilder();
        line.append("  &8▸ &d").append(r.banId());
        line.append(" &8│ &7").append(date).append(" UTC");
        line.append(" &8│ &5").append(r.type());
        line.append(" &8│ &7by &f").append(r.issuedBy());

        if (r.checkName()    != null) line.append(" &8│ &7check&8: &b").append(r.checkName());
        if (r.waveId()       != null) line.append(" &8│ &7wave&8: &d#").append(r.waveId());

        line.append(" &8│ &7dur&8: &f").append(r.duration());

        if (r.flagsSummary() != null) line.append(" &8│ &7flags&8: &7").append(r.flagsSummary());

        return msg(line.toString());
    }

    private static Component msg(String legacy) {
        return MessageUtil.miniMessage(legacy);
    }
}
