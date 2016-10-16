///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveTask;

/**
 * This is an optimization of the CCD (Cyclic Causal Discovery) algorithm by Thomas Richardson.
 *
 * @author Joseph Ramsey
 */
public final class CcdMax implements GraphSearch {
    private final IndependenceTest independenceTest;
    private int depth = -1;
    private boolean applyOrientAwayFromCollider = false;
    private long elapsed = 0;

    public CcdMax(IndependenceTest test) {
        if (test == null) throw new NullPointerException();
        this.independenceTest = test;
    }

    //======================================== PUBLIC METHODS ====================================//

    /**
     * Searches for a PAG satisfying the description in Thomas Richardson (1997), dissertation,
     * Carnegie Mellon University. Uses a simplification of that algorithm.
     */
    public Graph search() {
        SepsetMap map = new SepsetMap();
        Graph graph = fastAdjacencySearch();
        orientTwoShieldConstructs(graph);
        orientCollidersMaxP(graph, map);
        orientTowardDConnection(graph, map);
        return graph;
    }

    /**
     * @return The depth of search for the Fast Adjacency Search.
     */
    public int getDepth() {
        return depth;
    }

    /**
     * @param depth The depth of search for the Fast Adjacency Search.
     */
    public void setDepth(int depth) {
        this.depth = depth;
    }

    /**
     * @return The elapsed time in milliseconds.
     */
    public long getElapsedTime() {
        return elapsed;
    }

    /**
     * @param applyOrientAwayFromCollider True if the orient away from collider rule should be
     *                                    applied.
     */
    public void setApplyOrientAwayFromCollider(boolean applyOrientAwayFromCollider) {
        this.applyOrientAwayFromCollider = applyOrientAwayFromCollider;
    }

    //======================================== PRIVATE METHODS ====================================//

    private Graph fastAdjacencySearch() {
        long start = System.currentTimeMillis();

        FasStableConcurrent fas = new FasStableConcurrent(null, independenceTest);
        fas.setDepth(getDepth());
        fas.setVerbose(false);
        fas.setRecordSepsets(false);
        Graph graph = fas.search();

        long stop = System.currentTimeMillis();
        this.elapsed = stop - start;

        return new EdgeListGraph(graph);
    }

