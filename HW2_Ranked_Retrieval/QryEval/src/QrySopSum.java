/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The Sum operator for BM25 retrieval models.
 */
public class QrySopSum extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin(r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    int matchDoc = Integer.MAX_VALUE;
    matchDoc = this.docIteratorGetMatch();

    if (matchDoc == Integer.MAX_VALUE) {
      return 0.0;
    }

    double score = 0.0;
    for (Qry query : this.args) {
      if (query.docIteratorHasMatch(r) && query.docIteratorGetMatch() == matchDoc) {
        score += ((QrySop) query).getScore(r);
      }
    }
    return score;
  }

  /**
   *  Get a score for the document when nothing matched in Indri Model
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getErrorScoreIndri(RetrievalModel r, int doc_id) throws IOException {
    return 0.0;
  }
}
