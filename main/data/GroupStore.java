package main.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupStore {
    public static class Group {
        private final String groupId;
        private String groupName;
        private final Set<String> members = ConcurrentHashMap.newKeySet();
        private final String creatorUserId;
        private final long creationTimestamp;
        private long lastUpdateTimestamp;

        public Group(String groupId, String groupName, Collection<String> initialMembers, String creatorUserId,
                long creationTimestamp) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.creatorUserId = creatorUserId;
            this.creationTimestamp = creationTimestamp;
            this.lastUpdateTimestamp = creationTimestamp;
            if (initialMembers != null) {
                this.members.addAll(initialMembers);
            }
        }

        public String getGroupId() {
            return groupId;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String name) {
            this.groupName = name;
        }

        public Set<String> getMembers() {
            return Collections.unmodifiableSet(members);
        }

        public boolean addMember(String userId) {
            boolean added = members.add(userId);
            if (added)
                lastUpdateTimestamp = System.currentTimeMillis() / 1000L;
            return added;
        }

        public boolean removeMember(String userId) {
            boolean removed = members.remove(userId);
            if (removed)
                lastUpdateTimestamp = System.currentTimeMillis() / 1000L;
            return removed;
        }

        public boolean isMember(String userId) {
            return members.contains(userId);
        }

        public String getCreatorUserId() {
            return creatorUserId;
        }

        public long getCreationTimestamp() {
            return creationTimestamp;
        }

        public long getLastUpdateTimestamp() {
            return lastUpdateTimestamp;
        }
    }

    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    public boolean createGroup(String groupId, String groupName, Collection<String> members, String creatorUserId,
            long timestamp) {
        Group group = new Group(groupId, groupName, members, creatorUserId, timestamp);
        return groups.putIfAbsent(groupId, group) == null;
    }

    public boolean updateGroupMembers(String groupId, Collection<String> addMembers, Collection<String> removeMembers) {
        Group group = groups.get(groupId);
        if (group == null)
            return false;

        boolean changed = false;
        if (addMembers != null) {
            for (String u : addMembers) {
                changed |= group.addMember(u);
            }
        }
        if (removeMembers != null) {
            for (String u : removeMembers) {
                changed |= group.removeMember(u);
            }
        }
        return changed;
    }

    public Group getGroup(String groupId) {
        return groups.get(groupId);
    }

    public boolean isUserMember(String groupId, String userId) {
        Group g = groups.get(groupId);
        return g != null && g.isMember(userId);
    }

    public boolean deleteGroup(String groupId) {
        return groups.remove(groupId) != null;
    }

    public Set<String> getAllGroupIds() {
        return Collections.unmodifiableSet(groups.keySet());
    }
}
