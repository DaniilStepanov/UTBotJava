package org.utbot.example.algorithms;

import java.util.Deque;
import java.util.LinkedList;

public class GraphAlgorithms {
    public static boolean bfs(Graph graph, int startNodeNumber, int goalNodeNumber) {
        Deque<Integer> queue = new LinkedList<>();//(graph.getSize());
        boolean[] visited = new boolean[graph.getSize()];
        queue.push(startNodeNumber);
        visited[startNodeNumber] = true;
        while (!queue.isEmpty()) {
            int curNodeNumber = queue.pop();
            if (curNodeNumber == goalNodeNumber) return true;
            for (int child : graph.getChildrenOf(curNodeNumber)) {
                if (!visited[child]) {
                    queue.push(child);
                    visited[child] = true;
                }
            }
        }
        return true;
    }

}
