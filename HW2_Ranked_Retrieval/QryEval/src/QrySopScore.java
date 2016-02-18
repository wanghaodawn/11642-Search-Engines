/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

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
  private double getScoreBM25(RetrievalModel r) throws IOException {
    if (this.docIteratorHasMatchCache()) {

      // Get input variables
      double b  = ((RetrievalModelBM25) r).b;
      double k1 = ((RetrievalModelBM25) r).k1;
      double k3 = ((RetrievalModelBM25) r).k3;

      // Get other variables
      double N = Idx.getNumDocs();
      QryIop query = this.getArg(0);
      double tf = q.docIteratorGetMatchPosting().tf;
      double df = query.getDf();
      double doc_len_avg = Idx.getSumOfFieldLengths(query.getField());
      double doc_len = Idx.getFieldLength(query.getField(), this.docIteratorGetMatch());

      double rsj = 1.0 * (N - df + 0.5) / (df + 0.5);
      if (rsj < 1.0) {
        // Log will be minus
        rsj = 0.0;
      } else {
        double rsj = Math.log(rsj);
      }

      double tf_weight = 1.0 * tf / (tf + k1 * (1 - b + b / avg_doc_len * doc_len));
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
  private double getScoreIndri(RetrievalModel r) throws IOException {
    if (this.docIteratorHasMatchCache()) {

      // Get input variables
      double mu = ((RetrievalModelIndri) r).mu;
      double lambda = ((RetrievalModelIndri) r).lambda;

      // Get other variables
      QryIop query = this.getArg(0);
      double ctf = arg.getCtf();
      double doc_len_avg = Idx.getSumOfFieldLengths(query.getField());
      double mle = ctf / doc_len_avg;
      double tf = q.docIteratorGetMatchPosting().tf;
      double doc_len = Idx.getFieldLength(query.getField(), this.docIteratorGetMatch());

      return (1.0 - lambda) * (tf + mu * mle) / (doc_len + mu) + mle * lambda;
    }
    return 0.0;
  }

  /**
   *  Get a score for the document when nothing matched in Indri Model
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getErrorScoreIndri(RetrievalModel r, int doc_id) throws IOException {
    // Get input variables
    double mu = ((RetrievalModelIndri) r).mu;
    double lambda = ((RetrievalModelIndri) r).lambda;

    // Get other variables
    QryIop query = this.getArg(0);
    double doc_len_avg = Idx.getSumOfFieldLengths(query.getField());
    double doc_len = Idx.getFieldLength(query.getField(), doc_id);
    double mle = ctf / doc_len_avg;

    return (1.0 - lambda) * mu * mle / (doc_len + mu) + mle * lambda;

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
    q.initialize (r);
  }

}
