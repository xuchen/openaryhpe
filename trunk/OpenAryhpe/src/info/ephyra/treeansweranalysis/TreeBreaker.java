package info.ephyra.treeansweranalysis;

import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.NETagger;
import info.ephyra.nlp.OpenNLP;
import info.ephyra.nlp.StanfordParser;
import info.ephyra.nlp.TreeUtil;
import info.ephyra.util.StringUtils;

import org.apache.log4j.Logger;

import edu.stanford.nlp.trees.CollinsHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.trees.tregex.tsurgeon.Tsurgeon;
import edu.stanford.nlp.trees.tregex.tsurgeon.TsurgeonPattern;

// this class breaks a long sentence to several small sentences, cf. Table 2.
public class TreeBreaker {

	private static boolean initialized = false;
	private static Logger log = Logger.getLogger(TreeBreaker.class);
	
	//warning: older version of stanford parser will get it wrong here
	//test sent: Average maturity of the funds' investments lengthened by a day to 41 days , the longest since early August , according to Donoghue's .
	//in this case, the comma following August will not be the sister of "August" ($..), but in an upper position
	//private static String tregexMatchNounApp = "NP !< CC !< CONJP < (NP=noun $.. (/,/ $.. (NP=app $.. /,/)))";
	private static String tregexMatchNounApp = "NP !< CC !< CONJP < (NP=noun $.. (/,/ $.. (NP=app .. /,/)))";
	private static TregexPattern tregexPatternMatchNounApp;
	
	private static String tregexMatchNounModifier = "NP=noun > NP $.. VP=modifier";
	private static TregexPattern tregexPatternMatchNounModifier;

	private static String tregexMatchFiniteS = "S=finite !> ROOT !>> NP|PP < NP < (VP < VBP|VB|VBZ|VBD|MD) ?< /\\./=punct";
	private static TregexPattern tregexPatternMatchFiniteS;
	
	
	/*
	// As an indication of subject and object, NP-SBJ and -NONE- are free gifts from PTB so sadly we can't use it here.
	// One alternative is to use the Enju parser as a backup, which will give arg1/arg2 of verb
	// note we can't replace Stanford parser with Enju because in this way we lose the power of Tregex/Tsurgon
	// The other alternative is to use Mark Johnson's "empty node restorer" (C++), then we lose the power of platform independence...
	// http://www.cog.brown.edu/~mj/Software.htm
	// Oh gosh, there's no free lunch and PTB has spoiled us!  
	private static String tregexMatchRelativeClauseObject = "NP=noun > NP$.. (SBAR < (S=rel < (NP-SBJ !< /-NONE-/) < (VP << (/NP/ < /-NONE/=obj))) !< WHADVP !< WHADJP)";
	private static TregexPattern tregexPatternMatchRelativeClauseObject;
	private static String operationReplaceObject = "replace obj noun";
	private static TsurgeonPattern tsurgeonPatternReplaceObject;
	*/
	
	private static String tregexMatchRelativeClauseObject = 
      "NP=object > NP $.. (SBAR " +
		                        " <  (S=rel " +
		                                    " <, NP " +
		                                    " < (VP  < (/VB/=verb !. /VB/ " + //verb is the last VB of VP (no VB follows verb)
		                                                          "!$+ NP " + //avoid looping caused by insertion.
		                                               ") " +
		                                         ") " +
		                              ") " +
		                        "!< WHADVP " +
		                        "!< WHADJP " +
		                   ")";
	private static TregexPattern tregexPatternMatchRelativeClauseObject;
	private static String operationInsertObject = "insert object $- verb";
	private static TsurgeonPattern tsurgeonPatternInsertObject;
	
	private static String tregexMatchRelativeClauseSubject = 
	      "NP=subject > NP $.. (SBAR " +
			                        " <  (S=rel " +
			                                    " < (VP  !, NP  ) " +
			                              ") " +
			                        "!< WHADVP " +
			                        "!< WHADJP " +
			                   ")";
	private static TregexPattern tregexPatternMatchRelativeClauseSubject;
	
	private static String tregexMatchCC = "VP < (CC=cc ,, VP=vp1 .. VP=vp2) > (S > ROOT)";
	private static TregexPattern tregexPatternMatchCC;
	
