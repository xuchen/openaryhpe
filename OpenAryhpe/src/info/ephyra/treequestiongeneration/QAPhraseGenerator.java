package info.ephyra.treequestiongeneration;

import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.StanfordParser;
import info.ephyra.nlp.TreeUtil;
import info.ephyra.questionanalysis.Term;
import info.ephyra.treeansweranalysis.TreeAnswer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import cern.colt.Arrays;

import edu.stanford.nlp.ling.LabeledWord;
import edu.stanford.nlp.trees.CollinsHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

public class QAPhraseGenerator {
	
	// generate a list of possible QA phrase pairs
	public static ArrayList<QAPhrasePair> generate(TreeAnswer treeAnswer) {
		ArrayList<QAPhrasePair> qaList = new ArrayList<QAPhrasePair>();
		
		Tree tree = treeAnswer.getUnmvTree();
		Tree t = treeAnswer.getTree();
		treeAnswer.setSubject(StanfordParser.getSubject(t));
		Term[] terms = treeAnswer.getTerms();
		
		// first deal with NP who's not under a PP
		String tregex = "NP !> PP";
		TregexPattern tPattern = null;
		// TODO: consider SemanticHeadFinder
		CollinsHeadFinder headFinder = new CollinsHeadFinder();
		try {
			tPattern = TregexPattern.compile(tregex);
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
		}
		TregexMatcher tregexMatcher = tPattern.matcher(tree);
		while (tregexMatcher.find()) {
			Tree npTree = tregexMatcher.getMatch();
			
			// unmovable phrases can't construct WH-movement
			String checkUnmv = npTree.labels().toString();
			if (checkUnmv.contains("UNMV-")) {
				continue;
			}
			
			Tree npHeadTree = headFinder.determineHead(npTree);
			
			// find out the lexical labels
			String npWord = TreeUtil.getTightLabelNoIndex(npTree);
			String npHeadWord = TreeUtil.getTightLabelNoIndex(npHeadTree);
			
			Tree termNPtree = null;
			String ansPhrase = "";
			Term ansTerm = null;
			// if either npWord or npHeadWord is a term with a NE type
			for (Term term:terms) {
				if (term.getNeTypes().length == 0) continue;
				String termStr = term.getText().replaceAll("\\s+", "");
				if(termStr.equals(npWord)) {
					termNPtree = npTree;
					ansPhrase = TreeUtil.getLabel(npTree);
					ansTerm = term;
					break;
				}
				if(termStr.equals(npHeadWord)) {
					termNPtree = npHeadTree;
					//TODO: maybe the whole NP should be the answer, rather than only the head of NP
					ansPhrase = TreeUtil.getLabel(npHeadTree);
					ansTerm = term;
					break;
				}
			}
			// then put it as an answer candidate
			if (termNPtree != null) {
				// construct QAphrasePair
				QAPhrasePair pair = new QAPhrasePair(ansPhrase, termNPtree, ansTerm);
				// TODO: return multiple question types here
				String qType = determineQuesType(pair);
				pair.setQuesType(qType);
				String qPhrase = constructQuesPhrase(pair);
				pair.setQuesPhrase(qPhrase);
				// add to qaList
				qaList.add(pair);
			}
		}
		
		// then deal with PP who's child is a NP
		tregex = "PP=pp < IN=in < NP=np";

		try {
			tPattern = TregexPattern.compile(tregex);
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
		}
		tregexMatcher = tPattern.matcher(tree);
		while (tregexMatcher.find()) {
			Tree ppTree = tregexMatcher.getNode("pp");
			Tree inTree = tregexMatcher.getNode("in");
			Tree npTree = tregexMatcher.getNode("np");
			
			// unmovable phrases can't construct WH-movement
			String checkUnmv = ppTree.labels().toString();
			if (checkUnmv.contains("UNMV-")) {
				continue;
			}
			
			Tree npHeadTree = headFinder.determineHead(npTree);
			
			// find out the lexical labels
			String ppWord = TreeUtil.getTightLabelNoIndex(ppTree);
			String inWord = TreeUtil.getTightLabelNoIndex(inTree);
			String npWord = TreeUtil.getTightLabelNoIndex(npTree);
			String npHeadWord = TreeUtil.getTightLabelNoIndex(npHeadTree);

			Tree termNPtree = null;
			String ansPhrase = "";
			Term ansTerm = null;
			// if either npWord or npHeadWord is a term with a NE type
			// TODO: maybe we should trace back to the root PP phrase and let it be the ansPhrase
			for (Term term:terms) {
				if (term.getNeTypes().length == 0) continue;
				String termStr = term.getText().replaceAll("\\s+", "");
				if(termStr.equals(npWord)) {
					termNPtree = npTree;
					ansPhrase = TreeUtil.getLabel(inTree)+" "+TreeUtil.getLabel(npTree);
					ansTerm = term;
					break;
				}
				if(termStr.equals(npHeadWord)) {
					termNPtree = npHeadTree;
					//TODO: maybe the whole NP should be the answer, rather than only the head of NP
					ansPhrase = TreeUtil.getLabel(inTree)+" "+TreeUtil.getLabel(npHeadTree);
					ansTerm = term;
					break;
				}
			}
			// then put it as an answer candidate
			if (termNPtree != null) {
				// construct QAphrasePair
				QAPhrasePair pair = new QAPhrasePair(inWord, ansPhrase, termNPtree, ansTerm);
				// TODO: return multiple question types here
				String qType = determineQuesType(pair);
				pair.setQuesType(qType);
				String qPhrase = constructQuesPhrase(pair);
				pair.setQuesPhrase(qPhrase);
				// add to qaList
				qaList.add(pair);
			}
		}
		// the following tries to find out NPs that match terms
		// this constraint is too tight and thus discarded
//		// each term of the i-th sentence
//		for (Term term:terms[i]) {
//			if (term.getNeTypes().length == 0)
//				continue;
//			String to = term.getText();
//			String[] tokens = OpenNLP.tokenize(to);
//			int j=tokens.length-1;
//			// form Tregex expressions to query trees
//			String tregex = "/"+tokens[j]+"/";
//			// this term contains multiple terms, such as "August 28, 1958"
//			// construct a Tregex query expression such as:
//			// (/August/ . (/28/ . (/,/ . /1958/)))
//			if (j > 0) {
//				j--;
//				for (; j>=0; j--) {
//					tregex = "(/" + tokens[j] + "/ . " + tregex + ")";
//				}
//			}
//			tregex = "@NP << " + tregex;
//			TregexPattern tPattern = null;
//			try {
//				 tPattern = TregexPattern.compile(tregex);
//			} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
//				MsgPrinter.printErrorMsg("Error parsing regex pattern.");
//			}
//			TregexMatcher tregexMatcher = tPattern.matcher(trees[i]);
//			while (tregexMatcher.find()) {
//				Tree termTree = tregexMatcher.getMatch();
//			}
//		}
		return qaList;
	}
	
	// given a QA phrase pair, determine what kind of questions can be asked
	// TODO: add more QA types according to other NE tagger (currently only Stanford is used)
	private static String determineQuesType(QAPhrasePair pair) {
		String qType = "";
		String neType = Arrays.toString(pair.getAnsTerm().getNeTypes());
		if (neType.length() == 0) {
			MsgPrinter.printErrorMsg("NE types shouldn't be none");
		}
		if (neType.contains("NEperson")) {
			qType = "WHO";
		} else if (neType.contains("NElocation")) {
			qType = "WHERE";
		} else if (neType.contains("NEdate")) {
			qType = "WHEN";
		}
		return qType;
	}
	
	// given a QA phrase pair, construct question phrases
	// TODO: add more question phrases
	private static String constructQuesPhrase(QAPhrasePair pair) {
		String qPhrase = "";
		String qType = pair.getQuesType();
		if (qType.equals("WHO")) {
			qPhrase = "who";
		} else if (qType.equals("WHERE")) {
			qPhrase = "where";
		} else if (qType.equals("WHEN")) {
			qPhrase = "when";
		}
		
		return qPhrase;
	}

}
