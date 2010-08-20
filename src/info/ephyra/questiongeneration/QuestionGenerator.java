package info.ephyra.questiongeneration;


import info.ephyra.answeranalysis.Answer;
import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.indices.FunctionWords;
import info.ephyra.nlp.semantics.ontologies.WordNet;
import info.ephyra.questionanalysis.QuestionInterpretation;
import info.ephyra.questionanalysis.QuestionPattern;
import info.ephyra.questionanalysis.Term;
import info.ephyra.util.Dictionary;
import info.ephyra.util.FileUtils;
import info.ephyra.util.HashDictionary;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.regex.PatternSyntaxException;

import javatools.PlingStemmer;

/**
 * The <code>QuestionInterpreter</code> parses a question and determines the
 * TARGET, the CONTEXT and the PROPERTY it asks for.
 * 
 * @author Nico Schlaefer
 * @version 2005-11-09
 */
public class QuestionGenerator {
	private static Hashtable<String, ArrayList<QuestionPattern>> questionTable =
		new Hashtable<String, ArrayList<QuestionPattern>>();
	/** The patterns that are applied to a question. */
	private static ArrayList<QuestionPattern> questionPatterns =
		new ArrayList<QuestionPattern>();
	/** For each PROPERTY a dictionary of keywords. */
	private static Hashtable<String, HashDictionary> keywords =
		new Hashtable<String, HashDictionary>();
	/** For each PROPERTY a template for a question asking for it. */
	private static Hashtable<String, String> questionTemplates =
		new Hashtable<String, String>();
	/** For each PROPERTY a template for an answer string. */
	private static Hashtable<String, String> answerTemplates =
		new Hashtable<String, String>();
	/** For each PROPERTY a constraint for the named entity of the question target. */
	private static Hashtable<String, ArrayList<String>> questionConstraint =
		new Hashtable<String, ArrayList<String>>();
	/** For each PROPERTY a constraint for the named entity of the answer string. */
	private static Hashtable<String, ArrayList<String>> answerConstraint =
		new Hashtable<String, ArrayList<String>>();	
	
	/**
	 * Adds the keywords in a descriptor of a question pattern to the dictionary
	 * for the respective PROPERTY.
	 * 
	 * @param expr pattern descriptor 
	 * @param prop PROPERTY the question pattern belongs to
	 */
	private static void addKeywords(String expr, String prop) {
		// tokenize expr, delimiters are meta-characters, '<', '>' and blank
		StringTokenizer st = new StringTokenizer(expr, "\\|*+?.^$(){}[]<> ");
		
		String token;
		HashDictionary dict;
		while (st.hasMoreTokens()) {
			token = st.nextToken();
			if (token.length() > 2 && !FunctionWords.lookup(token)) {
				// token has a length of at least 3 and is not a function word
				dict = keywords.get(prop);
				if (dict == null) {  // new dictionary
					dict = new HashDictionary();
					keywords.put(prop, dict);
				}
				
				dict.add(token);  // add token to the dictionary
			}
		}
	}
	
