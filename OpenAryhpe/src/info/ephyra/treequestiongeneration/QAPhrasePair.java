package info.ephyra.treequestiongeneration;

import info.ephyra.questionanalysis.Term;
import edu.stanford.nlp.trees.Tree;

public class QAPhrasePair {
	// question type
	private String quesType;
	// question phrase;
	private String quesPhrase;
	// answer phrase
	private String ansPhrase;
	// the whole question sentence
	private String quesSentence;
	// preposition/subordinating conjunction in a PP
	private String inPhrase;
	// the tree containing the answer phrase
	private Tree ansTree;
	// term for the answer phrase
	private Term ansTerm;

	public QAPhrasePair(String ansPhrase, Tree ansTree, Term ansTerm) {
		this.ansPhrase = ansPhrase;
		this.ansTree = ansTree;
		this.ansTerm = ansTerm;
		this.inPhrase = "";
	}
	
	public QAPhrasePair(String inPhrase, String ansPhrase, Tree ansTree, Term ansTerm) {
		this.ansPhrase = ansPhrase;
		this.ansTree = ansTree;
		this.ansTerm = ansTerm;
		this.inPhrase = inPhrase;
	}
	
	public QAPhrasePair(String quesType, String quesPhrase, 
			String ansPhrase, Tree ansTree, Term ansTerm) {
		this(ansPhrase, ansTree, ansTerm);
		this.quesType = quesType;
		this.quesPhrase = quesPhrase;
	}
	
	public String getQuesType() {
		return quesType;
	}
	
	public String getQuesPhrase() {
		return quesPhrase;
	}
	
	public String getAnsPhrase() {
		return ansPhrase;
	}
	
	public void setAnsPhrase(String ans) {
		this.ansPhrase = ans;
	}
	
	public Tree getAnsTree() {
		return ansTree;
	}
	
	public Term getAnsTerm() {
		return ansTerm;
	}
	
	public void setQuesType(String qType) {
		this.quesType = qType;
	}

	public void setQuesPhrase(String qPhrase) {
		this.quesPhrase = qPhrase;
	}
	
	public void setQuesSentence(String quesSentence) {
		this.quesSentence = quesSentence;
	}
	
	public String getQuesSentence() {
		return this.quesSentence;
	}
	
	public String getInPhrase() {
		return this.inPhrase;
	}
	
	// whether the answer phrase is a PP
	public boolean isPP() {
		if (this.inPhrase.length()!=0)
			return true;
		else
			return false;
	}
}
