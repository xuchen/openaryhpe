package info.ephyra.treequestiongeneration;

import java.util.ArrayList;
import java.util.Iterator;

import edu.stanford.nlp.trees.Tree;

import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.OpenNLP;
import info.ephyra.nlp.TreeUtil;
import info.ephyra.questionanalysis.Term;
import info.ephyra.treeansweranalysis.TreeAnswer;

public class TreeQuestionGenerator {
	
	// generate questions according to qaPhrasePair in treeAnswer
	public static void generate(TreeAnswer treeAnswer) {
		ArrayList<QAPhrasePair> qaPhraseList = treeAnswer.getQAPhraseList();
		QAPhrasePair pPair;
		Tree ansTree, auxTree, invTree, sentTree;
		Term ansTerm;
		String ansPhrase, quesPhrase, quesSent, auxSent, invSent, subject, oriSent, idxSent;
		auxTree = treeAnswer.getAuxTree();
		invTree = treeAnswer.getInvTree();
		auxSent = treeAnswer.getAuxSentence();
		invSent = treeAnswer.getInvSentence();
		subject = treeAnswer.getSubject();
		oriSent = treeAnswer.getSentence();
		sentTree = treeAnswer.getTree();
		idxSent = TreeUtil.getLabel(sentTree);
		Iterator<QAPhrasePair> iter = qaPhraseList.iterator();
		while (iter.hasNext()) {
			pPair = iter.next();
			ansTree = pPair.getAnsTree();
			ansTerm = pPair.getAnsTerm();
			//ansPhrase = OpenNLP.tokenizeWithSpaces(pPair.getAnsPhrase());
			ansPhrase = pPair.getAnsPhrase();
			quesPhrase = pPair.getQuesPhrase();
			if (!pPair.getQuesType().equals("Y/N")) {
				if (ansPhrase.equals(subject)) {
					// answer phrase is the subject
					quesSent = idxSent.replaceFirst(subject, quesPhrase);
				} else {
					quesSent = quesPhrase+" "+invSent.replaceFirst(OpenNLP.tokenizeWithSpaces(ansPhrase), "");
				}
			} else {
				quesSent = invSent.substring(0,1).toUpperCase() + invSent.substring(1);
			}
			// remove the index
			quesSent = quesSent.replaceAll("-\\d+\\b", "");
			// remove the punctuation at the end and append with a question mark
			quesSent = quesSent.replaceAll("(\\.|\\?|!)$", "").trim()+"?";
			// remove extra spaces
			quesSent = quesSent.replaceAll("\\s{2,}", " ");
			pPair.setQuesSentence(quesSent);
			// generate another y/n question here, which should be invSent with the first capitalized
			//TODO: post-processing here
			//TODO: parse quesSentence and judge whether it's grammatical according to the score
		}
		return;
	}
	
	public static void print(ArrayList<TreeAnswer> treeAnswerList) {
		Iterator<TreeAnswer> tAnsIter = treeAnswerList.iterator();
		TreeAnswer treeAnswer;
		ArrayList<QAPhrasePair> qaPhraseList;
		Iterator<QAPhrasePair> pPairIter;
		QAPhrasePair pPair;
		int sentCount=0, quesCount=0;
		while (tAnsIter.hasNext()) {
			sentCount++;
			quesCount=0;
			treeAnswer = tAnsIter.next();
			MsgPrinter.printStatusMsg(sentCount+". "+treeAnswer.getSentence());
			qaPhraseList = treeAnswer.getQAPhraseList();
			pPairIter = qaPhraseList.iterator();
			while (pPairIter.hasNext()) {
				quesCount++;
				pPair = pPairIter.next();
				MsgPrinter.printStatusMsg("\t"+quesCount+". Q type: "+pPair.getQuesType());
				MsgPrinter.printStatusMsg("\t\tQuestion:"+pPair.getQuesSentence());
				MsgPrinter.printStatusMsg("\t\tAnswer:"+pPair.getAnsPhrase().replaceAll("-\\d+\\b", ""));
			}
			
		}
	}
}
