package main.data;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupStore {
    private final Map<String, Group> groups = new ConcurrentHashMap<>();

    public static class Group {
        private final String groupId;
        private String groupName;
        private final Set<String> members = ConcurrentHashMap.newKeySet();
        private long creationTimestamp;
        private long lastUpdateTimestamp;
        private final String creatorUserId;

        public Group(String groupId, String groupName, long creationTimestamp, String creatorUserId) {
            this.groupId = groupId;
            this.groupName = groupName;
            this.creationTimestamp = creationTimestamp;
            this.lastUpdateTimestamp = creationTimestamp;
            this.creatorUserId = creatorUserId;
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

        public long getCreationTimestamp() {
            return creationTimestamp;
        }

        public long getLastUpdateTimestamp() {
            return lastUpdateTimestamp;
        }

        public String getCreatorUserId() {
            return creatorUserId;
        }

        public void addMember(String userId) {
            members.add(userId);
            updateTimestamp(System.currentTimeMillis() / 1000L);
        }

        public void removeMember(String userId) {
            members.remove(userId);
            updateTimestamp(System.currentTimeMillis() / 1000L);
        }

        private void updateTimestamp(long timestamp) {
            lastUpdateTimestamp = timestamp;
        }
    }

    public boolean createGroup(String groupId, String groupName, Collection<String> members,
            long creationTimestamp, String creatorUserId) {
        Group group = new Group(groupId, groupName, creationTimestamp, creatorUserId);
        if (members != null) {
            for (String m : members) {
                group.addMember(m);
            }
        }
        return groups.putIfAbsent(groupId, group) == null;
    }

    public boolean updateGroupMembers(String groupId, Collection<String> addMembers, Collection<String> removeMembers) {
        Group group = groups.get(groupId);
        if (group == null)
            return false;

        boolean changed = false;
        if (addMembers != null) {
            for (String m : addMembers) {
                if (!group.getMembers().contains(m)) {
                    group.addMember(m);
                    changed = true;
                }
            }
        }
        if (removeMembers != null) {
            for (String m : removeMembers) {
                if (group.getMembers().contains(m)) {
                    group.removeMember(m);
                    changed = true;
                }
            }
        }
        return changed;
    }

    public Group getGroup(String groupId) {
        return groups.get(groupId);
    }

    public boolean isMember(String groupId, String userId) {
        Group g = groups.get(groupId);
        return g != null && g.getMembers().contains(userId);
    }

    public boolean groupExists(String groupId) {
        return groups.containsKey(groupId);
    }

    public Set<String> getAllGroupIds() {
        return Collections.unmodifiableSet(groups.keySet());
    }

    public Set<String> getMembers(String groupId) {
        Group g = groups.get(groupId);
        if (g == null)
            return Collections.emptySet();
        return g.getMembers();
    }

    public boolean deleteGroup(String groupId) {
        return groups.remove(groupId) != null;
    }

}
