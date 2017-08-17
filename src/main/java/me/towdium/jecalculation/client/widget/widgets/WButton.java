package me.towdium.jecalculation.client.widget.widgets;

import me.towdium.jecalculation.client.gui.JecGui;
import me.towdium.jecalculation.client.widget.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.init.SoundEvents;
import net.minecraftforge.fml.client.config.GuiButtonExt;

/**
 * Author: towdium
 * Date:   8/14/17.
 */
public class WButton extends Widget {
    protected int xPos, yPos, xSize, ySize;
    protected Runnable lsnrLeft, lsnrRight;
    protected String text;
    protected GuiButtonExt button;

    public WButton(int xPos, int yPos, int xSize, int ySize, String text) {
        this.xPos = xPos;
        this.yPos = yPos;
        this.xSize = xSize;
        this.ySize = ySize;
        this.text = text;
    }

    public WButton setListenerLeft(Runnable r) {
        lsnrLeft = r;
        return this;
    }

    public WButton setListenerRight(Runnable r) {
        lsnrRight = r;
        return this;
    }

    @Override
    public void onGuiInit(JecGui gui) {
        super.onGuiInit(gui);
        button = new GuiButtonExt(0, xPos + gui.getGuiLeft(), yPos + gui.getGuiTop(), xSize, ySize, text);
        gui.buttonList.add(button);
    }

    @Override
    public void onClicked(JecGui gui, int xMouse, int yMouse, int button) {
        if (this.button.mousePressed(Minecraft.getMinecraft(), xMouse, yMouse)) {
            if (button == 0 && lsnrLeft != null) {
                lsnrLeft.run();
                Minecraft.getMinecraft().getSoundHandler().playSound(
                        PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            } else if (button == 1 && lsnrRight != null) {
                lsnrLeft.run();
                Minecraft.getMinecraft().getSoundHandler().playSound(
                        PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 0.8F));
            }
        }
    }
}
