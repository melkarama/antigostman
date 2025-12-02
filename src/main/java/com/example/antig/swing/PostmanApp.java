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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        SwingUtilities.invokeLater(this::loadLastOpenedProject);
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
        if (node == null) return;
        
        // Create custom dialog with NO button focused by default
        Object[] options = {"Yes", "No"};
        int response = JOptionPane.showOptionDialog(this,
            "Are you sure you want to delete '" + node.getName() + "'?",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[1]); // Default to "No"
            
        if (response != 0) return; // 0 = Yes, 1 = No

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
            }
            
            // Select parent or sibling to keep selection valid
            if (parent.getChildCount() > 0) {
                int newIndex = Math.min(index, parent.getChildCount() - 1);
                PostmanNode sibling = (PostmanNode) parent.getChildAt(newIndex);
                projectTree.setSelectionPath(new TreePath(sibling.getPath()));
            } else {
                // If parent is workspaceRoot (invisible), and we deleted last collection, select nothing?
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
                recentProjectsManager.addRecentProject(file);
                updateRecentProjectsMenu((JMenu) getJMenuBar().getMenu(0).getMenuComponent(4));
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
            // Load project with expansion state
            Object[] result = projectService.loadProjectWithExpansionState(file);
            PostmanCollection loadedCollection = (PostmanCollection) result[0];
            @SuppressWarnings("unchecked")
            Set<String> expandedIds = (Set<String>) result[1];
            
            // Remove default "My Collection" if it's the only node and empty
            if (workspaceRoot.getChildCount() == 1) {
                PostmanNode child = (PostmanNode) workspaceRoot.getChildAt(0);
                if (child instanceof PostmanCollection && 
                    "My Collection".equals(child.getName()) && 
                    child.getChildCount() == 0) {
                    workspaceRoot.remove(0);
                }
            }
            
            // Add to workspace
            workspaceRoot.add(loadedCollection);
            treeModel.reload();
            
            // Restore expansion state BEFORE showing dialog
            restoreExpansionFromIds(loadedCollection, expandedIds);
            
            // Scroll to collection
            TreePath path = new TreePath(loadedCollection.getPath());
            projectTree.scrollPathToVisible(path);
            
            currentNode = null;
            nodeConfigPanel.loadNode(null);
            
            recentProjectsManager.addRecentProject(file);
            updateRecentProjectsMenu((JMenu) getJMenuBar().getMenu(0).getMenuComponent(4));
            
            // Map the collection to the file
            collectionFileMap.put(loadedCollection, file);
            
            // Show confirmation AFTER expansion is applied
            JOptionPane.showMessageDialog(this, 
                "Project loaded successfully!\n\nLocation: " + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading: " + e.getMessage());
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
                com.example.antig.swing.model.xml.XmlNode xmlNode = 
                    com.example.antig.swing.service.NodeConverter.toXmlNode(collection, expandedIds);
                
                // Save using ProjectService (which expects PostmanCollection, but we'll update it)
                projectService.saveProject(collection, file, expandedIds);
                System.out.println("Autosaved " + collection.getName() + " to " + file.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("Autosave failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    private PostmanCollection getCollectionForNode(PostmanNode node) {
        if (node == null) return null;
        if (node instanceof PostmanCollection) return (PostmanCollection) node;
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
