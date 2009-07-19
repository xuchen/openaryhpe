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
	// the tree containing the answer phrase
	private Tree ansTree;
	// term for the answer phrase
	private Term ansTerm;

	public QAPhrasePair(String ansPhrase, Tree ansTree, Term ansTerm) {
		this.ansPhrase = ansPhrase;
		this.ansTree = ansTree;
		this.ansTerm = ansTerm;
	}
	
	public QAPhrasePair(String quesType, String quesPhrase, 
			String ansPhrase, Tree ansTree, Term ansTerm) {
		this.quesType = quesType;
		this.quesPhrase = quesPhrase;
		this.ansPhrase = ansPhrase;
		this.ansTree = ansTree;
		this.ansTerm = ansTerm;
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
}
