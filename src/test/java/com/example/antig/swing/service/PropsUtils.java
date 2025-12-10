package com.example.antig.swing.service;

import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.mvel2.MVEL;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.MapVariableResolverFactory;

import lombok.experimental.UtilityClass;

@UtilityClass
public class PropsUtils {

	public Map<String, String> parse(String content) throws Exception {
		return parse(content, new LinkedHashMap<>());
	}

	public Map<String, String> parse(String content, Map<String, ?> context) throws Exception {
		Pattern p = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_]*)(?![a-zA-Z0-9_.\\(])");
		Matcher m = p.matcher(content);

		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			m.appendReplacement(sb, "\\${" + m.group(1) + "}");
		}

		m.appendTail(sb);
		String s = sb.toString();

		try (StringReader r2 = new StringReader(s)) {
			PropertiesConfiguration config = new PropertiesConfiguration();
			config.read(r2);
			Map<String, String> map = new LinkedHashMap<>();
			config.getKeys().forEachRemaining(key -> map.put(key, config.getString(key)));

			if (!context.isEmpty()) {
				parse(map, context);
			}

			return map;
		}
	}

	private static void parse(Map<String, String> map, Map<String, ?> context) {
		Map<String, Object> mvelContext = new LinkedHashMap<>(context);
		mvelContext.putAll(map);

		VariableResolverFactory resolverFactory = new MapVariableResolverFactory(mvelContext);

		Pattern exprPattern = Pattern.compile("\\$([a-zA-Z_][a-zA-Z0-9_.(),'\"\\s-]*)");

		boolean changed;
		int maxIterations = 10;
		int iteration = 0;

		do {
			changed = false;
			iteration++;

			for (Map.Entry<String, String> entry : map.entrySet()) {
				String original = entry.getValue();
				String resolved = original;
				Matcher matcher = exprPattern.matcher(original);
				StringBuilder result = new StringBuilder();

				while (matcher.find()) {
					String expr = matcher.group(1);
					String replacement;

					try {
						if (expr.contains("(") || expr.contains(".")) {
							mvelContext.putAll(map);
							resolverFactory = new MapVariableResolverFactory(mvelContext);
							Object evalResult = MVEL.eval(expr, resolverFactory);
							replacement = evalResult != null ? evalResult.toString() : "";
						} else {
							replacement = matcher.group(0);
						}
					} catch (Exception ex) {
						replacement = matcher.group(0);
					}

					matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
				}
				matcher.appendTail(result);

				resolved = result.toString();

				if (!original.equals(resolved)) {
					entry.setValue(resolved);
					changed = true;
				}
			}
		} while (changed && iteration < maxIterations);
	}

}