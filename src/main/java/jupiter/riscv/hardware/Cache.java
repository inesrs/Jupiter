/*
Copyright (C) 2018-2019 Andres Castellanos

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>
*/

package jupiter.riscv.hardware;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;

import jupiter.Flags;
import jupiter.utils.Data;
import jupiter.utils.IO;


/** Represents a processor's main cache. */
public final class Cache {

  /** replacement policies */
  public static enum ReplacePolicy { LRU, FIFO, RAND };

  /** random number generator */
  private static final Random RNG = new Random();

  /** hit indexes */
  private static final HashSet<Integer> HIT = new HashSet<>();
  /** miss indexes */
  private static final HashSet<Integer> MISS = new HashSet<>();

  /** Represents a cache block. */
  private static final class Block {

    /** indicates if the block is valid */
    private boolean valid;
    /** block tag */
    private int tag;
    /** used times */
    private long age;

    /** Creates a new cache block. */
    private Block() {
      valid = false;
      tag = -1;
      age = 0;
    }

    /** Clones cache block. */
    private Block copy() {
      Block block = new Block();
      block.valid = valid;
      block.tag = tag;
      block.age = age;
      return block;
    }

    /** Resets cache block. */
    private void reset() {
      valid = false;
      tag = -1;
      age = 0l;
    }

  };

  /** Represents a cache set */
  private static final class Set {

    /** set index */
    private final int set;
    /** number of blocks in set */
    private final int size;
    /** replacement policy */
    private final ReplacePolicy replacePol;
    /** array of blocks */
    private final ArrayList<Block> blocks;
    /** fifo list */
    private final ArrayList<Integer> fifo;

    /**
     * Creates a new cache set.
     *
     * @param size set size
     */
    private Set(int set, int size, ReplacePolicy replacePol) {
      this.set = set;
      this.size = size;
      this.replacePol = replacePol;
      blocks = new ArrayList<>(size);
      fifo = new ArrayList<>();
      for (int i = 0; i < size; i++) {
        blocks.add(new Block());
      }
    }

    /**
     * Simulates a cache load.
     *
     * @param tag block tag field
     */
    private boolean load(int tag) {
      for (int i = 0; i < size; i++) {
        Block block = blocks.get(i);
        if (block.valid && block.tag == tag) {
          if (replacePol == ReplacePolicy.LRU) {
            block.age++;
          }
          HIT.add(i + set * size);
          return true;
        }
      }
      int index = evict(true);
      update(index, tag);
      MISS.add(index + set * size);
      return false;
    }

    /**
     * Simulates a cache write.
     *
     * @param tag block tag field
     */
    private boolean write(int tag) {
      for (int i = 0; i < size; i++) {
        Block block = blocks.get(i);
        if (block.valid && block.tag == tag) {
          if (replacePol == ReplacePolicy.LRU) {
            block.age++;
          }
          HIT.add(i + set * size);
          return true;
        }
      }
      int index = evict(false);
      MISS.add(index + set * size);
      return false;
    }

    /**
     * Updates a evicted block.
     *
     * @param index block index
     * @param tag new block tag
     */
    private void update(int index, int tag) {
      Block block = blocks.get(index);
      block.tag = tag;
      block.valid = true;
      block.age = 1;
    }

    /**
     * Evicts a cache block.
     *
     * @param read if true called from read, if false called from write
     * @return evicted block index
     */
    private int evict(boolean read) {
      for (int i = 0; i < size; i++) {
        Block block = blocks.get(i);
        if (!block.valid) {
          if (replacePol == ReplacePolicy.FIFO) {
            fifo.add(i);
            fifo.trimToSize();
          }
          return i;
        }
      }
      // replace
      if (replacePol == ReplacePolicy.LRU) {
        int index = 0;
        Block min = blocks.get(0);
        for (int i = 1; i < size; i++) {
          Block block = blocks.get(i);
          if (block.age < min.age) {
            min = block;
            index = i;
          }
        }
        return index;
      } else if (replacePol == ReplacePolicy.FIFO) {
        int index = fifo.get(0);
        if (read) {
          fifo.remove(0);
          fifo.add(index);
        }
        return index;
      } else {
        if (fifo.size() == 0) {
          fifo.add(RNG.nextInt(size));
        }
        int index = fifo.get(0);
        if (read) {
          fifo.remove(0);
        }
        return index;
      }
    }

