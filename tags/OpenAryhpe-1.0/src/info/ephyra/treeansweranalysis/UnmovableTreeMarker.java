package info.ephyra.treeansweranalysis;

import info.ephyra.io.MsgPrinter;
import info.ephyra.treequestiongeneration.VerbDecomposer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
//import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
//import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;

// this class marks phrases which may not undergo WH-movement
public class UnmovableTreeMarker {
	
	private static ArrayList<String> regexList = new ArrayList<String>();
	private static ArrayList<TregexPattern> regexPatternList= new ArrayList<TregexPattern>();
	private static Logger log = Logger.getLogger(UnmovableTreeMarker.class);

	UnmovableTreeMarker() {
//		regexList = new ArrayList<String>();
//		regexPatternList = new ArrayList<TregexPattern>();
	}
	
	public static boolean loadUnmvRegex (String path) {
		File file = new File(path);
		
		try {
			BufferedReader in = new BufferedReader(new FileReader(file));
			String line = null;
			while(in.ready()) {
				line = in.readLine().trim();
				if (line.length() == 0 || line.startsWith("#"))
					continue;  // skip blank lines and comments
				if (line != null) {
					regexList.add(line);
				}
			}
			in.close();
		} catch (IOException e) {
			return false;
		}
		
		if (regexList.isEmpty()) {
			return false;
		}
		
		//MsgPrinter.printStatusMsg("Building Tregex patterns...");
		Iterator<String> iter = regexList.iterator();
		TregexPattern tPattern = null;
		try {
			while (iter.hasNext()) {
				tPattern = TregexPattern.compile(iter.next());
				regexPatternList.add(tPattern);
			}
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
			return false;
		}
		
		return true;
	}
	
	// this method finds out all "unmv" node, insert "UNMV-" to the node label
	// then return the modified tree.
	public static Tree mark(Tree inTree) {
		if (inTree == null) {
			return null;
		}
		
		Tree outTree = inTree.deeperCopy();
		Iterator<TregexPattern> tregexPatternIter = regexPatternList.iterator();
		TregexPattern tregexPattern = null;
		TregexMatcher tregexMatcher = null;
		//TsurgeonPattern tsurgeonPattern = null;
		String lab, newlab;
		//String operation;
		while (tregexPatternIter.hasNext()) {
			tregexPattern = tregexPatternIter.next();
		
			tregexMatcher = tregexPattern.matcher(outTree); 
			try {
				while (tregexMatcher.find()) {
					log.debug("UNMV: "+tregexPattern.toString()+"\n");
					Tree matchedTreeWithName = tregexMatcher.getNode("unmv");
					// get the matched root label
					lab = matchedTreeWithName.label().toString();
					//lab = matchedTreeWithName.nodeString().replaceAll(" \\[[\\S]+\\]","");
					newlab = "UNMV-"+lab;
					// using setValue(newlab) here secretly changes outTree
					matchedTreeWithName.label().setValue(newlab);
					// reset() invokes recursive mathcing, still needs test here.
					tregexMatcher.reset();
					log.debug(outTree.pennString());
					// in cases NP|PP=unwm, a Tsurgeon operation will rename every 
					// match with UNMV-NP or UNMV-PP, so we can't use it.
					//operation = "relabel unmv "+newlab;
					//tsurgeonPattern = Tsurgeon.parseOperation(operation);
					//outTree = Tsurgeon.processPattern(tregexPattern, tsurgeonPattern, outTree);
					//Tsurgeon.processPattern(tregexPattern, tsurgeonPattern, matchedTreeWithName);
				}
			} catch (java.lang.NullPointerException e) {
				return outTree;
			}
		}
		log.debug("After UNMV: "+outTree.pennString());
		return outTree;
	}
}
