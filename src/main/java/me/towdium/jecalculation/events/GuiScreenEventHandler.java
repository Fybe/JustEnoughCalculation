package me.towdium.jecalculation.events;

import com.mojang.blaze3d.systems.RenderSystem;
import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecalculation.gui.JecaGui;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.renderer.Rectangle2d;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.client.gui.GuiUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GuiScreenEventHandler implements IGlobalGuiHandler {

    protected GuiScreenOverlayHandler overlayHandler = null;
    protected JecaGui gui = null;
    protected InventorySummary cachedInventory;
    protected RenderTooltipEvent.Pre cachedTooltipEvent;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        ClientPlayerEntity player = Minecraft.getInstance().player;
        Screen screen = event.getGui();
        if (player == null || !(screen instanceof ContainerScreen)) {
            return;
        }

        overlayHandler = new GuiScreenOverlayHandler(player.inventory);
        gui = new JecaGui(null, false, overlayHandler);
        gui.init(Minecraft.getInstance(), screen.width, screen.height);
        overlayHandler.setGui(gui);
    }

    protected boolean isScreenValidForOverlay(Screen screen) {
        return screen instanceof ContainerScreen
            && !(screen instanceof JecaGui);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    @SuppressWarnings("deprecation")
    public void onDrawForeground(GuiScreenEvent.DrawScreenEvent.Post event) {
        Screen screen = event.getGui();
        Minecraft minecraft = Minecraft.getInstance();
        ClientPlayerEntity player = minecraft.player;
        if (player == null || overlayHandler == null || !isScreenValidForOverlay(screen)) {
            return;
        }

        PlayerInventory inventory = player.inventory;
        if (didInventoryChange(inventory)) {
            overlayHandler = new GuiScreenOverlayHandler(inventory);
            gui = new JecaGui(null, false, overlayHandler);
            gui.init(Minecraft.getInstance(), screen.width, screen.height);
            overlayHandler.setGui(gui);
        } else if (screen.width != gui.width || screen.height != gui.height) {
            gui.init(screen.getMinecraft(), screen.width, screen.height);
        }

        gui.setMatrix(event.getMatrixStack());
        int mouseX = gui.getGlobalMouseX();
        int mouseY = gui.getGlobalMouseY();

        RenderSystem.pushMatrix();
        RenderSystem.translatef(gui.getGuiLeft(), gui.getGuiTop(), 0);
        overlayHandler.onDraw(gui, mouseX, mouseY);
        RenderSystem.popMatrix();

        List<String> tooltip = new ArrayList<>();
        overlayHandler.onTooltip(gui, mouseX, mouseY, tooltip);
        gui.drawHoveringText(event.getMatrixStack(), tooltip, mouseX + gui.getGuiLeft(), mouseY + gui.getGuiTop(), minecraft.fontRenderer);
        if (cachedTooltipEvent != null) {
            RenderTooltipEvent.Pre e = cachedTooltipEvent;
            GuiUtils.drawHoveringText(e.getStack(), e.getMatrixStack(), e.getLines(), e.getX(), e.getY(), e.getScreenWidth(), e.getScreenHeight(), e.getMaxWidth(), e.getFontRenderer());
            cachedTooltipEvent = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTooltip(RenderTooltipEvent.Pre event) {
        if (overlayHandler == null || cachedTooltipEvent != null) {
            return;
        }

        boolean overlap = overlayHandler.onTooltip(gui, event.getX() - gui.getGuiLeft(), event.getY() - gui.getGuiTop(), new ArrayList<>());
        if (!overlap) {
            cachedTooltipEvent = event;
        }
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouse(GuiScreenEvent.MouseInputEvent event) {
        Screen screen = event.getGui();
        if (overlayHandler == null || !isScreenValidForOverlay(screen)) {
            return;
        }

        int xMouse = gui.getGlobalMouseX();
        int yMouse = gui.getGlobalMouseY();

        if (event instanceof GuiScreenEvent.MouseScrollEvent.Pre) {
            double diff = ((GuiScreenEvent.MouseScrollEvent) event).getScrollDelta();
            if (diff != 0) {
                overlayHandler.onMouseScroll(gui, xMouse, yMouse, (int) diff);
            }
        } else if (event instanceof GuiScreenEvent.MouseClickedEvent.Pre) {
            int button = ((GuiScreenEvent.MouseClickedEvent) event).getButton();
            overlayHandler.onMouseFocused(gui, xMouse, yMouse, button);
            if (overlayHandler.onMouseClicked(gui, xMouse, yMouse, button)) {
                event.setCanceled(true);
            }
        } else if (event instanceof GuiScreenEvent.MouseDragEvent.Pre) {
            GuiScreenEvent.MouseDragEvent mde = (GuiScreenEvent.MouseDragEvent) event;
            overlayHandler.onMouseDragged(gui, xMouse, yMouse, (int) mde.getDragX(), (int)mde.getDragY());
        } else if (event instanceof GuiScreenEvent.MouseReleasedEvent.Pre) {
            int button = ((GuiScreenEvent.MouseReleasedEvent) event).getButton();
            overlayHandler.onMouseReleased(gui, xMouse, yMouse, button);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onKeyboardKey(GuiScreenEvent.KeyboardKeyEvent event) {
        Screen screen = event.getGui();
        if (overlayHandler == null || !isScreenValidForOverlay(screen)) {
            return;
        }

        if (event instanceof GuiScreenEvent.KeyboardKeyPressedEvent.Pre) {
            if (overlayHandler.onKeyPressed(gui, event.getKeyCode(), event.getModifiers())) {
                event.setCanceled(true);
            }
        } else if (event instanceof GuiScreenEvent.KeyboardKeyReleasedEvent.Pre) {
            if (overlayHandler.onKeyReleased(gui, event.getKeyCode(), event.getModifiers())) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onCharTyped(GuiScreenEvent.KeyboardCharTypedEvent event) {
        Screen screen = event.getGui();
        if (overlayHandler == null || !isScreenValidForOverlay(screen)) {
            return;
        }

        if (overlayHandler.onChar(gui, event.getCodePoint(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    private boolean didInventoryChange(PlayerInventory inventory) {
        if (cachedInventory == null) {
            cachedInventory = new InventorySummary(inventory);
            return false;
        }

        InventorySummary newSummery = new InventorySummary(inventory);
        if (newSummery.equals(cachedInventory)) {
            return false;
        }

        cachedInventory = newSummery;
        return true;
    }

    @Override
    public Collection<Rectangle2d> getGuiExtraAreas() {
        if (overlayHandler != null && gui != null) {
            return overlayHandler.getGuiExtraAreas(gui.getGuiLeft(), gui.getGuiTop());
        }
        return Collections.emptyList();
    }
}