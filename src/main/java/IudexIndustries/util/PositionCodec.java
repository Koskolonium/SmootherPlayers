package IudexIndustries.util;

import lombok.experimental.UtilityClass;

/**
 * The fixed-point encoding the client uses for entity positions in relative move packets:
 * whole steps of 1/4096 of a block, with each move limited to what fits in a short (~8 blocks).
 */
@UtilityClass
public class PositionCodec {

    public final double STEPS_PER_BLOCK = 4096.0;

    /** Same rounding the client applies to an entity's base position. */
    public long encode(double coordinate) {
        return Math.round(coordinate * STEPS_PER_BLOCK);
    }

    public double toBlocks(long steps) {
        return steps / STEPS_PER_BLOCK;
    }

    public boolean fitsInMove(long steps) {
        return steps >= Short.MIN_VALUE && steps <= Short.MAX_VALUE;
    }
}
