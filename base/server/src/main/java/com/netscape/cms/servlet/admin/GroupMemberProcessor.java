// --- BEGIN COPYRIGHT BLOCK ---
// This program is free software; you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation; version 2 of the License.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, write to the Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
// (C) 2013 Red Hat, Inc.
// All rights reserved.
// --- END COPYRIGHT BLOCK ---
package com.netscape.cms.servlet.admin;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.ws.rs.core.UriInfo;

import com.netscape.certsrv.base.BadRequestException;
import com.netscape.certsrv.base.ConflictingOperationException;
import com.netscape.certsrv.base.EBaseException;
import com.netscape.certsrv.base.PKIException;
import com.netscape.certsrv.base.ResourceNotFoundException;
import com.netscape.certsrv.base.SessionContext;
import com.netscape.certsrv.common.OpDef;
import com.netscape.certsrv.common.ScopeDef;
import com.netscape.certsrv.group.GroupMemberCollection;
import com.netscape.certsrv.group.GroupMemberData;
import com.netscape.certsrv.group.GroupNotFoundException;
import com.netscape.certsrv.logging.AuditFormat;
import com.netscape.certsrv.logging.ILogger;
import com.netscape.certsrv.logging.event.ConfigRoleEvent;
import com.netscape.cms.servlet.processors.Processor;
import com.netscape.cmscore.apps.CMS;
import com.netscape.cmscore.apps.CMSEngine;
import com.netscape.cmscore.apps.EngineConfig;
import com.netscape.cmscore.usrgrp.Group;
import com.netscape.cmscore.usrgrp.UGSubsystem;

/**
 * @author Endi S. Dewata
 */
public class GroupMemberProcessor extends Processor {

