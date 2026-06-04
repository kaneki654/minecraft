package com.mojang.minecraft.level;

import com.mojang.minecraft.HitResult;
import com.mojang.minecraft.Player;
import com.mojang.minecraft.level.tile.Tile;
import com.mojang.minecraft.phys.AABB;
import com.mojang.minecraft.renderer.Frustum;
import com.mojang.minecraft.renderer.Tesselator;
import com.mojang.minecraft.renderer.Textures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import org.lwjgl.opengl.GL11;

public class LevelRenderer implements LevelListener {
   /** Hard cap on chunk mesh rebuilds per frame – keeps the GPU busy but not stalled. */
   public static final int MAX_REBUILDS_PER_FRAME = 4;
   public static final int CHUNK_SIZE = 16;

   /**
    * How many chunks in each direction around the player to keep loaded.
    * 6 → 13×13 = 169 render chunks (roughly 208 block view distance).
    * Increase cautiously: virgl/llvmpipe translates GL display-lists to
    * CPU-side calls, so a higher value burns more CPU time on uploads.
    */
   public static int RENDER_DISTANCE = 6;

   public static int distanceForSetting(int setting) {
      switch (setting) {
         case 0: return 12;  // Far
         case 1: return 8;   // Normal
         case 2: return 5;   // Short
         case 3: return 3;   // Tiny
         default: return 8;
      }
   }

   /**
    * Chunks closer than this distance (in chunk-units) are always rebuilt
    * first when dirty.  Distant chunks are deferred to later frames so the
    * immediate view is always snappy.
    */
   private static final int PRIORITY_REBUILD_DISTANCE = 3;

   private Level level;
   private HashMap chunks = new HashMap();
   private ArrayList chunkList = new ArrayList();
   private int yChunks;
   private Textures textures;

   public static LevelRenderer activeInstance = null;
   private int lastPlayerChunkX = Integer.MIN_VALUE;
   private int lastPlayerChunkZ = Integer.MIN_VALUE;
   public void invalidateChunkWindow() {
      this.lastPlayerChunkX = Integer.MIN_VALUE;
      this.lastPlayerChunkZ = Integer.MIN_VALUE;
   }

   public LevelRenderer(Level level, Textures textures) {
      this.level = level;
      this.textures = textures;
      level.addListener(this);
      // Use TALL vertical slices instead of 16-block ones.  For depth=128 this
      // gives 2 vertical chunks instead of 8 — 4x fewer display lists, 4x fewer
      // frustum tests per frame, 4x fewer dirty rebuilds.  The rebuild cost per
      // chunk is slightly higher but most cells are air/buried so the inner
      // skip-air loop absorbs that easily.
      this.yChunks = level.depth / 64;
      if (this.yChunks < 1) {
         this.yChunks = 1;
      }
      activeInstance = this;
   }

   private static String key(int x, int y, int z) {
      return x + "," + y + "," + z;
   }

   private static int chunkCoord(float coord) {
      return (int)Math.floor((double)coord / 16.0D);
   }

   private static int chunkCoord(int coord) {
      return Math.floorDiv(coord, 16);
   }

