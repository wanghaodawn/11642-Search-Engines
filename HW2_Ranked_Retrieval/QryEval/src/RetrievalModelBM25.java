/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the BM25
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {
  
  // Global Variables
  public final double b;
  public final double k1;
  public final double k3;

  public String defaultQrySopName () {
    return new String ("#sum");
  }

}
