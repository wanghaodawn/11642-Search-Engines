/*
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 *  Version 3.1.1.
 */

import java.io.*;
import java.util.*;

import org.apache.lucene.analysis.Analyzer.TokenStreamComponents;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

/**
 * QryEval is a simple application that reads queries from a file,
 * evaluates them against an index, and writes the results to an
 * output file.  This class contains the main method, a method for
 * reading parameter and query files, initialization methods, a simple
 * query parser, a simple query processor, and methods for reporting
 * results.
 * <p>
 * This software illustrates the architecture for the portion of a
 * search engine that evaluates queries.  It is a guide for class
 * homework assignments, so it emphasizes simplicity over efficiency.
 * Everything could be done more efficiently and elegantly.
 * <p>
 * The {@link Qry} hierarchy implements query evaluation using a
 * 'document at a time' (DaaT) methodology.  Initially it contains an
 * #OR operator for the unranked Boolean retrieval model and a #SYN
 * (synonym) operator for any retrieval model.  It is easily extended
 * to support additional query operators and retrieval models.  See
 * the {@link Qry} class for details.
 * <p>
 * The {@link RetrievalModel} hierarchy stores parameters and
 * information required by different retrieval models.  Retrieval
 * models that need these parameters (e.g., BM25 and Indri) use them
 * very frequently, so the RetrievalModel class emphasizes fast access.
 * <p>
 * The {@link Idx} hierarchy provides access to information in the
 * Lucene index.  It is intended to be simpler than accessing the
 * Lucene index directly.
 * <p>
 * As the search engine becomes more complex, it becomes useful to
 * have a standard approach to representing documents and scores.
 * The {@link ScoreList} class provides this capability.
 */
public class QryEval {

  //  --------------- Constants and variables ---------------------

  private static final String USAGE =
    "Usage:  java QryEval paramFile\n\n";

  private static final EnglishAnalyzerConfigurable ANALYZER =
    new EnglishAnalyzerConfigurable(Version.LUCENE_43);
  private static final String[] TEXT_FIELDS =
    { "body", "title", "url", "inlink" };

  private static final String[] requiredParameters = {
    "indexPath", "retrievalAlgorithm", "queryFilePath", "trecEvalOutputPath"
  };

  private static Map<String, String> parameters = new HashMap<String, String>();


  //  --------------- Methods ---------------------------------------

  /**
   * @param args The only argument is the parameter file name.
   * @throws Exception Error accessing the Lucene index.
   */
  public static void main(String[] args) throws Exception {

    //  This is a timer that you may find useful.  It is used here to
    //  time how long the entire program takes, but you can move it
    //  around to time specific parts of your code.
    
    Timer timer = new Timer();
    timer.start ();

    //  Check that a parameter file is included, and that the required
    //  parameters are present.  Just store the parameters.  They get
    //  processed later during initialization of different system
    //  components.

    if (args.length < 1) {
      throw new IllegalArgumentException (USAGE);
    }

    // Read parameterFile
    File inFile = new File(args[0]);
    BufferedReader br = null;
    try {
      // Read string from the input file line by line
      String sCurrentLine;
      br = new BufferedReader(new FileReader(inFile));

      while ((sCurrentLine = br.readLine()) != null) {
        // System.out.println(sCurrentLine);
        sCurrentLine = sCurrentLine.trim();
        String[] line_array = sCurrentLine.split("=");

        // Invalid line
        if (line_array.length != 2) {
          continue;
        }

        // Duplicate lines saying the same thing
        if (parameters.containsKey(line_array[0])) {
          continue;
        }
        parameters.put(line_array[0], line_array[1]);
      }

      // Check whether the input is legal
      isValidParameterFile(parameters);

      // Configure query lexical processing to match index lexical
      // processing.  Initialize the index and retrieval model.
      ANALYZER.setLowercase(true);
      ANALYZER.setStopwordRemoval(true);
      ANALYZER.setStemmer(EnglishAnalyzerConfigurable.StemmerType.KSTEM);

      Idx.initialize(parameters.get ("indexPath"));
      RetrievalModel model = initializeRetrievalModel (parameters);

      // Perform experiments according to the given query file
      processQueryFile(parameters.get("queryFilePath"), model);

      // Clean up.
      timer.stop();
      System.out.println("Time:  " + timer);

    // Throws exception if no file is found
    } catch (IOException e) {
      // Throws IOException
      e.printStackTrace();
    }
  }