    /** Clones cache set. */
    public Set copy() {
      Set set = new Set(this.set, size, replacePol);
      for (int i = 0; i < size; i++) {
        set.blocks.set(i, blocks.get(i).copy());
      }
      for (int i = 0; i < fifo.size(); i++) {
        set.fifo.add(fifo.get(i));
      }
      return set;
    }

    /** Resets cache set. */
    private void reset() {
      fifo.clear();
      for (Block block : blocks) {
        block.reset();
      }
    }

  };

  /** Cache set backup. */
  public static final class CacheBackup {

    /** hits backup */
    private final int hits;
    /** accesses backup */
    private final int accesses;
    /** cache address */
    private final int address;
    /** Set backup */
    private final HashMap<Integer, Set> cache;
    /** blocks state */
    private final ArrayList<String> state;

    /**
     * Creates a new cache backup.
     *
     * @param hits hits count
     * @param accesses cache global accesses
     */
    private CacheBackup(int hits, int accesses, int address, HashMap<Integer, Set> cache, ArrayList<String> state) {
      this.hits = hits;
      this.accesses = accesses;
      this.address = address;
      this.cache = new HashMap<>();
      this.state = new ArrayList<>();
      for (int i = 0; i < state.size(); i++) {
        this.state.add(state.get(i));
      }
      for (Integer key : cache.keySet()) {
        this.cache.put(key, cache.get(key).copy());
      }
    }

  };

  /** cache associativity */
  public int associativity;
  /** cache size */
  private int cacheSize;
  /** block size */
  private int blockSize;
  /** number of blocks */
  private int numBlocks;
  /** replace policy */
  private ReplacePolicy replacePol;

  /** tag srl shift amount */
  private int tagShamt;
  /** index srl shift amount */
  private int indexShamt;

  /** accesses count */
  private int accesses;
  /** hits count */
  private int hits;

  /** property change support */
  private final PropertyChangeSupport pcs;

  /** cache data */
  private HashMap<Integer, Set> cache;
  /** blocks state */
  private ArrayList<String> state;
  /** cache diff */
  private CacheBackup diff;

  /** Creates a new cache simulator. */
  public Cache() {
    pcs = new PropertyChangeSupport(this);
    associativity = Flags.CACHE_ASSOCIATIVITY;
    blockSize = Flags.CACHE_BLOCK_SIZE;
    numBlocks = Flags.CACHE_NUM_BLOCKS;
    cacheSize = blockSize * numBlocks;
    replacePol = Flags.CACHE_REPLACE_POLICY;
    cache = new HashMap<>();
    state = new ArrayList<>();
    diff = null;
    setCache();
  }

  /**
   * Adds a new observer.
   *
   * @param observer observer to add
   */
  public void addObserver(PropertyChangeListener observer) {
    pcs.addPropertyChangeListener(observer);
  }

  /**
   * Removes an observer.
   *
   * @param observer observer to remove
   */
  public void removeObserver(PropertyChangeListener observer) {
    pcs.removePropertyChangeListener(observer);
  }

  /**
   * Sets cache associativity.
   *
   * @param n new cache associativity
   */
  public void setAssociativity(int n)  {
    if (Data.isPowerOf2(n) && n <= numBlocks) {
      associativity = n;
      setCache();
    }
  }

  /**
   * Sets cache block size.
   *
   * @param n new block size
   */
  public void setBlockSize(int n) {
    if (Data.isPowerOf2(n)) {
      blockSize = n;
      cacheSize = blockSize * numBlocks;
      setCache();
    }
  }

