package com.mojang.minecraft.level.tile;
public class Leaves extends Tile {
    protected Leaves(int id) {
        super(id);
        this.tex = 22;
    }
    public boolean blocksLight() {
        return false;
    }
    public boolean isSolid() {
        return false;
    }
}
