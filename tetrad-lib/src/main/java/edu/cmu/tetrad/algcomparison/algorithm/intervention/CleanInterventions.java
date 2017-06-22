package edu.cmu.tetrad.algcomparison.algorithm.intervention;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by bandrews on 6/21/17.
 */
public class CleanInterventions {

    public CleanInterventions() {
    }

    public Graph removeEdges(Graph I) {
        Graph G = GraphUtils.emptyGraph(0);
        for (Edge edge : G.getEdges()) {
            if (edge.getNode1().getName().startsWith("I") || edge.getNode2().getName().startsWith("I")) {
                G.removeEdge(edge);
            }
        }
        return G;
    }

    public Graph removeNodes(Graph I) {
        Graph G = GraphUtils.emptyGraph(0);
        for (Node node : G.getNodes()) {
            if (node.getName().startsWith("I")) {
                G.removeNode(node);
            }
        }
        return G;
    }

    public DataModel removeVars(DataModel I) {
        DataSet D = (DataSet) I.copy();
        for (Node col : D.getVariables()) {
            if (col.getName().startsWith("I")) {
                D.removeColumn(col);
            }
        }
        return D;
    }

    public DataModel removeRows(DataModel I) {
        DataSet D = (DataSet) I.copy();
        List<Integer> selectedRows = new ArrayList<>();
        for (int i = 0; i < D.getNumRows(); i++) {
            boolean rowI = false;
            for (Node node : D.getVariables()) {
                if (node.getName().startsWith("ID") && D.getInt(i,D.getColumn(node)) > 0) {
                    rowI = true;
                    break;
                }
            }
            if (!rowI) {
                selectedRows.add(i);
            }
        }

        DataSet newD = new BoxDataSet(new MixedDataBox(D.getVariables(), selectedRows.size()), D.getVariables());

        for (int row = 0; row < selectedRows.size(); row++) {
            for (int col = 0; col < D.getNumColumns(); col++) {
                if (D.getVariable(col) instanceof DiscreteVariable) {
                    newD.setInt(row, col, D.getInt(selectedRows.get(row), col));
                } else {
                    newD.setDouble(row, col, D.getDouble(selectedRows.get(row), col));
                }
            }
        }

        return newD;
    }

    public DataModel removeExtra(DataModel I) {
        DataSet D = (DataSet) I.copy();
        List<Integer> observedRows = new ArrayList<>();
        List<Integer> intervenedRows = new ArrayList<>();
        for (int i = 0; i < D.getNumRows(); i++) {
            boolean rowI = false;
            for (Node node : D.getVariables()) {
                if (node.getName().startsWith("ID") && D.getInt(i,D.getColumn(node)) > 0) {
                    intervenedRows.add(i);
                    rowI = true;
                    break;
                }
            }
            if (!rowI) {
                observedRows.add(i);
            }
        }

        DataSet newD = new BoxDataSet(new MixedDataBox(D.getVariables(), observedRows.size()), D.getVariables());

        int numObv = observedRows.size() - intervenedRows.size();

        for (int row = 0; row < numObv; row++) {
            for (int col = 0; col < D.getNumColumns(); col++) {
                if (D.getVariable(col) instanceof DiscreteVariable) {
                    newD.setInt(row, col, D.getInt(observedRows.get(row), col));
                } else {
                    newD.setDouble(row, col, D.getDouble(observedRows.get(row), col));
                }
            }
        }

        for (int row = 0; row < intervenedRows.size(); row++) {
            for (int col = 0; col < D.getNumColumns(); col++) {
                if (D.getVariable(col) instanceof DiscreteVariable) {
                    newD.setInt(numObv + row, col, D.getInt(intervenedRows.get(row), col));
                } else {
                    newD.setDouble(numObv + row, col, D.getDouble(intervenedRows.get(row), col));
                }
            }
        }

        int a = 0;

        return newD;
    }

}