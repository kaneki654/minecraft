package com.mojang.minecraft.level;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
public class Level {
    private static final int TILE_UPDATE_INTERVAL = 400;
    private static final int CHUNK_SIZE = 16;
    public static final int WORLD_BORDER = 30_000_000;
    private final long worldSeed;
    private final PerlinNoise broadNoise;
    private final PerlinNoise detailNoise;
    private final PerlinNoise biomeTemp;
    private final PerlinNoise biomeHumid;
    private final PerlinNoise continNoise;
    public final int width;
    public final int height;
    public final int depth;
    private HashMap chunks = new HashMap();
    private ArrayList levelListeners = new ArrayList();
    private Random random;
    int unprocessed = 0;
    // Bounded LRU-ish cache of surface heights (key packs world x,z into a long).
    // Avoids re-running 5 Perlin samples for every (x,z) that generateChunk +
    // populateTrees both want.
    private HashMap surfaceCache = new HashMap();
    private static final int SURFACE_CACHE_LIMIT = 65536;

    public Level(int w, int h, int d, long seed) {
        this.width = w;
        this.height = h;
        this.depth = d;
        this.worldSeed = seed;
        this.broadNoise = new PerlinNoise(seed ^ 0xDEADBEEFCAFEBABEL, 4, 0.50);
        this.detailNoise = new PerlinNoise(seed ^ 0x1234567890ABCDEFL, 3, 0.55);
        this.biomeTemp   = new PerlinNoise(seed ^ 0xFEEDFACEBADC0DEEL, 2, 0.60);
        this.biomeHumid  = new PerlinNoise(seed ^ 0xC0FFEEBABEDEAD11L, 2, 0.60);
        this.continNoise = new PerlinNoise(seed ^ 0xABCDEF01234567L,   3, 0.45);
        this.random = new Random(seed);
        this.load();
    }
    public Level(int w, int h, int d) {
        this(w, h, d, new java.util.Random().nextLong());
    }
    public long getWorldSeed() { return worldSeed; }

