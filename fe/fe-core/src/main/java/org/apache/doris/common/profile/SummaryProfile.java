// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.common.profile;

import org.apache.doris.common.util.RuntimeProfile;
import org.apache.doris.common.util.TimeUtils;
import org.apache.doris.thrift.TUnit;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * SummaryProfile is part of a query profile.
 * It contains the summary information of a query.
 */
public class SummaryProfile {
    // Summary
    public static final String PROFILE_ID = "Profile ID";
    public static final String TASK_TYPE = "Task Type";
    public static final String START_TIME = "Start Time";
    public static final String END_TIME = "End Time";
    public static final String TOTAL_TIME = "Total";
    public static final String TASK_STATE = "Task State";
    public static final String USER = "User";
    public static final String DEFAULT_DB = "Default Db";
    public static final String SQL_STATEMENT = "Sql Statement";
    public static final String IS_CACHED = "Is Cached";
    public static final String IS_NEREIDS = "Is Nereids";
    public static final String IS_PIPELINE = "Is Pipeline";
    public static final String TOTAL_INSTANCES_NUM = "Total Instances Num";
    public static final String INSTANCES_NUM_PER_BE = "Instances Num Per BE";
    public static final String PARALLEL_FRAGMENT_EXEC_INSTANCE = "Parallel Fragment Exec Instance Num";
    public static final String TRACE_ID = "Trace ID";

    // Execution  Summary
    public static final String ANALYSIS_TIME = "Analysis Time";
    public static final String JOIN_REORDER_TIME = "JoinReorder Time";
    public static final String CREATE_SINGLE_NODE_TIME = "CreateSingleNode Time";
    public static final String QUERY_DISTRIBUTED_TIME = "QueryDistributed Time";
    public static final String PLAN_TIME = "Plan Time";
    public static final String SCHEDULE_TIME = "Schedule Time";
    public static final String FETCH_RESULT_TIME = "Fetch Result Time";
    public static final String WRITE_RESULT_TIME = "Write Result Time";
    public static final String WAIT_FETCH_RESULT_TIME = "Wait and Fetch Result Time";

    public static final ImmutableList<String> SUMMARY_KEYS = ImmutableList.of(PROFILE_ID, TASK_TYPE,
            START_TIME, END_TIME, TOTAL_TIME, TASK_STATE, USER, DEFAULT_DB, SQL_STATEMENT, IS_NEREIDS, IS_PIPELINE,
            IS_CACHED, TOTAL_INSTANCES_NUM, INSTANCES_NUM_PER_BE, PARALLEL_FRAGMENT_EXEC_INSTANCE, TRACE_ID);

    public static final ImmutableList<String> EXECUTION_SUMMARY_KEYS = ImmutableList.of(ANALYSIS_TIME, PLAN_TIME,
            SCHEDULE_TIME, FETCH_RESULT_TIME, WRITE_RESULT_TIME, WAIT_FETCH_RESULT_TIME);

    private RuntimeProfile summaryProfile;
    private RuntimeProfile executionSummaryProfile;

    // timestamp of query begin
    private long queryBeginTime = -1;
    // Analysis end time
    private long queryAnalysisFinishTime = -1;
    // Join reorder end time
    private long queryJoinReorderFinishTime = -1;
    // Create single node plan end time
    private long queryCreateSingleNodeFinishTime = -1;
    // Create distribute plan end time
    private long queryDistributedFinishTime = -1;
    // Plan end time
    private long queryPlanFinishTime = -1;
    // Fragment schedule and send end time
    private long queryScheduleFinishTime = -1;
    // Query result fetch end time
    private long queryFetchResultFinishTime = -1;
    private long tempStarTime = -1;
    private long queryFetchResultConsumeTime = 0;
    private long queryWriteResultConsumeTime = 0;

    public SummaryProfile(RuntimeProfile rootProfile) {
        summaryProfile = new RuntimeProfile("Summary");
        executionSummaryProfile = new RuntimeProfile("Execution Summary");
        init();
        rootProfile.addChild(summaryProfile);
        rootProfile.addChild(executionSummaryProfile);
    }

