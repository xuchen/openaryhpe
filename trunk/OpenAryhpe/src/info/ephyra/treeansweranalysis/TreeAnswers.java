package info.ephyra.treeansweranalysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.ephyra.answeranalysis.AnswerAnalyzer;
import info.ephyra.answerselection.AnswerPattern;
import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.NETagger;
import info.ephyra.nlp.OpenNLP;
import info.ephyra.nlp.StanfordParser;
import info.ephyra.nlp.TreeUtil;
import info.ephyra.questionanalysis.QuestionNormalizer;
import info.ephyra.questionanalysis.Term;
import info.ephyra.questionanalysis.TermExtractor;
import info.ephyra.util.Dictionary;
import info.ephyra.util.RegexConverter;
import info.ephyra.util.StringUtils;

import edu.stanford.nlp.trees.Tree;

public class TreeAnswers {
	
	private String answers;
	private int countOfSents;
	private String[] originalSentences;
	private String[][] tokens;
	private String[] sentences;
	private String[][][] nes;
	//private String[] parses;
	private Tree[] trees;
	//private ArrayList<String>[] to;
	private Term[][] terms;
	private boolean[] firstCapitalize;

	// Heavy constructor;-)
	public TreeAnswers(String answers) {
		this.answers = answers;
		this.originalSentences = OpenNLP.sentDetect(this.answers);
		this.countOfSents = this.originalSentences.length;
		this.tokens = new String[this.countOfSents][];
		//this.parses = new String[this.countOfSents];
		this.trees = new Tree[this.countOfSents];
		this.sentences = new String[this.countOfSents];
		this.firstCapitalize = new boolean[this.countOfSents];
		String firstWord="";
		for (int i = 0; i < this.countOfSents; i++) {
			tokens[i] = NETagger.tokenize(this.originalSentences[i]);
			//parses[i] = StanfordParser.parse(this.originalSentences[i]);
			trees[i] = StanfordParser.parseTree(this.originalSentences[i]);
			//give every leave a unique index
			trees[i] = TreeUtil.indexLeaves(trees[i]);
			sentences[i] = StringUtils.concatWithSpaces(this.tokens[i]);
			this.firstCapitalize[i] = false;
		}
		this.terms = new Term[this.countOfSents][];
		// extract named entities
		this.nes = NETagger.extractNes(this.tokens);
		if (this.nes != null) {
			for (int i=0; i<this.countOfSents; i++){
				String ansNormalized = QuestionNormalizer.normalize(this.originalSentences[i]);
				// resolve verb constructions with auxiliaries
				// TODO return only one best string
				String verbMod = (QuestionNormalizer.handleAuxiliaries(ansNormalized))[0];
				this.terms[i] = TermExtractor.getTerms(verbMod, "", this.nes[i],
						AnswerAnalyzer.getDictionaries());
				
				// check whether the first letter of the first word of the sentence should be capitalized
				// when the first word is moving to a non-initial position.
				// "She plays basketball." -> "Does she play basketball?"
				firstWord = tokens[i][0];
				for (Term term:this.terms[i]) {
					if (term.getNeTypes().length > 0 && term.getText().equals(firstWord)) {
						this.firstCapitalize[i] = true;
						break;
					}
				}
				// IBM
				if (StringUtils.isAllUppercase(firstWord)) {
					this.firstCapitalize[i] = true;
				}
				
				// exception
				if (firstWord.equals("A")) {
					this.firstCapitalize[i] = false;
				}
				
				if (! this.firstCapitalize[i]) {
					sentences[i] = sentences[i].replaceFirst(firstWord, StringUtils.lowercaseFirst(firstWord));
					originalSentences[i] = originalSentences[i].replaceFirst(firstWord, StringUtils.lowercaseFirst(firstWord));
					tokens[i][0] = firstWord;
				}
				
			}

		}

		// parse trees
		for (int i = 0; i < this.countOfSents; i++) {
			trees[i] = StanfordParser.parseTree(this.originalSentences[i]);
			//give every leave a unique index
			trees[i] = TreeUtil.indexLeaves(trees[i]);
		}
	}
	
	public String[] getSentences() {
		return sentences;
	}
	
	public String[][][] getNes() {
		return nes;
	}
	
	public String[][] getTokens() {
		return tokens;
	}
	
	public Term[][] getTerms() {
		return terms;
	}
	
	public String[] getOriginalSentences() {
		return originalSentences;
	}

	public Tree[] getTrees() {
		return trees;
	}
	
	public static void main(String[] args) {
		String answers  = "Al Gore was born in Washington DC.";
		TreeAnswers ans = new TreeAnswers(answers);
	}
}
