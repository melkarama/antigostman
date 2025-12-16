package com.antigostman.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

import com.antigostman.model.PostmanCollection;
import com.antigostman.model.PostmanFolder;
import com.antigostman.model.PostmanRequest;

/**
 * Custom tree cell renderer that displays different icons for Collection,
 * Folder, and Request nodes.
 */
public class PostmanTreeCellRenderer extends DefaultTreeCellRenderer {

	private final Icon collectionIcon;
	private final Icon folderIcon;
	private final Icon requestIcon;

	// Icons for different HTTP methods
	private final Icon getIcon;
	private final Icon postIcon;
	private final Icon putIcon;
	private final Icon deleteIcon;
	private final Icon patchIcon;
	private final Icon defaultRequestIcon;

	public PostmanTreeCellRenderer() {
		// Create simple colored icons for different node types
		collectionIcon = createColoredIcon(new Color(52, 152, 219), 16, 16); // Blue
		folderIcon = createColoredIcon(new Color(241, 196, 15), 16, 16); // Yellow/Gold

		// Method specific icons - using circles
		getIcon = createCircleIcon(new Color(46, 204, 113), 14, 14); // Green
		postIcon = createCircleIcon(new Color(230, 126, 34), 14, 14); // Orange
		putIcon = createCircleIcon(new Color(52, 152, 219), 14, 14); // Blue
		deleteIcon = createCircleIcon(new Color(231, 76, 60), 14, 14); // Red
		patchIcon = createCircleIcon(new Color(155, 89, 182), 14, 14); // Purple
		defaultRequestIcon = createCircleIcon(Color.GRAY, 14, 14); // Gray

		requestIcon = defaultRequestIcon; // Fallback
	}

	@Override
	public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
			boolean leaf, int row, boolean hasFocus) {

		super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

		// Set icon based on node type
		if (value instanceof PostmanCollection) {
			setIcon(collectionIcon);
		} else if (value instanceof PostmanFolder) {
			setIcon(folderIcon);
		} else if (value instanceof PostmanRequest) {
			PostmanRequest req = (PostmanRequest) value;
			String method = req.getMethod();
			if (method == null) {
				method = "GET";
			}

			switch (method.toUpperCase()) {
			case "GET":
				setIcon(getIcon);
				break;
			case "POST":
				setIcon(postIcon);
				break;
			case "PUT":
				setIcon(putIcon);
				break;
			case "DELETE":
				setIcon(deleteIcon);
				break;
			case "PATCH":
				setIcon(patchIcon);
				break;
			default:
				setIcon(defaultRequestIcon);
				break;
			}
		}

		// Add padding for better spacing
		setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

		// Set font size for better readability
		Font currentFont = getFont();
		setFont(currentFont.deriveFont(13.0f));

		return this;
	}

	/**
	 * Create a simple colored square icon.
	 */
	private Icon createColoredIcon(Color color, int width, int height) {
		return new Icon() {
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				// Draw rounded rectangle
				g2d.setColor(color);
				g2d.fillRoundRect(x, y, width, height, 4, 4);

				// Draw border
				g2d.setColor(color.darker());
				g2d.drawRoundRect(x, y, width, height, 4, 4);

				g2d.dispose();
			}

			@Override
			public int getIconWidth() {
				return width;
			}

			@Override
			public int getIconHeight() {
				return height;
			}
		};
	}

	/**
	 * Create a simple colored circle icon.
	 */
	private Icon createCircleIcon(Color color, int width, int height) {
		return new Icon() {
			@Override
			public void paintIcon(Component c, Graphics g, int x, int y) {
				Graphics2D g2d = (Graphics2D) g.create();
				g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				// Draw circle
				g2d.setColor(color);
				g2d.fillOval(x, y, width, height);

				// Draw border
				g2d.setColor(color.darker());
				g2d.drawOval(x, y, width, height);

				g2d.dispose();
			}

			@Override
			public int getIconWidth() {
				return width;
			}

			@Override
			public int getIconHeight() {
				return height;
			}
		};
	}
}
