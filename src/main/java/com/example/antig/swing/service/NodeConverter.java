package com.example.antig.swing.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.example.antig.swing.model.PostmanCollection;
import com.example.antig.swing.model.PostmanFolder;
import com.example.antig.swing.model.PostmanNode;
import com.example.antig.swing.model.PostmanRequest;
import com.example.antig.swing.model.xml.XmlCollection;
import com.example.antig.swing.model.xml.XmlFolder;
import com.example.antig.swing.model.xml.XmlNode;
import com.example.antig.swing.model.xml.XmlRequest;

/**
 * Utility class for converting between Swing tree nodes (PostmanNode hierarchy)
 * and XML-safe nodes (XmlNode hierarchy).
 * 
 * The XML nodes contain no parent references, only children, to avoid cyclic
 * dependencies during XML marshalling/unmarshalling.
 */
public class NodeConverter {

	/**
	 * Convert a PostmanNode tree to an XmlNode tree. Recursively processes all
	 * children.
	 * 
	 * @param postmanNode the root PostmanNode to convert
	 * @return the corresponding XmlNode
	 */
	public static XmlNode toXmlNode(PostmanNode postmanNode) {
		return toXmlNode(postmanNode, null, 0, 100);
	}

	/**
	 * Convert a PostmanNode tree to an XmlNode tree with expansion state.
	 */
	public static XmlNode toXmlNode(PostmanNode postmanNode, java.util.Set<String> expandedNodeIds) {
		return toXmlNode(postmanNode, expandedNodeIds, 0, 100);
	}

	/**
	 * Internal method with depth limiting to prevent stack overflow.
	 */
	private static XmlNode toXmlNode(PostmanNode postmanNode, java.util.Set<String> expandedNodeIds, int depth,
			int maxDepth) {
		if (postmanNode == null) {
			return null;
		}

		if (depth > maxDepth) {
			System.err
					.println("WARNING: Maximum recursion depth reached during node conversion. Possible circular reference.");
			return null;
		}

		XmlNode xmlNode;

		if (postmanNode instanceof PostmanCollection) {
			PostmanCollection collection = (PostmanCollection) postmanNode;
			XmlCollection xmlCollection = new XmlCollection(collection.getId(), collection.getName());

			// Defensive null checks
			if (collection.getEnvironment() != null) {
				xmlCollection.setEnvironment(new LinkedHashMap<>(collection.getEnvironment()));
			}
			if (collection.getHeaders() != null) {
				xmlCollection.setHeaders(new LinkedHashMap<>(collection.getHeaders()));
			}
			xmlCollection.setPrescript(collection.getPrescript() != null ? collection.getPrescript() : "");
			xmlCollection.setPostscript(collection.getPostscript() != null ? collection.getPostscript() : "");
			xmlCollection.setPrescript(collection.getPrescript() != null ? collection.getPrescript() : "");
			xmlCollection.setPostscript(collection.getPostscript() != null ? collection.getPostscript() : "");
			xmlCollection.setSelectedTabIndex(collection.getSelectedTabIndex());
			xmlCollection.setSelectedTabIndex(collection.getSelectedTabIndex());
			xmlCollection.setLastSelectedNodeId(collection.getLastSelectedNodeId());
			if (collection.getGlobalVariables() != null) {
				xmlCollection.setGlobalVariables(new LinkedHashMap<>(collection.getGlobalVariables()));
			}

			// Convert children - use actual tree structure, not the children list
			List<XmlNode> xmlChildren = new ArrayList<>();
			int childCount = collection.getChildCount();
			for (int i = 0; i < childCount; i++) {
				Object child = collection.getChildAt(i);
				if (child instanceof PostmanNode) {
					XmlNode xmlChild = toXmlNode((PostmanNode) child, expandedNodeIds, depth + 1, maxDepth);
					if (xmlChild != null) {
						xmlChildren.add(xmlChild);
					}
				}
			}
			xmlCollection.setChildren(xmlChildren);

			// Save expansion state
			if (expandedNodeIds != null) {
				xmlCollection.setExpanded(expandedNodeIds.contains(collection.getId()));
			}

			xmlNode = xmlCollection;

		} else if (postmanNode instanceof PostmanFolder) {
			PostmanFolder folder = (PostmanFolder) postmanNode;
			XmlFolder xmlFolder = new XmlFolder(folder.getId(), folder.getName());

			// Defensive null checks
			if (folder.getEnvironment() != null) {
				xmlFolder.setEnvironment(new LinkedHashMap<>(folder.getEnvironment()));
			}
			if (folder.getHeaders() != null) {
				xmlFolder.setHeaders(new LinkedHashMap<>(folder.getHeaders()));
			}
			xmlFolder.setPrescript(folder.getPrescript() != null ? folder.getPrescript() : "");
			xmlFolder.setPostscript(folder.getPostscript() != null ? folder.getPostscript() : "");
			xmlFolder.setSelectedTabIndex(folder.getSelectedTabIndex());

			// Convert children - use actual tree structure, not the children list
			List<XmlNode> xmlChildren = new ArrayList<>();
			int childCount = folder.getChildCount();
			for (int i = 0; i < childCount; i++) {
				Object child = folder.getChildAt(i);
				if (child instanceof PostmanNode) {
					XmlNode xmlChild = toXmlNode((PostmanNode) child, expandedNodeIds, depth + 1, maxDepth);
					if (xmlChild != null) {
						xmlChildren.add(xmlChild);
					}
				}
			}
			xmlFolder.setChildren(xmlChildren);

			// Save expansion state
			if (expandedNodeIds != null) {
				xmlFolder.setExpanded(expandedNodeIds.contains(folder.getId()));
			}

			xmlNode = xmlFolder;

		} else if (postmanNode instanceof PostmanRequest) {
			PostmanRequest request = (PostmanRequest) postmanNode;
			XmlRequest xmlRequest = new XmlRequest(request.getId(), request.getName());

			// Defensive null checks
			if (request.getEnvironment() != null) {
				xmlRequest.setEnvironment(new LinkedHashMap<>(request.getEnvironment()));
			}
			if (request.getHeaders() != null) {
				xmlRequest.setHeaders(new LinkedHashMap<>(request.getHeaders()));
			}
			xmlRequest.setPrescript(request.getPrescript() != null ? request.getPrescript() : "");
			xmlRequest.setPostscript(request.getPostscript() != null ? request.getPostscript() : "");
			xmlRequest.setSelectedTabIndex(request.getSelectedTabIndex());
			xmlRequest.setMethod(request.getMethod() != null ? request.getMethod() : "GET");
			xmlRequest.setUrl(request.getUrl() != null ? request.getUrl() : "");
			xmlRequest.setBody(request.getBody() != null ? request.getBody() : "");
			xmlRequest.setParams(request.getParams() != null ? request.getParams() : "");
			xmlRequest.setBodyType(request.getBodyType() != null ? request.getBodyType() : "TEXT");
			xmlRequest.setExecutionTabIndex(request.getExecutionTabIndex());
			xmlRequest.setTimeout(request.getTimeout());
			xmlRequest.setHttpVersion(request.getHttpVersion() != null ? request.getHttpVersion() : "HTTP/1.1");
			xmlNode = xmlRequest;

		} else {
			// Unknown type, skip
			return null;
		}

		return xmlNode;
	}

