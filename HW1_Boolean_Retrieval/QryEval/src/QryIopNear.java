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

      for (int i = 0; i < this.args.size(); i++) {
        if (this.getArg(i).docIteratorHasMatch(null)) {
          int q_i_docid = this.getArg(i).docIteratorGetMatch();
          
          minDocid = Math.min(minDocid, q_i_docid);
        }
      }

      // All docids have been processed.  Done.
      if (minDocid == Integer.MAX_VALUE) {
        break;
      }
      
      for (int i = 0; i < this.args.size(); i++) {
        if (this.getArg(i).docIteratorHasMatch(null)) {
          continue;
        }
        if ((this.getArg(i).docIteratorGetMatch() == minDocid)) {
          matchDoc = false;
          break;
        }
      }

      if (!matchDoc) {
        for (int i = 0; i < this.args.size(); i++) {
          if (!this.getArg(i).docIteratorHasMatch(null)) {
            continue;
          }
          if ((this.getArg(i).docIteratorGetMatch() == minDocid)) {
            this.getArg(i).docIteratorAdvancePast(minDocid);
          }
        }
        continue;
      }

      //  Create a new posting that is the union of the posting lists
      //  that match the minDocid.  Save it.
      List<Integer> positions = new ArrayList<Integer>();

      QryIop first = this.getArg(0);
      for (int i = 1; i < this.args.size(); i++) {
        this.getArg(i).locIteratorAdvancePast(first.locIteratorGetMatch());
      }

      boolean flag = true;
      QryIop min_qry = null;
      int min_location = Integer.MAX_VALUE;
      
      while (flag) {

        for (int i = 0; i < this.args.size(); i++) {
          if (!this.getArg(i).locIteratorHasMatch()) {
            flag = false;
            break;
          }
          min_location = Math.min(min_location, this.getArg(i).locIteratorGetMatch());
          if (min_location == this.getArg(i).locIteratorGetMatch()) {
            min_qry = this.getArg(i);
          }
        }

        if (!flag) {
          break;
        }

        int first_location = first.locIteratorGetMatch();
        for (int i = 0; i < this.args.size(); i++) {
          int pos_2 = this.getArg(i+1).locIteratorGetMatch();
          int pos_1 = this.getArg(i).locIteratorGetMatch();
          if (pos_1 < pos_2 - distance || pos_1 > pos_2) {
            flag = false;
            break;
          }
        }

        positions.add(first_location);
        for (int i = 0; i < this.args.size(); i++) {
          this.getArg(i).locIteratorAdvance();
        }
      }

      if (!flag) {
        min_qry.locIteratorAdvancePast(min_location);
      }
      if (positions.size() == 0) {
        this.invertedList.appendPosting(minDocid, positions);
        return;
      }

      for (int i = 0; i < this.args.size(); i++) {
        this.getArg(i).docIteratorAdvancePast(minDocid);
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
