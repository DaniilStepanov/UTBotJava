package org.utbot.example;

import java.util.*;

public class GraphAlgorithms<T extends Number> {
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

    public boolean testFunc(ArrayList<ArrayList<Long>> array) {
        for (int i = 0; i < array.size() - 1; i++) {
            if (array.get(i).get(i) > array.get(i + 1).get(i + 1)) {
                return true;
            }
        }
        return false;
    }

    public boolean testFunc1(ArrayList<Long> array) {
        for (int i = 0; i < array.size() - 1; i++) {
            if (array.get(i) > array.get(i + 1)) {
                return true;
            }
        }
        return false;
    }

    public boolean testFunc2(long[] array) {
        for (int i = 0; i < array.length - 1; i++) {
            if (array[i] > array[i + 1]) {
                return true;
            }
        }
        return false;
    }

    public boolean testFunc3(ArrayList<H> a) {
//        for (int i = 0; i < array.size() - 1; i++) {
//            if (array.get(i).a > array.get(i + 1).a) {
//                return true;
//            }
//        }
        return false;
    }

//    public boolean testFunc3(A<Integer> a) {
////        for (int i = 0; i < array.size() - 1; i++) {
////            if (array.get(i).a > array.get(i + 1).a) {
////                return true;
////            }
////        }
//        return false;
//    }

}
