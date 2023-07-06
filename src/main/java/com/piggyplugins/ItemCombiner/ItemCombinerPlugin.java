package com.piggyplugins.ItemCombiner;

import com.example.EthanApiPlugin.Collections.Bank;
import com.example.EthanApiPlugin.Collections.BankInventory;
import com.example.EthanApiPlugin.Collections.Inventory;
import com.example.EthanApiPlugin.Collections.TileObjects;
import com.example.EthanApiPlugin.Collections.query.TileObjectQuery;
import com.example.EthanApiPlugin.EthanApiPlugin;
import com.example.InteractionApi.BankInteraction;
import com.example.InteractionApi.BankInventoryInteraction;
import com.example.InteractionApi.TileObjectInteraction;
import com.example.PacketUtils.PacketUtilsPlugin;
import com.example.Packets.MousePackets;
import com.example.Packets.MovementPackets;
import com.example.Packets.WidgetPackets;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.piggyplugins.PiggyUtils.PiggyUtilsPlugin;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.HotkeyListener;

import java.util.Arrays;
import java.util.Optional;

@PluginDescriptor(
        name = "Item Combiner",
        description = "Automatically banks & combines items for you",
        enabledByDefault = false,
        tags = {"ethan", "piggy"}
)
@PluginDependency(EthanApiPlugin.class)
@PluginDependency(PacketUtilsPlugin.class)
@PluginDependency(PiggyUtilsPlugin.class)
@Slf4j
public class ItemCombinerPlugin extends Plugin {
    @Inject
    private Client client;
    @Inject
    private MousePackets mousePackets;
    @Inject
    private WidgetPackets widgetPackets;
    @Inject
    private EthanApiPlugin api;
    @Inject
    private ItemCombinerConfig config;
    @Inject
    private KeyManager keyManager;
    private boolean started;
    private int afkTicks;
    private boolean deposit;
    private boolean isMaking;
    private int amtTwo;

    @Provides
    private ItemCombinerConfig getConfig(ConfigManager configManager) {
        return configManager.getConfig(ItemCombinerConfig.class);
    }

    @Override
    protected void startUp() throws Exception {
        keyManager.registerKeyListener(toggle);
    }

    @Override
    protected void shutDown() throws Exception {
        keyManager.unregisterKeyListener(toggle);
    }

    @Subscribe
    private void onGameTick(GameTick event) {
        if (client.getGameState() != GameState.LOGGED_IN
            || !started
            || EthanApiPlugin.isMoving()
            || client.getLocalPlayer().getAnimation() != -1) {
            afkTicks = 0;
            return;
        }

        if (isMaking) {
            if (isDoneMaking()) {
                isMaking = false;
            }
            Widget levelUp = client.getWidget(WidgetInfo.LEVEL_UP);
            if (levelUp != null && !levelUp.isHidden()) {
                isMaking = false;
            }
            return;
        }

        if (deposit) {
            Optional<Widget> item = BankInventory.search()
                    .filter(widget -> !widget.getName().equalsIgnoreCase(config.itemOneName()) && !widget.getName().equalsIgnoreCase(config.itemTwoName())).first();
            item.ifPresent(widget -> BankInventoryInteraction.useItem(widget, "Deposit-All"));
            deposit = false;
            return;
        }

        if (!hasItemOne()) {
            if (!Bank.isOpen()) {
                findBank();
                return;
            }
            withdrawItemOne();
            return;
        }

        if (!hasItemTwo()) {
            if (!Bank.isOpen()) {
                findBank();
                return;
            }
            withdrawItemTwo();
            return;
        }

        if (Bank.isOpen()) {
            MousePackets.queueClickPacket();
            MovementPackets.queueMovement(client.getLocalPlayer().getWorldLocation());
            return;
        }

        Widget potionWidget = client.getWidget(17694734);
        if (potionWidget != null) {
            MousePackets.queueClickPacket();
            WidgetPackets.queueResumePause(17694734, amtTwo);
            isMaking = true;
            return;
        }

        useItems();
    }

    private boolean isDoneMaking() {
        return Inventory.getEmptySlots() == config.itemOneAmt();
    }

    private void findBank() {
        TileObjects.search()
                .filter(tileObject -> {
                    ObjectComposition objectComposition = TileObjectQuery.getObjectComposition(tileObject);
                    return objectComposition.getName().toLowerCase().contains("bank") ||
                            Arrays.stream(objectComposition.getActions()).anyMatch(action -> action != null && action.toLowerCase().contains("bank"));
                })
                .nearestToPlayer()
                .ifPresent(tileObject -> {
                    TileObjectInteraction.interact(tileObject, "Use", "Bank");
                });

        if (!deposit) {
            deposit = true;
        }
    }

    private void withdrawItemOne() {
        Bank.search()
                .nameContains(config.itemOneName())
                .first()
                .ifPresent(item -> BankInteraction.withdrawX(item, config.itemOneAmt()));
    }

    private void withdrawItemTwo() {
        Bank.search()
                .nameContains(config.itemTwoName())
                .first()
                .ifPresent(item -> BankInteraction.withdrawX(item, config.itemTwoAmt()));
    }

    private void useItems() {
        Widget itemOne = Inventory.search().nameContains(config.itemOneName()).first().get();
        Widget itemTwo = Inventory.search().nameContains(config.itemTwoName()).first().get();

        amtTwo = Inventory.getItemAmount(config.itemTwoName());

        MousePackets.queueClickPacket();
        MousePackets.queueClickPacket();
        WidgetPackets.queueWidgetOnWidget(itemOne, itemTwo);
    }

    private boolean hasItemOne() {
        return Inventory.search().filter(item -> item.getName().contains(config.itemOneName())).first().isPresent();
    }

    private boolean hasItemTwo() {
        return Inventory.search().filter(item -> item.getName().contains(config.itemTwoName())).first().isPresent();
    }

    private final HotkeyListener toggle = new HotkeyListener(() -> config.toggle()) {
        @Override
        public void hotkeyPressed() {
            toggle();
        }
    };

    public void toggle() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return;
        }
        started = !started;
    }
}