  /**
   * Judge whether a required item in parameterFile.txt is missing
   */
  public static void isValidParameterFile(Map<String, String> map) {
    
    // No correct input
    if (map == null || map.size() < 1) {
      System.out.println("Error: No Parameters Obtained in parameterFile.txt!!!");
      System.exit(-1);
    }

    // Missing input arguments
    if (map.size() != 4) {
      System.out.println("Error: Missing Parameters in parameterFile.txt!!!");
      System.exit(-1);
    }

    // Missing input arguments
    for (int i = 0; i < requiredParameters.length; i++) {
      if (!map.containsKey(requiredParameters[i])) {
        System.err.println("Error: Missing " + requiredParameters[i] + " in parameterFile.txt!!!");
        System.exit(-1);
      }
    }
  }


  /**
   * Allocate the retrieval model and initialize it using parameters
   * from the parameter file.
   * @return The initialized retrieval model
   * @throws IOException Error accessing the Lucene index.
   */
  private static RetrievalModel initializeRetrievalModel (Map<String, String> parameters)
    throws IOException {

    RetrievalModel model = null;
    String modelString = parameters.get ("retrievalAlgorithm").toLowerCase();

    // Get the type of model
    if (modelString.equals("unrankedboolean")) {
      model = new RetrievalModelUnrankedBoolean();
    } else if (modelString.equals("rankedboolean")) {
      model = new RetrievalModelRankedBoolean();
    } else {
      throw new IllegalArgumentException
        ("Unknown retrieval model " + parameters.get("retrievalAlgorithm"));
    }

    return model;
  }

  /**
   * Optimize the query by removing degenerate nodes produced during
   * query parsing, for example '#NEAR/1 (of the)' which turns into 
   * '#NEAR/1 ()' after stopwords are removed; and unnecessary nodes
   * or subtrees, such as #AND (#AND (a)), which can be replaced by 'a'.
   */
  static Qry optimizeQuery(Qry q) {

    //  Term operators don't benefit from optimization.
    if (q instanceof QryIopTerm) {
      return q;
    }

    //  Optimization is a depth-first task, so recurse on query
    //  arguments.  This is done in reverse to simplify deleting
    //  query arguments that become null.
    for (int i = q.args.size() - 1; i >= 0; i--) {

      Qry q_i_before = q.args.get(i);
      Qry q_i_after = optimizeQuery (q_i_before);

      if (q_i_after == null) {
        q.removeArg(i);			// optimization deleted the argument
      } else {
        if (q_i_before != q_i_after) {
          q.args.set (i, q_i_after);	// optimization changed the argument
        }
      }
    }

    //  If the operator now has no arguments, it is deleted.
    if (q.args.size () == 0) {
      return null;
    }

    //  Only SCORE operators can have a single argument.  Other
    //  query operators that have just one argument are deleted.
    if ((q.args.size() == 1) &&
        (! (q instanceof QrySopScore))) {
      q = q.args.get (0);
    }

    return q;

  }

