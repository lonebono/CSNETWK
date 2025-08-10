package main.utils;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {
    private static final Map<String, Set<InetSocketAddress>> groups = new ConcurrentHashMap<>();

    public static boolean createGroup(String groupName) {
        return groups.putIfAbsent(groupName, ConcurrentHashMap.newKeySet()) == null;
    }

    public static boolean deleteGroup(String groupName) {
        return groups.remove(groupName) != null;
    }

    public static boolean addMember(String groupName, String ip, int port) {
        Set<InetSocketAddress> members = groups.get(groupName);
        if (members == null)
            return false;
        return members.add(new InetSocketAddress(ip, port));
    }

    public static boolean removeMember(String groupName, String ip, int port) {
        Set<InetSocketAddress> members = groups.get(groupName);
        if (members == null)
            return false;
        return members.remove(new InetSocketAddress(ip, port));
    }

    public static Set<InetSocketAddress> getMembers(String groupName) {
        Set<InetSocketAddress> members = groups.get(groupName);
        if (members == null)
            return Collections.emptySet();
        return Collections.unmodifiableSet(members);
    }

    public static boolean groupExists(String groupName) {
        return groups.containsKey(groupName);
    }

    public static Set<String> getAllGroupNames() {
        return Collections.unmodifiableSet(groups.keySet());
    }
}
