package org.utbot.example;

import java.util.*;

public class GraphAlgorithms<T extends Number> {
    public GraphAlgorithms(T lol) {
        this.lol = lol;
    }

    public T lol;
//    ArrayList<? extends Double> arr2;

    public boolean bfs(Graph graph, int startNodeNumber, int goalNodeNumber) {
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


//    public Object testFunc3(ArrayList<A<T>> a) {
//        if (a.get(0) == null) {
//            return null;
//        }
//        return a.get(a.size() - 1);
//    }

    public Number testFunc3() {
        if (this.lol instanceof Integer) {
            return ((Integer) this.lol + 1);
        }
        if (this.lol instanceof Short) {
            return ((Short) this.lol + 1);
        }
        if (this.lol instanceof Long) {
            return ((Long) this.lol + 1);
        }
        if (this.lol instanceof Double) {
            return ((Double) this.lol + 1);
        }
        if (this.lol instanceof Float) {
            return ((Float) this.lol + 1);
        }
        return this.lol;
    }

    public void lol() {
    }

//    public boolean testFunc3(java.util.concurrent.BlockingQueue<java.lang.Runnable> a) {
//        for (int i = 0; i < array.size() - 1; i++) {
//            if (array.get(i).a > array.get(i + 1).a) {
//                return true;
//            }
//        }
//        return false;
//    }

//    public boolean testFunc3(A<Integer> a) {
////        for (int i = 0; i < array.size() - 1; i++) {
////            if (array.get(i).a > array.get(i + 1).a) {
////                return true;
////            }
////        }
//        return false;
//    }

}
