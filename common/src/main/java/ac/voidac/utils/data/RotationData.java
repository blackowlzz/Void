package ac.voidac.utils.data;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.Contract;

@Getter
@EqualsAndHashCode
@ToString
public final class RotationData {
    private final float yaw;
    private final float pitch;
    private final boolean relativeYaw;
    private final boolean relativePitch;
    private final int transaction;
    private boolean isAccepted;

    public RotationData(float yaw, float pitch, int transaction) {
        this(yaw, pitch, false, false, transaction);
    }

    public RotationData(float yaw, float pitch, boolean relativeYaw, boolean relativePitch, int transaction) {
        this.yaw = yaw;
        this.pitch = pitch;
        this.relativeYaw = relativeYaw;
        this.relativePitch = relativePitch;
        this.transaction = transaction;
    }

    @Contract(mutates = "this")
    public void accept() {
        this.isAccepted = true;
    }
}
