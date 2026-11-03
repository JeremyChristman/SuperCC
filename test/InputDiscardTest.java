import emulator.Solution;
import emulator.SuperCC;
import emulator.TickFlags;
import game.Direction;
import game.Level;
import game.Position;
import game.Ruleset;
import game.Step;
import game.Tile;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * INPUT DISCARDING: which key presses the engine ignores, and what the move list records for them.
 * Upstream's b997c27 + 98366f7, merged as step 2 of the upstream merge.
 *
 * WHAT CHANGED
 * ------------
 * Level.tick used to return a boolean ("the next move is automatic"). It now returns two bits:
 * MASK_TICK_MULTI (the same thing) and MASK_DISCARD_INPUT ("the key given to this tick was not
 * used"). SuperCC.tick records a WAIT in the move list for a discarded key. Only Lynx sets the
 * discard bit; MS never does (98366f7 took b997c27's MS discard back out -- section 4 shows why).
 *
 * THE RULES, FROM TILE WORLD (lxlogic.c / play.c)
 * -----------------------------------------------
 *  - advancegame (lxlogic.c:1958): choosemove(cr) runs only `if (cr->moving <= 0)`. A key pressed
 *    while Chip is between tiles reaches no decision at all.
 *  - choosemove (1038): `choosechipmove(cr, getforcedmove(cr))`, and choosechipmove (949) consumes
 *    the key and drops it when discard is TRUE: "the player's input should not be retained."
 *  - getforcedmove (1002): on ICE, forced unless Chip has skates (or no direction yet); on a FORCE
 *    FLOOR, forced unless Chip has boots -- `return !(cr->state & CS_SLIDETOKEN)`; and on tick 0
 *    never.
 *  - the slide token (startmovement, 1209): set when Chip starts a move FROM a force floor with no
 *    chosen direction (a forced slide); any other move clears it, except one starting from ice
 *    without skates, which keeps it. So the first force floor Chip walks onto holds him, and the
 *    next one in a slide lets him step off.
 *  - play.c doturn (404): Tile World records a move only `if (state.lastmove)`, and lastmove is set
 *    only when choosechipmove ACCEPTS a key. Tile World never records a key it threw away, which
 *    is what the WAIT in SuperCC's move list now matches.
 *
 * WHY IT IS SAFE
 * --------------
 * A discarded key changes nothing in the engine, so recording it as a wait cannot change a replay.
 * Measured on the maintainer's whole collection: 24,204 solutions end identically to jc-14; 40
 * Lynx routes (and no MS route) record differently once loaded; all 40, saved and reloaded, end in
 * the same state; and Tile World accepts both the old and the new .tws export of the three routes
 * where a mid-move key was followed by a wait. Section 3 checks the same round trip here.
 */
public class InputDiscardTest {

    public static void main(String[] args) {
        System.exit(Harness.run("InputDiscardTest", InputDiscardTest::body));
    }

    private static final int MULTI = Level.MASK_TICK_MULTI, DISCARD = Level.MASK_DISCARD_INPUT;

    /** One level: Chip at 3,5 facing right, `row` laid from x=4 rightward on row 5. */
    private static File set(Path dir, String name, int signature, Tile... row) throws Exception {
        return setWith(dir, name, signature, Tile.CHIP_RIGHT, null, row);
    }

    /** As above, with Chip's own tile, and the floor he starts on (null for plain floor). */
    private static File setWith(Path dir, String name, int signature, Tile chip, Tile under, Tile... row)
            throws Exception {
        DatBuilder b = new DatBuilder().signature(signature);
        DatBuilder.Level lv = b.level().title(name).password("ABCD").tile(3, 5, chip);
        if (under != null) lv.under(3, 5, under);
        for (int i = 0; i < row.length; i++) lv.tile(4 + i, 5, row[i]);
        lv.end();
        return b.writeTo(dir, name + ".dat").toFile();
    }

    private static Level parse(File dat, Ruleset rules) throws Exception {
        return new io.DatParser(dat).parseLevel(1, 0, Step.EVEN, rules, Direction.UP);
    }

    private static final char[] EVERY_KEY = { 'u', 'd', 'l', 'r', '-',
            SuperCC.UP_LEFT, SuperCC.UP_RIGHT, SuperCC.DOWN_LEFT, SuperCC.DOWN_RIGHT };

    private static Direction dir(char c) {
        switch (c) {
            case 'u': return Direction.UP;
            case 'd': return Direction.DOWN;
            case 'l': return Direction.LEFT;
            case 'r': return Direction.RIGHT;
            case SuperCC.UP_LEFT:    return Direction.UP_LEFT;
            case SuperCC.UP_RIGHT:   return Direction.UP_RIGHT;
            case SuperCC.DOWN_LEFT:  return Direction.DOWN_LEFT;
            case SuperCC.DOWN_RIGHT: return Direction.DOWN_RIGHT;
            default:  return Direction.NONE;
        }
    }

    /**
     * THE SAFETY PROPERTY behind recording a wait: on every tick the engine reports DISCARDED,
     * each of the nine keys, played from the same state, leaves the same state (the whole
     * savestate) and the same flags. If one did not, the key mattered, and a saved wait would
     * replay differently. Plays `keys` one tick each and returns a description of the first
     * violation, or "ok" with how many discarded ticks were checked.
     */
    private static String inert(Level lv, String keys) {
        int checked = 0;
        for (char c : keys.toCharArray()) {
            if (lv.getChip().isDead() || lv.isCompleted()) break;
            byte[] before = lv.save();
            int f = lv.tick(c, new Direction[]{ dir(c) });
            byte[] after = lv.save();
            if ((f & DISCARD) == 0) continue;
            checked++;
            for (char k : EVERY_KEY) {
                lv.load(before);
                int fk = lv.tick(k, new Direction[]{ dir(k) });
                if (fk != f || !java.util.Arrays.equals(lv.save(), after))
                    return "key '" + k + "' in place of '" + c + "' changed the outcome of tick " + lv.getTickNumber();
            }
            lv.load(before);                          // leave the level exactly as the real key did
            lv.tick(c, new Direction[]{ dir(c) });
        }
        return "ok: " + checked + " discarded ticks";
    }

    /** Level.tick once per key, no automatic ticks: the flag each tick returned. */
    private static List<Integer> flags(Level lv, String keys) {
        List<Integer> out = new ArrayList<>();
        for (char c : keys.toCharArray()) out.add(lv.tick(c, new Direction[]{ dir(c) }));
        return out;
    }

    private static String bits(List<Integer> f) {
        StringBuilder sb = new StringBuilder();
        for (int x : f) sb.append(x == 0 ? '0' : x == MULTI ? 'M' : x == DISCARD ? 'D' : '!');
        return sb.toString();
    }

    private static SuperCC load(File dat, Ruleset rules) {
        SuperCC emulator = new SuperCC(false);
        emulator.openLevelset(dat);
        emulator.loadLevel(1, 0, Step.EVEN, false, rules, Direction.UP);
        return emulator;
    }

    /** Plays the keys as SuperCC plays a loaded route (PRELOADING: recorded, multi-ticked). */
    private static String play(SuperCC emulator, String keys) {
        for (char c : keys.toCharArray()) emulator.tick(c, TickFlags.PRELOADING);
        return new String(emulator.getSavestates().getMoveList().toArray());
    }

    /** Saves the move list as SuperCC's menu does, reloads it, and replays it. */
    private static SuperCC reloadAndReplay(SuperCC emulator, String recorded) {
        Level lv = emulator.getLevel();
        Solution s = new Solution(recorded.toCharArray(), lv.getRngSeed(), lv.getStep(),
                                  Solution.SUCC_MOVES, lv.getRuleset(), lv.getInitialRFFDirection());
        s = Solution.fromJSON(s.toJSON().toJSONString());
        s.load(emulator);
        return emulator;
    }

    private static String chipAt(SuperCC e) {
        return chipAt(e.getLevel());
    }

    private static String chipAt(Level lv) {
        Position p = lv.getChip().getPosition();
        return p.getX() + "," + p.getY();
    }

    private static void body() throws Exception {
        Path dir = Harness.tempDir("scc-discard-");

        /* ================================================================
         * 1. Lynx, Level.tick: the flags, tick by tick
         * ================================================================
         * Force floors at 4,5 and 5,5; floor beyond. Chip walks right, then UP is held throughout.
         *   t1 r  chosen on floor; onto a force floor, so no automatic ticks       -> 0
         *   t2 u  between tiles (a force floor takes 2 ticks)                      -> D
         *   t3 u  on 4,5 with no slide token: forced                               -> D
         *   t4 u  between tiles                                                    -> D
         *   t5 u  on 5,5 after a forced slide: the token lets Chip step off        -> 0
         *   t6-8  between tiles (onto plain floor: 4 ticks)                        -> D D D
         *   t9 u  on floor at 5,4: an ordinary move                                -> M  */
        Harness.section("1. Lynx flags");
        File ffLynx = set(dir, "FFLynx", DatBuilder.SIG_LYNX, Tile.FF_RIGHT, Tile.FF_RIGHT);
        Level ff = parse(ffLynx, Ruleset.LYNX);
        List<Integer> ffFlags = flags(ff, "ruuuuuuuu");
        Harness.eq("force floors: the flags of nine ticks", bits(ffFlags), "0DDD0DDDM");
        /* (A Lynx move updates Chip's position on its FIRST tick, as Tile World does, so after t9
         * he is already on 5,3.) */
        Harness.eq("the kept UP took Chip off the force floor, and t9 started the next square",
                   chipAt(ff), "5,3");
        Harness.check("no tick returned both bits", !ffFlags.contains(MULTI | DISCARD));

        /* Ice at 4,5. Without skates the ice holds Chip and the key on arrival is dropped; with
         * skates the very same key on the very same tile is a move (getforcedmove: Boots_Ice). */
        File iceLynx = set(dir, "IceLynx", DatBuilder.SIG_LYNX, Tile.ICE);
        Level ice = parse(iceLynx, Ruleset.LYNX);
        List<Integer> iceFlags = flags(ice, "ruuuuuu");
        Harness.eq("ice, no skates: on, between, forced on the ice, 3 between, then a move",
                   bits(iceFlags), "0DDDDDM");
        Level skates = parse(iceLynx, Ruleset.LYNX);
        skates.getBoots()[2] = 1;
        Harness.eq("ice with skates: the first move is an ordinary one", bits(flags(skates, "r")), "M");
        for (int i = 0; i < 3; i++) skates.tick('R', new Direction[]{ Direction.NONE });
        Harness.eq("and the key on the ice is taken, not dropped", bits(flags(skates, "u")), "M");
        Harness.check("no tick returned both bits", !iceFlags.contains(MULTI | DISCARD));

        /* Plain floor: nothing is discarded. A wait standing still is not a discarded key. */
        File floor = set(dir, "Floor", DatBuilder.SIG_LYNX);
        Harness.eq("floor: wait, then a move", bits(flags(parse(floor, Ruleset.LYNX), "-r")), "0M");

        /* A key that BUMPS is used, never discarded. choosechipmove accepts it (lastmove is set, so
         * Tile World records it) before canmakemove refuses; the refused move still turns Chip. */
        File wall = setWith(dir, "Wall", DatBuilder.SIG_LYNX, Tile.CHIP_UP, null, Tile.WALL);
        Level bump = parse(wall, Ruleset.LYNX);
        Harness.eq("bumping a wall: kept, no move", bits(flags(bump, "r")), "0");
        Harness.eq("and Chip turned to face it", bump.getChip().getDirection(), Direction.RIGHT);
        /* A diagonal into a wall along Chip's facing takes the other half (lxlogic.c:974-977). */
        Level diag = parse(set(dir, "WallDiag", DatBuilder.SIG_LYNX, Tile.WALL), Ruleset.LYNX);
        Harness.eq("a diagonal blocked on one half: the other half is a full move",
                   bits(flags(diag, String.valueOf(SuperCC.UP_RIGHT))), "M");
        Harness.eq("and Chip went up", chipAt(diag), "3,4");

        /* A teleport: the tick after Chip comes out is forced (getforcedmove: CS_TELEPORTED).
         * Teleports at 4,5 and 10,5; entering 4,5 sends Chip to 10,5, which he leaves rightward.
         *   t1 r onto the teleport (an ordinary move, not a slide)   -> M
         *   t2-4 between tiles; he is teleported as t4 ends          -> D D D
         *   t5 on 10,5, just teleported: forced                      -> D
         *   t6-8 between tiles, onto plain floor                     -> D D D
         *   t9 on 11,5, free                                         -> M */
        Tile[] tp = new Tile[7];
        java.util.Arrays.fill(tp, Tile.FLOOR);
        tp[0] = Tile.TELEPORT;
        tp[6] = Tile.TELEPORT;
        File teleport = set(dir, "Teleport", DatBuilder.SIG_LYNX, tp);
        Level tele = parse(teleport, Ruleset.LYNX);
        Harness.eq("teleport: the tick after it is forced", bits(flags(tele, "ruuuuuuuu")), "MDDDDDDDM");
        Harness.eq("Chip came out at 10,5 and went on to 11,5, then up",
                   chipAt(tele), "11,4");

        /* The exit: the key on the tick Chip arrives is not used -- he was between tiles when it
         * was read, and Tile World never chooses for him again. */
        Level exit = parse(set(dir, "Exit", DatBuilder.SIG_LYNX, Tile.EXIT), Ruleset.LYNX);
        Harness.eq("exit: a move, then between tiles to the end", bits(flags(exit, "ruuu")), "MDDD");
        Harness.check("and the last of those ticks won the level", exit.isCompleted());

        /* Tick 1 is never forced (getforcedmove: `if (currenttime() == 0) return FALSE`): Chip
         * starting ON a force floor may step off it with the very first key. */
        File startFF = setWith(dir, "StartFF", DatBuilder.SIG_LYNX, Tile.CHIP_RIGHT, Tile.FF_RIGHT);
        Level first = parse(startFF, Ruleset.LYNX);
        Harness.eq("starting on a force floor: the first key is kept", bits(flags(first, "u")), "0");
        Harness.eq("and moved Chip up", chipAt(first), "3,4");
        Level second = parse(startFF, Ruleset.LYNX);
        Harness.eq("waiting instead, the force floor takes him on tick 2", bits(flags(second, "-u")), "0D");

        /* The property every discard above relies on, checked on all of these routes: on every
         * discarded tick, all nine keys from the same state give the same state and flags. */
        Harness.eq("inert: force floors", inert(parse(ffLynx, Ruleset.LYNX), "ruuuuuuuuuuuu"), "ok: 9 discarded ticks");
        /* (Ice: across to 5,5, then LEFT takes Chip back onto the ice, which slides him home.) */
        Harness.eq("inert: ice", inert(parse(iceLynx, Ruleset.LYNX), "rlllllllll"), "ok: 8 discarded ticks");
        Harness.eq("inert: teleport", inert(parse(teleport, Ruleset.LYNX), "rdddddddddd"), "ok: 9 discarded ticks");
        Harness.eq("inert: exit", inert(parse(set(dir, "Exit2", DatBuilder.SIG_LYNX, Tile.EXIT), Ruleset.LYNX), "rddd"),
                   "ok: 3 discarded ticks");
        Harness.eq("inert: start on a force floor", inert(parse(startFF, Ruleset.LYNX), "-dddddd"),
                   "ok: 5 discarded ticks");

        /* ================================================================
         * 2. Lynx, SuperCC.tick: what the move list records
         * ================================================================
         * The same keys through the real recording path. A discarded key is a '-'; a kept key is
         * itself; a move with automatic ticks is a capital. The kept keys are exactly the moves
         * Tile World would record (play.c: lastmove). */
        Harness.section("2. Lynx move list");
        SuperCC eFF = load(ffLynx, Ruleset.LYNX);
        Harness.eq("force floors: only the kept keys survive", play(eFF, "ruuuuuuuu"), "r---u---U");
        Harness.eq("Chip ends 5,3", chipAt(eFF), "5,3");
        Harness.eq("after 9 recorded ticks and 3 automatic ones", eFF.getLevel().getTickNumber(), 12);

        SuperCC eIce = load(iceLynx, Ruleset.LYNX);
        Harness.eq("ice, no skates", play(eIce, "ruuuuuu"), "r-----U");
        SuperCC eSkates = load(iceLynx, Ruleset.LYNX);
        eSkates.getLevel().getBoots()[2] = 1;
        Harness.eq("ice with skates: both keys kept, both full moves", play(eSkates, "ru"), "RU");
        Harness.eq("and Chip went up off the ice", chipAt(eSkates), "4,4");

        /* ================================================================
         * 3. Lynx: the waits change nothing
         * ================================================================
         * The recorded list, saved and reloaded the way SuperCC's menu does it, replays to the
         * same square on the same tick as the keys that were typed. */
        Harness.section("3. Lynx round trip");
        SuperCC typed = load(ffLynx, Ruleset.LYNX);
        play(typed, "ruuuuuuuu");
        String typedEnd = chipAt(typed) + " t" + typed.getLevel().getTickNumber();
        SuperCC replayed = reloadAndReplay(load(ffLynx, Ruleset.LYNX), "r---u---U");
        Harness.eq("the recorded list ends where the typed keys did",
                   chipAt(replayed) + " t" + replayed.getLevel().getTickNumber(), typedEnd);
        Harness.eq("and records itself unchanged",
                   new String(replayed.getSavestates().getMoveList().toArray()), "r---u---U");

        /* The trade-off, pinned: the list records what HAPPENED, not what was TYPED. A macro or a
         * copy of the move list is a slice of it, so played from a square where those keys would
         * have counted, it waits where the typed keys moved. Here: the same keys on plain floor. */
        SuperCC typedOnFloor = load(floor, Ruleset.LYNX);
        play(typedOnFloor, "ruuuuuuuu");
        SuperCC listOnFloor = load(floor, Ruleset.LYNX);
        play(listOnFloor, "r---u---U".toLowerCase());    // as SavestateManager.playMacro: lowerCase(c)
        Harness.check("replayed elsewhere, the recorded list is NOT the typed keys: "
                      + chipAt(listOnFloor) + " vs " + chipAt(typedOnFloor),
                      !chipAt(listOnFloor).equals(chipAt(typedOnFloor)));

        /* ================================================================
         * 4. MS never discards (98366f7)
         * ================================================================
         * b997c27 had MSLevel return the discard bit on every tick without automatic ticks, which
         * includes a key pressed while Chip slides on a force floor. In MS that key is an
         * OVERRIDE: it moves Chip off the floor (mslogic.c only discards on CS_SLIP, i.e. ice --
         * `choosechipmove(cr, cr->state & CS_SLIP)`). Recording it as a wait would lose the move,
         * so 98366f7 took the MS discard out. MS keeps recording keys exactly as jc-14 did.
         * Not even an MS key on ice may become a wait, though the move it asks for is refused
         * there: the key still resets MSLevel's idle-move count and voluntary-move state (and
         * would cancel a pending click), so it is engine state, not noise. Pinned below. */
        Harness.section("4. MS keeps every key");
        File ffMs = set(dir, "FFMs", DatBuilder.SIG_MS, Tile.FF_RIGHT, Tile.FF_RIGHT, Tile.FF_RIGHT,
                        Tile.FF_RIGHT, Tile.FF_RIGHT);
        File iceMs = set(dir, "IceMs", DatBuilder.SIG_MS, Tile.ICE, Tile.ICE, Tile.ICE);
        String msKeys = "ruuuuuuuu";
        for (File f : new File[]{ ffMs, iceMs }) {
            Level ms = parse(f, Ruleset.MS);
            List<Integer> fl = flags(ms, msKeys + "-r-u");     // odd ticks included: half moves
            Harness.check(f.getName() + ": MS never returns the discard bit",
                          fl.stream().noneMatch(x -> (x & DISCARD) != 0));
        }
        SuperCC eMs = load(ffMs, Ruleset.MS);
        String msRecorded = play(eMs, msKeys);
        Harness.check("MS move list: no typed key became a wait, " + msRecorded,
                      msRecorded.indexOf('-') < 0);
        String msEnd = chipAt(eMs);
        Harness.check("an UP pressed on the force floors took Chip off them, at " + msEnd,
                      eMs.getLevel().getChip().getPosition().getY() < 5);
        Harness.eq("the recorded list replays to the same square",
                   chipAt(reloadAndReplay(load(ffMs, Ruleset.MS), msRecorded)), msEnd);
        /* The override alone, as a wait -- the lower-case u, typed while Chip slid. (b997c27 would
         * also have turned the r that stepped onto the force floor into a wait; leaving it in
         * isolates the override.) */
        Harness.eq("the override key was recorded as typed", msRecorded.chars().filter(ch -> ch == 'u').count(), 1L);
        String noOverride = msRecorded.replace('u', '-');
        String noOverrideEnd = chipAt(reloadAndReplay(load(ffMs, Ruleset.MS), noOverride));
        Harness.check("with just that key as a wait, Chip rides the floors on and ends elsewhere: "
                      + noOverrideEnd + " vs " + msEnd, !noOverrideEnd.equals(msEnd));

        /* An MS key on ice: the move is refused, yet the key is state. */
        /* Compared mid-slide, on a long stretch of ice: a key on the tick Chip slides OFF the ice
         * is used (the slide runs first, then the key), so the comparison must stop before that. */
        File iceLong = set(dir, "IceMsLong", DatBuilder.SIG_MS, Tile.ICE, Tile.ICE, Tile.ICE, Tile.ICE,
                           Tile.ICE, Tile.ICE, Tile.ICE, Tile.ICE);
        Level iceKeys = parse(iceLong, Ruleset.MS), iceWaits = parse(iceLong, Ruleset.MS);
        flags(iceKeys, "ruuu");
        flags(iceWaits, "r---");
        Harness.check("both still sliding on the ice", iceKeys.getChip().isSliding() && iceWaits.getChip().isSliding());
        Harness.eq("mid-slide on MS ice, keys and waits leave Chip on the same square",
                   chipAt(iceKeys),
                   chipAt(iceWaits));
        Harness.check("but not in the same state -- so an MS ice key must stay recorded",
                      !java.util.Arrays.equals(iceKeys.save(), iceWaits.save()));

        /* ================================================================
         * 5. The recorder, if an engine ever returns BOTH bits
         * ================================================================
         * No engine path does today, so this wraps a real Lynx level in one that does: every tick
         * reported discarded ALSO claims automatic ticks. The automatic-tick bit only comes from a
         * direction Chip chose from the key, so SuperCC.tick keeps the key (a capital) rather than
         * write '-' for a tick on which the engine then ran the automatic ones. Upstream's line wrote
         * '-' there; restored, it fails here -- and FORK.md measures the cost: a route replaying 3
         * ticks short. */
        Harness.section("5. both bits");
        SuperCC eBoth = load(ffLynx, Ruleset.LYNX);
        Level real = eBoth.getLevel();
        Level bothBits = (Level) java.lang.reflect.Proxy.newProxyInstance(Level.class.getClassLoader(),
                new Class<?>[]{ Level.class }, (proxy, method, args) -> {
                    Object r;
                    try {
                        r = method.invoke(real, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                    if (method.getName().equals("tick") && r instanceof Integer && ((Integer) r & DISCARD) != 0)
                        r = (Integer) r | MULTI;
                    return r;
                });
        java.lang.reflect.Field levelField = SuperCC.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(eBoth, bothBits);
        String recordedBoth = play(eBoth, "ruuuuuuuu");
        Harness.eq("one entry per key", recordedBoth.length(), 9);
        Harness.check("every key was kept, none written as a wait: " + recordedBoth,
                      recordedBoth.indexOf('-') < 0 && recordedBoth.indexOf('_') < 0);
    }
}
