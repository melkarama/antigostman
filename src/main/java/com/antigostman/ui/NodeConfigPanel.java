package com.antigostman.ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JToolBar;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.fife.ui.autocomplete.AutoCompletion;
import org.fife.ui.autocomplete.BasicCompletion;
import org.fife.ui.autocomplete.DefaultCompletionProvider;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import com.antigostman.model.PostmanNode;
import com.antigostman.model.PostmanRequest;
import com.antigostman.service.RecentProjectsManager;

/**
 * Tabbed panel for configuring nodes (Collection, Folder, Request).
 * 
 * Common tabs for all nodes: - Environment: Text area for environment variables
 * in properties format - Headers: Text area for headers in properties format -
 * Prescript: Script to run before request execution - Postscript: Script to run
 * after request execution
 * 
 * Request-only tab: - Execution: Params (top) and Response (bottom)
 */
public class NodeConfigPanel extends JPanel {

	private JTabbedPane tabbedPane;
	private static final String INDICATOR_HTML = " <font color='#FF8C00'>●</font>";
	private static final String INDICATOR_RAW = "●";

	// Common tabs
	private RSyntaxTextArea environmentArea;
	private RSyntaxTextArea headersArea;
	private RSyntaxTextArea prescriptArea;
	private RSyntaxTextArea postscriptArea;
	private RSyntaxTextArea globalVarsArea; // For root node only
	private JTextArea emailToArea; // For root collection settings
	private JTextArea emailCcArea; // For root collection settings

	// Request-only tabs
	private RSyntaxTextArea bodyArea;
	private JTextArea responseArea;
	// private JPanel executionPanel; // Removed
	private JTabbedPane executionTabbedPane;
	private JSplitPane mainSplitPane;
	private JPanel executionResultsPanel;
	private RSyntaxTextArea requestHeadersArea;
	private RSyntaxTextArea requestBodyArea;
	private RSyntaxTextArea responseHeadersArea;
	private RSyntaxTextArea responseBodyArea;

	private PostmanNode currentNode;
	private RecentProjectsManager recentProjectsManager;
	private Runnable pdfGenerator;
	private Runnable pdfEmailSender;
	private Runnable onExecuteRequest;
	private boolean isLoading = false;
	
	public void setOnExecuteRequest(Runnable onExecuteRequest) {
		this.onExecuteRequest = onExecuteRequest;
	}
	
	public void setPdfGenerator(Runnable pdfGenerator) {
		this.pdfGenerator = pdfGenerator;
		// Re-create execution tabs to include the PDF button
		if (executionResultsPanel != null) {
			executionResultsPanel.removeAll();
			createExecutionResultsPanel();
			executionResultsPanel.revalidate();
			executionResultsPanel.repaint();
		}
	}
	
	public void setPdfEmailSender(Runnable pdfEmailSender) {
		this.pdfEmailSender = pdfEmailSender;
		// Re-create execution tabs to include the email button
		if (executionResultsPanel != null) {
			executionResultsPanel.removeAll();
			createExecutionResultsPanel();
			executionResultsPanel.revalidate();
			executionResultsPanel.repaint();
		}
	}

	public void setRecentProjectsManager(RecentProjectsManager recentProjectsManager) {
		this.recentProjectsManager = recentProjectsManager;
	}

	// Map to store caret positions per node (nodeId -> areaKey -> caretPos)
	private final Map<String, Map<String, Integer>> nodeCaretMap = new LinkedHashMap<>();

