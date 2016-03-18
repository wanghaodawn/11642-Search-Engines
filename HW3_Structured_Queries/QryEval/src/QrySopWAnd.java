/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The WAnd operator for Indri retrieval model.
 */
public class QrySopWAnd extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    if (r instanceof RetrievalModelIndri) {
      return this.docIteratorHasMatchMin (r);
    }
    throw new IllegalArgumentException
        (r.getClass().getName() + " WSum can only support Indri!");
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelIndri) {
      return this.getScoreInddri(r);
    }
    throw new IllegalArgumentException
        (r.getClass().getName() + " WSum can only support Indri!");
  }

  /**
   *  getScore for the Indri retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreIndri (RetrievalModel r) throws IOException {
    if (!this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      double max_score = -1.0;
      for (int i = 0; i < this.args.size(); i++) {
        Qry q = this.args.get(i);
        if (!q.docIteratorHasMatch(r)){
          continue;
        }
        if (q.docIteratorGetMatch() == this.docIteratorGetMatch()) {
          double score = ((QrySop) q).getScore(r);
          max_score = Math.max(max_score, score);
        }
      }
      return max_score;
    }
  }

  /**
   *  Get a score for the document when nothing matched in Indri Model
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getDefaultScoreIndri(RetrievalModel r, int doc_id) throws IOException {
    return 0.0;
  }
}
