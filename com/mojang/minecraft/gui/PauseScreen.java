package com.mojang.minecraft.gui;
import com.mojang.minecraft.Minecraft;
public class PauseScreen extends Screen {
    private String title = "Game menu";
    public void init(Minecraft minecraft, int width, int height) {
        super.init(minecraft, width, height);
        this.buttons.clear();
        this.buttons.add(new Button(0, this.width / 2 - 100, this.height / 4 + 24, "Options..."));
        this.buttons.add(new Button(1, this.width / 2 - 100, this.height / 4 + 48, "Save level"));
        this.buttons.add(new Button(2, this.width / 2 - 100, this.height / 4 + 96, "Back to game"));
    }
    public void render(int mouseX, int mouseY) {
        super.render(mouseX, mouseY);
        int tw = this.minecraft.font.width(this.title);
        this.minecraft.font.drawShadow(this.title, this.width / 2 - tw / 2, 40, 16777215);
    }
    protected void buttonClicked(Button b) {
        if (b.id == 0) this.minecraft.setScreen(new OptionsScreen(this));
        if (b.id == 1) { this.minecraft.level.save(); this.minecraft.setScreen(null); }
        if (b.id == 2) this.minecraft.setScreen(null);
    }
}
