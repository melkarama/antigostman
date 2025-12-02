package com.example.antig.swing;

import com.example.antig.swing.model.PostmanCollection;
import com.example.antig.swing.model.PostmanFolder;
import com.example.antig.swing.model.PostmanNode;
import com.example.antig.swing.model.PostmanRequest;
import com.example.antig.swing.service.ProjectService;
import com.example.antig.swing.service.RecentProjectsManager;
import com.example.antig.swing.ui.NodeConfigPanel;
import com.example.antig.swing.ui.PostmanTreeCellRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

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
    private JTextArea requestBodyArea;
    private JButton sendButton;

    private PostmanNode currentNode;
    private File currentProjectFile;
    
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
        SwingUtilities.invokeLater(this::loadLastOpenedProject);
    }

    private void initMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = new JMenu("File");

        JMenuItem saveItem = new JMenuItem("Save Project");
        saveItem.addActionListener(e -> saveProject());
        JMenuItem loadItem = new JMenuItem("Load Project");
        loadItem.addActionListener(e -> loadProject());

        fileMenu.add(saveItem);
        fileMenu.add(loadItem);
        fileMenu.addSeparator();
        
        // Recent Projects submenu
        JMenu recentMenu = new JMenu("Recent Projects");
        updateRecentProjectsMenu(recentMenu);
        fileMenu.add(recentMenu);
        
        menuBar.add(fileMenu);
        setJMenuBar(menuBar);
    }

    private void initComponents() {
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(250);

        // Left: Tree View
        rootCollection = new PostmanCollection("My Collection");
        treeModel = new DefaultTreeModel(rootCollection);
        projectTree = new JTree(treeModel);
        
        // Set custom cell renderer for icons
        projectTree.setCellRenderer(new PostmanTreeCellRenderer());
        
        projectTree.addTreeSelectionListener(e -> onNodeSelected());
        projectTree.addMouseListener(new MouseAdapter() {
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
        rightPanel.add(nodeConfigPanel, BorderLayout.CENTER);

        mainSplitPane.setRightComponent(rightPanel);
        add(mainSplitPane, BorderLayout.CENTER);
    }
    
    private JPanel createRequestToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(5, 5));
        toolbar.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        String[] methods = {"GET", "POST", "PUT", "DELETE", "PATCH"};
        methodComboBox = new JComboBox<>(methods);
        methodComboBox.setPreferredSize(new Dimension(100, 30));
        
        urlField = new JTextField();
        
        sendButton = new JButton("Send");
        sendButton.addActionListener(e -> sendRequest());
        sendButton.setPreferredSize(new Dimension(80, 30));
        
        toolbar.add(methodComboBox, BorderLayout.WEST);
        toolbar.add(urlField, BorderLayout.CENTER);
        toolbar.add(sendButton, BorderLayout.EAST);
        
        // Initially hidden, shown only for requests
        toolbar.setVisible(false);
        
        return toolbar;
    }



    private void onNodeSelected() {
        PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
        if (node == null) return;

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
            
            // Show request toolbar
            Component[] components = ((JPanel) nodeConfigPanel.getParent()).getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel && comp != nodeConfigPanel) {
                    comp.setVisible(true);
                }
            }
        } else {
            // Hide request toolbar for collections/folders
            Component[] components = ((JPanel) nodeConfigPanel.getParent()).getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel && comp != nodeConfigPanel) {
                    comp.setVisible(false);
                }
            }
        }
    }

    private void saveCurrentNodeState() {
        if (currentNode == null) return;

        // Save config panel state
        nodeConfigPanel.saveNode();
        
        // Save request-specific toolbar fields
        if (currentNode instanceof PostmanRequest) {
            PostmanRequest req = (PostmanRequest) currentNode;
            req.setUrl(urlField.getText());
            req.setMethod((String) methodComboBox.getSelectedItem());
        }
    }

    private void renameSelectedNode() {
        PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
        if (node == null) return;

        String newName = JOptionPane.showInputDialog(this, "Enter name:", node.getName());
        if (newName != null && !newName.trim().isEmpty()) {
            node.setName(newName);
            treeModel.nodeChanged(node);
            if (node == currentNode) onNodeSelected(); // Refresh UI label
            autoSaveProject();
        }
    }

    private void showContextMenu(int x, int y) {
        PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
        if (node == null) return;

        JPopupMenu menu = new JPopupMenu();

        if (node instanceof PostmanCollection || node instanceof PostmanFolder) {
            JMenuItem addFolder = new JMenuItem("Add Folder");
            addFolder.addActionListener(e -> addChild(node, new PostmanFolder("New Folder")));
            menu.add(addFolder);

            JMenuItem addRequest = new JMenuItem("Add Request");
            addRequest.addActionListener(e -> addChild(node, new PostmanRequest("New Request")));
            menu.add(addRequest);
        }

        JMenuItem rename = new JMenuItem("Rename");
        rename.addActionListener(e -> renameSelectedNode());
        menu.add(rename);

        JMenuItem delete = new JMenuItem("Delete");
        delete.addActionListener(e -> {
            if (node.getParent() != null) {
                treeModel.removeNodeFromParent(node);
                currentNode = null;
                nodeConfigPanel.loadNode(null);
                autoSaveProject();
            }
        });
        menu.add(delete);
        
        JMenuItem clone = new JMenuItem("Clone");
        clone.addActionListener(e -> cloneNode(node));
        menu.add(clone);

        menu.show(projectTree, x, y);
    }

    private void addChild(PostmanNode parent, PostmanNode child) {
        treeModel.insertNodeInto(child, parent, parent.getChildCount());
        projectTree.scrollPathToVisible(new TreePath(child.getPath()));
        autoSaveProject();
    }
    
    private void cloneNode(PostmanNode node) {
        try {
            String defaultName = node.getName() + "-CLONE";
            String newName = JOptionPane.showInputDialog(this, "Enter name for clone:", defaultName);

            if (newName == null) return; // User cancelled

            // Convert to XML model (no parent references), then back to PostmanNode
            // This avoids cyclic serialization issues
            com.example.antig.swing.model.xml.XmlNode xmlNode = 
                com.example.antig.swing.service.NodeConverter.toXmlNode(node);
            
            if (xmlNode == null) {
                throw new RuntimeException("Failed to convert node to XML (returned null)");
            }

            PostmanNode clone = 
                com.example.antig.swing.service.NodeConverter.toPostmanNode(xmlNode);
            
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

    private void sendRequest() {
        if (!(currentNode instanceof PostmanRequest)) return;
        saveCurrentNodeState(); // Ensure latest edits are saved

        PostmanRequest req = (PostmanRequest) currentNode;
        
        // Collect Scoped Headers
        Map<String, String> effectiveHeaders = new HashMap<>();
        collectHeaders(req, effectiveHeaders);

        sendButton.setEnabled(false);
        nodeConfigPanel.getResponseArea().setText("Sending request...");

        SwingWorker<HttpResponse<String>, Void> worker = new SwingWorker<>() {
            @Override
            protected HttpResponse<String> doInBackground() throws Exception {
                // We need to modify HttpClientService to accept headers
                // For now, we will simulate it or assume HttpClientService is updated
                // Let's assume we update HttpClientService.sendRequest to take headers
                return httpClientService.sendRequest(req.getUrl(), req.getMethod(), req.getBody(), effectiveHeaders);
            }

            @Override
            protected void done() {
                try {
                    HttpResponse<String> response = get();
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

    private void collectHeaders(PostmanNode node, Map<String, String> headers) {
        if (node.getParent() instanceof PostmanNode) {
            collectHeaders((PostmanNode) node.getParent(), headers);
        }
        if (node.getHeaders() != null) {
            headers.putAll(node.getHeaders());
        }
    }

    private void saveProject() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                saveCurrentNodeState();
                projectService.saveProject(rootCollection, file);
                currentProjectFile = file; // Set current file
                recentProjectsManager.addRecentProject(file);
                updateRecentProjectsMenu((JMenu) getJMenuBar().getMenu(0).getMenuComponent(3));
                JOptionPane.showMessageDialog(this, "Project saved!");
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
            rootCollection = projectService.loadProject(file);
            treeModel.setRoot(rootCollection);
            treeModel.reload();
            
            // Expand all nodes to show the loaded structure
            expandAllNodes();
            
            currentNode = null;
            nodeConfigPanel.loadNode(null);  // Clear the config panel
            
            recentProjectsManager.addRecentProject(file);
            updateRecentProjectsMenu((JMenu) getJMenuBar().getMenu(0).getMenuComponent(3));
            
            JOptionPane.showMessageDialog(this, "Project loaded!");
            
            // Set current file
            currentProjectFile = file;
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading: " + e.getMessage());
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

    private void loadLastOpenedProject() {
        java.util.List<String> recent = recentProjectsManager.getRecentProjects();
        if (!recent.isEmpty()) {
            File lastFile = new File(recent.get(0));
            if (lastFile.exists()) {
                loadProjectFromFile(lastFile);
            }
        }
    }
    
    private void autoSaveProject() {
        if (currentProjectFile != null) {
            // Ensure in-memory model is up to date with UI if we are editing a node
            saveCurrentNodeState();
            try {
                projectService.saveProject(rootCollection, currentProjectFile);
                System.out.println("Autosaved to " + currentProjectFile.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("Autosave failed: " + e.getMessage());
                e.printStackTrace();
            }
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
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        SwingUtilities.invokeLater(() -> new PostmanApp().setVisible(true));
    }
}
