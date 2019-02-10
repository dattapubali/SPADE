package spade.storage.wlog.jparser;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import spade.storage.wlog.jgraph.GraphParser;
import spade.storage.wlog.jgraph.ULogGraph;
import spade.storage.wlog.jgraph.ULogLink;
import spade.storage.wlog.jgraph.ULogNode;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class JParser {
	private static final Logger l = LogManager.getLogger(JParser.class.getName());
	
	private static final int UnsupportedOperation = 1;

	private String 	inFile;
	private String 	logFile;
	private boolean watch;
	private int lookahead;
	private FormatMatcher		expr;

	// @path is the current path being built by the parser as it is parsing and matching!
	private List<State> 		path;

	// @history is a vector of vectors containing all of the previous paths taken by the parse.
	// Note that is history contains more than one vector, then it is likely that the start of each
	// vector is the head of a new execution unit.
	private List<List<State>> 	history;

	
	private ULogGraph graph;
	
	public enum JMatchType {
		Sequential, /* matched sequentially in the graph */
		Lookahead,  /* matched by performing lookahead */
		Exhaustive, /* matched by performing exhaustive search */
		Heuristic,  /* matched by a heuristic on metada */
		Starting,   /* Base starting state for parsing */
		Failed,     /* Failed matching */
		Unknown,    /* Yet to be known match type */
		Last_One    /* Keep this last one */
	}

	public JParser(String _if, String _lf, boolean _w, FormatMatcher _e, int _lk) {
		inFile 		= _if;
		logFile		= _lf;
		watch		= _w;
		expr		= _e;
		lookahead 	= _lk;
		
		if (watch) {
			l.error("Watch mode is not yet supported");
			System.exit(-UnsupportedOperation);
		}
		
		l.debug("Read lms graph from {}", inFile);
		GraphParser gp = new GraphParser(inFile);
		graph = gp.ReadGraph();
		
		// init path and history 
		path 	= new LinkedList<State> ();
		history = new LinkedList<List<State>> ();
	}

	public class State {
		private ULogNode 	node;
		private ULogGraph	graph;
		private int 		matchLen;
		private JMatchType  matchType;
		// the number of times we couldn't get out of this state
		private int			holdingTime;

		// should never be access publicly
		private Set<ULogNode> visited;
		
		public State(ULogNode _n, ULogGraph _g, int _m) {
			node	 	= _n;
			graph	 	= _g;
			matchLen 	= _m;
			visited  	= null;
			matchType 	= JMatchType.Unknown;
			holdingTime = 0;
		}
		
		public ULogNode GetData() {
			return node;
		}
		
		public Boolean IsPhonyNode(ULogNode n) {
			return n.IsPhonyNode() || n.IsFuncHead() || n.IsFuncOut();
		}
		
		public JMatchType GetMatchType() {
			return matchType;
		}
		
		public void SetMatchType(JMatchType type) {
			matchType = type;
		}
		
		public int GetMatchLen() {
			return matchLen;
		}
		
		public int GetHoldingTime() {
			return holdingTime;
		}
		
		public int IncreaseHoldingTime() {
			holdingTime++;
			return holdingTime;
		}
		
		public List<ULogNode> GetPossibleTransitions() {
			ClearHashSet();
			return GetPossibleTransitionsHelper(node);
		}
		
		public void ClearHashSet() {
			if (visited == null)
				visited = new HashSet<ULogNode>();
			visited.clear();
		}
		
		private List<ULogNode> GetPossibleTransitionsHelper(ULogNode n) {
			List<ULogNode> listOfNodes = new LinkedList<ULogNode>();
			for (ULogLink e : graph.GetOutEdges(n)) {
				if (visited.contains(e.dst)) {
					continue;
				} else {
					visited.add(e.dst);
				}

				if (IsPhonyNode(e.dst)) {
					// skip over the phony nodes and fetch their successors
					listOfNodes.addAll(GetPossibleTransitionsHelper(e.dst));
				} else {
					listOfNodes.add(e.dst);
				}
			}
			return listOfNodes;
		}
		
		public String toString() {
			return MessageFormat.format("< {0} >", (String)node.GetAttribute("val"));
		}
		


		/**
		 * IsLikelyNewExecutionUnit - Check if this state is likely the start of a new execution unit
		 * 
		 * @return true if it might be an execution unit, false otherwise.
		 */
		public Boolean IsLikelyNewExecutionUnit() {
			// TODO: update this to capture loop edges as well.
			return matchType == JMatchType.Exhaustive ||
					matchType == JMatchType.Heuristic;
		}
	}
	
	public void parseAndMatch(String start_from) {
		State sstate = null;
		if (start_from == "start") {
			sstate = new State(graph.FindStartNode(), graph, 0);
			l.debug("Starting from: {}", sstate);
		} else {
			l.info("Will try to find the start state from the first line...");
		}
		
		// start matching from here
		if (sstate != null)
			path.add(sstate);
		
		/** Main parsing loop starts here **/
		try (BufferedReader br = new BufferedReader(new FileReader(logFile))) {
			for (String line; (line = br.readLine()) != null; ) {
				l.debug("==> Processing line: {}", line);
				State nstate = sstate;
				if (sstate == null) {
					sstate = FindStartingState(line);
					if (sstate == null) {
						l.warn("Performing exhaustive search...");
						sstate = DoExhaustiveSearch(line);
					}
					nstate = sstate;
                    // set the match type to starting state
                    nstate.SetMatchType(JMatchType.Starting);
				} else {
					nstate = LookupNextState(sstate, line);
				}

				if (nstate == null && sstate == null) {
					l.warn("Could not find a starting state with the current log message, ignoring...");
				} else if (nstate == null) {
					l.debug("No next state found, performing lookahead...");
					if ((nstate = FindExecStates(line)) != null) {
						l.debug("=> Switching path to a new start state.");
						sstate = nstate;

						// add current path to history
						history.add(new LinkedList<State> (path));

						l.debug("Clearing the current path and resetting");
						path.clear();
						path.add(nstate);
						nstate.SetMatchType(JMatchType.Heuristic);
						
						continue;
					}

					// now try lookahead
					nstate = PerformLookahead(sstate, line);
					if (nstate == null) {
						l.debug("Finally trying to perform an exhaustive search...");
						nstate = DoExhaustiveSearch(line);
						if (nstate != null) {
							nstate.SetMatchType(JMatchType.Exhaustive);
						}
					} else {
						nstate.SetMatchType(JMatchType.Lookahead);
					}
					
					if (nstate == null) {
						// still couldn't find anything
						l.debug("Could not find next state even with lookahead and exhaustive search, ignoring...");
						sstate.IncreaseHoldingTime();
					} else {
						l.debug("=> Advancing with lookahead from {} to {}", sstate, nstate);
						sstate = nstate;
						path.add(nstate);
					}
				} else {
					l.debug("=> Advancing sequentially from {} to {}", sstate, nstate);
					sstate = nstate;
					nstate.SetMatchType(JMatchType.Sequential);
					path.add(nstate);
				}
				l.debug(path);
			}
            // add the last parsed path to history
            history.add(new LinkedList<State> (path));
			l.debug(history);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			return;
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}
	
	/**
	 * GetMaxMatchingState - Return the matching state with the longest constant match
	 * 	Complexity is O(n)
	 * 
	 * @param matchingStates: List:		The list of all possible matching states
	 * @return: State: Returns the state with maximum match length or null if there are none
	 */
	private State GetMaxMatchingState(List<State> matchingStates) {
		// check the longest match and return it
		int maxMatch = -1;
		State mState = null;
		for (State s : matchingStates) {
			if (s.GetMatchLen() > maxMatch) {
				maxMatch = s.GetMatchLen();
				mState = s;
			}
		}
		return mState;
	}
	
	/**
	 * LookupNextState - lookup the next state to go to given the current line
	 * @param s:		The current state 
	 * @param line:		The line we are currently working with
	 * @return: The next state if found, null if cannot find it
	 */
	public State LookupNextState(State s, String line) {
		// use expr.IsMatch(fmt, line)
		List<ULogNode> nextStates = s.GetPossibleTransitions();
		
		l.debug("====> Looking up next possible state using line = {}", line); 
		List<State> matchingStates = new LinkedList<State>();
		for (ULogNode n : nextStates) {
			
			String fmt = (String) n.GetAttribute("val");
			// TODO WAJIH make sure that there is a best possible match here.
			// TODO count the number of non-format specifier and pick the one which has best word match
			int matchLength = expr.IsMatch(fmt, line);
			if (matchLength > 0) {
				// found it, return it 
				l.debug("Found a match for {} in {}.", line, fmt);
				matchingStates.add(new State(n, graph, matchLength));
			}
		}

		// return the max matching state, this will return null if there's none
		return GetMaxMatchingState(matchingStates);
	}
	
	/**
	 * Perform lookahead from the given state to check if we can match
	 * somewhere in the given future. This will keep trying until it hits
	 * the lookahead depth
	 * 
	 * @param state:	The current state to start from 
	 * @param line:		The line we are currently working with
	 * @return a new state to process if any, null otherwise
	 */
	public State PerformLookahead(State state, String line) {
		l.debug("Performing lookahead using depth={}", lookahead);
		List<ULogNode> possibleStates = state.GetPossibleTransitions();
		
		List<State> matchingStates = new LinkedList<State>();
		Set<ULogNode> visited = new HashSet<ULogNode>();
		for (ULogNode n : possibleStates) {
			// check for self loops
			if (n == state.GetData()) {
				int matchLength = CheckLogMatch(n, line);
				if (matchLength > 0) {
					matchingStates.add(new State(state.GetData(), graph, matchLength));
				}
			} else {
				if (!visited.contains(n)) {
					State nxt = LookaheadHelper(n, line, lookahead, state, visited);
					if (nxt != null)
						matchingStates.add(nxt);
					visited.add(n);
				}
			}
		}

		return GetMaxMatchingState(matchingStates);
	}
	
	/**
	 * LookaheadHelper - Helper function for performing lookahead computation
	 * 
	 * @param node:		The node we are working with 
	 * @param line: 	The line we are currently processing
	 * @param lkhd:		The current lookahead value, if 0 then return
	 * @param state:	The state that started the lookahead computation
	 * @return: A state if a match is found, null if not found
	 */
	private State LookaheadHelper(ULogNode node, String line, int lkhd, State state, Set<ULogNode> visited) {
		if (lkhd == 0)
			return null;
		
		// l.debug("Entering lookahead with lkhd = {}", lkhd);
		List<ULogNode> possibleStates = (new State(node, graph, 0)).GetPossibleTransitions();
		List<State> matchingStates = new LinkedList<State> ();
		for (ULogNode n : possibleStates) {
			// essentially, there's no point checking if the state matches since if it would, it should
			// have been caught by the caller before jumping in here, so only move forward if not self 
			// loop in this case
			if (n != node) {
				int matchLength = CheckLogMatch(n, line);
				if (matchLength > 0) {
					matchingStates.add(new State(n, graph, matchLength));
				} else {
						if (!visited.contains(n)) {
						State nxt = LookaheadHelper(n, line, lkhd-1, state, visited);
						if (nxt != null)
							matchingStates.add(nxt);
						visited.add(n);
					}
				}
			}
		}

		return GetMaxMatchingState(matchingStates);
	}
	
	/**
	 * Find states that are likely to be heads of execution units
	 * 
	 * @param line:		The current line to match against
	 * @return A state containing the matched node, if any
	 */
	private State FindExecStates(String line) {
		List <State> matchingStates = new LinkedList<State>();
		for (ULogNode node : graph.GetCache()) {
			int matchLength = CheckLogMatch(node, line);
			if (matchLength > 0 && !node.IsStartNode()) {
				matchingStates.add(new State(node, graph, matchLength));
			}
		}
		return GetMaxMatchingState(matchingStates);
	}
	
	
	/**
	 * Find the starting state from a given line of the log file
	 * 
	 * @param line	The line currently being processed
	 * @return a state if found, null if none
	 */
	public State FindStartingState(String line) {
		List <State> matchingStates = new LinkedList<State>();
		for (ULogNode node : graph.GetCache()) {
			int matchLength = CheckLogMatch(node, line);
			if (matchLength > 0) {
				matchingStates.add(new State(node, graph, matchLength));
			}
		}
		return GetMaxMatchingState(matchingStates);
	}
	
	/**
	 * Perform an exhaustive search over all of the lms in the graph
	 * 
	 * @param line: 	The line to match against
	 * @return: returns a state with the matched node if any, null otherwise.
	 */
	private State DoExhaustiveSearch(String line) {
		long startTime = System.nanoTime();

		List <State> matchingStates = new LinkedList<State>();
		for (Map.Entry<Integer, ULogNode> entry : graph.Nodes().entrySet()) {
			ULogNode node = entry.getValue();
			if (!(node.IsPhonyNode() || node.IsEndNode() 
					|| node.IsFuncHead() || node.IsFuncOut())) {
				int matchLength = CheckLogMatch(node, line);
				if (matchLength > 0) {
					matchingStates.add(new State(node, graph, matchLength));
				}
			}
		}
		State s = GetMaxMatchingState(matchingStates);

		long endTime = System.nanoTime();
		long duration = (endTime - startTime);
		
		l.debug("Ran exhaustive search in {} milliseconds", (duration / 1000000.0));
		return s;
	}
	
	private int CheckLogMatch(ULogNode node, String line) {
		String fmt = (String) node.GetAttribute("val");
		return CheckLogMatch(fmt, line);
	}
	
	private int CheckLogMatch(String fmt, String line) {
		return expr.IsMatch(fmt, line);
	}
	
	public static void main(String[] args) {
		ArgumentParser parser = ArgumentParsers.newFor("JParser").build()
				.defaultHelp(true)
				.description("Parse a log file and match it to a give json graph");
		parser.addArgument("-i", "--input")
				.type(String.class)
				.required(true)
				.help("The input graph file in json format");
		parser.addArgument("-l", "--log")
				.type(String.class)
				.required(true)
				.help("The input log file to read from");
		parser.addArgument("--watch", "-w")
				.action(Arguments.storeTrue())
				.help("Run in watch mode (experimental");
		parser.addArgument("--simulate", "-s")
				.action(Arguments.storeTrue())
				.help("Run in simulation mode starting from the head node");
		parser.addArgument("--lookahead", "-k")
				.type(Integer.class)
				.required(true)
				.help("The maximum lookahead depth to use when parsing log entries");

		Namespace res;
		try {
			res = parser.parseArgs(args);
			l.debug(res);
			
			// get a new matcher
			RegexMatcher matcher = new RegexMatcher();
			
			JParser jparser = new JParser(res.getString("input"), res.getString("log"),
					res.getBoolean("watch"), matcher, res.getInt("lookahead"));
			
			String start = res.getBoolean("simulate")? "start" : "first";
			jparser.parseAndMatch(start);
		} catch (ArgumentParserException e) {
			parser.handleError(e);
		}
	}
}

