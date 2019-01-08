package me.towdium.jecalculation.data;

import mcp.MethodsReturnNonnullByDefault;
import me.towdium.jecalculation.JecaCapability;
import me.towdium.jecalculation.JecaConfig;
import me.towdium.jecalculation.JecaItem;
import me.towdium.jecalculation.JustEnoughCalculation;
import me.towdium.jecalculation.data.label.ILabel;
import me.towdium.jecalculation.data.structure.Recents;
import me.towdium.jecalculation.data.structure.Recipe;
import me.towdium.jecalculation.data.structure.Recipes;
import me.towdium.jecalculation.network.packets.PCalculator;
import me.towdium.jecalculation.network.packets.PRecipe;
import me.towdium.jecalculation.network.packets.PRecord;
import me.towdium.jecalculation.utils.Utilities;
import me.towdium.jecalculation.utils.wrappers.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static me.towdium.jecalculation.JustEnoughCalculation.network;

/**
 * Author: towdium
 * Date:   17-10-15.
 */
@Mod.EventBusSubscriber
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class Controller {
    public static final String KEY_RECIPES = "recipes";
    public static final String KEY_RECENTS = "recents";
    public static final String KEY_AMOUNT = "amount";
    public static final String KEY_INVENTORY = "inventory";
    static Recipes recipesClient;
    static Recents recentsClient;
    static String amountClient;
    static boolean inventoryClient;
    static Recipes recipesServer;

    public static void setRecipesServer(Recipes r) {
        recipesServer = r;
    }

    public static boolean isServerActive() {
        return recipesServer != null;
    }

    static Recipes getRecord() {
        if (isServerActive()) return recipesServer;
        else return recipesClient;
    }

    public static Optional<ItemStack> getStack() {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        ItemStack is = player.getHeldItem(EnumHand.MAIN_HAND);
        if (is.getItem() instanceof JecaItem) return Optional.of(is);
        is = player.getHeldItem(EnumHand.OFF_HAND);
        return Optional.ofNullable(is.getItem() instanceof JecaItem ? is : null);
    }

    // file, recipes
    public static List<Pair<String, Recipes>> discover() {
        File dir = new File(Loader.instance().getConfigDir(), "JustEnoughCalculation/data/");
        File[] fs = dir.listFiles();
        Function<File, Recipes> read = f -> {
            NBTTagCompound nbt = Utilities.Json.read(f);
            JustEnoughCalculation.logger.warn("File " + f.getAbsolutePath() + " contains invalid records.");
            return nbt == null ? null : new Recipes(nbt);
        };
        if (fs == null) return new ArrayList<>();
        return Arrays.stream(fs)
                .map(i -> new Pair<>(i.getName(), read.apply(i)))
                .filter(i -> i.two != null)
                .collect(Collectors.toList());
    }

    public static void inport(Recipes recipes, String group) {
        ArrayList<Recipe> buffer = new ArrayList<>();
        recipes.getGroup(group).forEach(i ->
                getRecord().getGroup(group).stream().filter(j -> j.equals(i)).findAny().orElseGet(() -> {
                    buffer.add(i);
                    return null;
                }));
        for (Recipe r : buffer) addRecipe(group, r);
    }

    public static File export(String group) {
        File f = new File(Loader.instance().getConfigDir(), "JustEnoughCalculation/data/" + group + ".json");
        Utilities.Json.write(getRecord().serialize(Collections.singleton(group)), f);
        return f;
    }

    public static File export() {
        File f = new File(Loader.instance().getConfigDir(), "JustEnoughCalculation/data/groups.json");
        Utilities.Json.write(getRecord().serialize(), f);
        return f;
    }

    public static List<String> getGroups() {
        return getRecord().getGroups();
    }

    public static void addRecipe(String group, Recipe recipe) {
        getRecord().add(group, recipe);
        if (isServerActive()) network.sendToServer(new PRecipe(group, -1, recipe));
    }

    public static void setRecipe(String group, int index, Recipe recipe) {
        getRecord().set(group, index, recipe);
        if (isServerActive()) network.sendToServer(new PRecipe(group, index, recipe));
    }

    public static void removeRecipe(String group, int index) {
        getRecord().remove(group, index);
        if (isServerActive()) network.sendToServer(new PRecipe(group, index, null));
    }

    public static Recipe getRecipe(String group, int index) {
        return getRecord().getRecipe(group, index);
    }

    public static Stream<Pair<String, List<Recipe>>> stream() {
        return getRecord().stream();
    }

    public static Recipes.RecipeIterator recipeIterator() {
        return getRecord().recipeIterator();
    }

    public static List<Recipe> getRecipes() {
        return getRecord().getRecipes();
    }

    public static List<Recipe> getRecipes(String group) {
        return getRecord().getRecipes(group);
    }

    public static String getAmount() {
        if (!isServerActive()) return amountClient;
        else return getStack()
                .map(is -> Utilities.getTag(is).getString(KEY_AMOUNT))
                .orElseGet(() -> amountClient);
    }

    public static void setAmount(String amount) {
        if (!isServerActive()) amountClient = amount;
        else getStack().ifPresent(is -> {
            Utilities.getTag(is).setString(KEY_AMOUNT, amount);
            network.sendToServer(new PCalculator(is));
        });
    }

    public static boolean getDetectInv() {
        if (!isServerActive()) return inventoryClient;
        else return getStack()
                .map(is -> Utilities.getTag(is).getBoolean(KEY_INVENTORY))
                .orElseGet(() -> inventoryClient);
    }

    public static void setDetectInv(boolean b) {
        if (!isServerActive()) inventoryClient = b;
        else getStack().ifPresent(is -> {
            Utilities.getTag(is).setBoolean(KEY_INVENTORY, b);
            network.sendToServer(new PCalculator(is));
        });
    }

    public static List<ILabel> getRecent() {
        if (!isServerActive()) return recentsClient.getRecords();
        else {
            ArrayList<ILabel> ret = new ArrayList<>();
            Optional<ItemStack> ois = getStack();
            ois.ifPresent(is -> {
                Recents recent = new Recents(Utilities.getTag(is).getTagList(KEY_RECENTS, 10));
                ret.addAll(recent.getRecords());
            });
            return ret;
        }
    }

    public static void setRecent(ILabel label, boolean replace) {
        if (!isServerActive()) recentsClient.push(label, replace);
        else {
            Optional<ItemStack> ois = getStack();
            ois.ifPresent(is -> {
                Recents recent = new Recents(Utilities.getTag(is).getTagList(KEY_RECENTS, 10));
                recent.push(label, replace);
                Utilities.getTag(is).setTag(KEY_RECENTS, recent.serialize());
                network.sendToServer(new PCalculator(is));
            });
        }
    }

    public static boolean hasDuplicate(Recipe r) {
        return getRecord().hasDuplicate(r);
    }

    public static void loadFromLocal() {
        //noinspection ResultOfMethodCallIgnored
        new File(Loader.instance().getConfigDir(), "JustEnoughCalculation/data").mkdirs();
        File file = new File(Loader.instance().getConfigDir(), "JustEnoughCalculation/record.json");
        NBTTagCompound nbt = Utilities.Json.read(file);
        if (nbt != null) {
            recipesClient = nbt.hasKey(KEY_RECIPES) ? new Recipes(nbt.getCompoundTag(KEY_RECIPES)) : new Recipes();
            recentsClient = nbt.hasKey(KEY_RECENTS) ? new Recents(nbt.getTagList(KEY_RECENTS, 10)) : new Recents();
            amountClient = nbt.hasKey(KEY_AMOUNT) ? nbt.getString(KEY_AMOUNT) : "";
            inventoryClient = !nbt.hasKey(KEY_INVENTORY) || nbt.getBoolean(KEY_INVENTORY);
        } else {
            recipesClient = new Recipes();
            recentsClient = new Recents();
            amountClient = "";
            inventoryClient = true;
        }
    }

    public static void writeToLocal() {
        File file = new File(Loader.instance().getConfigDir(), "JustEnoughCalculation/record.json");
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag(KEY_RECENTS, recentsClient.serialize());
        nbt.setTag(KEY_RECIPES, recipesClient.serialize());
        nbt.setString(KEY_AMOUNT, amountClient);
        nbt.setBoolean(KEY_INVENTORY, inventoryClient);
        Utilities.Json.write(nbt, file);
    }

    // client side
    @SubscribeEvent
    public static void onLogOut(ClientDisconnectionFromServerEvent event) {
        writeToLocal();
        recipesServer = null;
    }

    // server side
    @SubscribeEvent
    public static void onJoin(PlayerLoggedInEvent e) {
        if (!JecaConfig.clientMode)
            network.sendTo(new PRecord(JecaCapability.getRecipes(e.player)), (EntityPlayerMP) e.player);
    }
}
