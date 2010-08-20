package info.ephyra.treequestiongeneration;

import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import edu.stanford.nlp.ling.LabeledWord;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;
import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.semantics.ontologies.WordNet;
import info.ephyra.questionanalysis.atype.FocusFinder;
import info.ephyra.treeansweranalysis.TreeAnswer;

// refer to README-tsurgeon.txt in the tregex program for Tregex syntax and Tsurgeon operation
// TODO: write a main method to test
/*
John should be a man
John is a man
John was a man
John is being a man
John has been a man
John has been being a man
John does see that
They do see that
They have seen that
They did see that
They saw that
 */
public class VerbDecomposer {
	
	private static boolean initialized = false;
	private static Logger log = Logger.getLogger(VerbDecomposer.class);
	
	// match the auxiliary word, could be model word, such as "should", "will",
	// or could be the first verb of two verbs, such as "has been"
	// one exception: "stopped doing(VBG)"
	// TODO: the following doesn't match: "A large male can be 2 metres (6 ft 7 in) tall and weigh 90 kg (200 lb)."
	private static String tregexMatchVb1Vb2 = "ROOT < (S=clause < (VP=mainvp < /(AUX.*|MD|VB.?)/=vb1 < (VP < /VB[^G]?/=vb2)))";
	private static TregexPattern tregexPatternMatchVb1Vb2;
	// move the auxiliary word to the be the first child of the main clause
	private static String operationMoveAux = "move vb1 >1 clause";
	private static TsurgeonPattern tsurgeonPatternMoveAux;
	
	// match vb which is the only verb in VP
	// in this case a new auxiliary should be inserted
	private static String tregexMatchVb1 = "ROOT < (S=clause < (VP=mainvp < /(AUX.*|^VB.?)/=vb1 !< (VP < /VB.?/)))";
	private static TregexPattern tregexPatternMatchVb1;
	
	// match auxiliary in the auxiliarized tree
	private static String tregexMatchAux = "ROOT < (S=clause < (VP=mainvp < /(Q-AUX|AUX-.?)/=vb1))";
	private static TregexPattern tregexPatternMatchAux;
	
	// almost the same as tregexMatchVb1, used to invert aux to an auxlirized tree. 
	// should have a negation ! to avoid looping when performing an insertion
	private static String tregexNoLoopInsAux = "ROOT < (S=clause < (VP=mainvp < (/(^VB.?|AUX-.?)/=vb1 !$- /^AUX-.?/ !$- /Q-AUX/ )!< (VP < /VB.?/)))";
	private static TregexPattern tregexPatternNoLoopInsAux;

	// change the main clause from "S" to "SQ"
	private static String tregexStoSQ = "ROOT < S=clause";
	private static TregexPattern tregexPatternStoSQ;
	private static String operationRelabel = "relabel clause SQ";
	private static TsurgeonPattern tsurgeonPatternStoSQ;
	
	// match any PP adjunct after Q-AUX (quesPhrase+auxiliary)
	// http://www.ucl.ac.uk/internet-grammar/phfunc/adjuncts.htm
	private static String tregexAdjunct = "ROOT < (SQ=clause <1 /Q-AUX|AUX-.?/=qaux <2 ((/PP/=pp . /,/=comma)) )";
	private static TregexPattern tregexPatternAdjunct;
	// move Q-AUX to be the right sister of comma
	private static String operationMoveAdjunct= "move qaux $- comma";
	private static TsurgeonPattern tsurgeonPatternMoveAdjunct;
	
	// match aux in "MD VB" or "be" form to adjoin with Q-AUX
	private static String tregexAdjoinNoLoop = "/^AUX-/=aux !> /Q-AUX/";
	private static TregexPattern tregexPatternAdjoin;
	private static String operationAdjoin = "adjoin (Q-AUX (Q <quesPhrase>) AUX@) aux";
	private static TsurgeonPattern tsurgeonPatternAdjoin;
	// almost the same as tregexMatchVb1, used to invert aux to an inverted tree. 
	// should have a negation ! to avoid looping when performing an insertion
	//private static String tregexNoLoopInsInv = "ROOT < (S=clause !< /^AUX-.?/ < (VP=mainvp < /^VB.?/=vb1 !< (VP < /VB.?/)))";
	//private static TregexPattern tregexPatternNoLoopInsInv;
	
