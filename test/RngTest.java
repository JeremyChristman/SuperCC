import game.RNG;

import java.util.Arrays;

/**
 * The random number generator.
 *
 * THE ORACLE, AND WHY IT IS NOT "WHATEVER SUPERCC PRINTS"
 * ------------------------------------------------------
 * SuperCC and Tile World share one generator, byte for byte. It is a fixed LCG that Tile World
 * hard-coded so that recorded solutions replay identically:
 *
 *     next = (value * 1103515245 + 12345) & 0x7FFFFFFF
 *     random4() = next >>> 29
 *
 * That shared stream is the reason most blob and walker levels port between the two engines at all.
 * So this file does not ask "what does RNG.java produce"; it implements the documented formula
 * independently, right here, and requires the two to agree step for step. If RNG.java is ever
 * edited, the reimplementation below does not move with it, and they diverge.
 *
 * 🔴 THE MOST IMPORTANT TEST IN THIS FILE IS SECTION 3, AND IT ASSERTS A DIFFERENCE.
 * The original CHIPS.EXE used a completely different generator -- the Microsoft C runtime's rand().
 * Reverse-engineering established that matching MSCC here would be the WRONG fix: it would break
 * every SuperCC-to-Tile-World port, because Tile World already matches what SuperCC does. The RNG
 * is NOT the cause of the remaining SuperCC/TW desyncs; those are a per-tick order and timing
 * subtlety. Section 3 exists so that a future "let us be faithful to the original game" change
 * fails loudly instead of silently breaking the fork's whole reason for existing.
 *
 * Per-creature consumption, also established by that work and pinned below: a blob draws one
 * randomPermutation4, a walker one randomPermutation3, a random force floor one random4.
 */
public class RngTest {

    public static void main(String[] args) {
        System.exit(Harness.run("RngTest", RngTest::body));
    }

    /**
     * The documented Tile World LCG, reimplemented from the formula rather than called through
     * RNG. This is the oracle: it is deliberately a separate piece of arithmetic, so that an edit
     * to RNG.java cannot drag the expectation along with it.
     */
    private static int twNext(int value) {
        return (value * 1103515245 + 12345) & 0x7FFFFFFF;
    }

    /**
     * The Microsoft C runtime generator that the ORIGINAL CHIPS.EXE used. Present only so that
     * section 3 can assert SuperCC does NOT behave like it.
     */
    private static int msccNext(int seed) {
        return seed * 214013 + 2531011;
    }

    /** Which slot of the permuted array a given element ended up in, for the "it shuffles" checks. */
    private static int slotOf(Object[] arr, String element) {
        for (int i = 0; i < arr.length; i++) if (element.equals(arr[i])) return i;
        return -1;
    }

    private static RNG rngWithSeed(int seed) {
        RNG r = new RNG(0, 0, 0);
        r.setCurrentValue(seed);
        return r;
    }

