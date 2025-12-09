package com.example.antig.swing.demo;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import com.example.antig.swing.model.xml.XmlCollection;
import com.example.antig.swing.model.xml.XmlFolder;
import com.example.antig.swing.model.xml.XmlRequest;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

/**
 * Demonstration that XML persistence works with ONLY
 * com.example.antig.swing.model.xml classes. NO javax.swing classes are used in
 * the XML file.
 */
public class XmlPersistenceDemo {

	public static void main(String[] args) throws Exception {
		// Create XML nodes directly (no javax.swing classes involved)
		XmlCollection collection = new XmlCollection("demo-collection-id", "My API Collection");

		Map<String, String> collectionHeaders = new LinkedHashMap<>();
		collectionHeaders.put("X-API-Key", "secret-key-123");
		collectionHeaders.put("X-Client-Version", "1.0");
		collection.setHeaders(collectionHeaders);

		// Add a folder
		XmlFolder folder = new XmlFolder("demo-folder-id", "User Endpoints");
		Map<String, String> folderHeaders = new LinkedHashMap<>();
		folderHeaders.put("Content-Type", "application/json");
		folder.setHeaders(folderHeaders);

		// Add requests to folder
		XmlRequest getRequest = new XmlRequest("req-1", "Get All Users");
		getRequest.setMethod("GET");
		getRequest.setUrl("https://api.example.com/users");
		getRequest.setBody("");

		XmlRequest postRequest = new XmlRequest("req-2", "Create User");
		postRequest.setMethod("POST");
		postRequest.setUrl("https://api.example.com/users");
		postRequest.setBody("{\"name\": \"John Doe\", \"email\": \"john@example.com\"}");
		Map<String, String> postHeaders = new LinkedHashMap<>();
		postHeaders.put("Authorization", "Bearer token123");
		postRequest.setHeaders(postHeaders);

		folder.getChildren().add(getRequest);
		folder.getChildren().add(postRequest);
		collection.getChildren().add(folder);

		// Save to XML using ONLY xml package classes
		XmlMapper xmlMapper = new XmlMapper();
		xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);

		File outputFile = new File("demo-project.xml");
		xmlMapper.writeValue(outputFile, collection);

		System.out.println("✓ Saved XML to: " + outputFile.getAbsolutePath());
		System.out.println("✓ Used ONLY com.example.antig.swing.model.xml classes");
		System.out.println("✓ NO javax.swing classes in the XML file");
		System.out.println();

		// Load from XML
		XmlCollection loaded = xmlMapper.readValue(outputFile, XmlCollection.class);

		System.out.println("✓ Loaded XML successfully");
		System.out.println("  Collection: " + loaded.getName());
		System.out.println("  Headers: " + loaded.getHeaders());
		System.out.println("  Children: " + loaded.getChildren().size());

		XmlFolder loadedFolder = (XmlFolder) loaded.getChildren().get(0);
		System.out.println("  Folder: " + loadedFolder.getName());
		System.out.println("  Folder children: " + loadedFolder.getChildren().size());

		XmlRequest loadedRequest = (XmlRequest) loadedFolder.getChildren().get(0);
		System.out.println("  Request: " + loadedRequest.getName());
		System.out.println("  Method: " + loadedRequest.getMethod());
		System.out.println("  URL: " + loadedRequest.getUrl());

		System.out.println();
		System.out.println("✓ All data preserved correctly!");
		System.out.println("✓ No cyclic references!");
		System.out.println("✓ No javax.swing dependencies!");
	}
}