	// initialize all the matching patterns
	// must be called before running other operations
	public static boolean initialize() {
		
		//log.setLevel(Level.DEBUG);
		try {
			tregexPatternMatchVb1Vb2 = TregexPattern.compile(tregexMatchVb1Vb2);
			tregexPatternMatchVb1 = TregexPattern.compile(tregexMatchVb1);
			tregexPatternMatchAux = TregexPattern.compile(tregexMatchAux);
			tregexPatternNoLoopInsAux = TregexPattern.compile(tregexNoLoopInsAux);
			//tregexPatternNoLoopInsInv = TregexPattern.compile(tregexNoLoopInsInv);
			tsurgeonPatternMoveAux = Tsurgeon.parseOperation(operationMoveAux);
			tregexPatternStoSQ = TregexPattern.compile(tregexStoSQ);
			tsurgeonPatternStoSQ = Tsurgeon.parseOperation(operationRelabel);
			tregexPatternAdjunct = TregexPattern.compile(tregexAdjunct);
			tsurgeonPatternMoveAdjunct = Tsurgeon.parseOperation(operationMoveAdjunct);
			tregexPatternAdjoin = TregexPattern.compile(tregexAdjoinNoLoop);
			tsurgeonPatternAdjoin = Tsurgeon.parseOperation(operationAdjoin);
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
	public static TreeAnswer decompose(TreeAnswer treeAnswer) {
		if (!initialized) {
			MsgPrinter.printErrorMsg("Must initialize VerbDecomposer first. Returning null");
			return null;
		}
		Tree tree = treeAnswer.getTree();
		Tree auxiliarizedTree = null, invertedTree = null;
		Tree vpTree = null, vbTree = null;
		String lab;
		TregexMatcher tregexMatcher;
		auxiliarizedTree = tree.deeperCopy();
		
		// VPs with two or more verbs, such as "has done", don't need to be decomposed
		// VPs with model verb, such as "should be", don't need to be decomposed
		// one exception: "stopped doing(VBG)"

		// must match auxiliarizedTree here, otherwise the original tree is changed
		tregexMatcher = tregexPatternMatchVb1Vb2.matcher(auxiliarizedTree);

		log.debug("Decomposing the verb\n");
		
		if (tregexMatcher.find()) {
			log.debug("VPs with two or more verbs.\n");
			vpTree = tregexMatcher.getNode("mainvp");
			vbTree = tregexMatcher.getNode("vb1");
			// vbTree contains the "auxiliary"
			lab = vbTree.label().toString();
			// rename it with prefix "AUX-"
			// this changes the auxiliarizedTree
			lab = "AUX-"+lab;
			vbTree.label().setValue(lab);
			auxiliarizedTree = Tsurgeon.processPattern(tregexPatternAdjoin, tsurgeonPatternAdjoin, auxiliarizedTree);
		} else {

			// the main verb of the main clause is only one verb
			// VB verb, base form, "take" 
			// VBD verb, past tense, "took" 
			// VBG verb, gerund/present participle, "taking" 
			// VBN verb, past participle, "taken" 
			// VBP verb, sing. present, non-3d, "take" 
			// VBZ verb, 3rd person sing. present, "takes"
			
			// when VB.? is is/are/were/was/be, they serve as auxiliary themselves
			// when not, we have the following transformation:
			// VBZ -> (AUX-VBZ does) (VB lemma)
			// VBD -> (AUX-VBD did)  (VB lemma)
			// VBP -> (AUX-VBP do)   (VB lemma)
			// VB  -> (AUX-VB  do)   (VB lemma) (quite unlikely here)
			// VBG, VBN are also quite unlikely here

			// must match auxiliarizedTree here, otherwise the original tree is changed
			tregexMatcher = tregexPatternMatchVb1.matcher(auxiliarizedTree);
			if (tregexMatcher.find()) {
				vpTree = tregexMatcher.getNode("mainvp");
				vbTree = tregexMatcher.getNode("vb1");
				lab = vbTree.label().value();
				String word = vbTree.firstChild().label().value().replaceFirst("-\\d+$", "");
				// TODO: BUG: getLemma will return "saw" as the lemma of VBZ "saw", mostly it should return "see"
				// ref: http://nlp.stanford.edu/nlp/javadoc/jwnl-docs/net/didion/jwnl/data/IndexWord.html
				String lemma = WordNet.getLemma(word, WordNet.VERB);
				if (lemma == null) 
					return null;
				String auxTree = "(AUX-VB do)";
				if (lemma.equals("be")) {
					// John is a man -> Is John a man
					// rename vb with prefix "AUX-"
					lab = "AUX-"+lab;
					vbTree.label().setValue(lab);
					// WARNING: auxiliarizedTree isn't grammatical after insertion.
					auxiliarizedTree = Tsurgeon.processPattern(tregexPatternAdjoin, tsurgeonPatternAdjoin, auxiliarizedTree);
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
						// parser might be wrong, so we still use the cases here
						if (lab.equals("VBG")) {
							//VBZ verb, 3rd person sing. present, "take"
							auxTree = "(AUX-VB does)";
						} else if (lab.equals("VBN")) {
							//VB verb, base form, "take"
							auxTree = "(AUX-VB did)";
						}	
					
					}
					// construct a tree with the question phrase and auxiliary verb
					auxTree = "(Q-AUX (Q <quesPhrase>)" + auxTree + " )";
					// John does that -> (AuxTree) John does do that -> (InvTree) Does John do that
					// John sees that -> (AuxTree) John does see that -> (InvTree)Does John see that
					// change vb1 tree to its lemma's format (VB lemma)
					vbTree.label().setValue("VB");
					vbTree.firstChild().label().setValue(lemma);
										
					// insert auxiliary to the auxiliarizedTree
					//e.g. String operationInsertAuxToAux = "insert (AUX-VBZ does) $+ vb1";
					String operationInsertAuxToAux = "insert " + auxTree + " $+ vb1";
					TsurgeonPattern tsurgeonPatternInsertAuxToAux = Tsurgeon.parseOperation(operationInsertAuxToAux);
					// WARNING: auxiliarizedTree isn't grammatical after insertion.
					auxiliarizedTree = Tsurgeon.processPattern(tregexPatternNoLoopInsAux, tsurgeonPatternInsertAuxToAux, auxiliarizedTree);
				} else {
					// lemma == null
					MsgPrinter.printErrorMsg("Lemma of word not found! Debugging someone else's code!");
				}
			}
		}

		if (vpTree == null || vbTree == null) {
			log.debug("No verb found here, returning null.");
			log.debug("Tree: "+auxiliarizedTree.pennString());
			return null;
		}
		
		log.debug("Auxiliarized Tree:\n"+auxiliarizedTree.pennString());
		invertedTree = auxiliarizedTree.deeperCopy();
		// move the Q-AUX tree to the first child of the main clause
		// TODO: negation case, such as "does not do sth", should move "not" also (really?)
		// TODO: make the original first word lower case
		// WARNING: invertedTree isn't grammatical after the move operation.
		log.debug("Moving the AUX tree to the first child of the main clause:\n");
		invertedTree = Tsurgeon.processPattern(tregexPatternMatchAux, tsurgeonPatternMoveAux, invertedTree);
		log.debug(invertedTree.pennString());
		if (invertedTree.equals(auxiliarizedTree)) {
			MsgPrinter.printErrorMsg("Auxiliary inversion operation failed.");
			log.warn("Auxiliary inversion operation failed.");
		}
		// relabel S as SQ
		invertedTree = Tsurgeon.processPattern(tregexPatternStoSQ, tsurgeonPatternStoSQ, invertedTree);
		
		tregexMatcher = tregexPatternAdjunct.matcher(invertedTree);
		if (tregexMatcher.find()) {
			// Move PP adjunct to the front, if any
			//such as: <quesPhrase> in 2009, did Jackson die
			//becomes: in 2009, <questionPhrase> did Jackson die
			log.debug("Moving PP adjunct to the front:\n");
			invertedTree = Tsurgeon.processPattern(tregexPatternAdjunct, tsurgeonPatternMoveAdjunct, invertedTree);
			log.debug(invertedTree.pennString());
		}
		
		treeAnswer.setAuxTree(auxiliarizedTree);
		treeAnswer.setInvTree(invertedTree);
		treeAnswer.setAuxSentence(TreeAnswer.getSentFromTree(auxiliarizedTree));
		treeAnswer.setInvSentence(TreeAnswer.getSentFromTree(invertedTree));
		log.debug("Inverted Tree:\n"+invertedTree.pennString());
		//System.out.println(auxiliarizedTree.toString());
		//System.out.println(invertedTree.toString());
		return treeAnswer;
	}

}
