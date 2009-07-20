package info.ephyra.treequestiongeneration;

import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.semantics.ontologies.WordNet;
import info.ephyra.treeansweranalysis.TreeAnswer;

// refer to README-tsurgeon.txt in the tregex program for Tregex syntax and Tsurgeon operation
// TODO: write a main method to test
public class VerbDecomposer {
	
	private static boolean initialized = false;
	
	// match the auxiliary word, could be model word, such as "should", "will",
	// or could be the first verb of two verbs, such as "has been"
	// one exception: "stopped doing(VBG)"
	private static String tregexMatchAux = "ROOT < (S=clause < (VP=mainvp < /(AUX.*|MD|VB.?)/=vb1 < (VP < /VB[^G]?/=vb2)))";
	private static TregexPattern tregexPatternMatchAux;
	// move the auxiliary word to the be the first child of the main clause
	private static String operationMoveAux = "move vb1 >1 clause";
	private static TsurgeonPattern tsurgeonPatternMoveAux = Tsurgeon.parseOperation(operationMoveAux);
	
	// match vb which is the only verb in VP
	// in this case a new auxiliary should be inserted
	private static String tregexMatchVb1 = "ROOT < (S=clause < (VP=mainvp < /(AUX.*|^VB.?)/=vb1 !< (VP < /VB.?/)))";
	private static TregexPattern tregexPatternMatchVb1;
	
	// almost the same as tregexMatchVb1, used to invert aux to an auxlirized tree. 
	// should have a negation ! to avoid looping when performing an insertion
	private static String tregexNoLoopInsAux = "ROOT < (S=clause < (VP=mainvp < (/^VB.?/=vb1 !$- /^AUX-VB.?/ )!< (VP < /VB.?/)))";
	private static TregexPattern tregexPatternNoLoopInsAux;

	// almost the same as tregexMatchVb1, used to invert aux to an inverted tree. 
	// should have a negation ! to avoid looping when performing an insertion
	private static String tregexNoLoopInsInv = "ROOT < (S=clause !< /^AUX-.?/ < (VP=mainvp < /^VB.?/=vb1 !< (VP < /VB.?/)))";
	private static TregexPattern tregexPatternNoLoopInsInv;
	
	// initialize all the matching patterns
	// must be called before running other operations
	public static boolean initialize() {
		
		try {
			tregexPatternMatchAux = TregexPattern.compile(tregexMatchAux);
			tregexPatternMatchVb1 = TregexPattern.compile(tregexMatchVb1);
			tregexPatternNoLoopInsAux = TregexPattern.compile(tregexNoLoopInsAux);
			tregexPatternNoLoopInsInv = TregexPattern.compile(tregexNoLoopInsInv);
			initialized = true;
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
			return false;
		}
		
		return initialized;
	}
	
	// 
	//private static String operationInsertAuxToAux = "insert vb1 $+ clause";
	//private static TsurgeonPattern tsurgeonPatternInsertAuxToAux = Tsurgeon.parseOperation(operationInsertAuxToAux);
	
	//private static String operationInsertAuxToInv = "move vb1 >1 clause";
	//private static TsurgeonPattern tsurgeonPatternMoveAuxToInv = Tsurgeon.parseOperation(operationInsertAuxToInv);
	
