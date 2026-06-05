package ac.voidac.platform.fabric.mc1171.entity;

import ac.voidac.platform.fabric.mc1161.entity.Fabric1161VoidEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class Fabric1170VoidEntity extends Fabric1161VoidEntity {

    public Fabric1170VoidEntity(Entity entity) {
        super(entity);
    }

    @Override
    public boolean isDead() {
        return this.entity instanceof LivingEntity living ? living.isDeadOrDying() : this.entity.isRemoved();
    }
}
