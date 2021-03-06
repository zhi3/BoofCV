/*
 * Copyright (c) 2011-2019, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.fiducial.calib.chess;

import boofcv.alg.fiducial.calib.chess.ChessboardCornerGraph.Node;
import georegression.metric.UtilAngle;
import org.ddogleg.sorting.QuickSort_F64;
import org.ddogleg.struct.FastQueue;
import org.ddogleg.struct.GrowQueue_B;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Given a chessboard corner cluster find the grid which it matches. The grid will be in "standard order". Depending
 * on the chessboard pattern there might be multiple multiple configurations that are in standard order or a unique
 * ordering. The corner at (0,0) is a corner that has a black square on the chessboard corner attached to it and
 * has the other corner with the same properly along its positive row direction and follows a right hand rule. Edges
 * will be ordered in CCW direction and the index of an edge which connects two adjacent corners to each other follows
 * the (i+2)%4 relationship. If multiple corners can be (0,0) then the one closest to the top left corner will be
 * selected.
 *
 * @author Peter Abeles
 */
public class ChessboardCornerClusterToGrid {
	QuickSort_F64 sorter = new QuickSort_F64();
	double[] directions = new double[4];
	int[] order = new int[4];
	Node[] tmpEdges = new Node[4];
	GrowQueue_B marked = new GrowQueue_B();
	Queue<Node> open = new LinkedList<>(); // FIFO queue

	List<Node> edgeList = new ArrayList<>();
	List<Node> cornerList = new ArrayList<>();

	// Used to optionally print extra debugging information
	PrintStream verbose;

	/**
	 * Puts cluster nodes into grid order and computes the number of rows and columns. If the cluster is not
	 * a complete grid this function will fail and return false
	 *
	 * @param cluster (Input) cluster. Edge order will be modified.
	 * @param info (Output) Contains ordered nodes and the grid's size.
	 * @return true if successful or false if it failed
	 */
	public boolean convert( ChessboardCornerGraph cluster , GridInfo info ) {
		// default to an invalid value to ensure a failure doesn't go unnoticed.
		info.cols = info.rows = -1;
		info.nodes.clear();

		// Get the edges in a consistent order
		if( !orderEdges(cluster) )
			return false;

		// Now we need to order the nodes into a proper grid which follows right hand rule
		if( !orderNodes(cluster,info) )
			return false;

		// select a valid corner to be (0,0). If there are multiple options select the one which is
		int corner = selectCorner(info);
		// rotate the grid until the select corner is at (0,0)
		for (int i = 0; i < corner; i++) {
			rotateCCW(info);
		}

		return true;
	}

	/**
	 * Selects a corner to be the grid's origin. 0 = top-left, 1 = top-right, 2 = bottom-right, 3 = bottom-left.
	 *
	 * Looks at each grid and see if it can be valid. Out of the valid list
	 */
	int selectCorner( GridInfo info ) {

		info.lookupGridCorners(cornerList);

		int bestCorner = -1;
		double bestScore = Double.MAX_VALUE;

		for (int i = 0; i < cornerList.size(); i++) {
			Node n = cornerList.get(i);
			if( isCornerValidOrigin(n) ) {
				double distance = n.normSq();
				if( distance < bestScore ) {
					bestScore = distance;
					bestCorner = i;
				}
			}
		}

		return bestCorner;
	}

	/**
	 * A corner can be an origin if the corner's orientation (a line between the two adjacent black squares) and
	 * the line splitting the direction to the two connecting nodes are the same.
	 */
	boolean isCornerValidOrigin( Node candidate ) {
		candidate.putEdgesIntoList(edgeList);
		if( edgeList.size() != 2 ) {
			throw new RuntimeException("BUG! Should be a corner and have two edges");
		}

		Node a = edgeList.get(0);
		Node b = edgeList.get(1);

		// Find the average angle from the two vectors defined by the two connected nodes
		double dirA = Math.atan2(a.y-candidate.y, a.x-candidate.x);
		double dirB = Math.atan2(b.y-candidate.y, b.x-candidate.x);

		double dirAB = UtilAngle.boundHalf(dirA+UtilAngle.distanceCW(dirA,dirB)/2.0);

		// Find the acute angle between the corner's orientation and the vector
		double acute = UtilAngle.distHalf(dirAB,candidate.orientation);

		return acute < Math.PI/4.0;
	}

	/**
	 * Put corners into a proper grid. Make sure its a rectangular grid or else return false. Rows and columns
	 * are selected to ensure right hand rule.
	 */
	boolean orderNodes( ChessboardCornerGraph cluster , GridInfo info ) {

		// Find a node with just two edges. This is a corner and will be the arbitrary origin in our graph
		Node seed = null;
		for (int i = 0; i < cluster.corners.size; i++) {
			Node n = cluster.corners.get(i);
			if( n.countEdges() == 2 ) {
				seed = n;
				break;
			}
		}
		if( seed == null ) {
			if( verbose != null ) verbose.println("Can't find a corner with just two edges. Aborting");
			return false;
		}

		// find one edge and mark that as the row direction
		int rowEdge = 0;
		while( seed.edges[rowEdge] == null )
			rowEdge++;
		int colEdge = rowEdge+1;
		while( seed.edges[colEdge] == null )
			colEdge++;

		// if it's left handed swap the row and column direction
		if( !isRightHanded(seed,rowEdge,colEdge)) {
			int tmp = rowEdge;
			rowEdge = colEdge;
			colEdge = tmp;
		}

		// add the corns to list in a row major order
		while( seed.edges[colEdge] != null ) {
			int before = info.nodes.size();
			info.nodes.add(seed);
			Node n = seed;
			while( n != null ) {
				n = n.edges[rowEdge];
				info.nodes.add(n);
			}
			seed = seed.edges[colEdge];

			if( info.cols == -1 ) {
				info.cols = info.nodes.size();
			} else {
				int columnsInRow = info.nodes.size()-before;
				if( columnsInRow != info.cols ) {
					if( verbose != null ) verbose.println("Number of columns in each row is variable");
					return false;
				}
			}
		}
		info.rows = info.nodes.size()/info.cols;
		return true;
	}

