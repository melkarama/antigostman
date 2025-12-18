package com.antigostman.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.antigostman.model.PostmanCollection;
import com.antigostman.model.PostmanFolder;
import com.antigostman.model.PostmanNode;
import com.antigostman.model.PostmanRequest;
import com.antigostman.model.postman.PostmanCollectionV2;

/**
 * Converts Postman Collection V2.x to Antigostman internal model
 */
public class PostmanImportService {

	/**
	 * Convert a Postman Collection V2 to Antigostman PostmanCollection
	 */
	public PostmanCollection convertToAntigostman(PostmanCollectionV2 postmanCollection) {
		if (postmanCollection == null) {
			throw new IllegalArgumentException("Postman collection cannot be null");
		}

		PostmanCollection antigostmanCollection = new PostmanCollection();

		// Set collection name
		if (postmanCollection.getInfo() != null) {
			antigostmanCollection.setName(postmanCollection.getInfo().getName());
			antigostmanCollection.setDescription(postmanCollection.getInfo().getDescriptionAsString());
		} else {
			antigostmanCollection.setName("Imported Collection");
		}

		// Convert collection-level variables to environment
		Map<String, String> environment = new LinkedHashMap<>();
		if (postmanCollection.getVariable() != null) {
			for (PostmanCollectionV2.Variable var : postmanCollection.getVariable()) {
				if (var.getKey() != null) {
					String value = var.getValue() != null ? var.getValue() : "";
					environment.put(var.getKey(), value);
				}
			}
		}
		antigostmanCollection.setEnvironment(environment);

		// Convert items (folders and requests)
		if (postmanCollection.getItem() != null) {
			for (PostmanCollectionV2.Item item : postmanCollection.getItem()) {
				PostmanNode childNode = convertItem(item);
				if (childNode != null) {
					antigostmanCollection.add(childNode);
				}
			}
		}

		return antigostmanCollection;
	}

	/**
	 * Convert a Postman Item (can be a folder or request)
	 */
	private PostmanNode convertItem(PostmanCollectionV2.Item item) {
		if (item == null) {
			return null;
		}

		// Check if this is a folder (has nested items)
		if (item.isFolder()) {
			return convertFolder(item);
		} else {
			return convertRequest(item);
		}
	}

	/**
	 * Convert a Postman folder
	 */
	private PostmanFolder convertFolder(PostmanCollectionV2.Item folderItem) {
		PostmanFolder folder = new PostmanFolder();
		folder.setName(folderItem.getName() != null ? folderItem.getName() : "Unnamed Folder");
		folder.setDescription(folderItem.getDescriptionAsString());

		// Convert events (scripts)
		convertEvents(folderItem.getEvent(), folder);

		// Convert nested items
		if (folderItem.getItem() != null) {
			for (PostmanCollectionV2.Item nestedItem : folderItem.getItem()) {
				PostmanNode childNode = convertItem(nestedItem);
				if (childNode != null) {
					folder.add(childNode);
				}
			}
		}

		return folder;
	}

