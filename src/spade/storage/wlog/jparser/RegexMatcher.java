package spade.storage.wlog.jparser;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class RegexMatcher implements FormatMatcher {
	private final static String regex = "%([^ \\t\\n\\r\\f\\v%%*]+)";
	private final static Pattern p = Pattern.compile(regex);

	private static final Logger l = LogManager.getLogger(RegexMatcher.class.getName());
	
	/** format specifier to regex map */
	private static final Map<Character, String> fmtMap;
	static {
		Map<Character, String> tmpMap = new HashMap<Character, String>();
		tmpMap.put('c', ".");
		tmpMap.put('d', "[-+]?\\d+");
		tmpMap.put('u', "\\d+");
		tmpMap.put('e', "[-+]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?");
		tmpMap.put('E', "[-+]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?");
		tmpMap.put('f', "[-+]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?");
		tmpMap.put('g', "[-+]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][-+]?\\d+)?");
		tmpMap.put('i', "[-+]?(0[xX][\\dA-Fa-f]+|0[0-7]*|\\d+)");
		tmpMap.put('o', "[-+]?[0-7]+");
		tmpMap.put('s', "[\\s\\S]*");
		tmpMap.put('x', "[-+]?(0[xX])?[\\dA-Fa-f]+");
		tmpMap.put('X', "[-+]?(0[xX])?[\\dA-Fa-f]+");
		tmpMap.put('p', "(0[xX])?[\\dA-Fa-f]+");
		
		fmtMap = Collections.unmodifiableMap(tmpMap);
	}

	public int IsMatch(String fmt, String in) {
		if (fmt == in) {
			// stupid case when there is an exact match
			return GetWordCount(fmt);
		}
		
		// sanitize the format specifier
		String fmtClean = CleanupString(fmt);
		
		// start matching backwards since usually, the logging functions will prepend 
		// and not append things to the string.
		int[] numConsts = new int[1];
		String regString = BuildRegexString(fmtClean, numConsts);
		if (regString == null)
			return 0;
		
		// build the pattern
		l.debug("Checking {} against {}", in, regString);
		Pattern regp = Pattern.compile(regString);
		Matcher m = regp.matcher(in);
		
		while (m.find()) {
			return numConsts[0];
		}
		return 0;
	}
	
	/** GetWordCount - Obtain the word count in a string
	 * 
	 * @param message:		The message to count the number of words in
	 * 
	 * @return: int: The number words separated by spaces in the message
	 */
	private int GetWordCount(String message) {
		String trimmed = message.trim();
		return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
	}
	
	private String BuildRegexString(String fmt, int[] numConsts) {
		// build the equivalent regex string from the format specifier
		Matcher m = p.matcher(fmt);
		
		String result = fmt;
		numConsts[0] = GetWordCount(fmt);
		while(m.find()) {
			String spec = m.group();
			if (spec == null) {
				l.debug("BuildRegexString ERROR: Hit an empty spec from parsing {}", fmt);
				continue;
			}
			String expr = ParseSpecifier(spec);
			if (expr == null) {
				l.debug("BuildRegexString: Cannot parse the format specifier {} into a regex expression, skipping...", spec);
				continue;
			}
			
			numConsts[0]--;
			result = result.replace(spec, expr);
		}
		
		l.debug("Replaced {} with {}", fmt, result);
		return result;
	}
	
	private String ParseSpecifier (String specifier) {
		char lastChar = specifier.charAt(specifier.length()-1);
		int len = specifier.length()-1;
		while (!Character.isLetter(lastChar)) {
			len = len - 1;
			if (len >= 0) {
				lastChar = specifier.charAt(len);
			} else {
				l.error("Malformed format specifier {}", specifier);
				return null;
			}
		}
		
		if (len == specifier.length() - 1)
			return fmtMap.get(lastChar);
		
		// need to attach the invalid characters
		l.debug("Appending {} to regex", specifier.substring(len+1, specifier.length()));
		return fmtMap.get(lastChar) + specifier.substring(len+1,  specifier.length());
	}
	
	private String CleanupString(String in) {
		StringBuilder sb = new StringBuilder();
		for (Character c : in.toCharArray()) {
			if (c == '*' || c == '+' || c == '?') {
				sb.append("\\" + c);
			} else {
				sb.append(c);
			}
		}
		
		return sb.toString();
	}
	
	public static void main(String[] args) {
		String fmt = "This is a test: %s hello %d %0.8lf world %%s:";
		String sshFmt = "Disconnected from %.200s port %d";
		String sshLog = "Disconnected from user vagrant 192.168.121.1 port 42862";
		String bad = "?%uA shutdown timeout ";

		String in = "This is a test: world hello 55 12354 world %woops:";
		String notin = "This is b test: world hello 55 12354 world %woops:";
		String in2 = "[Some logging info]: " + in;
		String in3 = "[Some logging info]: " + notin;

		RegexMatcher bm = new RegexMatcher();
		l.info(bm.IsMatch(fmt, in));
		l.info(bm.IsMatch(fmt, notin));
		l.info(bm.IsMatch(fmt, in2));
		l.info(bm.IsMatch(fmt, in3));
		
		l.info("{} becomes {}", bad, bm.CleanupString(bad));
		l.info(bm.IsMatch(bad, in3));
		
		l.info("Checking {} with {}", sshFmt, sshLog);
		l.info(bm.IsMatch(sshFmt, sshLog));
	}
}