	/**
	 * Checks to see if the rows and columns for a coordinate system which si right handed
	 */
	static boolean isRightHanded( Node seed , int idxRow , int idxCol ) {
		Node r = seed.edges[idxRow];
		Node c = seed.edges[idxCol];

		double dirRow = Math.atan2(r.y-seed.y,r.x-seed.x);
		double dirCol = Math.atan2(c.y-seed.y,c.x-seed.x);

		return UtilAngle.distanceCW(dirRow,dirCol) < Math.PI;
	}

	/**
	 * Puts the edges in CCW order and aligns edge indexes into pairs.
	 */
	boolean orderEdges( ChessboardCornerGraph cluster ) {
		sortEdgesCCW(cluster.corners);
		return alignEdges(cluster.corners);
	}

	/**
	 * Enforces the rule that an edge in node A has an edge in node B that points back to A at index (i+2)%4.
	 */
	boolean alignEdges(FastQueue<Node> corners) {
		open.clear();
		open.add( corners.get(0) );

		marked.resize(corners.size);
		marked.fill(false);

		marked.set(corners.get(0).index,true);

		while( !open.isEmpty() ) {
			Node na = open.remove();

			// examine each neighbor and see the neighbor is correctly aligned
			for (int i = 0; i < 4; i++) {
				if( na.edges[i] == null ) {
					continue;
				}
				// Compute which index should be an edge pointing back at 'na'
				int j = (i+2)%4;

				Node nb = na.edges[i];
				if( marked.get(nb.index) ) {
					if( nb.edges[j] != na ) {
						if( verbose != null ) verbose.println("BUG! node has been processed and its edges do not align.");
						return false;
					}
					continue;
				}

				// Rotate edges
				boolean failed = true;
				for (int attempt = 0; attempt < 4; attempt++) {
					if( nb.edges[j] != na ) {
						nb.rotateEdgesDown();
					} else {
						failed = false;
						break;
					}
				}
				if( failed ) {
					if( verbose != null ) verbose.println("BUG! Can't align edges");
					return false;
				}
				marked.set(nb.index,true);
			}
		}
		return true;
	}

	/**
	 * Sorts edges so that they point towards nodes in an increasing counter clockwise direction
	 */
	void sortEdgesCCW(FastQueue<Node> corners) {
		for (int nodeIdx = 0; nodeIdx < corners.size; nodeIdx++) {
			Node na = corners.get(nodeIdx);

			int count = 0;
			for (int i = 0; i < 4; i++) {
				order[i] = i;
				tmpEdges[i] = na.edges[i];
				if( na.edges[i] == null ) {
					directions[i] = Double.MAX_VALUE;
				} else {
					count++;
					Node nb = na.edges[i];
					directions[i] = Math.atan2(nb.y-na.y,nb.x-na.x);
				}
			}

			sorter.sort(directions,0,4,order);
			for (int i = 0; i < 4; i++) {
				na.edges[i] = tmpEdges[ order[i] ];
			}
			// Edges need to point along the 4 possible directions, in the case of 3 edges, there might
			// need to be a gap at a different location than at the end
			if( count == 3 ) {
				double tail = UtilAngle.distanceCCW(directions[order[2]],directions[order[0]]);
				for (int i = 1; i <3; i++) {
					double ccw = UtilAngle.distanceCCW(directions[order[i-1]],directions[order[i]]);
					if( tail < ccw ) {
						for (int j = 3; j >= i+1; j--) {
							na.edges[j] = na.edges[j-1];
						}
						na.edges[i] = null;
						break;
					}
				}
			}
		}
	}

	/**
	 * Rotates the grid in the CCW direction
	 */
	void rotateCCW(GridInfo grid ) {
		cornerList.clear();
		for (int row = 0; row < grid.rows; row++) {
			for (int col = 0; col < grid.cols; col++) {
				cornerList.add(grid.get(col, grid.cols - row - 1));
			}
		}
		grid.nodes.clear();
		grid.nodes.addAll(cornerList);
	}

	public void setVerbose(PrintStream verbose) {
		this.verbose = verbose;
	}

	public static class GridInfo {
		public List<Node> nodes = new ArrayList<>();
		public int rows,cols;

		public Node get( int row , int col ) {
			return nodes.get( row*cols + col);
		}

		public void lookupGridCorners( List<Node> corners ) {
			corners.clear();
			corners.add( this.nodes.get(0) );
			corners.add( this.nodes.get(cols-1) );
			corners.add( this.nodes.get(rows*cols-1) );
			corners.add( this.nodes.get(rows*(cols-1)) );
		}
	}
}