    public boolean load() {
        try {
            File f = new File("level.dat");
            if (!f.exists()) return false;
            DataInputStream dis = new DataInputStream(new GZIPInputStream(new FileInputStream(f)));
            int count = dis.readInt();
            for (int i = 0; i < count; ++i) {
                int xChunk = dis.readInt();
                int zChunk = dis.readInt();
                byte[] data = new byte[CHUNK_SIZE * this.depth * CHUNK_SIZE];
                dis.readFully(data);
                this.chunks.put(Long.valueOf(chunkKey(xChunk, zChunk)), data);
            }
            dis.close();
            this.allChanged();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    public void save() {
        try {
            DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(new File("level.dat"))));
            dos.writeInt(this.chunks.size());
            Object[] keys = this.chunks.keySet().toArray();
            for (int i = 0; i < keys.length; ++i) {
                Long key = (Long) keys[i];
                long k = key.longValue();
                int xChunk = (int)(k >> 32);
                int zChunk = (int)k;
                byte[] data = (byte[]) this.chunks.get(key);
                dos.writeInt(xChunk);
                dos.writeInt(zChunk);
                dos.write(data);
            }
            dos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static long chunkKey(int xChunk, int zChunk) {
        return ((long) xChunk << 32) ^ ((long) zChunk & 4294967295L);
    }
    private byte[] getChunk(int xChunk, int zChunk) {
        Long key = Long.valueOf(chunkKey(xChunk, zChunk));
        byte[] chunk = (byte[]) this.chunks.get(key);
        if (chunk == null) {
            chunk = this.generateChunk(xChunk, zChunk);
            this.chunks.put(key, chunk);
        }
        return chunk;
    }
    private byte[] generateChunk(int xChunk, int zChunk) {
        byte[] blocks = new byte[CHUNK_SIZE * this.depth * CHUNK_SIZE];
        int worldX0 = xChunk * CHUNK_SIZE;
        int worldZ0 = zChunk * CHUNK_SIZE;
        for (int z = 0; z < CHUNK_SIZE; ++z) {
            for (int x = 0; x < CHUNK_SIZE; ++x) {
                int wx = worldX0 + x;
                int wz = worldZ0 + z;
                boolean beyondBorder = Math.abs(wx) >= WORLD_BORDER || Math.abs(wz) >= WORLD_BORDER;
                int surface, rock;
                if (beyondBorder) {
                    surface = 1;
                    rock = 1;
                } else {
                    surface = this.getSurfaceHeight(wx, wz);
                    rock = surface - 2 - Math.abs(this.hash(wx, wz, 1) % 3);
                }
                for (int y = 0; y < this.depth; ++y) {
                    int id = 0;
                    if (y == surface) {
                        id = beyondBorder ? Tile.rock.id : Tile.grass.id;
                    } else if (y < surface) {
                        id = Tile.dirt.id;
                    }
                    if (y <= rock) {
                        id = Tile.rock.id;
                    }
                    blocks[(y * CHUNK_SIZE + z) * CHUNK_SIZE + x] = (byte) id;
                }
            }
        }
        this.populateTrees(blocks, xChunk, zChunk);
        return blocks;
    }
    private void populateTrees(byte[] blocks, int xChunk, int zChunk) {
        int worldX0 = xChunk * CHUNK_SIZE;
        int worldZ0 = zChunk * CHUNK_SIZE;
        for (int tx = worldX0 - 2; tx < worldX0 + CHUNK_SIZE + 2; ++tx) {
            for (int tz = worldZ0 - 2; tz < worldZ0 + CHUNK_SIZE + 2; ++tz) {
                if ((this.hash(tx, tz, 2) & 1023) >= 5) continue;
                int base = this.getSurfaceHeight(tx, tz) + 1;
                int trunkH = 4 + Math.abs(this.hash(tx, tz, 3) % 3);
                for (int yy = 0; yy < trunkH; ++yy) {
                    this.setGeneratedTile(blocks, worldX0, worldZ0, tx, base + yy, tz, Tile.log.id);
                }
                for (int yy = base + trunkH - 2; yy <= base + trunkH + 1; ++yy) {
                    int layer = yy - (base + trunkH);
                    int r = layer >= 0 ? 1 : 2;
                    for (int xx = tx - r; xx <= tx + r; ++xx) {
                        for (int zz = tz - r; zz <= tz + r; ++zz) {
                            int dx = xx - tx;
                            int dz = zz - tz;
                            if (Math.abs(dx) == r && Math.abs(dz) == r && ((this.hash(xx, zz, yy) & 1) == 0 || layer >= 0)) {
                                continue;
                            }
                            this.setGeneratedTile(blocks, worldX0, worldZ0, xx, yy, zz, Tile.leaves.id);
                        }
                    }
                }
            }
        }
    }
    private void setGeneratedTile(byte[] blocks, int worldX0, int worldZ0, int x, int y, int z, int id) {
        int lx = x - worldX0;
        int lz = z - worldZ0;
        if (lx >= 0 && lz >= 0 && lx < CHUNK_SIZE && lz < CHUNK_SIZE && y >= 0 && y < this.depth) {
            int index = (y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx;
            if (id == Tile.log.id || blocks[index] == 0) {
                blocks[index] = (byte) id;
            }
        }
    }
    private int getSurfaceHeight(int x, int z) {
        if (Math.abs(x) >= WORLD_BORDER || Math.abs(z) >= WORLD_BORDER) return 1;
        long key = ((long) x << 32) ^ ((long) z & 4294967295L);
        Integer cached = (Integer) this.surfaceCache.get(Long.valueOf(key));
        if (cached != null) return cached.intValue();
        int h = computeSurfaceHeight(x, z);
        if (this.surfaceCache.size() > SURFACE_CACHE_LIMIT) this.surfaceCache.clear();
        this.surfaceCache.put(Long.valueOf(key), Integer.valueOf(h));
        return h;
    }
    private int computeSurfaceHeight(int x, int z) {
        final double BROAD_SCALE = 96.0;
        final double DETAIL_SCALE = 24.0;
        double broad = broadNoise.sample(x / BROAD_SCALE, z / BROAD_SCALE);
        double detail = detailNoise.sample(x / DETAIL_SCALE, z / DETAIL_SCALE);
        final double BIOME_SCALE = 512.0;
        double temp = biomeTemp.sample(x / BIOME_SCALE, z / BIOME_SCALE);
        double humid = biomeHumid.sample(x / BIOME_SCALE, z / BIOME_SCALE);
        final double CONT_SCALE = 256.0;
        double cont = continNoise.sample(x / CONT_SCALE, z / CONT_SCALE);
        double broadAmp, detailAmp;
        if (cont < -0.3) {
            broadAmp = 3.0;
            detailAmp = 1.0;
        } else if (temp < -0.3) {
            broadAmp = 16.0;
            detailAmp = 5.0;
        } else if (temp > 0.3 && humid < 0.0) {
            broadAmp = 4.0;
            detailAmp = 1.5;
        } else {
            broadAmp = 10.0;
            detailAmp = 4.0;
        }
        int base = this.depth / 3;
        int h = (int)(base + broad * broadAmp + detail * detailAmp);
        if (h < 4) h = 4;
        if (h > this.depth - 8) h = this.depth - 8;
        return h;
    }
    private int hash(int x, int z, int salt) {
        long h = worldSeed;
        h ^= (long) x * 341873128712L;
        h ^= (long) z * 132897987541L;
        h ^= (long) salt * 42317861L;
        h = h ^ h >>> 33;
        h *= -49064778989728563L;
        h = h ^ h >>> 33;
        h *= -4265267296055464877L;
        h = h ^ h >>> 33;
        return (int) h;
    }
    private void setRawTile(int x, int y, int z, int type) {
        if (y >= 0 && y < this.depth) {
            int xChunk = Math.floorDiv(x, CHUNK_SIZE);
            int zChunk = Math.floorDiv(z, CHUNK_SIZE);
            int lx = Math.floorMod(x, CHUNK_SIZE);
            int lz = Math.floorMod(z, CHUNK_SIZE);
            byte[] chunk = this.getChunk(xChunk, zChunk);
            chunk[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] = (byte) type;
        }
    }
    public void calcLightDepths(int x0, int z0, int x1, int z1) {
        for (int x = x0; x < x0 + x1; ++x) {
            for (int z = z0; z < z0 + z1; ++z) {
                int y = this.getLightDepth(x, z);
                for (int i = 0; i < this.levelListeners.size(); ++i) {
                    ((LevelListener) this.levelListeners.get(i)).lightColumnChanged(x, z, y, this.depth);
                }
            }
        }
    }
    private int getLightDepth(int x, int z) {
        int y;
        for (y = this.depth - 1; y > 0 && !this.isLightBlocker(x, y, z); --y) {
            ;
        }
        return y;
    }
    public void addListener(LevelListener levelListener) {
        this.levelListeners.add(levelListener);
    }
    public void removeListener(LevelListener levelListener) {
        this.levelListeners.remove(levelListener);
    }
    private void allChanged() {
        for (int i = 0; i < this.levelListeners.size(); ++i) {
            ((LevelListener) this.levelListeners.get(i)).allChanged();
        }
    }
    public boolean isLightBlocker(int x, int y, int z) {
        Tile tile = Tile.tiles[this.getTile(x, y, z)];
        return tile == null ? false : tile.blocksLight();
    }
    public ArrayList getCubes(AABB aABB) {
        ArrayList aABBs = new ArrayList();
        int x0 = (int) Math.floor(aABB.x0);
        int x1 = (int) Math.floor(aABB.x1 + 1.0F);
        int y0 = (int) Math.floor(aABB.y0);
        int y1 = (int) Math.floor(aABB.y1 + 1.0F);
        int z0 = (int) Math.floor(aABB.z0);
        int z1 = (int) Math.floor(aABB.z1 + 1.0F);
        if (y0 < 0) y0 = 0;
        if (y1 > this.depth) y1 = this.depth;
        for (int x = x0; x < x1; ++x) {
            for (int y = y0; y < y1; ++y) {
                for (int z = z0; z < z1; ++z) {
                    Tile tile = Tile.tiles[this.getTile(x, y, z)];
                    if (tile != null) {
                        AABB aabb = tile.getAABB(x, y, z);
                        if (aabb != null) {
                            aABBs.add(aabb);
                        }
                    }
                }
            }
        }
        return aABBs;
    }
    public boolean setTile(int x, int y, int z, int type) {
        if (y >= 0 && y < this.depth) {
            int old = this.getTile(x, y, z);
            if (type == old) {
                return false;
            } else {
                this.setRawTile(x, y, z, type);
                this.calcLightDepths(x, z, 1, 1);
                for (int i = 0; i < this.levelListeners.size(); ++i) {
                    ((LevelListener) this.levelListeners.get(i)).tileChanged(x, y, z);
                }
                return true;
            }
        } else {
            return false;
        }
    }
    public boolean isLit(int x, int y, int z) {
        return y >= this.getLightDepth(x, z);
    }
    public int getTile(int x, int y, int z) {
        if (y < 0 || y >= this.depth) return 0;
        int xChunk = Math.floorDiv(x, CHUNK_SIZE);
        int zChunk = Math.floorDiv(z, CHUNK_SIZE);
        int lx = Math.floorMod(x, CHUNK_SIZE);
        int lz = Math.floorMod(z, CHUNK_SIZE);
        byte[] chunk = this.getChunk(xChunk, zChunk);
        return chunk[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] & 255;
    }
    public boolean isSolidTile(int x, int y, int z) {
        Tile tile = Tile.tiles[this.getTile(x, y, z)];
        return tile == null ? false : tile.isSolid();
    }
    public void tick() {
        if (this.chunks.isEmpty()) return;
        this.unprocessed += this.chunks.size() * CHUNK_SIZE * CHUNK_SIZE * this.depth;
        int ticks = this.unprocessed / TILE_UPDATE_INTERVAL;
        this.unprocessed -= ticks * TILE_UPDATE_INTERVAL;
        Object[] keys = this.chunks.keySet().toArray();
        if (keys.length == 0) return;
        for (int i = 0; i < ticks; ++i) {
            Long key = (Long) keys[this.random.nextInt(keys.length)];
            int xChunk = (int)(key.longValue() >> 32);
            int zChunk = (int) key.longValue();
            int x = xChunk * CHUNK_SIZE + this.random.nextInt(CHUNK_SIZE);
            int y = this.random.nextInt(this.depth);
            int z = zChunk * CHUNK_SIZE + this.random.nextInt(CHUNK_SIZE);
            Tile tile = Tile.tiles[this.getTile(x, y, z)];
            if (tile != null) {
                tile.tick(this, x, y, z, this.random);
            }
        }
    }
}