    private static void body() throws Exception {

        Harness.section("1. the LCG matches Tile World's, step for step");
        /* Two hundred steps against an independent implementation of the published formula. One or
         * two steps could agree by luck with a wrong multiplier; two hundred cannot. */
        int[] seeds = {0, 1, 12345, 0x2A2A2A2A, 0x7FFFFFFF};
        boolean allMatch = true;
        int firstBadSeed = -1, firstBadStep = -1;
        for (int seed : seeds) {
            RNG rng = rngWithSeed(seed);
            int expected = seed;
            for (int step = 0; step < 200; step++) {
                expected = twNext(expected);
                rng.random4();                       // advances exactly once
                if (rng.getCurrentValue() != expected) {
                    allMatch = false;
                    if (firstBadSeed < 0) { firstBadSeed = seed; firstBadStep = step; }
                    break;
                }
            }
        }
        Harness.check("1000 steps across 5 seeds match the documented LCG"
                      + (allMatch ? "" : " (first divergence: seed " + firstBadSeed + " step " + firstBadStep + ")"),
                      allMatch);

        Harness.section("2. random4() is the top two bits, and advances the stream once");
        /* random4 = next >>> 29 on a value already masked to 31 bits, so it yields 0..3. Both
         * halves matter: the RANGE, and that it consumes exactly one step -- a generator that
         * advanced twice would desynchronize the shared stream with Tile World immediately. */
        RNG r2 = rngWithSeed(12345);
        int prev = r2.getCurrentValue();
        boolean rangeOk = true, derivationOk = true, advanceOk = true;
        boolean[] seen = new boolean[4];
        for (int i = 0; i < 500; i++) {
            int expectedValue = twNext(prev);
            int v = r2.random4();
            if (v < 0 || v > 3) rangeOk = false;
            if (v != (expectedValue >>> 29)) derivationOk = false;
            if (r2.getCurrentValue() != expectedValue) advanceOk = false;
            seen[v] = true;
            prev = expectedValue;
        }
        Harness.check("every result is in 0..3", rangeOk);
        Harness.check("each result is exactly next >>> 29", derivationOk);
        Harness.check("each call advances the stream exactly once", advanceOk);
        Harness.check("all four outcomes occur (the generator is not stuck)",
                      seen[0] && seen[1] && seen[2] && seen[3]);

        Harness.section("3. it is deliberately NOT the original game's generator");
        /* 🔴 DO NOT 'FIX' THIS. CHIPS.EXE used the Microsoft C runtime's rand(); SuperCC and Tile
         * World share a different, fixed LCG. Reverse-engineering settled that making this match
         * MSCC would BREAK every SuperCC-to-Tile-World port, because TW already matches SuperCC.
         * The remaining desyncs are a per-tick order and timing subtlety, not the RNG. If this
         * assertion ever fails, someone has "corrected" the generator toward the original game and
         * has broken the fork's whole purpose. */
        RNG r3 = rngWithSeed(1);
        int mscc = 1;
        int agreements = 0;
        for (int i = 0; i < 50; i++) {
            r3.random4();
            mscc = msccNext(mscc);
            if (r3.getCurrentValue() == (mscc & 0x7FFFFFFF)) agreements++;
        }
        Harness.eq("the SuperCC/TW stream never coincides with the MSCC rand() stream",
                   agreements, 0);

        Harness.section("4. the same seed always replays the same stream");
        /* Determinism is the entire basis of solution playback: a stored solution replays only
         * because the generator is a pure function of its seed. */
        RNG a = rngWithSeed(0x1234567);
        RNG b = rngWithSeed(0x1234567);
        StringBuilder sa = new StringBuilder(), sb = new StringBuilder();
        for (int i = 0; i < 100; i++) { sa.append(a.random4()); sb.append(b.random4()); }
        Harness.eq("two generators on one seed produce identical output", sa.toString(), sb.toString());

        RNG c = rngWithSeed(0x1234568);          // one bit different
        StringBuilder sc = new StringBuilder();
        for (int i = 0; i < 100; i++) sc.append(c.random4());
        Harness.check("a different seed produces a different stream", !sa.toString().equals(sc.toString()));

        Harness.section("5. the seed is masked to 31 bits");
        /* The generator's state is 31 bits. A seed with the sign bit set has to be masked on the
         * way in, or the very first multiply starts from a state the reference never reaches. */
        RNG r5 = rngWithSeed(0xFFFFFFFF);
        Harness.eq("a seed with the high bit set is masked to 0x7FFFFFFF",
                   r5.getCurrentValue(), 0x7FFFFFFF);
        Harness.eq("LAST_SEED names that maximum", RNG.LAST_SEED, 0x7FFFFFFF);

        Harness.section("6. randomPermutation3 permutes, and costs exactly one step");
        /* Used by walkers on {left, backwards, right}. Two properties, and losing either is a
         * desync: the array must still hold the same three elements (a permutation, not a
         * rewrite), and the call must consume exactly one step of the shared stream. */
        RNG r6 = rngWithSeed(99);
        int expected6 = r6.getCurrentValue();
        boolean perm3Ok = true, advance3Ok = true;
        boolean[] seenFirst = new boolean[3];
        for (int i = 0; i < 300; i++) {
            Object[] arr = {"L", "B", "R"};
            expected6 = twNext(expected6);
            r6.randomPermutation3(arr);
            if (r6.getCurrentValue() != expected6) advance3Ok = false;
            Object[] sorted = arr.clone();
            Arrays.sort(sorted);
            if (!Arrays.equals(sorted, new Object[]{"B", "L", "R"})) perm3Ok = false;
            seenFirst["LBR".indexOf((String) arr[0])] = true;
        }
        Harness.check("the three elements are always all still present", perm3Ok);
        Harness.check("each call advances the stream exactly once", advance3Ok);
        Harness.check("every element reaches the front at least once (it really shuffles)",
                      seenFirst[0] && seenFirst[1] && seenFirst[2]);

        Harness.section("7. randomPermutation4 permutes, and costs exactly one step");
        /* Used by blobs on {forwards, left, backwards, right}. Blobs are the single largest
         * consumer of the shared stream, so an extra or missing step here cascades forever. */
        RNG r7 = rngWithSeed(4242);
        int expected7 = r7.getCurrentValue();
        boolean perm4Ok = true, advance4Ok = true;
        boolean[] seenFirst4 = new boolean[4];
        for (int i = 0; i < 400; i++) {
            Object[] arr = {"F", "L", "B", "R"};
            expected7 = twNext(expected7);
            r7.randomPermutation4(arr);
            if (r7.getCurrentValue() != expected7) advance4Ok = false;
            Object[] sorted = arr.clone();
            Arrays.sort(sorted);
            if (!Arrays.equals(sorted, new Object[]{"B", "F", "L", "R"})) perm4Ok = false;
            seenFirst4["FLBR".indexOf((String) arr[0])] = true;
        }
        Harness.check("the four elements are always all still present", perm4Ok);
        Harness.check("each call advances the stream exactly once", advance4Ok);
        Harness.check("every element reaches the front at least once",
                      seenFirst4[0] && seenFirst4[1] && seenFirst4[2] && seenFirst4[3]);

        Harness.section("8. pseudoRandom4 is a SEPARATE stream from the main LCG");
        /* The Lynx walker generator. It runs on prngValue1/prngValue2 and must not touch
         * currentValue -- if it did, Lynx play would consume the MS stream and the two rulesets
         * would interfere. */
        RNG r8 = rngWithSeed(777);
        int before = r8.getCurrentValue();
        boolean prngRangeOk = true;
        for (int i = 0; i < 200; i++) {
            int v = r8.pseudoRandom4();
            if (v < 0 || v > 3) prngRangeOk = false;
        }
        Harness.check("every pseudoRandom4 result is in 0..3", prngRangeOk);
        Harness.eq("and the main LCG state is untouched", r8.getCurrentValue(), before);

        RNG r8b = rngWithSeed(777);
        r8b.setPRNG1(0x5A); r8b.setPRNG2(0x3C);
        Harness.eq("PRNG state round-trips through its setter (savestates rely on this)",
                   r8b.getPRNG1(), 0x5A);
        Harness.eq("and the second half too", r8b.getPRNG2(), 0x3C);

        Harness.section("9. KNOWN DEFECT: the constructor drops its prng arguments (harmless today)");
        /* Not a test of desired behavior: a record of a real latent defect, pinned where whoever
         * trips over it will be standing.
         *
         * `new RNG(seed, p1, p2)` assigns currentValue and silently ignores p1 and p2. It is
         * harmless TODAY only because both call sites in LevelFactory pass (0, 0), which is what
         * the fields default to anyway. Add a caller that passes a real starting PRNG state and it
         * will be discarded without a word, and Lynx walkers will run on the wrong stream.
         *
         * Left unfixed on purpose: RNG.java is pristine upstream code and is NOT in
         * $SPLICE_MODIFIED, so repairing it would diverge the fork for a bug with no present
         * effect. The savestate path sets these through the setters and is unaffected. */
        RNG r9 = new RNG(0, 0x11, 0x22);
        Harness.eq("prngValue1 argument is dropped (should be 0x11 if it were honored)",
                   r9.getPRNG1(), 0);
        Harness.eq("prngValue2 argument is dropped as well", r9.getPRNG2(), 0);
        Harness.eq("only the seed argument actually lands", r9.getCurrentValue(), 0);
    }

}