    private void init() {
        for (String key : SUMMARY_KEYS) {
            summaryProfile.addInfoString(key, "N/A");
        }
        for (String key : EXECUTION_SUMMARY_KEYS) {
            executionSummaryProfile.addInfoString(key, "N/A");
        }
    }

    public void update(Map<String, String> summaryInfo) {
        updateSummaryProfile(summaryInfo);
        updateExecutionSummaryProfile();
    }

    private void updateSummaryProfile(Map<String, String> infos) {
        for (String key : infos.keySet()) {
            if (SUMMARY_KEYS.contains(key)) {
                summaryProfile.addInfoString(key, infos.get(key));
            }
        }
    }

    private void updateExecutionSummaryProfile() {
        executionSummaryProfile.addInfoString(ANALYSIS_TIME, getPrettyQueryAnalysisFinishTime());
        executionSummaryProfile.addInfoString(JOIN_REORDER_TIME, getPrettyQueryJoinReorderFinishTime());
        executionSummaryProfile.addInfoString(CREATE_SINGLE_NODE_TIME, getPrettyCreateSingleNodeFinishTime());
        executionSummaryProfile.addInfoString(QUERY_DISTRIBUTED_TIME, getPrettyQueryDistributedFinishTime());
        executionSummaryProfile.addInfoString(PLAN_TIME, getPrettyQueryPlanFinishTime());
        executionSummaryProfile.addInfoString(SCHEDULE_TIME, getPrettyQueryScheduleFinishTime());
        executionSummaryProfile.addInfoString(FETCH_RESULT_TIME,
                RuntimeProfile.printCounter(queryFetchResultConsumeTime, TUnit.TIME_MS));
        executionSummaryProfile.addInfoString(WRITE_RESULT_TIME,
                RuntimeProfile.printCounter(queryWriteResultConsumeTime, TUnit.TIME_MS));
        executionSummaryProfile.addInfoString(WAIT_FETCH_RESULT_TIME, getPrettyQueryFetchResultFinishTime());
    }

    public void setQueryBeginTime() {
        this.queryBeginTime = TimeUtils.getStartTimeMs();
    }

    public void setQueryAnalysisFinishTime() {
        this.queryAnalysisFinishTime = TimeUtils.getStartTimeMs();
    }

    public void setQueryJoinReorderFinishTime() {
        this.queryJoinReorderFinishTime = TimeUtils.getStartTimeMs();
    }

    public void setCreateSingleNodeFinishTime() {
        this.queryCreateSingleNodeFinishTime = TimeUtils.getStartTimeMs();
    }

    public void setQueryDistributedFinishTime() {
        this.queryDistributedFinishTime = TimeUtils.getStartTimeMs();
    }

    public void setQueryPlanFinishTime() {
        this.queryPlanFinishTime = TimeUtils.getStartTimeMs();
    }

    public void setQueryScheduleFinishTime() {
        this.queryScheduleFinishTime = TimeUtils.getStartTimeMs();
    }

    public void setQueryFetchResultFinishTime() {
        this.queryFetchResultFinishTime = TimeUtils.getStartTimeMs();
    }

    public void setTempStartTime() {
        this.tempStarTime = TimeUtils.getStartTimeMs();
    }

    public void freshFetchResultConsumeTime() {
        this.queryFetchResultConsumeTime += TimeUtils.getStartTimeMs() - tempStarTime;
    }

    public void freshWriteResultConsumeTime() {
        this.queryWriteResultConsumeTime += TimeUtils.getStartTimeMs() - tempStarTime;
    }

    public long getQueryBeginTime() {
        return queryBeginTime;
    }

    public static class SummaryBuilder {
        private Map<String, String> map = Maps.newHashMap();

        public SummaryBuilder profileId(String val) {
            map.put(PROFILE_ID, val);
            return this;
        }

        public SummaryBuilder taskType(String val) {
            map.put(TASK_TYPE, val);
            return this;
        }

