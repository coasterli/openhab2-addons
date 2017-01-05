package org.openhab.binding.mihome.internal;

import com.google.gson.JsonObject;

public interface XiaomiItemUpdateListener {
    void onItemUpdate(String sid, String command, JsonObject message);
}
