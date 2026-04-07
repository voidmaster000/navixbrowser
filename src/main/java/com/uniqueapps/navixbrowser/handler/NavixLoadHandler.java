package com.uniqueapps.navixbrowser.handler;

import com.uniqueapps.navixbrowser.Main;
import com.uniqueapps.navixbrowser.component.BrowserWindow;
import com.uniqueapps.navixbrowser.object.DevToolsClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.network.CefRequest.TransitionType;

import javax.swing.*;
import java.util.Map;
import java.util.concurrent.*;

public class NavixLoadHandler extends CefLoadHandlerAdapter {

    private static final ScheduledExecutorService DARK_MODE_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "navix-dark-mode");
        thread.setDaemon(true);
        return thread;
    });
    private static final Map<Integer, ScheduledFuture<?>> DARK_MODE_TASKS = new ConcurrentHashMap<>();

    JButton forwardNav, backwardNav;
    BrowserWindow browserWindow;

    public NavixLoadHandler(JButton forwardNav, JButton backwardNav, BrowserWindow browserWindow) {
        this.forwardNav = forwardNav;
        this.backwardNav = backwardNav;
        this.browserWindow = browserWindow;
    }

    @Override
    public void onLoadStart(CefBrowser cefBrowser, CefFrame frame, TransitionType transitionType) {
        super.onLoadStart(cefBrowser, frame, transitionType);
        if (browserWindow.tabbedPane.getSelectedBrowser() == cefBrowser) {
            SwingUtilities.invokeLater(() -> {
                browserWindow.loadBar.setIndeterminate(false);
                browserWindow.loadBar.setValue(0);
                browserWindow.loadBar.setIndeterminate(true);
                browserWindow.loadBar.setVisible(true);
            });
        }
        scheduleDarkModeUpdate(cefBrowser);
    }

    @Override
    public void onLoadEnd(CefBrowser cefBrowser, CefFrame frame, int httpStatusCode) {
        super.onLoadEnd(cefBrowser, frame, httpStatusCode);
        if (browserWindow.tabbedPane.getSelectedBrowser() == cefBrowser) {
            SwingUtilities.invokeLater(() -> browserWindow.loadBar.setVisible(false));
        }
        scheduleDarkModeUpdate(cefBrowser);
    }

    @Override
    public void onLoadingStateChange(CefBrowser cefBrowser, boolean b, boolean b1, boolean b2) {
        super.onLoadingStateChange(cefBrowser, b, b1, b2);
        if (browserWindow.tabbedPane.getSelectedBrowser() == cefBrowser) {
            forwardNav.setEnabled(cefBrowser.canGoForward());
            backwardNav.setEnabled(cefBrowser.canGoBack());
        }
        scheduleDarkModeUpdate(cefBrowser);
    }

    @Override
    public void onLoadError(CefBrowser cefBrowser, CefFrame cefFrame, ErrorCode errorCode, String s, String s1) {
        super.onLoadError(cefBrowser, cefFrame, errorCode, s, s1);
        if (browserWindow.tabbedPane.getSelectedBrowser() == cefBrowser) {
            SwingUtilities.invokeLater(() -> browserWindow.loadBar.setVisible(false));
            if (errorCode.getCode() != -3) {
                SwingUtilities.invokeLater(() -> JOptionPane.showOptionDialog(
                        browserWindow,
                        "Failed to load \"" + s1 + "\" with error \"" + s + "\"!",
                        "Failed to load page!",
                        JOptionPane.DEFAULT_OPTION,
                        JOptionPane.ERROR_MESSAGE,
                        null,
                        null,
                        null));
            }
        }
    }

    private static void scheduleDarkModeUpdate(CefBrowser cefBrowser) {
        int browserObjectKey = System.identityHashCode(cefBrowser);
        ScheduledFuture<?> previousTask = DARK_MODE_TASKS.get(browserObjectKey);
        if (previousTask != null) {
            previousTask.cancel(false);
        }

        ScheduledFuture<?> newTask = DARK_MODE_EXECUTOR.schedule(() -> {
            new DarkModeHandler(cefBrowser).run();
            DARK_MODE_TASKS.remove(browserObjectKey);
        }, 150, TimeUnit.MILLISECONDS);

        DARK_MODE_TASKS.put(browserObjectKey, newTask);
    }
}

class DarkModeHandler extends DevToolsClient {

    public DarkModeHandler(CefBrowser cefBrowser) {
        super(cefBrowser, "{\"id\":1,\"method\":\"Emulation.setAutoDarkModeOverride\",\"params\":{\"enabled\":" + (Main.settings.forceDarkMode ? "true" : "false") + "}}");
    }
}

