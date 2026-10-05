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

import static ac.voidac.command.render.MessageStyle.*;

/**
 * /void punishments <IGN | VOID-XXXXXX>
 *
 * Looks up all punishment records for a player by name, or a single record
 * by its ban ID.
 *
 * Permission: void.punishments
 */
public class VoidPunishments implements BuildableCommand {

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

        // Direct ban-ID lookup: no DB query on the main thread needed
        if (query.toUpperCase().startsWith("VOID-")) {
            PunishmentRecord record = db.queryByBanId(query);
            if (record == null) {
                sender.sendMessage(error(sc("No punishment found with ID ") + "&d" + query.toUpperCase() + "&7."));
                return;
            }
            sender.sendMessage(header("Punishment", "&d" + record.banId()));
            sender.sendMessage(separator());
            sender.sendMessage(formatRecord(record));
            sender.sendMessage(separator());
            return;
        }

        // IGN lookup: run async to avoid blocking the main thread on DB I/O
        VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(
                VoidAPI.INSTANCE.getVoidPlugin(),
                () -> {
                    List<PunishmentRecord> records = db.queryByName(query);

                    if (records.isEmpty()) {
                        sender.sendMessage(error(sc("No punishments found for ") + "&f" + query + "&7."));
                        return;
                    }

                    sender.sendMessage(header("Punishments", "&f" + query + " &8(&f" + records.size()
                            + " &7" + sc(records.size() == 1 ? "record" : "records") + "&8)"));
                    sender.sendMessage(separator());
                    for (PunishmentRecord r : records) {
                        sender.sendMessage(formatRecord(r));
                    }
                    sender.sendMessage(separator());
                }
        );
    }

    private Component formatRecord(PunishmentRecord r) {
        String date = PunishmentDatabase.DATE_FORMAT.format(Instant.ofEpochMilli(r.timestamp()));

        StringBuilder line = new StringBuilder();
        line.append("  &8▸ &d").append(r.banId());
        line.append(" &8│ &7").append(date).append(" UTC");
        line.append(" &8│ &5").append(r.type());
        line.append(" &8│ &7").append(sc("by")).append(" &f").append(r.issuedBy());

        if (r.checkName()    != null) line.append(" &8│ &7").append(sc("check")).append("&8: &b").append(r.checkName());
        if (r.waveId()       != null) line.append(" &8│ &7").append(sc("wave")).append("&8: &d#").append(r.waveId());

        line.append(" &8│ &7").append(sc("duration")).append("&8: &f").append(r.duration());

        if (r.flagsSummary() != null) line.append(" &8│ &7").append(sc("flags")).append("&8: &7").append(r.flagsSummary());

        return MessageUtil.miniMessage(line.toString());
    }
}
