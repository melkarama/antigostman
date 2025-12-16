package com.antigostman;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.UUID;

public class Utils {

	private static final Random RND = new SecureRandom();

	public String uuid() {
		return UUID.randomUUID().toString();
	}

	public String dt(String format) {
		return DateTimeFormatter.ofPattern(format).format(LocalDateTime.now());
	}

	public String dt() {
		return dt("yyMMddHHmmss");
	}

	public String rnd(String cfg, int length) {

		cfg = cfg.toLowerCase();

		String choice = "";

		if (cfg.contains("a")) {
			for (char c = 'a'; c <= 'z'; c++) {
				choice += c;
			}
		}
		if (cfg.contains("n")) {
			for (char c = '0'; c <= '9'; c++) {
				choice += c;
			}
		}
		if (cfg.contains("s")) {
			choice += "@#./:\\;,$£§+=-*&{}()";
		}

		if (cfg.contains("u")) {
			String s2 = choice.toUpperCase();
			if (cfg.contains("l")) {
				s2 += choice.toLowerCase();
			}
			choice = s2;
		} else if (cfg.contains("l")) {
			String s2 = choice.toLowerCase();
			if (cfg.contains("u")) {
				s2 += choice.toUpperCase();
			}
			choice = s2;
		}

		String s = "";

		for (int i = 0; i < length; i++) {
			char c = choice.charAt(RND.nextInt(choice.length()));
			s += c;
		}

		return s;
	}

}
