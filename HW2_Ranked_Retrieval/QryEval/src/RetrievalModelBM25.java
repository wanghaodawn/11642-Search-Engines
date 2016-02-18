/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  An object that stores parameters for the BM25
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */

public class RetrievalModelBM25 extends RetrievalModel {
  
  // Global Variables
  public double b;
  public double k1;
  public double k3;

  public RetrievalModelBM25(double b, double k1, double k3) throws IOException  {
  	this.b = b;
  	this.k1 = k1;
  	this.k3 = k3;
  }

  public String defaultQrySopName () {
    return new String ("#sum");
  }

}
