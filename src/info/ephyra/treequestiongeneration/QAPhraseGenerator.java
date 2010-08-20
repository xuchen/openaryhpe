package info.ephyra.treequestiongeneration;

import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.StanfordParser;
import info.ephyra.nlp.TreeUtil;
import info.ephyra.questionanalysis.Term;
import info.ephyra.treeansweranalysis.TreeAnswer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;

import cern.colt.Arrays;

import edu.stanford.nlp.trees.CollinsHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;

public class QAPhraseGenerator {
	
	private static Logger log = Logger.getLogger(QAPhraseGenerator.class);
	
	public QAPhraseGenerator () {
	}
	
	// generate a list of possible QA phrase pairs
	public static ArrayList<QAPhrasePair> generate(TreeAnswer treeAnswer) {
		ArrayList<QAPhrasePair> qaList = new ArrayList<QAPhrasePair>();
		
		Tree tree = treeAnswer.getUnmvTree();
		Tree oriTree = treeAnswer.getTree();
		treeAnswer.setSubject(StanfordParser.getSubject(oriTree));
		Term[] terms = treeAnswer.getTerms();
		
		log.debug("Terms: "+Arrays.toString(terms));
		// first deal with NP who's not under a PP
		String tregex = "NP !>> PP ?< DT=det";
		TregexPattern tPattern = null;
		// TODO: consider SemanticHeadFinder
		CollinsHeadFinder headFinder = new CollinsHeadFinder();
		try {
			tPattern = TregexPattern.compile(tregex);
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
		}
		TregexMatcher tregexMatcher = tPattern.matcher(tree);
		while (tregexMatcher.find()) {
			Tree npTree = tregexMatcher.getMatch();
			Tree dtTree = tregexMatcher.getNode("det");
			String determiner="";
			if(dtTree!=null) {
				determiner=TreeUtil.getTightLabelNoIndex(dtTree);
			}
			
			// unmovable phrases can't construct WH-movement
			String checkUnmv = npTree.labels().toString();
			if (checkUnmv.contains("UNMV-")) {
				continue;
			}
			
			// don't generate questions regarding pronouns
			if (npTree.labels().toString().contains("PRP")) {
				continue;
			}
			
			Tree npHeadTree = headFinder.determineHead(npTree);
			
			// find out the lexical labels
			String npWord = TreeUtil.getTightLabelNoIndex(npTree);
			String npHeadWord = TreeUtil.getTightLabelNoIndex(npHeadTree);
			
			Tree termNPtree = null;
			String ansPhrase = "";
			Term ansTerm = null;
			// if either npWord or npHeadWord is a term with a NE type
			for (Term term:terms) {
				if (term.getNeTypes().length == 0) continue;
				termNPtree = null;
				String termStr = term.getText().replaceAll("\\s+", "");
				if(termStr.equals(npWord) || (determiner+termStr).equals(npWord)) {
					termNPtree = npTree;
					ansPhrase = TreeUtil.getLabel(npTree);
					ansTerm = term;
				} else
				if(termStr.equals(npHeadWord) || termStr.equals(determiner+npHeadWord)) {
					termNPtree = npHeadTree;
					//TODO: maybe the whole NP should be the answer, rather than only the head of NP
					//X. Yao. Aug,13,2009. Change to let the whole NP be the answer
					//This is a case where a list NEtagger finds "tiger" as an animal, but
					//fails to capture the determiner "the" in "the tiger".
					//ansPhrase = TreeUtil.getLabel(npHeadTree);
					ansPhrase = TreeUtil.getLabel(npTree);
					ansTerm = term;
				}
				// then put it as an answer candidate
				if (termNPtree != null) {
					ArrayList<QAPhrasePair> list = setupQuesTypePhrase("", ansPhrase, termNPtree, ansTerm);
					if (list != null)
						qaList.addAll(list);
				}
			}
		}
		
		
		
		// then deal with top level NP who's not under a NP and who's not a term
		tregex = "NP !>> NP";

		try {
			tPattern = TregexPattern.compile(tregex);
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
		}
		tregexMatcher = tPattern.matcher(tree);
		while (tregexMatcher.find()) {
			Tree npTree = tregexMatcher.getMatch();
			
			// don't generate questions regarding pronouns
			if (npTree.labels().toString().contains("PRP")) {
				continue;
			}
			
			// find out the lexical labels
			String npWord = TreeUtil.getTightLabelNoIndex(npTree);
			
			String ansPhrase = "";
			boolean npIsTerm = false;

			for (Term term:terms) {
				if (term.getNeTypes().length == 0) continue;
				String termStr = term.getText().replaceAll("\\s+", "");
				if(termStr.equals(npWord)) {
					npIsTerm = true;
					break;
				}
			}
			if (npIsTerm == false) {
				// put it as a candidate anyway since this is "over-generating"
				String[] neTypes = {"NEnp"};
				Term npTerm = new Term(TreeUtil.getLabelNoIndex(npTree), "NP", neTypes, "");
				ansPhrase = TreeUtil.getLabel(npTree);
				ArrayList<QAPhrasePair> list = setupQuesTypePhrase("", ansPhrase, npTree, npTerm);
				if (list != null)
					qaList.addAll(list);
			}

		}
		
		
		// at last deal with PP whose child is a NP
		tregex = "PP=pp < IN=in < (NP=np ?< DT=det)";

		try {
			tPattern = TregexPattern.compile(tregex);
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
		}
		tregexMatcher = tPattern.matcher(tree);
		while (tregexMatcher.find()) {
			Tree ppTree = tregexMatcher.getNode("pp");
			Tree inTree = tregexMatcher.getNode("in");
			Tree npTree = tregexMatcher.getNode("np");
			Tree dtTree = tregexMatcher.getNode("det");
			String determiner="";
			if(dtTree!=null) {
				determiner=TreeUtil.getTightLabelNoIndex(dtTree);
			}

			// unmovable phrases can't construct WH-movement
			String checkUnmv = ppTree.labels().toString();
			if (checkUnmv.contains("UNMV-")) {
				continue;
			}
			
			// don't generate questions regarding pronouns
			if (ppTree.labels().toString().contains("PRP")) {
				continue;
			}
			
			Tree npHeadTree = headFinder.determineHead(npTree);
			
			// find out the lexical labels
			String ppWord = TreeUtil.getTightLabelNoIndex(ppTree);
			String inWord = TreeUtil.getTightLabelNoIndex(inTree);
			String npWord = TreeUtil.getTightLabelNoIndex(npTree);
			String npHeadWord = TreeUtil.getTightLabelNoIndex(npHeadTree);

			Tree termNPtree = null;
			String ansPhrase = "";
			Term ansTerm = null;
			// if either npWord or npHeadWord is a term with a NE type
			// TODO: maybe we should trace back to the root PP phrase and let it be the ansPhrase
			for (Term term:terms) {
				if (term.getNeTypes().length == 0) continue;
				termNPtree = null;
				String termStr = term.getText().replaceAll("\\s+", "");
				if(termStr.equals(npWord) || (determiner+termStr).equals(npWord)) {
					termNPtree = npTree;
					ansPhrase = TreeUtil.getLabel(inTree)+" "+TreeUtil.getLabel(npTree);
					ansTerm = term;
				} else
				if(termStr.equals(npHeadWord) || termStr.equals(determiner+npHeadWord)) {
					termNPtree = npHeadTree;
					//TODO: maybe the whole NP should be the answer, rather than only the head of NP
					//X. Yao. Aug,13,2009. Change to let the whole NP be the answer
					//ansPhrase = TreeUtil.getLabel(inTree)+" "+TreeUtil.getLabel(npHeadTree);
					ansPhrase = TreeUtil.getLabel(inTree)+" "+TreeUtil.getLabel(npTree);
					ansTerm = term;
				}
				// then put it as an answer candidate
				if (termNPtree != null) {
					ArrayList<QAPhrasePair> list = setupQuesTypePhrase(inWord, ansPhrase, termNPtree, ansTerm);
					if (list != null) {
						qaList.addAll(list);
					}
				}
			}
		}

		// generate an extra Y/N question
		QAPhrasePair pair = new QAPhrasePair("", null, null);
		pair.setQuesType("Y/N");
		// don't forget the index -\\d+ when matching!
		String matchNOT = "ROOT < (S < (VP < /VB.?/ < (/RB/ < /(^not-\\d+$)|(^n't-\\d+$)/) ))";
		TregexPattern tregexPattern;
		try {
			tregexPattern = TregexPattern.compile(matchNOT);
		} catch (edu.stanford.nlp.trees.tregex.ParseException e) {
			MsgPrinter.printErrorMsg("Error parsing regex pattern.");
			return qaList;
		}
		tregexMatcher = tregexPattern.matcher(oriTree);
		if (tregexMatcher.find()) {
			pair.setAnsPhrase("no");
		} else {
			pair.setAnsPhrase("yes");
		}
		qaList.add(pair);
		return qaList;
	}
	
