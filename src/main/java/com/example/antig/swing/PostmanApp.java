package com.example.antig.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.StringReader;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import com.example.antig.swing.model.PostmanCollection;
import com.example.antig.swing.model.PostmanFolder;
import com.example.antig.swing.model.PostmanNode;
import com.example.antig.swing.model.PostmanRequest;
import com.example.antig.swing.service.ProjectService;
import com.example.antig.swing.service.RecentProjectsManager;
import com.example.antig.swing.ui.NodeConfigPanel;
import com.example.antig.swing.ui.PostmanTreeCellRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.socle2.utils.PropertiesUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PostmanApp extends JFrame {

	private final HttpClientService httpClientService;
	private final ProjectService projectService;
	private final RecentProjectsManager recentProjectsManager;
	private final ObjectMapper objectMapper;

	private JTree projectTree;
	private DefaultTreeModel treeModel;
	private PostmanCollection rootCollection;

	// Node configuration panel (tabbed interface)
	private NodeConfigPanel nodeConfigPanel;

	// Request execution components
	private JTextField urlField;
	private JComboBox<String> methodComboBox;
	private JComboBox<String> bodyTypeComboBox;
	private JTextArea requestBodyArea;
	private JButton sendButton;

	private PostmanNode currentNode;
	// Map to track file association for each collection
	private final Map<PostmanCollection, File> collectionFileMap = new HashMap<>();

	private PostmanNode workspaceRoot; // Invisible root to hold multiple collections

	// Static reference to keep the socket alive
	private static java.net.ServerSocket lockSocket;

	public PostmanApp() {
		this.httpClientService = new HttpClientService();
		this.projectService = new ProjectService();
		this.recentProjectsManager = new RecentProjectsManager();
		this.objectMapper = new ObjectMapper();

		setTitle("Swing Postman Clone");
		setSize(1000, 700);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		initMenu();
		initComponents();

		// Load last opened project
		SwingUtilities.invokeLater(this::restoreWorkspace);
	}

	private void initMenu() {
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");

		JMenuItem saveItem = new JMenuItem("Save Project");
		saveItem.addActionListener(e -> saveProject());
		JMenuItem loadItem = new JMenuItem("Load Project");
		loadItem.addActionListener(e -> loadProject());

		JMenuItem newCollectionItem = new JMenuItem("New Collection");
		newCollectionItem.addActionListener(e -> addNewCollection());

		fileMenu.add(newCollectionItem);
		fileMenu.add(saveItem);
		fileMenu.add(loadItem);
		fileMenu.addSeparator();

		// Recent Projects submenu
		JMenu recentMenu = new JMenu("Recent Projects");
		updateRecentProjectsMenu(recentMenu);
		fileMenu.add(recentMenu);

		menuBar.add(fileMenu);

		// View menu for theme switching
		JMenu viewMenu = new JMenu("View");
		JMenuItem toggleThemeItem = new JMenuItem("Toggle Theme (Light/Dark)");
		toggleThemeItem.addActionListener(e -> toggleTheme());
		viewMenu.add(toggleThemeItem);

		menuBar.add(viewMenu);
		setJMenuBar(menuBar);
	}

	private void initComponents() {
		JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		mainSplitPane.setDividerLocation(250);

		// Left: Tree View
		// Left: Tree View
		// Create an invisible root node to hold multiple collections
		workspaceRoot = new PostmanFolder("Workspace");
		treeModel = new DefaultTreeModel(workspaceRoot);
		projectTree = new JTree(treeModel);

		// Hide the root node so only collections are visible at top level
		projectTree.setRootVisible(false);
		projectTree.setShowsRootHandles(true);

		// Add a default collection
		PostmanCollection defaultCollection = new PostmanCollection("My Collection");
		workspaceRoot.add(defaultCollection);
		treeModel.reload();

		// Set custom cell renderer for icons
		projectTree.setCellRenderer(new PostmanTreeCellRenderer());

		// Enable Drag and Drop
		projectTree.setDragEnabled(true);
		projectTree.setDropMode(javax.swing.DropMode.ON_OR_INSERT);
		projectTree.setTransferHandler(new TreeTransferHandler());

		// Increase row height for better spacing
		projectTree.setRowHeight(28);

		// Track tree expansion/collapse events
		projectTree.addTreeExpansionListener(new javax.swing.event.TreeExpansionListener() {
			@Override
			public void treeExpanded(javax.swing.event.TreeExpansionEvent event) {
				saveTreeExpansionState();
				autoSaveProject();
			}

			@Override
			public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {
				saveTreeExpansionState();
				autoSaveProject();
			}
		});

		projectTree.addTreeSelectionListener(e -> onNodeSelected());
		projectTree.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e)) {
					int row = projectTree.getClosestRowForLocation(e.getX(), e.getY());
					projectTree.setSelectionRow(row);
					showContextMenu(e.getX(), e.getY());
				}
			}
		});

		// Add F2 key listener for renaming nodes
		projectTree.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent e) {
				if (e.getKeyCode() == java.awt.event.KeyEvent.VK_F2) {
					renameSelectedNode();
				} else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_F3) {
					PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
					if (node != null) {
						cloneNode(node);
					}
				} else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DELETE) {
					deleteSelectedNode();
				}
			}
		});

		mainSplitPane.setLeftComponent(new JScrollPane(projectTree));

		// Right: Node Configuration Panel with Request Execution Toolbar
		JPanel rightPanel = new JPanel(new BorderLayout());

		// Top toolbar for request execution (only visible for requests)
		JPanel requestToolbar = createRequestToolbar();
		rightPanel.add(requestToolbar, BorderLayout.NORTH);

		// Center: Tabbed configuration panel
		nodeConfigPanel = new NodeConfigPanel();
		nodeConfigPanel.setAutoSaveCallback(this::autoSaveProject);
		rightPanel.add(nodeConfigPanel, BorderLayout.CENTER);

		mainSplitPane.setRightComponent(rightPanel);
		add(mainSplitPane, BorderLayout.CENTER);
	}

	private JPanel createRequestToolbar() {
		JPanel toolbar = new JPanel(new BorderLayout(5, 5));
		toolbar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		String[] methods = { "GET", "POST", "PUT", "DELETE", "PATCH" };
		methodComboBox = new JComboBox<>(methods);
		methodComboBox.setPreferredSize(new Dimension(100, 30));
		methodComboBox.addActionListener(e -> {
			if (currentNode instanceof PostmanRequest) {
				PostmanRequest req = (PostmanRequest) currentNode;
				String newMethod = (String) methodComboBox.getSelectedItem();
				if (newMethod != null && !newMethod.equals(req.getMethod())) {
					req.setMethod(newMethod);
					// Notify tree model of change to trigger repaint (icon update)
					treeModel.nodeChanged(req);
					autoSaveProject();
				}
			}
		});

		String[] bodyTypes = { "TEXT", "JSON", "XML", "FORM ENCODED" };
		bodyTypeComboBox = new JComboBox<>(bodyTypes);
		bodyTypeComboBox.setPreferredSize(new Dimension(120, 30));
		bodyTypeComboBox.addActionListener(e -> {
			if (currentNode instanceof PostmanRequest) {
				PostmanRequest req = (PostmanRequest) currentNode;
				String newType = (String) bodyTypeComboBox.getSelectedItem();
				if (newType != null && !newType.equals(req.getBodyType())) {
					req.setBodyType(newType);
					nodeConfigPanel.setBodySyntax(newType);
					autoSaveProject();
				}
			}
		});

		urlField = new JTextField();

		sendButton = new JButton("Send");
		sendButton.addActionListener(e ->

		sendRequest());
		sendButton.setPreferredSize(new Dimension(80, 30));

		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		leftPanel.add(methodComboBox);
		leftPanel.add(Box.createHorizontalStrut(5));
		leftPanel.add(bodyTypeComboBox);

		toolbar.add(leftPanel, BorderLayout.WEST);
		toolbar.add(urlField, BorderLayout.CENTER);
		toolbar.add(sendButton, BorderLayout.EAST);

		// Initially hidden, shown only for requests
		toolbar.setVisible(false);

		return toolbar;
	}

	private void onNodeSelected() {
		PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
		if (node == null) {
			return;
		}

		// Save current state before switching
		saveCurrentNodeState();
		autoSaveProject(); // Autosave when switching nodes

		currentNode = node;

		// Load node into config panel
		nodeConfigPanel.loadNode(node);

		// Show/hide request toolbar and load request-specific fields
		if (node instanceof PostmanRequest) {
			PostmanRequest req = (PostmanRequest) node;
			urlField.setText(req.getUrl());
			methodComboBox.setSelectedItem(req.getMethod());
			bodyTypeComboBox.setSelectedItem(req.getBodyType() != null ? req.getBodyType() : "TEXT");

			// Show request toolbar
			Component[] components = nodeConfigPanel.getParent().getComponents();
			for (Component comp : components) {
				if (comp instanceof JPanel && comp != nodeConfigPanel) {
					comp.setVisible(true);
				}
			}
		} else {
			// Hide request toolbar for collections/folders
			Component[] components = nodeConfigPanel.getParent().getComponents();
			for (Component comp : components) {
				if (comp instanceof JPanel && comp != nodeConfigPanel) {
					comp.setVisible(false);
				}
			}
		}
	}

	private void saveCurrentNodeState() {
		if (currentNode == null) {
			return;
		}

		// Save config panel state
		nodeConfigPanel.saveNode();

		// Save request-specific toolbar fields
		if (currentNode instanceof PostmanRequest) {
			PostmanRequest req = (PostmanRequest) currentNode;
			req.setUrl(urlField.getText());
			req.setMethod((String) methodComboBox.getSelectedItem());
			req.setBodyType((String) bodyTypeComboBox.getSelectedItem());
		}
	}

	private void renameSelectedNode() {
		PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
		if (node == null) {
			return;
		}

		String newName = JOptionPane.showInputDialog(this, "Enter name:", node.getName());
		if (newName != null && !newName.trim().isEmpty()) {
			node.setName(newName);
			treeModel.nodeChanged(node);
			if (node == currentNode) {
				onNodeSelected(); // Refresh UI label
			}
			autoSaveProject();
		}
	}

	private void showContextMenu(int x, int y) {
		PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
		if (node == null) {
			return;
		}

		JPopupMenu menu = new JPopupMenu();

		if (node instanceof PostmanCollection || node instanceof PostmanFolder) {
			JMenuItem addFolder = new JMenuItem("Add Folder");
			addFolder.addActionListener(e -> promptAndAddChild(node, "Folder"));
			menu.add(addFolder);

			JMenuItem addRequest = new JMenuItem("Add Request");
			addRequest.addActionListener(e -> promptAndAddChild(node, "Request"));
			menu.add(addRequest);
		}

		JMenuItem rename = new JMenuItem("Rename");
		rename.addActionListener(e -> renameSelectedNode());
		menu.add(rename);

		JMenuItem delete = new JMenuItem("Delete");
		delete.addActionListener(e -> deleteSelectedNode());
		menu.add(delete);

		if (node instanceof PostmanCollection) {
			JMenuItem closeProject = new JMenuItem("Close Project");
			closeProject.addActionListener(e -> {
				collectionFileMap.remove(node);
				updateOpenProjectsList();
				treeModel.removeNodeFromParent(node);
				currentNode = null;
				nodeConfigPanel.loadNode(null);
			});
			menu.add(closeProject);
		}

		JMenuItem clone = new JMenuItem("Clone");
		clone.addActionListener(e -> cloneNode(node));
		menu.add(clone);

		menu.show(projectTree, x, y);
	}

	private void promptAndAddChild(PostmanNode parent, String type) {
		String name = JOptionPane.showInputDialog(this, "Enter " + type + " name:", "New " + type);
		if (name != null && !name.trim().isEmpty()) {
			PostmanNode child;
			if ("Folder".equals(type)) {
				child = new PostmanFolder(name);
			} else {
				child = new PostmanRequest(name);
			}
			addChild(parent, child);
		}
	}

	private void addChild(PostmanNode parent, PostmanNode child) {
		treeModel.insertNodeInto(child, parent, parent.getChildCount());
		TreePath path = new TreePath(child.getPath());
		projectTree.scrollPathToVisible(path);
		projectTree.setSelectionPath(path);
		autoSaveProject();
	}

	private void deleteSelectedNode() {
		PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
		if (node == null) {
			return;
		}

		// Create custom dialog with NO button focused by default
		Object[] options = { "Yes", "No" };
		int response = JOptionPane.showOptionDialog(this, "Are you sure you want to delete '" + node.getName() + "'?",
				"Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[1]); // Default
																																																								// to
																																																								// "No"

		if (response != 0) {
			return; // 0 = Yes, 1 = No
		}

		if (node.getParent() != null) {
			// Identify collection for autosave before removing
			PostmanCollection collection = getCollectionForNode(node);
			boolean isCollection = (node instanceof PostmanCollection);

			PostmanNode parent = (PostmanNode) node.getParent();
			int index = parent.getIndex(node);

			// Remove from tree
			treeModel.removeNodeFromParent(node);

			// If deleting a collection, remove from file map
			if (isCollection) {
				collectionFileMap.remove(node);
				updateOpenProjectsList();
			}

			// Select parent or sibling to keep selection valid
			if (parent.getChildCount() > 0) {
				int newIndex = Math.min(index, parent.getChildCount() - 1);
				PostmanNode sibling = (PostmanNode) parent.getChildAt(newIndex);
				projectTree.setSelectionPath(new TreePath(sibling.getPath()));
			} else {
				// If parent is workspaceRoot (invisible), and we deleted last collection,
				// select nothing?
				// Or if parent is a folder, select the folder.
				if (parent != workspaceRoot) {
					projectTree.setSelectionPath(new TreePath(parent.getPath()));
				} else {
					// Deleted a collection, clear selection
					currentNode = null;
					nodeConfigPanel.loadNode(null);
				}
			}

			// Autosave if we modified a collection (deleted a child)
			if (!isCollection && collection != null && collectionFileMap.containsKey(collection)) {
				File file = collectionFileMap.get(collection);
				try {
					projectService.saveProject(collection, file);
					System.out.println("Autosaved (after delete) " + collection.getName() + " to " + file.getAbsolutePath());
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void cloneNode(PostmanNode node) {
		try {
			String defaultName = node.getName() + "-CLONE";
			String newName = JOptionPane.showInputDialog(this, "Enter name for clone:", defaultName);

			if (newName == null) {
				return; // User cancelled
			}

			// Convert to XML model (no parent references), then back to PostmanNode
			// This avoids cyclic serialization issues
			com.example.antig.swing.model.xml.XmlNode xmlNode = com.example.antig.swing.service.NodeConverter.toXmlNode(node);

			if (xmlNode == null) {
				throw new RuntimeException("Failed to convert node to XML (returned null)");
			}

			PostmanNode clone = com.example.antig.swing.service.NodeConverter.toPostmanNode(xmlNode);

			if (clone == null) {
				throw new RuntimeException("Failed to convert XML back to PostmanNode (returned null)");
			}

			// Rename and assign new IDs recursively
			clone.setName(newName);
			regenerateIds(clone);

			PostmanNode parent = (PostmanNode) node.getParent();
			if (parent != null) {
				addChild(parent, clone);
				// Select the new cloned node
				TreePath path = new TreePath(clone.getPath());
				projectTree.setSelectionPath(path);
				projectTree.scrollPathToVisible(path);
			} else {
				JOptionPane.showMessageDialog(this, "Cannot clone the root node.");
			}
			autoSaveProject();
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Clone failed: " + e.getMessage());
		}
	}

	/**
	 * Recursively regenerate IDs for a node and all its children.
	 */
	private void regenerateIds(PostmanNode node) {
		node.setId(java.util.UUID.randomUUID().toString());

		// Recursively regenerate IDs for children
		int childCount = node.getChildCount();
		for (int i = 0; i < childCount; i++) {
			PostmanNode child = (PostmanNode) node.getChildAt(i);
			regenerateIds(child);
		}
	}

	private Map<String, String> createEnv(PostmanNode node) {
		if (node == null) {
			return Map.of();
		}

		TreeNode parent = node.getParent();

		Map<String, String> parentEnvMap = parent == null ? Map.of() : createEnv((PostmanNode) parent);

		TreeMap<String, String> map = new TreeMap<>();
		map.putAll(parentEnvMap);
		map.putAll(node.getEnvironment());

		Properties env = new Properties();
		env.putAll(map);

		PropertiesUtils.analyseAndProcessProperties(env, Map.of());

		return PropertiesUtils.propertiesToMap(env);
	}

	private Map<String, String> createHeaders(PostmanNode node, Map<String, String> env) {
		if (node == null) {
			return Map.of();
		}

		TreeNode parent = node.getParent();

		Map<String, String> parentHeaderMap = parent == null ? Map.of() : createHeaders((PostmanNode) parent, env);

		TreeMap<String, String> map = new TreeMap<>();
		map.putAll(parentHeaderMap);
		map.putAll(node.getHeaders());

		Properties header = new Properties();
		header.putAll(map);

		PropertiesUtils.analyseAndProcessProperties(header, env);

		return PropertiesUtils.propertiesToMap(header);
	}

	private void sendRequest() {
		if (!(currentNode instanceof PostmanRequest)) {
			return;
		}

		PostmanRequest req = (PostmanRequest) currentNode;
		saveCurrentNodeState(); // Ensure latest edits are saved

		// 1. Initial Environment
		Map<String, String> env = createEnv(currentNode);

		// 2. Script Engine Setup
		ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
		if (engine == null) {
			// Fallback if nashorn is not found (e.g. newer Java versions without
			// dependency)
			engine = new ScriptEngineManager().getEngineByName("js");
		}

		if (engine == null) {
			JOptionPane.showMessageDialog(this,
					"JavaScript engine not found. Please ensure Nashorn or GraalJS is available.");
			return;
		}

		ScriptEngine fEngine = engine;

		// 3. PM Object Setup & Prescript
		PmContext pm = new PmContext(env, req);
		fEngine.put("pm", pm);
		fEngine.put("console", new ConsoleLogger());

		String prescript = req.getPrescript();
		if (prescript != null && !prescript.trim().isEmpty()) {
			try {
				fEngine.eval(prescript);
			} catch (Exception e) {
				log.error("Prescript error", e);
				nodeConfigPanel.getResponseArea().setText("Prescript Error: " + e.getMessage());
				return;
			}
		}

		// 4. Prepare Request (using potentially modified env)
		Map<String, String> headers = createHeaders(currentNode, env);

		// 5. Body Formatting & Content-Type
		String bodyType = req.getBodyType() != null ? req.getBodyType() : "TEXT";
		String bodyToSend = req.getBody();

		if ("FORM ENCODED".equalsIgnoreCase(bodyType)) {
			// Parse properties to map and convert to URL encoded
			Map<String, String> formParams = parseProperties(bodyToSend);
			StringBuilder encoded = new StringBuilder();
			for (Map.Entry<String, String> entry : formParams.entrySet()) {
				if (encoded.length() > 0) {
					encoded.append("&");
				}
				encoded.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
				if (entry.getValue() != null) {
					encoded.append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
				}
			}
			bodyToSend = encoded.toString();
			if (!headers.containsKey("Content-Type")) {
				headers.put("Content-Type", "application/x-www-form-urlencoded");
			}
		} else if ("JSON".equalsIgnoreCase(bodyType)) {
			if (!headers.containsKey("Content-Type")) {
				headers.put("Content-Type", "application/json");
			}
		} else if ("XML".equalsIgnoreCase(bodyType)) {
			if (!headers.containsKey("Content-Type")) {
				headers.put("Content-Type", "application/xml");
			}
		}

		// 6. Send Request
		sendButton.setEnabled(false);
		nodeConfigPanel.getResponseArea().setText("Sending request...");

		String finalBody = bodyToSend;
		Map<String, String> finalHeaders = headers;

		SwingWorker<HttpResponse<String>, Void> worker = new SwingWorker<>() {
			@Override
			protected HttpResponse<String> doInBackground() throws Exception {
				return httpClientService.sendRequest(req.getUrl(), req.getMethod(), finalBody, finalHeaders);
			}

			@Override
			protected void done() {
				try {
					HttpResponse<String> response = get();

					// 7. Postscript
					pm.response = new PmResponse(response);
					String postscript = req.getPostscript();
					if (postscript != null && !postscript.trim().isEmpty()) {
						try {
							fEngine.eval(postscript);
						} catch (Exception e) {
							log.error("Postscript error", e);
							// Append error to response area later
						}
					}

					// 8. Display Response
					StringBuilder sb = new StringBuilder();
					sb.append("Status: ").append(response.statusCode()).append("\n");
					sb.append("Headers:\n");
					response.headers().map().forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
					sb.append("\nBody:\n");

					try {
						Object json = objectMapper.readValue(response.body(), Object.class);
						sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
					} catch (Exception e) {
						sb.append(response.body());
					}

					nodeConfigPanel.getResponseArea().setText(sb.toString());
				} catch (Exception e) {
					nodeConfigPanel.getResponseArea().setText("Error: " + e.getMessage());
					e.printStackTrace();
				} finally {
					sendButton.setEnabled(true);
				}
			}
		};

		worker.execute();
	}

	private Map<String, String> parseProperties(String text) {
		Map<String, String> map = new HashMap<>();
		if (text == null || text.isBlank()) {
			return map;
		}
		try (StringReader reader = new StringReader(text)) {
			Properties props = new Properties();
			props.load(reader);
			for (String name : props.stringPropertyNames()) {
				map.put(name, props.getProperty(name));
			}
		} catch (Exception e) {
			log.error("Failed to parse properties", e);
		}
		return map;
	}

	// --- Scripting Support Classes ---

	public static class PmContext {
		public PmEnvironment environment;
		public PmRequest request;
		public PmResponse response;
		public Object test; // placeholder

		public PmContext(Map<String, String> env, PostmanRequest req) {
			this.environment = new PmEnvironment(env);
			this.request = new PmRequest(req);
		}
	}

	public static class PmEnvironment {
		private final Map<String, String> env;

		public PmEnvironment(Map<String, String> env) {
			this.env = env;
		}

		public String get(String key) {
			return env.get(key);
		}

		public void set(String key, String value) {
			env.put(key, value);
		}

		public Map<String, String> toMap() {
			return env;
		}
	}

	public static class PmRequest {
		public String url;
		public String method;
		public String body;
		public Map<String, String> headers = new HashMap<>();

		public PmRequest(PostmanRequest req) {
			this.url = req.getUrl();
			this.method = req.getMethod();
			this.body = req.getBody();
		}
	}

	public static class PmResponse {
		public int code;
		public String status;
		public String body;
		public Map<String, String> headers;

		public PmResponse(HttpResponse<String> response) {
			this.code = response.statusCode();
			this.status = "OK";
			this.body = response.body();
			this.headers = new HashMap<>();
			response.headers().map().forEach((k, v) -> this.headers.put(k, String.join(",", v)));
		}

		public Object json() {
			try {
				return new com.fasterxml.jackson.databind.ObjectMapper().readValue(body, java.util.Map.class);
			} catch (Exception e) {
				return null;
			}
		}
	}

	public static class ConsoleLogger {
		public void log(Object msg) {
			System.out.println("[Script Console] " + msg);
		}
	}

	private void collectHeaders(PostmanNode node, Map<String, String> headers) {
		if (node.getParent() instanceof PostmanNode) {
			collectHeaders((PostmanNode) node.getParent(), headers);
		}
		if (node.getHeaders() != null) {
			headers.putAll(node.getHeaders());
		}
	}

	private void saveProject() {
		PostmanCollection collection = getCollectionForNode(currentNode);
		if (collection == null) {
			JOptionPane.showMessageDialog(this, "Please select a node within the collection you want to save.");
			return;
		}

		JFileChooser fileChooser = new JFileChooser();
		// If we have a file for this collection, select it
		if (collectionFileMap.containsKey(collection)) {
			fileChooser.setSelectedFile(collectionFileMap.get(collection));
		}

		if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			try {
				File file = fileChooser.getSelectedFile();
				saveCurrentNodeState();
				projectService.saveProject(collection, file);

				collectionFileMap.put(collection, file);
				updateOpenProjectsList();
				recentProjectsManager.addRecentProject(file);
				updateRecentProjectsMenu((JMenu) getJMenuBar().getMenu(0).getMenuComponent(4));
				// Update title bar with saved file path
				setTitle(file.getAbsolutePath());
			} catch (Exception e) {
				e.printStackTrace();
				JOptionPane.showMessageDialog(this, "Error saving: " + e.getMessage());
			}
		}
	}

	private void loadProject() {
		JFileChooser fileChooser = new JFileChooser();
		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			loadProjectFromFile(fileChooser.getSelectedFile());
		}
	}

	private void loadProjectFromFile(File file) {
		try {
			PostmanCollection loadedCollection = loadProjectInternal(file);

			// Update UI and persistence
			currentNode = null;
			nodeConfigPanel.loadNode(null);

			recentProjectsManager.addRecentProject(file);
			updateRecentProjectsMenu((JMenu) getJMenuBar().getMenu(0).getMenuComponent(4));

			updateOpenProjectsList();

			setTitle(file.getAbsolutePath());

		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error loading: " + e.getMessage());
		}
	}

	private PostmanCollection loadProjectInternal(File file) throws Exception {
		// Load project with expansion state
		Object[] result = projectService.loadProjectWithExpansionState(file);
		PostmanCollection loadedCollection = (PostmanCollection) result[0];
		@SuppressWarnings("unchecked")
		java.util.Set<String> expandedIds = (java.util.Set<String>) result[1];

		// Remove default "My Collection" if it's the only node and empty
		if (workspaceRoot.getChildCount() == 1) {
			PostmanNode child = (PostmanNode) workspaceRoot.getChildAt(0);
			if (child instanceof PostmanCollection && "My Collection".equals(child.getName()) && child.getChildCount() == 0) {
				treeModel.removeNodeFromParent(child);
			}
		}

		// Add to workspace
		treeModel.insertNodeInto(loadedCollection, workspaceRoot, workspaceRoot.getChildCount());

		// Map the collection to the file
		collectionFileMap.put(loadedCollection, file);

		// Restore expansion state
		restoreExpansionFromIds(loadedCollection, expandedIds);

		// Scroll to collection
		TreePath path = new TreePath(loadedCollection.getPath());
		projectTree.scrollPathToVisible(path);

		return loadedCollection;
	}

	private void restoreWorkspace() {
		java.util.List<String> openProjects = recentProjectsManager.getOpenProjects();
		boolean anyLoaded = false;

		if (!openProjects.isEmpty()) {
			for (String path : openProjects) {
				File file = new File(path);
				if (file.exists()) {
					try {
						loadProjectInternal(file);
						anyLoaded = true;
					} catch (Exception e) {
						System.err.println("Failed to restore project " + path + ": " + e.getMessage());
						e.printStackTrace();
					}
				}
			}
		}

		if (anyLoaded) {
			updateOpenProjectsList();
		} else {
			// Fallback to legacy behavior: load last recent project
			java.util.List<String> recent = recentProjectsManager.getRecentProjects();
			if (!recent.isEmpty()) {
				File lastFile = new File(recent.get(0));
				if (lastFile.exists()) {
					loadProjectFromFile(lastFile);
				}
			}
		}
	}

	private void restoreExpansionFromIds(PostmanNode node, Set<String> expandedIds) {
		if (expandedIds.contains(node.getId())) {
			TreePath path = new TreePath(node.getPath());
			projectTree.expandPath(path);
		}

		// Recursively restore children
		for (int i = 0; i < node.getChildCount(); i++) {
			PostmanNode child = (PostmanNode) node.getChildAt(i);
			restoreExpansionFromIds(child, expandedIds);
		}
	}

	private void expandAllNodes() {
		for (int i = 0; i < projectTree.getRowCount(); i++) {
			projectTree.expandRow(i);
		}
	}

	private void updateRecentProjectsMenu(JMenu recentMenu) {
		recentMenu.removeAll();

		java.util.List<String> recentProjects = recentProjectsManager.getRecentProjects();

		if (recentProjects.isEmpty()) {
			JMenuItem emptyItem = new JMenuItem("(No recent projects)");
			emptyItem.setEnabled(false);
			recentMenu.add(emptyItem);
		} else {
			for (String path : recentProjects) {
				File file = new File(path);
				JMenuItem item = new JMenuItem(file.getName());
				item.setToolTipText(path);
				item.addActionListener(e -> loadProjectFromFile(file));
				recentMenu.add(item);
			}

			recentMenu.addSeparator();
			JMenuItem clearItem = new JMenuItem("Clear Recent Projects");
			clearItem.addActionListener(e -> {
				recentProjectsManager.clearRecentProjects();
				updateRecentProjectsMenu(recentMenu);
			});
			recentMenu.add(clearItem);
		}
	}

	private void updateOpenProjectsList() {
		recentProjectsManager.saveOpenProjects(new java.util.ArrayList<>(collectionFileMap.values()));
	}

	private void autoSaveProject() {
		// Find which collection needs saving
		PostmanCollection collection = getCollectionForNode(currentNode);
		if (collection != null && collectionFileMap.containsKey(collection)) {
			File file = collectionFileMap.get(collection);

			// Ensure in-memory model is up to date with UI if we are editing a node
			saveCurrentNodeState();

			// Collect expansion state
			Set<String> expandedIds = new HashSet<>();
			collectExpandedNodeIds(collection, expandedIds);

			try {
				// Convert to XML with expansion state
				com.example.antig.swing.model.xml.XmlNode xmlNode = com.example.antig.swing.service.NodeConverter
						.toXmlNode(collection, expandedIds);

				// Save using ProjectService (which expects PostmanCollection, but we'll update
				// it)
				projectService.saveProject(collection, file, expandedIds);
				System.out.println("Autosaved " + collection.getName() + " to " + file.getAbsolutePath());
			} catch (Exception e) {
				System.err.println("Autosave failed: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private PostmanCollection getCollectionForNode(PostmanNode node) {
		if (node == null) {
			return null;
		}
		if (node instanceof PostmanCollection) {
			return (PostmanCollection) node;
		}
		return getCollectionForNode((PostmanNode) node.getParent());
	}

	private void addNewCollection() {
		String name = JOptionPane.showInputDialog(this, "Enter Collection name:", "New Collection");
		if (name != null && !name.trim().isEmpty()) {
			PostmanCollection newCollection = new PostmanCollection(name);
			workspaceRoot.add(newCollection);
			treeModel.reload();

			// Select it
			TreePath path = new TreePath(newCollection.getPath());
			projectTree.setSelectionPath(path);
		}
	}

	private void toggleTheme() {
		String currentTheme = recentProjectsManager.getThemePreference();
		boolean isDark = "dark".equals(currentTheme);

		try {
			if (isDark) {
				com.formdev.flatlaf.FlatLightLaf.setup();
				recentProjectsManager.setThemePreference("light");
			} else {
				com.formdev.flatlaf.FlatDarkLaf.setup();
				recentProjectsManager.setThemePreference("dark");
			}

			// Update all components
			SwingUtilities.updateComponentTreeUI(this);

			// Refresh tree to apply new theme
			treeModel.reload();

		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Failed to switch theme: " + e.getMessage());
		}
	}

	private void saveTreeExpansionState() {
		// Expansion state is now saved directly in autoSaveProject
		// This method is kept for compatibility but does nothing
	}

	private void collectExpandedNodeIds(PostmanNode node, Set<String> expandedIds) {
		TreePath path = new TreePath(node.getPath());
		if (projectTree.isExpanded(path)) {
			expandedIds.add(node.getId());
		}

		// Recursively check children
		for (int i = 0; i < node.getChildCount(); i++) {
			PostmanNode child = (PostmanNode) node.getChildAt(i);
			collectExpandedNodeIds(child, expandedIds);
		}
	}

	// Inner class for handling Drag and Drop
	private class TreeTransferHandler extends javax.swing.TransferHandler {
		@Override
		public int getSourceActions(javax.swing.JComponent c) {
			return MOVE;
		}

		@Override
		protected java.awt.datatransfer.Transferable createTransferable(javax.swing.JComponent c) {
			JTree tree = (JTree) c;
			TreePath path = tree.getSelectionPath();
			if (path != null) {
				PostmanNode node = (PostmanNode) path.getLastPathComponent();
				// Prevent dragging the root workspace or collections (optional, but good for
				// structure)
				// Allowing dragging collections to reorder them is fine, but not into folders
				return new java.awt.datatransfer.StringSelection(node.getId()); // Use ID as transfer data
			}
			return null;
		}

		@Override
		public boolean canImport(TransferSupport support) {
			if (!support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) {
				return false;
			}
			// Check drop location
			JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
			TreePath path = dl.getPath();
			if (path == null) {
				return false;
			}

			PostmanNode targetNode = (PostmanNode) path.getLastPathComponent();

			// Can only drop into Folders or Collections (which act as folders)
			// Or insert between nodes (parent must be folder/collection)
			return targetNode instanceof PostmanFolder || targetNode instanceof PostmanCollection;
		}

		@Override
		public boolean importData(TransferSupport support) {
			if (!canImport(support)) {
				return false;
			}

			try {
				// Get dropped node ID
				String nodeId = (String) support.getTransferable()
						.getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
				PostmanNode draggedNode = findNodeById(workspaceRoot, nodeId);

				if (draggedNode == null) {
					return false;
				}

				JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
				TreePath destPath = dl.getPath();
				PostmanNode targetParent = (PostmanNode) destPath.getLastPathComponent();

				// Prevent dropping into itself or its children
				if (draggedNode == targetParent || isChildOf(draggedNode, targetParent)) {
					return false;
				}

				// Perform move
				treeModel.removeNodeFromParent(draggedNode);

				int index = dl.getChildIndex();
				if (index == -1) {
					index = targetParent.getChildCount();
				}

				treeModel.insertNodeInto(draggedNode, targetParent, index);

				// Expand target and select moved node
				projectTree.expandPath(destPath);
				projectTree.setSelectionPath(new TreePath(draggedNode.getPath()));

				// Autosave
				autoSaveProject();

				return true;
			} catch (Exception e) {
				e.printStackTrace();
				return false;
			}
		}

		private PostmanNode findNodeById(PostmanNode root, String id) {
			if (root.getId().equals(id)) {
				return root;
			}
			for (int i = 0; i < root.getChildCount(); i++) {
				PostmanNode found = findNodeById((PostmanNode) root.getChildAt(i), id);
				if (found != null) {
					return found;
				}
			}
			return null;
		}

		private boolean isChildOf(PostmanNode potentialParent, PostmanNode node) {
			if (node == null) {
				return false;
			}
			if (node == potentialParent) {
				return true;
			}
			if (node.getParent() == null) {
				return false; // Reached root without finding potentialParent
			}
			return isChildOf(potentialParent, (PostmanNode) node.getParent());
		}
	}

	public static void main(String[] args) {
		// Single instance check
		try {
			// Bind to 127.0.0.1 explicitly to avoid IPv4/IPv6 ambiguity
			// Use a static field to prevent garbage collection
			lockSocket = new java.net.ServerSocket(54321, 0, java.net.InetAddress.getByName("127.0.0.1"));
		} catch (java.io.IOException e) {
			JOptionPane.showMessageDialog(null, "Application is already running.", "Error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

		try {
			// Load theme preference
			RecentProjectsManager tempManager = new RecentProjectsManager();
			String theme = tempManager.getThemePreference();

			if ("light".equals(theme)) {
				com.formdev.flatlaf.FlatLightLaf.setup();
			} else {
				// Default to dark theme
				com.formdev.flatlaf.FlatDarkLaf.setup();
			}
		} catch (Exception e) {
			e.printStackTrace();
			// Fallback to system look and feel
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		SwingUtilities.invokeLater(() -> new PostmanApp().setVisible(true));
	}
}
