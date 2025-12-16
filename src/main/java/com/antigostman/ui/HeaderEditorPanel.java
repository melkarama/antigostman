package com.antigostman.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

public class HeaderEditorPanel extends JPanel {

	private final DefaultTableModel tableModel;
	private final JTable table;

	public HeaderEditorPanel() {
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createTitledBorder("Headers (Scoped)"));

		String[] columnNames = { "Key", "Value" };
		tableModel = new DefaultTableModel(columnNames, 0);
		table = new JTable(tableModel);

		add(new JScrollPane(table), BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		JButton addButton = new JButton("Add Header");
		JButton removeButton = new JButton("Remove Selected");

		addButton.addActionListener(e -> tableModel.addRow(new String[] { "", "" }));
		removeButton.addActionListener(e -> {
			int selectedRow = table.getSelectedRow();
			if (selectedRow != -1) {
				tableModel.removeRow(selectedRow);
			}
		});

		buttonPanel.add(addButton);
		buttonPanel.add(removeButton);
		add(buttonPanel, BorderLayout.SOUTH);
	}

	public void setHeaders(Map<String, String> headers) {
		tableModel.setRowCount(0);
		if (headers != null) {
			headers.forEach((k, v) -> tableModel.addRow(new String[] { k, v }));
		}
	}

	public Map<String, String> getHeaders() {
		Map<String, String> headers = new LinkedHashMap<>();
		for (int i = 0; i < tableModel.getRowCount(); i++) {
			String key = (String) tableModel.getValueAt(i, 0);
			String value = (String) tableModel.getValueAt(i, 1);
			if (key != null && !key.trim().isEmpty()) {
				headers.put(key.trim(), value != null ? value.trim() : "");
			}
		}
		return headers;
	}
}
