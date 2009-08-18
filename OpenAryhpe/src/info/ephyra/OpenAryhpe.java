package info.ephyra;

import info.ephyra.answeranalysis.AnswerAnalyzer;
import info.ephyra.answeranalysis.Answers;
import info.ephyra.answeranalysis.Answer;
import info.ephyra.answerselection.AnswerSelection;
import info.ephyra.answerselection.filters.AnswerPatternFilter;
import info.ephyra.answerselection.filters.AnswerTypeFilter;
import info.ephyra.answerselection.filters.DuplicateFilter;
import info.ephyra.answerselection.filters.FactoidSubsetFilter;
import info.ephyra.answerselection.filters.FactoidsFromPredicatesFilter;
import info.ephyra.answerselection.filters.PredicateExtractionFilter;
import info.ephyra.answerselection.filters.QuestionKeywordsFilter;
import info.ephyra.answerselection.filters.ScoreCombinationFilter;
import info.ephyra.answerselection.filters.ScoreNormalizationFilter;
import info.ephyra.answerselection.filters.ScoreSorterFilter;
import info.ephyra.answerselection.filters.StopwordFilter;
import info.ephyra.answerselection.filters.TruncationFilter;
import info.ephyra.answerselection.filters.WebDocumentFetcherFilter;
import info.ephyra.io.Logger;
import info.ephyra.io.MsgPrinter;
import info.ephyra.nlp.LingPipe;
import info.ephyra.nlp.NETagger;
import info.ephyra.nlp.OpenNLP;
import info.ephyra.nlp.SnowballStemmer;
import info.ephyra.nlp.StanfordNeTagger;
import info.ephyra.nlp.StanfordParser;
import info.ephyra.nlp.indices.FunctionWords;
import info.ephyra.nlp.indices.IrregularVerbs;
import info.ephyra.nlp.indices.Prepositions;
import info.ephyra.nlp.indices.WordFrequencies;
import info.ephyra.nlp.semantics.ontologies.Ontology;
import info.ephyra.nlp.semantics.ontologies.WordNet;
import info.ephyra.querygeneration.Query;
import info.ephyra.querygeneration.QueryGeneration;
import info.ephyra.querygeneration.generators.BagOfTermsG;
import info.ephyra.querygeneration.generators.BagOfWordsG;
import info.ephyra.querygeneration.generators.PredicateG;
import info.ephyra.querygeneration.generators.QuestionInterpretationG;
import info.ephyra.querygeneration.generators.QuestionReformulationG;
import info.ephyra.questionanalysis.AnalyzedQuestion;
import info.ephyra.questionanalysis.QuestionAnalysis;
import info.ephyra.questionanalysis.QuestionInterpreter;
import info.ephyra.questionanalysis.QuestionNormalizer;
import info.ephyra.questiongeneration.QAPair;
import info.ephyra.questiongeneration.QuestionAnswerPair;
import info.ephyra.questiongeneration.QuestionGenerator;
import info.ephyra.search.Result;
import info.ephyra.search.Search;
import info.ephyra.search.searchers.IndriKM;
import info.ephyra.search.searchers.YahooKM;
import info.ephyra.treeansweranalysis.TreeAnswer;
import info.ephyra.treeansweranalysis.TreeAnswerAnalyzer;
import info.ephyra.treeansweranalysis.TreeAnswers;
import info.ephyra.treeansweranalysis.TreeBreaker;
import info.ephyra.treeansweranalysis.TreeCompressor;
import info.ephyra.treeansweranalysis.UnmovableTreeMarker;
import info.ephyra.treequestiongeneration.QAPhrasePair;
import info.ephyra.treequestiongeneration.TreeQuestionGenerator;
import info.ephyra.treequestiongeneration.VerbDecomposer;
import info.ephyra.util.StringUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.log4j.PropertyConfigurator;

/**
 * <code>OpenAryhpe</code> is an open framework for question generation (QG).
 * 
 * @author Nico Schlaefer
 * @version 2008-01-26
 */
public class OpenAryhpe {
	/** Maximum number of factoid answers. */
	protected static final int FACTOID_MAX_ANSWERS = 10;
	/** Absolute threshold for factoid question scores. */
	protected static final float FACTOID_ABS_THRESH = 0;
	/** Relative threshold for list question scores (fraction of top score). */
	protected static final float LIST_REL_THRESH = 0.1f;
	/** Apache logger */
	private static org.apache.log4j.Logger log;
	/** Whether to load pattern matching to generate questions */
	private boolean patternMatching = false;
	
