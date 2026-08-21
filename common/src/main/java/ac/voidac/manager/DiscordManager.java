package ac.voidac.manager;

import ac.voidac.VoidAPI;
import ac.voidac.api.VoidUser;
import ac.voidac.api.config.ConfigManager;
import ac.voidac.manager.init.ReloadableInitable;
import ac.voidac.manager.init.start.StartableInitable;
import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.anticheat.LogUtil;
import ac.voidac.utils.anticheat.MessageUtil;
import ac.voidac.utils.common.arguments.CommonVoidArguments;
import ac.voidac.utils.data.Pair;
import ac.voidac.utils.data.webhook.discord.CompiledDiscordTemplate;
import ac.voidac.utils.data.webhook.discord.Embed;
import ac.voidac.utils.data.webhook.discord.EmbedField;
import ac.voidac.utils.data.webhook.discord.EmbedFooter;
import ac.voidac.utils.data.webhook.discord.WebhookMessage;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class DiscordManager implements StartableInitable, ReloadableInitable {
    private static final Predicate<String> WEBHOOK_REGEX = Pattern.compile("^https://(?:canary\\.)?discord\\.com/api(?:/v\\d+)?/webhooks/\\d+/[\\w-]+(\\?thread_id=\\d+)?$").asMatchPredicate();
    private static final Predicate<String> HTTPS_URL_REGEX = Pattern.compile("^https://[^/\\s]+/\\S+$").asMatchPredicate();
    private static final Duration timeout = Duration.ofMillis(CommonVoidArguments.URL_TIMEOUT.value());
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
    private static final int MAX_QUEUE_SIZE = 50;
    private static final ConcurrentLinkedDeque<Pair<HttpRequest, CompletableFuture<Boolean>>> requests = new ConcurrentLinkedDeque<>();
    private static final AtomicBoolean taskStarted = new AtomicBoolean();
    private static final AtomicBoolean sending = new AtomicBoolean();
    private static long rateLimitedUntil;

    private static final String LOGO_FILENAME = "void_logo.png";
    private static final String LOGO_ATTACHMENT_REF = "attachment://" + LOGO_FILENAME;
    private static final String MULTIPART_BOUNDARY = "VoidACWebhook" + Long.toHexString(System.nanoTime());
    private static final byte[] LOGO_BYTES;

    // Pre-computed static multipart byte sequences, computed once and reused on every webhook post
    private static final byte[] MULTIPART_PART_JSON_HEADERS;
    private static final byte[] MULTIPART_PART_IMAGE_HEADERS;
    private static final byte[] MULTIPART_CRLF;
    private static final byte[] MULTIPART_CLOSE;

    static {
        byte[] bytes = null;
        try (InputStream in = DiscordManager.class.getResourceAsStream("/assets/voidac/" + LOGO_FILENAME)) {
            if (in != null) bytes = in.readAllBytes();
        } catch (IOException ignored) {}
        LOGO_BYTES = bytes;

        String nl = "\r\n";
        String dash = "--";
        MULTIPART_CRLF = nl.getBytes(StandardCharsets.UTF_8);
        MULTIPART_PART_JSON_HEADERS = (dash + MULTIPART_BOUNDARY + nl
                + "Content-Disposition: form-data; name=\"payload_json\"" + nl
                + "Content-Type: application/json" + nl + nl).getBytes(StandardCharsets.UTF_8);
        MULTIPART_PART_IMAGE_HEADERS = (dash + MULTIPART_BOUNDARY + nl
                + "Content-Disposition: form-data; name=\"files[0]\"; filename=\"" + LOGO_FILENAME + "\"" + nl
                + "Content-Type: image/png" + nl + nl).getBytes(StandardCharsets.UTF_8);
        MULTIPART_CLOSE = (dash + MULTIPART_BOUNDARY + dash + nl).getBytes(StandardCharsets.UTF_8);
    }

    private URI url;
    private int embedColor;
    private CompiledDiscordTemplate compiledContent;
    private char backtickReplacement = 'ˋ';
    private String embedTitle = "";
    private boolean includeTimestamp;
    private boolean includeVerbose;
    private boolean useInternalImage;
    private @Nullable String embedImageUrl;
    private @Nullable String embedThumbnailUrl;
    private @Nullable String embedFooterUrl;
    private String embedFooterText = "";

    private static final Pattern URL_PATTERN = Pattern.compile("^https?://(?:www\\.)?[-a-z0-9@:%._+~#=]{1,256}\\.[a-z0-9()]{1,6}\\b[-a-z0-9()@:%_+.~#?&/=]*$", Pattern.CASE_INSENSITIVE);

    private static String validatedConfigURL(String configPath, String defaultURL) {
        String url = VoidAPI.INSTANCE.getConfigManager().getConfig().getStringElse(configPath, defaultURL);
        if (url == null || url.isBlank()) return null;
        if (URL_PATTERN.matcher(url).matches()) {
            return url;
        } else {
            LogUtil.warn("Invalid embed url for config path " + configPath + ": " + url);
            return defaultURL;
        }
    }

    @Override
    public void start() {
        reload();
    }

    @Override
    public void reload() {
        try {
            // Yes all of these fields should technically be volatile so they will be updated correctly on reload for HTTP threads to read
            // No we're not going to pay for atomic reads in the hot loop however cheap for a one in a billion chance to read an outdated config
            // When your discord webhook settings are changed (who changes them in prod?) that can be fixed with a restart
            ConfigManager config = VoidAPI.INSTANCE.getConfigManager().getConfig();
            if (!config.getBooleanElse("enabled", false)) {
                url = null;
                return;
            }

            String webhook = config.getStringElse("webhook", "");
            boolean strictValidation = !config.getBooleanElse("disable-webhook-validation", false);

            if (webhook.isEmpty()) {
                url = null;
            } else if (strictValidation) {
                if (!WEBHOOK_REGEX.test(webhook)) {
                    LogUtil.error("Void webhook URL does not match expected format"
                            + " (https://discord.com/api/webhooks/<id>/<token>): " + webhook);
                    LogUtil.error("If you are using a proxy or custom endpoint,"
                            + " set 'disable-webhook-validation: true' in the Void config.");
                    url = null;
                } else {
                    url = new URI(webhook);
                }
            } else {
                if (!HTTPS_URL_REGEX.test(webhook)) {
                    LogUtil.error("Void webhook URL is not a valid HTTPS URL: " + webhook);
                    url = null;
                } else {
                    LogUtil.info("Webhook validation disabled, using custom Void endpoint: "
                            + webhook.substring(0, Math.min(webhook.length(), 40)) + "...");
                    url = new URI(webhook);
                }
            }
            // not adding these to the config since they may change in the future
            // mainly for just for allowing more customization
            embedImageUrl = validatedConfigURL("embed-image-url", null);
            embedThumbnailUrl = validatedConfigURL("embed-thumbnail-url", "https://crafthead.net/helm/%uuid%");
            embedFooterUrl = validatedConfigURL("embed-footer-url", null);
            // When no image URL is configured, use the bundled logo as the large embed image
            if (embedImageUrl == null && LOGO_BYTES != null) {
                embedImageUrl = LOGO_ATTACHMENT_REF;
                useInternalImage = true;
            } else {
                useInternalImage = false;
            }
            embedFooterText = config.getStringElse("embed-footer-text", "Void v%void_version%");
            embedTitle = config.getStringElse("embed-title", "**Void Signal**");

            try {
                embedColor = Color.decode(config.getStringElse("embed-color", "#8B5CF6")).getRGB();
            } catch (NumberFormatException e) {
                LogUtil.warn("Void webhook embed color is invalid");
            }

            StringBuilder sb = new StringBuilder();
            for (String string : config.getStringListElse("violation-content", getDefaultContents())) {
                sb.append(string).append("\n");
            }
            includeTimestamp = config.getBooleanElse("include-timestamp", true);
            includeVerbose = config.getBooleanElse("include-verbose", true);
            String btReplace = config.getStringElse("backtick-replacement-char", "ˋ");
            backtickReplacement = (btReplace.isEmpty()) ? 'ˋ' : btReplace.charAt(0);
            compiledContent = CompiledDiscordTemplate.compile(sb.toString());
        } catch (Exception e) {
            LogUtil.error("Failed to load Void webhook configuration", e);
        }
    }

    @Contract(value = " -> new", pure = true)
    private @NotNull @Unmodifiable List<@NotNull String> getDefaultContents() {
        return List.of(
                "**Target**: `%player%`",
                "**Trace**: %check%",
                "**Violations**: %violations%",
                "**Client**: %version%",
                "**Brand**: `%brand%`",
                "**Ping**: %ping%",
                "**TPS**: %tps%"
        );
    }

    public void sendAlert(@NotNull VoidPlayer player, String verbose, String checkName, int violations) {
        if (isDisabled()) {
            return;
        }

        // Per-alert overlay to avoid polluting the global static map
        Map<String, String> statics = new HashMap<>(VoidAPI.INSTANCE.getExternalAPI().getStaticReplacements());
        statics.put("%check%", checkName);
        statics.put("%violations%", Integer.toString(violations));

        Map<String, Function<VoidUser, String>> dynamics = VoidAPI.INSTANCE.getExternalAPI().getVariableReplacements();

        String content = compiledContent.render(player, statics, dynamics, backtickReplacement);

        Embed embed = new Embed(content)
                .color(embedColor)
                .title(embedTitle)
                .imageURL(MessageUtil.replacePlaceholders(player, embedImageUrl, false))
                .thumbnailURL(MessageUtil.replacePlaceholders(player, embedThumbnailUrl, false))
                .footer(new EmbedFooter(
                        MessageUtil.replacePlaceholders(player, embedFooterText, false),
                        MessageUtil.replacePlaceholders(player, embedFooterUrl, false)
                ));

        if (includeTimestamp) embed.timestamp(Instant.now());

        if (!verbose.isEmpty() && includeVerbose) {
            embed.addFields(new EmbedField("Verbose", CompiledDiscordTemplate.escapeMarkdown(verbose), true));
        }

        WebhookMessage message = new WebhookMessage().addEmbeds(embed);
        if (useInternalImage && LOGO_BYTES != null) {
            sendWebhookMessageInternal(message, LOGO_BYTES);
        } else {
            sendWebhookMessage(message);
        }
    }

    public CompletableFuture<Boolean> sendWebhookMessage(WebhookMessage message) {
        return sendWebhookMessageInternal(message, null);
    }

    private CompletableFuture<Boolean> sendWebhookMessageInternal(WebhookMessage message, byte[] attachment) {
        if (isDisabled()) return CompletableFuture.completedFuture(false);

        String json = message.toJson().toString();
        HttpRequest request;
        if (attachment != null) {
            request = HttpRequest.newBuilder()
                    .uri(url)
                    .header("Content-Type", "multipart/form-data; boundary=" + MULTIPART_BOUNDARY)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(buildMultipart(json, attachment)))
                    .timeout(timeout)
                    .build();
        } else {
            request = HttpRequest.newBuilder()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(timeout)
                    .build();
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        // Drop the oldest queued alert if the queue is full (e.g. during a long rate-limit window)
        // so the deque cannot grow without bound. Recent alerts are more useful than stale ones.
        while (requests.size() >= MAX_QUEUE_SIZE) {
            Pair<HttpRequest, CompletableFuture<Boolean>> dropped = requests.pollFirst();
            if (dropped != null) dropped.second().complete(false);
        }
        requests.add(new Pair<>(request, future));

        if (!taskStarted.getAndSet(true)) {
            // there's probably a better way to handle rate limits, but this works, so whatever.
            VoidAPI.INSTANCE.getScheduler().getAsyncScheduler().runAtFixedRate(VoidAPI.INSTANCE.getVoidPlugin(), DiscordManager::tick, 0, 1);
        }

        return future;
    }

    private static byte[] buildMultipart(String json, byte[] file) {
        byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream out = new ByteArrayOutputStream(
                MULTIPART_PART_JSON_HEADERS.length + jsonBytes.length + MULTIPART_CRLF.length
                + MULTIPART_PART_IMAGE_HEADERS.length + file.length + MULTIPART_CRLF.length
                + MULTIPART_CLOSE.length);
        try {
            out.write(MULTIPART_PART_JSON_HEADERS);
            out.write(jsonBytes);
            out.write(MULTIPART_CRLF);
            out.write(MULTIPART_PART_IMAGE_HEADERS);
            out.write(file);
            out.write(MULTIPART_CRLF);
            out.write(MULTIPART_CLOSE);
        } catch (IOException ignored) {}
        return out.toByteArray();
    }

    public boolean isDisabled() {
        return url == null;
    }

    private static void tick() {
        Pair<HttpRequest, CompletableFuture<Boolean>> pair = requests.peek();
        if (pair != null && rateLimitedUntil < System.currentTimeMillis() && !sending.getAndSet(true)) {
            HttpRequest request = pair.first();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, throwable) -> {
                if (throwable != null) {
                    sending.set(false);
                    LogUtil.error("Exception caught while sending a Void webhook alert", throwable);
                    return;
                }

                if (response != null && response.statusCode() == 429) {
                    sending.set(false);
                    var reset = response.headers().firstValueAsLong("X-RateLimit-Reset");
                    if (reset.isPresent()) {
                        rateLimitedUntil = Math.max(reset.getAsLong() * 1000, rateLimitedUntil);
                    }
                    return;
                }

                requests.remove(pair);
                sending.set(false);

                // TODO: handle 503 (Service Unavailable)?
                if (response != null && response.statusCode() >= 400) {
                    LogUtil.error("Encountered status code " + response.statusCode() + " with body " + response.body() + " and headers " + response.headers().map() + " while sending a Void webhook alert.");
                    pair.second().complete(false);
                } else {
                    pair.second().complete(true);
                }
            });
        }
    }
}