	public NodeConfigPanel() {
		setLayout(new BorderLayout());

		mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		mainSplitPane.setResizeWeight(0.7); // 70% config, 30% results

		tabbedPane = new JTabbedPane();

		// Tab 1: Environment
		environmentArea = createPropertiesEditor();
		tabbedPane.addTab("Environment", new RTextScrollPane(environmentArea));

		// Tab 2: Headers
		headersArea = createPropertiesEditor();
		tabbedPane.addTab("Headers", new RTextScrollPane(headersArea));

		// Tab 3: Prescript
		prescriptArea = createCodeEditor();
		tabbedPane.addTab("Prescript", new RTextScrollPane(prescriptArea));

		// Tab 4: Postscript
		postscriptArea = createCodeEditor();
		tabbedPane.addTab("Postscript", new RTextScrollPane(postscriptArea));

		// Tab 6: Global Variables (initially hidden, added dynamically)
		globalVarsArea = createPropertiesEditor();

		// Body Editor
		bodyArea = createBodyEditor();

		// Bottom: Execution Results
		// Initially created without PDF button (generator is null). 
		// Will be recreated when setPdfGenerator is called.
		createExecutionResultsPanel();

		// Add indicator listeners
		addIndicatorListener(environmentArea, "Environment");
		addIndicatorListener(headersArea, "Headers");
		addIndicatorListener(prescriptArea, "Prescript");
		addIndicatorListener(postscriptArea, "Postscript");
		addIndicatorListener(globalVarsArea, "Global Variables");
		addIndicatorListener(bodyArea, "Body");

		// Add listener to track tab selection changes
		tabbedPane.addChangeListener(e -> {
			if (currentNode != null) {
				currentNode.setSelectedTabIndex(tabbedPane.getSelectedIndex());
			}
		});

		mainSplitPane.setTopComponent(tabbedPane);
		mainSplitPane.setBottomComponent(executionResultsPanel);

		add(mainSplitPane, BorderLayout.CENTER);
	}

