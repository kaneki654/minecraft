package com.mojang.minecraft.gui;
import com.mojang.minecraft.Minecraft;
public class OptionsScreen extends Screen {
    private Screen parent;
    private String title = "Options";
    public OptionsScreen(Screen parent) {
        this.parent = parent;
    }
    public void init(Minecraft minecraft, int width, int height) {
        super.init(minecraft, width, height);
        this.buttons.clear();
        for (int i = 0; i < 5; ++i) {
            this.buttons.add(new Button(i, this.width / 2 - 100, this.height / 6 + 24 * i, this.minecraft.settings.getOption(i)));
        }
        this.buttons.add(new Button(200, this.width / 2 - 100, this.height / 6 + 168, "Done"));
    }
    public void render(int mouseX, int mouseY) {
        super.render(mouseX, mouseY);
        int tw = this.minecraft.font.width(this.title);
        this.minecraft.font.drawShadow(this.title, this.width / 2 - tw / 2, 20, 16777215);
    }
    protected void buttonClicked(Button b) {
        if (b.id == 200) {
            this.minecraft.setScreen(this.parent);
            return;
        }
        this.minecraft.settings.toggle(b.id);
        b.text = this.minecraft.settings.getOption(b.id);
    }
}