	/** Serialized classifier for score normalization. */
	public static final String NORMALIZER =
		"res/scorenormalization/classifiers/" +
		"AdaBoost70_" +
		"Score+Extractors_" +
		"TREC10+TREC11+TREC12+TREC13+TREC14+TREC15+TREC8+TREC9" +
		".serialized";
	
	/** The directory of Ephyra, required when Ephyra is used as an API. */
	protected String dir;
	
	/**
	 * Entry point of Ephyra. Initializes the engine and starts the command line
	 * interface.
	 * 
	 * @param args command line arguments are ignored
	 */
	public static void main(String[] args) {
		// enable output of status and error messages
		MsgPrinter.enableStatusMsgs(true);
		MsgPrinter.enableErrorMsgs(true);
		
		// set log file and enable logging
		//Logger.setLogfile("log/OpenEphyra");
		Logger.enableLogging(false);
		
		// initialize Ephyra and start command line interface
		(new OpenAryhpe()).commandLine();
	}
	
	/**
	 * <p>Creates a new instance of Ephyra and initializes the system.</p>
	 * 
	 * <p>For use as a standalone system.</p>
	 */
	protected OpenAryhpe() {
		this("");
	}
	
	/**
	 * <p>Creates a new instance of Ephyra and initializes the system.</p>
	 * 
	 * <p>For use as an API.</p>
	 * 
	 * @param dir directory of Ephyra
	 */
	public OpenAryhpe(String dir) {
		this.dir = dir;
		
		// get logging working
		PropertyConfigurator.configure("conf/log4j.properties");
		log = org.apache.log4j.Logger.getLogger(OpenAryhpe.class);
		log.info("OpenAryhpe started at "+MsgPrinter.getTimestamp());
		
		MsgPrinter.printInitializing();
		
		// create tokenizer
		MsgPrinter.printStatusMsg("Creating tokenizer...");
		if (!OpenNLP.createTokenizer(dir +
				"res/nlp/tokenizer/opennlp/EnglishTok.bin.gz"))
			MsgPrinter.printErrorMsg("Could not create tokenizer.");
		LingPipe.createTokenizer();
		
		// create sentence detector
		MsgPrinter.printStatusMsg("Creating sentence detector...");
		if (!OpenNLP.createSentenceDetector(dir +
				"res/nlp/sentencedetector/opennlp/EnglishSD.bin.gz"))
			MsgPrinter.printErrorMsg("Could not create sentence detector.");
		LingPipe.createSentenceDetector();
		
		// create stemmer
		MsgPrinter.printStatusMsg("Creating stemmer...");
		SnowballStemmer.create();
		
		// create part of speech tagger
		MsgPrinter.printStatusMsg("Creating POS tagger...");
		if (!OpenNLP.createPosTagger(
				dir + "res/nlp/postagger/opennlp/tag.bin.gz",
				dir + "res/nlp/postagger/opennlp/tagdict"))
			MsgPrinter.printErrorMsg("Could not create OpenNLP POS tagger.");
//		if (!StanfordPosTagger.init(dir + "res/nlp/postagger/stanford/" +
//				"wsj3t0-18-bidirectional/train-wsj-0-18.holder"))
//			MsgPrinter.printErrorMsg("Could not create Stanford POS tagger.");
		
		// create chunker
		MsgPrinter.printStatusMsg("Creating chunker...");
		if (!OpenNLP.createChunker(dir +
				"res/nlp/phrasechunker/opennlp/EnglishChunk.bin.gz"))
			MsgPrinter.printErrorMsg("Could not create chunker.");
		
		// create syntactic parser
		MsgPrinter.printStatusMsg("Creating syntactic parser...");
//		if (!OpenNLP.createParser(dir + "res/nlp/syntacticparser/opennlp/"))
//			MsgPrinter.printErrorMsg("Could not create OpenNLP parser.");
		try {
			StanfordParser.initialize();
		} catch (Exception e) {
			MsgPrinter.printErrorMsg("Could not create Stanford parser."+e.toString());
		}
		
		// create named entity taggers
		MsgPrinter.printStatusMsg("Creating NE taggers...");
		NETagger.loadListTaggers(dir + "res/nlp/netagger/lists/");
		NETagger.loadRegExTaggers(dir + "res/nlp/netagger/patterns.lst");
		MsgPrinter.printStatusMsg("  ...loading Standford NETagger");
//		if (!NETagger.loadNameFinders(dir + "res/nlp/netagger/opennlp/"))
//			MsgPrinter.printErrorMsg("Could not create OpenNLP NE tagger.");
		if (!StanfordNeTagger.isInitialized() && !StanfordNeTagger.init())
			MsgPrinter.printErrorMsg("Could not create Stanford NE tagger.");
		MsgPrinter.printStatusMsg("  ...done");
		
		// create linker
//		MsgPrinter.printStatusMsg("Creating linker...");
//		if (!OpenNLP.createLinker(dir + "res/nlp/corefresolver/opennlp/"))
//			MsgPrinter.printErrorMsg("Could not create linker.");
		
		// create WordNet dictionary
		MsgPrinter.printStatusMsg("Creating WordNet dictionary...");
		if (!WordNet.initialize(dir +
				"res/ontologies/wordnet/file_properties.xml"))
			MsgPrinter.printErrorMsg("Could not create WordNet dictionary.");
		
		// load function words (numbers are excluded)
		MsgPrinter.printStatusMsg("Loading function verbs...");
		if (!FunctionWords.loadIndex(dir +
				"res/indices/functionwords_nonumbers"))
			MsgPrinter.printErrorMsg("Could not load function words.");
		
		// load prepositions
		MsgPrinter.printStatusMsg("Loading prepositions...");
		if (!Prepositions.loadIndex(dir +
				"res/indices/prepositions"))
			MsgPrinter.printErrorMsg("Could not load prepositions.");
		
		// load irregular verbs
		MsgPrinter.printStatusMsg("Loading irregular verbs...");
		if (!IrregularVerbs.loadVerbs(dir + "res/indices/irregularverbs"))
			MsgPrinter.printErrorMsg("Could not load irregular verbs.");
		
		// load word frequencies
		MsgPrinter.printStatusMsg("Loading word frequencies...");
		if (!WordFrequencies.loadIndex(dir + "res/indices/wordfrequencies"))
			MsgPrinter.printErrorMsg("Could not load word frequencies.");
		
		if (patternMatching) {
			// load query reformulators
			MsgPrinter.printStatusMsg("Loading query reformulators...");
			if (!QuestionReformulationG.loadReformulators(dir +
					"res/reformulations/"))
				MsgPrinter.printErrorMsg("Could not load query reformulators.");
			
			// load question patterns
			MsgPrinter.printStatusMsg("Loading question patterns...");
			if (!QuestionGenerator.loadPatterns(dir +
					"res/patternlearning/questionpatternsTest/"))
				MsgPrinter.printErrorMsg("Could not load question patterns.");
			
			// load answer patterns
			MsgPrinter.printStatusMsg("Loading answer patterns...");
			if (!AnswerAnalyzer.loadPatterns(dir +
					"res/patternlearning/answerpatternsTest/"))
				MsgPrinter.printErrorMsg("Could not load answer patterns.");
		}
		
		// load Tregex patterns for unmovable phrases 
		MsgPrinter.printStatusMsg("Loading Tregex patterns for unmovable phrases...");
		if (!UnmovableTreeMarker.loadUnmvRegex("res/nlp/treetransform/unmovable"))
			MsgPrinter.printErrorMsg("Could not Tregex patterns for unmovable phrases.");
		
		// Initialize TreeBreaker
		MsgPrinter.printStatusMsg("Initialize TreeBreaker...");
		if (!TreeBreaker.initialize()) {
			MsgPrinter.printErrorMsg("failed.");
			System.exit(-1);
		}
		else
			MsgPrinter.printStatusMsg("Done");
		
		// Initialize TreeCompressor
		MsgPrinter.printStatusMsg("Initialize TreeCompressor...");
		if (!TreeCompressor.initialize()) {
			MsgPrinter.printErrorMsg("failed.");
			System.exit(-1);
		}
		else
			MsgPrinter.printStatusMsg("Done");
		
		// Initialize VerbDecomposer 
		MsgPrinter.printStatusMsg("Initialize VerbDecomposer...");
		if (!VerbDecomposer.initialize()) {
			MsgPrinter.printErrorMsg("failed.");
			System.exit(-1);
		}
		else
			MsgPrinter.printStatusMsg("Done");
		
		MsgPrinter.printUsage();
	}
	
