package org.openhab.binding.mihome.handler;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.smarthome.config.core.Configuration;
import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.mihome.internal.EncryptionHelper;
import org.openhab.binding.mihome.internal.XiaomiItemUpdateListener;
import org.openhab.binding.mihome.internal.socket.XiaomiSocket;
import org.openhab.binding.mihome.internal.socket.XiaomiSocketListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.HOST;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.PORT;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.SERIAL_NUMBER;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_BRIDGE;
import static org.openhab.binding.mihome.internal.ModelMapper.getThingTypeForModel;

public class XiaomiBridgeHandler extends ConfigStatusBridgeHandler implements XiaomiSocketListener {

    private final static int ONLINE_TIMEOUT = 30000;

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections.singleton(THING_TYPE_BRIDGE);
    private final static JsonParser parser = new JsonParser();

    private Logger logger = LoggerFactory.getLogger(XiaomiBridgeHandler.class);

    private List<XiaomiItemUpdateListener> itemListeners = new CopyOnWriteArrayList<>();

    private Map<String, JsonObject> xiaomiItems = new HashMap<>();
    private String token; // token of gateway
    private long lastDiscoveryTime;
    private Map<String, Long> lastOnlineMap = new HashMap<>();

    public XiaomiBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        // Currently we have no errors. Since we always use discover, it should always be okay.
        return Collections.emptyList();
    }

    @Override
    public void initialize() {
        // Long running initialization should be done asynchronously in background.
        XiaomiSocket.registerListener(this);
        discoverItems();

        scheduler.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                updateGatewayStatus();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    @Override
    public void dispose() {
        logger.error("dispose");
        XiaomiSocket.unregisterListener(this);
        super.dispose();
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.error("Can't handle command " + command);
    }

    @Override
    public void onDataReceived(String command, JsonObject message) {
        logger.info("Received " + message.toString());

        String sid = message.has("sid") ? message.get("sid").getAsString() : null;
        String token = message.has("token") ? message.get("token").getAsString() : null;

        if (command.equals("get_id_list_ack")) {
            // TODO do something with the token?
            //String token = data.get("token").getAsString();

            JsonArray devices = parser.parse(message.get("data").getAsString()).getAsJsonArray();
            for (JsonElement deviceId : devices) {
                String device = deviceId.getAsString();
                sendMessageToBridge("{\"cmd\": \"read\", \"sid\": \"" + device + "\"}");
            }
        } else if (command.equals("read_ack")) {
            String model = message.get("model").getAsString();
            ThingUID thingUID = getThingUID(model, sid);
            if (thingUID != null) {
                xiaomiItems.put(sid, message);
            }
        }

        // device last seen update
        if (sid != null) {
            lastOnlineMap.put(sid, System.currentTimeMillis());

            // update state for gateway
            if (isGatewayOnline()) {
                updateStatus(ThingStatus.ONLINE);
            }
        }
        if (token != null) {
            this.token = token;
        }

        notifyListeners(command, message);
    }

    private ThingUID getThingUID(String model, String sid) {
        ThingTypeUID thingType = getThingTypeForModel(model);
        if (thingType == null) {
            logger.error("Unknown discovered model: " + model);
            return null;
        }

        return new ThingUID(thingType, sid);
    }

    private void notifyListeners(String command, JsonObject message) {
        for (XiaomiItemUpdateListener itemListener : itemListeners) {
            try {
                String sid = message.get("sid").getAsString();
                itemListener.onItemUpdate(sid, command, message);
            } catch (Exception e) {
                logger.error("An exception occurred while calling the BridgeHeartbeatListener", e);
            }
        }
    }

    public boolean registerItemListener(XiaomiItemUpdateListener listener) {
        if (listener == null) {
            throw new NullPointerException("It's not allowed to pass a null XiaomiItemUpdateListener.");
        }
        boolean result = itemListeners.add(listener);
        if (result) {
            onUpdate();

            // inform the listener initially about all items (it will just look at own item)
            for (Map.Entry<String, JsonObject> entry : new HashSet<>(xiaomiItems.entrySet())) {
                listener.onItemUpdate(entry.getKey(), "read_ack", entry.getValue());
            }
        }
        return result;
    }

    public boolean unregisterItemListener(XiaomiItemUpdateListener listener) {
        boolean result = itemListeners.remove(listener);
        if (result) {
            onUpdate();
        }
        return result;
    }

    private void onUpdate() {
        if (isInitialized()) {
            discoverItems(); // this will as well send all items again to all listeners
        }
    }

    public void sendMessageToBridge(String message) {
        try {
            Configuration config = getThing().getConfiguration();
            String host = (String) config.get(HOST);
            int port = getConfigInteger(config, PORT);
            XiaomiSocket.sendMessage(message, InetAddress.getByName(host), port);
        } catch (UnknownHostException e) {
            logger.error("Could not send message to bridge", e);
        }
    }


    public void writeToDevice(String itemId, String[] keys, Object[] values) {
        String key = (String) getConfig().get("key");

        logger.info("Encrypting \"" + token + "\" with key \"" + key + "\"");
        String encryptedKey = EncryptionHelper.encrypt(token, key);

        sendMessageToBridge("{\"cmd\": \"write\", \"sid\": \"" + itemId + "\", \"data\": \"{" + createDataString(keys, values) + ", \\\"key\\\": \\\"" + encryptedKey + "\\\"}\"}");
    }

    private String createDataString(String[] keys, Object[] values) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;

        if (keys.length != values.length)
            return "";

        for (int i = 0; i < keys.length; i++) {
            String k = keys[i];
            if (!first)
                builder.append(",");
            else
                first = false;

            //write key
            builder.append("\\\"").append(k).append("\\\"").append(": ");

            //write value
            builder.append(getValue(values[i]));
        }
        return builder.toString();
    }

    private String getValue(Object o) {
        if (o instanceof String) {
            return "\\\"" + o + "\\\"";
        } else
            return o.toString();
    }

    private int getConfigInteger(Configuration config, String key) {
        Object value = config.get(key);
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).intValue();
        } else if (value instanceof String) {
            return Integer.parseInt((String) value);
        } else {
            return (Integer) value;
        }
    }

    private void discoverItems() {
        if (System.currentTimeMillis() - lastDiscoveryTime > 10000) {
            forceDiscovery();
        }
    }

    private void forceDiscovery() {
        sendMessageToBridge("{\"cmd\": \"get_id_list\"}");
        lastDiscoveryTime = System.currentTimeMillis();
    }

    public boolean hasItemActivity(String itemId, long withinLastMillis) {
        Long lastOnlineTimeMillis = lastOnlineMap.get(itemId);
        return lastOnlineTimeMillis != null && System.currentTimeMillis() - lastOnlineTimeMillis < withinLastMillis;
    }

    private void updateGatewayStatus() {
        updateStatus(isGatewayOnline() ? ThingStatus.ONLINE : ThingStatus.OFFLINE);
    }

    private boolean isGatewayOnline() {
        return hasItemActivity((String) getConfig().get(SERIAL_NUMBER), ONLINE_TIMEOUT);
    }
}
