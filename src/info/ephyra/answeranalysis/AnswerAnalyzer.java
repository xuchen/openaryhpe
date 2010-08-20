package info.ephyra.answeranalysis;

import info.ephyra.answerselection.AnswerPattern;
import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.NETagger;
import info.ephyra.nlp.OpenNLP;
import info.ephyra.questionanalysis.Term;
import info.ephyra.util.Dictionary;
import info.ephyra.util.FileUtils;
import info.ephyra.util.RegexConverter;
import info.ephyra.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class AnswerAnalyzer {
	private static Hashtable<String, String> reverseMap =
		new Hashtable<String, String>();
	private static Hashtable<String, HashSet<AnswerPattern>> props =
		new Hashtable<String, HashSet<AnswerPattern>>();
	private static Hashtable<String, Integer> nOfPassages =
		new Hashtable<String, Integer>();
	/** PROPERTY objects extracted from a <code>Result</code>. */
	private static ArrayList<String> extr;
	/** For each PROPERTY object the NE types. */
	private static ArrayList<String[]> types;
	/** For each PROPERTY object the sentence it was extracted from. */
	private static ArrayList<String> sents;
	/** For each PROPERTY object the answer pattern used to extract it. */
	private static ArrayList<AnswerPattern> aps;
	/** <code>Dictionaries</code> for term extraction. */
	private static ArrayList<Dictionary> dicts = new ArrayList<Dictionary>();
	
	public static ArrayList<Answer> analyze (Answers ans) {
		ArrayList<Answer> answerList = new ArrayList<Answer>();
		extr = new ArrayList<String>();
		types = new ArrayList<String[]>();
		sents = new ArrayList<String>();
		aps = new ArrayList<AnswerPattern>();
		String prop;
		
		String[] cos = new String[0];  // CONTEXT objects are ignored
		//String prop = "PLACEOFBIRTH";
		HashSet<AnswerPattern> patterns;
		String[] sentences = ans.getSentences();
		String[] originalSentences = ans.getOriginalSentences();
		String[][][] nes = ans.getNes();
		Term[][] terms = ans.getTerms();
		if (terms == null) return null;
		// looping through every term of every sentence
		// every term of NE could potentially be a <TO>
		for (int t=0; t<terms.length; t++){
			for (Term term:terms[t]) {
				// if term is not an NE, then continue
				if (term.getNeTypes().length == 0) continue;
				String to = term.getText();

				// prepare sentence for answer extraction
				String sentence = prepSentence(sentences[t], to, cos, nes[t]);
				if (sentence == null) continue;
				Enumeration<String> ePattern = props.keys();
				while (ePattern.hasMoreElements()) {
					prop = ePattern.nextElement();
					patterns = props.get(prop);
					for (AnswerPattern pattern : patterns) {
						// apply answer pattern
						String[] NEpos = pattern.apply(sentence);
											
						// get NE types of PROPERTY objects
						String[][] neTypes = new String[NEpos.length][];
						if (NEpos.length > 0) {
							String[] pos = new String[NEpos.length];
							Term[] poTerm = new Term[NEpos.length];
							for (int j = 0; j < NEpos.length; j++)
								neTypes[j] = getNeTypes(NEpos[j], pattern);
							
							// replace tags and untokenize PROPERTY objects
							// poTerm may come out null in the following for loop
							// there are generally 3 reasons:
							// 1. pos[j] is with extra punctuations, such as "Washington DC.", which doesn't exactly match with term
							// 2. pos[j] contains multiple terms, such as "August 28, 1958", which doesn't exist in terms
							// 3. pos[j] contains prepositions, such as "in Washington".
							// 1 and 3 don't matter too much. for 2 it's critical to retrieve the right NE types
							// (TODO) it's better to modify the getTerms() function to return contiguous words with the same NE types as a term.
							for (int j = 0; j < NEpos.length; j++) {
								pos[j] = replaceTags(NEpos[j]);
								pos[j] = OpenNLP.untokenize(pos[j], originalSentences[t]);
								// find out pos's term
								for (Term tm:terms[t]) {
									if (tm.getText().equals(pos[j])) {
										poTerm[j] = new Term(pos[j], tm.getPos(), tm.getNeTypes());
										break;
									}
								}
							}
							// store the PROPERTY objects, the sentences they were extracted
							// from, the patterns used to extract them and the NE types
							for (int j = 0; j < NEpos.length; j++) {
								Answer p = new Answer(originalSentences[t],
										prop, to, term, pos[j], poTerm[j], pattern);
								if(!answerList.contains(p)) {
									answerList.add(p);
								}
								extr.add(pos[j]);
								types.add(neTypes[j]);
								sents.add(originalSentences[t]);
								aps.add(pattern);
							}
						}
					}
				}

			}
		}
		return answerList;
	}
	
	/**
	 * Loads the answer patterns from a directory of PROPERTY files. The first
	 * line of each file is the total number of passages used to assess the
	 * patterns. It is followed by a list of pattern descriptors, along with
	 * their number of correct and wrong applications. The format of the
	 * descriptors is described in the documentation of the class
	 * <code>AnswerPattern</code>.
	 * 
	 * @param dir directory of the answer patterns
	 * @return true, iff the answer patterns were loaded successfully
	 */
	public static boolean loadPatterns(String dir) {
		File[] files = FileUtils.getFiles(dir);
		
		try {
			BufferedReader in;
			String prop, expr;
			int passages, correct, wrong;
			HashSet<AnswerPattern> patterns;
			
			for (File file : files) {
				if (file.getName().startsWith(".")) {
					//hidden file, probably a .swp file
					continue;
				}
				MsgPrinter.printStatusMsg("  ...for " + file.getName());
				
				prop = file.getName();
				in = new BufferedReader(new FileReader(file));
				
				// total number of passages used to assess the patterns
				passages = Integer.parseInt(in.readLine().split(" ")[1]);
				nOfPassages.put(prop, passages);
				
				patterns = new HashSet<AnswerPattern>();
				while (in.ready()) {
					in.readLine();
					// pattern descriptor
					expr = in.readLine();
					// number of correct applications
					correct = Integer.parseInt(in.readLine().split(" ")[1]);
					// number of wrong applications
					wrong = Integer.parseInt(in.readLine().split(" ")[1]);
					
					// strict constraint on the number of correct and wrong
					if (wrong < 50 && (wrong == 0 || correct*1.0/(wrong + correct)> 0.75)) {
						try {
							patterns.add(new AnswerPattern(expr, prop,
														   correct, wrong));
						} catch (PatternSyntaxException pse) {
							MsgPrinter.printErrorMsg("Problem loading pattern:\n" +
													 prop + " " + expr);
							MsgPrinter.printErrorMsg(pse.getMessage());
						}
					}
				}
				props.put(prop, patterns);
				
				in.close();
			}
			
			MsgPrinter.printStatusMsg("  ...done");
		} catch (IOException e) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * Replaces tags in an extracted PROPERTY object with the original strings
	 * stored in <code>reverseMap</code>.
	 * 
	 * @param po PROPERTY object
	 * @return PROPERTY object without tags
	 */
	private static String replaceTags(String po) {
		Pattern p = Pattern.compile("<(TO|CO|NE).*?>");
		Matcher m = p.matcher(po);
		
		while (m.find()) {
			String tag = m.group(0);
			String rep = reverseMap.get(tag);  // look up replacement
			if (rep != null) po = po.replace(tag, rep);
		}
		
		return po;
	}
	
	/**
	 * Gets the NE types that a PROPERTY object has in common with the answer
	 * pattern used to extract it.
	 * 
	 * @param pos PROPERTY object
	 * @param pattern answer pattern used to extract it
	 * @return NE types or <code>null</code> if the answer pattern does not
	 * 		   expect specific types
	 */
	private static String[] getNeTypes(String pos, AnswerPattern pattern) {
		ArrayList<String> neTypes = new ArrayList<String>();
		
		String[] propertyTypes = pattern.getPropertyTypes();
		if (propertyTypes == null) return null;
		
		for (String propertyType : propertyTypes)
			if (pos.contains(propertyType)) 
				neTypes.add(propertyType);
		
		return neTypes.toArray(new String[neTypes.size()]);
	}
	
	/**
	 * Adds an answer pattern for a specific PROPERTY.
	 * 
	 * @param expr pattern descriptor
	 * @param prop PROPERTY that the pattern extracts
	 * @return true, iff a new pattern was added
	 */
	public static boolean addPattern(String expr, String prop) {
		// get the answer patterns for the specified PROPERTY
		HashSet<AnswerPattern> patterns = props.get(prop);
		if (patterns == null) {  // new PROPERTY
			patterns = new HashSet<AnswerPattern>();
			props.put(prop, patterns);
			nOfPassages.put(prop, 0);
		}
		
		// if the pattern is not in the set, add it
		boolean added = patterns.add(new AnswerPattern(expr, prop));
		
		// print out new patterns
		if (added) MsgPrinter.printStatusMsg(prop + ": " + expr);
		
		return added;
	}
	
	/**
	 * Replaces all TARGET objects in the sentence. The reverse mappings are
	 * stored in <code>reverseMap</code>.
	 * 
	 * @param sentence input sentence
	 * @param to the TARGET object of the question
	 * @param nes the NEs in the sentence
	 * @return sentence with TARGET tags or <code>null</code>, if the sentence
	 * 		   does not contain the TARGET
	 */
	private static String replaceTarget(String sentence, String to,
										String[][] nes) {
		HashSet<String> reps = new HashSet<String>();
		String tag, result = sentence;
		int id = 1;
		
		for (String[] neType : nes)
			for (String ne : neType)
				if (StringUtils.equalsCommonNorm(ne, to)) reps.add(ne);
		to = "(?i)" + RegexConverter.strToRegexWithBounds(to);
		Matcher m = Pattern.compile(to).matcher(result);
		while (m.find()) reps.add(m.group(0));  // get proper case
		
		// sort expressions by length
		String[] sorted = reps.toArray(new String[reps.size()]);
		StringUtils.sortByLengthDesc(sorted);
		
		for (String rep : sorted) {
			tag = "<TO_" + id++ + ">";  // add unique tag ID
			reverseMap.put(tag, rep);  // remember reverse mapping
			rep = RegexConverter.strToRegexWithBounds(rep);
			result = result.replaceAll(rep, tag);
		}
		
		return (result.equals(sentence)) ? null : result;
	}
	
	/**
	 * Replaces all CONTEXT objects in the sentence. The reverse mappings are
	 * stored in <code>reverseMap</code>.
	 * 
	 * @param sentence input sentence
	 * @param cos the CONTEXT objects of the question
	 * @param nes the NEs in the sentence
	 * @return sentence with CONTEXT tags
	 */
	private static String replaceContext(String sentence, String[] cos,
										 String[][] nes) {
		HashSet<String> reps = new HashSet<String>();
		String tag;
		int id = 1;
		
		for (String[] neType : nes)
			for (String ne : neType)
				for (String co : cos)
					if (StringUtils.equalsCommonNorm(ne, co)) reps.add(ne);
		for (String co : cos) {
			co = "(?i)" + RegexConverter.strToRegexWithBounds(co);
			Matcher m = Pattern.compile(co).matcher(sentence);
			while (m.find()) reps.add(m.group(0));  // get proper case
		}
		
		// sort expressions by length
		String[] sorted = reps.toArray(new String[reps.size()]);
		StringUtils.sortByLengthDesc(sorted);
		
		for (String rep : sorted) {
			tag = "<CO_" + id++ + ">";  // add unique tag ID
			reverseMap.put(tag, rep);  // remember reverse mapping
			rep = RegexConverter.strToRegexWithBounds(rep);
			sentence = sentence.replaceAll(rep, tag);
		}
		
		return sentence;
	}
	
	/**
	 * Replaces all NEs in the sentence. The reverse mappings are stored in
	 * <code>reverseMap</code>.
	 * 
	 * @param sentence input sentence
	 * @param nes the NEs in the sentence
	 * @return sentence with NE tags
	 */
	private static String replaceNes(String sentence, String[][] nes) {
		Hashtable<String, String> reps = new Hashtable<String, String>();
		String neType, tag;
		int id = 1;
		
		for (int i = 0; i < nes.length; i++) {
			neType = NETagger.getNeType(i);
			
			for (String ne : nes[i]) {
				tag = reps.get(ne);
				
				if (tag == null) tag = "<" + neType;
				else if (!tag.contains(neType))	tag += "_" + neType;
				
				reps.put(ne, tag);
			}
		}
		
		// sort expressions by length
		String[] sorted = reps.keySet().toArray(new String[reps.size()]);
		StringUtils.sortByLengthDesc(sorted);
		
		for (String rep : sorted) {
			tag = reps.get(rep) + "_" + id++ + ">";  // add unique tag ID
			reverseMap.put(tag, rep);  // remember reverse mapping
			rep = RegexConverter.strToRegexWithBounds(rep);
			sentence = sentence.replaceAll(rep, tag);
		}
		
		return sentence;
	}
	
	/**
	 * Prepares a sentence for answer extraction.
	 * 
	 * @param sentence input sentence
	 * @param to the TARGET object of the question
	 * @param cos the CONTEXT objects of the question
	 * @param nes the NEs in the sentence
	 * @return sentence ready for answer extraction or <code>null</code>, if
	 * 		   there is no TARGET object in the input sentence
	 */
	private static String prepSentence(String sentence, String to, String[] cos,
									   String[][] nes) {
		// initialize reverse map
		reverseMap = new Hashtable<String, String>();
		
		// replace TARGET and CONTEXT objects and NEs
		sentence = replaceTarget(sentence, to, nes);
		if (sentence == null) return null;
		sentence = replaceContext(sentence, cos, nes);
		sentence = replaceNes(sentence, nes);
		
		// add '#' at beginning and end of sentence
		sentence = "# " + sentence + " #";
		
		return sentence;
	}
	
	public static void addDictionary(Dictionary dict) {
		dicts.add(dict);
	}
	
	/**
	 * Returns the <code>Dictionaries</code>.
	 * 
	 * @return dictionaries
	 */
	public static Dictionary[] getDictionaries() {
		return dicts.toArray(new Dictionary[dicts.size()]);
	}	
	/**
	 * Unregisters all <code>Dictionaries</code>.
	 */
	public static void clearDictionaries() {
		dicts.clear();
	}
}
