package com.uniqueapps.navixbrowser.object;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.logging.Level;

import org.cef.browser.CefBrowser;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.uniqueapps.navixbrowser.Main;

public class DevToolsClient implements Runnable {

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
        try {
            URL url = URI.create("http://localhost:" + Main.DEBUG_PORT + "/json").toURL();
            try (InputStream in = url.openStream()) {
                JsonArray browserJsonArray = JsonParser.parseReader(new BufferedReader(new InputStreamReader(in))).getAsJsonArray();
                browserJsonArray.forEach(jsonElement -> {
                    JsonObject browserJson = jsonElement.getAsJsonObject();
                    if (browserJson.get("url").getAsString().equals(cefBrowser.getURL())) {
                        HttpClient
                                .newHttpClient()
                                .newWebSocketBuilder()
                                .buildAsync(URI.create(browserJson.get("webSocketDebuggerUrl").getAsString()), new WebSocket.Listener() {
                                    @Override
                                    public void onOpen(WebSocket webSocket) {
                                        webSocket.sendText(msgJson, true);
                                    }
                                });
                    }
                });
            }
        } catch (IOException e) {
            Main.logger.log(Level.SEVERE, "Failed to connect to DevTools: {0}", e);
        }
    }
}

