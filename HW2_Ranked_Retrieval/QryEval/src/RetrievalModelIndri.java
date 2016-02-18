/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  An object that stores parameters for the Indri Model
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {
  
  // Global Variables
  public double mu;
  public double lambda;

  public RetrievalModelIndri(double mu, double lambda) throws IOException {
  	this.mu = mu;
  	this.lambda = lambda;
  }

  public String defaultQrySopName () {
    return new String ("#and");
  }

}
