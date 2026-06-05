package ac.voidac.platform.fabric.entity;

import ac.voidac.platform.api.entity.VoidEntity;
import ac.voidac.platform.api.world.PlatformWorld;
import ac.voidac.utils.math.Location;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.Entity;

public abstract class AbstractFabricVoidEntity implements VoidEntity {

    protected final Entity entity;

    public AbstractFabricVoidEntity(Entity entity) {
        this.entity = Objects.requireNonNull(entity);
    }

    @Override
    public UUID getUniqueId() {
        return entity.getUUID();
    }

    @Override
    public boolean eject() {
        if (entity.isVehicle()) {
            entity.ejectPassengers();
            return true;
        }
        return false;
    }

    @Override
    public @NotNull Entity getNative() {
        return this.entity;
    }

    @Override
    public PlatformWorld getWorld() {
        return this.entity.level;
    }

    @Override
    public Location getLocation() {
        return new Location(
                this.getWorld(),
                this.entity.getX(),
                this.entity.getY(),
                this.entity.getZ(),
                this.entity.getViewYRot(1.0F),
                this.entity.getViewXRot(1.0F)
        );
    }

    @Override
    public double distanceSquared(double oX, double oY, double oZ) {
        double x = this.entity.getX();
        double y = this.entity.getY();
        double z = this.entity.getZ();
        double distX = (x - oX) * (x - oX);
        double distY = (y - oY) * (y - oY);
        double distZ = (z - oZ) * (z - oZ);
        return distX + distY + distZ;
    }
}
