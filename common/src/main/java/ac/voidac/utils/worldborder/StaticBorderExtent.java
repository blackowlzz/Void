package ac.voidac.utils.worldborder;

import ac.voidac.utils.math.VoidMath;

public record StaticBorderExtent(double size) implements BorderExtent {

    @Override
    public double getMinX(double centerX, double absoluteMaxSize) {
        return VoidMath.clamp(centerX - size / 2.0, -absoluteMaxSize, absoluteMaxSize);
    }

    @Override
    public double getMaxX(double centerX, double absoluteMaxSize) {
        return VoidMath.clamp(centerX + size / 2.0, -absoluteMaxSize, absoluteMaxSize);
    }

    @Override
    public double getMinZ(double centerZ, double absoluteMaxSize) {
        return VoidMath.clamp(centerZ - size / 2.0, -absoluteMaxSize, absoluteMaxSize);
    }

    @Override
    public double getMaxZ(double centerZ, double absoluteMaxSize) {
        return VoidMath.clamp(centerZ + size / 2.0, -absoluteMaxSize, absoluteMaxSize);
    }

    @Override
    public BorderExtent tick() {
        return this;
    }

    @Override
    public BorderExtent update() {
        return this;
    }

}
