package info.ephyra.treeansweranalysis;

import info.ephyra.io.MsgPrinter;
import info.ephyra.treequestiongeneration.VerbDecomposer;

import org.apache.log4j.Logger;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;

// this class removes conj, adjunct and appositives of a tree, described in Table 1.
public class TreeCompressor {
	
	private static boolean initialized = false;
	private static Logger log = Logger.getLogger(TreeCompressor.class);
	
	private static String tregexMatchConj = "ROOT < (S < CC=conj)";
	private static TregexPattern tregexPatternMatchConj;
	// Sentence-initial conjuctions are removed by deleting conj.
	private static String operationDelConj = "delete conj";
	private static TsurgeonPattern tsurgeonPatternDelConj;

	// TODO: test whether it needs to remove adjunct.
	private static String tregexMatchAdjunct = "ROOT < (S < (/[^,]/=adjunct $.. (/,/ $.. VP)))";
	private static TregexPattern tregexPatternMatchAdjunct;
	// Sentence-initial adjunct phrases are removed by deleting adjunct.
	private static String operationDelAdjunct = "delete adjunct";
	private static TsurgeonPattern tsurgeonPatternDelAdjunct;

	private static String tregexMatchAppo = "SBAR|VP|NP=app $, /,/=lead $. /,/=trail !$ CC !$ CONJP";
	private static TregexPattern tregexPatternMatchAppo;
	// Appositives are removed by deleting app, lead, and trail.
	private static String operationDelAppo = "delete app lead trail";
	private static TsurgeonPattern tsurgeonPatternDelAppo;

	// initialize all the matching patterns
	// must be called before running other operations
	public static boolean initialize() {
		
		//log.setLevel(Level.DEBUG);
		try {
			tregexPatternMatchConj = TregexPattern.compile(tregexMatchConj);
			tregexPatternMatchAdjunct = TregexPattern.compile(tregexMatchAdjunct);
			tregexPatternMatchAppo = TregexPattern.compile(tregexMatchAppo);

			tsurgeonPatternDelConj = Tsurgeon.parseOperation(operationDelConj);
			tsurgeonPatternDelAdjunct = Tsurgeon.parseOperation(operationDelAdjunct);
			tsurgeonPatternDelAppo = Tsurgeon.parseOperation(operationDelAppo);
			initialized = true;
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			log.error("Error parsing regex pattern.");
			return false;
		}
		
		return initialized;
	}
	
	public static Tree compress(Tree inTree) {
		if (!initialized) {
			MsgPrinter.printErrorMsg("Must initialize TreeCompressor first. Returning null");
			return null;
		}
		
		if (inTree == null) {
			return null;
		}
		
		TregexMatcher tregexMatcher;
		Tree outTree = inTree.deeperCopy();
		
		tregexMatcher = tregexPatternMatchConj.matcher(outTree);
		if (tregexMatcher.find()) {
			outTree = Tsurgeon.processPattern(tregexPatternMatchConj, tsurgeonPatternDelConj, outTree);
			log.debug("Sentence-initial conjunction found. Performing deletion.");
			log.debug("Tree: "+outTree.pennString());
		}
		tregexMatcher = tregexPatternMatchAppo.matcher(outTree);
		if (tregexMatcher.find()) {
			outTree = Tsurgeon.processPattern(tregexPatternMatchAppo, tsurgeonPatternDelAppo, outTree);
			log.debug("Appositives found. Performing deletion.");
			log.debug("Tree: "+outTree.pennString());
		}
		
		return outTree;
	}
}
