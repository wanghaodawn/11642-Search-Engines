/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

/**
 *  The TERM operator for all retrieval models.  The TERM operator stores
 *  information about a query term, for example "apple" in the query
 *  "#AND (apple pie).  Although it may seem odd to use a query
 *  operator to store a term, doing so makes it easy to build
 *  structured queries with nested query operators.
 *
 */
public class QryIopNear extends QryIop {

  private int distance;

  /**
   *  The term is assumed to match the body field.
   *  @param termString A term string.
   */
  public QryIopNear(int distance) {
    this.distance = distance;
    this.field = "body";	// Default field if none is specified.
  }

  /**
   *  The term matches in the specified field.
   *  @param termString A term string.
   *  @param fieldString A field string.
   */
  public QryIopNear(String termString, String fieldString) {
    this.distance = distance;
    this.field = fieldString;
  }

  /**
   *  Evaluate the query operator; the result is an internal inverted
   *  list that may be accessed via the internal iterators.
   *  @throws IOException Error accessing the Lucene index.
   */
  protected void evaluate () throws IOException {
    
    //  Create an empty inverted list.  If there are no query arguments,
    //  that's the final result.
    this.invertedList = new InvList (this.getField());

    if (args.size () <= 1) {
      return;
    }

    //  Each pass of the loop adds 1 document to result inverted list
    //  until all of the argument inverted lists are depleted.
    while (true) {

      //  Find the minimum next document id.  If there is none, we're done.
      int minDocid = Integer.MAX_VALUE;
      boolean matchDoc = true;
      int min_location = Integer.MAX_VALUE;
      QryIop min_qry = null;
      boolean flag = true;

      // Create a new posting that is the union of the posting lists
      // that match the minDocid.  Save it.
      List<Integer> positions = new ArrayList<Integer>();

      for (Qry q : this.args) {
        if (q.docIteratorHasMatch(null)) {
          int q_docid = q.docIteratorGetMatch();
          
          minDocid = Math.min(minDocid, q_docid);
        }
      }

      // All docids have been processed.  Done.
      if (minDocid == Integer.MAX_VALUE) {
        break;
      }

      for (Qry q : this.args) {
        if ((q.docIteratorHasMatch(null) && (q.docIteratorGetMatch() == minDocid))) {
          continue;
        }
        matchDoc = false;
      }

      if (!matchDoc) {
        for (Qry q : this.args) {
          if (!q.docIteratorHasMatch(null) || !(q.docIteratorGetMatch() == minDocid)) {
            continue;
          }
          q.docIteratorAdvancePast(minDocid);
        }
        continue;
      }

      QryIop first_q = this.getArg(0);
      for (Qry q : this.args) {
        QryIop query = (QryIop) q;
        if (query == this.getArg(0)) {
          continue;
        }
        query.locIteratorAdvancePast(first_q.locIteratorGetMatch());
      }
      
      while (flag) {
        
        min_location = Integer.MAX_VALUE;
        min_qry = null;

        for (Qry q : this.args) {
          QryIop query = (QryIop) q;
          if (!query.locIteratorHasMatch()) {
            flag = false;
            break;
          }
          min_location = Math.min(min_location, query.locIteratorGetMatch());
          if (min_location == query.locIteratorGetMatch()) {
            min_qry = query;
          }
        }

        if (!flag) {
          continue;
        }

        int first_location = first_q.locIteratorGetMatch();
        for (int i = 0; i < this.args.size()-1; i++) {
          int pos_2 = this.getArg(i+1).locIteratorGetMatch();
          int pos_1 = this.getArg(i).locIteratorGetMatch();

          if (pos_1 < pos_2 - distance || pos_1 > pos_2) {
            min_qry.locIteratorAdvancePast(min_location);
            flag = false;
            break;
          }
        }

        if (flag) {
          positions.add(first_location);
          for (Qry q : this.args) {
            QryIop query = (QryIop) q;
            query.locIteratorAdvance();
          }
          continue;
        }
        min_qry.locIteratorAdvancePast(min_location);
        flag = true;
      }


      if (positions.size() > 0) {
        this.invertedList.appendPosting(minDocid, positions);
      }

      for (Qry q : this.args) {
        QryIop query = (QryIop) q;
        query.docIteratorAdvancePast(minDocid);
      }
      
    }
  }

  /**
   *  Get a string version of this query operator.  
   *  @return The string version of this query operator.
   */
  public String toString() {
    return ("" + this.distance + "." + this.field);
  }
}