	// initialize all the matching patterns
	// must be called before running other operations
	public static boolean initialize() {
		
		try {
			tregexPatternMatchNounApp = TregexPattern.compile(tregexMatchNounApp);
			tregexPatternMatchNounModifier = TregexPattern.compile(tregexMatchNounModifier);
			tregexPatternMatchFiniteS = TregexPattern.compile(tregexMatchFiniteS);
			
			tregexPatternMatchRelativeClauseObject = TregexPattern.compile(tregexMatchRelativeClauseObject);
			tsurgeonPatternInsertObject = Tsurgeon.parseOperation(operationInsertObject);
			
			tregexPatternMatchRelativeClauseSubject = TregexPattern.compile(tregexMatchRelativeClauseSubject);
			tregexPatternMatchCC = TregexPattern.compile(tregexMatchCC);
			
			initialized = true;
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			log.error("Error parsing regex pattern for TreeBreaker."+e);
			return false;
		}
		
		return initialized;
	}
	
	public static String doBreak(String answers) {
		if (!initialized) {
			MsgPrinter.printErrorMsg("Must initialize TreeBreaker first. Returning null");
			return null;
		}
		if (answers == null) return null;
		
		log.debug("Breaking sentences.");
		String newAnswers="";
		String[] originalSentences = OpenNLP.sentDetect(answers);
		int countOfSents = originalSentences.length;
		log.debug("Count of original one: "+countOfSents);

		String original;
		Tree tree;
		TregexMatcher tregexMatcher;
		CollinsHeadFinder headFinder = new CollinsHeadFinder();
		for (int i = 0; i < countOfSents; i++) {
			original = originalSentences[i];
			newAnswers += original+" ";
			tree = StanfordParser.parseTree(original);
			log.debug("Sentence "+i+" :"+original);
			log.debug(tree.pennString()+"\n");
			
			/*
			A new tree is constructed from an appositive phrase app modifying
			a noun phrase noun by creating a sentence of the form
			noun copula app, where copula is in the past tense and agrees
			with the head of noun.
			*/
			tregexMatcher = tregexPatternMatchNounApp.matcher(tree);
			while (tregexMatcher.find()) {
				log.debug("Enter noun copula app");
				Tree nounTree = tregexMatcher.getNode("noun");
				Tree appTree = tregexMatcher.getNode("app");
				Tree npHeadTree = headFinder.determineHead(nounTree);
				String headTag = npHeadTree.labels().toString();
				String copula = "is";
				if (headTag.contains("NNS") || headTag.contains("NNS")) {
					copula = "are";
				}
				String newSent = TreeUtil.getLabel(nounTree)+" "+copula+" "+TreeUtil.getLabel(appTree)+". ";
				newSent = StringUtils.capitalizeFirst(newSent);
				log.debug("New sent: "+newSent);
				if (! newAnswers.contains(newSent)) {
					newAnswers += newSent;
				}
			}
			
			/*
			A new tree is constructed from a finite clause by placing finite under
			a new root node, with the appropriate punctuation punct. There is a
			check to avoid extracting clauses dominated by noun phrases or prepositional
			phrases. (Extracting phrases in such cases too often led to vague
			or ungrammatical questions during development.)
			*/
			// This is almost completely useless because only 2 matching instances in PTB are found...
			// But amazingly, this does the trick for "John thought Mary said that Bill wanted Susan to like Peter."
			// So let's do it...
			tregexMatcher = tregexPatternMatchFiniteS.matcher(tree);
			while (tregexMatcher.find()) {
				log.debug("Enter finite");
				Tree sTree = tregexMatcher.getNode("finite");

				String newSent = TreeUtil.getLabel(sTree);
				newSent = StringUtils.capitalizeFirst(newSent);
				if (tregexMatcher.getNode("punc")==null) {
					newSent+=". ";
				}
				log.debug("New sent: "+newSent);
				// if we append newSent to newAnswers, the sentence detector of opennlp won't
				// recgonize it as two sentences! Extremely wierd...
				newAnswers = newSent+newAnswers;
			}
			
			
			/*
			A new tree is constructed from a verbal modifier (e.g., bought by John
			in This is the car bought by John.) by creating a sentence of the form
			noun copula modifier, where copula is in the past tense and
			agrees with the head of noun.
			*/
			tregexMatcher = tregexPatternMatchNounModifier.matcher(tree);
			while (tregexMatcher.find()) {
				log.debug("Enter noun copula modifier");
				Tree nounTree = tregexMatcher.getNode("noun");
				Tree modTree = tregexMatcher.getNode("modifier");
				Tree npHeadTree = headFinder.determineHead(nounTree);
				String headTag = npHeadTree.labels().toString();
				String copula = "is";
				if (headTag.contains("NNS") || headTag.contains("NNS")) {
					copula = "are";
				}
				String newSent = TreeUtil.getLabel(nounTree)+" "+copula+" "+TreeUtil.getLabel(modTree)+". ";
				newSent = StringUtils.capitalizeFirst(newSent);
				log.debug("New sent: "+newSent);
				if (! newAnswers.contains(newSent)) {
					newAnswers += newSent;
				}
			}
			
			/*
			A new tree is constructed from a relative clause by creating a sentence in
			which noun is the subject or object of the clause in rel that is missing
			a subject or object.
			*/
			// let's start from when noun is the object
			Tree aTree = tree.deeperCopy();
			tregexMatcher = tregexPatternMatchRelativeClauseObject.matcher(aTree);
			while (tregexMatcher.find()) {
				log.debug("Enter object in relative clause");
				// replace the NONE object with noun
				aTree = Tsurgeon.processPattern(tregexPatternMatchRelativeClauseObject, 
						tsurgeonPatternInsertObject, aTree);
				Tree sTree = tregexMatcher.getNode("rel");
				
				String newSent = TreeUtil.getLabel(sTree);
				newSent = StringUtils.capitalizeFirst(newSent)+". ";
				log.debug("New sent: "+newSent);
				if (! newAnswers.contains(newSent)) {
					newAnswers = newSent+newAnswers;
				}
			}
			
			// when noun is the subject
			tregexMatcher = tregexPatternMatchRelativeClauseSubject.matcher(tree);
			while (tregexMatcher.find()) {
				log.debug("Enter subject in relative clause");
				Tree subTree = tregexMatcher.getNode("subject");
				Tree sTree = tregexMatcher.getNode("rel");
				
				String newSent = TreeUtil.getLabel(subTree)+" "+TreeUtil.getLabel(sTree)+". ";
				newSent = StringUtils.capitalizeFirst(newSent);
				log.debug("New sent: "+newSent);
				if (! newAnswers.contains(newSent)) {
					newAnswers = newSent+newAnswers;
				}
			}
			
			/* Two new trees are obtained by dividing a sentence with a CC connecting 2 VPs into 2.
			 * For instance: John is tall and plays basketball.
			 * -> John is tall. John plays basketball.
			 */
			tregexMatcher = tregexPatternMatchCC.matcher(tree);
			while (tregexMatcher.find()) {
				log.debug("Enter VP CC VP");
				Tree ccTree = tregexMatcher.getNode("cc");
				Tree vp1Tree = tregexMatcher.getNode("vp1");
				Tree vp2Tree = tregexMatcher.getNode("vp2");
				
				String wholeSent = TreeUtil.getLabel(tree);
				String vp1Sent = TreeUtil.getLabel(vp1Tree);
				String ccSent = TreeUtil.getLabel(ccTree);
				String vp2Sent = TreeUtil.getLabel(vp2Tree);
				String vp1ccSent = vp1Sent+" "+ccSent;
				String ccvp2Sent = ccSent+" "+vp2Sent;
				log.debug("VP1 and CC: "+vp1ccSent);
				log.debug("CC and VP2: "+ccvp2Sent);
				
				String newSent = wholeSent.replaceFirst(vp1ccSent+" ", "")+" ";
				if(newSent.equals(wholeSent)) {
					log.warn("breaking VP CC VP failed:\n"+newSent);
				}
				if (! newAnswers.contains(newSent)) {
					log.debug("New sent: "+newSent);
					newAnswers = newSent+newAnswers;
				}
				log.debug("wholesent: "+wholeSent);
				newSent = wholeSent.replaceFirst(ccvp2Sent+" ", "")+" ";
				if(newSent.equals(wholeSent)) {
					log.warn("breaking VP CC VP failed:\n"+newSent);
				}
				if (! newAnswers.contains(newSent)) {
					log.debug("New sent: "+newSent);
					newAnswers = newSent+newAnswers;
				}
			}
		}
		log.debug("New answers: "+newAnswers);
		return newAnswers.trim();
	}

	
}
