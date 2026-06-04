package com.mojang.minecraft;
import com.mojang.minecraft.level.LevelRenderer;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
public class GameSettings {
    public int viewDistance = 0;
    public boolean fog = true;
    public int fov = 70;
    public int mouseSensitivity = 5;
    public boolean smoothLighting = false;
    // Render scale: 0=100%, 1=75%, 2=50%, 3=33%.  Lower = fewer fragments
    // shaded per frame — the single biggest FPS win on software OpenGL
    // (llvmpipe) since fragment processing dominates the cost.
    public int renderScale = 2;
    private File file = new File("options.txt");
    public GameSettings() {
        this.load();
        this.applyViewDistance();
    }
    public void applyViewDistance() {
        LevelRenderer.RENDER_DISTANCE = LevelRenderer.distanceForSetting(this.viewDistance);
        if (LevelRenderer.activeInstance != null) {
            LevelRenderer.activeInstance.invalidateChunkWindow();
        }
    }
    public String getOption(int i) {
        switch (i) {
            case 0:
                String[] names = {"Far", "Normal", "Short", "Tiny"};
                return "View distance: " + names[this.viewDistance];
            case 1:
                return "Fog: " + (this.fog ? "ON" : "OFF");
            case 2:
                return "FOV: " + this.fov;
            case 3:
                return "Mouse sens.: " + this.mouseSensitivity;
            case 4:
                return "Smooth lighting: " + (this.smoothLighting ? "ON" : "OFF");
            case 5:
                String[] scales = {"100%", "75%", "50%", "33%"};
                return "Render scale: " + scales[this.renderScale];
            default:
                return "?";
        }
    }
    public float getRenderScaleFactor() {
        switch (this.renderScale) {
            case 0: return 1.0F;
            case 1: return 0.75F;
            case 2: return 0.5F;
            case 3: return 0.33F;
            default: return 1.0F;
        }
    }
    public void toggle(int i) {
        if (i == 0) { this.viewDistance = (this.viewDistance + 1) % 4; this.applyViewDistance(); }
        if (i == 1) this.fog = !this.fog;
        if (i == 2) { this.fov += 10; if (this.fov > 110) this.fov = 30; }
        if (i == 3) { this.mouseSensitivity++; if (this.mouseSensitivity > 10) this.mouseSensitivity = 1; }
        if (i == 4) this.smoothLighting = !this.smoothLighting;
        if (i == 5) this.renderScale = (this.renderScale + 1) % 4;
        this.save();
    }
    public float getFogDistance() {
        switch (this.viewDistance) {
            case 0: return 0.001F;
            case 1: return 0.003F;
            case 2: return 0.01F;
            case 3: return 0.03F;
            default: return 0.001F;
        }
    }
    public void load() {
        try {
            if (!this.file.exists()) return;
            BufferedReader r = new BufferedReader(new FileReader(this.file));
            String line;
            while ((line = r.readLine()) != null) {
                String[] kv = line.split(":");
                if (kv.length != 2) continue;
                if (kv[0].equals("viewDistance")) this.viewDistance = Integer.parseInt(kv[1]);
                if (kv[0].equals("fog")) this.fog = kv[1].equals("true");
                if (kv[0].equals("fov")) this.fov = Integer.parseInt(kv[1]);
                if (kv[0].equals("mouseSensitivity")) this.mouseSensitivity = Integer.parseInt(kv[1]);
                if (kv[0].equals("smoothLighting")) this.smoothLighting = kv[1].equals("true");
                if (kv[0].equals("renderScale")) this.renderScale = Integer.parseInt(kv[1]);
            }
            r.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void save() {
        try {
            BufferedWriter w = new BufferedWriter(new FileWriter(this.file));
            w.write("viewDistance:" + this.viewDistance); w.newLine();
            w.write("fog:" + this.fog); w.newLine();
            w.write("fov:" + this.fov); w.newLine();
            w.write("mouseSensitivity:" + this.mouseSensitivity); w.newLine();
            w.write("smoothLighting:" + this.smoothLighting); w.newLine();
            w.write("renderScale:" + this.renderScale); w.newLine();
            w.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
