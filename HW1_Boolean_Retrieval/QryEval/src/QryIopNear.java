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

    if (args.size () == 0) {
      return;
    }

    //  Each pass of the loop adds 1 document to result inverted list
    //  until all of the argument inverted lists are depleted.
    while (true) {

      //  Find the minimum next document id.  If there is none, we're done.
      int minDocid = Qry.INVALID_DOCID;
      boolean matchDoc = true;

      for (Qry q_i: this.args) {
        if (q_i.docIteratorHasMatch(null)) {
          int q_iDocid = q_i.docIteratorGetMatch();
          
          if ((minDocid > q_iDocid) || (minDocid == Qry.INVALID_DOCID)) {
            minDocid = q_iDocid;
          }
        }
      }

      if (minDocid == Qry.INVALID_DOCID)
        break;        // All docids have been processed.  Done.
      
      for (Qry q_i: this.args) {
        if (q_i.docIteratorHasMatch(null)) {
          continue;
        }
        if ((q_i.docIteratorGetMatch() == minDocid)) {
          matchDoc = false;
          break;
        }
      }

      if (!matchDoc) {
        for (Qry q_i: this.args) {
          if (!q_i.docIteratorHasMatch(null)) {
            continue;
          }
          if ((q_i.docIteratorGetMatch() == minDocid)) {
            q_i.docIteratorAdvancePast(minDocid);
          }
        }
        continue;
      }

      //  Create a new posting that is the union of the posting lists
      //  that match the minDocid.  Save it.
      List<Integer> positions = new ArrayList<Integer>();

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
