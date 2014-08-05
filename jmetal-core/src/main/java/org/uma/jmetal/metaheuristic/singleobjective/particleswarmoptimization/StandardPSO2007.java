//  StandardPSO2007.java
//
//  Author:
//       Antonio J. Nebro <antonio@lcc.uma.es>
//
//  Copyright (c) 2014 Antonio J. Nebro
//
//  This program is free software: you can redistribute it and/or modify
//  it under the terms of the GNU Lesser General Public License as published by
//  the Free Software Foundation, either version 3 of the License, or
//  (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful,
//  but WITHOUT ANY WARRANTY; without even the implied warranty of
//  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//  GNU Lesser General Public License for more details.
// 
//  You should have received a copy of the GNU Lesser General Public License
//  along with this program.  If not, see <http://www.gnu.org/licenses/>.

package org.uma.jmetal.metaheuristic.singleobjective.particleswarmoptimization;

import org.uma.jmetal.core.*;
import org.uma.jmetal.operator.selection.BestSolutionSelection;
import org.uma.jmetal.util.AdaptiveRandomNeighborhood;
import org.uma.jmetal.util.JMetalLogger;
import org.uma.jmetal.util.JMetalException;
import org.uma.jmetal.util.comparator.ObjectiveComparator;
import org.uma.jmetal.util.random.PseudoRandom;
import org.uma.jmetal.util.wrapper.XReal;

import java.util.Comparator;
import java.util.HashMap;
import java.util.logging.Level;

/**
 * Class implementing a Standard PSO 2007 algorithm
 */
public class StandardPSO2007 extends Algorithm {

  int evaluations;

  Comparator comparator;
  Operator findBestSolution;
  private SolutionSet swarm;
  private int swarmSize;
  private int maxIterations;
  private int iteration;
  private int numberOfParticlesToInform; // Referred a K in the SPSO document
  private Solution[] localBest;
  private Solution[] neighborhoodBest;
  private double[][] speed;
  private AdaptiveRandomNeighborhood neighborhood;
  private double W;
  private double C;

  /** Constructor */
  public StandardPSO2007() {
    super();

    W = 1.0 / (2.0 * Math.log(2)); //0.721;
    C = 1.0 / 2.0 + Math.log(2); //1.193;

    comparator = new ObjectiveComparator(0); // Single objective comparator
    HashMap parameters; // Operator parameters

    parameters = new HashMap();
    parameters.put("comparator", comparator);
    findBestSolution = new BestSolutionSelection(parameters);

    evaluations = 0;
  }

  public double getW() {
    return W;
  }

  public double getC() {
    return C;
  }

  /**
   * Initialize all parameter of the algorithm
   */
  public void initParams() {
    swarmSize = (Integer) getInputParameter("swarmSize");
    maxIterations = (Integer) getInputParameter("maxIterations");
    numberOfParticlesToInform =
      (Integer) getInputParameter("numberOfParticlesToInform");

    //Configuration.logger.info("Swarm size: " + swarmSize) ;

    iteration = 0;

    swarm = new SolutionSet(swarmSize);
    localBest = new Solution[swarmSize];
    neighborhoodBest = new Solution[swarmSize];

    // Create the speed vector
    speed = new double[swarmSize][problem.getNumberOfVariables()];
  }


  private Solution getNeighborBest(int i) {
    Solution bestLocalBestSolution = null;

    try {
      for (int index : neighborhood.getNeighbors(i)) {
        if ((bestLocalBestSolution == null) || (bestLocalBestSolution.getObjective(0)
          > localBest[index].getObjective(0))) {
          bestLocalBestSolution = localBest[index];
        }
      }
    } catch (JMetalException e) {
      JMetalLogger.logger.log(Level.SEVERE, "Error", e);
    }

    return bestLocalBestSolution;
  }

  private void computeSpeed() throws JMetalException {
    double r1, r2;

    for (int i = 0; i < swarmSize; i++) {

      XReal particle = new XReal(swarm.get(i));
      XReal localBest = new XReal(this.localBest[i]);
      XReal neighborhoodBest = new XReal(this.neighborhoodBest[i]);

      r1 = PseudoRandom.randDouble(0, C);
      r2 = PseudoRandom.randDouble(0, C);

      if (this.localBest[i] != this.neighborhoodBest[i]) {
        for (int var = 0; var < particle.getNumberOfDecisionVariables(); var++) {
          speed[i][var] = W * speed[i][var] +
            r1 * (localBest.getValue(var) - particle.getValue(var)) +
            r2 * (neighborhoodBest.getValue(var) - particle.getValue(var));
        }
      } else {
        for (int var = 0; var < particle.getNumberOfDecisionVariables(); var++) {
          speed[i][var] = W * speed[i][var] +
            r1 * (localBest.getValue(var) - particle.getValue(var));
        }
      }
    }
  }

