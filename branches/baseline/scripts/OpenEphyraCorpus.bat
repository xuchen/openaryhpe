@echo off
rem Usage: OpenEphyraCorpus.sh index_dir [assert_dir]

rem export CLASSPATH=bin:lib/ml/maxent.jar:lib/ml/minorthird.jar:lib/nlp/jwnl.jar:lib/nlp/lingpipe.jar:lib/nlp/opennlp-tools.jar:lib/nlp/plingstemmer.jar:lib/nlp/snowball.jar:lib/nlp/stanford-ner.jar:lib/nlp/stanford-parser.jar:lib/nlp/stanford-postagger.jar:lib/qa/javelin.jar:lib/search/googleapi.jar:lib/search/indri.jar:lib/search/yahoosearch.jar:lib/util/commons-logging.jar:lib/util/htmlparser.jar:lib/util/log4j.jar:lib/util/trove.jar

title OpenEphyra

set CLASSPATH=bin;lib/search;lib/ml/maxent.jar;lib/ml/minorthird.jar;lib/nlp/jwnl.jar;lib/nlp/lingpipe.jar;lib/nlp/opennlp-tools.jar;lib/nlp/plingstemmer.jar;lib/nlp/snowball.jar;lib/nlp/stanford-ner.jar;lib/nlp/stanford-parser.jar;lib/nlp/stanford-postagger.jar;lib/qa/javelin.jar;lib/search/googleapi.jar;lib/search/indri.jar;lib/search/yahoosearch.jar;lib/util/commons-logging.jar;lib/util/htmlparser.jar;lib/util/log4j.jar;lib/util/trove.jar

set INDRI_INDEX=D:\\Xuchen\\Indri\\Indri 2.6\\lib\\US

rem export ASSERT=$2

cd ..

java -Djava.library.path=lib/search -Xms1000m -Xmx1400m -Djava.library.path=lib/search/ info.ephyra.trec.OpenEphyraCorpus


cd scripts
