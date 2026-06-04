package com.mojang.minecraft.level;

/**
 * Seeded Perlin noise with octave layering.
 *
 * Usage:
 *   PerlinNoise n = new PerlinNoise(seed, octaves, persistence);
 *   double value = n.sample(x, z);   // returns roughly [-1, 1]
 */
public class PerlinNoise {

    // ---------------------------------------------------------------------------
    // Permutation table – shuffled once per seed using a fast xorshift64 PRNG
    // ---------------------------------------------------------------------------
    private final int[] perm = new int[512];

    /** Number of layered octaves. */
    private final int octaves;
    /** How quickly amplitude falls per octave (typically 0.5). */
    private final double persistence;

    public PerlinNoise(long seed, int octaves, double persistence) {
        this.octaves   = octaves;
        this.persistence = persistence;

        // Build a seeded permutation table [0..255] using Fisher-Yates shuffle
        // driven by a xorshift64 PRNG so every seed gives a unique table.
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;

        long s = seed;
        for (int i = 255; i > 0; i--) {
            // xorshift64
            s ^= s << 13;
            s ^= s >>> 7;
            s ^= s << 17;
            int j = (int)((s & Long.MAX_VALUE) % (i + 1));
            int tmp = p[i]; p[i] = p[j]; p[j] = tmp;
        }
        // Double the table to avoid index wrapping
        for (int i = 0; i < 256; i++) perm[i] = perm[i + 256] = p[i];
    }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    /**
     * Sample layered Perlin noise at (x, z).
     * Coordinate units are "blocks"; scale each octave internally.
     *
     * @return value in approximately [-1, 1]
     */
    public double sample(double x, double z) {
        double total = 0;
        double frequency = 1.0;
        double amplitude = 1.0;
        double maxValue  = 0;

        for (int o = 0; o < octaves; o++) {
            total    += perlin(x * frequency, z * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2.0;
        }
        return total / maxValue;   // normalise to [-1, 1]
    }

    // ---------------------------------------------------------------------------
    // Classic Perlin internals
    // ---------------------------------------------------------------------------

    private double perlin(double x, double z) {
        int xi = (int) Math.floor(x) & 255;
        int zi = (int) Math.floor(z) & 255;
        double xf = x - Math.floor(x);
        double zf = z - Math.floor(z);
        double u = fade(xf);
        double v = fade(zf);

        int aa = perm[perm[xi    ] + zi    ];
        int ab = perm[perm[xi    ] + zi + 1];
        int ba = perm[perm[xi + 1] + zi    ];
        int bb = perm[perm[xi + 1] + zi + 1];

        double x1 = lerp(u, grad(aa, xf,     zf    ),
                            grad(ba, xf - 1, zf    ));
        double x2 = lerp(u, grad(ab, xf,     zf - 1),
                            grad(bb, xf - 1, zf - 1));
        return lerp(v, x1, x2);
    }

    private static double fade(double t) {
        // Ken Perlin's improved smoothstep: 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** 2-D gradient using only x and z components. */
    private static double grad(int hash, double x, double z) {
        int h = hash & 3;
        double u = (h < 2) ? x : z;
        double v = (h < 2) ? z : x;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
}
