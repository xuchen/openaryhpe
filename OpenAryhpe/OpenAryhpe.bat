@echo off
rem - Runs OpenAryhpe in command line mode.

rem - The '-server' option of the Java VM improves the runtime of OpenAryhpe.
rem - We recommend using 'java -server' if your VM supports this option.

title OpenAryhpe

set CLASSPATH=bin;lib/search;lib/ml/maxent.jar;lib/ml/minorthird.jar;lib/nlp/jwnl.jar;lib/nlp/lingpipe.jar;lib/nlp/opennlp-tools.jar;lib/nlp/plingstemmer.jar;lib/nlp/snowball.jar;lib/nlp/stanford-ner.jar;lib/nlp/stanford-parser.jar;lib/nlp/stanford-postagger.jar;lib/qa/javelin.jar;lib/search/googleapi.jar;lib/search/indri.jar;lib/search/yahoosearch.jar;lib/util/commons-logging.jar;lib/util/htmlparser.jar;lib/util/log4j.jar;lib/util/trove.jar


java -Xms1024m -Xmx1500m info.ephyra.OpenAryhpe

