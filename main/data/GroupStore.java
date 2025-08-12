package main.data;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupStore {
    public static class Group {
        private final String groupId;
        private String groupName;
        private final Map<String, InetSocketAddress> members = new ConcurrentHashMap<>();

        private final String creatorUserId;
        private final long creationTimestamp;
        private long lastUpdateTimestamp;

        public Group(String groupId, String groupName, Map<String, InetSocketAddress> initialMembers,
                String creatorUserId,
                long creationTimestamp) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.creatorUserId = creatorUserId;
            this.creationTimestamp = creationTimestamp;
            this.lastUpdateTimestamp = creationTimestamp;
            if (initialMembers != null) {
                this.members.putAll(initialMembers);
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

        // Return unmodifiable map of members userId -> InetSocketAddress
        public Map<String, InetSocketAddress> getMembers() {
            return Collections.unmodifiableMap(members);
        }

        public boolean addMember(String userId, InetSocketAddress addr) {
            InetSocketAddress prev = members.putIfAbsent(userId, addr);
            boolean added = (prev == null);
            if (added)
                lastUpdateTimestamp = System.currentTimeMillis() / 1000L;
            return added;
        }

        public boolean removeMember(String userId) {
            InetSocketAddress removed = members.remove(userId);
            if (removed != null) {
                lastUpdateTimestamp = System.currentTimeMillis() / 1000L;
                return true;
            }
            return false;
        }

        public boolean isMember(String userId) {
            return members.containsKey(userId);
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

    // Map from groupId -> Group
    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    // Change method signature to accept Map<String, InetSocketAddress> instead of
    // Collection<String>
    public boolean createGroup(String groupId, String groupName, Map<String, InetSocketAddress> members,
            String creatorUserId,
            long timestamp) {
        Group group = new Group(groupId, groupName, members, creatorUserId, timestamp);
        return groups.putIfAbsent(groupId, group) == null;
    }

    // Change addMembers and removeMembers to maps for IP+port (or update
    // accordingly)
    // For simplicity, keep parameter as Map for adding, Collection<String> for
    // removal by userId string
    public boolean updateGroupMembers(String groupId, Map<String, InetSocketAddress> addMembers,
            Collection<String> removeMembers) {
        Group group = groups.get(groupId);
        if (group == null)
            return false;

        boolean changed = false;
        if (addMembers != null) {
            for (Map.Entry<String, InetSocketAddress> entry : addMembers.entrySet()) {
                changed |= group.addMember(entry.getKey(), entry.getValue());
            }
        }
        if (removeMembers != null) {
            for (String userId : removeMembers) {
                changed |= group.removeMember(userId);
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
