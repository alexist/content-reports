/*
 * ==========================================================================================
 * =                            JAHIA'S ENTERPRISE DISTRIBUTION                             =
 * ==========================================================================================
 *
 *                                  http://www.jahia.com
 *
 * JAHIA'S ENTERPRISE DISTRIBUTIONS LICENSING - IMPORTANT INFORMATION
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2020 Jahia Solutions Group. All rights reserved.
 *
 *     This file is part of a Jahia's Enterprise Distribution.
 *
 *     Jahia's Enterprise Distributions must be used in accordance with the terms
 *     contained in the Jahia Solutions Group Terms &amp; Conditions as well as
 *     the Jahia Sustainable Enterprise License (JSEL).
 *
 *     For questions regarding licensing, support, production usage...
 *     please contact our team at sales@jahia.com or go to http://www.jahia.com/license.
 *
 * ==========================================================================================
 */
package org.jahia.modules.reports.bean;

import org.jahia.exceptions.JahiaException;
import org.jahia.modules.reports.service.ConditionService;
import org.jahia.modules.reports.service.ExpiredConditionService;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.visibility.VisibilityService;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The ReportByExpiredContent class
 *
 * @author nonico
 */
public class ReportByExpiredContent extends QueryReport {
    private static Logger logger = LoggerFactory.getLogger(ReportByExpiredContent.class);
    private ConditionService conditionService;
    private long totalContent;
    private String searchPath;
    private Set<String> seenNodes;


    public ReportByExpiredContent(JCRSiteNode siteNode, String searchPath) {
        super(siteNode);
        this.searchPath = searchPath;
        this.conditionService = new ExpiredConditionService();
        seenNodes = new HashSet<>();
    }

    @Override public void execute(JCRSessionWrapper session, int offset, int limit)
            throws RepositoryException, JSONException, JahiaException {
        logger.debug("Building jcr sql query");
        String query = "SELECT * FROM [jnt:content] AS parent \n"
                + "INNER JOIN [jnt:conditionalVisibility] as child ON ISCHILDNODE(child,parent) \n"
                + "INNER JOIN [jnt:startEndDateCondition] as condition ON ISCHILDNODE(condition,child) \n"
                + "WHERE ISDESCENDANTNODE(parent,['" + searchPath + "']) \n"
                + "AND condition.end < CAST('" + LocalDateTime.now().toString() + "' AS DATE)\n"
                + "ORDER BY parent.Name";
        logger.debug(query);
        fillReport(session, query, offset, limit);
        totalContent = getTotalCount(session, query);
    }

    @Override public void addItem(JCRNodeWrapper node) throws RepositoryException {

        Map<String, String> expiredConditions = conditionService.getConditions(node);
        if (expiredConditions.size() == 1 && !seenNodes.contains(node.getName())) {
            Map<String, String> map = new HashMap<>();
            map.put("name", node.getName());
            map.put("path", node.getParent().getPath());
            map.put("type", String.join("<br/>", node.getNodeTypes()));
            map.put("expiresOn", expiredConditions.values().iterator().next());
            this.dataList.add(map);
            this.seenNodes.add(node.getName());
        }
    }
    @Override public JSONObject getJson() throws JSONException, RepositoryException {

        JSONObject jsonObject = new JSONObject();
        JSONArray jArray = new JSONArray();

        for (Map<String, String> nodeMap : this.dataList) {
            JSONArray item = new JSONArray();
            item.put(nodeMap.get("name"));
            item.put(nodeMap.get("path"));
            item.put(nodeMap.get("type"));
            item.put(nodeMap.get("expiresOn"));
            jArray.put(item);
        }

        jsonObject.put("recordsTotal", totalContent);
        jsonObject.put("recordsFiltered", totalContent);
        jsonObject.put("siteName", siteNode.getName());
        jsonObject.put("siteDisplayableName", siteNode.getDisplayableName());
        jsonObject.put("data", jArray);
        return jsonObject;
    }
}
