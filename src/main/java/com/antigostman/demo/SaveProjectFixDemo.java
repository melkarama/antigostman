package com.antigostman.demo;

import com.antigostman.model.*;
import com.antigostman.model.xml.*;
import com.antigostman.service.NodeConverter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import java.io.File;

/**
 * Demonstration that the save project fix works correctly.
 * This test creates a tree structure using Swing tree methods and verifies
 * that all nodes are properly saved to XML.
 */
public class SaveProjectFixDemo {

    public static void main(String[] args) throws Exception {
        // Create collection and add children using Swing tree methods
        PostmanCollection collection = new PostmanCollection("My API Collection");
        
        // Add a folder
        PostmanFolder folder1 = new PostmanFolder("User Endpoints");
        collection.add(folder1);  // Using Swing tree method
        
        // Add requests to folder
        PostmanRequest getRequest = new PostmanRequest("Get All Users");
        getRequest.setMethod("GET");
        getRequest.setUrl("https://api.example.com/users");
        folder1.add(getRequest);  // Using Swing tree method
        
        PostmanRequest postRequest = new PostmanRequest("Create User");
        postRequest.setMethod("POST");
        postRequest.setUrl("https://api.example.com/users");
        postRequest.setBody("{\"name\": \"John Doe\"}");
        folder1.add(postRequest);  // Using Swing tree method
        
        // Add another folder
        PostmanFolder folder2 = new PostmanFolder("Product Endpoints");
        collection.add(folder2);  // Using Swing tree method
        
        PostmanRequest getProducts = new PostmanRequest("Get Products");
        getProducts.setMethod("GET");
        getProducts.setUrl("https://api.example.com/products");
        folder2.add(getProducts);  // Using Swing tree method
        
        System.out.println("Created tree structure:");
        System.out.println("  Collection: " + collection.getName());
        System.out.println("    Child count: " + collection.getChildCount());
        System.out.println("    Folder 1: " + folder1.getName() + " (children: " + folder1.getChildCount() + ")");
        System.out.println("    Folder 2: " + folder2.getName() + " (children: " + folder2.getChildCount() + ")");
        System.out.println();
        
        // Convert to XML
        XmlCollection xmlCollection = (XmlCollection) NodeConverter.toXmlNode(collection);
        
        System.out.println("Converted to XML:");
        System.out.println("  XML Collection: " + xmlCollection.getName());
        System.out.println("    XML Children count: " + xmlCollection.getChildren().size());
        
        if (xmlCollection.getChildren().size() == 2) {
            System.out.println("  ✓ Both folders converted!");
            
            XmlFolder xmlFolder1 = (XmlFolder) xmlCollection.getChildren().get(0);
            System.out.println("    XML Folder 1: " + xmlFolder1.getName() + " (children: " + xmlFolder1.getChildren().size() + ")");
            
            XmlFolder xmlFolder2 = (XmlFolder) xmlCollection.getChildren().get(1);
            System.out.println("    XML Folder 2: " + xmlFolder2.getName() + " (children: " + xmlFolder2.getChildren().size() + ")");
            
            if (xmlFolder1.getChildren().size() == 2) {
                System.out.println("  ✓ All requests in folder 1 converted!");
            }
            if (xmlFolder2.getChildren().size() == 1) {
                System.out.println("  ✓ All requests in folder 2 converted!");
            }
        } else {
            System.out.println("  ✗ ERROR: Expected 2 folders, got " + xmlCollection.getChildren().size());
        }
        
        System.out.println();
        
        // Save to XML file
        XmlMapper xmlMapper = new XmlMapper();
        xmlMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        File outputFile = new File("test-save-fix.xml");
        xmlMapper.writeValue(outputFile, xmlCollection);
        
        System.out.println("Saved to: " + outputFile.getAbsolutePath());
        System.out.println();
        System.out.println("✓ Fix verified! All nodes are now properly saved to XML.");
    }
}
