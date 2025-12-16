package com.antigostman.service;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

import com.antigostman.model.PostmanRequest;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

public class PdfReportService {

	private static final Color COLOR_PRIMARY = new Color(52, 152, 219); // Blue
	private static final Color COLOR_SECONDARY = new Color(44, 62, 80); // Dark Blue/Grey
	private static final Color COLOR_ACCENT = new Color(46, 204, 113); // Green
	private static final Color COLOR_BG_HEADER = new Color(236, 240, 241); // Light Grey
	private static final Color COLOR_BG_CODE = new Color(248, 249, 250); // Very Light Grey

	private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, Color.WHITE);
	private static final Font FONT_H1 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, COLOR_SECONDARY);
	private static final Font FONT_H2 = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, COLOR_PRIMARY);
	private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
	private static final Font FONT_BOLD = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK);
	private static final Font FONT_CODE = FontFactory.getFont(FontFactory.COURIER, 9, Color.DARK_GRAY);
	private static final Font FONT_TABLE_HEADER = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, COLOR_SECONDARY);

	public void generateReport(PostmanRequest req, String projectName, Map<String, String> requestHeaders, String requestBody,
			HttpResponse<String> response, Exception exception, String consoleOutput, long durationMs, File outputFile) throws IOException, DocumentException {

		Document document = new Document(PageSize.A4, 50, 50, 50, 50);
		PdfWriter.getInstance(document, new FileOutputStream(outputFile));

		// Footer with page numbers
		HeaderFooter footer = new HeaderFooter(new Phrase("Page ", FONT_BODY), true);
		footer.setAlignment(Element.ALIGN_CENTER);
		footer.setBorder(Rectangle.NO_BORDER);
		document.setFooter(footer);

		document.open();

		// Title Page
		addTitlePage(document, req, projectName, durationMs);

		// Request Section
		addSectionHeader(document, "Request Details");
		addRequestInfo(document, req);
		
		addSubHeader(document, "Headers");
		addHeadersTable(document, requestHeaders);

		addSubHeader(document, "Body");
		addCodeBlock(document, formatBody(requestBody));

		// Spacer
		document.add(Chunk.NEWLINE);

		// Response Section
		addSectionHeader(document, "Response Details");
		
		boolean hasContent = false;
		
		if (response != null) {
			hasContent = true;
			addResponseInfo(document, response);
			
			addSubHeader(document, "Headers");
			Map<String, String> respHeaders = new java.util.TreeMap<>(); // Sorted
			response.headers().map().forEach((k, v) -> respHeaders.put(k, String.join(", ", v)));
			addHeadersTable(document, respHeaders);

			addSubHeader(document, "Body");
			addCodeBlock(document, formatBody(response.body()));
		}
		
		if (exception != null) {
			hasContent = true;
			addExceptionInfo(document, exception);
		}
		
		if (!hasContent) {
			document.add(new Paragraph("No response recorded.", FONT_BODY));
		}

		// Execution Log Section
		document.add(Chunk.NEWLINE);
		addSectionHeader(document, "Execution Log");
		if (consoleOutput != null && !consoleOutput.isEmpty()) {
			addCodeBlock(document, consoleOutput);
		} else {
			document.add(new Paragraph("(No console output)", FONT_BODY));
		}

		document.close();
	}

	private void addTitlePage(Document document, PostmanRequest req, String projectName, long durationMs) throws DocumentException {
		PdfPTable headerTable = new PdfPTable(1);
		headerTable.setWidthPercentage(100);
		headerTable.setSpacingAfter(20);

		PdfPCell cell = new PdfPCell(new Phrase("API Execution Report", FONT_TITLE));
		cell.setBackgroundColor(COLOR_PRIMARY);
		cell.setHorizontalAlignment(Element.ALIGN_CENTER);
		cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
		cell.setPadding(20);
		cell.setBorder(Rectangle.NO_BORDER);
		headerTable.addCell(cell);

		document.add(headerTable);

		Paragraph dateP = new Paragraph("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), FONT_BODY);
		dateP.setAlignment(Element.ALIGN_RIGHT);
		dateP.setSpacingAfter(10);
		document.add(dateP);
		
		// Format duration: mm:ss
		long seconds = durationMs / 1000;
		long minutes = seconds / 60;
		long remainingSeconds = seconds % 60;
		String durationStr = String.format("%02d:%02d", minutes, remainingSeconds);
		
		Paragraph durationP = new Paragraph("Duration: " + durationStr, FONT_BODY);
		durationP.setAlignment(Element.ALIGN_RIGHT);
		durationP.setSpacingAfter(10);
		document.add(durationP);
		
		Paragraph projectP = new Paragraph("Project: " + projectName, FONT_H1);
		projectP.setSpacingAfter(10);
		document.add(projectP);

		Paragraph nameP = new Paragraph("Request Name: " + req.getName(), FONT_H1);
		nameP.setSpacingAfter(20);
		document.add(nameP);
	}

	private void addSectionHeader(Document document, String title) throws DocumentException {
		Paragraph p = new Paragraph(title, FONT_H1);
		p.setSpacingBefore(10);
		p.setSpacingAfter(10);
		
		// Add a line under the header
		PdfPTable line = new PdfPTable(1);
		line.setWidthPercentage(100);
		PdfPCell cell = new PdfPCell();
		cell.setBorder(Rectangle.BOTTOM);
		cell.setBorderColor(COLOR_PRIMARY);
		cell.setBorderWidth(2);
		cell.setFixedHeight(3);
		line.addCell(cell);
		
		document.add(p);
		document.add(line);
		document.add(Chunk.NEWLINE); // spacer
	}
	
	private void addSubHeader(Document document, String title) throws DocumentException {
		Paragraph p = new Paragraph(title, FONT_H2);
		p.setSpacingBefore(10);
		p.setSpacingAfter(5);
		document.add(p);
	}

	private void addRequestInfo(Document document, PostmanRequest req) throws DocumentException {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new float[] { 1, 4 });
		table.setSpacingAfter(10);

		addKeyValueRow(table, "Method", req.getMethod());
		addKeyValueRow(table, "URL", req.getUrl());
		
		document.add(table);
	}

	private void addResponseInfo(Document document, HttpResponse<String> response) throws DocumentException {
		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new float[] { 1, 4 });
		table.setSpacingAfter(10);

		addKeyValueRow(table, "Status Code", String.valueOf(response.statusCode()));
		// Could calculate duration if we passed start/end times
		
		document.add(table);
	}
	
	private void addExceptionInfo(Document document, Exception e) throws DocumentException {
		Paragraph p = new Paragraph("Execution Failed", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.RED));
		document.add(p);
		
		java.io.StringWriter sw = new java.io.StringWriter();
		java.io.PrintWriter pw = new java.io.PrintWriter(sw);
		e.printStackTrace(pw);
		
		Chunk c = new Chunk(sw.toString(), FONT_CODE);
		document.add(new Paragraph(c));
	}

	private void addKeyValueRow(PdfPTable table, String key, String value) {
		PdfPCell keyCell = new PdfPCell(new Phrase(key, FONT_BOLD));
		keyCell.setBorder(Rectangle.NO_BORDER);
		keyCell.setPadding(5);
		// keyCell.setBackgroundColor(COLOR_BG_HEADER);
		
		PdfPCell valueCell = new PdfPCell(new Phrase(value, FONT_BODY));
		valueCell.setBorder(Rectangle.NO_BORDER);
		valueCell.setPadding(5);

		table.addCell(keyCell);
		table.addCell(valueCell);
	}

	private void addHeadersTable(Document document, Map<String, String> headers) throws DocumentException {
		if (headers == null || headers.isEmpty()) {
			document.add(new Paragraph("(No headers)", FONT_BODY));
			return;
		}

		PdfPTable table = new PdfPTable(2);
		table.setWidthPercentage(100);
		table.setWidths(new float[] { 1, 2 });
		table.setHeaderRows(1);

		// Header
		PdfPCell h1 = new PdfPCell(new Phrase("Name", FONT_TABLE_HEADER));
		h1.setBackgroundColor(COLOR_BG_HEADER);
		h1.setPadding(5);
		table.addCell(h1);

		PdfPCell h2 = new PdfPCell(new Phrase("Value", FONT_TABLE_HEADER));
		h2.setBackgroundColor(COLOR_BG_HEADER);
		h2.setPadding(5);
		table.addCell(h2);

		// Data
		boolean alternate = false;
		for (Map.Entry<String, String> entry : headers.entrySet()) {
			Color bg = alternate ? COLOR_BG_CODE : Color.WHITE;
			
			PdfPCell c1 = new PdfPCell(new Phrase(entry.getKey(), FONT_CODE));
			c1.setPadding(5);
			c1.setBackgroundColor(bg);
			table.addCell(c1);

			PdfPCell c2 = new PdfPCell(new Phrase(entry.getValue(), FONT_CODE));
			c2.setPadding(5);
			c2.setBackgroundColor(bg);
			table.addCell(c2);
			
			alternate = !alternate;
		}

		document.add(table);
	}



	private String formatBody(String content) {
		if (content == null || content.isBlank()) {
			return content;
		}
		String trimmed = content.trim();
		// JSON detection
		if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
			try {
				com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
				Object json = mapper.readValue(content, Object.class);
				return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
			} catch (Exception e) {
				// Not valid JSON or error parsing, return original
				return content;
			}
		} 
		// XML detection
		else if (trimmed.startsWith("<") && trimmed.endsWith(">")) {
			try {
				javax.xml.parsers.DocumentBuilderFactory dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
				dbf.setValidating(false);
				// Prevent XXE
				dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
				javax.xml.parsers.DocumentBuilder db = dbf.newDocumentBuilder();
				org.w3c.dom.Document doc = db.parse(new org.xml.sax.InputSource(new java.io.StringReader(content)));
				
				javax.xml.transform.Transformer tf = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
				tf.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");
				tf.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
				tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
				
				java.io.StringWriter writer = new java.io.StringWriter();
				tf.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(writer));
				return writer.toString();
			} catch (Exception e) {
				 // Not valid XML or error parsing, return original
				 return content;
			}
		}
		return content;
	}

	private void addCodeBlock(Document document, String content) throws DocumentException {
		if (content == null || content.isEmpty()) {
			document.add(new Paragraph("(Empty)", FONT_BODY));
			return;
		}

		PdfPTable table = new PdfPTable(1);
		table.setWidthPercentage(100);
		
		PdfPCell cell = new PdfPCell(new Phrase(content, FONT_CODE));
		cell.setBackgroundColor(COLOR_BG_CODE);
		cell.setPadding(10);
		cell.setBorderColor(Color.LIGHT_GRAY);
		
		table.addCell(cell);
		document.add(table);
	}
}
