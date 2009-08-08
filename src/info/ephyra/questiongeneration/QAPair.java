package info.ephyra.questiongeneration;

import info.ephyra.answeranalysis.Answer;

import java.util.ArrayList;
import java.util.Hashtable;

public class QAPair {
	private String sentence;
	// for every PROPERTY, there's an ArrayList of Answer,
	// indicating what kind of questions can be asked from the sentence.
	public Hashtable<String, ArrayList<Answer>> ansTable;
	
	public QAPair (String sent, Hashtable<String, ArrayList<Answer>> ansTable) {
		this.sentence = sent;
		this.ansTable = ansTable;
	}
	
	public void setSentence(String sent) {
		this.sentence = sent;
	}
	
	public String getSentence() {
		return sentence;
	}

}
