package info.ephyra.answeranalysis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import info.ephyra.answerselection.AnswerPattern;
import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.NETagger;
import info.ephyra.nlp.OpenNLP;
import info.ephyra.questionanalysis.QuestionNormalizer;
import info.ephyra.questionanalysis.Term;
import info.ephyra.questionanalysis.TermExtractor;
import info.ephyra.util.Dictionary;
import info.ephyra.util.RegexConverter;
import info.ephyra.util.StringUtils;

public class Answers {
	
	private String answers;
	private int countOfSents;
	private String[] originalSentences;
	private String[][] tokens;
	private String[] sentences;
	private String[][][] nes;
	//private ArrayList<String>[] to;
	private Term[][] terms;

//	private ArrayList<String> to = new ArrayList<String>();
//	public Answers(String answers) {
//		this.answers = answers;
//		this.originalSentences = OpenNLP.sentDetect(this.answers);
//		this.countOfSents = this.originalSentences.length;
//		this.tokens = new String[this.countOfSents][];
//		this.sentences = new String[this.countOfSents];
//		for (int i = 0; i < this.countOfSents; i++) {
//			tokens[i] = NETagger.tokenize(this.originalSentences[i]);
//			sentences[i] = StringUtils.concatWithSpaces(this.tokens[i]);
//		}
//		// extract named entities
//		this.nes = NETagger.extractNes(this.tokens);
//		if (this.nes != null) {
//			int len1 = nes.length;
//			for (int i=0; i<len1; i++){
//				int len2 = nes[i].length;
//				for (int j=0; j<len2; j++){
//					int len3 = nes[i][j].length;
//					for (int k=0; k<len3; k++) {
//						if (nes[i][j][k] != null && ! to.contains(nes[i][j][k])) {
//							to.add(nes[i][j][k]);
//						}
//					}
//				}
//			}
//		}
//	}

	// Heavy constructor;-)
	public Answers(String answers) {
		this.answers = answers;
		this.originalSentences = OpenNLP.sentDetect(this.answers);
		this.countOfSents = this.originalSentences.length;
		this.tokens = new String[this.countOfSents][];
		this.sentences = new String[this.countOfSents];
		for (int i = 0; i < this.countOfSents; i++) {
			tokens[i] = NETagger.tokenize(this.originalSentences[i]);
			sentences[i] = StringUtils.concatWithSpaces(this.tokens[i]);
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
			}

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

	public static void main(String[] args) {
		String answers  = "Al Gore was born in Washington DC.";
		Answers ans = new Answers(answers);
	}
}