	/**
	 * Reads a line from the command prompt.
	 * 
	 * @return user input
	 */
	protected String readLine() {
		try {
			return new java.io.BufferedReader(new
				java.io.InputStreamReader(System.in)).readLine();
		}
		catch(java.io.IOException e) {
			return new String("");
		}
	}
	
	/**
	 * Initializes the pipeline for factoid questions.
	 */
	protected void initFactoid() {
		// question analysis
		Ontology wordNet = new WordNet();
		// - dictionaries for term extraction
		AnswerAnalyzer.clearDictionaries();
		AnswerAnalyzer.addDictionary(wordNet);
		// - ontologies for term expansion
		//QuestionAnalysis.clearOntologies();
		//QuestionAnalysis.addOntology(wordNet);
		
		// query generation
		QueryGeneration.clearQueryGenerators();
//		QueryGeneration.addQueryGenerator(new BagOfWordsG());
//		QueryGeneration.addQueryGenerator(new BagOfTermsG());
//		QueryGeneration.addQueryGenerator(new PredicateG());
//		QueryGeneration.addQueryGenerator(new QuestionInterpretationG());
//		QueryGeneration.addQueryGenerator(new QuestionReformulationG());
		
		// search
		// - knowledge miners for unstructured knowledge sources
		Search.clearKnowledgeMiners();
//		Search.addKnowledgeMiner(new GoogleKM());
//		Search.addKnowledgeMiner(new YahooKM());
//		for (String[] indriIndices : IndriKM.getIndriIndices())
//			Search.addKnowledgeMiner(new IndriKM(indriIndices, false));
//		for (String[] indriServers : IndriKM.getIndriServers())
//			Search.addKnowledgeMiner(new IndriKM(indriServers, true));
		// - knowledge annotators for (semi-)structured knowledge sources
		Search.clearKnowledgeAnnotators();
		
		// answer extraction and selection
		// (the filters are applied in this order)
		AnswerSelection.clearFilters();
		// - answer extraction filters
		//AnswerSelection.addFilter(new AnswerTypeFilter());
		AnswerSelection.addFilter(new AnswerPatternFilter());
		//AnswerSelection.addFilter(new WebDocumentFetcherFilter());
		//AnswerSelection.addFilter(new PredicateExtractionFilter());
		//AnswerSelection.addFilter(new FactoidsFromPredicatesFilter());
		//AnswerSelection.addFilter(new TruncationFilter());
		// - answer selection filters
		//AnswerSelection.addFilter(new StopwordFilter());
		//AnswerSelection.addFilter(new QuestionKeywordsFilter());
		//AnswerSelection.addFilter(new ScoreNormalizationFilter(NORMALIZER));
		//AnswerSelection.addFilter(new ScoreCombinationFilter());
		//AnswerSelection.addFilter(new FactoidSubsetFilter());
		//AnswerSelection.addFilter(new DuplicateFilter());
		//AnswerSelection.addFilter(new ScoreSorterFilter());
	}
	
