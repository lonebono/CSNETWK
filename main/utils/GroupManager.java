package main.utils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupManager {
    public static class Group {
        private final String groupId;
        private String groupName;
        private final Set<String> members = ConcurrentHashMap.newKeySet();
        private long creationTimestamp;
        private long lastUpdateTimestamp;

        public Group(String groupId, String groupName, long creationTimestamp) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.creationTimestamp = creationTimestamp;
            this.lastUpdateTimestamp = creationTimestamp;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public Set<String> getMembers() {
            return Collections.unmodifiableSet(members);
        }

        public boolean addMember(String userId) {
            boolean added = members.add(userId);
            if (added)
                updateTimestamp(System.currentTimeMillis());
            return added;
        }

        public boolean removeMember(String userId) {
            boolean removed = members.remove(userId);
            if (removed)
                updateTimestamp(System.currentTimeMillis());
            return removed;
        }

        public boolean isMember(String userId) {
            return members.contains(userId);
        }

        public long getCreationTimestamp() {
            return creationTimestamp;
        }

        public long getLastUpdateTimestamp() {
            return lastUpdateTimestamp;
        }

        private void updateTimestamp(long ts) {
            lastUpdateTimestamp = ts;
        }

    }

    private static final Map<String, Group> groups = new ConcurrentHashMap<>();

    public static boolean createGroup(String groupId, String groupName, Collection<String> members, long timestamp) {
        Group group = new Group(groupId, groupName, timestamp);
        if (members != null) {
            for (String member : members) {
                group.addMember(member);
            }
        }
        return groups.putIfAbsent(groupId, group) == null;
    }

    public static boolean updateGroupMembers(String groupId, Collection<String> addMembers,
            Collection<String> removeMembers, long timestamp) {
        Group group = groups.get(groupId);
        if (group == null)
            return false;

        boolean changed = false;
        if (addMembers != null) {
            for (String userId : addMembers) {
                changed |= group.addMember(userId);
            }
        }
        if (removeMembers != null) {
            for (String userId : removeMembers) {
                changed |= group.removeMember(userId);
            }
        }

        if (changed) {
            // Update last update timestamp to provided timestamp (e.g. from message)
            group.updateTimestamp(timestamp);
        }
        return changed;
    }

    public static Group getGroup(String groupId) {
        return groups.get(groupId);
    }

    public static boolean deleteGroup(String groupId) {
        return groups.remove(groupId) != null;
    }

    public static boolean isUserMember(String groupId, String userId) {
        Group group = groups.get(groupId);
        return group != null && group.isMember(userId);
    }

    public static Set<String> getAllGroupIds() {
        return Collections.unmodifiableSet(groups.keySet());
    }

}
