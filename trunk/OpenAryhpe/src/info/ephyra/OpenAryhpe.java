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
import info.ephyra.treeansweranalysis.TreeCompressor;
import info.ephyra.treeansweranalysis.UnmovableTreeMarker;
import info.ephyra.treequestiongeneration.TreeQuestionGenerator;
import info.ephyra.treequestiongeneration.VerbDecomposer;

import java.util.ArrayList;
import java.util.Iterator;

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
		
		// load query reformulators
		MsgPrinter.printStatusMsg("Loading query reformulators...");
		if (!QuestionReformulationG.loadReformulators(dir +
				"res/reformulations/"))
			MsgPrinter.printErrorMsg("Could not load query reformulators.");
		
		// load answer types
//		MsgPrinter.printStatusMsg("Loading answer types...");
//		if (!AnswerTypeTester.loadAnswerTypes(dir +
//				"res/answertypes/patterns/answertypepatterns"))
//			MsgPrinter.printErrorMsg("Could not load answer types.");
		
		// load question patterns
		MsgPrinter.printStatusMsg("Loading question patterns...");
		if (!QuestionGenerator.loadPatterns(dir +
				"res/patternlearning/questionpatternsTest/"))
			MsgPrinter.printErrorMsg("Could not load question patterns.");
		
//		MsgPrinter.printStatusMsg("Loading question patterns...");
//		if (!QuestionInterpreter.loadPatterns(dir +
//				"res/patternlearning/questionpatternsTest/"))
//			MsgPrinter.printErrorMsg("Could not load question patterns.");		
		
		// load answer patterns
		MsgPrinter.printStatusMsg("Loading answer patterns...");
		if (!AnswerAnalyzer.loadPatterns(dir +
				"res/patternlearning/answerpatternsTest/"))
			MsgPrinter.printErrorMsg("Could not load answer patterns.");
		
		// load Tregex patterns for unmovable phrases 
		MsgPrinter.printStatusMsg("Loading Tregex patterns for unmovable phrases...");
		if (!UnmovableTreeMarker.loadUnmvRegex("res/nlp/treetransform/unmovable"))
			MsgPrinter.printErrorMsg("Could not Tregex patterns for unmovable phrases.");
		
		// Initialize TreeCompressor
		MsgPrinter.printStatusMsg("Initialize VerbDecomposer...");
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
			if (question.startsWith("LIST:")) {
				question = question.substring(5).trim();
				Logger.logListStart(question);
				results = askList(question, LIST_REL_THRESH);
				Logger.logResults(results);
				Logger.logListEnd();
			} else {
				//Logger.logFactoidStart(question);
//				results = askFactoid(question, FACTOID_MAX_ANSWERS,
//									 FACTOID_ABS_THRESH);
				//AnalyzedQuestion aq = QuestionAnalysis.analyze(question);
				TreeAnswers answers = new TreeAnswers(question);
				ArrayList<TreeAnswer> treeAnsList = TreeAnswerAnalyzer.analyze(answers);
				Iterator<TreeAnswer> tAnsIter = treeAnsList.iterator();
				while (tAnsIter.hasNext()) {
					TreeAnswer treeAnswer = tAnsIter.next();
					TreeQuestionGenerator.generate(treeAnswer);
				}
				TreeQuestionGenerator.print(treeAnsList);
				//ArrayList<Answer> ansList = AnswerAnalyzer.analyze(answers);
				//ArrayList<QuestionAnswerPair> qaPairList = QuestionGenerator.makeQApairs(ansList);
				//ArrayList<QuestionAnswerPair> lessList = QuestionGenerator.shrinkByTo(qaPairList);
				//ArrayList<QAPair> qaPairList = QuestionGenerator.makeQApair(ansList);
				//QuestionGenerator.printQAlist(qaPairList);
				//QuestionGenerator.generate(ansList);
				//Logger.logResults(results);
				//Logger.logFactoidEnd();
			}
			
			// print answers
			//MsgPrinter.printAnswers(results);
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
