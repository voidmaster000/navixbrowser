package com.uniqueapps.navixbrowser.object;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;

import org.cef.browser.CefBrowser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uniqueapps.navixbrowser.Main;

public class DevToolsClient implements Runnable {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final ConcurrentMap<Integer, String> DEBUGGER_WS_URL_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Integer, WebSocket> DEBUGGER_WS_CACHE = new ConcurrentHashMap<>();

    private CefBrowser cefBrowser;
    private String msgJson;

    public DevToolsClient(CefBrowser cefBrowser, String msgJson) {
        this.cefBrowser = cefBrowser;
        this.msgJson = msgJson;
    }

    public CefBrowser getCefBrowser() {
        return cefBrowser;
    }

    public String getMsgJson() {
        return msgJson;
    }

    public void setCefBrowser(CefBrowser cefBrowser) {
        this.cefBrowser = cefBrowser;
    }

    public void setMsgJson(String msgJson) {
        this.msgJson = msgJson;
    }

    @Override
    public void run() {
        int browserObjectKey = System.identityHashCode(cefBrowser);
        try {
            String wsUrl = DEBUGGER_WS_URL_CACHE.get(browserObjectKey);
            if (wsUrl == null) {
                wsUrl = resolveDebuggerWebSocketUrl();
                if (wsUrl != null) {
                    DEBUGGER_WS_URL_CACHE.put(browserObjectKey, wsUrl);
                }
            }

            if (wsUrl == null) {
                return;
            }

            if (!sendMessage(browserObjectKey, wsUrl)) {
                // Retry once with a fresh endpoint if the old one is stale.
                DEBUGGER_WS_CACHE.remove(browserObjectKey);
                DEBUGGER_WS_URL_CACHE.remove(browserObjectKey);
                String refreshedWsUrl = resolveDebuggerWebSocketUrl();
                if (refreshedWsUrl != null) {
                    DEBUGGER_WS_URL_CACHE.put(browserObjectKey, refreshedWsUrl);
                    sendMessage(browserObjectKey, refreshedWsUrl);
                }
            }
        } catch (IOException e) {
            Main.logger.log(Level.SEVERE, "Failed to connect to DevTools: {0}", e);
        }
    }

    private String resolveDebuggerWebSocketUrl() throws IOException {
        URL url = URI.create("http://localhost:" + Main.DEBUG_PORT + "/json").toURL();
        try (InputStream in = url.openStream()) {
            JsonArray browserJsonArray = JsonParser.parseReader(new BufferedReader(new InputStreamReader(in))).getAsJsonArray();
            for (var jsonElement : browserJsonArray) {
                JsonObject browserJson = jsonElement.getAsJsonObject();
                if (browserJson.get("url").getAsString().equals(cefBrowser.getURL())) {
                    return browserJson.get("webSocketDebuggerUrl").getAsString();
                }
            }
        }
        return null;
    }

    private boolean sendMessage(int browserObjectKey, String wsUrl) {
        try {
            WebSocket webSocket = DEBUGGER_WS_CACHE.get(browserObjectKey);
            if (webSocket == null || webSocket.isInputClosed() || webSocket.isOutputClosed()) {
                webSocket = HTTP_CLIENT
                        .newWebSocketBuilder()
                        .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {})
                        .join();
                DEBUGGER_WS_CACHE.put(browserObjectKey, webSocket);
            }
            webSocket.sendText(msgJson, true);
            return true;
        } catch (Exception e) {
            Main.logger.log(Level.FINE, "Failed to send DevTools message: {0}", e.getMessage());
            return false;
        }
    }
}

