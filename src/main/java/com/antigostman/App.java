package com.antigostman;

import java.awt.BorderLayout;
import java.awt.Desktop;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JSplitPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.apache.commons.lang3.StringUtils;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import com.antigostman.model.PostmanCollection;
import com.antigostman.model.PostmanFolder;
import com.antigostman.model.PostmanNode;
import com.antigostman.model.PostmanRequest;
import com.antigostman.model.postman.PostmanCollectionV2;
import com.antigostman.service.DownloadSaver;
import com.antigostman.service.HttpClientService;
import com.antigostman.service.PostmanImportService;
import com.antigostman.service.ProjectService;
import com.antigostman.service.RecentProjectsManager;
import com.antigostman.ui.ConsolePanel;
import com.antigostman.ui.MainMenuBar;
import com.antigostman.ui.NodeConfigPanel;
import com.antigostman.ui.ProjectTreePanel;
import com.antigostman.ui.RequestToolbarPanel;
import com.antigostman.utils.PropsUtils;
import com.antigostman.utils.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class App extends JFrame implements MainMenuBar.AntigostmanController, ProjectTreePanel.TreeController {

	private final HttpClientService httpClientService;
	private final ProjectService projectService;
	private final RecentProjectsManager recentProjectsManager;
	private final ObjectMapper objectMapper;

	private MainMenuBar mainMenuBar;
	private ProjectTreePanel treePanel;
	private RequestToolbarPanel toolbarPanel;
	private ConsolePanel consolePanel;
	private NodeConfigPanel nodeConfigPanel;
	private JSplitPane verticalSplitPane;

	private PostmanCollection rootCollection;
	private PostmanNode currentNode;
	private boolean isLoadingNode = false; 
	private File currentProjectFile;

	private static java.net.ServerSocket lockSocket;

	// Transient state for last execution (for PDF generation)
	private HttpResponse<String> lastExecutionResponse;
	private Exception lastExecutionException;
	private Map<String, String> lastExecutionRequestHeaders;
	private String lastExecutionRequestBody;
	private String lastExecutionConsoleLog;
	private long lastExecutionDuration;
	private int consoleStartOffset = 0;

	public App() throws KeyManagementException, NoSuchAlgorithmException {
		this.httpClientService = new HttpClientService();
		this.projectService = new ProjectService();
		this.recentProjectsManager = new RecentProjectsManager();
		this.objectMapper = new ObjectMapper();

		updateTitle();
		setSize(1200, 800);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);

		initComponents();
		initGlobalShortcuts();

		SwingUtilities.invokeLater(this::restoreWorkspace);

		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				saveAllProjects();
			}
		});
	}

	private void initComponents() {
		mainMenuBar = new MainMenuBar(this);
		setJMenuBar(mainMenuBar);

		JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
		mainSplitPane.setDividerLocation(250);

		rootCollection = new PostmanCollection("My Project");
		treePanel = new ProjectTreePanel(rootCollection, this);
		mainSplitPane.setLeftComponent(treePanel);

		JPanel rightPanel = new JPanel(new BorderLayout());
		toolbarPanel = new RequestToolbarPanel(this::sendRequest, 
				type -> nodeConfigPanel.setBodySyntax(type),
				() -> treePanel.nodeChanged(currentNode));
		toolbarPanel.setVisible(false);
		rightPanel.add(toolbarPanel, BorderLayout.NORTH);

		nodeConfigPanel = new NodeConfigPanel();
		nodeConfigPanel.setPdfGenerator(this::generatePdfReport);
		nodeConfigPanel.setPdfEmailSender(this::emailPdfReport);
		nodeConfigPanel.setRecentProjectsManager(recentProjectsManager);
		nodeConfigPanel.setOnExecuteRequest(this::sendRequest);
		rightPanel.add(nodeConfigPanel, BorderLayout.CENTER);

		mainSplitPane.setRightComponent(rightPanel);

		consolePanel = new ConsolePanel();
		verticalSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
		verticalSplitPane.setResizeWeight(0.67);
		verticalSplitPane.setTopComponent(mainSplitPane);
		verticalSplitPane.setBottomComponent(consolePanel);

		add(verticalSplitPane, BorderLayout.CENTER);
	}

	// --- MainMenuBar.AntigostmanController Implementation ---

	@Override
	public void saveProject() {
		if (currentProjectFile == null) {
			String lastDir = recentProjectsManager.getLastSaveDirectory();
			JFileChooser fileChooser = lastDir != null ? new JFileChooser(lastDir) : new JFileChooser();
			
			// Propose project name as filename
			if (rootCollection != null && rootCollection.getName() != null) {
				String suggestedName = rootCollection.getName();
				// Remove any characters that aren't valid for filenames
				suggestedName = suggestedName.replaceAll("[^a-zA-Z0-9._-]", "_");
				if (!suggestedName.toLowerCase().endsWith(".xml")) {
					suggestedName += ".xml";
				}
				fileChooser.setSelectedFile(new File(fileChooser.getCurrentDirectory(), suggestedName));
			}
			
			if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
				currentProjectFile = fileChooser.getSelectedFile();
				if (!currentProjectFile.getName().toLowerCase().endsWith(".xml")) {
					currentProjectFile = new File(currentProjectFile.getAbsolutePath() + ".xml");
				}
				
				// Remember the directory
				recentProjectsManager.setLastSaveDirectory(currentProjectFile.getParent());
				
				recentProjectsManager.addRecentProject(currentProjectFile);
				updateRecentProjectsMenu(mainMenuBar.getRecentProjectsMenu());
			} else {
				return;
			}
		}

		saveCurrentNodeState();
		try {
			projectService.saveProject(rootCollection, currentProjectFile);
		} catch (Exception e) {
			log.error("Failed to save project", e);
			JOptionPane.showMessageDialog(this, "Failed to save project: " + e.getMessage());
			return;
		}
		updateTitle();
		triggerSaveFeedback();
	}

	@Override
	public void loadProject() {
		String lastDir = recentProjectsManager.getLastSaveDirectory();
		JFileChooser fileChooser = lastDir != null ? new JFileChooser(lastDir) : new JFileChooser();
		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			// Remember the directory
			recentProjectsManager.setLastSaveDirectory(selectedFile.getParent());
			loadProjectFile(selectedFile);
		}
	}

	private void loadProjectFile(File file) {
		try {
			PostmanCollection loaded = projectService.loadProject(file);
			if (loaded != null) {
				rootCollection = loaded;
				treePanel.setRoot(rootCollection);
				currentProjectFile = file;
				recentProjectsManager.addRecentProject(file);
				updateRecentProjectsMenu(mainMenuBar.getRecentProjectsMenu());
				updateTitle();
				
				currentNode = null;
				toolbarPanel.setVisible(false);
				nodeConfigPanel.loadNode(null);

				SwingUtilities.invokeLater(() -> {
					// Expand some levels...
				});
			}
		} catch (Exception e) {
			log.error("Failed to load project", e);
			JOptionPane.showMessageDialog(this, "Failed to load project: " + e.getMessage());
		}
	}

	@Override
	public void newProject() {
		String name = JOptionPane.showInputDialog(this, "Enter Project Name:", "New Project");
		if (name != null && !name.trim().isEmpty()) {
			rootCollection = new PostmanCollection(name);
			treePanel.setRoot(rootCollection);
			currentProjectFile = null;
			updateTitle();
			
			currentNode = null;
			toolbarPanel.setVisible(false);
			nodeConfigPanel.loadNode(null);
		}
	}

	@Override
	public void importPostmanCollection() {
		String lastDir = recentProjectsManager.getLastSaveDirectory();
		JFileChooser fileChooser = lastDir != null ? new JFileChooser(lastDir) : new JFileChooser();
		if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			// Remember the directory
			recentProjectsManager.setLastSaveDirectory(selectedFile.getParent());
			try {
				PostmanCollectionV2 v2 = objectMapper.readValue(selectedFile, PostmanCollectionV2.class);
				PostmanImportService importer = new PostmanImportService();
				PostmanCollection imported = importer.convertToAntigostman(v2);
				
				rootCollection = imported;
				treePanel.setRoot(rootCollection);
				currentProjectFile = null;
				updateTitle();
				JOptionPane.showMessageDialog(this, "Imported " + imported.getName());
			} catch (Exception e) {
				log.error("Import failed", e);
				JOptionPane.showMessageDialog(this, "Import failed: " + e.getMessage());
			}
		}
	}

	@Override
	public void toggleTheme() {
		String currentTheme = recentProjectsManager.getThemePreference();
		String newTheme = "dark".equals(currentTheme) ? "light" : "dark";
		recentProjectsManager.setThemePreference(newTheme);

		try {
			if ("light".equals(newTheme)) {
				com.formdev.flatlaf.FlatLightLaf.setup();
			} else {
				com.formdev.flatlaf.FlatDarkLaf.setup();
			}
			SwingUtilities.updateComponentTreeUI(this);
		} catch (Exception e) {
			log.error("Theme toggle failed", e);
		}
	}

	@Override
	public void toggleConsole() {
		boolean isVisible = consolePanel.isVisible();
		consolePanel.setVisible(!isVisible);
		if (!isVisible) {
			verticalSplitPane.setDividerLocation(0.67);
		}
	}

	@Override
	public void clearConsole() {
		consolePanel.clear();
	}

	@Override
	public void openLogFile() {
		if (currentProjectFile == null) {
			JOptionPane.showMessageDialog(this, "No project saved. Log file is not available.");
			return;
		}
		File logFile = new File(currentProjectFile.getParentFile(), currentProjectFile.getName() + ".log");
		if (!logFile.exists()) {
			JOptionPane.showMessageDialog(this, "Log file does not exist yet.");
			return;
		}
		try {
			Desktop.getDesktop().open(logFile);
		} catch (Exception e) {
			log.error("Failed to open log file", e);
		}
	}

	@Override
	public void openAboutPage() {
		try {
			Desktop.getDesktop().browse(new URI("https://github.com/melkarama/antigostman"));
		} catch (Exception e) {
			log.error("Failed to open about page", e);
		}
	}

	@Override
	public void saveAllProjects() {
		if (currentProjectFile != null) {
			saveProject();
		}
	}

	// --- ProjectTreePanel.TreeController Implementation ---

	@Override
	public void onNodeSelected(PostmanNode node) {
		saveCurrentNodeState();
		currentNode = node;
		
		isLoadingNode = true;
		try {
			nodeConfigPanel.loadNode(node);
			if (node instanceof PostmanRequest) {
				toolbarPanel.loadRequest((PostmanRequest) node);
				toolbarPanel.setVisible(true);
			} else {
				toolbarPanel.setVisible(false);
			}
			revalidate();
			repaint();
		} finally {
			isLoadingNode = false;
		}
	}

	@Override
	public void onNodeRenamed(PostmanNode node) {
		if (node == currentNode) {
			updateTitle();
		}
	}

	@Override
	public void onNodeDeleted(List<PostmanNode> nodes) {
		if (nodes.contains(currentNode)) {
			currentNode = null;
			toolbarPanel.setVisible(false);
			nodeConfigPanel.loadNode(null);
		}
	}

	@Override
	public void onNodeCloned(PostmanNode original, PostmanNode clone) {
		// No special action needed
	}

	@Override
	public void onNodesMoved() {
		// No special action needed
	}

	private void saveCurrentNodeState() {
		if (currentNode != null) {
			nodeConfigPanel.saveNode();
		}
	}

	// PDF and Email methods
	private void generatePdfReport() {
		if (!(currentNode instanceof PostmanRequest)) return;
		PostmanRequest req = (PostmanRequest) currentNode;
		if (lastExecutionResponse == null && lastExecutionException == null) {
			if (JOptionPane.showConfirmDialog(this, "No execution data. Run request now?", "Confirm", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				sendRequest();
			}
			return;
		}
		try {
			File tempFile = File.createTempFile("antigostman_report_" + req.getId() + "_", ".pdf");
			com.antigostman.service.PdfReportService pdfService = new com.antigostman.service.PdfReportService();
			String projectName = rootCollection != null ? rootCollection.getName() : "Unknown Project";
			pdfService.generateReport(req, projectName, lastExecutionRequestHeaders, lastExecutionRequestBody,
					lastExecutionResponse, lastExecutionException, lastExecutionConsoleLog, lastExecutionDuration, tempFile);
			if (tempFile.exists()) Desktop.getDesktop().open(tempFile);
		} catch (Exception e) {
			log.error("PDF generation failed", e);
		}
	}

	private void emailPdfReport() {
		generatePdfReport();
	}

	private void regenerateIds(PostmanNode node) {
		node.setId(UUID.randomUUID().toString());
		for (int i = 0; i < node.getChildCount(); i++) {
			regenerateIds((PostmanNode) node.getChildAt(i));
		}
	}

	private String createPrescript(PostmanNode node) {
		if (node == null) return null;
		if (!StringUtils.isBlank(node.getPrescript())) return node.getPrescript();
		return createPrescript((PostmanNode) node.getParent());
	}

	private String createPostscript(PostmanNode node) {
		if (node == null) return null;
		if (!StringUtils.isBlank(node.getPostscript())) return node.getPostscript();
		return createPostscript((PostmanNode) node.getParent());
	}

	private Map<String, String> createEnv(PostmanNode node, boolean parse) {
		if (node == null) return Map.of();
		TreeNode parent = node.getParent();
		Map<String, String> parentEnvMap = parent == null ? Map.of() : createEnv((PostmanNode) parent, false);
		Map<String, String> map = new TreeMap<>();
		map.putAll(parentEnvMap);
		map.putAll(node.getEnvironment());
		Collection<String> keys = map.keySet();
		if (parse) map = parse(map, new HashMap<>());
		Map<String, String> results = new TreeMap<>();
		for (String k : keys) results.put(k, map.get(k));
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
		if (node == null) return Map.of();
		TreeNode parent = node.getParent();
		Map<String, String> parentHeaderMap = parent == null ? Map.of() : createHeaders((PostmanNode) parent, variables, false);
		Map<String, String> map = new TreeMap<>();
		map.putAll(parentHeaderMap);
		map.putAll(node.getHeaders());
		Collection<String> keys = map.keySet();
		if (parse) map = parse(map, variables);
		Map<String, String> results = new TreeMap<>();
		for (String k : keys) results.put(k, map.get(k));
		return results;
	}

	private Map<String, Object> createVariableMap(PostmanRequest req) {
		Map<String, String> env = createEnv(currentNode, true);
		Map<String, String> globalEnv = ((PostmanNode) rootCollection.getChildAt(0)).getEnvironment();
		env.putAll(globalEnv);
		Map<String, Object> map = new TreeMap<>();
		map.putAll(rootCollection.getGlobalVariables());
		map.putAll(env);
		map.put("utils", new Utils());
		map.put("request", req);
		map.put("console", new ConsoleLogger());
		map.put("vars", rootCollection.getGlobalVariables());
		return map;
	}

	private void sendRequest() {
		if (!(currentNode instanceof PostmanRequest)) return;

		PostmanRequest req = (PostmanRequest) currentNode;
		saveCurrentNodeState();

		consoleStartOffset = consolePanel.getTextArea().getDocument().getLength();

		lastExecutionResponse = null;
		lastExecutionException = null;
		lastExecutionRequestHeaders = null;
		lastExecutionRequestBody = null;
		lastExecutionConsoleLog = null;
		lastExecutionDuration = 0;

		try {
			Map<String, Object> variables = createVariableMap(req);

			ScriptEngine engine = new ScriptEngineManager().getEngineByName("nashorn");
			if (engine == null) engine = new ScriptEngineManager().getEngineByName("js");

			if (engine == null) {
				JOptionPane.showMessageDialog(this, "JavaScript engine not found.");
				lastExecutionException = new RuntimeException("JavaScript engine not found.");
				captureConsoleLog();
				return;
			}

			ScriptEngine fEngine = engine;
			for (String k : variables.keySet()) fEngine.put(k, variables.get(k));

			String prescript = createPrescript(req);
			if (prescript != null && !prescript.trim().isEmpty()) {
				try {
					fEngine.eval(prescript);
				} catch (Exception e) {
					log.error("Prescript error", e);
					nodeConfigPanel.getResponseArea().setText("Prescript Error: " + e.getMessage());
					nodeConfigPanel.setResponseBodySyntax(SyntaxConstants.SYNTAX_STYLE_NONE);
					lastExecutionException = e;
					captureConsoleLog();
					return;
				}
			}

			Map<String, String> headers = createHeaders(currentNode, variables, true);

			String bodyType = req.getBodyType() != null ? req.getBodyType() : "TEXT";
			String bodyToSend = req.getBody();
			boolean isGet = "GET".equalsIgnoreCase(req.getMethod());
			String queryParams = null;
			HttpRequest.BodyPublisher multipartPublisher = null;
			String multipartContentType = null;

			if ("FORM ENCODED".equalsIgnoreCase(bodyType) || (isGet && StringUtils.isNotBlank(bodyToSend))) {
				try {
					bodyToSend = parse(bodyToSend, variables);
				} catch (Exception e) {
					log.error("Error parsing variables in body", e);
				}

				Map<String, String> formParams = parseProperties(bodyToSend);
				StringBuilder encoded = new StringBuilder();
				for (Map.Entry<String, String> entry : formParams.entrySet()) {
					if (encoded.length() > 0) encoded.append("&");
					encoded.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
					if (entry.getValue() != null) {
						encoded.append("=").append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
					}
				}

				String encodedBody = encoded.toString();
				if (isGet) {
					queryParams = encodedBody;
					bodyToSend = encodedBody;
				} else {
					bodyToSend = encodedBody;
					headers.put("Content-Type", "application/x-www-form-urlencoded");
				}
			} else if ("MULTIPART".equalsIgnoreCase(bodyType)) {
				try {
					bodyToSend = parse(bodyToSend, variables);
				} catch (Exception e) {
					log.error("Error parsing variables in multipart body", e);
				}

				Map<String, String> params = parseProperties(bodyToSend);
				String boundary = "----AntigostmanBoundary" + System.currentTimeMillis();
				multipartContentType = "multipart/form-data; boundary=" + boundary;

				java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
				java.io.PrintWriter writer = new java.io.PrintWriter(
						new java.io.OutputStreamWriter(baos, StandardCharsets.UTF_8), true);

				for (Map.Entry<String, String> entry : params.entrySet()) {
					String key = entry.getKey();
					String value = entry.getValue();

					if (value != null && value.startsWith("file:")) {
						String filePath = value.substring(5);
						java.io.File file = new java.io.File(filePath);

						if (file.exists() && file.isFile()) {
							writer.append("--").append(boundary).append("\r\n");
							writer.append("Content-Disposition: form-data; name=\"").append(key).append("\"; filename=\"")
									.append(file.getName()).append("\"\r\n");
							writer.append("Content-Type: application/octet-stream\r\n");
							writer.append("\r\n");
							writer.flush();

							try {
								byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
								baos.write(fileBytes);
							} catch (Exception e) {
								log.error("Error reading file: " + filePath, e);
							}

							writer.append("\r\n");
							writer.flush();
						} else {
							log.warn("File not found or not a file: " + filePath);
						}
					} else {
						writer.append("--").append(boundary).append("\r\n");
						writer.append("Content-Disposition: form-data; name=\"").append(key).append("\"\r\n");
						writer.append("\r\n");
						writer.append(value != null ? value : "");
						writer.append("\r\n");
						writer.flush();
					}
				}

				writer.append("--").append(boundary).append("--").append("\r\n");
				writer.flush();
				writer.close();

				byte[] multipartBytes = baos.toByteArray();
				multipartPublisher = HttpRequest.BodyPublishers.ofByteArray(multipartBytes);
				bodyToSend = "Multipart upload with " + params.size() + " field(s)";

			} else if ("JSON".equalsIgnoreCase(bodyType)) {
				headers.put("Content-Type", "application/json");
			} else if ("XML".equalsIgnoreCase(bodyType)) {
				headers.put("Content-Type", "application/xml");
			} else if ("TEXT".equalsIgnoreCase(bodyType)) {
				headers.put("Content-Type", "text/plain");
			}

			String finalQueryParams = queryParams;
			HttpRequest.BodyPublisher finalMultipartPublisher = multipartPublisher;
			String finalMultipartContentType = multipartContentType;

			toolbarPanel.setSendButtonEnabled(false);
			nodeConfigPanel.getResponseArea().setText("Sending request...");
			nodeConfigPanel.setResponseBodySyntax(SyntaxConstants.SYNTAX_STYLE_NONE);

			String finalBody = bodyToSend;
			Map<String, String> finalHeaders = headers;

			long startTime = System.currentTimeMillis();

			SwingWorker<HttpResponse<String>, Void> worker = new SwingWorker<>() {
				@Override
				protected HttpResponse<String> doInBackground() throws Exception {
					String url = parse(req.getUrl(), variables);
					
					if (finalQueryParams != null && !finalQueryParams.isEmpty()) {
						String separator = url.contains("?") ? "&" : "?";
						url = url + separator + finalQueryParams;
					}
					String body = parse(finalBody, variables);

					System.out.println("> " + url);

					if (req.isDownloadContent()) {
						HttpResponse<byte[]> binaryResp = httpClientService.sendRequestBytes(url, req.getMethod(), body,
								finalHeaders, req.getTimeout(), req.getHttpVersion());

						if (binaryResp.body() != null && binaryResp.body().length > 0) {
							DownloadSaver.saveAndOpen(binaryResp.body());
						}

						String stringBody = "";
						if (binaryResp.body() != null) {
							if (binaryResp.body().length < 100000) {
								stringBody = new String(binaryResp.body(), StandardCharsets.UTF_8);
							} else {
								stringBody = "[Binary content size: " + binaryResp.body().length + " bytes]";
							}
						}

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
							}

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
						if (finalMultipartPublisher != null) {
							return httpClientService.sendRequest(url, req.getMethod(), finalMultipartPublisher, finalHeaders,
									req.getTimeout(), req.getHttpVersion(), finalMultipartContentType);
						} else {
							return httpClientService.sendRequest(url, req.getMethod(), body, finalHeaders, req.getTimeout(),
									req.getHttpVersion());
						}
					}
				}

				@Override
				protected void done() {
					StringBuilder reqHeadersSb = new StringBuilder();
					finalHeaders.forEach((k, v) -> reqHeadersSb.append(k).append(": ").append(v).append("\n"));
					nodeConfigPanel.setRequestHeaders(reqHeadersSb.toString());
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

						if (response != null) {
							StringBuilder respHeadersSb = new StringBuilder();
							respHeadersSb.append("Status: ").append(response.statusCode()).append("\n\n");
							response.headers().map().forEach((k, v) -> respHeadersSb.append(k).append(": ").append(v).append("\n"));
							nodeConfigPanel.setResponseHeaders(respHeadersSb.toString());

							String responseBody;
							String syntaxStyle = SyntaxConstants.SYNTAX_STYLE_NONE;

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
								syntaxStyle = SyntaxConstants.SYNTAX_STYLE_JSON;
							} catch (Exception e) {
								responseBody = response.body();
							}
							nodeConfigPanel.setResponseBody(responseBody);
							nodeConfigPanel.setResponseBodySyntax(syntaxStyle);
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

						toolbarPanel.setSendButtonEnabled(true);
						logExecution(req, finalHeaders, finalBody, response, executionException);

						lastExecutionResponse = response;
						lastExecutionException = executionException;
						lastExecutionRequestHeaders = finalHeaders;
						lastExecutionRequestBody = finalBody;
						lastExecutionDuration = duration;

						SwingUtilities.invokeLater(() -> captureConsoleLog());
					}
				}
			};

			worker.execute();

		} catch (Exception e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Error preparing request: " + e.getMessage());
			lastExecutionException = e;
			captureConsoleLog();
		}
	}

	private Map<String, String> parseProperties(String text) {
		Map<String, String> map = new java.util.LinkedHashMap<>();
		if (text == null || text.isBlank()) return map;
		String[] lines = text.split("\\R");
		for (String line : lines) {
			line = line.trim();
			if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
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

	public static class ConsoleLogger {
		public void log(Object msg) {
			System.out.println("[Script Console] " + msg);
		}
	}

	private void updateTitle() {
		if (currentProjectFile != null) {
			setTitle("Antigostman - " + currentProjectFile.getAbsolutePath());
		} else if (rootCollection != null) {
			setTitle("Antigostman - " + rootCollection.getName());
		} else {
			setTitle("Antigostman");
		}
	}

	private void updateRecentProjectsMenu(javax.swing.JMenu recentMenu) {
		// Delegated to mainMenuBar
	}

	private void restoreWorkspace() {
		java.util.List<String> recent = recentProjectsManager.getRecentProjects();
		if (!recent.isEmpty()) {
			File file = new File(recent.get(0));
			if (file.exists()) {
				try {
					loadProjectFile(file);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	private PostmanNode findNodeById(PostmanNode root, String id) {
		if (root.getId().equals(id)) return root;
		for (int i = 0; i < root.getChildCount(); i++) {
			PostmanNode found = findNodeById((PostmanNode) root.getChildAt(i), id);
			if (found != null) return found;
		}
		return null;
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

	private void captureConsoleLog() {
		try {
			JTextArea textArea = consolePanel.getTextArea();
			int endOffset = textArea.getDocument().getLength();
			if (endOffset > consoleStartOffset) {
				lastExecutionConsoleLog = textArea.getText(consoleStartOffset, endOffset - consoleStartOffset);
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
		if (currentProjectFile == null) return;

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

	public static void main(String[] args) {
		try {
			lockSocket = new java.net.ServerSocket(54321, 0, java.net.InetAddress.getByName("127.0.0.1"));
		} catch (java.io.IOException e) {
			JOptionPane.showMessageDialog(null, "Application is already running.", "Error", JOptionPane.ERROR_MESSAGE);
			System.exit(1);
		}

		System.setProperty("org.slf4j.simpleLogger.logFile", "System.out");
		System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
		System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");

		try {
			RecentProjectsManager tempManager = new RecentProjectsManager();
			String theme = tempManager.getThemePreference();

			if ("light".equals(theme)) {
				com.formdev.flatlaf.FlatLightLaf.setup();
			} else {
				com.formdev.flatlaf.FlatDarkLaf.setup();
			}
		} catch (Exception e) {
			e.printStackTrace();
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		SwingUtilities.invokeLater(() -> {
			try {
				App app = new App();
				app.setExtendedState(JFrame.MAXIMIZED_BOTH);
				app.setVisible(true);

				app.addComponentListener(new java.awt.event.ComponentAdapter() {
					@Override
					public void componentResized(java.awt.event.ComponentEvent e) {
						SwingUtilities.invokeLater(() -> {
							int height = app.verticalSplitPane.getHeight();
							if (height > 0) {
								app.verticalSplitPane.setDividerLocation((int) (height * 0.67));
								app.removeComponentListener(this);
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
}