	/**
	 * Convert a Postman request
	 */
	private PostmanRequest convertRequest(PostmanCollectionV2.Item requestItem) {
		PostmanRequest request = new PostmanRequest();
		request.setName(requestItem.getName() != null ? requestItem.getName() : "Unnamed Request");
		request.setDescription(requestItem.getDescriptionAsString());

		// Convert events (scripts)
		convertEvents(requestItem.getEvent(), request);

		// Convert request details
		if (requestItem.getRequest() != null) {
			PostmanCollectionV2.Request pmRequest = requestItem.getRequest();

			// Set HTTP method
			String method = pmRequest.getMethod();
			if (method != null) {
				request.setMethod(method.toUpperCase());
			} else {
				request.setMethod("GET");
			}

			// Set URL
			request.setUrl(pmRequest.getUrlAsString());

			// Convert headers
			Map<String, String> headers = new LinkedHashMap<>();
			if (pmRequest.getHeader() != null) {
				for (PostmanCollectionV2.Header header : pmRequest.getHeader()) {
					if (header.getKey() != null && !header.isDisabled()) {
						headers.put(header.getKey(), header.getValue() != null ? header.getValue() : "");
					}
				}
			}
			request.setHeaders(headers);

			// Convert body
			if (pmRequest.getBody() != null) {
				PostmanCollectionV2.Body body = pmRequest.getBody();
				String mode = body.getMode();

				if (mode != null) {
					switch (mode.toLowerCase()) {
					case "raw":
						request.setBodyType(determineBodyType(body));
						request.setBody(body.getRaw() != null ? body.getRaw() : "");
						break;

					case "urlencoded":
						request.setBodyType("FORM ENCODED");
						request.setBody(convertFormParameters(body.getUrlencoded()));
						break;

					case "formdata":
						request.setBodyType("MULTIPART");
						request.setBody(convertFormData(body.getFormdata()));
						break;

					case "file":
					case "graphql":
					default:
						request.setBodyType("TEXT");
						request.setBody(body.getRaw() != null ? body.getRaw() : "");
						break;
					}
				}
			}
		}

		return request;
	}

	/**
	 * Determine body type from Postman raw body
	 */
	private String determineBodyType(PostmanCollectionV2.Body body) {
		if (body.getOptions() != null && body.getOptions().getRaw() != null
				&& body.getOptions().getRaw().getLanguage() != null) {

			String language = body.getOptions().getRaw().getLanguage().toLowerCase();
			switch (language) {
			case "json":
				return "JSON";
			case "xml":
				return "XML";
			case "javascript":
			case "html":
			case "text":
			default:
				return "TEXT";
			}
		}
		return "TEXT";
	}

	/**
	 * Convert Postman form parameters to Antigostman format
	 */
	private String convertFormParameters(List<PostmanCollectionV2.FormParameter> parameters) {
		if (parameters == null || parameters.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		for (PostmanCollectionV2.FormParameter param : parameters) {
			if (!param.isDisabled() && param.getKey() != null) {
				if (builder.length() > 0) {
					builder.append("\n");
				}
				builder.append(param.getKey()).append("=").append(param.getValue() != null ? param.getValue() : "");
			}
		}
		return builder.toString();
	}

	/**
	 * Convert Postman form data (multipart) to Antigostman format
	 */
	private String convertFormData(List<PostmanCollectionV2.FormParameter> formData) {
		if (formData == null || formData.isEmpty()) {
			return "";
		}

		StringBuilder builder = new StringBuilder();
		for (PostmanCollectionV2.FormParameter param : formData) {
			if (!param.isDisabled() && param.getKey() != null) {
				if (builder.length() > 0) {
					builder.append("\n");
				}
				builder.append(param.getKey()).append("=");

				// Handle file uploads
				if ("file".equals(param.getType()) && param.getSrc() != null) {
					builder.append("file:").append(param.getSrc());
				} else {
					builder.append(param.getValue() != null ? param.getValue() : "");
				}
			}
		}
		return builder.toString();
	}

	/**
	 * Convert Postman events (prerequest and test scripts) to Antigostman scripts
	 */
	private void convertEvents(List<PostmanCollectionV2.Event> events, PostmanNode node) {
		if (events == null || events.isEmpty()) {
			return;
		}

		StringBuilder prescript = new StringBuilder();
		StringBuilder postscript = new StringBuilder();

		for (PostmanCollectionV2.Event event : events) {
			if (event.getListen() != null && event.getScript() != null) {
				String script = event.getScript().getExecutionScript();

				if ("prerequest".equals(event.getListen())) {
					if (prescript.length() > 0) {
						prescript.append("\n\n");
					}
					prescript.append(script);
				} else if ("test".equals(event.getListen())) {
					if (postscript.length() > 0) {
						postscript.append("\n\n");
					}
					postscript.append(script);
				}
			}
		}

		if (prescript.length() > 0) {
			node.setPrescript(prescript.toString());
		}
		if (postscript.length() > 0) {
			node.setPostscript(postscript.toString());
		}
	}
}
