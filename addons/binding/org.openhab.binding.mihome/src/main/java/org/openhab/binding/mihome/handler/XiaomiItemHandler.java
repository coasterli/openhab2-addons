/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.mihome.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.mihome.internal.XiaomiItemUpdateListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.CHANNEL_HUMIDITY;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.CHANNEL_IS_OPEN;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.CHANNEL_LAST_MOTION;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.CHANNEL_LOAD_POWER;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.CHANNEL_LOAD_VOLTAGE;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.CHANNEL_MOTION;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.CHANNEL_POWER_CONSUMED;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.CHANNEL_POWER_ON;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.CHANNEL_TEMPERATURE;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.ITEM_ID;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_HT;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_MAGNET;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_MOTION;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_PLUG;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_SWITCH;

/**
 * The {@link XiaomiItemHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Patrick Boos - Initial contribution
 */
public class XiaomiItemHandler extends BaseThingHandler implements XiaomiItemUpdateListener {

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = new HashSet<>(Arrays.asList(THING_TYPE_SENSOR_HT, THING_TYPE_SENSOR_MOTION, THING_TYPE_SENSOR_SWITCH, THING_TYPE_SENSOR_MAGNET, THING_TYPE_SENSOR_PLUG));

    private static final long ONLINE_TIMEOUT = 24 * 60 * 60 * 1000;

    private JsonParser parser = new JsonParser();

    private XiaomiBridgeHandler bridgeHandler;
    private String itemId;

    private Logger logger = LoggerFactory.getLogger(XiaomiItemHandler.class);

    public XiaomiItemHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        initializeThing();

        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                updateItemStatus();
            }
        }, 1, 60, TimeUnit.SECONDS);
    }

    private void initializeThing() {
        final String configItemId = (String) getConfig().get(ITEM_ID);
        if (configItemId != null) {
            itemId = configItemId;
        }
        updateItemStatus();
    }

    @Override
    public void dispose() {
        logger.debug("Handler disposes. Unregistering listener.");
        if (itemId != null) {
            XiaomiBridgeHandler bridgeHandler = getXiaomiBridgeHandler();
            if (bridgeHandler != null) {
                bridgeHandler.unregisterItemListener(this);
                this.bridgeHandler = null;
            }
            itemId = null;
        }
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO somehow it seems that this is called as well with LAST KNOWN STATE when openhab gets started. Can this be turned off somehow?
        if (CHANNEL_POWER_ON.equals(channelUID.getId())) {
            String status = command.toString().toLowerCase();
            getXiaomiBridgeHandler().writeToDevice(itemId, new String[]{"status"}, new Object[]{status});
        }
    }

    @Override
    public void onItemUpdate(String sid, String command, JsonObject message) {
        if (itemId != null && itemId.equals(sid)) {
            updateItemStatus();
            logger.info("Item got update: " + message.toString());

            JsonObject data = parser.parse(message.get("data").getAsString()).getAsJsonObject();
            String model = message.get("model").getAsString();
            switch (model) {
                case "sensor_ht":
                    if (data.get("humidity") != null) {
                        float humidity = data.get("humidity").getAsFloat() / 100;
                        updateState(CHANNEL_HUMIDITY, new DecimalType(humidity));
                    }
                    if (data.get("temperature") != null) {
                        float temperature = data.get("temperature").getAsFloat() / 100;
                        updateState(CHANNEL_TEMPERATURE, new DecimalType(temperature));
                    }
                    break;
                case "motion":
                    boolean hasMotion = data.get("status").getAsString().equals("motion");
                    updateState(CHANNEL_MOTION, hasMotion ? OnOffType.ON : OnOffType.OFF);
                    if (hasMotion) {
                        updateState(CHANNEL_LAST_MOTION, new DateTimeType());
                    }
                    break;
                case "switch":
                    if (data.has("status")) {
                        triggerChannel("button", data.get("status").getAsString());
                    }
                    break;
                case "magnet":
                    if (data.has("status")) {
                        boolean isOpen = !data.get("status").getAsString().equals("close");
                        updateState(CHANNEL_IS_OPEN, isOpen ? OpenClosedType.OPEN : OpenClosedType.CLOSED);
                    }
                    break;
                case "plug":
                    if (data.has("status")) {
                        boolean isOn = data.get("status").getAsString().equals("on");
                        updateState(CHANNEL_POWER_ON, isOn ? OnOffType.ON : OnOffType.OFF);
                    }
                    if (data.has("load_voltage")) {
                        updateState(CHANNEL_LOAD_VOLTAGE, new DecimalType(data.get("load_voltage").getAsBigDecimal()));
                    }
                    if (data.has("load_power")) {
                        updateState(CHANNEL_LOAD_POWER, new DecimalType(data.get("load_power").getAsBigDecimal()));
                    }
                    if (data.has("power_consumed")) {
                        updateState(CHANNEL_POWER_CONSUMED, new DecimalType(data.get("power_consumed").getAsBigDecimal()));
                    }
                    break;
            }
        }
    }

    private synchronized XiaomiBridgeHandler getXiaomiBridgeHandler() {
        if (this.bridgeHandler == null) {
            Bridge bridge = getBridge();
            if (bridge == null) {
                return null;
            }
            ThingHandler handler = bridge.getHandler();
            if (handler instanceof XiaomiBridgeHandler) {
                this.bridgeHandler = (XiaomiBridgeHandler) handler;
                this.bridgeHandler.registerItemListener(this);
            } else {
                return null;
            }
        }
        return this.bridgeHandler;
    }

    private void updateItemStatus() {
        if (itemId != null) {
            // note: this call implicitly registers our handler as a listener on the bridge
            if (getXiaomiBridgeHandler() != null) {
                ThingStatus bridgeStatus = (getBridge() == null) ? null : getBridge().getStatus();
                if (bridgeStatus == ThingStatus.ONLINE) {
                    ThingStatus itemStatus = getThing().getStatus();
                    ThingStatus newStatus = getXiaomiBridgeHandler().hasItemActivity(itemId, ONLINE_TIMEOUT)
                            ? ThingStatus.ONLINE : ThingStatus.OFFLINE;

                    if (!newStatus.equals(itemStatus)) {
                        updateStatus(newStatus);

                        // TODO initialize properties?
                        // initializeProperties();
                    }
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
                }
            } else {
                updateStatus(ThingStatus.OFFLINE);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR);
        }
    }
}
