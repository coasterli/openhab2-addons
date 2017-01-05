package org.openhab.binding.mihome.internal;

import org.eclipse.smarthome.core.thing.ThingTypeUID;

import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_HT;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_MAGNET;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_MOTION;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_SWITCH;
import static org.openhab.binding.mihome.XiaomiGatewayBindingConstants.THING_TYPE_SENSOR_PLUG;

public class ModelMapper {

    public static ThingTypeUID getThingTypeForModel(String model) {
        switch (model) {
            case "sensor_ht":
                return THING_TYPE_SENSOR_HT;
            case "motion":
                return THING_TYPE_SENSOR_MOTION;
            case "switch":
                return THING_TYPE_SENSOR_SWITCH;
            case "magnet":
                return THING_TYPE_SENSOR_MAGNET;
            case "plug":
                return THING_TYPE_SENSOR_PLUG;
        }
        return null;
    }

    public static String getLabelForModel(String model) {
        switch (model) {
            case "sensor_ht":
                return "Temperature & Humidity Sensor";
            case "motion":
                return "Motion Sensor";
            case "magnet":
                return "Open/close Sensor";
            case "switch":
                return "Button";
            case "plug":
                return "Plug";
        }
        return null;
    }
}
