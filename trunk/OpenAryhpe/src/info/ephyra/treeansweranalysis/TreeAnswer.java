package info.ephyra.treeansweranalysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	// all the terms this sentence contains
	private Term[] terms = null;
	// the tree structure of this sentence
	private Tree tree = null;
	// the tree structure marked with UNMV component
	private Tree unmvTree = null;
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
	
	public ArrayList<QAPhrasePair> getQAPhraseList() {
		return qaPhraseList;
	}
}