        public SummaryBuilder startTime(String val) {
            map.put(START_TIME, val);
            return this;
        }

        public SummaryBuilder endTime(String val) {
            map.put(END_TIME, val);
            return this;
        }

        public SummaryBuilder totalTime(String val) {
            map.put(TOTAL_TIME, val);
            return this;
        }

        public SummaryBuilder taskState(String val) {
            map.put(TASK_STATE, val);
            return this;
        }

        public SummaryBuilder user(String val) {
            map.put(USER, val);
            return this;
        }

        public SummaryBuilder defaultDb(String val) {
            map.put(DEFAULT_DB, val);
            return this;
        }

        public SummaryBuilder sqlStatement(String val) {
            map.put(SQL_STATEMENT, val);
            return this;
        }

        public SummaryBuilder isCached(String val) {
            map.put(IS_CACHED, val);
            return this;
        }

        public SummaryBuilder totalInstancesNum(String val) {
            map.put(TOTAL_INSTANCES_NUM, val);
            return this;
        }

        public SummaryBuilder instancesNumPerBe(String val) {
            map.put(INSTANCES_NUM_PER_BE, val);
            return this;
        }

        public SummaryBuilder parallelFragmentExecInstance(String val) {
            map.put(PARALLEL_FRAGMENT_EXEC_INSTANCE, val);
            return this;
        }

        public SummaryBuilder traceId(String val) {
            map.put(TRACE_ID, val);
            return this;
        }

        public SummaryBuilder isNereids(String isNereids) {
            map.put(IS_NEREIDS, isNereids);
            return this;
        }

        public SummaryBuilder isPipeline(String isPipeline) {
            map.put(IS_PIPELINE, isPipeline);
            return this;
        }

        public Map<String, String> build() {
            return map;
        }
    }

    private String getPrettyQueryAnalysisFinishTime() {
        if (queryBeginTime == -1 || queryAnalysisFinishTime == -1) {
            return "N/A";
        }
        return RuntimeProfile.printCounter(queryAnalysisFinishTime - queryBeginTime, TUnit.TIME_MS);
    }

    private String getPrettyQueryJoinReorderFinishTime() {
        if (queryAnalysisFinishTime == -1 || queryJoinReorderFinishTime == -1) {
            return "N/A";
        }
        return RuntimeProfile.printCounter(queryJoinReorderFinishTime - queryAnalysisFinishTime, TUnit.TIME_MS);
    }

    private String getPrettyCreateSingleNodeFinishTime() {
        if (queryJoinReorderFinishTime == -1 || queryCreateSingleNodeFinishTime == -1) {
            return "N/A";
        }
        return RuntimeProfile.printCounter(queryCreateSingleNodeFinishTime - queryJoinReorderFinishTime, TUnit.TIME_MS);
    }

    private String getPrettyQueryDistributedFinishTime() {
        if (queryCreateSingleNodeFinishTime == -1 || queryDistributedFinishTime == -1) {
            return "N/A";
        }
        return RuntimeProfile.printCounter(queryDistributedFinishTime - queryCreateSingleNodeFinishTime, TUnit.TIME_MS);
    }

    private String getPrettyQueryPlanFinishTime() {
        if (queryAnalysisFinishTime == -1 || queryPlanFinishTime == -1) {
            return "N/A";
        }
        return RuntimeProfile.printCounter(queryPlanFinishTime - queryAnalysisFinishTime, TUnit.TIME_MS);
    }

    private String getPrettyQueryScheduleFinishTime() {
        if (queryPlanFinishTime == -1 || queryScheduleFinishTime == -1) {
            return "N/A";
        }
        return RuntimeProfile.printCounter(queryScheduleFinishTime - queryPlanFinishTime, TUnit.TIME_MS);
    }

    private String getPrettyQueryFetchResultFinishTime() {
        if (queryScheduleFinishTime == -1 || queryFetchResultFinishTime == -1) {
            return "N/A";
        }
        return RuntimeProfile.printCounter(queryFetchResultFinishTime - queryScheduleFinishTime, TUnit.TIME_MS);
    }
}
