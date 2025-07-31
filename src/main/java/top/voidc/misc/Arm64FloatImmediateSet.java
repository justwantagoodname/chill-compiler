package top.voidc.misc;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Gemini 2.5 Pro
 */
public class Arm64FloatImmediateSet {

    /**
     * A pre-computed set of all possible float values that can be represented
     * as an immediate in the ARM64 architecture.
     * The ARM64 FMOV instruction can load an 8-bit immediate which is then
     * expanded into a 32-bit single-precision float. This set contains all
     * 256 possible values.
     */
    private static final Set<Float> VALID_IMMEDIATES;

    // Static initializer block to generate and store all possible immediates.
    // This code runs only once when the class is loaded.
    static {
        Set<Float> immediates = new HashSet<>();
        // Iterate through all 256 possible 8-bit immediate values.
        for (int imm8 = 0; imm8 < 256; imm8++) {
            // Decode the 8-bit immediate into a 32-bit IEEE 754 float representation.

            // Extract components from imm8 (abcdefgh)
            // Bit 7 (a) -> sign
            // Bits 6-4 (bcd) -> exponent part
            // Bits 3-0 (efgh) -> mantissa part
            int signBit = (imm8 >> 7) & 1;
            int expBits = (imm8 >> 4) & 7; // bcd
            int mantissaBits = imm8 & 15; // efgh

            // Construct the 32-bit float representation.
            // Sign: The highest bit of the float.
            int floatSign = signBit << 31;

            // Mantissa: The 4 bits from imm8 become the top 4 bits of the 23-bit mantissa.
            // The remaining 19 bits are zero.
            int floatMantissa = mantissaBits << 19;

            // Exponent: This is the most complex part. The 3 bits from imm8 (bcd)
            // form the 8-bit exponent field of the float.
            // Let b = expBits[2], c = expBits[1], d = expBits[0]
            // The 8-bit float exponent is: NOT(b), c, c, c, c, c, d, d
            int b = (expBits >> 2) & 1;
            int c = (expBits >> 1) & 1;
            int d = expBits & 1;

            int not_b = (b == 0) ? 1 : 0;

            int floatExponent = (not_b << 7) | (c << 6) | (c << 5) | (c << 4) | (c << 3) | (c << 2) | (d << 1) | d;

            // Shift the exponent to its correct position in the 32-bit float.
            floatExponent <<= 23;

            // Combine all parts to form the final 32-bit integer representation.
            int floatAsInt = floatSign | floatExponent | floatMantissa;

            // Convert the integer bit-representation to an actual float and add to the set.
            immediates.add(Float.intBitsToFloat(floatAsInt));
        }
        // Make the set unmodifiable for safety.
        VALID_IMMEDIATES = Collections.unmodifiableSet(immediates);
    }

    /**
     * Checks if a given float value can be loaded as an immediate operand
     * by the ARM64 FMOV instruction.
     *
     * @param value The float value to check.
     * @return {@code true} if the float can be represented as an ARM64 immediate,
     *         {@code false} otherwise.
     */
    public static boolean canBeArm64Immediate(float value) {
        // The check is now a simple and efficient lookup in the pre-computed set.
        // Note: Float.NaN will always return false, which is correct.
        // For -0.0f, it depends on whether the 0-value imm8 generates +0.0 or -0.0.
        // Our generator correctly handles the sign bit, so both 0.0f and -0.0f
        // can be checked.
        return VALID_IMMEDIATES.contains(value);
    }
}