  /**
   * Sets number of blocks.
   *
   * @param n new number of blocks
   */
  public void setNumBlocks(int n) {
    if (Data.isPowerOf2(n)) {
      numBlocks = n;
      cacheSize = blockSize * numBlocks;
      setCache();
    }
  }

  /**
   * Sets replace policy.
   *
   * @param policy new replace policy
   */
  public void setReplacePolicy(ReplacePolicy policy) {
    replacePol = policy;
  }

  /** Prints cache stats */
  public void stats() {
    String fmt = "accesses: %d, hits: %d, misses: %d, hit rate: %.2f";
    IO.stdout().println(String.format(fmt, accesses, hits, accesses - hits, (float)hits / accesses));
  }

  /**
   * Loads a byte from cache.
   *
   * @param address memory address
   */
  public void loadByte(int address) {
    if (read(address)) {
      hits++;
    }
    accesses++;
    fireNotification(address);
  }

  /**
   * Loads a half from cache.
   *
   * @param address memory address
   */
  public void loadHalf(int address) {
    boolean byte0 = read(address);
    boolean byte1 = read(address + Data.BYTE_LENGTH);
    if (byte0 && byte1) {
      hits++;
    }
    accesses++;
    fireNotification(address);
  }

  /**
   * Loads a word from cache.
   *
   * @param address memory address
   */
  public void loadWord(int address) {
    boolean byte0 = read(address);
    boolean byte1 = read(address + Data.BYTE_LENGTH);
    boolean byte2 = read(address + 2 * Data.BYTE_LENGTH);
    boolean byte3 = read(address + 3 * Data.BYTE_LENGTH);
    if (byte0 && byte1 && byte2 && byte3) {
      hits++;
    }
    accesses++;
    fireNotification(address);
  }

  /**
   * Stores a byte in cache.
   *
   * @param address memory address
   */
  public void storeByte(int address) {
    if (write(address)) {
      hits++;
    }
    accesses++;
    fireNotification(address);
  }

  /**
   * Stores a half in cache.
   *
   * @param address memory address
   */
  public void storeHalf(int address) {
    boolean byte0 = write(address);
    boolean byte1 = write(address + Data.BYTE_LENGTH);
    if (byte0 && byte1) {
      hits++;
    }
    accesses++;
    fireNotification(address);
  }

  /**
   * Stores a word in cache.
   *
   * @param address memory address
   */
  public void storeWord(int address) {
    boolean byte0 = write(address);
    boolean byte1 = write(address + Data.BYTE_LENGTH);
    boolean byte2 = write(address + 2 * Data.BYTE_LENGTH);
    boolean byte3 = write(address + 3 * Data.BYTE_LENGTH);
    if (byte0 && byte1 && byte2 && byte3) {
      hits++;
    }
    accesses++;
    fireNotification(address);
  }

  /**
   * Restores cache state.
   *
   * @param diff cache diff
   */
  public void restore(CacheBackup diff) {
    if (diff != null) {
      hits = diff.hits;
      accesses = diff.accesses;
      cache = diff.cache;
      state = diff.state;
      for (int i = 0; i < state.size(); i++) {
        pcs.firePropertyChange(state.get(i), String.format("0x%08x", diff.address), i);
      }
    }
  }

  /** Resets cache. */
  public void reset() {
    hits = 0;
    accesses = 0;
    for (Integer t : cache.keySet()) {
      cache.get(t).reset();
    }
    for (int i = 0; i < state.size(); i++) {
      state.set(i, "empty");
    }
    for (int i = 0; i < numBlocks; i++) {
      pcs.firePropertyChange("empty", "reset", i);
    }
  }

  /**
   * Gets the current diff of the cache.
   *
   * @return cache diff
   */
  public CacheBackup getDiff() {
    CacheBackup old = diff;
    diff = null;
    return old;
  }