  /**
   * Return a query tree that corresponds to the query.
   * 
   * @param qString
   *          A string containing a query.
   * @param qTree
   *          A query tree
   * @throws IOException Error accessing the Lucene index.
   */
  static Qry parseQuery(String qString, RetrievalModel model) throws IOException {

    //  Add a default query operator to every query. This is a tiny
    //  bit of inefficiency, but it allows other code to assume
    //  that the query will return document ids and scores.

    String defaultOp = model.defaultQrySopName();
    qString = defaultOp + "(" + qString + ")";

    //  Simple query tokenization.  Terms like "near-death" are handled later.

    StringTokenizer tokens = new StringTokenizer(qString, "\t\n\r ,()", true);
    String token = null;

    //  This is a simple, stack-based parser.  These variables record
    //  the parser's state.
    
    Qry currentOp = null;
    Stack<Qry> opStack = new Stack<Qry>();
    boolean weightExpected = false;
    Stack<Double> weightStack = new Stack<Double>();

    //  Each pass of the loop processes one token. The query operator
    //  on the top of the opStack is also stored in currentOp to
    //  make the code more readable.

    while (tokens.hasMoreTokens()) {

      token = tokens.nextToken();

      if (token.matches("[ ,(\t\n\r]")) {
        continue;
      } else if (token.equals(")")) {	// Finish current query op.

        // If the current query operator is not an argument to another
        // query operator (i.e., the opStack is empty when the current
        // query operator is removed), we're done (assuming correct
        // syntax - see below).

        opStack.pop();

        if (opStack.empty())
          break;

	      // Not done yet.  Add the current operator as an argument to
        // the higher-level operator, and shift processing back to the
        // higher-level operator.

        Qry arg = currentOp;
        currentOp = opStack.peek();
	      currentOp.appendArg(arg);

      } else if (token.equalsIgnoreCase("#and")) {
        currentOp = new QrySopAnd();
        currentOp.setDisplayName(token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#or")) {
        currentOp = new QrySopOr();
        currentOp.setDisplayName(token);
        opStack.push(currentOp);
      } else if (token.equalsIgnoreCase("#syn")) {
        currentOp = new QryIopSyn();
        currentOp.setDisplayName(token);
        opStack.push(currentOp);
      } else if (token.length() >= 7 && token.substring(0, 6).equalsIgnoreCase("#near/")) {
        int dis = Integer.parseInt(token.substring(6));
        currentOp = new QryIopNear(dis);
        currentOp.setDisplayName(token);
        opStack.push(currentOp);
      } else {

        //  Split the token into a term and a field.

        int delimiter = token.indexOf('.');
        String field = null;
        String term = null;

        if (delimiter < 0) {
          field = "body";
          term = token;
        } else {
          field = token.substring(delimiter + 1).toLowerCase();
          term = token.substring(0, delimiter);
        }

        if ((field.compareTo("url") != 0) &&
      	    (field.compareTo("keywords") != 0) &&
      	    (field.compareTo("title") != 0) &&
      	    (field.compareTo("body") != 0) &&
            (field.compareTo("inlink") != 0)) {
          throw new IllegalArgumentException ("Error: Unknown field " + token);
        }

        //  Lexical processing, stopwords, stemming.  A loop is used
        //  just in case a term (e.g., "near-death") gets tokenized into
        //  multiple terms (e.g., "near" and "death").

        String t[] = tokenizeQuery(term);

        for (int j = 0; j < t.length; j++) {

          Qry termOp = new QryIopTerm(t [j], field);

      	  currentOp.appendArg (termOp);
      	}
      }
    }

    //  A broken structured query can leave unprocessed tokens on the opStack,
    if (tokens.hasMoreTokens()) {
      throw new IllegalArgumentException
        ("Error:  Query syntax is incorrect.  " + qString);
    }

    return currentOp;
  }

  /**
   * Print a message indicating the amount of memory used. The caller
   * can indicate whether garbage collection should be performed,
   * which slows the program but reduces memory usage.
   * 
   * @param gc
   *          If true, run the garbage collector before reporting.
   */
  public static void printMemoryUsage(boolean gc) {

    Runtime runtime = Runtime.getRuntime();

    if (gc)
      runtime.gc();

    System.out.println("Memory used:  "
        + ((runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)) + " MB");
  }

  /**
   * Process one query.
   * @param qString A string that contains a query.
   * @param model The retrieval model determines how matching and scoring is done.
   * @return Search results
   * @throws IOException Error accessing the index
   */
  static ScoreList processQuery(String qString, RetrievalModel model)
    throws IOException {

    Qry q = parseQuery(qString, model);
    q = optimizeQuery (q);

    // Show the query that is evaluated

    System.out.println("    --> " + q);
    
    if (q != null) {

      ScoreList r = new ScoreList ();
      
      if (q.args.size () > 0) {		// Ignore empty queries

        q.initialize (model);

        while (q.docIteratorHasMatch (model)) {
          int docid = q.docIteratorGetMatch ();
          double score = ((QrySop) q).getScore (model);
          r.add (docid, score);
          q.docIteratorAdvancePast (docid);
        }
      }

      return r;
    } else
      return null;
  }

  /**
   * Process the query file.
   * @param queryFilePath
   * @param model
   * @throws IOException Error accessing the Lucene index.
   */
  static void processQueryFile(String queryFilePath,
                               RetrievalModel model) throws IOException {
    
    BufferedReader input = null;

    try {

      // Get the output file path
      File output_file = new File(parameters.get(requiredParameters[3]));

      // If file doesnt exists, then create it
      if (!output_file.exists()) {
        output_file.createNewFile();
      }

      FileWriter fw = new FileWriter(output_file.getAbsoluteFile());
      BufferedWriter bw = new BufferedWriter(fw);

      String qLine = null;

      input = new BufferedReader(new FileReader(queryFilePath));

      //  Each pass of the loop processes one query.

      while ((qLine = input.readLine()) != null) {
        int d = qLine.indexOf(':');

        if (d < 0) {
          throw new IllegalArgumentException
            ("Syntax error:  Missing ':' in query line.");
        }

        printMemoryUsage(false);

        String qid = qLine.substring(0, d);
        String query = qLine.substring(d + 1);

        System.out.println("Query " + qLine);

        ScoreList r = null;

        r = processQuery(query, model);

        if (r != null) {
          // If there are input
          int len = r.size();
          r.sort();

          // printResults(qid, r);
          // System.out.println();

          for (int i = 0; i < 100 && i < len; i++) {
            if (r.getScoreListEntry(i).docid <= 0 || r.getScoreListEntry(i).externalId == "") {
              continue;
            }
            String tmp = qid + " " + "Q0 " + r.getScoreListEntry(i).externalId + " " + (i+1)
                     + " " + r.getScoreListEntry(i).score + " run-1\n";
            bw.write(tmp);
          }
        } else {
          // If the input is null
          String tmp = "10 Q0 dummy 1 0 run-1";
          bw.write(tmp);
        }
      }
      bw.close();
    } catch (IOException ex) {
      ex.printStackTrace();
    } finally {
      input.close();
    }
  }

  /**
   * Print the query results.
   * 
   * THIS IS NOT THE CORRECT OUTPUT FORMAT. YOU MUST CHANGE THIS METHOD SO
   * THAT IT OUTPUTS IN THE FORMAT SPECIFIED IN THE HOMEWORK PAGE, WHICH IS:
   * 
   * QueryID Q0 DocID Rank Score RunID
   * 
   * @param queryName
   *          Original query.
   * @param result
   *          A list of document ids and scores
   * @throws IOException Error accessing the Lucene index.
   */
  static void printResults(String queryName, ScoreList result) 
  throws IOException {
    System.out.println(queryName + ":  ");
    if (result.size() < 1) {
      System.out.println("\tNo results.");
    } else {
      // for (int i = 0; i < result.size(); i++) {
      //   System.out.println("\t" + i + ":  " + ", "
      //       + result.getDocidScore(i));
      // }
    }
  }

  /**
   * Read the specified parameter file, and confirm that the required
   * parameters are present.  The parameters are returned in a
   * HashMap.  The caller (or its minions) are responsible for
   * processing them.
   * @return The parameters, in <key, value> format.
   */
  private static Map<String, String> readParameterFile (String parameterFileName)
    throws IOException {

    Map<String, String> parameters = new HashMap<String, String>();

    File parameterFile = new File (parameterFileName);

    if (! parameterFile.canRead ()) {
      throw new IllegalArgumentException
        ("Can't read " + parameterFileName);
    }

    Scanner scan = new Scanner(parameterFile);
    String line = null;
    do {
      line = scan.nextLine();
      String[] pair = line.split ("=");
      parameters.put(pair[0].trim(), pair[1].trim());
    } while (scan.hasNext());

    scan.close();

    if (! (parameters.containsKey ("indexPath") &&
           parameters.containsKey ("queryFilePath") &&
           parameters.containsKey ("trecEvalOutputPath") &&
           parameters.containsKey ("retrievalAlgorithm"))) {
      throw new IllegalArgumentException
        ("Required parameters were missing from the parameter file.");
    }

    return parameters;
  }

  /**
   * Given a query string, returns the terms one at a time with stopwords
   * removed and the terms stemmed using the Krovetz stemmer.
   * 
   * Use this method to process raw query terms.
   * 
   * @param query
   *          String containing query
   * @return Array of query tokens
   * @throws IOException Error accessing the Lucene index.
   */
  static String[] tokenizeQuery(String query) throws IOException {

    TokenStreamComponents comp =
      ANALYZER.createComponents("dummy", new StringReader(query));
    TokenStream tokenStream = comp.getTokenStream();

    CharTermAttribute charTermAttribute =
      tokenStream.addAttribute(CharTermAttribute.class);
    tokenStream.reset();

    List<String> tokens = new ArrayList<String>();

    while (tokenStream.incrementToken()) {
      String term = charTermAttribute.toString();
      tokens.add(term);
    }

    return tokens.toArray (new String[tokens.size()]);
  }

}
