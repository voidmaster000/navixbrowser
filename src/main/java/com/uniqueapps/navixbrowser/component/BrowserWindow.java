package com.uniqueapps.navixbrowser.component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.RenderingHints;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

import org.cef.CefApp;
import org.cef.browser.CefBrowser;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.uniqueapps.navixbrowser.Main;
import com.uniqueapps.navixbrowser.listener.NavixWindowListener;
import com.uniqueapps.navixbrowser.listener.SimpleDocumentListener;

public class BrowserWindow extends JFrame {

	private static final long serialVersionUID = -3658310837225120769L;

	static GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()[0];

	public final CefApp cefApp;
	private final JTextField browserAddressField;
	private final JTextField browserSearchField;
	private final IconButton homeButton;
	private final IconButton forwardNav;
	private final IconButton backwardNav;
	private final IconButton reloadButton;
	private final IconButton addTabButton;
	private final IconButton addBookmarkButton;
	private final IconButton contextMenuButton;
	public final JProgressBar loadBar;
	public final BrowserTabbedPane tabbedPane;
	public final JSplitPane splitPane;
	public JToolBar toolBar;
	public JToolBar toolBar2;
	public final JPopupMenu suggestionsPopupMenu;
	private final Timer suggestionTimer;
	public final BetterJLabel tooltip;
	public final Timer tooltipTimer;
	public boolean browserIsInFocus = false;

	public static Map<Component, CefBrowser> devToolsComponentMap = new HashMap<>();

	File bookmarkFile = new File(Main.userAppData, "bookmarks");
	private final Map<String, String> bookmarks = new HashMap<>();
	JPanel bookmarksPanel = new JPanel(new FlowLayout(FlowLayout.LEADING, 3, 3));

