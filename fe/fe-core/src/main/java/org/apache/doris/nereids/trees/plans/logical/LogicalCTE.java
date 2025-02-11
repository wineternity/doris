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

package org.apache.doris.nereids.trees.plans.logical;

import org.apache.doris.nereids.memo.GroupExpression;
import org.apache.doris.nereids.properties.LogicalProperties;
import org.apache.doris.nereids.trees.expressions.CTEId;
import org.apache.doris.nereids.trees.expressions.Expression;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.PlanType;
import org.apache.doris.nereids.trees.plans.visitor.PlanVisitor;
import org.apache.doris.nereids.util.Utils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Logical Node for CTE
 */
public class LogicalCTE<CHILD_TYPE extends Plan> extends LogicalUnary<CHILD_TYPE> {

    private final List<LogicalSubQueryAlias<Plan>> aliasQueries;

    private final Map<String, CTEId> cteNameToId;

    private final boolean registered;

    public LogicalCTE(List<LogicalSubQueryAlias<Plan>> aliasQueries, CHILD_TYPE child) {
        this(aliasQueries, Optional.empty(), Optional.empty(), child, false, null);
    }

    public LogicalCTE(List<LogicalSubQueryAlias<Plan>> aliasQueries, CHILD_TYPE child, boolean registered,
            Map<String, CTEId> cteNameToId) {
        this(aliasQueries, Optional.empty(), Optional.empty(), child, registered,
                cteNameToId);
    }

    public LogicalCTE(List<LogicalSubQueryAlias<Plan>> aliasQueries, Optional<GroupExpression> groupExpression,
            Optional<LogicalProperties> logicalProperties, CHILD_TYPE child,
            boolean registered, Map<String, CTEId> cteNameToId) {
        super(PlanType.LOGICAL_CTE, groupExpression, logicalProperties, child);
        this.aliasQueries = ImmutableList.copyOf(Objects.requireNonNull(aliasQueries, "aliasQueries can not be null"));
        this.registered = registered;
        this.cteNameToId = cteNameToId == null ? ImmutableMap.copyOf(initCTEId()) : cteNameToId;
    }

    private Map<String, CTEId> initCTEId() {
        Map<String, CTEId> subQueryAliasToUniqueId = new HashMap<>();
        for (LogicalSubQueryAlias<Plan> subQueryAlias : aliasQueries) {
            subQueryAliasToUniqueId.put(subQueryAlias.getAlias(), subQueryAlias.getCteId());
        }
        return subQueryAliasToUniqueId;
    }

    public List<LogicalSubQueryAlias<Plan>> getAliasQueries() {
        return aliasQueries;
    }

    @Override
    public List<Plan> extraPlans() {
        return (List) aliasQueries;
    }

    /**
     * In fact, the action of LogicalCTE is to store and register with clauses, and this logical node will be
     * eliminated immediately after finishing the process of with-clause registry; This process is executed before
     * all the other analyze and optimize rules, so this function will not be called.
     */
    @Override
    public List<Slot> computeOutput() {
        return child().getOutput();
    }

    @Override
    public String toString() {
        return Utils.toSqlString("LogicalCTE",
                "aliasQueries", aliasQueries
        );
    }

    @Override
    public boolean displayExtraPlanFirst() {
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LogicalCTE that = (LogicalCTE) o;
        return aliasQueries.equals(that.aliasQueries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(aliasQueries);
    }

    @Override
    public Plan withChildren(List<Plan> children) {
        Preconditions.checkArgument(aliasQueries.size() > 0);
        return new LogicalCTE<>(aliasQueries, children.get(0), registered, cteNameToId);
    }

    @Override
    public <R, C> R accept(PlanVisitor<R, C> visitor, C context) {
        return visitor.visitLogicalCTE(this, context);
    }

    @Override
    public List<Expression> getExpressions() {
        return ImmutableList.of();
    }

    @Override
    public LogicalCTE<CHILD_TYPE> withGroupExpression(Optional<GroupExpression> groupExpression) {
        return new LogicalCTE<>(aliasQueries, groupExpression, Optional.of(getLogicalProperties()), child(),
                registered, cteNameToId);
    }

    @Override
    public LogicalCTE<CHILD_TYPE> withLogicalProperties(Optional<LogicalProperties> logicalProperties) {
        return new LogicalCTE<>(aliasQueries, Optional.empty(), logicalProperties, child(), registered,
                cteNameToId);
    }

    @Override
    public Plan withGroupExprLogicalPropChildren(Optional<GroupExpression> groupExpression,
            Optional<LogicalProperties> logicalProperties, List<Plan> children) {
        return new LogicalCTE<>(aliasQueries, groupExpression, logicalProperties, children.get(0),
                registered, cteNameToId);
    }

    public boolean isRegistered() {
        return registered;
    }

    public CTEId findCTEId(String subQueryAlias) {
        CTEId id = cteNameToId.get(subQueryAlias);
        Preconditions.checkArgument(id != null, "Cannot find id for sub-query : %s",
                subQueryAlias);
        return id;
    }

    public Map<String, CTEId> getCteNameToId() {
        return cteNameToId;
    }
}