   private void updateChunkWindow(Player player) {
      int px = chunkCoord(player.x);
      int pz = chunkCoord(player.z);

      if (px == lastPlayerChunkX && pz == lastPlayerChunkZ) return;
      lastPlayerChunkX = px;
      lastPlayerChunkZ = pz;

      HashSet needed = new HashSet();

      int slice = this.level.depth / this.yChunks;
      for(int x = px - RENDER_DISTANCE; x <= px + RENDER_DISTANCE; ++x) {
         for(int z = pz - RENDER_DISTANCE; z <= pz + RENDER_DISTANCE; ++z) {
            for(int y = 0; y < this.yChunks; ++y) {
               String key = key(x, y, z);
               needed.add(key);
               if (!this.chunks.containsKey(key)) {
                  int x0 = x * 16;
                  int y0 = y * slice;
                  int z0 = z * 16;
                  int y1 = (y + 1) * slice;
                  if (y1 > this.level.depth) {
                     y1 = this.level.depth;
                  }

                  Chunk chunk = new Chunk(this.level, x0, y0, z0, x0 + 16, y1, z0 + 16);
                  this.chunks.put(key, chunk);
                  this.chunkList.add(chunk);
               }
            }
         }
      }

      // Evict (unload) chunks that have moved outside the render window.
      // GL display lists are freed here → memory returned to the GPU driver.
      for(Iterator it = this.chunkList.iterator(); it.hasNext(); ) {
         Chunk chunk = (Chunk)it.next();
         int cx = chunkCoord(chunk.x0);
         int cy = chunk.y0 / slice;
         int cz = chunkCoord(chunk.z0);
         if (!needed.contains(key(cx, cy, cz))) {
            chunk.dispose();   // frees GL display list memory
            it.remove();
            this.chunks.remove(key(cx, cy, cz));
         }
      }
   }

   public List getAllDirtyChunks() {
      ArrayList dirty = null;

      for(int i = 0; i < this.chunkList.size(); ++i) {
         Chunk chunk = (Chunk)this.chunkList.get(i);
         if (chunk.isDirty()) {
            if (dirty == null) {
               dirty = new ArrayList();
            }

            dirty.add(chunk);
         }
      }

      return dirty;
   }

   public void render(Player player, int layer) {
      GL11.glEnable(3553);
      int id = this.textures.loadTexture("/terrain.png", 9728);
      GL11.glBindTexture(3553, id);
      Frustum frustum = Frustum.getFrustum();

      for(int i = 0; i < this.chunkList.size(); ++i) {
         Chunk chunk = (Chunk)this.chunkList.get(i);
         if (frustum.isVisible(chunk.aabb)) {
            chunk.render(layer);
         }
      }

      GL11.glDisable(3553);
   }

   public void updateDirtyChunks(Player player) {
      this.updateChunkWindow(player);
      List dirty = this.getAllDirtyChunks();
      if (dirty != null) {
         // Sort: closest + in-frustum first, then further chunks.
         // DirtyChunkSorter already handles this; it combines player distance
         // with frustum visibility so the most "important" chunks rebuild first.
         Collections.sort(dirty, new DirtyChunkSorter(player, Frustum.getFrustum()));

         int rebuilt = 0;
         Frustum frustum = Frustum.getFrustum();
         for (int i = 0; i < dirty.size() && rebuilt < MAX_REBUILDS_PER_FRAME; ++i) {
            Chunk c = (Chunk) dirty.get(i);
            // Always rebuild in-frustum chunks; defer far out-of-frustum chunks
            // to save GPU upload bandwidth on the virgl path.
            boolean inFrustum = frustum.isVisible(c.aabb);
            float chunkDist   = c.distanceToSqr(player);
            float priorityDistSq = (PRIORITY_REBUILD_DISTANCE * CHUNK_SIZE)
                                 * (PRIORITY_REBUILD_DISTANCE * CHUNK_SIZE);

            if (inFrustum || chunkDist < priorityDistSq) {
               c.rebuild();
               ++rebuilt;
            } else if (rebuilt == 0) {
               // If nothing in-frustum needs a rebuild, allow one background rebuild
               c.rebuild();
               ++rebuilt;
            }
         }
      }
   }