	@SuppressWarnings("unchecked")
	public BrowserWindow(boolean useOSR, boolean isTransparent, CefApp cefAppX) throws IOException {

		File resources = new File(".", "resources");
		if (resources.mkdir()) {
			Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/resources/navix.ico")),
					new File(resources, "navix.ico").toPath());
			Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/resources/newtab-dark.html")),
					new File(resources, "newtab-dark.html").toPath());
			Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/resources/style-dark.css")),
					new File(resources, "style-dark.css").toPath());
			Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/resources/newtab-light.html")),
					new File(resources, "newtab-light.html").toPath());
			Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/resources/style-light.css")),
					new File(resources, "style-light.css").toPath());
		}

		cefApp = cefAppX;

		bookmarkFile.createNewFile();

		try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(bookmarkFile))) {
			bookmarks.putAll((HashMap<String, String>) ois.readObject());
		} catch (Exception e) {
			refreshBookmarks();
		}

		try {
			setIconImage(ImageIO.read(Objects.requireNonNull(getClass().getResourceAsStream("/images/navix.png"))));
		} catch (IOException e) {
			Main.logger.log(Level.SEVERE, "Failed to load app icon: {0}", e.getMessage());
		}

		homeButton = new IconButton('\uea8a');
		backwardNav = new IconButton('\ue830');
		forwardNav = new IconButton('\uea47');
		reloadButton = new IconButton('\ue72c');
		addTabButton = new IconButton('\uf8aa');
		addBookmarkButton = new IconButton('\ue8a4');
		contextMenuButton = new IconButton('\ue700');
		loadBar = new JProgressBar();
		splitPane = new JSplitPane();
		tooltip = new BetterJLabel();
		tooltipTimer = new Timer(1000, e -> tooltip.setVisible(false));
		browserAddressField = new BetterJTextField(100);
		browserSearchField = new HintTextField("Search in page", 100);
		suggestionsPopupMenu = new JPopupMenu();
		suggestionTimer = new Timer(500, null);
		suggestionTimer.addActionListener(e -> {
			if (!browserAddressField.hasFocus()) {
				suggestionTimer.stop();
			}
			updateSuggestions();
		});

		tabbedPane = new BrowserTabbedPane(this, homeButton, forwardNav, backwardNav,
				reloadButton, addBookmarkButton, browserAddressField, browserSearchField);

		Main.downloadWindow.setVisible(false);

		addListeners();
		prepareNavBar(useOSR, isTransparent);

		tabbedPane.setBorder(new EmptyBorder(0, 0, 0, 0));
		tabbedPane.addBrowserTab(cefApp, Main.settings.newTabURL, useOSR, isTransparent);
	}

	private void addListeners() {
		addWindowListener(new NavixWindowListener(this, cefApp));
	}

	private void prepareNavBar(boolean useOSR, boolean isTransparent) {
		browserAddressField.addActionListener(l -> {
			suggestionsPopupMenu.setVisible(false);
			String query = browserAddressField.getText();
			try {
				URI.create(query).toURL();
				tabbedPane.getSelectedBrowser().loadURL(query);
			} catch (IllegalArgumentException | MalformedURLException e) {
				if (query.contains(".") || query.contains("://")) {
					tabbedPane.getSelectedBrowser().loadURL(query);
				} else {
					tabbedPane.getSelectedBrowser().loadURL(Main.settings.searchEngine + query);
				}
			}
		});
		browserAddressField.getDocument().addDocumentListener((SimpleDocumentListener) e -> {
			if (browserAddressField.hasFocus()) {
				suggestionTimer.restart();
			}
		});
		browserAddressField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				browserAddressField.selectAll();
				if (!browserIsInFocus)
					return;
				browserIsInFocus = false;
				KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
				browserAddressField.requestFocusInWindow();
			}
		});

		browserSearchField.getDocument().addDocumentListener((SimpleDocumentListener) e -> {
			if (!browserSearchField.getText().isEmpty()) {
				tabbedPane.getSelectedBrowser().find(
						browserSearchField.getText(),
						true,
						false,
						false);
			} else {
				if (tabbedPane.getSelectedBrowser() != null) {
					tabbedPane.getSelectedBrowser().stopFinding(true);
				}
			}
		});
		browserSearchField.addFocusListener(new FocusAdapter() {
			@Override
			public void focusGained(FocusEvent e) {
				if (!browserIsInFocus)
					return;
				browserIsInFocus = false;
				KeyboardFocusManager.getCurrentKeyboardFocusManager().clearGlobalFocusOwner();
				browserSearchField.requestFocusInWindow();
			}
		});

		browserAddressField.setBorder(new RoundedBorder(Color.LIGHT_GRAY.darker(), 1, 28, 5));
		browserAddressField.setOpaque(false);
		browserAddressField.setFont(new JLabel().getFont());

		browserSearchField.setBorder(new RoundedBorder(Color.LIGHT_GRAY.darker(), 1, 28, 5));
		browserSearchField.setOpaque(false);
		browserSearchField.setFont(new JLabel().getFont());

		suggestionsPopupMenu.setVisible(false);
		suggestionsPopupMenu.setFocusable(false);
		suggestionsPopupMenu.setLightWeightPopupEnabled(false);
		suggestionTimer.setRepeats(false);
		
		tooltip.setVisible(false);
		tooltipTimer.setRepeats(false);

		backwardNav.setEnabled(false);
		forwardNav.setEnabled(false);

		homeButton.addActionListener(l -> {
			if (tabbedPane.getSelectedBrowser() != null) {
				tabbedPane.getSelectedBrowser().loadURL("navix://home");
			}
		});
		backwardNav.addActionListener(l -> {
			if (tabbedPane.getSelectedBrowser() != null) {
				if (tabbedPane.getSelectedBrowser().canGoBack()) {
					tabbedPane.getSelectedBrowser().goBack();
				}
			}
		});
		forwardNav.addActionListener(l -> {
			if (tabbedPane.getSelectedBrowser() != null) {
				if (tabbedPane.getSelectedBrowser().canGoForward()) {
					tabbedPane.getSelectedBrowser().goForward();
				}
			}
		});
		reloadButton.addActionListener(l -> {
			if (tabbedPane.getSelectedBrowser() != null) {
				tabbedPane.getSelectedBrowser().reload();
			}
		});
		addTabButton.addActionListener(l -> tabbedPane.addBrowserTab(cefApp, Main.settings.newTabURL, useOSR, isTransparent));
		addBookmarkButton.addActionListener(l -> {
			String name = JOptionPane.showInputDialog("Bookmark name", "New Bookmark");
			if (name != null) {
				String url = JOptionPane.showInputDialog("URL",
						tabbedPane.getSelectedBrowser() != null ? tabbedPane.getSelectedBrowser().getURL()
								: "https://google.com/");
				if (url != null) {
					bookmarks.put(name, url);
					refreshBookmarks();
				}
			}
		});
		contextMenuButton.addActionListener(l -> {
			JPopupMenu popup = new JPopupMenu();

			JMenuItem newTab = new JMenuItem("New tab");
			newTab.addActionListener(l1 -> tabbedPane.addBrowserTab(cefApp, Main.settings.newTabURL, useOSR, isTransparent));
			popup.add(newTab);

			JMenuItem downloads = new JMenuItem("Downloads");
			downloads.addActionListener(l1 -> tabbedPane.addDownloadsTab(cefApp));
			popup.add(downloads);

			JMenuItem settings = new JMenuItem("Settings");
			settings.addActionListener(l1 -> tabbedPane.addSettingsTab(cefApp));
			popup.add(settings);

			popup.addSeparator();

			JMenuItem toggleFullscreen = new JMenuItem("Toggle fullscreen");
			toggleFullscreen.addActionListener(l1 -> {
				if (device.getFullScreenWindow() != BrowserWindow.this) {
					BrowserWindow.this.dispose();
					setUndecorated(true);
					setExtendedState(JFrame.MAXIMIZED_BOTH);
					setVisible(true);
					device.setFullScreenWindow(BrowserWindow.this);
				} else {
					BrowserWindow.this.dispose();
					setUndecorated(false);
					setVisible(true);
					device.setFullScreenWindow(null);
				}
			});
			popup.add(toggleFullscreen);

			JMenuItem toggleInspector = new JMenuItem("Toggle inspector");
			toggleInspector.addActionListener(l1 -> {
				if (splitPane.getRightComponent() == null) {
					if (tabbedPane.getSelectedBrowser() != null) {
						CefBrowser devTools = tabbedPane.getSelectedBrowser().getDevTools();
						devToolsComponentMap.put(devTools.getUIComponent(), devTools);
						splitPane.setRightComponent(devTools.getUIComponent());
						splitPane.setDividerLocation(0.7);
					} else {
						JOptionPane.showMessageDialog(this, "Cannot open inspector for current tab!");
					}
				} else {
					devToolsComponentMap.get(splitPane.getRightComponent()).close(true);
					devToolsComponentMap.remove(splitPane.getRightComponent());
					splitPane.setRightComponent(null);
				}
			});
			popup.add(toggleInspector);

			popup.addSeparator();

			JMenuItem print = new JMenuItem("Print");
			print.addActionListener(l1 -> {
				if (tabbedPane.getSelectedBrowser() != null) {
					tabbedPane.getSelectedBrowser().print();
				} else {
					JOptionPane.showMessageDialog(this, "Cannot print current tab!");
				}
			});
			popup.add(print);

			JMenuItem printToPDF = new JMenuItem("Print to PDF");
			printToPDF.addActionListener(l1 -> {
				if (tabbedPane.getSelectedBrowser() != null) {
					SwingUtilities.invokeLater(() -> {
						PrintFrame printFrame = new PrintFrame(this, tabbedPane);
						printFrame.setLocationRelativeTo(BrowserWindow.this);
						printFrame.setVisible(true);
					});
				} else {
					JOptionPane.showMessageDialog(this, "Cannot print current tab to PDF!");
				}
			});
			popup.add(printToPDF);

			popup.addSeparator();

			JMenuItem exit = new JMenuItem("Exit");
			exit.addActionListener(l1 -> {
				cefApp.dispose();
				BrowserWindow.this.dispose();
			});
			popup.add(exit);

			popup.show(contextMenuButton, 0, contextMenuButton.getHeight());
		});

		JPanel navBar = new JPanel(new GridBagLayout());

		for (var bookmark : bookmarks.entrySet()) {
			BetterJButton bookmarkButton = new BetterJButton(bookmark.getKey()) {
				private static final long serialVersionUID = 7012838912951844369L;

				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2d = (Graphics2D) g;
					g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
					super.paintComponent(g2d);
				}
			};
			bookmarkButton.setBorder(new EmptyBorder(4, 4, 4, 4));
			bookmarkButton.setBackground(this.getBackground());
			try {
				bookmarkButton.setIcon(new ImageIcon(
						ImageIO.read(URI.create("https://www.google.com/s2/favicons?domain=" + bookmark.getValue()).toURL())));
			} catch (IOException e) {
				Main.logger.log(Level.SEVERE, "Failed to load bookmark icon: {0}", e.getMessage());
			}
			bookmarkButton.addActionListener(l -> tabbedPane.getSelectedBrowser().loadURL(bookmark.getValue()));
			JPopupMenu popup = new JPopupMenu();
			JMenuItem removeBookmark = new JMenuItem("Remove Bookmark");
			removeBookmark.addActionListener(l -> {
				bookmarks.remove(bookmark.getKey());
				refreshBookmarks();
			});
			popup.add(removeBookmark);
			bookmarkButton.setComponentPopupMenu(popup);
			bookmarksPanel.add(bookmarkButton);
		}
		bookmarksPanel.setVisible(!bookmarks.isEmpty());

		loadBar.setVisible(false);
		loadBar.setIndeterminate(true);
		JPanel bottomPanel = new JPanel(new GridBagLayout());
		GridBagConstraints gbcX = new GridBagConstraints();
		gbcX.fill = GridBagConstraints.HORIZONTAL;
		gbcX.weightx = 8;
		bottomPanel.add(loadBar, gbcX);
		gbcX.fill = GridBagConstraints.HORIZONTAL;
		gbcX.gridx = 1;
		gbcX.gridy = 1;
		gbcX.weightx = 10;
		bottomPanel.add(bookmarksPanel, gbcX);
		gbcX.fill = GridBagConstraints.NONE;
		gbcX.gridx = 0;
		gbcX.weightx = 1;
		bottomPanel.add(tooltip, gbcX);

		toolBar = new JToolBar();
		toolBar.setFloatable(false);
		toolBar.setMargin(new Insets(3, 0, 3, 0));
		toolBar.add(addTabButton);
		toolBar.addSeparator();
		toolBar.add(backwardNav);
		toolBar.add(forwardNav);
		toolBar.add(reloadButton);
		toolBar.add(homeButton);

		toolBar2 = new JToolBar();
		toolBar2.setFloatable(false);
		toolBar2.setMargin(new Insets(3, 0, 3, 0));
		toolBar2.add(addBookmarkButton);
		toolBar2.add(contextMenuButton);

		JPanel textFields = new JPanel(new GridBagLayout());
		GridBagConstraints gbcT = new GridBagConstraints();
		gbcT.fill = GridBagConstraints.HORIZONTAL;
		gbcT.insets = new Insets(0, 0, 0, 3);
		gbcT.weightx = 80;
		textFields.add(browserAddressField, gbcT);
		gbcT.insets = new Insets(0, 3, 0, 0);
		gbcT.weightx = 20;
		textFields.add(browserSearchField, gbcT);

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.weightx = 0.1;
		navBar.add(toolBar, gbc);
		gbc.weightx = 1000;
		gbc.fill = GridBagConstraints.HORIZONTAL;
		navBar.add(textFields, gbc);
		gbc.weightx = 0.1;
		gbc.fill = GridBagConstraints.NONE;
		navBar.add(toolBar2, gbc);

		int defaultSize = splitPane.getDividerSize();
		splitPane.addContainerListener(new ContainerAdapter() {
			@Override
			public void componentAdded(ContainerEvent e) {
				super.componentAdded(e);
				if (splitPane.getRightComponent() == null) {
					splitPane.setDividerSize(0);
				} else {
					splitPane.setDividerSize(defaultSize);
				}
			}

			@Override
			public void componentRemoved(ContainerEvent e) {
				super.componentRemoved(e);
				if (splitPane.getRightComponent() == null) {
					splitPane.setDividerSize(0);
				} else {
					splitPane.setDividerSize(defaultSize);
				}
			}
		});
		splitPane.setLeftComponent(tabbedPane);
		splitPane.setRightComponent(null);

		getContentPane().add(navBar, BorderLayout.NORTH);
		getContentPane().add(splitPane, BorderLayout.CENTER);
		getContentPane().add(bottomPanel, BorderLayout.SOUTH);
	}

	private void refreshBookmarks() {
		try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(bookmarkFile))) {
			oos.writeObject(bookmarks);
		} catch (IOException e) {
			Main.logger.log(Level.SEVERE, "Failed to save bookmarks: {0}", e.getMessage());
		}
		bookmarksPanel.removeAll();
		for (var bookmark : bookmarks.entrySet()) {
			BetterJButton bookmarkButton = new BetterJButton(bookmark.getKey()) {
				private static final long serialVersionUID = 6390725135135905940L;

				@Override
				protected void paintComponent(Graphics g) {
					Graphics2D g2d = (Graphics2D) g;
					g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
					super.paintComponent(g2d);
				}
			};
			bookmarkButton.setBorder(new EmptyBorder(4, 4, 4, 4));
			bookmarkButton.setBackground(this.getBackground());
			try {
				bookmarkButton.setIcon(new ImageIcon(
						ImageIO.read(URI.create("https://www.google.com/s2/favicons?domain=" + bookmark.getValue()).toURL())));
			} catch (IOException e) {
				Main.logger.log(Level.SEVERE, "Failed to load bookmark icon: {0}", e.getMessage());
			}
			bookmarkButton.addActionListener(l -> tabbedPane.getSelectedBrowser().loadURL(bookmark.getValue()));
			JPopupMenu popup = new JPopupMenu();
			JMenuItem removeBookmark = new JMenuItem("Remove Bookmark");
			removeBookmark.addActionListener(l -> {
				bookmarks.remove(bookmark.getKey());
				refreshBookmarks();
			});
			popup.add(removeBookmark);
			bookmarkButton.setComponentPopupMenu(popup);
			bookmarksPanel.add(bookmarkButton);
		}
		if (!bookmarks.isEmpty()) {
			bookmarksPanel.setVisible(false);
			bookmarksPanel.setVisible(true);
		} else {
			bookmarksPanel.setVisible(false);
		}
	}

	private void updateSuggestions() {
		if (Main.settings.enableSearchSuggestions) {
			String searchString = browserAddressField.getText();
			if (searchString.trim().isEmpty()) {
				suggestionsPopupMenu.setVisible(false);
				return;
			}
			try {
				URL url = URI.create("https://suggestqueries.google.com/complete/search?client=chrome&gl=US&q=" + URLEncoder.encode(searchString, StandardCharsets.UTF_8)).toURL();
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				JsonReader reader = new JsonReader(new BufferedReader(new InputStreamReader(conn.getInputStream())));
				reader.setLenient(true);
				JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray().get(1).getAsJsonArray();
				suggestionsPopupMenu.removeAll();
				jsonArray.forEach(jsonElement -> {
					JMenuItem menuItem = new JMenuItem(jsonElement.getAsString());
					menuItem.addActionListener(l -> {
						browserAddressField.setText(menuItem.getText());
						browserAddressField.getActionListeners()[0].actionPerformed(null);
						suggestionsPopupMenu.setVisible(false);
					});
					suggestionsPopupMenu.add(menuItem);
				});
				suggestionsPopupMenu.revalidate();
				suggestionsPopupMenu.repaint();
				if (!jsonArray.isEmpty()) {
					if (!suggestionsPopupMenu.isVisible()) {
						suggestionsPopupMenu.setPopupSize(browserAddressField.getWidth(), 300);
						suggestionsPopupMenu.show(browserAddressField, 0, browserAddressField.getHeight());
					}
				} else {
					suggestionsPopupMenu.setVisible(false);
				}
			} catch (IOException e) {
				Main.logger.log(Level.SEVERE, "Failed to get search suggestions: {0}", e.getMessage());
			}
		}
	}
}
