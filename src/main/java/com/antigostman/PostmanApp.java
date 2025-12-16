package com.antigostman;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.commons.lang3.StringUtils;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import com.antigostman.model.PostmanCollection;
import com.antigostman.model.PostmanFolder;
import com.antigostman.model.PostmanNode;
import com.antigostman.model.PostmanRequest;
import com.antigostman.service.ProjectService;
import com.antigostman.service.RecentProjectsManager;
import com.antigostman.ui.NodeConfigPanel;
import com.antigostman.ui.PostmanTreeCellRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;

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
	private JComboBox<String> httpVersionComboBox; // New HTTP version selector

	// Console components
	private JTextArea consoleTextArea;
	private JTabbedPane consoleTabbedPane;
	private JSplitPane verticalSplitPane;
	private JTextArea requestBodyArea;
	private JSpinner timeoutSpinner;
	private JButton sendButton;
	private JButton pdfButton; // New PDF Button
	private JCheckBox pdfOpenCheckbox; // New Checkbox
	private JCheckBox dlContentCheckbox; // New DL Content Checkbox
	private JPanel requestToolbar;

	private PostmanNode currentNode;
	private boolean isLoadingNode = false; // Flag to prevent listeners from firing during load

	private File currentProjectFile;

	// Static reference to keep the socket alive

	// Static reference to keep the socket alive
	private static java.net.ServerSocket lockSocket;

	// Transient state for last execution (for PDF generation)
	private HttpResponse<String> lastExecutionResponse;
	private Exception lastExecutionException;
	private Map<String, String> lastExecutionRequestHeaders;
	private String lastExecutionRequestBody;
	private String lastExecutionConsoleLog;
	private long lastExecutionDuration; // Duration in milliseconds
	private int consoleStartOffset = 0; // To track log start for current request

	public PostmanApp() throws KeyManagementException, NoSuchAlgorithmException {
		this.httpClientService = new HttpClientService();
		this.projectService = new ProjectService();
		this.recentProjectsManager = new RecentProjectsManager();
		this.objectMapper = new ObjectMapper();

		updateTitle();
		setSize(1000, 700);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		initMenu();
		initComponents();
		initMenu();
		initComponents();
		initConsole();
		initGlobalShortcuts();

		// Load last opened project
		SwingUtilities.invokeLater(this::restoreWorkspace);

		// Add window listener to save before closing
		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				// Save all projects before closing
				saveAllProjects();
			}
		});
	}

	private void initMenu() {
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");

		JMenuItem saveItem = new JMenuItem("Save Project");
		saveItem.addActionListener(e -> saveProject());
		JMenuItem loadItem = new JMenuItem("Load Project");
		loadItem.addActionListener(e -> loadProject());

		JMenuItem newProjectItem = new JMenuItem("New Project");
		newProjectItem.addActionListener(e -> newProject());

		fileMenu.add(newProjectItem);
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

		// Console menu items
		viewMenu.addSeparator();
		JMenuItem toggleConsoleItem = new JMenuItem("Toggle Console");
		toggleConsoleItem.addActionListener(e -> toggleConsole());
		viewMenu.add(toggleConsoleItem);

		JMenuItem clearConsoleItem = new JMenuItem("Clear Console");
		clearConsoleItem.addActionListener(e -> clearConsole());
		viewMenu.add(clearConsoleItem);

		viewMenu.addSeparator();
		JMenuItem openLogItem = new JMenuItem("Open Log File");
		openLogItem.addActionListener(e -> openLogFile());
		viewMenu.add(openLogItem);

		setJMenuBar(menuBar);
	}

	private void initComponents() {
		JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		mainSplitPane.setDividerLocation(250);

		// Left: Tree View
		// Left: Tree View
		// Create root collection
		rootCollection = new PostmanCollection("My Project");
		treeModel = new DefaultTreeModel(rootCollection);
		projectTree = new JTree(treeModel);

		// Show root
		projectTree.setRootVisible(true);
		projectTree.setShowsRootHandles(true);

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
//				saveTreeExpansionState();
			}

			@Override
			public void treeCollapsed(javax.swing.event.TreeExpansionEvent event) {
//				saveTreeExpansionState();
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
		requestToolbar = createRequestToolbar();
		rightPanel.add(requestToolbar, BorderLayout.NORTH);

		// Center: Tabbed configuration panel
		nodeConfigPanel = new NodeConfigPanel();
		nodeConfigPanel.setPdfGenerator(this::generatePdfReport);
		nodeConfigPanel.setPdfEmailSender(this::emailPdfReport);
		// nodeConfigPanel.setAutoSaveCallback(this::autoSaveProject); // Autosave
		// disabled
		nodeConfigPanel.setRecentProjectsManager(recentProjectsManager);
		nodeConfigPanel.setOnExecuteRequest(this::sendRequest);
		rightPanel.add(nodeConfigPanel, BorderLayout.CENTER);

		mainSplitPane.setRightComponent(rightPanel);
		mainSplitPane.setRightComponent(rightPanel);

		// Console Panel (Bottom)
		consoleTabbedPane = new JTabbedPane();
		consoleTextArea = new JTextArea();
		consoleTextArea.setEditable(false);
		consoleTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
		consoleTextArea.addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isRightMouseButton(e)) {
					JPopupMenu menu = new JPopupMenu();
					JMenuItem clearItem = new JMenuItem("Clear Console");
					clearItem.addActionListener(ev -> clearConsole());
					menu.add(clearItem);
					menu.show(e.getComponent(), e.getX(), e.getY());
				}
			}
		});
		JPanel consolePanel = new JPanel(new BorderLayout());

		JPanel consoleToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0)); // Compact toolbar
		consoleToolbar.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		JButton clearConsoleButton = new JButton("Clear");
		clearConsoleButton.setToolTipText("Clear Console Output");
		clearConsoleButton.addActionListener(e -> clearConsole());
		// Make button small
		clearConsoleButton.setMargin(new java.awt.Insets(2, 5, 2, 5));
		clearConsoleButton.setFocusable(false);
		consoleToolbar.add(clearConsoleButton);

		consolePanel.add(consoleToolbar, BorderLayout.NORTH);
		consolePanel.add(new JScrollPane(consoleTextArea), BorderLayout.CENTER);

		consoleTabbedPane.addTab("Console", consolePanel);

		// Vertical Split Pane
		verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		verticalSplitPane.setResizeWeight(0.67); // Give console 33% of height
		verticalSplitPane.setTopComponent(mainSplitPane);
		verticalSplitPane.setBottomComponent(consoleTabbedPane);

		add(verticalSplitPane, BorderLayout.CENTER);

		// Set initial divider location
		SwingUtilities.invokeLater(() -> verticalSplitPane.setDividerLocation(0.67));
	}

	private JPanel createRequestToolbar() {
		JPanel toolbar = new JPanel(new BorderLayout(5, 5));
		toolbar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		String[] methods = { "GET", "POST", "PUT", "DELETE", "PATCH" };
		methodComboBox = new JComboBox<>(methods);
		methodComboBox.setPreferredSize(new Dimension(100, 30));
		methodComboBox.addActionListener(e -> {
//			System.out.println("Method combo action listener fired. isLoadingNode=" + isLoadingNode);
			if (isLoadingNode) {
//				System.out.println("  Skipping because isLoadingNode=true");
				return; // Skip during load
			}
			if (currentNode instanceof PostmanRequest) {
				PostmanRequest req = (PostmanRequest) currentNode;
				String newMethod = (String) methodComboBox.getSelectedItem();
//				System.out.println("  Current method in model: " + req.getMethod());
//				System.out.println("  New method from combo: " + newMethod);
				if (newMethod != null && !newMethod.equals(req.getMethod())) {
//					System.out.println("  CHANGING method from " + req.getMethod() + " to " + newMethod);
					req.setMethod(newMethod);
					// Notify tree model of change to trigger repaint (icon update)
					treeModel.nodeChanged(req);
					// autoSaveProject();
				}
			}
		});

		String[] bodyTypes = { "TEXT", "JSON", "XML", "FORM ENCODED" };
		bodyTypeComboBox = new JComboBox<>(bodyTypes);
		bodyTypeComboBox.setPreferredSize(new Dimension(120, 30));
		bodyTypeComboBox.addActionListener(e -> {
			if (isLoadingNode) {
				return; // Skip during load
			}
			if (currentNode instanceof PostmanRequest) {
				PostmanRequest req = (PostmanRequest) currentNode;
				String newType = (String) bodyTypeComboBox.getSelectedItem();
				if (newType != null && !newType.equals(req.getBodyType())) {
					req.setBodyType(newType);
					nodeConfigPanel.setBodySyntax(newType);
					// autoSaveProject();
				}
			}
		});

		urlField = new JTextField();
		urlField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				saveUrlToModel();
			}

			@Override
			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				saveUrlToModel();
			}

			@Override
			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				saveUrlToModel();
			}

			private void saveUrlToModel() {
				if (currentNode instanceof PostmanRequest) {
					PostmanRequest req = (PostmanRequest) currentNode;
					req.setUrl(urlField.getText());
				}
			}
		});
		// Bind Ctrl+Enter to send request
		urlField.getInputMap().put(
				javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK),
				"sendRequest");
		urlField.getActionMap().put("sendRequest", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				sendRequest();
			}
		});

		sendButton = new JButton("Send");
		sendButton.addActionListener(e -> sendRequest());
		sendButton.setPreferredSize(new Dimension(80, 30));

		// Timeout spinner
		timeoutSpinner = new JSpinner(new SpinnerNumberModel(1000, 1, Integer.MAX_VALUE, 100));
		timeoutSpinner.setToolTipText("Timeout (ms)");
		timeoutSpinner.setPreferredSize(new Dimension(80, 30));
		timeoutSpinner.addChangeListener(e -> {
			if (isLoadingNode) {
				return;
			}
			if (currentNode instanceof PostmanRequest) {
				PostmanRequest req = (PostmanRequest) currentNode;
				req.setTimeout(((Number) timeoutSpinner.getValue()).longValue());
				// autoSaveProject();
			}
		});

		// HTTP Version ComboBox
		String[] httpVersions = { "HTTP/1.1", "HTTP/2" };
		httpVersionComboBox = new JComboBox<>(httpVersions);
		httpVersionComboBox.setToolTipText("HTTP Version");
		httpVersionComboBox.setPreferredSize(new Dimension(90, 30));
		httpVersionComboBox.addActionListener(e -> {
			if (isLoadingNode) {
				return;
			}
			if (currentNode instanceof PostmanRequest) {
				PostmanRequest req = (PostmanRequest) currentNode;
				String newVersion = (String) httpVersionComboBox.getSelectedItem();
				if (newVersion != null && !newVersion.equals(req.getHttpVersion())) {
					req.setHttpVersion(newVersion);
					// autoSaveProject();
				}
			}
		});

		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		leftPanel.add(methodComboBox);
		leftPanel.add(Box.createHorizontalStrut(5));
		leftPanel.add(bodyTypeComboBox);
		leftPanel.add(Box.createHorizontalStrut(5));
		leftPanel.add(httpVersionComboBox);

		toolbar.add(leftPanel, BorderLayout.WEST);
		toolbar.add(urlField, BorderLayout.CENTER);

		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));

		dlContentCheckbox = new JCheckBox("DL Content");
		dlContentCheckbox.setToolTipText("Download response as file and open it");
		dlContentCheckbox.addActionListener(e -> {
			if (isLoadingNode) {
				return;
			}
			if (currentNode instanceof PostmanRequest) {
				PostmanRequest req = (PostmanRequest) currentNode;
				req.setDownloadContent(dlContentCheckbox.isSelected());
				// autoSaveProject();
			}
		});
		rightPanel.add(dlContentCheckbox);
		rightPanel.add(Box.createHorizontalStrut(5));

		rightPanel.add(timeoutSpinner);
		rightPanel.add(Box.createHorizontalStrut(5));
		rightPanel.add(sendButton);

		toolbar.add(rightPanel, BorderLayout.EAST);

		// Initially hidden, shown only for requests
		toolbar.setVisible(false);

		return toolbar;
	}

	private void generatePdfReport() {
		if (!(currentNode instanceof PostmanRequest)) {
			return;
		}

		PostmanRequest req = (PostmanRequest) currentNode;

		if (lastExecutionResponse == null && lastExecutionException == null) {
			int choice = JOptionPane.showConfirmDialog(this,
					"No recent execution found in memory.\nDo you want to run the request now and generate the report?",
					"No Execution Data", JOptionPane.YES_NO_OPTION);
			if (choice == JOptionPane.YES_OPTION) {
				sendRequest();
				// We need to wait for worker to finish... this is async.
				// For now let's just return.
				return;
			} else {
				return;
			}
		}

		try {
			// Save to temp file
			File tempFile = File.createTempFile("antig_report_" + req.getId() + "_", ".pdf");

			com.antigostman.service.PdfReportService pdfService = new com.antigostman.service.PdfReportService();

			String projectName = rootCollection != null ? rootCollection.getName() : "Unknown Project";

			// Use captured execution log instead of full area
			String consoleOutput = lastExecutionConsoleLog != null ? lastExecutionConsoleLog : "";

			pdfService.generateReport(req, projectName, lastExecutionRequestHeaders, lastExecutionRequestBody,
					lastExecutionResponse, lastExecutionException, consoleOutput, lastExecutionDuration, tempFile);

			// Open immediately
			if (tempFile.exists()) {
				java.awt.Desktop.getDesktop().open(tempFile);
			}

		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error generating report: " + e.getMessage());
		}
	}

	private void emailPdfReport() {
		if (!(currentNode instanceof PostmanRequest)) {
			return;
		}

		PostmanRequest req = (PostmanRequest) currentNode;

		if (lastExecutionResponse == null && lastExecutionException == null) {
			int choice = JOptionPane.showConfirmDialog(this,
					"No recent execution found in memory.\nDo you want to run the request now and generate the report?",
					"No Execution Data", JOptionPane.YES_NO_OPTION);
			if (choice == JOptionPane.YES_OPTION) {
				sendRequest();
				return;
			} else {
				return;
			}
		}

		try {
			// Generate PDF to a temp file
			File tempFile = File.createTempFile("antig_report_" + req.getId() + "_", ".pdf");

			com.antigostman.service.PdfReportService pdfService = new com.antigostman.service.PdfReportService();

			String projectName = rootCollection != null ? rootCollection.getName() : "Unknown Project";

			String consoleOutput = lastExecutionConsoleLog != null ? lastExecutionConsoleLog : "";

			pdfService.generateReport(req, projectName, lastExecutionRequestHeaders, lastExecutionRequestBody,
					lastExecutionResponse, lastExecutionException, consoleOutput, lastExecutionDuration, tempFile);

			// Open email client with attachment
			if (tempFile.exists()) {
				String osName = System.getProperty("os.name").toLowerCase();
				String subject = "Rapport de test - " + projectName + " - " + req.getName();
				String body = "Bonjour,\n\nJe vous prie de trouver ci-joint le rapport de test.\n\nProjet : " + projectName
						+ "\nTest : " + req.getName() + "\n\nCordialement.";

				// Get email recipients from settings
				String emailTo = "";
				String emailCc = "";
				if (rootCollection != null) {
					emailTo = rootCollection.getEmailReportTo() != null ? rootCollection.getEmailReportTo() : "";
					emailCc = rootCollection.getEmailReportCc() != null ? rootCollection.getEmailReportCc() : "";
				}

				boolean success = false;

				// Windows: Use Outlook via PowerShell
				if (osName.contains("win")) {
					try {
						String powershellScript = String.format(
								"$outlook = New-Object -ComObject Outlook.Application;" + "$mail = $outlook.CreateItem(0);"
										+ "$mail.Subject = '%s';" + "$mail.Body = '%s';" + "$mail.To = '%s';" + "$mail.CC = '%s';"
										+ "$mail.Attachments.Add('%s');" + "$mail.Display()",
								subject.replace("'", "''"), body.replace("'", "''"), emailTo.replace("'", "''"),
								emailCc.replace("'", "''"), tempFile.getAbsolutePath().replace("\\", "\\\\"));

						ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", powershellScript);
						pb.start();
						success = true;
					} catch (Exception e) {
						System.err.println("Outlook PowerShell command failed: " + e.getMessage());
					}
				}
				// Linux/Unix: Use Thunderbird
				else if (osName.contains("nix") || osName.contains("nux") || osName.contains("aix")) {
					try {
						// Build compose command with to and cc
						StringBuilder composeStr = new StringBuilder();
						composeStr.append(String.format("subject='%s',body='%s',attachment='file://%s'",
								subject.replace("'", "\\'"), body.replace("'", "\\'"), tempFile.getAbsolutePath()));

						if (!emailTo.isEmpty()) {
							composeStr.append(",to='").append(emailTo.replace("'", "\\'")).append("'");
						}
						if (!emailCc.isEmpty()) {
							composeStr.append(",cc='").append(emailCc.replace("'", "\\'")).append("'");
						}

						ProcessBuilder pb = new ProcessBuilder("thunderbird", "-compose", composeStr.toString());
						pb.start();
						success = true;
					} catch (Exception e) {
						System.err.println("Thunderbird command failed: " + e.getMessage());
					}
				}

				// Fallback: use Desktop API (without attachment)
				if (!success && java.awt.Desktop.isDesktopSupported()) {
					java.awt.Desktop desktop = java.awt.Desktop.getDesktop();

					// Create mailto URI
					String mailto = String.format("mailto:?subject=%s&body=%s", java.net.URLEncoder.encode(subject, "UTF-8"),
							java.net.URLEncoder.encode(body, "UTF-8"));

					desktop.browse(new java.net.URI(mailto));

					// Also open the PDF so user can easily attach it
					desktop.open(tempFile);

					JOptionPane.showMessageDialog(this,
							"Email client opened with subject and body.\nPlease attach the PDF file that was opened.",
							"Email Prepared", JOptionPane.INFORMATION_MESSAGE);
				} else if (!success) {
					JOptionPane.showMessageDialog(this,
							"Could not open email client.\nPDF saved at: " + tempFile.getAbsolutePath(), "Error",
							JOptionPane.ERROR_MESSAGE);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error preparing email: " + e.getMessage());
		}
	}

	private void onNodeSelected() {
		PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
		if (node == null) {
			return;
		}

//		System.out.println("=== onNodeSelected ===");
//		System.out.println("Switching FROM: "
//				+ (currentNode != null ? currentNode.getName() + " (" + currentNode.getClass().getSimpleName() + ")" : "null"));
//		System.out.println("Switching TO: " + node.getName() + " (" + node.getClass().getSimpleName() + ")");

		// Save current state before switching
		saveCurrentNodeState();
		// autoSaveProject(); // Autosave when switching nodes

		currentNode = node;

		// Set flag to prevent listeners from firing during load
		isLoadingNode = true;
		try {
			// Load node into config panel
			nodeConfigPanel.loadNode(node);

			// Show/hide request toolbar and load request-specific fields
			if (node instanceof PostmanRequest) {
				PostmanRequest req = (PostmanRequest) node;
//				System.out.println("Loading request: " + req.getName());
//				System.out.println("  URL: " + req.getUrl());
//				System.out.println("  Method: " + req.getMethod());
//				System.out.println("  BodyType: " + req.getBodyType());

				urlField.setText(req.getUrl());
				methodComboBox.setSelectedItem(req.getMethod());
				bodyTypeComboBox.setSelectedItem(req.getBodyType() != null ? req.getBodyType() : "TEXT");
				httpVersionComboBox.setSelectedItem(req.getHttpVersion() != null ? req.getHttpVersion() : "HTTP/1.1");
				timeoutSpinner.setValue(req.getTimeout());
				dlContentCheckbox.setSelected(req.isDownloadContent());

				// Show request toolbar
				requestToolbar.setVisible(true);
				requestToolbar.revalidate();
				requestToolbar.repaint();
			} else {
				// Hide request toolbar for collections/folders
				Component[] components = nodeConfigPanel.getParent().getComponents();
				for (Component comp : components) {
					if (comp instanceof JPanel && comp != nodeConfigPanel) {
						comp.setVisible(false);
					}
				}
				requestToolbar.setVisible(false);
			}
		} finally {
			isLoadingNode = false; // Always reset flag
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
//			System.out.println("Saving request state:");
//			System.out.println("  URL from field: " + urlField.getText());
//			System.out.println("  Method from combo: " + methodComboBox.getSelectedItem());
//			System.out.println("  BodyType from combo: " + bodyTypeComboBox.getSelectedItem());
			req.setUrl(urlField.getText());
			req.setMethod((String) methodComboBox.getSelectedItem());
			req.setBodyType((String) bodyTypeComboBox.getSelectedItem());
			req.setHttpVersion((String) httpVersionComboBox.getSelectedItem());
			req.setTimeout(((Number) timeoutSpinner.getValue()).longValue());
			req.setDownloadContent(dlContentCheckbox.isSelected());
//			System.out.println("  Saved to model - URL: " + req.getUrl() + ", Method: " + req.getMethod() + ", BodyType: "
//					+ req.getBodyType());
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
			// autoSaveProject();
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
			// Root collection options
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
		// autoSaveProject();
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
			PostmanNode parent = (PostmanNode) node.getParent();
			int index = parent.getIndex(node);

			// Remove from tree
			treeModel.removeNodeFromParent(node);

			// Select parent or sibling to keep selection valid
			if (parent.getChildCount() > 0) {
				int newIndex = Math.min(index, parent.getChildCount() - 1);
				PostmanNode sibling = (PostmanNode) parent.getChildAt(newIndex);
				projectTree.setSelectionPath(new TreePath(sibling.getPath()));
			} else {
				projectTree.setSelectionPath(new TreePath(parent.getPath()));
			}

			// Autosave
			// autoSaveProject();
		} else {
			JOptionPane.showMessageDialog(this, "Cannot delete the root project.");
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
			com.antigostman.model.xml.XmlNode xmlNode = com.antigostman.service.NodeConverter.toXmlNode(node);

			if (xmlNode == null) {
				throw new RuntimeException("Failed to convert node to XML (returned null)");
			}

			PostmanNode clone = com.antigostman.service.NodeConverter.toPostmanNode(xmlNode);

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
				projectTree.scrollPathToVisible(path);
			} else {
				JOptionPane.showMessageDialog(this, "Cannot clone the root node.");
			}
			// autoSaveProject();
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

	private String createPrescript(PostmanNode node) {
		if (node == null) {
			return null;
		}

		if (!StringUtils.isBlank(node.getPrescript())) {
			return node.getPrescript();
		}

		return createPrescript((PostmanNode) node.getParent());
	}

	private String createPostscript(PostmanNode node) {
		if (node == null) {
			return null;
		}

		if (!StringUtils.isBlank(node.getPostscript())) {
			return node.getPostscript();
		}

		return createPostscript((PostmanNode) node.getParent());
	}

	private Map<String, String> createEnv(PostmanNode node, boolean parse) {
		if (node == null) {
			return Map.of();
		}

		TreeNode parent = node.getParent();

		Map<String, String> parentEnvMap = parent == null ? Map.of() : createEnv((PostmanNode) parent, false);

		Map<String, String> map = new TreeMap<>();
		map.putAll(parentEnvMap);
		map.putAll(node.getEnvironment());

		Collection<String> keys = map.keySet();

		if (parse) {
			map = parse(map, new HashMap<>());
		}

		Map<String, String> results = new TreeMap<>();
		for (String k : keys) {
			results.put(k, map.get(k));
		}

		return results;
	}

	private Map<String, String> parse(Map<String, String> map, Map<String, ?> variables) {
		return PropsUtils.parse(map, variables);
	}

	public String parse(String value, Map<String, ?> variables) {

		Map<String, String> m = new HashMap<>();
		String key = UUID.randomUUID().toString();
		m.put(key, value);

		Map<String, String> m2 = parse(m, variables);

		return m2.get(key);
	}

	private Map<String, String> createHeaders(PostmanNode node, Map<String, Object> variables, boolean parse) {
		if (node == null) {
			return Map.of();
		}

		TreeNode parent = node.getParent();

		Map<String, String> parentHeaderMap = parent == null ? Map.of()
				: createHeaders((PostmanNode) parent, variables, false);

		Map<String, String> map = new TreeMap<>();
		map.putAll(parentHeaderMap);
		map.putAll(node.getHeaders());

		Collection<String> keys = map.keySet();

		if (parse) {
			map = parse(map, variables);
		}

		Map<String, String> results = new TreeMap<>();
		for (String k : keys) {
			results.put(k, map.get(k));
		}

		return results;
	}

	private Map<String, Object> createVariableMap(PostmanRequest req) {
		Map<String, String> env = createEnv(currentNode, true);

		Map<String, String> globalEnv = ((PostmanNode) rootCollection.getChildAt(0)).getEnvironment();

		env.putAll(globalEnv);

		Map<String, Object> map = new TreeMap<>(env);

		map.put("utils", new Utils());
		map.put("request", req);
		map.put("console", new ConsoleLogger());
		map.put("vars", rootCollection.getGlobalVariables());
		map.putAll(rootCollection.getGlobalVariables());
		return map;
	}

	private void sendRequest() {
		if (!(currentNode instanceof PostmanRequest)) {
			return;
		}

		PostmanRequest req = (PostmanRequest) currentNode;
		saveCurrentNodeState(); // Ensure latest edits are saved

		// Capture start offset of console for this request
		consoleStartOffset = consoleTextArea.getDocument().getLength();

		// Reset transient state to avoid "stale" data from previous success
		lastExecutionResponse = null;
		lastExecutionException = null;
		lastExecutionRequestHeaders = null;
		lastExecutionRequestBody = null;
		lastExecutionConsoleLog = null;
		lastExecutionDuration = 0;

		try {
			// 1. Initial Environment

			Map<String, Object> variables = createVariableMap(req);

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
				// Record as exception
				lastExecutionException = new RuntimeException("JavaScript engine not found.");
				captureConsoleLog();
				return;
			}

			ScriptEngine fEngine = engine;

			for (String k : variables.keySet()) {
				fEngine.put(k, variables.get(k));
			}

			String prescript = createPrescript(req);

			if (prescript != null && !prescript.trim().isEmpty()) {
				try {
					fEngine.eval(prescript);
				} catch (Exception e) {
					log.error("Prescript error", e);
					nodeConfigPanel.getResponseArea().setText("Prescript Error: " + e.getMessage());
					nodeConfigPanel.setResponseBodySyntax(SyntaxConstants.SYNTAX_STYLE_NONE);
					// Record as exception
					lastExecutionException = e;
					captureConsoleLog();
					return;
				}
			}

			// 4. Prepare Request (using potentially modified env)
			Map<String, String> headers = createHeaders(currentNode, variables, true);

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
				headers.put("Content-Type", "application/x-www-form-urlencoded");
			} else if ("JSON".equalsIgnoreCase(bodyType)) {
				headers.put("Content-Type", "application/json");
			} else if ("XML".equalsIgnoreCase(bodyType)) {
				headers.put("Content-Type", "application/xml");
			} else if ("TEXT".equalsIgnoreCase(bodyType)) {
				headers.put("Content-Type", "text/plain");
			}

			// 6. Send Request
			// nodeConfigPanel.selectExecutionTab();
			sendButton.setEnabled(false);
			nodeConfigPanel.getResponseArea().setText("Sending request...");
			nodeConfigPanel.setResponseBodySyntax(SyntaxConstants.SYNTAX_STYLE_NONE);

			String finalBody = bodyToSend;
			Map<String, String> finalHeaders = headers;

			long startTime = System.currentTimeMillis();

			SwingWorker<HttpResponse<String>, Void> worker = new SwingWorker<>() {
				@Override
				protected HttpResponse<String> doInBackground() throws Exception {

					String url = parse(req.getUrl(), variables);
					String body = parse(finalBody, variables);

					System.out.println("> " + url);

					if (dlContentCheckbox.isSelected()) {
						// Binary download mode
						HttpResponse<byte[]> binaryResp = httpClientService.sendRequestBytes(url, req.getMethod(), body,
								finalHeaders, req.getTimeout(), req.getHttpVersion());

						// Check if successful before processing file
						if (binaryResp.body() != null && binaryResp.body().length > 0) {
							try {
								// Detect type
								org.apache.tika.Tika tika = new org.apache.tika.Tika();
								String mimeType = tika.detect(binaryResp.body());
								String ext = ".bin"; // Default
								try {
									org.apache.tika.mime.MimeTypes allTypes = org.apache.tika.mime.MimeTypes.getDefaultMimeTypes();
									org.apache.tika.mime.MimeType type = allTypes.forName(mimeType);
									ext = type.getExtension();
									if (ext == null || ext.isEmpty()) {
										ext = ".bin";
									}
								} catch (Exception ex) {
									// Fallback extension detection
									if (mimeType.contains("pdf")) {
										ext = ".pdf";
									} else if (mimeType.contains("json")) {
										ext = ".json";
									} else if (mimeType.contains("xml")) {
										ext = ".xml";
									} else if (mimeType.contains("html")) {
										ext = ".html";
									} else if (mimeType.contains("text")) {
										ext = ".txt";
									} else if (mimeType.contains("image/png")) {
										ext = ".png";
									} else if (mimeType.contains("image/jpeg")) {
										ext = ".jpg";
									}
									}
                                    
                                    // Generate filename: DL-yyyyMMddHHmmss
									String timestamp = java.time.LocalDateTime.now()
											.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
									String filename = "DL-" + timestamp + ext;
									File tempDir = new File(System.getProperty("java.io.tmpdir"));
									File tempFile = new File(tempDir, filename);
									
									java.nio.file.Files.write(tempFile.toPath(), binaryResp.body());
									
									System.out.println("Saved download to: " + tempFile.getAbsolutePath());

								// Open file
								if (java.awt.Desktop.isDesktopSupported()) {
									SwingUtilities.invokeLater(() -> {
										try {
											java.awt.Desktop.getDesktop().open(tempFile);
										} catch (Exception ex) {
											ex.printStackTrace();
											JOptionPane.showMessageDialog(PostmanApp.this, "Failed to open file: " + ex.getMessage());
										}
									});
								}
							} catch (Exception ex) {
								ex.printStackTrace();
								System.err.println("Failed to save/open download: " + ex.getMessage());
							}
						}

						// Convert back to String based response for UI compatibility (pseudo-response)
						// We construct a fake string response so the rest of the logic (logging, UI
						// update) works
						// We interpret bytes as UTF-8 for display, or summary if too large
						String stringBody = "";
						if (binaryResp.body() != null) {
							if (binaryResp.body().length < 100000) {
								stringBody = new String(binaryResp.body(), StandardCharsets.UTF_8);
							} else {
								stringBody = "[Binary content size: " + binaryResp.body().length + " bytes]";
							}
						}

						// Hack to adapt HttpResponse<byte[]> to HttpResponse<String> is hard because
						// HttpResponse is an interface
						// and we can't easily mock it without a library or custom class.
						// So we will just call the string version again for the UI? No, that triggers
						// double request.
						// Better: Return a dummy HttpResponse<String> if we can, or refactor.
						// For now, let's just make a simple wrapper or rely on the fact that we can't
						// easily change the return type of doInBackground here
						// without changing the SwingWorker type.

						// The SwingWorker is typed <HttpResponse<String>, Void>.
						// We MUST return HttpResponse<String>.
						// We already consumed the stream in sendRequestBytes.
						// We can implement a simple wrapper.

						final String bodyStr = stringBody;
						return new HttpResponse<String>() {
							@Override
							public int statusCode() {
								return binaryResp.statusCode();
							}

							@Override
							public HttpRequest request() {
								return binaryResp.request();
							}

							@Override
							public java.util.Optional<HttpResponse<String>> previousResponse() {
								return java.util.Optional.empty();
							} // Generics mismatch, ignore

							@Override
							public java.net.http.HttpHeaders headers() {
								return binaryResp.headers();
							}

							@Override
							public String body() {
								return bodyStr;
							}

							@Override
							public java.util.Optional<javax.net.ssl.SSLSession> sslSession() {
								return binaryResp.sslSession();
							}

							@Override
							public URI uri() {
								return binaryResp.uri();
							}

							@Override
							public java.net.http.HttpClient.Version version() {
								return binaryResp.version();
							}
						};

					} else {
						return httpClientService.sendRequest(url, req.getMethod(), body, finalHeaders, req.getTimeout(),
								req.getHttpVersion());
					}
				}

				@Override
				protected void done() {
					// Display Request in execution tabs (always, even on error)
					// Request Headers
					StringBuilder reqHeadersSb = new StringBuilder();
					finalHeaders.forEach((k, v) -> reqHeadersSb.append(k).append(": ").append(v).append("\n"));
					nodeConfigPanel.setRequestHeaders(reqHeadersSb.toString());

					// Request Body
					nodeConfigPanel.setRequestBody(finalBody != null ? finalBody : "");

					HttpResponse<String> response = null;
					Exception executionException = null;

					try {
						try {
							response = get();
						} catch (Exception ex) {
							ex.printStackTrace();
							executionException = ex;
						}

						// 7. Postscript (Run even if request failed)
						if (response != null) {
							fEngine.put("response", response);
						}

						String postscript = createPostscript(req);

						if (postscript != null && !postscript.trim().isEmpty()) {
							try {
								fEngine.eval(postscript);
							} catch (Exception ex) {
								ex.printStackTrace();
								nodeConfigPanel.getResponseArea().append("\n\n[Postscript Error] " + ex.getMessage());
							}
						}

						// 8. Display Response or Error
						if (response != null) {
							// Response Headers
							StringBuilder respHeadersSb = new StringBuilder();
							respHeadersSb.append("Status: ").append(response.statusCode()).append("\n\n");
							response.headers().map().forEach((k, v) -> respHeadersSb.append(k).append(": ").append(v).append("\n"));
							nodeConfigPanel.setResponseHeaders(respHeadersSb.toString());

							// Response Body (with JSON formatting if applicable)
							String responseBody;
							String syntaxStyle = SyntaxConstants.SYNTAX_STYLE_NONE;

							// Determine syntax from Content-Type header
							String contentType = response.headers().firstValue("Content-Type").orElse("").toLowerCase();
							if (contentType.contains("json")) {
								syntaxStyle = SyntaxConstants.SYNTAX_STYLE_JSON;
							} else if (contentType.contains("xml")) {
								syntaxStyle = SyntaxConstants.SYNTAX_STYLE_XML;
							} else if (contentType.contains("html")) {
								syntaxStyle = SyntaxConstants.SYNTAX_STYLE_HTML;
							}

							try {
								Object json = objectMapper.readValue(response.body(), Object.class);
								responseBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
								// If parsing succeeded, it is JSON
								syntaxStyle = SyntaxConstants.SYNTAX_STYLE_JSON;
							} catch (Exception e) {
								responseBody = response.body();
							}
							nodeConfigPanel.setResponseBody(responseBody);
							nodeConfigPanel.setResponseBodySyntax(syntaxStyle);

							// Also set the old responseArea (now just shows response body since we have
							// separate tabs)
							nodeConfigPanel.getResponseArea().setText(responseBody);
						} else if (executionException != null) {
							String errorMsg = "Error: " + executionException.getMessage();
							nodeConfigPanel.setResponseBody(errorMsg);
							nodeConfigPanel.setResponseBodySyntax(SyntaxConstants.SYNTAX_STYLE_NONE);
							nodeConfigPanel.getResponseArea().setText(errorMsg);
							executionException.printStackTrace();
						}
					} finally {
						long endTime = System.currentTimeMillis();
						long duration = endTime - startTime;

						sendButton.setEnabled(true);
						sendButton.setEnabled(true);
						// Log execution to file
						logExecution(req, finalHeaders, finalBody, response, executionException);

						// Store for PDF report
						lastExecutionResponse = response;
						lastExecutionException = executionException;
						lastExecutionRequestHeaders = finalHeaders;
						lastExecutionRequestBody = finalBody;
						lastExecutionDuration = duration;

						// Capture console log for this request using invokeLater to catch pending
						// prints (e.g. from printStackTrace)
						SwingUtilities.invokeLater(() -> captureConsoleLog());
					}
				}
			};

			worker.execute();

		} catch (Exception e) {
			// Handle synchronous setup errors (e.g. header parsing, global var failures)
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error preparing request: " + e.getMessage());
			lastExecutionException = e;
			captureConsoleLog();
		}
	}

	private Map<String, String> parseProperties(String text) {
		Map<String, String> map = new java.util.LinkedHashMap<>();
		if (text == null || text.isBlank()) {
			return map;
		}
		// Manual parsing to preserve order
		String[] lines = text.split("\\R");
		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
				continue;
			}
			int eqIndex = line.indexOf('=');
			int colIndex = line.indexOf(':');

			int splitIndex = -1;
			if (eqIndex != -1 && colIndex != -1) {
				splitIndex = Math.min(eqIndex, colIndex);
			} else if (eqIndex != -1) {
				splitIndex = eqIndex;
			} else {
				splitIndex = colIndex;
			}

			if (splitIndex != -1) {
				String key = line.substring(0, splitIndex).trim();
				String value = line.substring(splitIndex + 1).trim();
				map.put(key, value);
			} else {
				map.put(line, "");
			}
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
		public Map<String, String> headers = new java.util.LinkedHashMap<>();

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
			this.headers = new java.util.LinkedHashMap<>();
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

	private void updateTitle() {
		if (currentProjectFile != null) {
			setTitle(currentProjectFile.getAbsolutePath());
		} else if (rootCollection != null) {
			setTitle("Swing Postman Clone - " + rootCollection.getName());
		} else {
			setTitle("Swing Postman Clone");
		}
	}

	private void saveProject() {
		if (currentProjectFile == null) {
			JFileChooser fileChooser = new JFileChooser();
			if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				currentProjectFile = fileChooser.getSelectedFile();
			} else {
				return;
			}
		}

		try {
			saveCurrentNodeState();

			// Save last selected node ID
			if (currentNode != null) {
				rootCollection.setLastSelectedNodeId(currentNode.getId());
			}

			// Collect expansion state
			Set<String> expandedIds = new HashSet<>();
			collectExpandedNodeIds(rootCollection, expandedIds);

			projectService.saveProject(rootCollection, currentProjectFile, expandedIds);

			updateOpenProjectsList();
			recentProjectsManager.addRecentProject(currentProjectFile);
			updateRecentProjectsMenu((JMenu) getJMenuBar().getMenu(0).getMenuComponent(4));
			recentProjectsManager.addRecentProject(currentProjectFile);
			updateRecentProjectsMenu((JMenu) getJMenuBar().getMenu(0).getMenuComponent(4));
			// Update title bar with saved file path
			updateTitle();

			// Visual feedback
			triggerSaveFeedback();
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error saving: " + e.getMessage());
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

			updateTitle();

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

		// Set as root
		rootCollection = loadedCollection;
		treeModel.setRoot(rootCollection);
		currentProjectFile = file;

		// Expand whole tree (User request)
		expandAllNodes(projectTree, new TreePath(rootCollection.getPath()));

		// Scroll to collection
		TreePath path = new TreePath(rootCollection.getPath());
		projectTree.scrollPathToVisible(path);

		// Restore expansion state
		if (expandedIds != null) {
			for (String id : expandedIds) {
				PostmanNode node = findNodeById(rootCollection, id);
				if (node != null) {
					projectTree.expandPath(new TreePath(node.getPath()));
				}
			}
		}

		// Restore last selected node
		String lastSelectedId = rootCollection.getLastSelectedNodeId();
		if (lastSelectedId != null) {
			PostmanNode lastSelected = findNodeById(rootCollection, lastSelectedId);
			if (lastSelected != null) {
				TreePath path2 = new TreePath(lastSelected.getPath());
				projectTree.setSelectionPath(path2);
				projectTree.scrollPathToVisible(path2);
			}
		}

		return loadedCollection;
	}

	private void expandAllNodes(JTree tree, TreePath parent) {
		TreeNode node = (TreeNode) parent.getLastPathComponent();
		if (node.getChildCount() >= 0) {
			for (java.util.Enumeration<?> e = node.children(); e.hasMoreElements();) {
				TreeNode n = (TreeNode) e.nextElement();
				TreePath path = parent.pathByAddingChild(n);
				expandAllNodes(tree, path);
			}
		}
		tree.expandPath(parent);
	}

	private void restoreWorkspace() {
		java.util.List<String> recent = recentProjectsManager.getRecentProjects();
		if (!recent.isEmpty()) {
			File file = new File(recent.get(0));
			if (file.exists()) {
				try {
					loadProjectInternal(file);
					updateTitle();
				} catch (Exception e) {
//					System.err.println("Failed to restore project " + file + ": " + e.getMessage());
					e.printStackTrace();
				}
			}
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
		// No longer tracking multiple open projects, just recent ones
	}

	private void autoSaveProject() {
		if (currentProjectFile != null) {
			// Ensure in-memory model is up to date with UI if we are editing a node
			saveCurrentNodeState();

			// Collect expansion state
			Set<String> expandedIds = new HashSet<>();
			collectExpandedNodeIds(rootCollection, expandedIds);

			try {
				// Save using ProjectService
				projectService.saveProject(rootCollection, currentProjectFile, expandedIds);
//				System.out.println("Autosaved " + rootCollection.getName() + " to " + currentProjectFile.getAbsolutePath());
			} catch (Exception e) {
				System.err.println("Autosave failed: " + e.getMessage());
				e.printStackTrace();
			}
		}
	}

	private void saveAllProjects() {
		if (currentProjectFile != null) {
			saveProject();
		}
	}

	private void newProject() {
		String name = JOptionPane.showInputDialog(this, "Enter Project Name:", "New Project");
		if (name != null && !name.trim().isEmpty()) {
			rootCollection = new PostmanCollection(name);
			treeModel.setRoot(rootCollection);
			currentProjectFile = null;
			currentNode = null;
			nodeConfigPanel.loadNode(null);
			updateTitle();
			treeModel.reload();
		}
	}

	private void toggleTheme() {
		String currentTheme = recentProjectsManager.getThemePreference();
		boolean isDark = "dark".equals(currentTheme);

		try {
			// Capture expansion state
			java.util.Enumeration<TreePath> expandedPaths = projectTree
					.getExpandedDescendants(new TreePath(rootCollection.getPath()));
			java.util.List<TreePath> pathsToRestore = new java.util.ArrayList<>();
			if (expandedPaths != null) {
				while (expandedPaths.hasMoreElements()) {
					pathsToRestore.add(expandedPaths.nextElement());
				}
			}

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

			// Restore expansion state
			for (TreePath path : pathsToRestore) {
				projectTree.expandPath(path);
			}

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
				PostmanNode draggedNode = findNodeById(rootCollection, nodeId);

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

				PostmanNode currentParent = (PostmanNode) draggedNode.getParent();
				int currentIndex = currentParent.getIndex(draggedNode);

				// Perform move
				treeModel.removeNodeFromParent(draggedNode);

				int index = dl.getChildIndex();
				if (index == -1) {
					index = targetParent.getChildCount();
				}

				// If moving within the same parent and moving down (source index < target
				// index),
				// we need to decrement the target index because removing the node shifted
				// subsequent indices.
				if (currentParent == targetParent && currentIndex < index) {
					index--;
				}

				treeModel.insertNodeInto(draggedNode, targetParent, index);

				// Expand target and select moved node
				projectTree.expandPath(destPath);
				projectTree.setSelectionPath(new TreePath(draggedNode.getPath()));

				// Autosave
				// autoSaveProject();

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

		// Configure SLF4J Simple
		System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
		System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");

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
		SwingUtilities.invokeLater(() -> {
			try {
				PostmanApp app = new PostmanApp();
				app.setExtendedState(JFrame.MAXIMIZED_BOTH);
				app.setVisible(true);

				// Set divider location after window is fully sized
				app.addComponentListener(new java.awt.event.ComponentAdapter() {
					@Override
					public void componentResized(java.awt.event.ComponentEvent e) {
						SwingUtilities.invokeLater(() -> {
							int height = app.verticalSplitPane.getHeight();
							if (height > 0) {
								app.verticalSplitPane.setDividerLocation((int) (height * 0.67));
								app.removeComponentListener(this); // Only do this once
							}
						});
					}
				});

				app.toFront();
				app.requestFocus();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
	}

	private void initConsole() {
		java.io.PrintStream originalOut = System.out;
		java.io.PrintStream originalErr = System.err;

		System.setOut(new java.io.PrintStream(new ConsoleOutputStream(consoleTextArea, originalOut)));
		System.setErr(new java.io.PrintStream(new ConsoleOutputStream(consoleTextArea, originalErr)));
	}

	private void toggleConsole() {
		boolean isVisible = consoleTabbedPane.isVisible();
		consoleTabbedPane.setVisible(!isVisible);
		if (!isVisible) {
			verticalSplitPane.setDividerLocation(0.67);
		}
	}

	private void clearConsole() {
		consoleTextArea.setText("");
	}

	private void initGlobalShortcuts() {
		javax.swing.JRootPane rootPane = getRootPane();
		javax.swing.InputMap inputMap = rootPane.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
		javax.swing.ActionMap actionMap = rootPane.getActionMap();

		javax.swing.KeyStroke ctrlS = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S,
				java.awt.event.InputEvent.CTRL_DOWN_MASK);
		inputMap.put(ctrlS, "saveProject");
		actionMap.put("saveProject", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				saveProject();
			}
		});

		// Trigger execution with Ctrl+Enter
		javax.swing.KeyStroke ctrlEnter = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER,
				java.awt.event.InputEvent.CTRL_DOWN_MASK);
		inputMap.put(ctrlEnter, "sendRequest");
		actionMap.put("sendRequest", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				sendRequest();
			}
		});
	}

	private void triggerSaveFeedback() {
		final javax.swing.JPanel glassPane = new javax.swing.JPanel() {
			@Override
			protected void paintComponent(java.awt.Graphics g) {
				g.setColor(getBackground());
				g.fillRect(0, 0, getWidth(), getHeight());
				super.paintComponent(g);
			}
		};

		// Dark Grey with transparency
		glassPane.setBackground(new java.awt.Color(64, 64, 64, 100));
		glassPane.setOpaque(false);

		setGlassPane(glassPane);
		glassPane.setVisible(true);

		javax.swing.Timer timer = new javax.swing.Timer(200, e -> {
			glassPane.setVisible(false);
		});
		timer.setRepeats(false);
		timer.start();
	}

	private class ConsoleOutputStream extends java.io.OutputStream {
		private final JTextArea textArea;
		private final java.io.OutputStream target;

		public ConsoleOutputStream(JTextArea textArea, java.io.OutputStream target) {
			this.textArea = textArea;
			this.target = target;
		}

		@Override
		public void write(int b) throws java.io.IOException {
			target.write(b);
			SwingUtilities.invokeLater(() -> {
				textArea.append(String.valueOf((char) b));
				textArea.setCaretPosition(textArea.getDocument().getLength());
			});
		}

		@Override
		public void write(byte[] b, int off, int len) throws java.io.IOException {
			target.write(b, off, len);
			String s = new String(b, off, len);
			SwingUtilities.invokeLater(() -> {
				textArea.append(s);
				textArea.setCaretPosition(textArea.getDocument().getLength());
			});
		}

		@Override
		public void flush() throws java.io.IOException {
			target.flush();
		}
	}

	private void openLogFile() {
		if (currentProjectFile == null) {
			JOptionPane.showMessageDialog(this, "No project saved. Log file is not available.");
			return;
		}

		File logFile = new File(currentProjectFile.getParentFile(), currentProjectFile.getName() + ".log");
		if (!logFile.exists()) {
			JOptionPane.showMessageDialog(this, "Log file does not exist yet.\nExecute a request to generate it.");
			return;
		}

		try {
			Desktop.getDesktop().open(logFile);
		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Failed to open log file: " + e.getMessage());
		}
	}

	private void captureConsoleLog() {
		try {
			int endOffset = consoleTextArea.getDocument().getLength();
			// Careful with length, ensure non-negative
			if (endOffset > consoleStartOffset) {
				lastExecutionConsoleLog = consoleTextArea.getText(consoleStartOffset, endOffset - consoleStartOffset);
			} else {
				lastExecutionConsoleLog = "";
			}
		} catch (Exception ex) {
			ex.printStackTrace();
			lastExecutionConsoleLog = "";
		}
	}

	private void logExecution(PostmanRequest req, Map<String, String> requestHeaders, String requestBody,
			HttpResponse<String> response, Exception exception) {
		if (currentProjectFile == null) {
			return; // Cannot log if project is not saved
		}

		File logFile = new File(currentProjectFile.getParentFile(), currentProjectFile.getName() + ".log");

		try (PrintWriter writer = new PrintWriter(new FileWriter(logFile, true))) {
			DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

			writer.println("================================================================================");
			writer.println("Execution Date: " + LocalDateTime.now().format(dtf));
			writer.println("Request: " + req.getMethod() + " " + req.getUrl());
			writer.println("Request Headers:");
			if (requestHeaders != null) {
				requestHeaders.forEach((k, v) -> writer.println("  " + k + ": " + v));
			}
			writer.println();
			writer.println("Request Body:");
			if (requestBody != null && !requestBody.isEmpty()) {
				writer.println(requestBody);
			} else {
				writer.println("(empty)");
			}
			writer.println();

			if (response != null) {
				writer.println("Response Status: " + response.statusCode());
				writer.println("Response Headers:");
				response.headers().map().forEach((k, v) -> writer.println("  " + k + ": " + v));
				writer.println();
				writer.println("Response Body:");
				String body = response.body();
				if (body != null && !body.isEmpty()) {
					writer.println(body);
				} else {
					writer.println("(empty)");
				}
			} else if (exception != null) {
				writer.println("Execution Failed:");
				exception.printStackTrace(writer);
			}

			writer.println("================================================================================");
			writer.println();

		} catch (Exception e) {
			System.err.println("Failed to write to log file: " + e.getMessage());
			e.printStackTrace();
		}
	}
}
