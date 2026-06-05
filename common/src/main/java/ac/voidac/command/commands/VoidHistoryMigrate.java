package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.api.storage.backend.BackendException;
import ac.voidac.command.BuildableCommand;
import ac.voidac.internal.storage.backend.sqlite.SqliteBackend;
import ac.voidac.internal.storage.checks.CheckRegistry;
import ac.voidac.internal.storage.migrate.LegacyMigrator;
import ac.voidac.internal.storage.migrate.V0Reader;
import ac.voidac.manager.datastore.ClientVersionResolver;
import ac.voidac.manager.datastore.DataStoreLifecycle;
import ac.voidac.manager.datastore.V0Sources;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * {@code /void history migrate [--delete]} — on-demand v0 → v1 migration outside
 * the startup path. Detects the legacy source by reading
 * {@code history.database.type / host / port / database / username / password}
 * from {@code config.yml} (the same keys the pre-cutover plugin wrote), builds
 * the matching JDBC URL, and runs the same {@code LegacyMigrator} startup uses.
 * <p>
 * {@code --delete} (off by default) drops the v0 {@code void_history_*} tables
 * after state flips to {@code COMPLETE}. Operator-requested destructive action;
 * no confirmation prompt — the flag itself is the confirmation.
 * <p>
 * Runs synchronously on the command thread so RCON callers get the full output
 * before the reply channel closes. Migration against a large v0 can take
 * seconds to minutes; tolerable for a one-shot admin command.
 */
public class VoidHistoryMigrate implements BuildableCommand {

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        commandManager.command(
                commandManager.commandBuilder("void", "voidac", "void", "voidac")
                        .literal("history")
                        .literal("migrate")
                        .permission("void.history.migrate")
                        .flag(commandManager.flagBuilder("delete")
                                .withDescription(org.incendo.cloud.description.Description.of(
                                        "Drop the legacy v0 tables after migration completes")))
                        .handler(this::handle)
        );
    }

    private void handle(CommandContext<Sender> context) {
        Sender sender = context.sender();
        boolean delete = context.flags().hasFlag("delete");

        DataStoreLifecycle lifecycle = VoidAPI.INSTANCE.getDataStoreLifecycle();
        // isEnabled() is false when database.yml sets enabled=false;
        // isLoaded() is false when start() caught an init failure. Surface
        // each separately so the operator sees why the command won't run.
        if (!lifecycle.isEnabled()) {
            sender.sendMessage(MessageUtil.miniMessage("%prefix% &cThe archive is disabled."));
            return;
        }
        if (!lifecycle.isLoaded()) {
            sender.sendMessage(MessageUtil.miniMessage("%prefix% &cThe archive failed to load."));
            return;
        }

        V0Sources.V0Source source = V0Sources.detect(
                VoidAPI.INSTANCE.getVoidPlugin().getDataFolder().toPath(),
                VoidAPI.INSTANCE.getConfigManager().getConfig());
        if (source == null) {
            logBoth(sender, Component.text("No legacy source detected — nothing to migrate.", NamedTextColor.YELLOW));
            return;
        }

        logBoth(sender, Component.text()
                .append(Component.text("Starting legacy migration from ", NamedTextColor.AQUA))
                .append(Component.text(source.summary(), NamedTextColor.WHITE))
                .build());

        try {
            LegacyMigrator.Result result =
                    runLegacy(lifecycle, source, sender);
            logBoth(sender, Component.text()
                    .append(Component.text("Migration complete: ", NamedTextColor.GREEN))
                    .append(Component.text(result.sessionsWritten() + " sessions, "))
                    .append(Component.text(result.violationsWritten() + " violations in "))
                    .append(Component.text(result.elapsedMs() + "ms"))
                    .append(result.resumed() ? Component.text(" (resumed)", NamedTextColor.GRAY) : Component.empty())
                    .build());
            if (delete) {
                dropLegacy(source, sender);
            }
        } catch (BackendException e) {
            logBoth(sender, Component.text("Archive migration failed: " + e.getMessage(), NamedTextColor.RED));
            LogUtil.error("Archive migration failed via /void history migrate", e);
        }
    }

    private LegacyMigrator.Result runLegacy(
            DataStoreLifecycle lifecycle, V0Sources.V0Source source, Sender sender) throws BackendException {
        V0Reader reader =
                new V0Reader(
                        source.jdbcUrl(), source.username(), source.password());
        // Legacy migration only targets SQLite today — V0Reader understands
        // the old void_history_* schema and writes through SqliteBackend's
        // bulk-import path. /void history copy is the general-purpose
        // cross-backend hammer once more targets exist.
        SqliteBackend v1 = lifecycle.sqliteBackendForCommands();
        if (v1 == null) {
            throw new BackendException(
                    "no SQLite backend in routing — the archive needs SQLite as its target; "
                            + "switch a category to sqlite in database.yml or use /void history copy instead");
        }
        CheckRegistry registry = lifecycle.checkRegistryForCommands();
        long gapMs = lifecycle.config().session().gapMs();
        LegacyMigrator migrator =
                new LegacyMigrator(
                        reader, v1, registry,
                        ClientVersionResolver::legacyStringToPvn,
                        gapMs, Logger.getLogger("void-history-migrate"));
        return migrator.run(count -> {
            if (count > 0 && count % 5000 == 0) {
                logBoth(sender, Component.text("… " + count + " violations carried over", NamedTextColor.GRAY));
            }
        });
    }

    private void dropLegacy(V0Sources.V0Source source, Sender sender) {
        logBoth(sender, Component.text("--delete requested — erasing legacy v0 tables…", NamedTextColor.YELLOW));
        String[] tables = {
                "void_history_violations",
                "void_history_check_names",
                "void_history_servers",
                "void_history_versions",
                "void_history_client_brands",
                "void_history_client_versions",
                "void_history_server_versions",
        };
        try (Connection c = open(source); Statement s = c.createStatement()) {
            for (String t : tables) {
                try { s.executeUpdate("DROP TABLE IF EXISTS " + t); }
                catch (SQLException e) {
                    logBoth(sender, Component.text("  erase " + t + " failed: " + e.getMessage(), NamedTextColor.RED));
                }
            }
            logBoth(sender, Component.text("Legacy v0 tables erased.", NamedTextColor.GREEN));
        } catch (SQLException e) {
            logBoth(sender, Component.text("Failed to open legacy source for --delete: " + e.getMessage(), NamedTextColor.RED));
        }
    }

    private static Connection open(V0Sources.V0Source source) throws SQLException {
        if (source.username() == null && source.password() == null) {
            return DriverManager.getConnection(source.jdbcUrl());
        }
        return DriverManager.getConnection(source.jdbcUrl(), source.username(), source.password());
    }

    private static void logBoth(Sender sender, Component msg) {
        sender.sendMessage(msg);
        // Console log in plain text so operators still see progress if the
        // command channel (RCON connection) closes mid-run.
        LogUtil.info(plain(msg));
    }

    private static String plain(Component c) {
        StringBuilder sb = new StringBuilder();
        flatten(c, sb);
        return sb.toString();
    }

    private static void flatten(Component c, StringBuilder sb) {
        if (c instanceof net.kyori.adventure.text.TextComponent tc) sb.append(tc.content());
        for (Component child : c.children()) flatten(child, sb);
    }
}
