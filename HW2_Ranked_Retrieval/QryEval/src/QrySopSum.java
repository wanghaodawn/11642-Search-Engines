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
    // if (r instanceof RetrievalModelUnrankedBoolean) {
    //   return this.docIteratorHasMatchAll(r);
    // } else if (r instanceof RetrievalModelRankedBoolean) {
    //   return this.docIteratorHasMatchAll(r);
    // } else {
      return this.docIteratorHasMatchMin(r);
    // }
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
    for (Qry q : this.args) {
      if (q.docIteratorHasMatch(r) && q.docIteratorGetMatch() == matchDoc) {
        score += ((QrySop) q).getScore(r);
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
  
  // /**
  //  *  getScore for the UnrankedBoolean retrieval model.
  //  *  @param r The retrieval model that determines how scores are calculated.
  //  *  @return The document score.
  //  *  @throws IOException Error accessing the Lucene index
  //  */
  // private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
  //   if (! this.docIteratorHasMatchCache()) {
  //     return 0.0;
  //   } else {
  //     return 1.0;
  //   }
  // }

  // /**
  //  *  getScore for the RankedBoolean retrieval model.
  //  *  @param r The retrieval model that determines how scores are calculated.
  //  *  @return The document score.
  //  *  @throws IOException Error accessing the Lucene index
  //  */
  // private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
  //   if (! this.docIteratorHasMatchCache()) {
  //     return 0.0;
  //   } else {
  //     double min_score = Double.MAX_VALUE;
  //     int docid = this.docIteratorGetMatch();
  //     for (int i = 0; i < this.args.size(); i++) {
  //       Qry q = this.args.get(i);
  //       if(!q.docIteratorHasMatch(r) || docid != q.docIteratorGetMatch()){
  //         return 0.0;
  //       }
  //       double score = ((QrySop) q).getScore(r);
  //       min_score = Math.min(min_score, score);
  //     }
  //     return min_score;
  //   }
  // }

  // /**
  //  *  getScore for the Indri retrieval model.
  //  *  @param r The retrieval model that determines how scores are calculated.
  //  *  @return The document score.
  //  *  @throws IOException Error accessing the Lucene index
  //  */
  // private double getScoreIndri(RetrievalModel r) throws IOException {
  //   return 0.0;
  // }

}
