package ac.voidac.api.storage.category;

import ac.voidac.api.storage.event.PlayerIdentityEvent;
import ac.voidac.api.storage.event.SessionEvent;
import ac.voidac.api.storage.event.SettingEvent;
import ac.voidac.api.storage.event.ViolationEvent;
import ac.voidac.api.storage.model.BlobRef;
import ac.voidac.api.storage.model.PlayerIdentity;
import ac.voidac.api.storage.model.SessionRecord;
import ac.voidac.api.storage.model.SettingRecord;
import ac.voidac.api.storage.model.ViolationRecord;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.function.Supplier;

@ApiStatus.Experimental
public final class Categories {

    public static final Category<ViolationEvent> VIOLATION = new Builtin<>(
            "violation",
            ViolationEvent.class,
            ViolationEvent::new,
            ViolationRecord.class,
            EnumSet.of(Capability.INDEXED_KV, Capability.TIMESERIES_APPEND, Capability.HISTORY),
            AccessPattern.TIMESERIES);

    public static final Category<SessionEvent> SESSION = new Builtin<>(
            "session",
            SessionEvent.class,
            SessionEvent::new,
            SessionRecord.class,
            EnumSet.of(Capability.INDEXED_KV, Capability.HISTORY),
            AccessPattern.INDEXED_KV);

    public static final Category<PlayerIdentityEvent> PLAYER_IDENTITY = new Builtin<>(
            "player-identity",
            PlayerIdentityEvent.class,
            PlayerIdentityEvent::new,
            PlayerIdentity.class,
            EnumSet.of(Capability.INDEXED_KV, Capability.PLAYER_IDENTITY),
            AccessPattern.INDEXED_KV);

    public static final Category<SettingEvent> SETTING = new Builtin<>(
            "setting",
            SettingEvent.class,
            SettingEvent::new,
            SettingRecord.class,
            EnumSet.of(Capability.INDEXED_KV, Capability.SETTINGS),
            AccessPattern.INDEXED_KV);

    /**
     * Blob category. Declared on the surface for shape-consistency with the
     * other categories, but no producer currently publishes to it and the
     * event factory refuses to construct slots — a future recorder feature
     * will hook it up.
     */
    public static final Category<BlobRef> BLOB = new Builtin<>(
            "blob",
            BlobRef.class,
            () -> { throw new UnsupportedOperationException("BLOB category has no event factory"); },
            BlobRef.class,
            EnumSet.of(Capability.BLOB),
            AccessPattern.BLOB_REF);

    private Categories() {}

    private record Builtin<E>(
            @NotNull String id,
            @NotNull Class<E> eventType,
            @NotNull Supplier<E> newEvent,
            @NotNull Class<?> queryResultType,
            @NotNull EnumSet<Capability> requiredCapabilities,
            @NotNull AccessPattern accessPattern) implements Category<E> {}
}