	// decomose the verb in the tree and returned the decomposed VP tree
	public static Tree decompose(TreeAnswer treeAnswer) {
		if (!initialized) {
			MsgPrinter.printErrorMsg("Must initialize VerbDecomposer first. Returning null");
			return null;
		}
		Tree tree = treeAnswer.getTree();
		Tree auxiliarizedTree = null;
		Tree invertedTree = null;
		//TregexPattern tregexPattern;
		TregexMatcher tregexMatcher;
		auxiliarizedTree = tree.deeperCopy();
		
		// VPs with two or more verbs, such as "has done", don't need to be decomposed
		// VPs with model verb, such as "should be", don't need to be decomposed
		// one exception: "stopped doing(VBG)"
		//String tregex = "ROOT < (S=clause < (VP=mainvp < /(MD|VB.?)/=vb1 < (VP < /VB[^G]?/=vb2)))";
//		try {
//			tregexPatternMatchAux = TregexPattern.compile(tregexMatchAux);
//		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
//			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
//			return null;
//		}
		// must match auxiliarizedTree here, otherwise the original tree is changed
		tregexMatcher = tregexPatternMatchAux.matcher(auxiliarizedTree);
		Tree vpTree = null;
		Tree vbTree = null;
		String lab;

		if (tregexMatcher.find()) {
			vpTree = tregexMatcher.getNode("mainvp");
			vbTree = tregexMatcher.getNode("vb1");
			// vbTree contains the "auxiliary"
			lab = vbTree.label().toString();
			// rename it with prefix "AUX-"
			// this changes the auxiliarizedTree
			lab = "AUX-"+lab;
			vbTree.label().setValue(lab);
			invertedTree = auxiliarizedTree.deeperCopy();
			// move the vb1 tree to the first child of the main clause
			// TODO: negation case, such as "does not do sth", should move "not" also (really?)
			invertedTree = Tsurgeon.processPattern(tregexPatternMatchAux, tsurgeonPatternMoveAux, invertedTree);
			if (invertedTree.equals(auxiliarizedTree)) {
				MsgPrinter.printErrorMsg("Auxiliary inversion operation failed.");
			}
		}

		if (vpTree != null) {
			System.out.println(auxiliarizedTree.toString());
			System.out.println(invertedTree.toString());
			return vpTree;
		}
		
		// the main verb of the main clause is only one verb
		// VB verb, base form take 
		// VBD verb, past tense took 
		// VBG verb, gerund/present participle taking 
		// VBN verb, past participle taken 
		// VBP verb, sing. present, non-3d take 
		// VBZ verb, 3rd person sing. present takes
		
		// when VB.? is is/are/were/was/be, they serve as auxiliary themselves
		// when not, we have the following transformation:
		// VBZ -> (AUX-VBZ does) (VB lemma)
		// VBD -> (AUX-VBD did)  (VB lemma)
		// VBP -> (AUX-VBP do)   (VB lemma)
		// VB  -> (AUX-VB  do)   (VB lemma) (quite unlikely here)
		// VBG, VBN are also quite unlikely here
		//tregex = "ROOT < (S=clause < (VP=mainvp < /^VB.?/=vb1 !< (VP < /VB.?/)))";
//		try {
//			tregexPattern = TregexPattern.compile(tregexMatchVb1);
//		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
//			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
//			return null;
//		}
		// must match auxiliarizedTree here, otherwise the original tree is changed
		tregexMatcher = tregexPatternMatchVb1.matcher(auxiliarizedTree);
		if (tregexMatcher.find()) {
			vpTree = tregexMatcher.getNode("mainvp");
			vbTree = tregexMatcher.getNode("vb1");
			lab = vbTree.label().value();
			String word = vbTree.firstChild().label().value();
			// TODO: BUG: getLemma will return "saw" as the lemma of VBZ "saw", mostly it should return "see"
			// ref: http://nlp.stanford.edu/nlp/javadoc/jwnl-docs/net/didion/jwnl/data/IndexWord.html
			String lemma = WordNet.getLemma(word, WordNet.VERB);
			String auxTree = "(AUX-VB do)";
			if (lemma.equals("be")) {
				// John is a man -> Is John a man
				// rename vb with prefix "AUX-"
				lab = "AUX-"+lab;
				vbTree.label().setValue(lab);
				invertedTree = auxiliarizedTree.deeperCopy();
				// TODO: negation case, such as "John is not a man" -> "Isn't John a man"
				// TODO: MD case, such as "John should be a man" -> "Should John be a man"
				invertedTree = Tsurgeon.processPattern(tregexPatternMatchVb1, tsurgeonPatternMoveAux, invertedTree);
				if (invertedTree.equals(auxiliarizedTree)) {
					MsgPrinter.printErrorMsg("Auxiliary inversion operation failed.");
				}
			} else if (lemma != null) {
				if (lab.equals("VBZ")) {
					//VBZ verb, 3rd person sing. present, "takes"
					auxTree = "(AUX-VBZ does)";
				} else if (lab.equals("VBD")) {
					//VBD verb, past tense, "took" 
					auxTree = "(AUX-VBD did)";
				} else if (lab.equals("VBP")) {
					//VBZ verb, 3rd person sing. present, "take"
					auxTree = "(AUX-VB do)";
				} else if (lab.equals("VB")) {
					//VB verb, base form, "take"
					auxTree = "(AUX-VB do)";
				} else {
					// VBG verb, gerund/present participle, "taking"
					// VBN verb, past participle, "taken" 
					MsgPrinter.printErrorMsg(lab+" found as the main verb.");
				}
				// John does that -> (AuxTree) John does do that -> (InvTree) Does John do that
				// John sees that -> (AuxTree) John does see that -> (InvTree)Does John see that
				// change vb1 tree to its lemma's format (VB lemma)
				vbTree.label().setValue("VB");
				vbTree.firstChild().label().setValue(lemma);
				invertedTree = auxiliarizedTree.deeperCopy();
				
				// insert auxiliary to the auxiliarizedTree
				//e.g. String operationInsertAuxToAux = "insert (AUX-VBZ does) $+ vb1";
				String operationInsertAuxToAux = "insert " + auxTree + " $+ vb1";
				TsurgeonPattern tsurgeonPatternInsertAuxToAux = Tsurgeon.parseOperation(operationInsertAuxToAux);
				// the matching Tregex expression should have a negation ! to avoid looping
				//String tregexNoLoop = "ROOT < (S=clause < (VP=mainvp < (/^VB.?/=vb1 !$- /^AUX-VB.?/ )!< (VP < /VB.?/)))";
				//TregexPattern tregexPatternNoLoop = null;
//				try {
//					tregexPatternNoLoop = TregexPattern.compile(tregexNoLoop);
//				} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
//					MsgPrinter.printErrorMsg("Error parsing regex pattern.");
//					return null;
//				}
				// WARNING: auxiliarizedTree isn't grammatical after insertion.
				auxiliarizedTree = Tsurgeon.processPattern(tregexPatternNoLoopInsAux, tsurgeonPatternInsertAuxToAux, auxiliarizedTree);
				
				// insert auxiliary to the invertedTree
				// e.g. String operationInsertAuxToInv = "insert (AUX-VBZ does) >1 clause";
				String operationInsertAuxToInv = "insert " + auxTree + " >1 clause";

//				try {
//					tregexPatternNoLoop = TregexPattern.compile(tregexNoLoop);
//				} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
//					MsgPrinter.printErrorMsg("Error parsing regex pattern.");
//					return null;
//				}
				TsurgeonPattern tsurgeonPatternInsertAuxToInv = Tsurgeon.parseOperation(operationInsertAuxToInv);
				// TODO: negation case, such as "John is not a man" -> "Isn't John a man"
				// TODO: MD case, such as "John should be a man" -> "Should John be a man"
				// TODO: invertedTree can also be formed by moving the aux verb of the auxiliarizedTree
				// to the front, unify with other cases, thus the code could be much less
				invertedTree = Tsurgeon.processPattern(tregexPatternNoLoopInsInv, tsurgeonPatternInsertAuxToInv, invertedTree);
				if (invertedTree.equals(auxiliarizedTree)) {
					MsgPrinter.printErrorMsg("Auxiliary inversion operation failed.");
				}
			} else {
				// lemma == null
				MsgPrinter.printErrorMsg("Lemma of word not found! Debugging the code!");
			}
		}
		if (vpTree == null || vbTree == null) {
			MsgPrinter.printErrorMsg("No verb found here, interesting case.");
		}
		
		System.out.println(auxiliarizedTree.toString());
		System.out.println(invertedTree.toString());
		return vpTree;
	}

}