  /**
   * Update the position of each particle
   *
   * @throws org.uma.jmetal.util.JMetalException
   */
  private void computeNewPositions() throws JMetalException {
    for (int i = 0; i < swarmSize; i++) {
      XReal particle = new XReal(swarm.get(i));
      for (int var = 0; var < particle.size(); var++) {
        particle.setValue(var, particle.getValue(var) + speed[i][var]);

        if (particle.getValue(var) < problem.getLowerLimit(var)) {
          particle.setValue(var, problem.getLowerLimit(var));
          speed[i][var] = 0;
        }
        if (particle.getValue(var) > problem.getUpperLimit(var)) {
          particle.setValue(var, problem.getUpperLimit(var));
          speed[i][var] = 0;
        }
      }
    }
  }


  /**
   * Runs of the StandardPSO2007 algorithm.
   *
   * @return a <code>SolutionSet</code> that is a set of non dominated solutions
   * as a experimentoutput of the algorithm execution
   * @throws org.uma.jmetal.util.JMetalException
   */
  public SolutionSet execute() throws JMetalException, ClassNotFoundException {
    initParams();

    // Step 1 Create the initial population and evaluate
    for (int i = 0; i < swarmSize; i++) {
      Solution particle = new Solution(problem);
      problem.evaluate(particle);
      evaluations++;
      swarm.add(particle);
    }

    neighborhood = new AdaptiveRandomNeighborhood(swarm, numberOfParticlesToInform);

    JMetalLogger.logger.info("SwarmSize: " + swarmSize);
    JMetalLogger.logger.info("Swarm size: " + swarm.size());
    JMetalLogger.logger.info("list size: " + neighborhood.getNeighborhood().size());

    // Step2. Initialize the speed of each particle
    for (int i = 0; i < swarmSize; i++) {
      XReal particle = new XReal(swarm.get(i));
      for (int j = 0; j < problem.getNumberOfVariables(); j++) {
        speed[i][j] =
          (PseudoRandom.randDouble(particle.getLowerBound(j), particle.getUpperBound(j))
            - particle.getValue(j)) / 2.0;
      }
    }

    //-> Step 6. Initialize the memory of each particle
    for (int i = 0; i < swarm.size(); i++) {
      Solution particle = new Solution(swarm.get(i));
      localBest[i] = particle;
    }

    for (int i = 0; i < swarm.size(); i++) {
      neighborhoodBest[i] = getNeighborBest(i);
    }

    //Configuration.logger.info("neighborhood_i " + neighborhood.getNeighbors(0) );
    //for (int s :  neighborhood.getNeighbors(0)) {
    //  Configuration.logger.info(s + ": " + localBest[s].getObjective(0)) ;
    //}

    //Configuration.logger.info("localBest_i " + localBest[0].getObjective(0) );
    //Configuration.logger.info("neighborhoodBest_i " + getNeighborBest(0).getObjective(0) );

    //Configuration.logger.info("Swarm: " + swarm) ;
    swarm.printObjectives();
    Double b = swarm.best(comparator).getObjective(0);
    //Configuration.logger.info("Best: " + b) ;

    double bestFoundFitness = Double.MAX_VALUE;

    while (iteration < maxIterations) {
      //Compute the speed
      computeSpeed();

      //Compute the new positions for the swarm
      computeNewPositions();

      //Evaluate the new swarm in new positions
      for (int i = 0; i < swarm.size(); i++) {
        Solution particle = swarm.get(i);
        problem.evaluate(particle);
        evaluations++;
      }

      //Update the memory of the particles
      for (int i = 0; i < swarm.size(); i++) {
        if ((swarm.get(i).getObjective(0) < localBest[i].getObjective(0))) {
          Solution particle = new Solution(swarm.get(i));
          localBest[i] = particle;
        }
      }
      for (int i = 0; i < swarm.size(); i++) {
        neighborhoodBest[i] = getNeighborBest(i);
      }


      iteration++;

      Double bestCurrentFitness = swarm.best(comparator).getObjective(0);
      JMetalLogger.logger.info("Best: " + bestCurrentFitness);

      if (bestCurrentFitness == bestFoundFitness) {
        JMetalLogger.logger.info("Recomputing");
        neighborhood.recompute();
      }

      if (bestCurrentFitness < bestFoundFitness) {
        bestFoundFitness = bestCurrentFitness;
      }
    }

    // Return a population with the best individual
    SolutionSet resultPopulation = new SolutionSet(1);
    resultPopulation.add(swarm.get((Integer) findBestSolution.execute(swarm)));

    return resultPopulation;
  }
}