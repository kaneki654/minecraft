package com.mojang.minecraft.level.tile;
public class Log extends Tile {
    protected Log(int id) {
        super(id);
        this.tex = 20;
    }
    protected int getTexture(int face) {
        return face == 0 || face == 1 ? 21 : 20;
    }
}