	// given a QA phrase pair, determine what kind of questions can be asked
	// TODO: add more QA types according to other NE tagger (currently only Stanford is used)
	private static String determineQuesType(QAPhrasePair pair) {
		String qType = "";
		String neType = Arrays.toString(pair.getAnsTerm().getNeTypes());
		if (neType.length() == 0) {
			MsgPrinter.printErrorMsg("NE types shouldn't be none");
		}
		if (neType.contains("NEperson")) {
			qType = "WHO";
		} else if (neType.contains("NElocation")) {
			qType = "WHERE";
		} else if (neType.contains("NEdate")) {
			qType = "WHEN";
		} else {
			qType = "WHAT";
		}
		return qType;
	}
	
	// given a QA phrase pair, construct question phrases
	// TODO: add more question phrases
	private static String constructQuesPhrase(QAPhrasePair pair) {
		String qPhrase = "";
		String qType = pair.getQuesType();
		if (qType.equals("WHO")) {
			qPhrase = "who";
		} else if (qType.equals("WHERE")) {
			qPhrase = "where";
		} else if (qType.equals("WHEN")) {
			qPhrase = "when";
		} else {
			qPhrase = "what";
		}
		
		return qPhrase;
	}

	/** Takes in a set of  QAPhrasePair elements and return an ArrayList of QAPhrasePair.
	 * Since the ansTerm may contain multiple Named Entity terms, it will return a list for every NE term.
	 * @param inWord the prepositional word (whose POS tag is IN) of the ansPhrase
	 * @param ansPhrase the answer phrase, without the inWord
	 * @param termTree the Tree of the ansPhrase
	 * @param ansTerm the Term of the ansPhrase
	 * @return an ArrayList of QAPhrasePair 
	 */
	private static ArrayList<QAPhrasePair> setupQuesTypePhrase(
			String inWord, String ansPhrase, Tree termTree, Term ansTerm) {
		ArrayList<QAPhrasePair> list = new ArrayList<QAPhrasePair>();
		String[] neTypes = ansTerm.getNeTypes();
		String qType="";
		String qPhrase="";
		for (String neType:neTypes) {
			String[] types = {neType};
			Term t = new Term(ansTerm, types);
			// Stanford NE tagger
			if (neType.equals("NEperson")) {
				qType = "WHO";
				qPhrase = "who";
			} else if (neType.equals("NElocation")) {
				qType = "WHERE";
				qPhrase = "where";
			} else if (neType.equals("NEorganization")) {
				qType = "WHAT";
				qPhrase = "what organization";
			} else
			//allPatternNames String[48]
			if (neType.equals("NEdate")) {
				qType = "WHEN";
				qPhrase = "when";
			} else if (neType.equals("NEeducationalInstitution")) {
				qType = "WHICH";
				qPhrase = "which school";
			} else if (neType.equals("NEfrequency")) {
				qType = "WHAT";
				qPhrase = "what frequency";
			} else if (neType.equals("NEpercentage")) {
				qType = "WHAT";
				qPhrase = "what percentage";
			} else if (neType.equals("NEtime")) {
				qType = "WHAT";
				qPhrase = "what time";
			} else if (neType.equals("NEurl")) {
				qType = "WHAT";
				qPhrase = "what URL";
			} else if (neType.equals("NEweekday")) {
				qType = "WHEN";
				qPhrase = "which day";
//			} else if (neType.equals("NEyear")) {
//				qType = "WHEN";
//				qPhrase = "which year";
			} else if (neType.equals("NEzipcode")) {
				qType = "WHAT";
				qPhrase = "what zipcode";
			} else if (neType.equals("NEangle")) {
				qType = "WHAT";
				qPhrase = "what angle";
			} else if (neType.equals("NEarea")) {
				qType = "WHAT";
				qPhrase = "what area";
			} else if (neType.equals("NEduration")) {
				qType = "WHAT";
				qPhrase = "what duration";
			} else if (neType.equals("NEgallons")) {
				qType = "HOW MANY";
				qPhrase = "how many gallons";
			} else if (neType.equals("NEgrams")) {
				qType = "HOW MANY";
				qPhrase = "how many grams";
			} else if (neType.equals("NElength")) {
				qType = "HOW LONG";
				qPhrase = "how long";
			} else if (neType.equals("NEliters")) {
				qType = "HOW MANY";
				qPhrase = "how many liters";
			} else if (neType.equals("NEmiles")) {
				qType = "HOW MANY";
				qPhrase = "how many miles";
			} else if (neType.equals("NEmoney")) {
				qType = "HOW MUCH";
				qPhrase = "how much money";
			} else if (neType.equals("NEmph")) {
				qType = "HOW FAST";
				qPhrase = "how fast";
			} else if (neType.equals("NEounces")) {
				qType = "HOW MANY";
				qPhrase = "how many ounces";
			} else if (neType.equals("NEpounds")) {
				qType = "HOW MAHY";
				qPhrase = "how many pounds";
			} else if (neType.equals("NErange")) {
				qType = "WHAT";
				qPhrase = "what range";
			} else if (neType.equals("NEsize")) {
				qType = "WHAT";
				qPhrase = "what size";
			} else if (neType.equals("NEspeed")) {
				qType = "HOW";
				qPhrase = "how fast";
			} else if (neType.equals("NEtemperature")) {
				qType = "WHAT";
				qPhrase = "what temperature";
			} else if (neType.equals("NEtons")) {
				qType = "HOW MANY";
				qPhrase = "how many tons";
			} else if (neType.equals("NEvolume")) {
				qType = "HOW MUCH";
				qPhrase = "how much volume";
			} else if (neType.equals("NEweight")) {
				qType = "HOW MUCH";
				qPhrase = "how much weight";
			// listNames String[95]
			} else if (neType.equals("NEactor")) {
				qType = "WHICH";
				qPhrase = "which actor";
			} else if (neType.equals("NEairport")) {
				qType = "WHICH";
				qPhrase = "which airport";
			} else if (neType.equals("NEanimal")) {
				qType = "WHAT";
				qPhrase = "what animal";
			} else if (neType.equals("NEanthem")) {
				qType = "WHAT";
				qPhrase = "what anthem";
			} else if (neType.equals("NEauthor")) {
				qType = "WHICH";
				qPhrase = "which author";
			} else if (neType.equals("NEaward")) {
				qType = "WHICH";
				qPhrase = "which award";
			} else if (neType.equals("NEbacteria")) {
				qType = "WHAT";
				qPhrase = "what bacteria";
			} else if (neType.equals("NEbird")) {
				qType = "WHAT";
				qPhrase = "what bird";
			} else if (neType.equals("NEbirthstone")) {
				qType = "WHAT";
				qPhrase = "what birthstone";
			} else if (neType.equals("NEbodyPart")) {
				qType = "WHAT";
				qPhrase = "what bodypart";
			} else if (neType.equals("NEbook")) {
				qType = "WHAT";
				qPhrase = "what book";
			} else if (neType.equals("NEcanal")) {
				qType = "WHAT";
				qPhrase = "what canal";
			} else if (neType.equals("NEcapital")) {
				qType = "WHICH";
				qPhrase = "which capital city";
			} else if (neType.equals("NEchemicalElement")) {
				qType = "WHAT";
				qPhrase = "what chemical element";
			} else if (neType.equals("NEcolor")) {
				qType = "WHAT";
				qPhrase = "what color";
			} else if (neType.equals("NEcompetition")) {
				qType = "WHAT";
				qPhrase = "what competition";
			} else if (neType.equals("NEconflict")) {
				qType = "WHAT";
				qPhrase = "what war";
			} else if (neType.equals("NEcontinent")) {
				qType = "WHICH";
				qPhrase = "which continent";
			} else if (neType.equals("NEcountry")) {
				qType = "WHICH";
				qPhrase = "which country";
			} else if (neType.equals("NEcrime")) {
				qType = "WHAT";
				qPhrase = "what crime";
			} else if (neType.equals("NEcurrency")) {
				qType = "WHAT";
				qPhrase = "what currency";
			} else if (neType.equals("NEdirector")) {
				qType = "WHICH";
				qPhrase = "which director";
			} else if (neType.equals("NEdisease")) {
				qType = "WHAT";
				qPhrase = "what disease";
			} else if (neType.equals("NEdrug")) {
				qType = "WHAT";
				qPhrase = "what drug";
			} else if (neType.equals("NEeducationalInstitution")) {
				qType = "WHICH";
				qPhrase = "which school";
			} else if (neType.equals("NEethnicGroup")) {
				qType = "WHAT";
				qPhrase = "what ethnic group";
			} else if (neType.equals("NEfestival")) {
				qType = "WHAT";
				qPhrase = "what festival";
			} else if (neType.equals("NEfilm")) {
				qType = "WHAT";
				qPhrase = "what film";
			} else if (neType.equals("NEfilmType")) {
				qType = "WHAT";
				qPhrase = "what film type";
			} else if (neType.equals("NEfirstName")) {
				qType = "WHO";
				qPhrase = "who";
			} else if (neType.equals("NEflower")) {
				qType = "WHAT";
				qPhrase = "what flower";
			} else if (neType.equals("NEfruit")) {
				qType = "WHAT";
				qPhrase = "what fruit";
			} else if (neType.equals("NEhemisphere")) {
				qType = "WHICH";
				qPhrase = "which hemisphere";
			} else if (neType.equals("NEisland")) {
				qType = "WHAT";
				qPhrase = "what island";
			} else if (neType.equals("NElake")) {
				qType = "WHAT";
				qPhrase = "what lake";
			} else if (neType.equals("NElanguage")) {
				qType = "WHAT";
				qPhrase = "what language";
			} else if (neType.equals("NEmaterial")) {
				qType = "WHAT";
				qPhrase = "what material";
			} else if (neType.equals("NEmathematician")) {
				qType = "WHICH";
				qPhrase = "which mathematician";
			} else if (neType.equals("NEmedicalTreatment")) {
				qType = "WHAT";
				qPhrase = "what medical treatment";
			} else if (neType.equals("NEmedicinal")) {
				qType = "WHAT";
				qPhrase = "what medicinal";
			} else if (neType.equals("NEmetal")) {
				qType = "WHAT";
				qPhrase = "what metal";
			} else if (neType.equals("NEmilitaryRank")) {
				qType = "WHAT";
				qPhrase = "what military rank";
			} else if (neType.equals("NEmineral")) {
				qType = "WHAT";
				qPhrase = "what mineral";
			} else if (neType.equals("NEministry")) {
				qType = "WHAT";
				qPhrase = "what ministry";
//			} else if (neType.equals("NEmonth")) {
//				qType = "WHICH";
//				qPhrase = "what month";
			} else if (neType.equals("NEmountain")) {
				qType = "WHAT";
				qPhrase = "what mountain";
			} else if (neType.equals("NEmountainRange")) {
				qType = "WHAT";
				qPhrase = "what mountain range";
			} else if (neType.equals("NEmusical")) {
				qType = "WHAT";
				qPhrase = "what musical";
			} else if (neType.equals("NEmusicalInstrument")) {
				qType = "WHAT";
				qPhrase = "what musical instrument";
			} else if (neType.equals("NEmusicType")) {
				qType = "WHAT";
				qPhrase = "what music type";
			} else if (neType.equals("NEnarcotic")) {
				qType = "WHAT";
				qPhrase = "what narcotic";
			} else if (neType.equals("NEnationality")) {
				qType = "WHAT";
				qPhrase = "what nationality";
			} else if (neType.equals("NEnationalPark")) {
				qType = "WHAT";
				qPhrase = "what nationalPark";
			} else if (neType.equals("NEnewspaper")) {
				qType = "WHAT";
				qPhrase = "what newspaper";
			} else if (neType.equals("NEnobleTitle")) {
				qType = "WHAT";
				qPhrase = "what noble title";
			} else if (neType.equals("NEocean")) {
				qType = "WHAT";
				qPhrase = "what ocean";
			} else if (neType.equals("NEopera")) {
				qType = "WHAT";
				qPhrase = "what opera";
			} else if (neType.equals("NEorganization")) {
				qType = "WHAT";
				qPhrase = "what organization";
			} else if (neType.equals("NEpathogen")) {
				qType = "WHAT";
				qPhrase = "what pathogen";
			} else if (neType.equals("NEpeninsula")) {
				qType = "WHAT";
				qPhrase = "what peninsula";
			} else if (neType.equals("NEplanet")) {
				qType = "WHAT";
				qPhrase = "what planet";
			} else if (neType.equals("NEplant")) {
				qType = "WHAT";
				qPhrase = "what plant";
			} else if (neType.equals("NEplaywright")) {
				qType = "WHAT";
				qPhrase = "what playwright";
			} else if (neType.equals("NEpoliceRank")) {
				qType = "WHAT";
				qPhrase = "what police rank";
			} else if (neType.equals("NEpoliticalParty")) {
				qType = "WHAT";
				qPhrase = "what political party";
			} else if (neType.equals("NEprofession")) {
				qType = "WHO";
				qPhrase = "who";
//			} else if (neType.equals("NEprovince")) {
//				qType = "WHAT";
//				qPhrase = "what province";
			} else if (neType.equals("NEradioStation")) {
				qType = "WHAT";
				qPhrase = "what radio station";
			} else if (neType.equals("NErelation")) {
				qType = "WHAT";
				qPhrase = "what relation";
			} else if (neType.equals("NEreligion")) {
				qType = "WHAT";
				qPhrase = "what religion";
			} else if (neType.equals("NEriver")) {
				qType = "WHAT";
				qPhrase = "what river";
			} else if (neType.equals("NEscientist")) {
				qType = "WHICH";
				qPhrase = "which scientist";
			} else if (neType.equals("NEsea")) {
				qType = "WHAT";
				qPhrase = "what sea";
			} else if (neType.equals("NEseason")) {
				qType = "WHAT";
				qPhrase = "what season";
			} else if (neType.equals("NEshow")) {
				qType = "WHAT";
				qPhrase = "what show";
			} else if (neType.equals("NEshowType")) {
				qType = "WHAT";
				qPhrase = "what show type";
			} else if (neType.equals("NEsocialTitle")) {
				qType = "WHO";
				qPhrase = "who";
			} else if (neType.equals("NEsport")) {
				qType = "WHAT";
				qPhrase = "what sport";
			} else if (neType.equals("NEstadium")) {
				qType = "WHAT";
				qPhrase = "what stadium";
//			} else if (neType.equals("NEstate")) {
//				qType = "WHAT";
//				qPhrase = "what state";
			} else if (neType.equals("NEstone")) {
				qType = "WHAT";
				qPhrase = "what stone";
			} else if (neType.equals("NEstyle")) {
				qType = "WHAT";
				qPhrase = "what style";
			} else if (neType.equals("NEteam")) {
				qType = "WHAT";
				qPhrase = "what team";
			} else if (neType.equals("NEtherapy")) {
				qType = "WHAT";
				qPhrase = "what therapy";
			} else if (neType.equals("NEtimezone")) {
				qType = "WHAT";
				qPhrase = "what timezone";
			} else if (neType.equals("NEtvChannel")) {
				qType = "WHAT";
				qPhrase = "what TV channel";
			} else if (neType.equals("NEusPresident")) {
				qType = "WHAT";
				qPhrase = "which US president";
			} else if (neType.equals("NEvaccine")) {
				qType = "WHAT";
				qPhrase = "what vaccine";
			} else if (neType.equals("NEvirus")) {
				qType = "WHAT";
				qPhrase = "what virus";
			} else if (neType.equals("NEzodiacSign")) {
				qType = "WHAT";
				qPhrase = "what zodiacSign";
			} else if (neType.equals("NEnp")) {
				qType = "WHAT";
				qPhrase = "what";
			} else {
//				qType = "WHAT";
//				qPhrase = "what";
				return null;
			}
			QAPhrasePair p = new QAPhrasePair(qType, qPhrase, inWord, ansPhrase, termTree, t);
			if (!list.contains(p)) {
				list.add(p);
			}
			// add another phrase with IN(preposition) in a PP
			if (inWord.length() != 0) {
				if (qPhrase.equals("when")) {
					qPhrase = "which day";
				} else if (qPhrase.equals("where")) {
					qPhrase = "which location";
				}
				qPhrase = inWord+" "+qPhrase;
				p = new QAPhrasePair(qType, qPhrase, inWord, ansPhrase, termTree, t);
				if (!list.contains(p)) {
					list.add(p);
				}
			}
		}
		return list;
		
	}
}