	/**
	 * Runs the pipeline on a factoid question and returns an array of up to
	 * <code>maxAnswers</code> results that have a score of at least
	 * <code>absThresh</code>.
	 * 
	 * @param aq analyzed question
	 * @param maxAnswers maximum number of answers
	 * @param absThresh absolute threshold for scores
	 * @return array of results
	 */
	protected Result[] runPipeline(AnalyzedQuestion aq, int maxAnswers,
								  float absThresh) {
		// query generation
		MsgPrinter.printGeneratingQueries();
		Query[] queries = QueryGeneration.getQueries(aq);
		
		// search
		MsgPrinter.printSearching();
		Result[] results = Search.doSearch(queries);
		
		// answer selection
		MsgPrinter.printSelectingAnswers();
		results = AnswerSelection.getResults(results, maxAnswers, absThresh);
		
		return results;
	}
	
	/**
	 * Returns the directory of the Ephyra engine.
	 * 
	 * @return directory
	 */
	public String getDir() {
		return dir;
	}
	
	/**
	 * <p>A command line interface for Ephyra.</p>
	 * 
	 * <p>Repeatedly queries the user for a question, asks the system the
	 * question and prints out and logs the results.</p>
	 * 
	 * <p>The command <code>exit</code> can be used to quit the program.</p>
	 */
	public void commandLine() {
		while (true) {
			// query the user for a question, quit if the user types in "exit"
			MsgPrinter.printQuestionPrompt();
			String question = readLine().trim();
			if (question.length() == 0) continue;
			if (question.equalsIgnoreCase("exit")) {
				log.info("OpenAryhpe ended at "+MsgPrinter.getTimestamp());
				System.exit(0);
			}
			
			// ask the question
			Result[] results = new Result[0];
			// LIST: is used for QA TREC task, don't use it in QG.
			if (question.startsWith("LIST:")) {
				question = question.substring(5).trim();
				Logger.logListStart(question);
				results = askList(question, LIST_REL_THRESH);
				Logger.logResults(results);
				Logger.logListEnd();
			} else if (question.startsWith("FILE: ")||question.startsWith("file: ")) {
				// when input is the following format:
				// FILE: in.txt out.txt
				// read text from in.txt and output to out.txt
				String fileLine = question.substring(6).trim();
				String[] files = fileLine.split("\\s+");
				if (files.length != 2) {
					MsgPrinter.printErrorMsg("FILE field must only contain two valid files. e.g.:");
					MsgPrinter.printErrorMsg("FILE: input.txt output.txt");
					continue;
				}

				if (files[1].endsWith("xml") || files[1].endsWith("XML")) {
					processXmlFiles(files[0], files[1]);
				} else {
					processTxtFiles(files[0], files[1]);
				}
				
			} else {
				question = TreeBreaker.doBreak(question);
				TreeAnswers treeAnswers = new TreeAnswers(question);
				ArrayList<TreeAnswer> treeAnsList = TreeAnswerAnalyzer.analyze(treeAnswers);
				Iterator<TreeAnswer> tAnsIter = treeAnsList.iterator();
				while (tAnsIter.hasNext()) {
					TreeAnswer treeAnswer = tAnsIter.next();
					TreeQuestionGenerator.generate(treeAnswer);
				}
				TreeQuestionGenerator.print(treeAnsList);
				
				if (patternMatching) {
					Answers answers = new Answers(question);
					ArrayList<Answer> ansList = AnswerAnalyzer.analyze(answers);
					ArrayList<QAPair> qaPairList = QuestionGenerator.makeQApair(ansList);
					QuestionGenerator.printQAlist(qaPairList);
				}
			}
			
		}
	}
	
