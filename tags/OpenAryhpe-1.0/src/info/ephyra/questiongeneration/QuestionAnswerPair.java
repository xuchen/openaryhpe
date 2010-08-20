package info.ephyra.questiongeneration;

import java.util.ArrayList;
import java.util.Iterator;

import info.ephyra.answeranalysis.Answer;
import info.ephyra.questionanalysis.QuestionPattern;

public class QuestionAnswerPair {
	
	private Answer answer;
	private ArrayList<QuestionPattern> qPatternList;
	
	public QuestionAnswerPair (Answer answer, ArrayList<QuestionPattern> qPatternList) {
		this.answer = answer;
		this.qPatternList = qPatternList;
	}
	
	public Answer getAnswer() {
		return answer;
	}
	
	public void setAnwer(Answer answer) {
		this.answer = answer;
	}
	
	public ArrayList<QuestionPattern> getQPatternList() {
		return qPatternList;
	}
	
	public void setQPatternList(ArrayList<QuestionPattern> list) {
		qPatternList = list;
	}


}