  /**
   * Verifies if this cache is a direct mapped cache.
   *
   * @return true if the cache is a direct mapped cache, false if not
   */
  public boolean directMap() {
    return associativity == 1;
  }

  /**
   * Verifies if the cache is a fully associative cache.
   *
   * @return true if the cache is a fully associative cache, false if not
   */
  public boolean fullAssociative() {
    return associativity == numBlocks;
  }

  /**
   * Returns cache associativity.
   *
   * @return cache associativity
   */
  public int getAssociativity() {
    return associativity;
  }

  /**
   * Returns cache block size.
   *
   * @return cache block size
   */
  public int getBlockSize() {
    return blockSize;
  }

  /**
   * Returns number of blocks.
   *
   * @return number of blocks
   */
  public int getNumBlocks() {
    return numBlocks;
  }

  /**
   * Returns cache size.
   *
   * @return cache size
   */
  public int getCacheSize() {
    return cacheSize;
  }

  /**
   * Returns the number of cache hits.
   *
   * @return number of cache hits
   */
  public int getHits() {
    return hits;
  }

  /**
   * Returns the number of cache accesses.
   *
   * @return number of cache accesses
   */
  public int getAccesses() {
    return accesses;
  }

  /**
   * Returns the hit rate.
   *
   * @return hit rate
   */
  public float getHitRate() {
    if (accesses != 0) {
      return ((float) hits) / accesses;
    }
    return 0.0f;
  }

  /**
   * Reads a byte from cache.
   *
   * @param address byte address
   */
  private boolean read(int address) {
    int t = getTag(address);
    int i = getIndex(address);
    if (diff == null) {
      diff = new CacheBackup(hits, accesses, address, cache, state);
    }
    return cache.get(i).load(t);
  }

  /**
   * Writes a byte to cache.
   *
   * @param address byte address
   */
  private boolean write(int address) {
    int t = getTag(address);
    int i = getIndex(address);
    if (diff == null) {
      diff = new CacheBackup(hits, accesses, address, cache, state);
    }
    return cache.get(i).write(t);
  }

  /** Notify observers */
  private void fireNotification(int address) {
    for (Integer idx : HIT) {
      if (!MISS.contains(idx)) {
        state.set(idx, "hit");
        pcs.firePropertyChange("hit", String.format("0x%08x", address), idx);
      }
    }
    for (Integer idx: MISS) {
      state.set(idx, "miss");
      pcs.firePropertyChange("miss", String.format("0x%08x", address), idx);
    }
    HIT.clear();
    MISS.clear();
  }

  /** Sets cache organization. */
  private void setCache() {
    // remove cache data
    cache.clear();
    // clear state
    state.clear();
    for (int i = 0; i < numBlocks; i++) {
      state.add("empty");
    }
    // calculate number of blocks per set
    int numBlocksXSet = numBlocks / associativity;
    // calculate number of bits for index
    int indexSize = log2(numBlocksXSet);
    // calculate index srl shift amount
    indexShamt = log2(blockSize);
    // calculate tag srl shift amount
    tagShamt = indexSize + indexShamt;
    // init cache data
    for (int i = 0; i < numBlocksXSet; i++) {
      cache.put(i, new Set(i, associativity, replacePol));
    }
  }

  /**
   * Extracts tag field from the given memory address.
   *
   * @param address memory address
   * @return tag field
   */
  private int getTag(int address) {
    return address >>> tagShamt;
  }

  /**
   * Extracts index field from the given memory address.
   *
   * @param address memory address
   * @return index field
   */
  private int getIndex(int address) {
    return (address >>> indexShamt) & ((numBlocks / associativity) - 1);
  }

  /**
   * Extracts offset field from the given memory address.
   *
   * @param address memory address
   * @return offset field
   */
  private int getOffset(int address) {
    return address & (blockSize - 1);
  }

  /**
   * Calculates log2.
   *
   * @param x log2 argument
   * @return log2 result
   */
  private int log2(int x) {
    return (int) (Math.log(x) / Math.log(2));
  }

}
