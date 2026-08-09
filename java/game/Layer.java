package game;

/**
 *
 *
 * Benchmarks:
 *
 * Time taken to run pain, without writing savesates:
 * ByteLayer: 12.42920485ms
 * TileLayer: 12.07655951ms
 *
 * Time taken to run pain, with writing savesates:
 * ByteLayer: 20.73663555ms
 * TileLayer: 26.42774384ms
 *
 */

public interface Layer extends Iterable<Tile> {

    /** MOD (Jeremy): NO bounds check -- an index outside 0..1023 throws. Callers handling data
     *  that may name an off-map cell (e.g. a .dat monster-movement list) must use the
     *  {@link #get(Position)} overload, which is bounds-safe. See LevelFactory.getMSMonsterList. */
    public Tile get(int i);
    
    /** MOD (Jeremy): bounds-SAFE. Returns {@link Tile#WALL} when the position is not on the
     *  32x32 map, so an off-map cell reads as solid and is never mistaken for a creature.
     *  Prefer this overload wherever the index comes from level data. */
    public Tile get(Position p);
    
    public void set(int i, Tile t);
    
    public void set(Position p, Tile t);
    
    public byte[] getBytes();
    
    public Tile[] getTiles();
    
    public void load(byte[] b);
    
}
