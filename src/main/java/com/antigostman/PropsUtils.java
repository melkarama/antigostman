
package com.antigostman;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PropsUtils {

	/**
	 * Public entry point taking a map of raw properties (key -> value), resolving
	 * using Velocity with provided variables (context).
	 */
	public Map<String, String> parse(Map<String, String> map, Map<String, ?> variables) {
		Properties p = new Properties();
		p.putAll(map);

		try (StringWriter pw = new StringWriter()) {
			p.store(pw, null);
			return parse2(pw.toString(), variables);
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Public entry point taking the textual content of a .properties file.
	 */
	public Map<String, String> parse2(String content, Map<String, ?> variables) {
		return parseInternal(content, variables);
	}

	/**
	 * Core parser: normalizes tokens, loads properties, then resolves values via
	 * Velocity evaluate (multi-pass until convergence).
	 */
	private Map<String, String> parseInternal(String content, Map<String, ?> contextVars) {
		// --- 1) Normalize to support {{ var }} and keep ${var} as expected ---
		String input = normalizePlaceholders(content);

		try (StringReader r2 = new StringReader(input)) {
			PropertiesConfiguration config = new PropertiesConfiguration();
			config.read(r2);

			Map<String, String> map = new LinkedHashMap<>();
			config.getKeys().forEachRemaining(key -> map.put(key, config.getString(key)));

			// --- 2) Resolve with Velocity if we have context vars (and/or self references)
			// ---
			if (contextVars != null && !contextVars.isEmpty()) {
				resolveWithVelocity(map, contextVars);
			} else {
				// Still resolve in case values reference other keys (self-references)
				resolveWithVelocity(map, Map.of());
			}

			return map;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * Convert {{ var }} and {{ var }} to ${var}. Velocity already supports $var and
	 * ${var}; we only normalize double-curly inputs.
	 */
	private String normalizePlaceholders(String content) {
		String input = content;

		// {{ var }} -> ${var}
		{
			Pattern p = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}\\}",
					Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
			Matcher m = p.matcher(input);

			StringBuilder sb = new StringBuilder();
			while (m.find()) {
				m.appendReplacement(sb, "\\${" + m.group(1) + "}");
			}
			m.appendTail(sb);
			input = sb.toString();
		}

		// NOTE: Velocity already handles $name and ${name}, so we don't need to rewrite
		// $name → ${name}.
		return input;
	}

	/**
	 * Evaluate each property value via Velocity, iteratively, because a value may
	 * depend on other keys that get resolved in earlier passes.
	 */
	private void resolveWithVelocity(Map<String, String> map, Map<String, ?> userContext) {
		VelocityEngine engine = new VelocityEngine();

		// Config: lenient by default; set to true if you want strict failures
		engine.setProperty(RuntimeConstants.RUNTIME_REFERENCES_STRICT, Boolean.FALSE);
		// Allow hyphens in identifiers if your keys/variables use them
		engine.setProperty("parser.allow_hyphen_in_identifiers", Boolean.TRUE);
		engine.init();

		boolean changed;
		int maxIterations = 10;
		int iteration = 0;

		do {
			changed = false;
			iteration++;

			for (Map.Entry<String, String> entry : map.entrySet()) {
				String original = entry.getValue();
				String resolved = renderValue(engine, map, userContext, original);

				if (!original.equals(resolved)) {
					entry.setValue(resolved);
					changed = true;
				}
			}
		} while (changed && iteration < maxIterations);
	}

	private String renderValue(VelocityEngine engine, Map<String, String> currentMap, Map<String, ?> userContext,
			String template) {
		VelocityContext vc = new VelocityContext();

		// Put user variables/tools (e.g., "utils")
		if (userContext != null) {
			userContext.forEach(vc::put);
		}
		// Put current properties (so $otherKey works; keep last-writer-wins semantics)
		currentMap.forEach(vc::put);

		StringWriter out = new StringWriter();
		engine.evaluate(vc, out, "PropsUtils", template);
		return out.toString();
	}
}
