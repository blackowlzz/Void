package ac.voidac.command.commands;

import ac.voidac.VoidAPI;
import ac.voidac.command.BuildableCommand;
import ac.voidac.manager.init.start.SuperDebug;
import ac.voidac.platform.api.manager.cloud.CloudCommandAdapter;
import ac.voidac.platform.api.sender.Sender;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.parser.standard.IntegerParser;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public class VoidLog implements BuildableCommand {

    private static final String PASTEBIN_API  = "https://pastebin.com/api/api_post.php";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    // ── Public API (backward-compat overload used by VoidDump) ────────────

    public static void sendLogAsync(Sender sender, String log, Consumer<String> consumer, String type) {
        sendLogAsync(sender, log, consumer, -1);
    }

    public static void sendLogAsync(Sender sender, String log, Consumer<String> consumer, int flagId) {
        String success   = cfg("upload-log",                 "%prefix% &7Trace saved &8→ &f%url%");
        String failure   = cfg("upload-log-upload-failure",  "%prefix% &cTrace save failed. Check console.");
        String uploading = cfg("upload-log-start",           "%prefix% &7Saving trace...");

        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, uploading)));

        VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runNow(VoidAPI.INSTANCE.getVoidPlugin(), () -> {
            String key = VoidAPI.INSTANCE.getConfigManager().getPastebinApiKey().trim();
            if (!key.isEmpty()) {
                try {
                    pastebin(sender, log, success, failure, consumer, key, flagId);
                    return;
                } catch (Exception e) {
                    LogUtil.warn("[VoidLog] Pastebin failed (" + e.getMessage() + "), saving locally.");
                }
            }
            saveLocal(sender, log, success, failure, consumer, flagId);
        });
    }

    // ── Pastebin upload ───────────────────────────────────────────────────

    private static void pastebin(Sender sender, String log, String success, String failure,
                                  Consumer<String> consumer, String apiKey, int flagId) throws IOException {
        URL url = new URL(PASTEBIN_API);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("User-Agent", "VoidAC/" + VoidAPI.INSTANCE.getExternalAPI().getVoidVersion());

        String name = "VoidAC-Debug" + (flagId >= 0 ? "-flag" + flagId : "");
        String body = "api_dev_key="        + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "&api_option=paste"
                + "&api_paste_code="       + URLEncoder.encode(log, StandardCharsets.UTF_8)
                + "&api_paste_name="       + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&api_paste_expire_date=1W"
                + "&api_paste_private=1";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        String response;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                code == 200 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
            response = br.readLine();
        }
        conn.disconnect();

        if (response != null && response.startsWith("https://pastebin.com/")) {
            String msg = success.replace("%url%", response);
            consumer.accept(msg);
            sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
        } else {
            throw new IOException("Pastebin: " + response);
        }
    }

    // ── Local file save ───────────────────────────────────────────────────

    private static void saveLocal(Sender sender, String log, String success, String failure,
                                   Consumer<String> consumer, int flagId) {
        File dir = new File(VoidAPI.INSTANCE.getVoidPlugin().getDataFolder(), "debug-logs");
        dir.mkdirs();

        String ts   = TS.format(LocalDateTime.now());
        String name = "void-" + ts + (flagId >= 0 ? "-flag" + flagId : "") + ".log";
        File   file = new File(dir, name);

        try (OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            fw.write(log);
        } catch (IOException e) {
            LogUtil.error("[VoidLog] Failed to write debug-logs/" + name, e);
            sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, failure)));
            return;
        }

        String msg = success.replace("%url%", file.getAbsolutePath());
        consumer.accept(msg);
        sender.sendMessage(MessageUtil.miniMessage(MessageUtil.replacePlaceholders(sender, msg)));
    }

    // ── Command registration ──────────────────────────────────────────────

    @Override
    public void register(CommandManager<Sender> commandManager, CloudCommandAdapter adapter) {
        Command<Sender> command = commandManager.commandBuilder("void", "voidac", "void", "voidac")
                .literal("log", "logs")
                .permission("void.log")
                .required("flagId", IntegerParser.integerParser())
                .handler(this::handleLog)
                .manager(commandManager)
                .build();
        commandManager
                .command(command)
                .command(commandManager.commandBuilder("gl").proxies(command));
    }

    private void handleLog(@NotNull CommandContext<Sender> context) {
        Sender sender  = context.sender();
        int    flagId  = context.get("flagId");

        StringBuilder builder = SuperDebug.getFlag(flagId);
        if (builder == null) {
            sender.sendMessage(MessageUtil.getParsedComponent(sender, "upload-log-not-found", "%prefix% &cNo trace found."));
            return;
        }
        sendLogAsync(sender, builder.toString(), s -> {}, flagId);
    }

    // ── Util ──────────────────────────────────────────────────────────────

    private static String cfg(String key, String def) {
        return VoidAPI.INSTANCE.getConfigManager().getConfig().getStringElse(key, def);
    }
}
