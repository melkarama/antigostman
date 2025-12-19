package com.antigostman.ui;

import com.antigostman.model.PostmanCollection;
import com.antigostman.model.PostmanFolder;
import com.antigostman.model.PostmanNode;
import com.antigostman.model.PostmanRequest;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class ProjectTreePanel extends JPanel {
    private JTree projectTree;
    private DefaultTreeModel treeModel;
    private PostmanCollection rootCollection;
    private final TreeController controller;

    public interface TreeController {
        void onNodeSelected(PostmanNode node);
        void onNodeRenamed(PostmanNode node);
        void onNodeDeleted(java.util.List<PostmanNode> nodes);
        void onNodeCloned(PostmanNode original, PostmanNode clone);
        void onNodesMoved();
    }

    public ProjectTreePanel(PostmanCollection root, TreeController controller) {
        this.rootCollection = root;
        this.controller = controller;
        setLayout(new BorderLayout());
        initComponents();
    }

    private void initComponents() {
        treeModel = new DefaultTreeModel(rootCollection);
        projectTree = new JTree(treeModel);
        projectTree.setRootVisible(true);
        projectTree.setShowsRootHandles(true);
        projectTree.setCellRenderer(new PostmanTreeCellRenderer());
        projectTree.setDragEnabled(true);
        projectTree.setDropMode(javax.swing.DropMode.ON_OR_INSERT);
        projectTree.setTransferHandler(new TreeTransferHandler());
        projectTree.setRowHeight(28);

        projectTree.addTreeSelectionListener(e -> {
            PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
            controller.onNodeSelected(node);
        });

        projectTree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    TreePath path = projectTree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        int row = projectTree.getRowForPath(path);
                        if (!projectTree.isRowSelected(row)) {
                            projectTree.setSelectionRow(row);
                        }
                        showContextMenu(e.getX(), e.getY());
                    }
                }
            }
        });

        projectTree.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.isAltDown() || e.isShiftDown()) {
                    if (e.getKeyCode() == KeyEvent.VK_UP) {
                        moveSelectedNodes(-1);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                        moveSelectedNodes(1);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                        expandSelectedNodes();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                        collapseSelectedNodes();
                        e.consume();
                    }
                } else {
                    if (e.getKeyCode() == KeyEvent.VK_F2) {
                        renameSelectedNode();
                    } else if (e.getKeyCode() == KeyEvent.VK_F3) {
                        cloneSelectedNodes();
                    } else if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                        deleteSelectedNodes();
                    }
                }
            }
        });

        add(new JScrollPane(projectTree), BorderLayout.CENTER);
    }

    public void setRoot(PostmanCollection root) {
        this.rootCollection = root;
        this.treeModel = new DefaultTreeModel(root);
        projectTree.setModel(treeModel);
    }

    public JTree getTree() {
        return projectTree;
    }

    public DefaultTreeModel getTreeModel() {
        return treeModel;
    }

    public void nodeChanged(PostmanNode node) {
        treeModel.nodeChanged(node);
    }

    public void reload() {
        treeModel.reload();
    }

    private void showContextMenu(int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        PostmanNode selectedNode = (PostmanNode) projectTree.getLastSelectedPathComponent();
        if (selectedNode == null) return;

        if (selectedNode instanceof PostmanCollection || selectedNode instanceof PostmanFolder) {
            JMenuItem addFolder = new JMenuItem("Add Folder");
            addFolder.addActionListener(e -> promptAndAddChild(selectedNode, "Folder"));
            menu.add(addFolder);

            JMenuItem addRequest = new JMenuItem("Add Request");
            addRequest.addActionListener(e -> promptAndAddChild(selectedNode, "Request"));
            menu.add(addRequest);
            menu.addSeparator();
        }

        JMenuItem rename = new JMenuItem("Rename (F2)");
        rename.addActionListener(e -> renameSelectedNode());
        menu.add(rename);

        JMenuItem clone = new JMenuItem("Clone (F3)");
        clone.addActionListener(e -> cloneSelectedNodes());
        menu.add(clone);

        JMenuItem delete = new JMenuItem("Delete (Del)");
        delete.addActionListener(e -> deleteSelectedNodes());
        menu.add(delete);

        menu.show(projectTree, x, y);
    }

    private void promptAndAddChild(PostmanNode parent, String type) {
        String name = JOptionPane.showInputDialog(this, "Enter " + type + " name:", "New " + type, JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            PostmanNode child = "Folder".equals(type) ? new PostmanFolder(name) : new PostmanRequest(name);
            addChild(parent, child);
        }
    }

    private void addChild(PostmanNode parent, PostmanNode child) {
        treeModel.insertNodeInto(child, parent, parent.getChildCount());
        projectTree.setSelectionPath(new TreePath(child.getPath()));
    }

    private void renameSelectedNode() {
        PostmanNode node = (PostmanNode) projectTree.getLastSelectedPathComponent();
        if (node == null || node.getParent() == null) return;

        String newName = JOptionPane.showInputDialog(this, "Rename '" + node.getName() + "' to:", node.getName());
        if (newName != null && !newName.trim().isEmpty()) {
            node.setName(newName);
            treeModel.nodeChanged(node);
            controller.onNodeRenamed(node);
        }
    }

    private void deleteSelectedNodes() {
        TreePath[] paths = projectTree.getSelectionPaths();
        if (paths == null || paths.length == 0) return;

        String message = paths.length == 1 ? "Are you sure you want to delete '" + ((PostmanNode)paths[0].getLastPathComponent()).getName() + "'?"
                : "Are you sure you want to delete " + paths.length + " selected nodes?";

        Object[] options = { "Yes", "No" };
        int response = JOptionPane.showOptionDialog(this, message, "Confirm Delete", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE, null, options, options[1]);

        if (response != 0) return;

        java.util.List<PostmanNode> deletedNodes = new java.util.ArrayList<>();
        PostmanNode nodeToSelect = null;

        for (TreePath path : paths) {
            PostmanNode node = (PostmanNode) path.getLastPathComponent();
            if (node.getParent() != null) {
                PostmanNode parent = (PostmanNode) node.getParent();
                int index = parent.getIndex(node);

                if (nodeToSelect == null || isOrHasAncestorIn(nodeToSelect, paths)) {
                    if (parent.getChildCount() > 1) {
                        int newIndex = index > 0 ? index - 1 : 1;
                        nodeToSelect = (PostmanNode) (newIndex < parent.getChildCount() ? parent.getChildAt(newIndex) : parent);
                    } else {
                        nodeToSelect = parent;
                    }
                }
                treeModel.removeNodeFromParent(node);
                deletedNodes.add(node);
            } else if (paths.length == 1) {
                JOptionPane.showMessageDialog(this, "Cannot delete the root project.");
            }
        }

        if (nodeToSelect != null && nodeToSelect.getParent() != null) {
            projectTree.setSelectionPath(new TreePath(nodeToSelect.getPath()));
        } else if (rootCollection != null) {
            projectTree.setSelectionPath(new TreePath(rootCollection.getPath()));
        }
        controller.onNodeDeleted(deletedNodes);
    }

    private boolean isOrHasAncestorIn(PostmanNode node, TreePath[] paths) {
        for (TreePath path : paths) {
            if (isChildOf((PostmanNode) path.getLastPathComponent(), node)) return true;
        }
        return false;
    }

    private boolean isChildOf(PostmanNode potentialParent, PostmanNode node) {
        if (node == null) return false;
        if (node == potentialParent) return true;
        return isChildOf(potentialParent, (PostmanNode) node.getParent());
    }

    private void cloneSelectedNodes() {
        TreePath[] paths = projectTree.getSelectionPaths();
        if (paths == null || paths.length == 0) return;

        for (TreePath path : paths) {
            PostmanNode node = (PostmanNode) path.getLastPathComponent();
            if (node.getParent() != null) {
                PostmanNode parent = (PostmanNode) node.getParent();
                String newName;
                if (paths.length == 1) {
                    newName = JOptionPane.showInputDialog(this, "Enter name for clone:", node.getName() + " - CLONE");
                    if (newName == null) return;
                } else {
                    newName = node.getName() + " - CLONE";
                }
                PostmanNode clone = cloneNode(node);
                clone.setName(newName);
                treeModel.insertNodeInto(clone, parent, parent.getIndex(node) + 1);
                controller.onNodeCloned(node, clone);
            }
        }
    }

    private PostmanNode cloneNode(PostmanNode node) {
        PostmanNode clone;
        if (node instanceof PostmanRequest) {
            PostmanRequest original = (PostmanRequest) node;
            PostmanRequest r = new PostmanRequest(original.getName());
            r.setUrl(original.getUrl());
            r.setMethod(original.getMethod());
            r.setBody(original.getBody());
            r.setHeaders(new java.util.HashMap<>(original.getHeaders()));
            r.setPrescript(original.getPrescript());
            r.setPostscript(original.getPostscript());
            r.setBodyType(original.getBodyType());
            r.setTimeout(original.getTimeout());
            r.setHttpVersion(original.getHttpVersion());
            r.setDownloadContent(original.isDownloadContent());
            clone = r;
        } else if (node instanceof PostmanFolder) {
            clone = new PostmanFolder(node.getName());
            ((PostmanFolder)clone).setHeaders(new java.util.HashMap<>(((PostmanFolder)node).getHeaders()));
            cloneChildren(node, clone);
        } else {
            clone = new PostmanCollection(node.getName());
            ((PostmanCollection)clone).setHeaders(new java.util.HashMap<>(((PostmanCollection)node).getHeaders()));
            ((PostmanCollection)clone).setEmailReportTo(((PostmanCollection)node).getEmailReportTo());
            ((PostmanCollection)clone).setEmailReportCc(((PostmanCollection)node).getEmailReportCc());
            cloneChildren(node, clone);
        }
        return clone;
    }

    private void cloneChildren(PostmanNode source, PostmanNode target) {
        for (int i = 0; i < source.getChildCount(); i++) {
            target.add(cloneNode((PostmanNode) source.getChildAt(i)));
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

    private class TreeTransferHandler extends javax.swing.TransferHandler {
        @Override public int getSourceActions(JComponent c) { return MOVE; }
        @Override protected java.awt.datatransfer.Transferable createTransferable(JComponent c) {
            TreePath[] paths = projectTree.getSelectionPaths();
            if (paths != null && paths.length > 0) {
                java.util.List<String> ids = new java.util.ArrayList<>();
                for (TreePath path : paths) {
                    PostmanNode node = (PostmanNode) path.getLastPathComponent();
                    if (node.getParent() != null) ids.add(node.getId());
                }
                if (!ids.isEmpty()) return new java.awt.datatransfer.StringSelection(String.join(",", ids));
            }
            return null;
        }
        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDataFlavorSupported(java.awt.datatransfer.DataFlavor.stringFlavor)) return false;
            JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
            TreePath path = dl.getPath();
            if (path == null) return false;
            PostmanNode targetNode = (PostmanNode) path.getLastPathComponent();
            return targetNode instanceof PostmanFolder || targetNode instanceof PostmanCollection;
        }
        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                String nodeIds = (String) support.getTransferable().getTransferData(java.awt.datatransfer.DataFlavor.stringFlavor);
                String[] ids = nodeIds.split(",");
                JTree.DropLocation dl = (JTree.DropLocation) support.getDropLocation();
                TreePath destPath = dl.getPath();
                PostmanNode targetParent = (PostmanNode) destPath.getLastPathComponent();
                int insertIndex = dl.getChildIndex() == -1 ? targetParent.getChildCount() : dl.getChildIndex();
                java.util.List<PostmanNode> draggedNodes = new java.util.ArrayList<>();
                for (String id : ids) {
                    PostmanNode draggedNode = findNodeById(rootCollection, id);
                    if (draggedNode != null && draggedNode != targetParent && !isChildOf(draggedNode, targetParent)) draggedNodes.add(draggedNode);
                }
                if (draggedNodes.isEmpty()) return false;
                for (PostmanNode draggedNode : draggedNodes) {
                    PostmanNode currentParent = (PostmanNode) draggedNode.getParent();
                    int currentIndex = currentParent.getIndex(draggedNode);
                    treeModel.removeNodeFromParent(draggedNode);
                    if (currentParent == targetParent && currentIndex < insertIndex) insertIndex--;
                    treeModel.insertNodeInto(draggedNode, targetParent, insertIndex++);
                }
                projectTree.expandPath(destPath);
                TreePath[] newPaths = new TreePath[draggedNodes.size()];
                for (int i = 0; i < draggedNodes.size(); i++) newPaths[i] = new TreePath(draggedNodes.get(i).getPath());
                projectTree.setSelectionPaths(newPaths);
                controller.onNodesMoved();
                return true;
            } catch (Exception e) { e.printStackTrace(); return false; }
        }
    }

    private void moveSelectedNodes(int direction) {
        TreePath[] paths = projectTree.getSelectionPaths();
        if (paths == null || paths.length == 0) return;

        java.util.List<PostmanNode> selectedNodes = new java.util.ArrayList<>();
        for (TreePath path : paths) {
            PostmanNode node = (PostmanNode) path.getLastPathComponent();
            if (node.getParent() != null) {
                selectedNodes.add(node);
            }
        }

        if (selectedNodes.isEmpty()) return;

        java.util.Map<PostmanNode, java.util.List<PostmanNode>> parentToChildren = new java.util.HashMap<>();
        for (PostmanNode node : selectedNodes) {
            PostmanNode parent = (PostmanNode) node.getParent();
            parentToChildren.computeIfAbsent(parent, k -> new java.util.ArrayList<>()).add(node);
        }

        boolean anyMoved = false;
        for (java.util.Map.Entry<PostmanNode, java.util.List<PostmanNode>> entry : parentToChildren.entrySet()) {
            PostmanNode parent = entry.getKey();
            java.util.List<PostmanNode> childrenInParent = entry.getValue();
            
            childrenInParent.sort(java.util.Comparator.comparingInt(parent::getIndex));
            
            if (direction < 0) { // UP
                if (parent.getIndex(childrenInParent.get(0)) > 0) {
                    for (PostmanNode node : childrenInParent) {
                        int oldIdx = parent.getIndex(node);
                        treeModel.removeNodeFromParent(node);
                        treeModel.insertNodeInto(node, parent, oldIdx - 1);
                    }
                    anyMoved = true;
                }
            } else { // DOWN
                if (parent.getIndex(childrenInParent.get(childrenInParent.size() - 1)) < parent.getChildCount() - 1) {
                    for (int i = childrenInParent.size() - 1; i >= 0; i--) {
                        PostmanNode node = childrenInParent.get(i);
                        int oldIdx = parent.getIndex(node);
                        treeModel.removeNodeFromParent(node);
                        treeModel.insertNodeInto(node, parent, oldIdx + 1);
                    }
                    anyMoved = true;
                }
            }
        }

        if (anyMoved) {
            TreePath[] newPaths = new TreePath[selectedNodes.size()];
            for (int i = 0; i < selectedNodes.size(); i++) {
                newPaths[i] = new TreePath(selectedNodes.get(i).getPath());
            }
            projectTree.setSelectionPaths(newPaths);
            controller.onNodesMoved();
        }
    }

    private void expandSelectedNodes() {
        TreePath[] paths = projectTree.getSelectionPaths();
        if (paths != null) {
            for (TreePath path : paths) {
                projectTree.expandPath(path);
            }
        }
    }

    private void collapseSelectedNodes() {
        TreePath[] paths = projectTree.getSelectionPaths();
        if (paths == null || paths.length == 0) return;

        java.util.List<TreePath> newPaths = new java.util.ArrayList<>();
        boolean selectionChanged = false;

        for (TreePath path : paths) {
            PostmanNode node = (PostmanNode) path.getLastPathComponent();
            
            // If it's a folder/collection and it's expanded, collapse it
            if (node.getChildCount() > 0 && projectTree.isExpanded(path)) {
                projectTree.collapsePath(path);
                newPaths.add(path);
            } else {
                // It's either a request node or a closed folder -> select parent
                TreePath parentPath = path.getParentPath();
                if (parentPath != null) {
                    newPaths.add(parentPath);
                    selectionChanged = true;
                } else {
                    newPaths.add(path); // Keep root selected if at root
                }
            }
        }

        if (selectionChanged) {
            projectTree.setSelectionPaths(newPaths.toArray(new TreePath[0]));
        }
    }
}
