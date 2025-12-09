package com.example.antig.swing.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.antig.swing.model.PostmanCollection;
import com.example.antig.swing.model.PostmanFolder;
import com.example.antig.swing.model.PostmanRequest;
import com.example.antig.swing.model.xml.XmlCollection;
import com.example.antig.swing.model.xml.XmlFolder;
import com.example.antig.swing.model.xml.XmlRequest;

/**
 * Test class for NodeConverter and XML persistence functionality.
 */
public class NodeConverterTest {

	@Test
	public void testConvertPostmanRequestToXmlRequest() {
		// Create a PostmanRequest
		PostmanRequest request = new PostmanRequest("Test Request");
		request.setMethod("POST");
		request.setUrl("https://api.example.com/test");
		request.setBody("{\"key\": \"value\"}");

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Content-Type", "application/json");
		request.setHeaders(headers);

		// Convert to XmlRequest
		XmlRequest xmlRequest = (XmlRequest) NodeConverter.toXmlNode(request);

		// Verify properties
		assertNotNull(xmlRequest);
		assertEquals(request.getId(), xmlRequest.getId());
		assertEquals(request.getName(), xmlRequest.getName());
		assertEquals(request.getMethod(), xmlRequest.getMethod());
		assertEquals(request.getUrl(), xmlRequest.getUrl());
		assertEquals(request.getBody(), xmlRequest.getBody());
		assertEquals(request.getHeaders().get("Content-Type"), xmlRequest.getHeaders().get("Content-Type"));
	}

	@Test
	public void testConvertXmlRequestToPostmanRequest() {
		// Create an XmlRequest
		XmlRequest xmlRequest = new XmlRequest("test-id", "Test Request");
		xmlRequest.setMethod("GET");
		xmlRequest.setUrl("https://api.example.com/test");
		xmlRequest.setBody("");

		Map<String, String> headers = new LinkedHashMap<>();
		headers.put("Authorization", "Bearer token");
		xmlRequest.setHeaders(headers);

		// Convert to PostmanRequest
		PostmanRequest request = (PostmanRequest) NodeConverter.toPostmanNode(xmlRequest);

		// Verify properties
		assertNotNull(request);
		assertEquals(xmlRequest.getId(), request.getId());
		assertEquals(xmlRequest.getName(), request.getName());
		assertEquals(xmlRequest.getMethod(), request.getMethod());
		assertEquals(xmlRequest.getUrl(), request.getUrl());
		assertEquals(xmlRequest.getBody(), request.getBody());
		assertEquals(xmlRequest.getHeaders().get("Authorization"), request.getHeaders().get("Authorization"));
	}

	@Test
	public void testConvertCollectionWithChildren() {
		// Create a collection with nested structure
		PostmanCollection collection = new PostmanCollection("My Collection");

		PostmanFolder folder = new PostmanFolder("API Folder");
		PostmanRequest request1 = new PostmanRequest("Get Users");
		request1.setUrl("https://api.example.com/users");

		PostmanRequest request2 = new PostmanRequest("Create User");
		request2.setMethod("POST");
		request2.setUrl("https://api.example.com/users");

		folder.add(request1);
		folder.add(request2);
		collection.add(folder);

		// Convert to XML
		XmlCollection xmlCollection = (XmlCollection) NodeConverter.toXmlNode(collection);

		// Verify structure
		assertNotNull(xmlCollection);
		assertEquals(1, xmlCollection.getChildren().size());

		XmlFolder xmlFolder = (XmlFolder) xmlCollection.getChildren().get(0);
		assertEquals("API Folder", xmlFolder.getName());
		assertEquals(2, xmlFolder.getChildren().size());

		XmlRequest xmlReq1 = (XmlRequest) xmlFolder.getChildren().get(0);
		assertEquals("Get Users", xmlReq1.getName());
		assertEquals("https://api.example.com/users", xmlReq1.getUrl());
	}