	private RSyntaxTextArea createPropertiesEditor() {
		RSyntaxTextArea area = new RSyntaxTextArea();
		area.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
		area.setFont(new Font("Monospaced", Font.PLAIN, 12));
		area.setTabSize(2);
		area.setCodeFoldingEnabled(true);
		area.setAntiAliasingEnabled(true);
		// Document listener to trigger autosave on change
		area.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				saveNode();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				saveNode();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				saveNode();
			}
		});
		bindExecutionShortcut(area);
		return area;
	}

	private void bindExecutionShortcut(javax.swing.JComponent component) {
		component.getInputMap().put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK), "executeRequest");
		component.getActionMap().put("executeRequest", new javax.swing.AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent e) {
				if (onExecuteRequest != null) {
					onExecuteRequest.run();
				}
			}
		});
	}

	/**
	 * Save caret positions for the given node ID.
	 */
	private void saveCaretPositions(String nodeId) {
		Map<String, Integer> caretMap = new LinkedHashMap<>();
		caretMap.put("env", environmentArea.getCaretPosition());
		caretMap.put("headers", headersArea.getCaretPosition());
		caretMap.put("prescript", prescriptArea.getCaretPosition());
		caretMap.put("postscript", postscriptArea.getCaretPosition());
		if (globalVarsArea != null && globalVarsArea.isShowing()) {
			caretMap.put("globalVars", globalVarsArea.getCaretPosition());
		}
		if (bodyArea != null) {
			caretMap.put("body", bodyArea.getCaretPosition());
		}
		nodeCaretMap.put(nodeId, caretMap);
	}

	/**
	 * Restore caret positions for the given node ID if previously saved.
	 */
	private void restoreCaretPositions(String nodeId) {
		Map<String, Integer> caretMap = nodeCaretMap.get(nodeId);
		if (caretMap == null) {
			return;
		}
		// Helper to safely set caret within document bounds
		java.util.function.BiConsumer<RSyntaxTextArea, Integer> safeSet = (area, pos) -> {
			if (area == null) {
				return;
			}
			int length = area.getDocument().getLength();
			int safePos = Math.min(pos, length);
			area.setCaretPosition(safePos);
		};
		safeSet.accept(environmentArea, caretMap.getOrDefault("env", 0));
		safeSet.accept(headersArea, caretMap.getOrDefault("headers", 0));
		safeSet.accept(prescriptArea, caretMap.getOrDefault("prescript", 0));
		safeSet.accept(postscriptArea, caretMap.getOrDefault("postscript", 0));
		if (globalVarsArea != null && globalVarsArea.isShowing()) {
			safeSet.accept(globalVarsArea, caretMap.getOrDefault("globalVars", 0));
		}
		if (bodyArea != null) {
			safeSet.accept(bodyArea, caretMap.getOrDefault("body", 0));
		}
	}

	private RSyntaxTextArea createCodeEditor() {
		RSyntaxTextArea textArea = new RSyntaxTextArea();
		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
		textArea.setCodeFoldingEnabled(true);
		textArea.setAntiAliasingEnabled(true);
		textArea.setTabSize(2);

		// Auto completion
		DefaultCompletionProvider provider = createCompletionProvider();
		AutoCompletion ac = new AutoCompletion(provider);
		ac.install(textArea);

		// Focus listener for autosave
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				updateCompletions(textArea, provider);
				saveNode();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				updateCompletions(textArea, provider);
				saveNode();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				updateCompletions(textArea, provider);
				saveNode();
			}
		});

		bindExecutionShortcut(textArea);
		return textArea;
	}

	private DefaultCompletionProvider createCompletionProvider() {
		DefaultCompletionProvider provider = new DefaultCompletionProvider();

		// Add basic JavaScript keywords and Postman-specific objects
		addBaseCompletions(provider);

		return provider;
	}

	private void addBaseCompletions(DefaultCompletionProvider provider) {
		provider.addCompletion(new BasicCompletion(provider, "console.log"));
		provider.addCompletion(new BasicCompletion(provider, "pm.environment.get"));
		provider.addCompletion(new BasicCompletion(provider, "pm.environment.set"));
		provider.addCompletion(new BasicCompletion(provider, "pm.response.json"));
		provider.addCompletion(new BasicCompletion(provider, "pm.test"));
		provider.addCompletion(new BasicCompletion(provider, "pm.expect"));
		provider.addCompletion(new BasicCompletion(provider, "function"));
		provider.addCompletion(new BasicCompletion(provider, "var"));
		provider.addCompletion(new BasicCompletion(provider, "let"));
		provider.addCompletion(new BasicCompletion(provider, "const"));
		provider.addCompletion(new BasicCompletion(provider, "if"));
		provider.addCompletion(new BasicCompletion(provider, "else"));
		provider.addCompletion(new BasicCompletion(provider, "for"));
		provider.addCompletion(new BasicCompletion(provider, "return"));
	}

	private void updateCompletions(RSyntaxTextArea textArea, DefaultCompletionProvider provider) {
		SwingUtilities.invokeLater(() -> {
			String text = textArea.getText();
			Set<String> foundWords = new HashSet<>();

			// Regex to find variable and function declarations
			// Matches: var x, let y, const z, function f
			Pattern pattern = Pattern.compile("\\b(var|let|const|function)\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)");
			Matcher matcher = pattern.matcher(text);

			while (matcher.find()) {
				foundWords.add(matcher.group(2));
			}

			// Rebuild completions
			provider.clear();
			addBaseCompletions(provider);

			for (String word : foundWords) {
				provider.addCompletion(new BasicCompletion(provider, word));
			}
		});
	}

	private RSyntaxTextArea createBodyEditor() {
		RSyntaxTextArea textArea = new RSyntaxTextArea();
		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
		textArea.setCodeFoldingEnabled(true);
		textArea.setAntiAliasingEnabled(true);
		textArea.setTabSize(2);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);

		// Focus listener for autosave
		textArea.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				saveNode();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				saveNode();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				saveNode();
			}
		});
		bindExecutionShortcut(textArea);
		return textArea;
	}

	private void addIndicatorListener(JTextArea area, String title) {
		area.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				check();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				check();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				check();
			}

			private void check() {
				updateTabIndicator(title, area.getText().trim().length() > 0);
			}
		});
	}

	private void updateTabIndicator(String baseTitle, boolean hasContent) {
		for (int i = 0; i < tabbedPane.getTabCount(); i++) {
			String currentPlain = getPlainTitleAt(i);
			if (currentPlain.equals(baseTitle)) {
				String newTitle = baseTitle;
				if (hasContent) {
					newTitle = "<html>" + baseTitle + INDICATOR_HTML + "</html>";
				}
				if (!tabbedPane.getTitleAt(i).equals(newTitle)) {
					tabbedPane.setTitleAt(i, newTitle);
				}
				return;
			}
		}
	}

	private String getPlainTitleAt(int index) {
		String title = tabbedPane.getTitleAt(index);
		if (title.startsWith("<html>")) {
			return title.replaceAll("\\<.*?\\>", "").replace(INDICATOR_RAW, "").trim();
		}
		return title;
	}

	private void updateAllIndicators() {
		updateTabIndicator("Environment", environmentArea.getText().trim().length() > 0);
		updateTabIndicator("Headers", headersArea.getText().trim().length() > 0);
		updateTabIndicator("Prescript", prescriptArea.getText().trim().length() > 0);
		updateTabIndicator("Postscript", postscriptArea.getText().trim().length() > 0);
		if (globalVarsArea != null) {
			updateTabIndicator("Global Variables", globalVarsArea.getText().trim().length() > 0);
		}
		if (bodyArea != null) {
			updateTabIndicator("Body", bodyArea.getText().trim().length() > 0);
		}
	}

	private void createExecutionResultsPanel() {
		executionResultsPanel = new JPanel(new BorderLayout());
		
		executionTabbedPane = new JTabbedPane();
		executionTabbedPane.addChangeListener(e -> {
			if (currentNode instanceof PostmanRequest) {
				((PostmanRequest) currentNode).setExecutionTabIndex(executionTabbedPane.getSelectedIndex());
			}
		});

		// Tab 1: Request Headers
		requestHeadersArea = createReadOnlySyntaxTextArea();
		requestHeadersArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
		executionTabbedPane.addTab("Request Headers", createTabWithToolbar(requestHeadersArea, null));

		// Tab 2: Request Body
		requestBodyArea = createReadOnlySyntaxTextArea();
		executionTabbedPane.addTab("Request Body", createTabWithToolbar(requestBodyArea, null));

		// Tab 3: Response Headers
		responseHeadersArea = createReadOnlySyntaxTextArea();
		responseHeadersArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE);
		executionTabbedPane.addTab("Response Headers", createTabWithToolbar(responseHeadersArea, null));

		// Tab 4: Response Body
		responseBodyArea = createReadOnlySyntaxTextArea();
		executionTabbedPane.addTab("Response Body",
				createTabWithToolbar(responseBodyArea, () -> setResponseBodySyntax(SyntaxConstants.SYNTAX_STYLE_NONE)));

		// Keep reference to old responseArea for backward compatibility
		responseArea = responseBodyArea;

		executionResultsPanel.add(executionTabbedPane, BorderLayout.CENTER);
	}

	private RSyntaxTextArea createReadOnlySyntaxTextArea() {
		RSyntaxTextArea textArea = new RSyntaxTextArea();
		textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_NONE);
		textArea.setCodeFoldingEnabled(true);
		textArea.setAntiAliasingEnabled(true);
		textArea.setTabSize(2);
		textArea.setEditable(false);
		textArea.setLineWrap(true);
		textArea.setWrapStyleWord(true);
		return textArea;
	}

	/**
	 * Load node data into the UI.
	 */
	public void loadNode(PostmanNode node) {
		this.isLoading = true;
		try {
			this.currentNode = node;

			if (node == null) {
				clearAll();
				return;
			}

			// Load environment (convert map to properties format)
			environmentArea.setText(mapToProperties(node.getEnvironment()));

			// Load headers (convert map to properties format)
			headersArea.setText(mapToProperties(node.getHeaders()));

			// Restore caret positions for this node
			restoreCaretPositions(node.getId());

			// Load scripts
			prescriptArea.setText(node.getPrescript() != null ? node.getPrescript() : "");
			postscriptArea.setText(node.getPostscript() != null ? node.getPostscript() : "");

			// Handle Global Variables tab for root node
			if (node instanceof com.antigostman.model.PostmanCollection && node.getParent() == null) {
				// It's the root collection
				// It's the root collection
				boolean tabExists = false;
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					if ("Global Variables".equals(getPlainTitleAt(i))) {
						tabExists = true;
						break;
					}
				}

				if (!tabExists) {
					tabbedPane.addTab("Global Variables", new RTextScrollPane(globalVarsArea));
				}
				com.antigostman.model.PostmanCollection col = (com.antigostman.model.PostmanCollection) node;
				globalVarsArea.setText(mapToProperties(col.getGlobalVariables()));
				
				// Handle Settings tab for root node
				boolean settingsTabExists = false;
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					if ("Settings".equals(tabbedPane.getTitleAt(i))) {
						settingsTabExists = true;
						break;
					}
				}
				
				if (!settingsTabExists) {
					// Create Settings panel
					JPanel settingsPanel = new JPanel(new BorderLayout(5, 5));
					settingsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
					
					JPanel formPanel = new JPanel(new GridBagLayout());
					GridBagConstraints gbc = new GridBagConstraints();
					gbc.fill = GridBagConstraints.HORIZONTAL;
					gbc.insets = new Insets(5, 5, 5, 5);
					gbc.weightx = 0.0;
					gbc.gridx = 0;
					gbc.gridy = 0;
					
					// Email To
					formPanel.add(new JLabel("Email Report To:"), gbc);
					gbc.gridx = 1;
					gbc.weightx = 1.0;
					gbc.fill = GridBagConstraints.BOTH;
					gbc.weighty = 0.3;
					emailToArea = new JTextArea(3, 40);
					emailToArea.setLineWrap(true);
					emailToArea.setWrapStyleWord(true);
					bindExecutionShortcut(emailToArea);
					formPanel.add(new JScrollPane(emailToArea), gbc);
					
					// Email CC
					gbc.gridx = 0;
					gbc.gridy = 1;
					gbc.weightx = 0.0;
					gbc.weighty = 0.0;
					gbc.fill = GridBagConstraints.HORIZONTAL;
					formPanel.add(new JLabel("Email Report CC:"), gbc);
					gbc.gridx = 1;
					gbc.weightx = 1.0;
					gbc.fill = GridBagConstraints.BOTH;
					gbc.weighty = 0.3;
					emailCcArea = new JTextArea(3, 40);
					emailCcArea.setLineWrap(true);
					emailCcArea.setWrapStyleWord(true);
					bindExecutionShortcut(emailCcArea);
					formPanel.add(new JScrollPane(emailCcArea), gbc);
					
					settingsPanel.add(formPanel, BorderLayout.NORTH);
					
					tabbedPane.addTab("Settings", settingsPanel);
				}
				
				// Load email settings
				emailToArea.setText(col.getEmailReportTo() != null ? col.getEmailReportTo() : "");
				emailCcArea.setText(col.getEmailReportCc() != null ? col.getEmailReportCc() : "");
			} else {
				// Remove Global Variables tab if present
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					if ("Global Variables".equals(getPlainTitleAt(i))) {
						tabbedPane.removeTabAt(i);
						break;
					}
				}
				// Remove Settings tab if present
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					if ("Settings".equals(tabbedPane.getTitleAt(i))) {
						tabbedPane.removeTabAt(i);
						break;
					}
				}
			}

			// Show/hide Body tab and Execution Panel based on node type
			if (node instanceof PostmanRequest) {
				// ADD Body Tab if NOT present
				boolean bodyTabExists = false;
				for(int i=0; i<tabbedPane.getTabCount(); i++) {
					if("Body".equals(getPlainTitleAt(i))) {
						bodyTabExists = true;
						break;
					}
				}
				if(!bodyTabExists) {
					// Insert Body tab after Headers (index 2)
					// Env=0, Headers=1, Body=2
					// But Headers is index 1.
					// Insert at index 2
					if(tabbedPane.getTabCount() >= 2) {
						tabbedPane.insertTab("Body", null, new RTextScrollPane(bodyArea), null, 2);
					} else {
						tabbedPane.addTab("Body", new RTextScrollPane(bodyArea));
					}
				}

				// Show Execution Results Panel
				executionResultsPanel.setVisible(true);
				mainSplitPane.setDividerLocation(0.7); // Reset divider
				mainSplitPane.setBottomComponent(executionResultsPanel);
				mainSplitPane.setDividerSize(10); // Restore divider

				PostmanRequest request = (PostmanRequest) node;
				bodyArea.setText(request.getBody() != null ? request.getBody() : "");
				setBodySyntax(request.getBodyType());

				// Clear execution tabs
				if (requestHeadersArea != null) {
					requestHeadersArea.setText("");
				}
				if (requestBodyArea != null) {
					requestBodyArea.setText("");
				}
				if (responseHeadersArea != null) {
					responseHeadersArea.setText("");
				}
				if (responseBodyArea != null) {
					responseBodyArea.setText("");
				}

				// Restore execution tab index
				if (executionTabbedPane != null) {
					int execTabIndex = request.getExecutionTabIndex();
					if (execTabIndex >= 0 && execTabIndex < executionTabbedPane.getTabCount()) {
						executionTabbedPane.setSelectedIndex(execTabIndex);
					} else {
						executionTabbedPane.setSelectedIndex(0);
					}
				}
			} else {
				// Remove Body tab if present
				for (int i = 0; i < tabbedPane.getTabCount(); i++) {
					if ("Body".equals(getPlainTitleAt(i))) {
						tabbedPane.removeTabAt(i);
						break;
					}
				}
				
				// Hide Execution Results Panel
				executionResultsPanel.setVisible(false);
				mainSplitPane.setBottomComponent(null);
				mainSplitPane.setDividerSize(0); // Hide divider
				
			}

			// Restore selected tab index
			int savedIndex = node.getSelectedTabIndex();
			if (savedIndex >= 0 && savedIndex < tabbedPane.getTabCount()) {
				tabbedPane.setSelectedIndex(savedIndex);
			} else {
				tabbedPane.setSelectedIndex(0);
			}

			updateAllIndicators();
		} finally {
			this.isLoading = false;
		}
	}

	/**
	 * Save UI data back to the node.
	 */
	public void saveNode() {
		if (isLoading || currentNode == null) {
			return;
		}

		// Save environment (convert properties format to map)
		currentNode.setEnvironment(propertiesToMap(environmentArea.getText()));

		// Save headers (convert properties format to map)
		currentNode.setHeaders(propertiesToMap(headersArea.getText()));

		// Save caret positions for this node
		if (currentNode != null) {
			saveCaretPositions(currentNode.getId());
		}

		// Save scripts
		currentNode.setPrescript(prescriptArea.getText());
		currentNode.setPostscript(postscriptArea.getText());

		// Save global variables if root
		if (currentNode instanceof com.antigostman.model.PostmanCollection && currentNode.getParent() == null) {
			com.antigostman.model.PostmanCollection col = (com.antigostman.model.PostmanCollection) currentNode;
			Map<String, String> vars = propertiesToMap(globalVarsArea.getText());
			col.setGlobalVariables(vars);
			
			// Save email settings
			if (emailToArea != null) {
				col.setEmailReportTo(emailToArea.getText());
			}
			if (emailCcArea != null) {
				col.setEmailReportCc(emailCcArea.getText());
			}
		}

		// Save request-specific data
		if (currentNode instanceof PostmanRequest) {
			PostmanRequest request = (PostmanRequest) currentNode;
			request.setBody(bodyArea.getText());

			// Save execution tab index
			if (executionTabbedPane != null) {
				request.setExecutionTabIndex(executionTabbedPane.getSelectedIndex());
			}
		}

		// Save selected tab index
		currentNode.setSelectedTabIndex(tabbedPane.getSelectedIndex());
	}

	/**
	 * Get the response area for displaying HTTP responses.
	 */
	public JTextArea getResponseArea() {
		return responseArea;
	}

	/**
	 * Get the execution tabbed pane.
	 */
	public JTabbedPane getExecutionTabbedPane() {
		return executionTabbedPane;
	}

	/**
	 * Set request headers for display.
	 */
	public void setRequestHeaders(String headers) {
		if (requestHeadersArea != null) {
			requestHeadersArea.setText(headers);
		}
	}

	/**
	 * Set request body for display.
	 */
	public void setRequestBody(String body) {
		if (requestBodyArea != null) {
			requestBodyArea.setText(body);
		}
	}

	/**
	 * Set response headers for display.
	 */
	public void setResponseHeaders(String headers) {
		if (responseHeadersArea != null) {
			responseHeadersArea.setText(headers);
		}
	}

	/**
	 * Set response body for display.
	 */
	public void setResponseBody(String body) {
		if (responseBodyArea != null) {
			responseBodyArea.setText(body);
		}
	}

	/**
	 * Select the Execution tab if it exists.
	 */
	/**
	 * Select the Execution tab if it exists.
	 */
	public void selectExecutionTab() {
		// New behavior: Select the "Response Body" tab in the bottom panel
		if (executionTabbedPane != null) {
			int count = executionTabbedPane.getTabCount();
			for(int i=0; i<count; i++) {
				if("Response Body".equals(executionTabbedPane.getTitleAt(i))) {
					executionTabbedPane.setSelectedIndex(i);
					break;
				}
			}
		}
	}

	/**
	 * Clear all fields.
	 */
	private void clearAll() {
		environmentArea.setText("");
		headersArea.setText("");
		prescriptArea.setText("");
		postscriptArea.setText("");
		globalVarsArea.setText("");
		bodyArea.setText("");
		responseArea.setText("");
	}

	/**
	 * Convert map to properties format (key=value\n).
	 */
	private String mapToProperties(Map<String, String> map) {
		if (map == null || map.isEmpty()) {
			return "";
		}

		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, String> entry : map.entrySet()) {
			sb.append(entry.getKey());
			String value = entry.getValue();
			if (value != null) {
				sb.append("=").append(value);
			}
			sb.append("\n");
		}
		return sb.toString();
	}

	/**
	 * Convert properties format to map.
	 */
	private Map<String, String> propertiesToMap(String text) {
		Map<String, String> map = new LinkedHashMap<>();

		if (text == null || text.trim().isEmpty()) {
			return map;
		}

		String[] lines = text.split("\n");
		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#")) {
				continue; // Skip empty lines and comments
			}

			int equalsIndex = line.indexOf('=');
			if (equalsIndex >= 0) {
				String key = line.substring(0, equalsIndex).trim();
				String value = line.substring(equalsIndex + 1).trim();
				map.put(key, value);
			} else {
				// No equals sign, treat as key with null value (to preserve it)
				map.put(line, null);
			}
		}

		return map;
	}

	public void setBodySyntax(String bodyType) {
		if (bodyArea == null) {
			return;
		}

		if (bodyType == null) {
			bodyType = "TEXT";
		}

		String style = SyntaxConstants.SYNTAX_STYLE_NONE;
		switch (bodyType.toUpperCase()) {
		case "JSON":
			style = SyntaxConstants.SYNTAX_STYLE_JSON;
			break;
		case "XML":
			style = SyntaxConstants.SYNTAX_STYLE_XML;
			break;
		case "FORM ENCODED":
			style = SyntaxConstants.SYNTAX_STYLE_PROPERTIES_FILE;
			break;
		case "TEXT":
		default:
			style = SyntaxConstants.SYNTAX_STYLE_NONE;
			break;
		}

		bodyArea.setSyntaxEditingStyle(style);
		if (requestBodyArea != null) {
			requestBodyArea.setSyntaxEditingStyle(style);
		}
	}

	public void setResponseBodySyntax(String style) {
		if (responseBodyArea != null) {
			responseBodyArea.setSyntaxEditingStyle(style);
		}
	}

	public String getResponseBody() {
		return responseBodyArea != null ? responseBodyArea.getText() : "";
	}

	public String getResponseHeaders() {
		return responseHeadersArea != null ? responseHeadersArea.getText() : "";
	}

	private JPanel createTabWithToolbar(RSyntaxTextArea textArea, Runnable onClear) {
		JPanel panel = new JPanel(new BorderLayout());
		JToolBar toolbar = new JToolBar();
		toolbar.setFloatable(false);

		JButton saveButton = new JButton("Save");
		JButton copyButton = new JButton("Copy to Clipboard");
		JButton clearButton = new JButton("Clear");
		JCheckBox openAfterSave = new JCheckBox("Open after save");

		saveButton.addActionListener(e -> saveContent(textArea, openAfterSave.isSelected()));

		copyButton.addActionListener(e -> {
			textArea.selectAll();
			textArea.copy();
			textArea.setCaretPosition(0);
		});

		clearButton.addActionListener(e -> {
			textArea.setText("");
			if (onClear != null) {
				onClear.run();
			}
		});

		toolbar.add(copyButton);
		toolbar.add(clearButton);
		toolbar.add(saveButton);
		toolbar.add(openAfterSave);
		
		if (this.pdfGenerator != null) {
			JButton pdfButton = new JButton("PDF");
			pdfButton.setToolTipText("Generate PDF Report");
			pdfButton.addActionListener(e -> this.pdfGenerator.run());
			toolbar.add(pdfButton);
			
			JButton emailButton = new JButton("Email PDF");
			emailButton.setToolTipText("Send PDF Report via Email");
			emailButton.addActionListener(e -> {
				if (this.pdfEmailSender != null) {
					this.pdfEmailSender.run();
				}
			});
			toolbar.add(emailButton);
		}

		panel.add(toolbar, BorderLayout.NORTH);
		panel.add(new RTextScrollPane(textArea), BorderLayout.CENTER);

		return panel;
	}

	private void saveContent(RSyntaxTextArea textArea, boolean openAfterSave) {
		JFileChooser fileChooser = new JFileChooser();
		if (recentProjectsManager != null) {
			String lastDir = recentProjectsManager.getLastSaveDirectory();
			if (lastDir != null) {
				fileChooser.setCurrentDirectory(new File(lastDir));
			}
		}
		
		// Generate smart filename if this is the response body
		if (textArea == responseBodyArea) {
			String timestamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
			String extension = detectFileExtension(textArea.getText(), textArea.getSyntaxEditingStyle());
			String suggestedName = timestamp + extension;
			fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), suggestedName));
		}

		if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
			File file = fileChooser.getSelectedFile();
			try {
				// Save file
				java.nio.file.Files.writeString(file.toPath(), textArea.getText());

				// Save directory
				if (recentProjectsManager != null) {
					recentProjectsManager.setLastSaveDirectory(file.getParent());
				}

				// Open if requested
				if (openAfterSave) {
					java.awt.Desktop.getDesktop().open(file);
				}
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage());
			}
		}
	}
	
	private String detectFileExtension(String content, String syntaxStyle) {
		// Check syntax highlighting style first
		if (syntaxStyle != null) {
			if (syntaxStyle.contains("json")) {
				return ".json";
			} else if (syntaxStyle.contains("xml") || syntaxStyle.contains("html")) {
				return ".xml";
			} else if (syntaxStyle.contains("javascript")) {
				return ".js";
			} else if (syntaxStyle.contains("css")) {
				return ".css";
			}
		}
		
		// Fallback: detect from content
		if (content != null && !content.trim().isEmpty()) {
			String trimmed = content.trim();
			
			// JSON detection
			if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || 
			    (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
				return ".json";
			}
			
			// XML/HTML detection
			if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
				if (trimmed.toLowerCase().contains("<!doctype html") || 
				    trimmed.toLowerCase().contains("<html")) {
					return ".html";
				}
				return ".xml";
			}
			
			// CSV detection (simple heuristic: has commas on most lines)
			String[] lines = trimmed.split("\n");
			if (lines.length > 1) {
				int commaLines = 0;
				for (String line : lines) {
					if (line.contains(",")) commaLines++;
				}
				if (commaLines > lines.length / 2) {
					return ".csv";
				}
			}
		}
		
		// Default to .txt
		return ".txt";
	}
}
