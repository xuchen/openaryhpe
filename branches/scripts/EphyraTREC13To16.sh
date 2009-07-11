#!/bin/bash

# Runs Ephyra on the TREC 13-16 questions.
# Usage: EphyraTREC13To16.sh {trec13, trec14, trec15, trec16} unique_ID flags
#                            index_dir [assert_dir]

# The '-server' option of the Java VM improves the runtime of Ephyra.
# We recommend using 'java -server' if your VM supports this option.

export CLASSPATH=bin:lib/math/Jama-1.0.2.jar:lib/ml/maxent.jar:lib/ml/minorthird.jar:lib/nlp/adb-1.0.jar:lib/nlp/jwnl.jar:lib/nlp/kantoo.jar:lib/nlp/lingpipe.jar:lib/nlp/opennlp-tools.jar:lib/nlp/plingstemmer.jar:lib/nlp/simmetrics_jar_v1_3_d25_07_05.jar:lib/nlp/snowball.jar:lib/nlp/stanford-ner.jar:lib/nlp/stanford-parser.jar:lib/nlp/stanford-postagger.jar:lib/nlp/ta-1.0.jar:lib/qa/javelin.jar:lib/search/globalweather.jar:lib/search/googleapi.jar:lib/search/indri.jar:lib/search/yahoosearch.jar:lib/uima/uima-core.jar:lib/uima/uima-cpe.jar:lib/uima/uima-document-annotation.jar:lib/uima/uima-tools.jar:lib/util/commons-logging.jar:lib/util/htmlparser.jar:lib/util/log4j.jar:lib/util/mysql-connector-java-5.0.3-bin.jar:lib/util/resolver.jar:lib/util/trove.jar:lib/util/xercesImpl.jar:lib/util/xercesSamples.jar:lib/util/xml-apis.jar:lib/webservices/activation.jar:lib/webservices/axis.jar:lib/webservices/commons-discovery-0.2.jar:lib/webservices/jaxrpc.jar:lib/webservices/mail.jar:lib/webservices/saaj.jar:lib/webservices/wsdl4j-1.5.1.jar
export INDRI_INDEX=$4
export ASSERT=$5

cd ..

java -server -Xms1000m -Xmx1400m -Djava.library.path=lib/search/ \
     info.ephyra.trec.EphyraTREC13To16 res/testdata/trec/$1questions.xml \
     tag=$1_$2_out log=log/$1_$2 lflags=$3 \
     fp=res/testdata/trec/$1patterns_factoid \
     lp=res/testdata/trec/$1patterns_list
