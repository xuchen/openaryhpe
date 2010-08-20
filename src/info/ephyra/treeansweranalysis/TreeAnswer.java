package info.ephyra.treeansweranalysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.LabeledWord;
import edu.stanford.nlp.trees.Tree;

import info.ephyra.answerselection.AnswerPattern;
import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.NETagger;
import info.ephyra.nlp.OpenNLP;
import info.ephyra.questionanalysis.Term;
import info.ephyra.treequestiongeneration.QAPhrasePair;
import info.ephyra.util.RegexConverter;
import info.ephyra.util.StringUtils;

// a class for an answer organized as a tree
public class TreeAnswer {
	// the original answer sentence
	private String sentence;
	// the subject phrase
	private String subject = null;
	// all the terms this sentence contains
	private Term[] terms = null;
	// the tree structure of this sentence
	private Tree tree = null;
	// the tree structure marked with UNMV component
	private Tree unmvTree = null;
	// the tree with inserted auxiliary
	private Tree auxTree = null;
	// the sentence of auxTree
	private String auxSentence = "";
	// the sentence of invTree
	private String invSentence = "";
	// the tree with auxiliary inversion
	private Tree invTree = null;
	// lists of possible qa phrase pairs
	private ArrayList<QAPhrasePair> qaPhraseList = null;
	
	public TreeAnswer(String sent, Term[] terms, Tree tree) {
		this.sentence = sent;
		this.terms = terms;
		this.tree = tree;
	}
	
	public TreeAnswer(String sent, Term[] terms, Tree tree, Tree unmvTree) {
		this(sent, terms, tree);
		this.unmvTree = unmvTree;
	}
	
	public void setQAPhraseList(ArrayList<QAPhrasePair> qaPhraseList) {
		this.qaPhraseList = qaPhraseList;
	}

	public String getSentence() {
		return sentence;
	}
	
	public Term[] getTerms() {
		return terms;
	}
	
	public Tree getUnmvTree() {
		return unmvTree;
	}

	public Tree getTree() {
		return tree;
	}
	
	public void setAuxTree (Tree tree) {
		auxTree = tree;
	}
	
	public void setInvTree (Tree tree) {
		invTree = tree;
	}
	
	public Tree getAuxTree () {
		return auxTree;
	}
	
	public Tree getInvTree() {
		return invTree;
	}
	
	public ArrayList<QAPhrasePair> getQAPhraseList() {
		return qaPhraseList;
	}
	
	public void setAuxSentence(String auxSent) {
		this.auxSentence = auxSent;
	}
	
	public void setInvSentence(String invSent) {
		this.invSentence = invSent;
	}
	
	public String getAuxSentence() {
		return this.auxSentence;
	}
	
	public String getInvSentence() {
		return this.invSentence;
	}
	
	public String getSubject() {
		return this.subject;
	}
	
	public void setSubject(String sub) {
		this.subject = sub;
	}
	
	/**
	 * How many entries are there in <code>qaPairList</code>.
	 */
	public int size() {
		return this.qaPhraseList.size();
	}
	
	// return the sentence the tree contains, concatenate with spaces
	// I know it's weird to put this method here...
	public static String getSentFromTree(Tree tree) {
		if (tree == null) return null;
		String sent = "";
		// find out the lexical labels
		List<LabeledWord> labelWord = tree.labeledYield();
				
		Iterator<LabeledWord> npIter = labelWord.iterator();
		while (npIter.hasNext()) {
			sent += npIter.next().value()+" ";
		}
		return sent.trim();
	}
}