	/**
	 * Generate questions from the text of <code>inFile</code> and output to <code>outFile</code> 
	 * in evaluation format.
	 * 
	 */
	public void processTxtFiles (String inFile, String outFile) {
		if (inFile == null || outFile == null) {
			return;
		}
		
		int paragraphCounter=0, wordCounter=0;
		int oriSentCounter=0, actualSentCounter=0, quesCounter=0;
		
		try {
			BufferedReader in = new BufferedReader(new FileReader(new File(inFile)));
			BufferedWriter out = new BufferedWriter(new FileWriter(outFile));
			while (in.ready()) {
				String paragraph = in.readLine().trim();
				if (paragraph.length() == 0 || paragraph.startsWith("//"))
					continue;
				
				paragraphCounter++;
				MsgPrinter.printStatusMsg("processing paragraph "+paragraphCounter+"...");
				out.write("Paragraph "+paragraphCounter+": ");
				out.newLine();
				out.write(paragraph);
				out.newLine();
				
				// break the paragraph
				String[] sentences = OpenNLP.sentDetect(paragraph); 
				oriSentCounter += sentences.length;
				for (String sent:sentences) {
					wordCounter += (new StringTokenizer(sent)).countTokens();
				}
				paragraph = TreeBreaker.doBreak(paragraph);
				actualSentCounter += OpenNLP.sentDetect(paragraph).length;
				
				// generate questions
				TreeAnswers treeAnswers = new TreeAnswers(paragraph);
				ArrayList<TreeAnswer> treeAnsList = TreeAnswerAnalyzer.analyze(treeAnswers);
				Iterator<TreeAnswer> tAnsIter = treeAnsList.iterator();
				while (tAnsIter.hasNext()) {
					TreeAnswer treeAnswer = tAnsIter.next();
					TreeQuestionGenerator.generate(treeAnswer);
					quesCounter += treeAnswer.size();
				}
				
				// print formatted questions for evaluation
				TreeQuestionGenerator.printForICTevaluation(treeAnsList, out);
				
			}
			in.close();
			
			out.write("Summary:");
			out.newLine();
			out.write("Paragraph: "+paragraphCounter
					+". Original Sentences: "+oriSentCounter
					+". Actual Sentences: "+actualSentCounter
					+". Words: "+wordCounter
					+". Questions: "+quesCounter+".");
			out.newLine();
			out.newLine();
			
			out.close();
		} catch (java.io.IOException e) {
			System.err.println(e);
		}
	}
	
