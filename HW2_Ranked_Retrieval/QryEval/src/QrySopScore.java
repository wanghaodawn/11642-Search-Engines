/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  // Global Variables
  // Parameters for BM25
  private double b  = -1.0;
  private double k1 = -1.0;
  private double k3 = -1.0;

  // Parameters for Indri
  private double mu = -1.0;
  private double lambda = 1.0;

  // Parameters that needn't be computed for multiple times
  private double df;
  private double ctf;
  private double mle;
  private double N;
  private double doc_count;
  private double doc_len_all;
  private double doc_len_avg;
  private String field;

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    // Get the type of retrieval model
    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean(r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return this.getScoreRankedBoolean(r);
    } else if (r instanceof RetrievalModelIndri) {
      return this.getScoreIndri(r);
    } else if (r instanceof RetrievalModelBM25) {
      return this.getScoreBM25(r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

  /**
   *  getScore for the Ranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return this.getArg(0).docIteratorGetMatchPosting().tf;
    }
  }

  /**
   *  getScore for the BM25 retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreBM25 (RetrievalModel r) throws IOException {

    if (this.docIteratorHasMatchCache()) {

      // Get input parameters
      if (b == -1.0 || k1 == -1.0 || k3 == -1.0) {
        b  = ((RetrievalModelBM25) r).b;
        k1 = ((RetrievalModelBM25) r).k1;
        k3 = ((RetrievalModelBM25) r).k3;
      }

      int doc_id = this.docIteratorGetMatch();
      double doc_len = Idx.getFieldLength(field, doc_id);
      double tf = this.getArg(0).docIteratorGetMatchPosting().tf;

      double rsj = 1.0 * (N - df + 0.5) / (df + 0.5);
      if (rsj < 1.0) {
        // Prevent minus value
        rsj = 0.0;
      } else {
        rsj = Math.log(rsj);
      }
      double tf_weight = 1.0 * tf / (tf + k1 * (1.0 - b + b * doc_len / doc_len_avg));
      double user_weight = 1.0;

      return rsj * tf_weight * user_weight;
    }
    return 0.0;
  }

  /**
   *  getScore for the Indri retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreIndri (RetrievalModel r) throws IOException {

    if (this.docIteratorHasMatchCache()) {

      // Get input parameters
      if (mu == -1.0 || lambda == -1.0) {
        mu = ((RetrievalModelIndri) r).mu;
        lambda = ((RetrievalModelIndri) r).lambda;
      }

      int doc_id = this.docIteratorGetMatch();
      double doc_len = Idx.getFieldLength(field, doc_id);
      double tf = this.getArg(0).docIteratorGetMatchPosting().tf;

      return 1.0 * (1.0 - lambda) * (tf + mu * mle) / (doc_len + mu) + mle * lambda;
    }
    return 0.0;
  }

  /**
   *  Get a score for the document when nothing matched in Indri Model
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getErrorScoreIndri (RetrievalModel r, int doc_id) throws IOException {

    // Get input parameters
    if (mu == -1.0 || lambda == -1.0) {
      mu = ((RetrievalModelIndri) r).mu;
      lambda = ((RetrievalModelIndri) r).lambda;
    }

    double doc_len = Idx.getFieldLength(field, doc_id);

    return 1.0 * (1.0 - lambda) * mle * mu / (doc_len + mu) + mle * lambda;
  }

  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {

    Qry q = this.args.get(0);
    q.initialize(r);

    // Get Global Parameters
    QryIop query = this.getArg(0);
    field = query.getField();

    ctf = query.getCtf();
    df = query.getDf();
    N = Idx.getNumDocs();
    doc_len_all = Idx.getSumOfFieldLengths(field);
    mle = ctf / doc_len_all;
    doc_count = 1.0 + Idx.getDocCount(field);
    doc_len_avg = doc_len_all / doc_count;
  }
}