	/**
	 * Loads the question patterns from a directory of PROPERTY files. Each file
	 * contains a list of pattern descriptors. Their format is described in the
	 * documentation of the class <code>QuestionPattern</code>.
	 * 
	 * @param dir directory of the question patterns
	 * @return true, iff the question patterns were loaded successfully
	 */
	public static boolean loadPatterns(String dir) {
		File[] files = FileUtils.getFiles(dir);
		
		try {
			BufferedReader in;
			String prop, line;
			
			for (File file : files) {
				prop = file.getName();
				if (prop.startsWith(".")) {
					//hidden file, probably a .swp file
					continue;
				}
				in = new BufferedReader(new FileReader(file));
				
				ArrayList<QuestionPattern> qPatternsListOfProp = 
					new ArrayList<QuestionPattern>();
				
				while (in.ready()) {
					line = in.readLine().trim();
					if (line.length() == 0 || line.startsWith("//"))
						continue;  // skip blank lines and comments
					
					if (line.startsWith("QUESTION_TEMPLATE")) {
						// add question template
						String[] tokens = line.split("\\s+", 2);
						if (tokens.length > 1)
							questionTemplates.put(prop, tokens[1]);
					} else if (line.startsWith("ANSWER_TEMPLATE")) {
						// add answer template
						String[] tokens = line.split("\\s+", 2);
						if (tokens.length > 1)
							answerTemplates.put(prop, tokens[1]);
					} else if (line.startsWith("QUESTION_CONSTRAINT")) {
						// add question constraint
						String[] tokens = line.split("\\s+", 2);
						if (tokens.length > 1) {
							tokens = tokens[1].split("_");
							ArrayList<String> neArray = new ArrayList<String>(Arrays.asList(tokens));
							questionConstraint.put(prop, neArray);
						}
					} else if (line.startsWith("ANSWER_CONSTRAINT")) {
						// add question constraint
						String[] tokens = line.split("\\s+", 2);
						if (tokens.length > 1) {
							tokens = tokens[1].split("_");
							ArrayList<String> neArray = new ArrayList<String>(Arrays.asList(tokens));
							answerConstraint.put(prop, neArray);
						}
					} else {
						try {
							// add question pattern
							questionPatterns.add(new QuestionPattern(line, prop));
							qPatternsListOfProp.add(new QuestionPattern(line, prop, ""));
							// add keywords to the dictionary for prop
							addKeywords(line, prop);
						} catch (PatternSyntaxException pse) {
							MsgPrinter.printErrorMsg("Problem loading pattern:\n" +
													 prop + " " + line);
							MsgPrinter.printErrorMsg(pse.getMessage());
						}
					}
				}
				questionTable.put(prop, qPatternsListOfProp);
				
				in.close();
			}
		} catch (IOException e) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Interprets a question by applying the question patterns and returns the
	 * interpretations of minimal length.
	 * 
	 * @param qn normalized question string
	 * @param stemmed stemmed question string
	 * @return array of interpretations or an empty array, if there was no
	 * 		   matching question pattern
	 */
	public static QuestionInterpretation[] interpret(String qn,
													 String stemmed) {
		ArrayList<QuestionInterpretation> qis =
			new ArrayList<QuestionInterpretation>();
		
		// apply the question patterns
		for (QuestionPattern questionPattern : questionPatterns) {
			QuestionInterpretation qi =
				questionPattern.apply(qn, stemmed);
			
			if (qi != null) qis.add(qi);
		}
		
		// sort the interpretations by their length
		QuestionInterpretation[] sorted =
			qis.toArray(new QuestionInterpretation[qis.size()]);
		Arrays.sort(sorted);
		
		// only return interpretations of minimal length
		ArrayList<QuestionInterpretation> minLength =
			new ArrayList<QuestionInterpretation>();
		for (QuestionInterpretation qi : sorted)
			if (qi.getLength() == sorted[0].getLength()) minLength.add(qi);
		
		return minLength.toArray(new QuestionInterpretation[minLength.size()]);
	}
	
	/**
	 * Looks up a word in the dictionary for the given PROPERTY.
	 * 
	 * @param word the word to be looked up
	 * @param prop the PROPERTY
	 * @return true, iff <code>word</code> is in the dictionary for
	 * 		   <code>prop</code>
	 */
	public static boolean lookupKeyword(String word, String prop) {
		Dictionary dict = keywords.get(prop);
		if (dict == null) return false;
		
		if (dict.contains(word)) return true;
		String stem = PlingStemmer.stem(word);
		if (dict.contains(stem)) return true;
		String lemma = WordNet.getLemma(word, WordNet.VERB);
		if (lemma != null && dict.contains(lemma)) return true;
		
		return false;
	}
	
	/**
	 * Returns a question string that asks for the specified property of the
	 * target object or <code>null</code> if no question template is available
	 * for the property.
	 * 
	 * @param to target object
	 * @param prop property
	 * @return question string or <code>null</code>
	 */
	public static String getQuestion(String to, String prop) {
		String question = questionTemplates.get(prop);
		if (question == null) return null;
		
		return question.replace("<TO>", to);
	}
	
	/**
	 * Returns an answer string that expresses that the property object is an
	 * instance of the specified property or <code>null</code> if no answer
	 * template is available for the property.
	 * 
	 * @param po property object
	 * @param prop property
	 * @return answer string or <code>null</code>
	 */
	public static String getAnswer(String po, String prop) {
		String answer = answerTemplates.get(prop);
		if (answer == null) return null;
		
		return answer.replace("<PO>", po);
	}

	// Making this pairs could consume a lot of memory
	public static ArrayList<QuestionAnswerPair> makeQApairs(ArrayList<Answer> ansList) {
		Iterator<Answer> ansIter = ansList.iterator();
		String prop = null;
		ArrayList<QuestionPattern> qPatternList = null;
		ArrayList<QuestionAnswerPair> qaPairList = new ArrayList<QuestionAnswerPair>();
		while (ansIter.hasNext()) {
			Answer ans = ansIter.next();
			if (prop != ans.getProp()) {
				prop = ans.getProp();
				qPatternList = questionTable.get(prop);
			}
			QuestionAnswerPair qaPair = new QuestionAnswerPair(ans, qPatternList);
			qaPairList.add(qaPair);
		}
		return qaPairList;
	}
	
	public static void generate(ArrayList<Answer> ansList) {
		Iterator<Answer> ansIter = ansList.iterator();
		String to = null;
		String prop = null;
		ArrayList<QuestionPattern> qPatternList = null;
		Iterator<QuestionPattern> qPatternIter = null;
		while (ansIter.hasNext()) {
			Answer ans = ansIter.next();
			if (prop != ans.getProp()) {
				prop = ans.getProp();
				qPatternList = questionTable.get(prop);
				if (qPatternList == null){
					continue;
				}
			}
			to = ans.getTo();
			qPatternIter = qPatternList.iterator();
			while (qPatternIter.hasNext()) {
				QuestionPattern qPattern = qPatternIter.next();
				String expr = (String) qPattern.getExpr();
				expr = expr.replace("<TO>", to);
				MsgPrinter.printStatusMsg(expr);
			}
			
			
		}
	}

	// Making this pairs could consume a lot of memory
	public static ArrayList<QAPair> makeQApair(ArrayList<Answer> ansList) {
		if (ansList.isEmpty()) {
			return null;
		}
		Iterator<Answer> ansIter = ansList.iterator();
		String prop = null;
		String sent = null;
		int size_exempt = 0;
		ArrayList<QAPair> qaPairList = new ArrayList<QAPair>();
		ArrayList<Answer> ansListByProp = null;
		Hashtable<String, ArrayList<Answer>> ansTable = null;
		//hopefully the ansList is sorted by PROPERTY
		while (ansIter.hasNext()) {
			Answer ans = ansIter.next();
			if (sent != ans.getSentence()){
				// save ansListByProp of the last time
				if (ansListByProp != null && !ansListByProp.isEmpty()) {
					ansTable.put(prop, ansListByProp);
					ansListByProp = new ArrayList<Answer>();
				}
				
				// a new sentence means a new QAPair
				if (sent != null && ansTable !=null) {
					QAPair pair = new QAPair(sent, ansTable);
					qaPairList.add(pair);
				}
				
				sent = ans.getSentence();
				// a new ansTable for a new QAPair
				ansTable = new Hashtable<String, ArrayList<Answer>>();
			}
			
			if (prop != ans.getProp()) {
				if (ansListByProp != null && !ansListByProp.isEmpty()) {
					ansTable.put(prop, ansListByProp);
				}
				prop = ans.getProp();
				ansListByProp = ansTable.get(prop);
				if (ansListByProp == null) {
					ansListByProp = new ArrayList<Answer>();
				}
			}
			
			// match with constraint,if there is one
			// if there's a constraint file in res/patternlearning/questionpatternTest,
			// there must be a corresponding file in res/patternlearning/answerpatternTest
			ArrayList<String> qCons = QuestionGenerator.questionConstraint.get(prop);
			ArrayList<String> aCons = QuestionGenerator.answerConstraint.get(prop);
			// toTerm couldn't be null since every <TO> must be a term
			// poTerm could be null since <PO> might not be in the term list
			//if ( ans.getPoTerm() == null ||
			if ( qCons == null && aCons == null && 
					ans.getToTerm() != null && ans.getPoTerm() != null) {
				ansListByProp.add(ans);
			} else {
				boolean to = false, po = false;
				String[] neTypesTo = ans.getToTerm().getNeTypes();
				Term poTerm = ans.getPoTerm();
				// when poTerm is null, we don't add ans to the list. 
				if (poTerm == null) {
					po = false;
				} else {
					String[] neTypesPo = ans.getPoTerm().getNeTypes();
					// poTerm must match answerConstraint
					if (aCons!=null) {
						for (String s:neTypesPo) {
							if (aCons.contains(s)) {
								po = true;
								break;
							}
						}			
					}
				}
				

				// toTerm must match questionConstraint
				if (qCons!=null) {
					for (String s:neTypesTo) {
						if (qCons.contains(s)) {
							to = true;
							break;
						}
					}
				}

				// add this answer to the list if both constraints are satisfied
				if (to && po) {
					ansListByProp.add(ans);
				} else {
					// this answer is not aaded to the list, count it for check later.
					size_exempt++;
				}
			}
		}
		if (ansListByProp != null && !ansListByProp.isEmpty()) {
			ansTable.put(prop, ansListByProp);
		}
		QAPair pair = new QAPair(sent, ansTable);
		qaPairList.add(pair);
		
		// check the number
		int size1 = ansList.size();
		int size2 = 0;
		Iterator<QAPair> qaIter = qaPairList.iterator();
		while (qaIter.hasNext()) {
			QAPair p = qaIter.next();
			Enumeration<ArrayList<Answer>> eArrayList = p.ansTable.elements();
			while (eArrayList.hasMoreElements()) {
				ArrayList<Answer> list = (ArrayList<Answer>)eArrayList.nextElement();
				size2 += list.size();
			}
		}
		if (size1 != size2+size_exempt) {
			MsgPrinter.printStatusMsg("Error: please debug the source code!");
			return null;
		}
		
		return qaPairList;
	}
	
	// this method shrink the list according to the same <TO>
	// questions with the same <TO> are the same, although answers (<PO>) could be different
	public static ArrayList<QuestionAnswerPair> shrinkByTo(ArrayList<QuestionAnswerPair> pairList) {
		ArrayList<QuestionAnswerPair> lessList = new ArrayList<QuestionAnswerPair>();
		ArrayList<Answer> ansList = new ArrayList<Answer>();
		Iterator<QuestionAnswerPair> pairIter = pairList.iterator();
		while (pairIter.hasNext()) {
			QuestionAnswerPair p = pairIter.next();
			Answer ans = p.getAnswer();
			Answer ansNew = new Answer(ans.getSentence(), ans.getProp(), ans.getTo(), "", null);
			if(!ansList.contains(ansNew)) {
				ansList.add(ansNew);
				lessList.add(p);
			}
		}
		return lessList;
	}
	
	public static void printQAlist (ArrayList<QAPair> qaPairList) {
		if (qaPairList == null) return;
		Iterator<QAPair> qaIter = qaPairList.iterator();
		int nSent = 0, nProp = 0, nQues = 0;
		while (qaIter.hasNext()) {
			nProp = 0;
			nSent++;
			QAPair p = qaIter.next();
			MsgPrinter.printStatusMsg(nSent+". "+p.getSentence());
			// loop through PROPERTY
			Enumeration<String> eKeys = p.ansTable.keys();
			while (eKeys.hasMoreElements()) {
				nQues = 0;
				nProp++;
				String prop = eKeys.nextElement();
				// print the property of question
				MsgPrinter.printStatusMsg("\t"+nProp+". Question Type:"+prop);
				// print the question template
				String qTemp = questionTemplates.get(prop); 
				MsgPrinter.printStatusMsg("\tQuestion Template: "+qTemp);
				// print the answer template
				String aTemp = answerTemplates.get(prop); 
				MsgPrinter.printStatusMsg("\tAnswer Template: "+aTemp);
				
				// loop through answer of the same property
				ArrayList<Answer> list = p.ansTable.get(prop);
				Iterator<Answer> ansIter = list.iterator();
				while (ansIter.hasNext()) {
					nQues++;
					Answer ans= ansIter.next();
					String po = (ans.getPoTerm()==null)?ans.getPo():ans.getPoTerm().toString();
					MsgPrinter.printStatusMsg(
							"\t\t"+nQues+". <TO>: "+ans.getToTerm().toString()+"\t<PO>: "+po);
				}

			}
		}
		return;
	}
}