	/**
	 * Generate questions from the text of <code>inFile</code> and output to <code>outFile</code> 
	 * in XML format (used by plist).
	 * 
	 */
	public void processXmlFiles (String inFile, String outFile) {
		if (inFile == null || outFile == null) {
			return;
		}
		
		Hashtable<String, Integer> ansSentHash = new Hashtable<String, Integer>();
		Hashtable<String, Integer> ansPhraseHash = new Hashtable<String, Integer>();
		Integer sentIDcount = new Integer(0);
		Integer phraseIDcount = new Integer(0);
		
		int paragraphCounter=0, wordCounter=0;
		int oriSentCounter=0, actualSentCounter=0, quesCounter=0;
		
		try {
			BufferedReader in = new BufferedReader(new FileReader(new File(inFile)));
			BufferedWriter out = new BufferedWriter(new FileWriter(outFile));
			out.write("<?xml version=\"1.0\"?>\n");
			out.write("<Workbook>\n");
			out.write("\t<Row>\n");
			out.write("\t\t<Cell><Data ss:Type=\"String\">question</Data></Cell>\n");
			out.write("\t\t<Cell><Data ss:Type=\"String\">text</Data></Cell>\n");
			out.write("\t\t<Cell><Data ss:Type=\"String\">ID</Data></Cell>\n");
			out.write("\t</Row>\n");

			while (in.ready()) {
				String paragraph = in.readLine().trim();
				if (paragraph.length() == 0 || paragraph.startsWith("//"))
					continue;
				
				paragraphCounter++;
				MsgPrinter.printStatusMsg("processing paragraph "+paragraphCounter+"...");
				
				// break the paragraph
				String[] sentences = OpenNLP.sentDetect(paragraph); 
				oriSentCounter += sentences.length;
				for (String sent:sentences) {
					wordCounter += (new StringTokenizer(sent)).countTokens();
				}
				paragraph = TreeBreaker.doBreak(paragraph);
				actualSentCounter += OpenNLP.sentDetect(paragraph).length;
				
				// generate questions
				TreeAnswers treeAnswers = new TreeAnswers(paragraph);
				ArrayList<TreeAnswer> treeAnsList = TreeAnswerAnalyzer.analyze(treeAnswers);
				Iterator<TreeAnswer> tAnsIter = treeAnsList.iterator();
				while (tAnsIter.hasNext()) {
					TreeAnswer treeAnswer = tAnsIter.next();
					TreeQuestionGenerator.generate(treeAnswer);
				}
				
				// print formatted questions to XML files with unique IDs for sentences and answer phrases

				// Entries in treeAnsList are based on sentences
				// Every sentence has an entry in treeAnsList
				tAnsIter = treeAnsList.iterator();
				TreeAnswer treeAnswer;
				ArrayList<QAPhrasePair> qaPhraseList;
				Iterator<QAPhrasePair> pPairIter;
				QAPhrasePair pPair;
				String question="", ansSent="", ansPhrase="";
				String sentID="", phraseID="";

				try {
					while (tAnsIter.hasNext()) {
						treeAnswer = tAnsIter.next();
						// don't output y/s questions
						// if only contains 1 q-a pair, it must be a y/n question
						if(treeAnswer.size() == 1) continue;

						ansSent = treeAnswer.getSentence();
						ansSent = StringUtils.removeXMLspecials(ansSent);
						if (!ansSentHash.containsKey(ansSent)) {
							sentIDcount++;
							ansSentHash.put(ansSent, sentIDcount);
						} else {
							sentIDcount = ansSentHash.get(ansSent);
						}

						// For every sentence, there is a list of q-a pairs
						qaPhraseList = treeAnswer.getQAPhraseList();
						pPairIter = qaPhraseList.iterator();
						while (pPairIter.hasNext()) {
							pPair = pPairIter.next();
							
							// skip y/n questions
							if (pPair.getQuesType().equals("Y/N")) continue;
							
							question = pPair.getQuesSentence();
							ansPhrase = pPair.getAnsPhrase().replaceAll("-\\d+\\b", "");
							question = StringUtils.removeXMLspecials(question);
							ansPhrase = StringUtils.removeXMLspecials(ansPhrase);
							
							// for every ansPhrase, we output 1 question-ansSent pair and 1 question-ansPhrase pair
							quesCounter += 2;
							if (!ansPhraseHash.containsKey(ansPhrase)) {
								phraseIDcount = ansPhraseHash.size()+1;
								ansPhraseHash.put(ansPhrase, phraseIDcount);
							} else {
								phraseIDcount = ansPhraseHash.get(ansPhrase);
							}
							
							// S1-S2: the 2nd q-a pair for sentence 1. the answer is a sentence. 
							// S1-P2: the 2nd q-a pair for sentence 1. the answer is a phrase.
							sentID = "S"+sentIDcount;
							phraseID = "P"+phraseIDcount;
							
							out.write("\t<Row>\n");
							out.write("\t\t<Cell><Data ss:Type=\"String\">"+question+"</Data></Cell>\n");
							out.write("\t\t<Cell><Data ss:Type=\"String\">"+ansSent+"</Data></Cell>\n");
							out.write("\t\t<Cell><Data ss:Type=\"String\">"+sentID+"</Data></Cell>\n");
							out.write("\t</Row>\n");
							
							out.write("\t<Row>\n");
							out.write("\t\t<Cell><Data ss:Type=\"String\">"+question+"</Data></Cell>\n");
							out.write("\t\t<Cell><Data ss:Type=\"String\">"+ansPhrase+"</Data></Cell>\n");
							out.write("\t\t<Cell><Data ss:Type=\"String\">"+phraseID+"</Data></Cell>\n");
							out.write("\t</Row>\n");
							
						}
					}
				} catch (java.io.IOException e) {
					System.err.println(e);
				}
			}
			in.close();
			out.write("</Workbook>");
			out.close();
			
			int line = quesCounter*5+8;
			MsgPrinter.printStatusMsg("Summary (without y/n questions):");
			MsgPrinter.printStatusMsg("Paragraph: "+paragraphCounter
					+". Original Sentences: "+oriSentCounter
					+". Words: "+wordCounter
					+". Actual Sentences: "+ansSentHash.size()
					+". Answer phrases: "+ansPhraseHash.size()
					+". Questions: "+quesCounter
					+". XML lines: "+line+".");
		} catch (java.io.IOException e) {
			System.err.println(e);
		}
	}
	
