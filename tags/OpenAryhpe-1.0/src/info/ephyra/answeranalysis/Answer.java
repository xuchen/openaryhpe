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
import info.ephyra.questionanalysis.Term;
import info.ephyra.util.RegexConverter;
import info.ephyra.util.StringUtils;

public class Answer {
	
	private String sentence;
	private String prop;
	private String to;
	private String po;
	// Term for <TO>, containing more info, such as POS, NE
	private Term toTerm = null;
	// Term for <PO>, containing more info, such as POS, NE	
	private Term poTerm = null;
	private AnswerPattern pattern;

	
	public Answer(String sentence, String prop, String to, String po, AnswerPattern pattern) {
		this.sentence = sentence;
		this.prop = prop;
		this.to = to;
		this.po = po;
		this.pattern = pattern;
	}

	public Answer(String sentence, String prop, String to, Term toTerm, String po, Term poTerm, AnswerPattern pattern) {
		this(sentence, prop, to, po, pattern);
		this.toTerm = toTerm;
		this.poTerm = poTerm;
	
	}
	
	public String getSentence() {
		return sentence;
	}

	public void setSentence(String sentence) {
		this.sentence = sentence;
	}

	public String getProp() {
		return prop;
	}

	public void setProp(String prop) {
		this.prop = prop;
	}

	public String getTo() {
		return to;
	}

	public void setTo(String To) {
		this.to = To;
	}

	public String getPo() {
		return po;
	}

	public void setPo(String po) {
		this.po = po;
	}

	public AnswerPattern getPattern() {
		return pattern;
	}

	public void setPattern(AnswerPattern pattern) {
		this.pattern = pattern;
	}
	
	public Term getToTerm() {
		return this.toTerm;
	}
	
	public Term getPoTerm() {
		return this.poTerm;
	}
	
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		final Answer p = (Answer) o;
		if (this.po.equals(p.po) && this.prop.equals(p.prop)
				&& this.sentence.equals(p.sentence) && this.to.equals(p.to)) {
			return true;
		} else {
			return false;
		}
	}
}
