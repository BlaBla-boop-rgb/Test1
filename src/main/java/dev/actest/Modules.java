package dev.actest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Items;

/**
 * Die Testfunktionen. Zweck: dem Anticheat des eigenen Servers verdächtiges Verhalten liefern.
 * Alle Schalter sind nur im Speicher und beim Start immer aus.
 */
public final class Modules {

    private Modules() {
    }

    public static boolean autoHit = false;
    public static boolean bhop = false;
    public static boolean autoTotem = false;
    public static boolean autoSprint = false;

    private static int hitTicks = 0;
    private static int totemTicks = 0;
    private static boolean jumpForced = false;
    private static boolean warned = false;

    public static boolean anyOn() {
        return autoHit || bhop || autoTotem || autoSprint;
    }

    public static void disableAll() {
        autoHit = false;
        bhop = false;
        autoTotem = false;
        autoSprint = false;
        hitTicks = 0;
    }

    public static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null) {
            disableAll();
            return;
        }

        if (!Guard.allowed(mc)) {
            if (anyOn()) {
                disableAll();
                if (!warned) {
                    warned = true;
                    p.sendSystemMessage(Component.literal(
                            "[ACTest] Gesperrt: Dieser Server steht nicht in config/actest.json (allowedServers)."));
                }
            }
            releaseJump(mc);
            return;
        }
        warned = false;

        // Bei offenem Menü/Chat nichts tun
        if (mc.screen != null) {
            releaseJump(mc);
            return;
        }

        boolean forward = mc.options.keyUp.isDown();

        if (autoSprint && forward) {
            mc.options.keySprint.setDown(true);
        }

        if (bhop && forward && p.onGround()) {
            mc.options.keyJump.setDown(true);
            jumpForced = true;
        } else {
            releaseJump(mc);
        }

        if (autoTotem && ++totemTicks >= 4) {
            totemTicks = 0;
            tryTotem(mc, p);
        }

        if (autoHit) {
            hitTicks++;
            autoHit(mc, p);
        } else {
            hitTicks = 0;
        }
    }

    private static void releaseJump(Minecraft mc) {
        if (jumpForced) {
            mc.options.keyJump.setDown(false);
            jumpForced = false;
        }
    }

    /** Schlägt den nächsten Spieler im Abstand zu: ohne Blickrichtung, ohne Sichtlinie (auch durch Wände). */
    private static void autoHit(Minecraft mc, LocalPlayer p) {
        ActestConfig cfg = ActestConfig.get();
        if (hitTicks < cfg.hitInterval) return;

        double range = cfg.hitRangeTenths / 10.0;
        double best = range * range;
        AbstractClientPlayer target = null;
        for (AbstractClientPlayer o : mc.level.players()) {
            if (o == p || o.isSpectator() || !o.isAlive()) continue;
            double d = p.distanceToSqr(o);
            if (d <= best) {
                best = d;
                target = o;
            }
        }
        if (target == null) return;

        hitTicks = 0;
        mc.gameMode.attack(p, target);
        p.swing(InteractionHand.MAIN_HAND);
    }

    /** Legt einen Totem aus dem Inventar in die zweite Hand, falls dort keiner liegt. */
    private static void tryTotem(Minecraft mc, LocalPlayer p) {
        if (p.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) return;
        Inventory inv = p.getInventory();
        MultiPlayerGameMode gm = mc.gameMode;
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                // Inventar-Index -> Slot im Spielermenü: Hotbar 0-8 = Slot 36-44, Rest unverändert
                int slot = i < 9 ? 36 + i : i;
                // Button 40 = Tausch mit der zweiten Hand
                gm.handleContainerInput(p.containerMenu.containerId, slot, 40, ContainerInput.SWAP, p);
                return;
            }
        }
    }
}
