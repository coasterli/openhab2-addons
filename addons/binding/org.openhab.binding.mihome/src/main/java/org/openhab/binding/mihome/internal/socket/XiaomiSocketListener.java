package org.openhab.binding.mihome.internal.socket;

import com.google.gson.JsonObject;

public interface XiaomiSocketListener {
    void onDataReceived(String command, JsonObject message);
}