	/**
	 * Convert an XmlNode tree to a PostmanNode tree. Recursively processes all
	 * children and rebuilds the tree structure.
	 * 
	 * @param xmlNode the root XmlNode to convert
	 * @return the corresponding PostmanNode
	 */
	public static PostmanNode toPostmanNode(XmlNode xmlNode) {
		return toPostmanNode(xmlNode, 0, 100); // Max depth of 100 to prevent infinite recursion
	}

	/**
	 * Internal method with depth limiting to prevent stack overflow.
	 */
	private static PostmanNode toPostmanNode(XmlNode xmlNode, int depth, int maxDepth) {
		if (xmlNode == null) {
			return null;
		}

		if (depth > maxDepth) {
			System.err.println(
					"WARNING: Maximum recursion depth reached during node conversion. Possible circular reference in XML data.");
			return null;
		}

		PostmanNode postmanNode;

		if (xmlNode instanceof XmlCollection) {
			XmlCollection xmlCollection = (XmlCollection) xmlNode;
			PostmanCollection collection = new PostmanCollection(xmlCollection.getName());
			collection.setId(xmlCollection.getId());

			// Defensive null checks
			if (xmlCollection.getEnvironment() != null) {
				collection.setEnvironment(new LinkedHashMap<>(xmlCollection.getEnvironment()));
			}
			if (xmlCollection.getHeaders() != null) {
				collection.setHeaders(new LinkedHashMap<>(xmlCollection.getHeaders()));
			}
			collection.setPrescript(xmlCollection.getPrescript() != null ? xmlCollection.getPrescript() : "");
			collection.setPostscript(xmlCollection.getPostscript() != null ? xmlCollection.getPostscript() : "");
			collection.setPrescript(xmlCollection.getPrescript() != null ? xmlCollection.getPrescript() : "");
			collection.setPostscript(xmlCollection.getPostscript() != null ? xmlCollection.getPostscript() : "");
			collection.setSelectedTabIndex(xmlCollection.getSelectedTabIndex());
			collection.setSelectedTabIndex(xmlCollection.getSelectedTabIndex());
			collection.setLastSelectedNodeId(xmlCollection.getLastSelectedNodeId());
			if (xmlCollection.getGlobalVariables() != null) {
				collection.setGlobalVariables(new LinkedHashMap<>(xmlCollection.getGlobalVariables()));
			}

			// Convert children
			if (xmlCollection.getChildren() != null) {
				for (XmlNode xmlChild : xmlCollection.getChildren()) {
					PostmanNode postmanChild = toPostmanNode(xmlChild, depth + 1, maxDepth);
					if (postmanChild != null) {
						collection.add(postmanChild);
					}
				}
			}
			postmanNode = collection;

		} else if (xmlNode instanceof XmlFolder) {
			XmlFolder xmlFolder = (XmlFolder) xmlNode;
			PostmanFolder folder = new PostmanFolder(xmlFolder.getName());
			folder.setId(xmlFolder.getId());

			// Defensive null checks
			if (xmlFolder.getEnvironment() != null) {
				folder.setEnvironment(new LinkedHashMap<>(xmlFolder.getEnvironment()));
			}
			if (xmlFolder.getHeaders() != null) {
				folder.setHeaders(new LinkedHashMap<>(xmlFolder.getHeaders()));
			}
			folder.setPrescript(xmlFolder.getPrescript() != null ? xmlFolder.getPrescript() : "");
			folder.setPostscript(xmlFolder.getPostscript() != null ? xmlFolder.getPostscript() : "");
			folder.setSelectedTabIndex(xmlFolder.getSelectedTabIndex());

			// Convert children
			if (xmlFolder.getChildren() != null) {
				for (XmlNode xmlChild : xmlFolder.getChildren()) {
					PostmanNode postmanChild = toPostmanNode(xmlChild, depth + 1, maxDepth);
					if (postmanChild != null) {
						folder.add(postmanChild);
					}
				}
			}
			postmanNode = folder;

		} else if (xmlNode instanceof XmlRequest) {
			XmlRequest xmlRequest = (XmlRequest) xmlNode;
			PostmanRequest request = new PostmanRequest(xmlRequest.getName());
			request.setId(xmlRequest.getId());

			// Defensive null checks
			if (xmlRequest.getEnvironment() != null) {
				request.setEnvironment(new LinkedHashMap<>(xmlRequest.getEnvironment()));
			}
			if (xmlRequest.getHeaders() != null) {
				request.setHeaders(new LinkedHashMap<>(xmlRequest.getHeaders()));
			}
			request.setPrescript(xmlRequest.getPrescript() != null ? xmlRequest.getPrescript() : "");
			request.setPostscript(xmlRequest.getPostscript() != null ? xmlRequest.getPostscript() : "");
			request.setSelectedTabIndex(xmlRequest.getSelectedTabIndex());
			request.setMethod(xmlRequest.getMethod() != null ? xmlRequest.getMethod() : "GET");
			request.setUrl(xmlRequest.getUrl() != null ? xmlRequest.getUrl() : "");
			request.setBody(xmlRequest.getBody() != null ? xmlRequest.getBody() : "");
			request.setParams(xmlRequest.getParams() != null ? xmlRequest.getParams() : "");
			request.setBodyType(xmlRequest.getBodyType() != null ? xmlRequest.getBodyType() : "TEXT");
			request.setExecutionTabIndex(xmlRequest.getExecutionTabIndex());
			request.setTimeout(xmlRequest.getTimeout());
			request.setHttpVersion(xmlRequest.getHttpVersion() != null ? xmlRequest.getHttpVersion() : "HTTP/1.1");
			postmanNode = request;

		} else {
			// Unknown type, skip
			return null;
		}

		return postmanNode;
	}

	/**
	 * Collect IDs of nodes that were marked as expanded in the XML.
	 */
	public static void collectExpandedNodeIds(XmlNode xmlNode, java.util.Set<String> expandedIds) {
		if (xmlNode == null || expandedIds == null) {
			return;
		}

		if (xmlNode.isExpanded()) {
			expandedIds.add(xmlNode.getId());
		}

		// Check children
		if (xmlNode instanceof com.example.antig.swing.model.xml.XmlCollection) {
			com.example.antig.swing.model.xml.XmlCollection collection = (com.example.antig.swing.model.xml.XmlCollection) xmlNode;
			if (collection.getChildren() != null) {
				for (XmlNode child : collection.getChildren()) {
					collectExpandedNodeIds(child, expandedIds);
				}
			}
		} else if (xmlNode instanceof com.example.antig.swing.model.xml.XmlFolder) {
			com.example.antig.swing.model.xml.XmlFolder folder = (com.example.antig.swing.model.xml.XmlFolder) xmlNode;
			if (folder.getChildren() != null) {
				for (XmlNode child : folder.getChildren()) {
					collectExpandedNodeIds(child, expandedIds);
				}
			}
		}
	}
}
