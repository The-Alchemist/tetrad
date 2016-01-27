/*
 * Copyright (C) 2016 University of Pittsburgh.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package edu.cmu.tetrad.correlation;

/**
 *
 * Jan 27, 2016 5:48:23 PM
 *
 * @author Kevin V. Bui (kvb2@pitt.edu)
 */
public class RealCovarianceMatrixOnTheFly implements RealCovariance {

    private final double[][] data;

    private final int numOfRows;

    private final int numOfCols;

    public RealCovarianceMatrixOnTheFly(double[][] data) {
        this.data = data;
        this.numOfRows = data.length;
        this.numOfCols = data[0].length;
    }

    private void computeMeans() {
        for (int col = 0; col < numOfCols; col++) {
            double mean = 0;
            for (int row = 0; row < numOfRows; row++) {
                mean += data[row][col];
            }
            mean /= numOfRows;
            for (int row = 0; row < numOfRows; row++) {
                data[row][col] -= mean;
            }
        }
    }

    @Override
    public double[] computeLowerTriangle(boolean biasCorrected) {
        double[] covarianceMatrix = new double[(numOfCols * (numOfCols + 1)) / 2];

        computeMeans();

        int index = 0;
        for (int col = 0; col < numOfCols; col++) {
            for (int col2 = 0; col2 < col; col2++) {
                double variance = 0;
                for (int row = 0; row < numOfRows; row++) {
                    variance += ((data[row][col]) * (data[row][col2]) - variance) / (row + 1);
                }
                covarianceMatrix[index++] = biasCorrected ? variance * ((double) numOfRows / (double) (numOfRows - 1)) : variance;
            }
            double variance = 0;
            for (int row = 0; row < numOfRows; row++) {
                variance += ((data[row][col]) * (data[row][col]) - variance) / (row + 1);
            }
            covarianceMatrix[index++] = biasCorrected ? variance * ((double) numOfRows / (double) (numOfRows - 1)) : variance;
        }

        return covarianceMatrix;
    }

    @Override
    public double[][] compute(boolean biasCorrected) {
        double[][] covarianceMatrix = new double[numOfCols][numOfCols];

        computeMeans();

        for (int col = 0; col < numOfCols; col++) {
            for (int col2 = 0; col2 < col; col2++) {
                double variance = 0;
                for (int row = 0; row < numOfRows; row++) {
                    variance += ((data[row][col]) * (data[row][col2]) - variance) / (row + 1);
                }
                variance = biasCorrected ? variance * ((double) numOfRows / (double) (numOfRows - 1)) : variance;
                covarianceMatrix[col][col2] = variance;
                covarianceMatrix[col2][col] = variance;
            }
            double variance = 0;
            for (int row = 0; row < numOfRows; row++) {
                variance += ((data[row][col]) * (data[row][col]) - variance) / (row + 1);
            }
            covarianceMatrix[col][col] = biasCorrected ? variance * ((double) numOfRows / (double) (numOfRows - 1)) : variance;
        }

        return covarianceMatrix;
    }

}
