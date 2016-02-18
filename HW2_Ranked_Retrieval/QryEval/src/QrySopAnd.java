/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The And operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.docIteratorHasMatchAll(r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return this.docIteratorHasMatchAll(r);
    } else {
      return this.docIteratorHasMatchMin(r);
    }
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {

    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean(r);
    } else if (r instanceof RetrievalModelRankedBoolean) {
      return this.getScoreRankedBoolean(r);
    } else if (r instanceof RetrievalModelIndri) {
      return getScoreIndri(r);
    } else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the AND operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

  /**
   *  getScore for the RankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      double min_score = Double.MAX_VALUE;
      int docid = this.docIteratorGetMatch();
      for (int i = 0; i < this.args.size(); i++) {
        Qry q = this.args.get(i);
        if(!q.docIteratorHasMatch(r) || docid != q.docIteratorGetMatch()){
          return 0.0;
        }
        double score = ((QrySop) q).getScore(r);
        min_score = Math.min(min_score, score);
      }
      return min_score;
    }
  }

  /**
   *  getScore for the Indri retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreIndri(RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      int docid = this.docIteratorGetMatch();
      double score = 1.0;
      for (int i = 0; i < this.args.size(); i++) {
        Qry q = this.args.get(i);
        double temp = 1.0;
        if(!q.docIteratorHasMatch(r) || docid != q.docIteratorGetMatch()){
          double temp = ((QrySop) q).getScore(r);
          if (temp == 0.0) {
            continue;
          }
        } else {
          temp = ((QrySop) q).getScore(r);
        }
        score *= temp;
      }
      return Math.pow(score, 1.0 / this.args.size());
    }
  }
}
