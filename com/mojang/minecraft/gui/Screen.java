package com.mojang.minecraft.gui;
import com.mojang.minecraft.Minecraft;
import com.mojang.minecraft.renderer.Tesselator;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
public class Screen {
    protected Minecraft minecraft;
    public int width;
    public int height;
    protected List<Button> buttons = new ArrayList<Button>();
    public void init(Minecraft minecraft, int width, int height) {
        this.minecraft = minecraft;
        this.width = width;
        this.height = height;
    }
    public void render(int mouseX, int mouseY) {
        this.fillGradient(0, 0, this.width, this.height, 1610941696, -1607454624);
        for (int i = 0; i < this.buttons.size(); ++i) {
            Button b = this.buttons.get(i);
            if (!b.visible) continue;
            this.drawButton(b, mouseX, mouseY);
        }
    }
    protected void drawButton(Button b, int mouseX, int mouseY) {
        boolean hovered = b.contains(mouseX, mouseY);
        int bg = hovered ? -8355712 : -10921639;
        int border = -16777216;
        this.fillRect(b.x - 1, b.y - 1, b.x + b.w + 1, b.y + b.h + 1, border);
        this.fillRect(b.x, b.y, b.x + b.w, b.y + b.h, bg);
        int color = hovered ? 16777120 : 14737632;
        int tw = this.minecraft.font.width(b.text);
        this.minecraft.font.drawShadow(b.text, b.x + b.w / 2 - tw / 2, b.y + (b.h - 8) / 2, color);
    }
    protected void fillRect(int x0, int y0, int x1, int y1, int color) {
        float a = (float)(color >>> 24) / 255.0F;
        float r = (float)(color >> 16 & 255) / 255.0F;
        float g = (float)(color >> 8 & 255) / 255.0F;
        float b = (float)(color & 255) / 255.0F;
        Tesselator t = Tesselator.instance;
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(r, g, b, a);
        t.init();
        t.vertex((float)x0, (float)y1, 0.0F);
        t.vertex((float)x1, (float)y1, 0.0F);
        t.vertex((float)x1, (float)y0, 0.0F);
        t.vertex((float)x0, (float)y0, 0.0F);
        t.flush();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
    }
    protected void fillGradient(int x0, int y0, int x1, int y1, int top, int bot) {
        float ta = (float)(top >>> 24) / 255.0F;
        float tr = (float)(top >> 16 & 255) / 255.0F;
        float tg = (float)(top >> 8 & 255) / 255.0F;
        float tb = (float)(top & 255) / 255.0F;
        float ba = (float)(bot >>> 24) / 255.0F;
        float br = (float)(bot >> 16 & 255) / 255.0F;
        float bg = (float)(bot >> 8 & 255) / 255.0F;
        float bb = (float)(bot & 255) / 255.0F;
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glShadeModel(GL11.GL_SMOOTH);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glColor4f(tr, tg, tb, ta); GL11.glVertex3f((float)x1, (float)y0, 0.0F);
        GL11.glColor4f(tr, tg, tb, ta); GL11.glVertex3f((float)x0, (float)y0, 0.0F);
        GL11.glColor4f(br, bg, bb, ba); GL11.glVertex3f((float)x0, (float)y1, 0.0F);
        GL11.glColor4f(br, bg, bb, ba); GL11.glVertex3f((float)x1, (float)y1, 0.0F);
        GL11.glEnd();
        GL11.glShadeModel(GL11.GL_FLAT);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }
    public void mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) return;
        for (int i = 0; i < this.buttons.size(); ++i) {
            Button b = this.buttons.get(i);
            if (b.contains(mouseX, mouseY)) {
                this.buttonClicked(b);
                return;
            }
        }
    }
    protected void buttonClicked(Button b) {
    }
    public void keyPressed(char ch, int key) {
        if (key == 1) {
            this.minecraft.setScreen(null);
        }
    }
    public void tick() {
    }
}
