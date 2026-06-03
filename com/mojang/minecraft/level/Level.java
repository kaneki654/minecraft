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
   private static final long WORLD_SEED = 8675309L;
   public final int width;
   public final int height;
   public final int depth;
   private HashMap chunks = new HashMap();
   private ArrayList levelListeners = new ArrayList();
   private Random random = new Random();
   int unprocessed = 0;

   public Level(int w, int h, int d) {
      this.width = w;
      this.height = h;
      this.depth = d;
      this.load();
   }

   public boolean load() {
      try {
         byte[] blocks = new byte[this.width * this.height * this.depth];
         DataInputStream dis = new DataInputStream(new GZIPInputStream(new FileInputStream(new File("level.dat"))));
         dis.readFully(blocks);
         if (!LevelGen.hasTrees(blocks)) {
            LevelGen.plantTrees(blocks, this.width, this.height, this.depth, this.random);
         }

         this.chunks.clear();

         for(int y = 0; y < this.depth; ++y) {
            for(int z = 0; z < this.height; ++z) {
               for(int x = 0; x < this.width; ++x) {
                  int id = blocks[(y * this.height + z) * this.width + x] & 255;
                  this.setLoadedTile(x, y, z, id);
               }
            }
         }

         dis.close();
         this.allChanged();
         return true;
      } catch (Exception var6) {
         var6.printStackTrace();
         return false;
      }
   }

   public void save() {
      try {
         byte[] blocks = new byte[this.width * this.height * this.depth];

         for(int y = 0; y < this.depth; ++y) {
            for(int z = 0; z < this.height; ++z) {
               for(int x = 0; x < this.width; ++x) {
                  blocks[(y * this.height + z) * this.width + x] = (byte)this.getTile(x, y, z);
               }
            }
         }

         DataOutputStream dos = new DataOutputStream(new GZIPOutputStream(new FileOutputStream(new File("level.dat"))));
         dos.write(blocks);
         dos.close();
      } catch (Exception var5) {
         var5.printStackTrace();
      }

   }

   private static long chunkKey(int xChunk, int zChunk) {
      return ((long)xChunk << 32) ^ (long)zChunk & 4294967295L;
   }

   private byte[] getChunk(int xChunk, int zChunk) {
      Long key = Long.valueOf(chunkKey(xChunk, zChunk));
      byte[] chunk = (byte[])this.chunks.get(key);
      if (chunk == null) {
         chunk = this.generateChunk(xChunk, zChunk);
         this.chunks.put(key, chunk);
      }

      return chunk;
   }

   private byte[] getLoadedChunk(int xChunk, int zChunk) {
      Long key = Long.valueOf(chunkKey(xChunk, zChunk));
      byte[] chunk = (byte[])this.chunks.get(key);
      if (chunk == null) {
         chunk = new byte[CHUNK_SIZE * this.depth * CHUNK_SIZE];
         this.chunks.put(key, chunk);
      }

      return chunk;
   }

   private byte[] generateChunk(int xChunk, int zChunk) {
      byte[] blocks = new byte[CHUNK_SIZE * this.depth * CHUNK_SIZE];
      int worldX0 = xChunk * CHUNK_SIZE;
      int worldZ0 = zChunk * CHUNK_SIZE;

      for(int z = 0; z < CHUNK_SIZE; ++z) {
         for(int x = 0; x < CHUNK_SIZE; ++x) {
            int wx = worldX0 + x;
            int wz = worldZ0 + z;
            int surface = this.getSurfaceHeight(wx, wz);
            int rock = surface - 3 - Math.abs(this.hash(wx, wz, 1) % 3);

            for(int y = 0; y < this.depth; ++y) {
               int id = 0;
               if (y == surface) {
                  id = Tile.grass.id;
               }

               if (y < surface) {
                  id = Tile.dirt.id;
               }

               if (y <= rock) {
                  id = Tile.rock.id;
               }

               blocks[(y * CHUNK_SIZE + z) * CHUNK_SIZE + x] = (byte)id;
            }
         }
      }

      this.populateTrees(blocks, xChunk, zChunk);
      return blocks;
   }

   private void populateTrees(byte[] blocks, int xChunk, int zChunk) {
      int worldX0 = xChunk * CHUNK_SIZE;
      int worldZ0 = zChunk * CHUNK_SIZE;

      for(int tx = worldX0 - 2; tx < worldX0 + CHUNK_SIZE + 2; ++tx) {
         for(int tz = worldZ0 - 2; tz < worldZ0 + CHUNK_SIZE + 2; ++tz) {
            if ((this.hash(tx, tz, 2) & 1023) >= 5) {
               continue;
            }

            int base = this.getSurfaceHeight(tx, tz) + 1;
            int trunkH = 4 + Math.abs(this.hash(tx, tz, 3) % 3);

            for(int yy = 0; yy < trunkH; ++yy) {
               this.setGeneratedTile(blocks, worldX0, worldZ0, tx, base + yy, tz, Tile.log.id);
            }

            for(int yy = base + trunkH - 2; yy <= base + trunkH + 1; ++yy) {
               int layer = yy - (base + trunkH);
               int r = layer >= 0 ? 1 : 2;

               for(int xx = tx - r; xx <= tx + r; ++xx) {
                  for(int zz = tz - r; zz <= tz + r; ++zz) {
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
            blocks[index] = (byte)id;
         }
      }
   }

   private int getSurfaceHeight(int x, int z) {
      double broad = this.noise(x, z, 64) * 10.0D;
      double detail = this.noise(x + 9000, z - 9000, 24) * 5.0D;
      int h = (int)(this.depth / 3 + broad + detail);
      if (h < 4) {
         h = 4;
      }

      if (h > this.depth - 8) {
         h = this.depth - 8;
      }

      return h;
   }

   private double noise(int x, int z, int scale) {
      int x0 = Math.floorDiv(x, scale);
      int z0 = Math.floorDiv(z, scale);
      int x1 = x0 + 1;
      int z1 = z0 + 1;
      double fx = (double)Math.floorMod(x, scale) / (double)scale;
      double fz = (double)Math.floorMod(z, scale) / (double)scale;
      fx = fx * fx * (3.0D - 2.0D * fx);
      fz = fz * fz * (3.0D - 2.0D * fz);
      double a = this.randomValue(x0, z0);
      double b = this.randomValue(x1, z0);
      double c = this.randomValue(x0, z1);
      double d = this.randomValue(x1, z1);
      double ab = a + (b - a) * fx;
      double cd = c + (d - c) * fx;
      return ab + (cd - ab) * fz;
   }

   private double randomValue(int x, int z) {
      return (double)(this.hash(x, z, 0) & 65535) / 32767.5D - 1.0D;
   }

   private int hash(int x, int z, int salt) {
      long h = WORLD_SEED;
      h ^= (long)x * 341873128712L;
      h ^= (long)z * 132897987541L;
      h ^= (long)salt * 42317861L;
      h = h ^ h >> 33;
      h *= -49064778989728563L;
      h = h ^ h >> 33;
      h *= -4265267296055464877L;
      h = h ^ h >> 33;
      return (int)h;
   }

   private void setRawTile(int x, int y, int z, int type) {
      if (y >= 0 && y < this.depth) {
         int xChunk = Math.floorDiv(x, CHUNK_SIZE);
         int zChunk = Math.floorDiv(z, CHUNK_SIZE);
         int lx = Math.floorMod(x, CHUNK_SIZE);
         int lz = Math.floorMod(z, CHUNK_SIZE);
         byte[] chunk = this.getChunk(xChunk, zChunk);
         chunk[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] = (byte)type;
      }
   }

   private void setLoadedTile(int x, int y, int z, int type) {
      if (y >= 0 && y < this.depth) {
         int xChunk = Math.floorDiv(x, CHUNK_SIZE);
         int zChunk = Math.floorDiv(z, CHUNK_SIZE);
         int lx = Math.floorMod(x, CHUNK_SIZE);
         int lz = Math.floorMod(z, CHUNK_SIZE);
         byte[] chunk = this.getLoadedChunk(xChunk, zChunk);
         chunk[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] = (byte)type;
      }
   }

   public void calcLightDepths(int x0, int z0, int x1, int z1) {
      for(int x = x0; x < x0 + x1; ++x) {
         for(int z = z0; z < z0 + z1; ++z) {
            int y = this.getLightDepth(x, z);

            for(int i = 0; i < this.levelListeners.size(); ++i) {
               ((LevelListener)this.levelListeners.get(i)).lightColumnChanged(x, z, y, this.depth);
            }
         }
      }

   }

   private int getLightDepth(int x, int z) {
      int y;
      for(y = this.depth - 1; y > 0 && !this.isLightBlocker(x, y, z); --y) {
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
      for(int i = 0; i < this.levelListeners.size(); ++i) {
         ((LevelListener)this.levelListeners.get(i)).allChanged();
      }

   }

   public boolean isLightBlocker(int x, int y, int z) {
      Tile tile = Tile.tiles[this.getTile(x, y, z)];
      return tile == null ? false : tile.blocksLight();
   }

   public ArrayList getCubes(AABB aABB) {
      ArrayList aABBs = new ArrayList();
      int x0 = (int)aABB.x0;
      int x1 = (int)(aABB.x1 + 1.0F);
      int y0 = (int)aABB.y0;
      int y1 = (int)(aABB.y1 + 1.0F);
      int z0 = (int)aABB.z0;
      int z1 = (int)(aABB.z1 + 1.0F);
      if (y0 < 0) {
         y0 = 0;
      }

      if (y1 > this.depth) {
         y1 = this.depth;
      }

      for(int x = x0; x < x1; ++x) {
         for(int y = y0; y < y1; ++y) {
            for(int z = z0; z < z1; ++z) {
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

            for(int i = 0; i < this.levelListeners.size(); ++i) {
               ((LevelListener)this.levelListeners.get(i)).tileChanged(x, y, z);
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
      if (y < 0 || y >= this.depth) {
         return 0;
      } else {
         int xChunk = Math.floorDiv(x, CHUNK_SIZE);
         int zChunk = Math.floorDiv(z, CHUNK_SIZE);
         int lx = Math.floorMod(x, CHUNK_SIZE);
         int lz = Math.floorMod(z, CHUNK_SIZE);
         byte[] chunk = this.getChunk(xChunk, zChunk);
         return chunk[(y * CHUNK_SIZE + lz) * CHUNK_SIZE + lx] & 255;
      }
   }

   public boolean isSolidTile(int x, int y, int z) {
      Tile tile = Tile.tiles[this.getTile(x, y, z)];
      return tile == null ? false : tile.isSolid();
   }

   public void tick() {
      if (this.chunks.isEmpty()) {
         return;
      }

      this.unprocessed += this.chunks.size() * CHUNK_SIZE * CHUNK_SIZE * this.depth;
      int ticks = this.unprocessed / TILE_UPDATE_INTERVAL;
      this.unprocessed -= ticks * TILE_UPDATE_INTERVAL;
      Object[] keys = this.chunks.keySet().toArray();

      for(int i = 0; i < ticks; ++i) {
         Long key = (Long)keys[this.random.nextInt(keys.length)];
         int xChunk = (int)(key.longValue() >> 32);
         int zChunk = (int)key.longValue();
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