    public static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GroupMemberProcessor.class);

    public final static int DEFAULT_SIZE = 20;

    public final static String MULTI_ROLE_ENABLE = "multiroles.enable";
    public final static String MULTI_ROLE_ENFORCE_GROUP_LIST = "multiroles.false.groupEnforceList";

    public static String[] multiRoleGroupEnforceList;

    CMSEngine engine = CMS.getCMSEngine();
    public UGSubsystem userGroupManager = engine.getUGSubsystem();

    protected UriInfo uriInfo;

    public GroupMemberProcessor(Locale locale) throws EBaseException {
        super("group", locale);
    }

    public UriInfo getUriInfo() {
        return uriInfo;
    }

    public void setUriInfo(UriInfo uriInfo) {
        this.uriInfo = uriInfo;
    }

    public GroupMemberData createGroupMemberData(String groupID, String memberID) throws Exception {

        GroupMemberData groupMemberData = new GroupMemberData();
        groupMemberData.setID(memberID);
        groupMemberData.setGroupID(groupID);

        return groupMemberData;
    }

    public GroupMemberCollection findGroupMembers(String groupID, String filter, Integer start, Integer size) {

        try {
            start = start == null ? 0 : start;
            size = size == null ? DEFAULT_SIZE : size;

            if (groupID == null) {
                logger.error(CMS.getLogMessage("ADMIN_SRVLT_NULL_RS_ID"));
                throw new BadRequestException(getUserMessage("CMS_ADMIN_SRVLT_NULL_RS_ID"));
            }

            Group group = userGroupManager.getGroupFromName(groupID);
            if (group == null) {
                logger.error(CMS.getLogMessage("USRGRP_SRVLT_GROUP_NOT_EXIST"));
                throw new GroupNotFoundException(groupID);
            }

            GroupMemberCollection response = new GroupMemberCollection();

            Enumeration<String> members = group.getMemberNames();
            List<String> results = new ArrayList<>();

            // filter members
            while (members.hasMoreElements()) {
                String memberID = members.nextElement();
                if (filter == null || memberID.contains(filter)) results.add(memberID);
            }

            // return entries in the page
            for (int i = start; i < start+size && i < results.size(); i++) {
                String memberID = results.get(i);
                response.addEntry(createGroupMemberData(groupID, memberID));
            }

            // return the total entries
            response.setTotal(results.size());

            return response;

        } catch (PKIException e) {
            throw e;

        } catch (Exception e) {
            logger.error("GroupMemberProcessor: " + e.getMessage(), e);
            throw new PKIException(getUserMessage("CMS_INTERNAL_ERROR"));
        }
    }

    public GroupMemberData getGroupMember(String groupID, String memberID) {
        try {
            if (groupID == null) {
                logger.error(CMS.getLogMessage("ADMIN_SRVLT_NULL_RS_ID"));
                throw new BadRequestException(getUserMessage("CMS_ADMIN_SRVLT_NULL_RS_ID"));
            }

            Group group = userGroupManager.getGroupFromName(groupID);
            if (group == null) {
                logger.error(CMS.getLogMessage("USRGRP_SRVLT_GROUP_NOT_EXIST"));
                throw new GroupNotFoundException(groupID);
            }

            Enumeration<String> e = group.getMemberNames();
            while (e.hasMoreElements()) {
                String memberName = e.nextElement();
                if (!memberName.equalsIgnoreCase(memberID)) continue;

                GroupMemberData groupMemberData = createGroupMemberData(groupID, memberName);
                return groupMemberData;
            }

            throw new ResourceNotFoundException("Group member " + memberID + " not found");

        } catch (PKIException e) {
            throw e;

        } catch (Exception e) {
            logger.error("GroupMemberProcessor: " + e.getMessage(), e);
            throw new PKIException(e.getMessage(), e);
        }
    }

    public GroupMemberData addGroupMember(GroupMemberData groupMemberData) {
        String groupID = groupMemberData.getGroupID();

        CMSEngine engine = CMS.getCMSEngine();
        EngineConfig config = engine.getConfig();

        try {
            if (groupID == null) {
                logger.error(CMS.getLogMessage("ADMIN_SRVLT_NULL_RS_ID"));
                throw new BadRequestException(getUserMessage("CMS_ADMIN_SRVLT_NULL_RS_ID"));
            }

            Group group = userGroupManager.getGroupFromName(groupID);
            if (group == null) {
                logger.error(CMS.getLogMessage("USRGRP_SRVLT_GROUP_NOT_EXIST"));
                throw new GroupNotFoundException(groupID);
            }

            String memberID = groupMemberData.getID();
            boolean multiRole = true;

            try {
                multiRole = config.getBoolean(MULTI_ROLE_ENABLE);
            } catch (Exception e) {
                // ignore
            }

            if (multiRole) {
                // a user can be a member of multiple groups
                userGroupManager.addUserToGroup(group, memberID);

            } else {
                // a user can be a member of at most one group in the enforce list
                if (isGroupInMultiRoleEnforceList(groupID)) {
                    // make sure the user is not already a member in another group in the list
                    if (!isDuplicate(groupID, memberID)) {
                        userGroupManager.addUserToGroup(group, memberID);
                    } else {
                        throw new ConflictingOperationException(CMS.getUserMessage("CMS_BASE_DUPLICATE_ROLES", memberID));
                    }

                } else {
                    // the user can be a member of multiple groups outside the list
                    userGroupManager.addUserToGroup(group, memberID);
                }
            }

            // for audit log
            SessionContext sContext = SessionContext.getContext();
            String adminId = (String) sContext.get(SessionContext.USER_ID);

            logger.info(
                    AuditFormat.ADDUSERGROUPFORMAT,
                    adminId,
                    memberID,
                    groupID
            );

            auditAddGroupMember(groupID, groupMemberData, ILogger.SUCCESS);

            // read the data back
            groupMemberData = getGroupMember(groupID, memberID);

            return groupMemberData;

        } catch (PKIException e) {
            auditAddGroupMember(groupID, groupMemberData, ILogger.FAILURE);
            throw e;

        } catch (Exception e) {
            logger.error("GroupMemberProcessor: " + e.getMessage(), e);
            auditAddGroupMember(groupID, groupMemberData, ILogger.FAILURE);
            throw new PKIException(getUserMessage("CMS_USRGRP_GROUP_MODIFY_FAILED"), e);
        }
    }

    public boolean isGroupInMultiRoleEnforceList(String groupID) {

        if (groupID == null || groupID.equals("")) {
            return true;
        }

        CMSEngine engine = CMS.getCMSEngine();
        EngineConfig config = engine.getConfig();

        String groupList = null;
        if (multiRoleGroupEnforceList == null) {
            try {
                groupList = config.getString(MULTI_ROLE_ENFORCE_GROUP_LIST);
            } catch (Exception e) {
                // ignore
            }

            if (groupList != null && !groupList.equals("")) {
                multiRoleGroupEnforceList = groupList.split(",");
                for (int j = 0; j < multiRoleGroupEnforceList.length; j++) {
                    multiRoleGroupEnforceList[j] = multiRoleGroupEnforceList[j].trim();
                }
            }
        }

        if (multiRoleGroupEnforceList == null)
            return true;

        for (int i = 0; i < multiRoleGroupEnforceList.length; i++) {
            if (groupID.equals(multiRoleGroupEnforceList[i])) {
                return true;
            }
        }

        return false;
    }

    public boolean isDuplicate(String groupID, String memberID) {

        // Let's not mess with users that are already a member of this group
        try {
            boolean isMember = userGroupManager.isMemberOf(memberID, groupID);
            if (isMember == true) return false;

        } catch (Exception e) {
            // ignore
        }

        try {
            Enumeration<Group> groups = userGroupManager.listGroups(null);
            while (groups.hasMoreElements()) {
                Group group = groups.nextElement();
                String name = group.getName();

                Enumeration<Group> g = userGroupManager.findGroups(name);
                Group g1 = g.nextElement();

                if (!name.equals(groupID)) {
                    if (isGroupInMultiRoleEnforceList(name)) {
                        Enumeration<String> members = g1.getMemberNames();
                        while (members.hasMoreElements()) {
                            String m1 = members.nextElement();
                            if (m1.equals(memberID))
                                return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return false;
    }

    public void removeGroupMember(String groupID, String memberID) {
        GroupMemberData groupMemberData = new GroupMemberData();
        groupMemberData.setID(memberID);
        groupMemberData.setGroupID(groupID);
        removeGroupMember(groupMemberData);
    }

    public void removeGroupMember(GroupMemberData groupMemberData) {
        String groupID = groupMemberData.getGroupID();
        try {
            if (groupID == null) {
                logger.error(CMS.getLogMessage("ADMIN_SRVLT_NULL_RS_ID"));
                throw new BadRequestException(getUserMessage("CMS_ADMIN_SRVLT_NULL_RS_ID"));
            }

            Group group = userGroupManager.getGroupFromName(groupID);
            if (group == null) {
                logger.error(CMS.getLogMessage("USRGRP_SRVLT_GROUP_NOT_EXIST"));
                throw new GroupNotFoundException(groupID);
            }

            String memberID = groupMemberData.getID();
            userGroupManager.removeUserFromGroup(group, memberID);

            // for audit log
            SessionContext sContext = SessionContext.getContext();
            String adminId = (String) sContext.get(SessionContext.USER_ID);

            logger.info(
                    AuditFormat.REMOVEUSERGROUPFORMAT,
                    adminId,
                    memberID,
                    groupID
            );

            auditDeleteGroupMember(groupID, groupMemberData, ILogger.SUCCESS);

        } catch (PKIException e) {
            auditDeleteGroupMember(groupID, groupMemberData, ILogger.FAILURE);
            throw e;

        } catch (Exception e) {
            logger.error("GroupMemberProcessor: " + e.getMessage(), e);
            auditDeleteGroupMember(groupID, groupMemberData, ILogger.FAILURE);
            throw new PKIException(getUserMessage("CMS_USRGRP_GROUP_MODIFY_FAILED"));
        }
    }

    public void auditAddGroupMember(String groupID, GroupMemberData groupMemberData, String status) {
        audit(OpDef.OP_ADD, groupID, getParams(groupMemberData), status);
    }

    public void auditDeleteGroupMember(String groupID, GroupMemberData groupMemberData, String status) {
        audit(OpDef.OP_DELETE, groupID, getParams(groupMemberData), status);
    }

    public void audit(String type, String id, Map<String, String> params, String status) {

        if (auditor == null) return;

        signedAuditLogger.log(new ConfigRoleEvent(
                auditor.getSubjectID(),
                status,
                auditor.getParamString(ScopeDef.SC_GROUP_MEMBERS, type, id, params)));
    }
}
