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

package org.apache.doris.ldap;

import org.apache.doris.analysis.ResourcePattern;
import org.apache.doris.analysis.TablePattern;
import org.apache.doris.analysis.UserIdentity;
import org.apache.doris.catalog.Env;
import org.apache.doris.common.AnalysisException;
import org.apache.doris.common.LdapConfig;
import org.apache.doris.datasource.InternalCatalog;
import org.apache.doris.mysql.privilege.Auth;
import org.apache.doris.mysql.privilege.PrivBitSet;
import org.apache.doris.mysql.privilege.PrivPredicate;
import org.apache.doris.mysql.privilege.Privilege;
import org.apache.doris.mysql.privilege.Role;
import org.apache.doris.qe.ConnectContext;
import org.apache.doris.qe.SessionVariable;

import com.google.common.collect.Lists;
import mockit.Expectations;
import mockit.Mocked;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

public class LdapPrivsCheckerTest {
    private static final String CLUSTER = "default_cluster";
    private static final String INTERNAL = InternalCatalog.INTERNAL_CATALOG_NAME;
    private static final String DB = "palodb";
    private static final String TABLE_DB = "tabledb";
    private static final String TABLE1 = "table1";
    private static final String TABLE2 = "table2";
    private static final String RESOURCE1 = "spark_resource";
    private static final String RESOURCE2 = "resource";
    private static final String USER = "default_cluster:zhangsan";
    private static final String IP = "192.168.0.1";
    private UserIdentity userIdent = UserIdentity.createAnalyzedUserIdentWithIp(USER, IP);

    @Mocked
    private ConnectContext context;

    @Mocked
    private SessionVariable sessionVariable;

    @Mocked
    private Env env;

    @Mocked
    private Auth auth;

    @Mocked
    private LdapManager ldapManager;

    @Before
    public void setUp() {
        LdapConfig.ldap_authentication_enabled = true;
        new Expectations() {
            {
                ConnectContext.get();
                minTimes = 0;
                result = context;

                Env.getCurrentEnv();
                minTimes = 0;
                result = env;

                env.getAuth();
                minTimes = 0;
                result = auth;

                auth.getLdapManager();
                minTimes = 0;
                result = ldapManager;

                Role role = new Role("ldapRole");
                Map<TablePattern, PrivBitSet> tblPatternToPrivs = role.getTblPatternToPrivs();

                TablePattern global = new TablePattern("ctl1", "*", "*");
                tblPatternToPrivs.put(global, PrivBitSet.of(Privilege.SELECT_PRIV, Privilege.CREATE_PRIV));
                TablePattern db = new TablePattern(INTERNAL, DB, "*");
                tblPatternToPrivs.put(db, PrivBitSet.of(Privilege.SELECT_PRIV, Privilege.LOAD_PRIV));
                TablePattern tbl1 = new TablePattern(INTERNAL, TABLE_DB, TABLE1);
                tblPatternToPrivs.put(tbl1, PrivBitSet.of(Privilege.SELECT_PRIV, Privilege.ALTER_PRIV));
                TablePattern tbl2 = new TablePattern(INTERNAL, TABLE_DB, TABLE2);
                tblPatternToPrivs.put(tbl2, PrivBitSet.of(Privilege.SELECT_PRIV, Privilege.DROP_PRIV));

                Map<ResourcePattern, PrivBitSet> resourcePatternToPrivs = role.getResourcePatternToPrivs();
                ResourcePattern resource1 = new ResourcePattern(RESOURCE1);
                resourcePatternToPrivs.put(resource1, PrivBitSet.of(Privilege.USAGE_PRIV));
                ResourcePattern resource2 = new ResourcePattern(RESOURCE1);
                resourcePatternToPrivs.put(resource2, PrivBitSet.of(Privilege.USAGE_PRIV));
                Role ldapRole = new Role(role.getRoleName());
                try {
                    global.analyze(CLUSTER);
                    db.analyze(CLUSTER);
                    tbl1.analyze(CLUSTER);
                    tbl2.analyze(CLUSTER);
                    resource1.analyze();
                    resource2.analyze();
                    ldapRole.merge(role);
                } catch (Exception e) {
                    e.printStackTrace();
                }

                UserIdentity userIdentity = UserIdentity.createAnalyzedUserIdentWithIp(USER, IP);

                ldapManager.getUserInfo(userIdentity.getQualifiedUser());
                minTimes = 0;
                result = new LdapUserInfo(userIdentity.getQualifiedUser(), false, "", ldapRole);

                ldapManager.doesUserExist(userIdentity.getQualifiedUser());
                minTimes = 0;
                result = true;

                ldapManager.getUserRole(USER);
                minTimes = 0;
                result = ldapRole;

                context.getCurrentUserIdentity();
                minTimes = 0;
                result = userIdentity;

                context.getSessionVariable();
                minTimes = 0;
                result = sessionVariable;

                auth.getUserIdentityForLdap(USER, IP);
                minTimes = 0;
                result = Lists.newArrayList(userIdentity);
            }
        };
        // call the mocked method before replay
        // for there is exception in tests: Missing 1 invocation to: org.apache.doris.qe.ConnectContext#get()
        ConnectContext.get().getSessionVariable().isEnableUnicodeNameSupport();
    }

