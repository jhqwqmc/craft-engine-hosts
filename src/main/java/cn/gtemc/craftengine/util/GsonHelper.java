package cn.gtemc.craftengine.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class GsonHelper {
    private final Gson gson;

    public GsonHelper() {
        this.gson = new GsonBuilder()
                .disableHtmlEscaping()
                .create();
    }

    public Gson getGson() {
        return gson;
    }

    public static Gson get() {
        return SingletonHolder.INSTANCE.getGson();
    }

    private static class SingletonHolder {
        private static final GsonHelper INSTANCE = new GsonHelper();
    }
}
