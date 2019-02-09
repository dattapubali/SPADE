package spade.storage.wlog.jgraph;

import java.text.MessageFormat;

public class ULogLink {
	public ULogNode src;
	public ULogNode dst;
	
	public ULogLink(ULogNode s, ULogNode d) {
		src = s;
		dst = d;
	}
	
	public String toString() {
		return MessageFormat.format("{} --> {}", src, dst);
	}
}