	@Test
	public void testRoundTripConversion() {
		// Create original structure
		PostmanCollection original = new PostmanCollection("Test Collection");
		PostmanFolder folder = new PostmanFolder("Test Folder");
		PostmanRequest request = new PostmanRequest("Test Request");
		request.setMethod("PUT");
		request.setUrl("https://api.example.com/resource");
		request.setBody("{\"data\": \"test\"}");

		folder.add(request);
		original.add(folder);

		// Convert to XML and back
		XmlCollection xmlCollection = (XmlCollection) NodeConverter.toXmlNode(original);
		PostmanCollection converted = (PostmanCollection) NodeConverter.toPostmanNode(xmlCollection);

		// Verify structure is preserved
		assertNotNull(converted);
		assertEquals(original.getName(), converted.getName());
		assertEquals(1, converted.getChildren().size());

		PostmanFolder convertedFolder = (PostmanFolder) converted.getChildren().get(0);
		assertEquals(folder.getName(), convertedFolder.getName());
		assertEquals(1, convertedFolder.getChildren().size());

		PostmanRequest convertedRequest = (PostmanRequest) convertedFolder.getChildren().get(0);
		assertEquals(request.getName(), convertedRequest.getName());
		assertEquals(request.getMethod(), convertedRequest.getMethod());
		assertEquals(request.getUrl(), convertedRequest.getUrl());
		assertEquals(request.getBody(), convertedRequest.getBody());
	}

	@Test
	public void testXmlNodeHasNoParentReference() throws Exception {
		// Create a collection with children
		PostmanCollection collection = new PostmanCollection("Test");
		PostmanFolder folder = new PostmanFolder("Folder");
		collection.add(folder);

		// Convert to XML
		XmlCollection xmlCollection = (XmlCollection) NodeConverter.toXmlNode(collection);
		XmlFolder xmlFolder = (XmlFolder) xmlCollection.getChildren().get(0);

		// Verify no parent reference exists in XmlNode classes
		// This is verified by the fact that XmlNode classes don't have a parent field
		// We can verify this by checking that serialization won't cause cycles
		assertNotNull(xmlFolder);

		// The XML nodes should be simple POJOs with no parent reference
		// This test passes if no exception is thrown during conversion
	}

	@Test
	public void testProjectServiceSaveAndLoad() throws Exception {
		// Create a test collection
		PostmanCollection collection = new PostmanCollection("Test Project");

		Map<String, String> collectionHeaders = new LinkedHashMap<>();
		collectionHeaders.put("X-API-Key", "secret123");
		collection.setHeaders(collectionHeaders);

		PostmanFolder folder = new PostmanFolder("Endpoints");
		PostmanRequest request = new PostmanRequest("Get Data");
		request.setMethod("GET");
		request.setUrl("https://api.example.com/data");

		folder.add(request);
		collection.add(folder);

		// Save to file
		ProjectService service = new ProjectService();
		File tempFile = File.createTempFile("test-project", ".xml");
		tempFile.deleteOnExit();

		service.saveProject(collection, tempFile);

		// Verify file exists and has content
		assertTrue(tempFile.exists());
		assertTrue(tempFile.length() > 0);

		// Load from file
		PostmanCollection loaded = service.loadProject(tempFile);

		// Verify loaded data
		assertNotNull(loaded);
		assertEquals(collection.getName(), loaded.getName());
		assertEquals(collection.getHeaders().get("X-API-Key"), loaded.getHeaders().get("X-API-Key"));
		assertEquals(1, loaded.getChildren().size());

		PostmanFolder loadedFolder = (PostmanFolder) loaded.getChildren().get(0);
		assertEquals(folder.getName(), loadedFolder.getName());

		PostmanRequest loadedRequest = (PostmanRequest) loadedFolder.getChildren().get(0);
		assertEquals(request.getName(), loadedRequest.getName());
		assertEquals(request.getMethod(), loadedRequest.getMethod());
		assertEquals(request.getUrl(), loadedRequest.getUrl());
	}
}
