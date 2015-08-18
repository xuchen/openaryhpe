OpenAryhpe is an implementation of Michael Heilman's [question generation system](http://www.ark.cs.cmu.edu/mheilman/questions/) described in the following paper:

M. Heilman and N. A. Smith. 2009. Question Generation via Overgenerating Transformations and Ranking. Language Technologies Institute, Carnegie Mellon University Technical Report CMU-LTI-09-013. [PDF](http://www.cs.cmu.edu/~mheilman/papers/heilman-smith-qg-tech-report.pdf)

Currently it still lacks a question ranking module but all syntactic marking and transformation rules have been implemented.

OpenAryhpe is written in Java and can run on Linux/Mac/Windows (needs 1~1.5 GB RAM). It is based on [Nico Schlaefer](http://www.cs.cmu.edu/~nico/)'s [OpenEphyra](http://ephyra.info/), a question answering framework.

To compile, simple type "ant".

To run, execute OpenAryhpe.sh (Linux&Mac, must chmod to executable first) or OpenAryhpe.bat (Windows).