    // Orient feedback loops and a few extra directed edges.
    private void orientTwoShieldConstructs(Graph graph) {
        TetradLogger.getInstance().log("info", "\nStep E");

        for (Node c : graph.getNodes()) {
            List<Node> adj = graph.getAdjacentNodes(c);

            for (int i = 0; i < adj.size(); i++) {
                Node a = adj.get(i);

                for (int j = i + 1; j < adj.size(); j++) {
                    Node b = adj.get(j);
                    if (a == b) continue;
                    if (graph.isAdjacentTo(a, b)) continue;

                    for (Node d : adj) {
                        if (d == a || d == b) continue;

                        if (graph.isAdjacentTo(d, a) && graph.isAdjacentTo(d, b)) {
                            if (sepset(graph, a, b, set(), set(c, d)) != null) {
                                if ((graph.getEdges().size() == 2 || Edges.isDirectedEdge(graph.getEdge(c, d)))) {
                                    continue;
                                }

                                if (sepset(graph, a, b, set(c, d), set()) != null) {
                                    orientCollider(graph, a, c, b);
                                    orientCollider(graph, a, d, b);
                                    addFeedback(graph, c, d);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void orientCollidersMaxP(Graph graph, SepsetMap map) {
        final SepsetsMinScore sepsets = new SepsetsMinScore(graph, independenceTest, -1);
        sepsets.setReturnNullWhenIndep(false);

        final Map<Triple, Double> colliders = new ConcurrentHashMap<>();

        final List<Node> nodes = graph.getNodes();

        class Task extends RecursiveTask<Boolean> {
            private final SepsetProducer sepsets;
            private final SepsetMap map;
            private final Map<Triple, Double> colliders;
            private final int from;
            private final int to;
            private final int chunk = 100;
            private final List<Node> nodes;
            private final Graph graph;

            private Task(SepsetProducer sepsets, SepsetMap map, List<Node> nodes, Graph graph,
                         Map<Triple, Double> colliders,
                         int from, int to) {
                this.sepsets = sepsets;
                this.map = map;
                this.nodes = nodes;
                this.graph = graph;
                this.from = from;
                this.to = to;
                this.colliders = colliders;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        doNode(graph, colliders, nodes.get(i), map);
                    }

                    return true;
                } else {
                    int mid = (to + from) / 2;

                    Task left = new Task(sepsets, map, nodes, graph, colliders, from, mid);
                    Task right = new Task(sepsets, map, nodes, graph, colliders, mid, to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        Task task = new Task(sepsets, map, nodes, graph, colliders, 0, nodes.size());

        ForkJoinPoolInstance.getInstance().getPool().invoke(task);

        List<Triple> collidersList = new ArrayList<>(colliders.keySet());

        // Most independent ones first.
        Collections.sort(collidersList, new Comparator<Triple>() {

            @Override
            public int compare(Triple o1, Triple o2) {
                return Double.compare(colliders.get(o2), colliders.get(o1));
            }
        });

        for (Triple triple : collidersList) {
            Node a = triple.getX();
            Node b = triple.getY();
            Node c = triple.getZ();

            if (!(graph.getEndpoint(b, a) == Endpoint.ARROW || graph.getEndpoint(b, c) == Endpoint.ARROW)) {
                orientCollider(graph, a, b, c);
            } else {
                graph.addUnderlineTriple(a, b, c);
            }
        }

        orientAwayFromArrow(graph);
    }

    private void doNode(Graph graph, Map<Triple, Double> colliders, Node b, SepsetMap map) {
        List<Node> adjacentNodes = graph.getAdjacentNodes(b);

        if (adjacentNodes.size() < 2) {
            return;
        }

        ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
        int[] combination;

        while ((combination = cg.next()) != null) {
            Node a = adjacentNodes.get(combination[0]);
            Node c = adjacentNodes.get(combination[1]);

            // Skip triples that are shielded.
            if (graph.isAdjacentTo(a, c)) {
                continue;
            }

            Pair P = maxPSepset(a, c, graph);
            List<Node> S = P.getCond();
            double score = P.getScore();

            if (S == null) {
                continue;
            }

            map.set(a, c, S);

            if (S.contains(b)) {
                graph.addUnderlineTriple(a, b, c);
            } else {
                colliders.put(new Triple(a, b, c), score);
            }
        }
    }

    private void orientTowardDConnection(Graph graph, SepsetMap map) {

        EDGE:
        for (Edge edge : graph.getEdges()) {
            if (!Edges.isUndirectedEdge(edge)) continue;

            Set<Node> surround = new HashSet<>();
            Node b = edge.getNode1();
            Node c = edge.getNode2();
            surround.add(b);

            for (int i = 1; i < 2; i++) {
                for (Node z : new HashSet<>(surround)) {
                    surround.addAll(graph.getAdjacentNodes(z));
                }
            }

            surround.remove(b);
            surround.remove(c);
            surround.removeAll(graph.getAdjacentNodes(b));
            surround.removeAll(graph.getAdjacentNodes(c));
            boolean orient = false;
            boolean agree = true;

            for (Node a : surround) {
                List<Node> sepsetax = map.get(a, b);
                List<Node> sepsetay = map.get(a, c);

                if (sepsetax == null) continue;
                if (sepsetay == null) continue;

                if (!sepsetax.equals(sepsetay)) {
                    if (sepsetax.containsAll(sepsetay)) {
                        orient = true;
                    } else {
                        agree = false;
                    }
                }
            }

            if (orient && agree) {
                addDirectedEdge(graph, c, b);
            }

            for (Node a : surround) {
                if (b == a) continue;
                if (c == a) continue;
                if (graph.getAdjacentNodes(b).contains(a)) continue;
                if (graph.getAdjacentNodes(c).contains(a)) continue;

                List<Node> sepsetax = map.get(a, b);
                List<Node> sepsetay = map.get(a, c);

                if (sepsetax == null) continue;
                if (sepsetay == null) continue;
                if (sepsetay.contains(b)) continue;

                if (!sepsetay.containsAll(sepsetax)) {
                    if (!independenceTest.isIndependent(a, b, sepsetay)) {
                        addDirectedEdge(graph, c, b);
                        continue EDGE;
                    }
                }
            }
        }
    }

    private boolean addDirectedEdge(Graph graph, Node a, Node b) {
        graph.removeEdge(a, b);
        graph.addDirectedEdge(a, b);
        orientAwayFromArrow(graph, a, b);
        return true;
    }

    private void addFeedback(Graph graph, Node a, Node b) {
        graph.removeEdge(a, b);
        graph.addEdge(Edges.directedEdge(a, b));
        graph.addEdge(Edges.directedEdge(b, a));
    }

    private void orientCollider(Graph graph, Node a, Node b, Node c) {
        if (wouldCreateBadCollider(a, b, graph)) return;
        if (wouldCreateBadCollider(c, b, graph)) return;
        graph.removeEdge(a, b);
        graph.removeEdge(c, b);
        graph.addDirectedEdge(a, b);
        graph.addDirectedEdge(c, b);
    }

    private void orientAwayFromArrow(Graph graph) {
        for (Edge edge : graph.getEdges()) {
            Node n1 = edge.getNode1();
            Node n2 = edge.getNode2();

            edge = graph.getEdge(n1, n2);

            if (edge.pointsTowards(n1)) {
                orientAwayFromArrow(graph, n2, n1);
            } else if (edge.pointsTowards(n2)) {
                orientAwayFromArrow(graph, n1, n2);
            }
        }
    }

    private void orientAwayFromArrow(Graph graph, Node a, Node b) {
        if (!applyOrientAwayFromCollider) return;

        for (Node c : graph.getAdjacentNodes(b)) {
            if (c == a) continue;
            orientAwayFromArrowVisit(a, b, c, graph);
        }
    }

    private void orientAwayFromArrowVisit(Node a, Node b, Node c, Graph graph) {
        if (!Edges.isUndirectedEdge(graph.getEdge(b, c))) {
            return;
        }

        if (!(graph.isUnderlineTriple(a, b, c))) {
            return;
        }

        boolean oriented = addDirectedEdge(graph, b, c);
        if (!oriented) return;

        graph.removeUnderlineTriple(a, b, c);

        for (Node d : graph.getAdjacentNodes(c)) {
            if (d == b) continue;
            orientAwayFromArrowVisit(b, c, d, graph);
        }
    }

    private boolean wouldCreateBadCollider(Node x, Node y, Graph graph) {
        for (Node z : graph.getAdjacentNodes(y)) {
            if (x == z) continue;
            if (graph.getEndpoint(x, y) != Endpoint.ARROW && graph.getEndpoint(z, y) == Endpoint.ARROW) return true;
        }

        return false;
    }

    private class Pair {
        private List<Node> cond;
        private double score;

        Pair(List<Node> cond, double score) {
            this.cond = cond;
            this.score = score;
        }

        public List<Node> getCond() {
            return cond;
        }

        public double getScore() {
            return score;
        }
    }

    private Pair maxPSepset(Node i, Node k, Graph graph) {
        double _p = Double.POSITIVE_INFINITY;
        List<Node> _v = null;

        List<Node> adji = graph.getAdjacentNodes(i);
        List<Node> adjk = graph.getAdjacentNodes(k);
        adji.remove(k);
        adjk.remove(i);

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), Math.max(adji.size(), adjk.size())); d++) {
            if (d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v2 = GraphUtils.asList(choice, adji);

                    getIndependenceTest().isIndependent(i, k, v2);
                    double p2 = getIndependenceTest().getScore();

                    if (p2 < _p) {
                        _p = p2;
                        _v = v2;
                    }
                }
            }

            if (d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    List<Node> v2 = GraphUtils.asList(choice, adjk);

                    getIndependenceTest().isIndependent(i, k, v2);
                    double p2 = getIndependenceTest().getScore();

                    if (p2 < _p) {
                        _p = p2;
                        _v = v2;
                    }
                }
            }
        }

        return new Pair(_v, _p);
    }

    // Returns a sepset containing the nodes in 'containing' but not the nodes in 'notContaining', or
    // null if there is no such sepset.
    private List<Node> sepset(Graph graph, Node a, Node c, Set<Node> containing, Set<Node> notContaining) {
        List<Node> adj = graph.getAdjacentNodes(a);
        adj.addAll(graph.getAdjacentNodes(c));
        adj.remove(c);
        adj.remove(a);

        for (int d = 0; d <= Math.min((depth == -1 ? 1000 : depth), Math.max(adj.size(), adj.size())); d++) {
            if (d <= adj.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adj.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    Set<Node> v2 = GraphUtils.asSet(choice, adj);
                    v2.addAll(containing);
                    v2.removeAll(notContaining);
                    v2.remove(a);
                    v2.remove(c);

                    getIndependenceTest().isIndependent(a, c, new ArrayList<>(v2));
                    double p2 = getIndependenceTest().getScore();

                    if (p2 < 0) {
                        return new ArrayList<>(v2);
                    }
                }
            }
        }

        return null;
    }

    private Set<Node> set(Node... n) {
        Set<Node> S = new HashSet<>();
        Collections.addAll(S, n);
        return S;
    }

    private IndependenceTest getIndependenceTest() {
        return independenceTest;
    }
}






