package com.example.antig.swing.ui;

import com.example.antig.swing.model.PostmanNode;
import com.example.antig.swing.model.PostmanRequest;
import org.fife.ui.autocomplete.*;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextScrollPane;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tabbed panel for configuring nodes (Collection, Folder, Request).
 * 
 * Common tabs for all nodes:
 * - Environment: Text area for environment variables in properties format
 * - Headers: Text area for headers in properties format
 * - Prescript: Script to run before request execution
 * - Postscript: Script to run after request execution
 * 
 * Request-only tab:
 * - Execution: Params (top) and Response (bottom)
 */
public class NodeConfigPanel extends JPanel {
    
    private JTabbedPane tabbedPane;
    
    // Common tabs
    private JTextArea environmentArea;
    private JTextArea headersArea;
    private RSyntaxTextArea prescriptArea;
    private RSyntaxTextArea postscriptArea;
    
    // Request-only tabs
    private JTextArea paramsArea;
    private JTextArea responseArea;
    private JPanel executionPanel;
    
    private PostmanNode currentNode;
    
    public NodeConfigPanel() {
        setLayout(new BorderLayout());
        
        tabbedPane = new JTabbedPane();
        
        // Tab 1: Environment
        environmentArea = createTextArea();
        tabbedPane.addTab("Environment", new JScrollPane(environmentArea));
        
        // Tab 2: Headers
        headersArea = createTextArea();
        tabbedPane.addTab("Headers", new JScrollPane(headersArea));
        
        // Tab 3: Prescript
        prescriptArea = createCodeEditor();
        tabbedPane.addTab("Prescript", new RTextScrollPane(prescriptArea));
        
        // Tab 4: Postscript
        postscriptArea = createCodeEditor();
        tabbedPane.addTab("Postscript", new RTextScrollPane(postscriptArea));
        
        // Tab 5: Execution (for requests only)
        createExecutionPanel();
        
        add(tabbedPane, BorderLayout.CENTER);
    }
    
    private JTextArea createTextArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font("Monospaced", Font.PLAIN, 12));
        area.setTabSize(2);
        return area;
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
        
        // Dynamic completion update
        textArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateCompletions(textArea, provider);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateCompletions(textArea, provider);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateCompletions(textArea, provider);
            }
        });
        
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
    
    private void createExecutionPanel() {
        executionPanel = new JPanel(new BorderLayout());
        
        // Split into two parts: params (top) and response (bottom)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.3); // 30% for params, 70% for response
        
        // Top: Params
        paramsArea = createTextArea();
        JPanel paramsPanel = new JPanel(new BorderLayout());
        paramsPanel.add(new JLabel("Request Parameters (key=value format):"), BorderLayout.NORTH);
        paramsPanel.add(new JScrollPane(paramsArea), BorderLayout.CENTER);
        splitPane.setTopComponent(paramsPanel);
        
        // Bottom: Response
        responseArea = createTextArea();
        responseArea.setEditable(false);
        JPanel responsePanel = new JPanel(new BorderLayout());
        responsePanel.add(new JLabel("Response:"), BorderLayout.NORTH);
        responsePanel.add(new JScrollPane(responseArea), BorderLayout.CENTER);
        splitPane.setBottomComponent(responsePanel);
        
        executionPanel.add(splitPane, BorderLayout.CENTER);
    }
    
    /**
     * Load node data into the UI.
     */
    public void loadNode(PostmanNode node) {
        this.currentNode = node;
        
        if (node == null) {
            clearAll();
            return;
        }
        
        // Load environment (convert map to properties format)
        environmentArea.setText(mapToProperties(node.getEnvironment()));
        
        // Load headers (convert map to properties format)
        headersArea.setText(mapToProperties(node.getHeaders()));
        
        // Load scripts
        prescriptArea.setText(node.getPrescript() != null ? node.getPrescript() : "");
        postscriptArea.setText(node.getPostscript() != null ? node.getPostscript() : "");
        
        // Show/hide execution tab based on node type
        if (node instanceof PostmanRequest) {
            // Add execution tab if not present
            if (tabbedPane.indexOfComponent(executionPanel) == -1) {
                tabbedPane.addTab("Execution", executionPanel);
            }
            
            PostmanRequest request = (PostmanRequest) node;
            paramsArea.setText(request.getParams() != null ? request.getParams() : "");
            responseArea.setText(""); // Clear previous response
        } else {
            // Remove execution tab if present
            int index = tabbedPane.indexOfComponent(executionPanel);
            if (index != -1) {
                tabbedPane.removeTabAt(index);
            }
        }
    }
    
    /**
     * Save UI data back to the node.
     */
    public void saveNode() {
        if (currentNode == null) return;
        
        // Save environment (convert properties format to map)
        currentNode.setEnvironment(propertiesToMap(environmentArea.getText()));
        
        // Save headers (convert properties format to map)
        currentNode.setHeaders(propertiesToMap(headersArea.getText()));
        
        // Save scripts
        currentNode.setPrescript(prescriptArea.getText());
        currentNode.setPostscript(postscriptArea.getText());
        
        // Save request-specific data
        if (currentNode instanceof PostmanRequest) {
            PostmanRequest request = (PostmanRequest) currentNode;
            request.setParams(paramsArea.getText());
        }
    }
    
    /**
     * Get the response area for displaying HTTP responses.
     */
    public JTextArea getResponseArea() {
        return responseArea;
    }
    
    /**
     * Clear all fields.
     */
    private void clearAll() {
        environmentArea.setText("");
        headersArea.setText("");
        prescriptArea.setText("");
        postscriptArea.setText("");
        paramsArea.setText("");
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
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }
    
    /**
     * Convert properties format to map.
     */
    private Map<String, String> propertiesToMap(String text) {
        Map<String, String> map = new HashMap<>();
        
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
            if (equalsIndex > 0) {
                String key = line.substring(0, equalsIndex).trim();
                String value = line.substring(equalsIndex + 1).trim();
                map.put(key, value);
            }
        }
        
        return map;
    }
}
