package info.ephyra.treequestiongeneration;

import info.ephyra.io.MsgPrinter;
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
		Term[] terms = treeAnswer.getTerms();
		// only deal with NP now
		// TODO: PP
		String tregex = "NP";
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
			List<LabeledWord> labelNPWord = npTree.labeledYield();
			List<LabeledWord> labelNPheadWord = npHeadTree.labeledYield();
			
			String npWord = "";
			String npHeadWord = "";
			Iterator<LabeledWord> npIter = labelNPWord.iterator();
			while (npIter.hasNext()) {
				npWord += npIter.next().value();
			}
			Iterator<LabeledWord> headIter = labelNPheadWord.iterator();
			while (headIter.hasNext()) {
				npHeadWord += headIter.next().value();
			}
			Tree termNPtree = null;
			String ansPhrase = "";
			Term ansTerm = null;
			// if either npWord or npHeadWord is a term with a NE type
			for (Term term:terms) {
				if (term.getNeTypes().length == 0) continue;
				String termStr = term.getText().replaceAll("\\s+", "");
				if(termStr.equals(npWord)) {
					termNPtree = npTree;
					ansPhrase = term.getText().toString();
					ansTerm = term;
					break;
				}
				if(termStr.equals(npHeadWord)) {
					termNPtree = npHeadTree;
					ansPhrase = term.getText().toString();
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
			qPhrase = "Who";
		} else if (qType.equals("WHERE")) {
			qPhrase = "Where";
		} else if (qType.equals("WHEN")) {
			qPhrase = "When";
		}
		
		return qPhrase;
	}

}