    @Test
    public void testHasDbPrivFromLdap() {
        Assert.assertTrue(
                LdapPrivsChecker.hasDbPrivFromLdap(userIdent, INTERNAL, CLUSTER + ":" + DB, PrivPredicate.LOAD));
        Assert.assertFalse(
                LdapPrivsChecker.hasDbPrivFromLdap(userIdent, INTERNAL, CLUSTER + ":" + DB, PrivPredicate.DROP));
    }

    @Test
    public void testHasTblPrivFromLdap() {
        Assert.assertTrue(LdapPrivsChecker.hasTblPrivFromLdap(userIdent, INTERNAL, CLUSTER + ":" + TABLE_DB, TABLE1,
                PrivPredicate.ALTER));
        Assert.assertFalse(LdapPrivsChecker.hasTblPrivFromLdap(userIdent, INTERNAL, CLUSTER + ":" + TABLE_DB, TABLE1,
                PrivPredicate.DROP));
        Assert.assertTrue(LdapPrivsChecker.hasTblPrivFromLdap(userIdent, INTERNAL, CLUSTER + ":" + TABLE_DB, TABLE2,
                PrivPredicate.DROP));
        Assert.assertFalse(LdapPrivsChecker.hasTblPrivFromLdap(userIdent, INTERNAL, CLUSTER + ":" + TABLE_DB, TABLE2,
                PrivPredicate.CREATE));
    }

    @Test
    public void testHasResourcePrivFromLdap() {
        Assert.assertTrue(LdapPrivsChecker.hasResourcePrivFromLdap(userIdent, RESOURCE1, PrivPredicate.USAGE));
        Assert.assertFalse(LdapPrivsChecker.hasResourcePrivFromLdap(userIdent, "resource",
                PrivPredicate.USAGE));
    }

    @Test
    public void testGetResourcePrivFromLdap() {
        Assert.assertTrue(
                LdapPrivsChecker.getResourcePrivFromLdap(userIdent, RESOURCE1).satisfy(PrivPredicate.USAGE));
    }

    @Test
    public void testGetLdapAllDbPrivs() throws AnalysisException {
        Map<TablePattern, PrivBitSet> allDb = LdapPrivsChecker.getLdapAllDbPrivs(userIdent);
        TablePattern db = new TablePattern(DB, "*");
        db.analyze(CLUSTER);
        Assert.assertEquals(PrivBitSet.of(Privilege.SELECT_PRIV, Privilege.LOAD_PRIV).toString(),
                allDb.get(db).toString());
    }

    @Test
    public void testGetLdapAllTblPrivs() throws AnalysisException {
        Map<TablePattern, PrivBitSet> allTbl = LdapPrivsChecker.getLdapAllTblPrivs(userIdent);
        TablePattern tbl1 = new TablePattern(TABLE_DB, TABLE1);
        TablePattern tbl2 = new TablePattern(TABLE_DB, TABLE2);
        tbl1.analyze(CLUSTER);
        tbl2.analyze(CLUSTER);
        Assert.assertEquals(PrivBitSet.of(Privilege.SELECT_PRIV, Privilege.ALTER_PRIV).toString(),
                allTbl.get(tbl1).toString());
        Assert.assertEquals(PrivBitSet.of(Privilege.SELECT_PRIV, Privilege.DROP_PRIV).toString(),
                allTbl.get(tbl2).toString());
    }

    @Test
    public void testGetLdapAllResourcePrivs() {
        Map<ResourcePattern, PrivBitSet> allResource = LdapPrivsChecker.getLdapAllResourcePrivs(userIdent);
        ResourcePattern resource1 = new ResourcePattern(RESOURCE1);
        ResourcePattern resource2 = new ResourcePattern(RESOURCE1);
        Assert.assertEquals(PrivBitSet.of(Privilege.USAGE_PRIV).toString(), allResource.get(resource1).toString());
        Assert.assertEquals(PrivBitSet.of(Privilege.USAGE_PRIV).toString(), allResource.get(resource2).toString());
    }
}