	/**
	 * Asks Ephyra a factoid question and returns up to <code>maxAnswers</code>
	 * results that have a score of at least <code>absThresh</code>.
	 * 
	 * @param question factoid question
	 * @param maxAnswers maximum number of answers
	 * @param absThresh absolute threshold for scores
	 * @return array of results
	 */
	public Result[] askFactoid(String question, int maxAnswers,
							   float absThresh) {
		// initialize pipeline
		initFactoid();
		
		// analyze question
		MsgPrinter.printAnalyzingQuestion();
		AnalyzedQuestion aq = QuestionAnalysis.analyze(question);
		
		// get answers
		Result[] results = runPipeline(aq, maxAnswers, absThresh);
		
		return results;
	}
	
	/**
	 * Asks Ephyra a factoid question and returns a single result or
	 * <code>null</code> if no answer could be found.
	 * 
	 * @param question factoid question
	 * @return single result or <code>null</code>
	 */
	public Result askFactoid(String question) {
		Result[] results = askFactoid(question, 1, 0);
		
		return (results.length > 0) ? results[0] : null;
	}
	
	/**
	 * Asks Ephyra a list question and returns results that have a score of at
	 * least <code>relThresh * top score</code>.
	 * 
	 * @param question list question
	 * @param relThresh relative threshold for scores
	 * @return array of results
	 */
	public Result[] askList(String question, float relThresh) {
		question = QuestionNormalizer.transformList(question);
		
		Result[] results = askFactoid(question, Integer.MAX_VALUE, 0);
		
		// get results with a score of at least relThresh * top score
		ArrayList<Result> confident = new ArrayList<Result>();
		if (results.length > 0) {
			float topScore = results[0].getScore();
			
			for (Result result : results)
				if (result.getScore() >= relThresh * topScore)
					confident.add(result);
		}
		
		return confident.toArray(new Result[confident.size()]);
	}
}
