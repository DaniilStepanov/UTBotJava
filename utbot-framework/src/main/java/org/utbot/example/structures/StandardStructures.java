package org.utbot.example.structures;

import java.util.*;

public class StandardStructures {
    public List<Integer> getList(List<Integer> list) {
        if (list instanceof ArrayList) {
            return list;
        }

        if (list instanceof LinkedList) {
            return list;
        }

        if (list == null) {
            return null;
        }

        return list;
    }

    public Map<Integer, Integer> getMap(Map<Integer, Integer> map) {
        if (map instanceof TreeMap) {
            return map;
        }

        if (map == null) {
            return null;
        }

        return map;
    }

    public Deque<Integer> getDeque(Deque<Integer> deque) {
        if (deque instanceof ArrayDeque) {
            return deque;
        }

        if (deque instanceof LinkedList) {
            return deque;
        }

        if (deque == null) {
            return null;
        }

        return deque;
    }
}