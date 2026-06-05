package ac.voidac.utils.data.packetentity;

import ac.voidac.player.VoidPlayer;
import ac.voidac.utils.data.VectorData;

import java.util.Set;

public interface JumpableEntity {

    boolean isJumping();

    void setJumping(boolean jumping);

    float getJumpPower();

    void setJumpPower(float jumpPower);

    boolean canPlayerJump(VoidPlayer player);

    boolean hasSaddle();

    void executeJump(VoidPlayer player, Set<VectorData> possibleVectors);

}
