package com.mojang.minecraft.gui;
public class Button {
    public int id;
    public int x;
    public int y;
    public int w = 200;
    public int h = 20;
    public String text;
    public boolean visible = true;
    public Button(int id, int x, int y, String text) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.text = text;
    }
    public boolean contains(int mx, int my) {
        return this.visible && mx >= this.x && mx < this.x + this.w && my >= this.y && my < this.y + this.h;
    }
}