   public void pick(Player player, Frustum frustum) {
      Tesselator t = Tesselator.instance;
      float r = 3.0F;
      AABB box = player.bb.grow(r, r, r);
      int x0 = (int)box.x0;
      int x1 = (int)(box.x1 + 1.0F);
      int y0 = (int)box.y0;
      int y1 = (int)(box.y1 + 1.0F);
      int z0 = (int)box.z0;
      int z1 = (int)(box.z1 + 1.0F);
      GL11.glInitNames();
      GL11.glPushName(0);
      GL11.glPushName(0);

      for(int x = x0; x < x1; ++x) {
         GL11.glLoadName(x);
         GL11.glPushName(0);

         for(int y = y0; y < y1; ++y) {
            GL11.glLoadName(y);
            GL11.glPushName(0);

            for(int z = z0; z < z1; ++z) {
               Tile tile = Tile.tiles[this.level.getTile(x, y, z)];
               if (tile != null && frustum.isVisible(tile.getTileAABB(x, y, z))) {
                  GL11.glLoadName(z);
                  GL11.glPushName(0);

                  for(int i = 0; i < 6; ++i) {
                     GL11.glLoadName(i);
                     t.init();
                     tile.renderFaceNoTexture(t, x, y, z, i);
                     t.flush();
                  }

                  GL11.glPopName();
               }
            }

            GL11.glPopName();
         }

         GL11.glPopName();
      }

      GL11.glPopName();
      GL11.glPopName();
   }

   public void renderHit(HitResult h, int mode, int tileType) {
      Tesselator t = Tesselator.instance;
      GL11.glEnable(3042);
      GL11.glBlendFunc(770, 1);
      GL11.glColor4f(1.0F, 1.0F, 1.0F, ((float)Math.sin((double)System.currentTimeMillis() / 100.0D) * 0.2F + 0.4F) * 0.5F);
      if (mode == 0) {
         t.init();

         for(int i = 0; i < 6; ++i) {
            Tile.rock.renderFaceNoTexture(t, h.x, h.y, h.z, i);
         }

         t.flush();
      } else {
         GL11.glBlendFunc(770, 771);
         float br = (float)Math.sin((double)System.currentTimeMillis() / 100.0D) * 0.2F + 0.8F;
         GL11.glColor4f(br, br, br, (float)Math.sin((double)System.currentTimeMillis() / 200.0D) * 0.2F + 0.5F);
         GL11.glEnable(3553);
         int id = this.textures.loadTexture("/terrain.png", 9728);
         GL11.glBindTexture(3553, id);
         int x = h.x;
         int y = h.y;
         int z = h.z;
         if (h.f == 0) {
            --y;
         }

         if (h.f == 1) {
            ++y;
         }

         if (h.f == 2) {
            --z;
         }

         if (h.f == 3) {
            ++z;
         }

         if (h.f == 4) {
            --x;
         }

         if (h.f == 5) {
            ++x;
         }

         t.init();
         t.noColor();
         Tile.tiles[tileType].render(t, this.level, 0, x, y, z);
         Tile.tiles[tileType].render(t, this.level, 1, x, y, z);
         t.flush();
         GL11.glDisable(3553);
      }

      GL11.glDisable(3042);
   }

   public void setDirty(int x0, int y0, int z0, int x1, int y1, int z1) {
      int slice = this.level.depth / this.yChunks;
      x0 = chunkCoord(x0);
      x1 = chunkCoord(x1);
      y0 = y0 / slice;
      y1 = y1 / slice;
      z0 = chunkCoord(z0);
      z1 = chunkCoord(z1);
      if (y0 < 0) y0 = 0;
      if (y1 >= this.yChunks) y1 = this.yChunks - 1;

      for(int x = x0; x <= x1; ++x) {
         for(int y = y0; y <= y1; ++y) {
            for(int z = z0; z <= z1; ++z) {
               Chunk chunk = (Chunk)this.chunks.get(key(x, y, z));
               if (chunk != null) {
                  chunk.setDirty();
               }
            }
         }
      }

   }

   public void tileChanged(int x, int y, int z) {
      this.setDirty(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1);
   }

   public void lightColumnChanged(int x, int z, int y0, int y1) {
      this.setDirty(x - 1, y0 - 1, z - 1, x + 1, y1 + 1, z + 1);
   }

   public void allChanged() {
      for(int i = 0; i < this.chunkList.size(); ++i) {
         ((Chunk)this.chunkList.get(i)).setDirty();
      }
   }
}
