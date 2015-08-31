/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.integration.test.workflow.mgt;

import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.carbon.automation.engine.context.AutomationContext;
import org.wso2.carbon.automation.engine.context.TestUserMode;
import org.wso2.carbon.automation.extensions.servers.carbonserver.MultipleServersManager;
import org.wso2.carbon.identity.workflow.mgt.stub.bean.AssociationDTO;
import org.wso2.carbon.identity.workflow.mgt.stub.bean.BPSProfileDTO;
import org.wso2.carbon.identity.workflow.mgt.stub.bean.ParameterDTO;
import org.wso2.carbon.identity.workflow.mgt.stub.bean.WorkflowDTO;
import org.wso2.carbon.integration.common.admin.client.AuthenticatorClient;
import org.wso2.carbon.um.ws.api.stub.ClaimValue;
import org.wso2.carbon.um.ws.api.stub.PermissionDTO;
import org.wso2.identity.integration.common.clients.usermgt.remote.RemoteUserStoreManagerServiceClient;
import org.wso2.identity.integration.common.clients.workflow.mgt.WorkflowAdminClient;
import org.wso2.identity.integration.common.utils.CarbonTestServerManager;
import org.wso2.identity.integration.common.utils.ISIntegrationTest;
import org.wso2.identity.integration.test.utils.WorkflowConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowManagementTestCase extends ISIntegrationTest {

    private WorkflowAdminClient client;
    private RemoteUserStoreManagerServiceClient usmClient;
    public MultipleServersManager manager = new MultipleServersManager();


    private String addUserWorkflowName = "TestWorkflowAddUser1";
    private String workflowId = null;
    private String associationId = null;
    private String[] rolesToAdd = {"wfRole1", "wfRole2", "wfRole3"};
    private String sessionCookie2;
    private String servicesUrl = "https://localhost:9444/services/";

    @BeforeClass(alwaysRun = true)
    public void testInit() throws Exception {

        super.init();
        ConfigurationContext configContext = ConfigurationContextFactory
                .createConfigurationContextFromFileSystem(null
                        , null);
        startOtherCarbonServers();
        client = new WorkflowAdminClient(sessionCookie2, servicesUrl, configContext);
        usmClient = new RemoteUserStoreManagerServiceClient(servicesUrl, sessionCookie2);
        for (String role : rolesToAdd) {
            usmClient.addRole(role, new String[0], new PermissionDTO[0]);
        }

    }

    /**
     * Start additional carbon servers
     *
     * @throws Exception
     */
    private void startOtherCarbonServers() throws Exception {

        Map<String, String> startupParameterMap1 = new HashMap<String, String>();
        startupParameterMap1.put("-DportOffset", "1");
        startupParameterMap1.put("-Dprofile", WorkflowConstants.WORKFLOW_PROFILE);

        AutomationContext context1 = new AutomationContext("IDENTITY", "identity002", TestUserMode.SUPER_TENANT_ADMIN);
        CarbonTestServerManager server1 = new CarbonTestServerManager(context1, System.getProperty("carbon.zip"),
                startupParameterMap1);
        manager.startServers(server1);
        AuthenticatorClient authenticatorClient = new AuthenticatorClient(servicesUrl);
        Thread.sleep(2500);

        sessionCookie2 = authenticatorClient.login("admin", "admin", "localhost");
    }

    @AfterClass(alwaysRun = true)
    public void atEnd() throws Exception {

        for (String role : rolesToAdd) {
            usmClient.deleteRole(role);
        }
        client = null;
        usmClient = null;
        manager.stopAllServers();
    }

    @Test(alwaysRun = true, description = "Testing adding a BPS Profile")
    public void testAddBPSProfile() {

        String profileName = "TestBPSProfile";
        String host = "https://localhost:9444";
        String user = "admin";
        String userPassword = "admin";
        try {
            BPSProfileDTO bpsProfileDTO = new BPSProfileDTO();
            bpsProfileDTO.setProfileName(profileName);
            bpsProfileDTO.setHost(host);
            bpsProfileDTO.setUsername(user);
            bpsProfileDTO.setPassword(userPassword);
            bpsProfileDTO.setCallbackUser(user);
            bpsProfileDTO.setCallbackPassword(userPassword);
            client.addBPSProfile(bpsProfileDTO);
            BPSProfileDTO[] bpsProfiles = client.listBPSProfiles();
            if (bpsProfiles == null || bpsProfiles.length == 0) {
                Assert.fail("BPS Profile list is empty, profile adding has been failed");
            }
            boolean added = false;
            for (BPSProfileDTO bpsProfile : bpsProfiles) {
                if (profileName.equals(bpsProfile.getProfileName())) {
                    added = true;
                    break;
                }
            }
            Assert.assertTrue(added, "Failed to add profile");
        } catch (Exception e) {
            Assert.fail("Error while adding the BPS profile", e);
        }
    }

    @Test(alwaysRun = true, description = "Testing adding a BPS Profile with existing name", dependsOnMethods =
            "testAddBPSProfile", expectedExceptions = AxisFault.class)
    public void testAddDuplicateBPSProfile() throws Exception {

        String profileName = "TestBPSProfile";
        String host = "https://localhost:9445";
        String user = "testUser";
        String userPassword = "testPassword";BPSProfileDTO bpsProfileDTO = new BPSProfileDTO();
        bpsProfileDTO.setProfileName(profileName);
        bpsProfileDTO.setHost(host);
        bpsProfileDTO.setUsername(user);
        bpsProfileDTO.setPassword(userPassword);
        bpsProfileDTO.setCallbackUser(user);
        bpsProfileDTO.setCallbackPassword(userPassword);
        client.addBPSProfile(bpsProfileDTO);
    }

    @Test(alwaysRun = true, description = "Testing adding a new Workflow", dependsOnMethods = "testAddBPSProfile")
    public void testAddWorkflow() {

        String workflowDescription = "TestWorkflowDescription";
        try {
            WorkflowDTO workflowDTO = getWorkflowDTO(addUserWorkflowName, workflowDescription);


            client.addWorkflow(workflowDTO, Collections.EMPTY_LIST, getTemplateImplParams());

            WorkflowDTO[] workflows = client.listWorkflows();
            if (workflows == null || workflows.length == 0) {
                Assert.fail("Workflow list is empty, new workflow is not added");
            }
            boolean added = false;
            for (WorkflowDTO workflow : workflows) {
                if (addUserWorkflowName.equals(workflow.getWorkflowName()) && workflowDescription.equals(workflow
                        .getWorkflowDescription())) {
                    added = true;
                    workflowId = workflow.getWorkflowId();  //setting for future tests
                    break;
                }
            }
            Assert.assertTrue(added, "Failed to add workflow");
        } catch (Exception e) {
            Assert.fail("Error while adding the workflow", e);
        }
    }

    @Test(alwaysRun = true, description = "Testing adding a new association", dependsOnMethods = "testAddWorkflow")
    public void testAddAssociation() {

        String associationName = "TestAddUserAssociation";
        //the following xpath is valid for user with the role "wfRole1"
        String condition = "//*[local-name()='parameter'][@name='Roles']/*[local-name()='value']/*[local-name()" +
                "='itemValue']/text()='wfRole1'";
        if (workflowId == null) {
            Assert.fail("Workflow has not been added in the previous test");
        }
        try {
            client.addAssociation(workflowId, associationName, WorkflowConstants.ADD_USER_EVENT, condition);
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            if (associations == null || associations.length == 0) {
                Assert.fail("Association list is empty, new association is not added");
            }
            boolean added = false;
            for (AssociationDTO association : associations) {
                if (associationName.equals(association.getAssociationName())) {
                    added = true;
                    associationId = association.getAssociationId();
                    break;
                }
            }
            Assert.assertTrue(added, "Failed to add association");
        } catch (Exception e) {
            Assert.fail("Error while adding the association", e);
        }
    }

    @Test(alwaysRun = true, description = "Testing a added association where request matches condition",
            dependsOnMethods = "testAddAssociation")
    public void testAssociationForMatch() {
        String userName1 = "TestUser1ForSuccessAddUserWorkflow";
        String userName2 = "TestUser2ForSuccessAddUserWorkflow";
        try {

            usmClient.addUser(userName1, "test12345", new String[]{"wfRole1"}, new ClaimValue[0], null, false);
            Assert.assertFalse(usmClient.isExistingUser(userName1),
                    "User has been added, workflow has not been engaged");

            usmClient.addUser(userName2, "test12345", new String[]{"wfRole1", "wfRole2"}, new ClaimValue[0], null,
                    false);
            Assert.assertFalse(usmClient.isExistingUser(userName2),
                    "User has been added, workflow has not been engaged");
        } catch (Exception e) {
            log.error("Error occurred when adding test user, therefore ignoring testAssociation.", e);
        } finally {
            try {
                if (usmClient.isExistingUser(userName1)) {
                    usmClient.deleteUser(userName1);
                }
                if (usmClient.isExistingUser(userName2)) {
                    usmClient.deleteUser(userName2);
                }
            } catch (Exception e) {
                log.error("Error while removing added test users");
            }
        }
    }

    @Test(alwaysRun = true, description = "Testing a added association where the request doesn't match the condition",
            dependsOnMethods = "testAddAssociation")
    public void testAssociationForNonMatch() {
        String userName3 = "TestUser3ForAddUserWorkflow";
        String userName4 = "TestUser4ForAddUserWorkflow";
        String userName5 = "TestUser5ForAddUserWorkflow";
        try {
            //test when multiple roles present
            usmClient.addUser(userName3, "test12345", new String[]{"wfRole2", "wfRole3"}, new ClaimValue[0], null,
                    false);
            Assert.assertTrue(usmClient.isExistingUser(userName3),
                    "User has not been added, workflow has been engaged where it should have not");

            //test when single role is present
            usmClient.addUser(userName4, "test12345", new String[]{"wfRole3"}, new ClaimValue[0], null,
                    false);
            Assert.assertTrue(usmClient.isExistingUser(userName4),
                    "User has not been added, workflow has been engaged where it should have not");

            //test when no roles present
            usmClient.addUser(userName5, "test12345", new String[0], new ClaimValue[0], null,
                    false);
            Assert.assertTrue(usmClient.isExistingUser(userName5),
                    "User has not been added, workflow has been engaged where it should have not");
        } catch (Exception e) {
            log.error("Error occurred when adding test user, therefore ignoring testAssociation.", e);
        } finally {
            try {
                if (usmClient.isExistingUser(userName3)) {
                    usmClient.deleteUser(userName3);
                }
                if (usmClient.isExistingUser(userName4)) {
                    usmClient.deleteUser(userName4);
                }
                if (usmClient.isExistingUser(userName5)) {
                    usmClient.deleteUser(userName5);
                }
            } catch (Exception e) {
                log.error("Error while removing added test users");
            }
        }
    }

    @Test(alwaysRun = true, description = "Testing add user operation when workflows associated with it.",
            dependsOnMethods = "testAddAssociation")
    public void testAddUserOperation() {
        String userName6 = "TestUser6ForAddUserWorkflow";
        String userName7 = "TestUser7ForAddUserWorkflow";
        String roleName1 = "TestRole1ForAddUserWorkflow";
        try {

            usmClient.addUser(userName6, "test12345", new String[]{"wfRole1"}, new ClaimValue[0], null,
                    false);
        } catch (Exception e) {
            log.error("Error occurred when adding test user, therefore ignoring testAssociation.", e);
        }
        try {

            usmClient.addUser(userName6, "test12345", new String[]{"wfRole1"}, new ClaimValue[0], null,
                    false);
            Assert.fail("Since user with same name already in a workflow, operation should have failed.");
        } catch (Exception e) {
            //test passed
        }
        try {

            usmClient.addUser(userName6, "test12345", new String[]{}, new ClaimValue[0], null,
                    false);
            Assert.fail("Since user with same name already in a workflow, operation should have failed.");
        } catch (Exception e) {
            //test passed
        }
        try {
            client.addAssociation(workflowId, "TestDeleteRoleAssociation", WorkflowConstants.DELETE_ROLE_EVENT,
                    "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add deleteRole workflow.");
        }
        try {
            usmClient.addRole(roleName1, new String[0], new PermissionDTO[0]);
            usmClient.deleteRole(roleName1);
            Assert.assertTrue(usmClient.isExistingRole(roleName1), "Role should still exist in user store since " +
                    "workflow has not approved yet.");
        } catch (Exception e) {
            Assert.fail("Failed at triggering delete role workflow");
        }
        try {
            usmClient.addUser(userName7, "test12345", new String[]{roleName1}, new ClaimValue[0], null,
                    false);
            Assert.fail("Since role is in a delete workflow, operation should have failed.");
        } catch (Exception e) {
            // test passed
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestDeleteRoleAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
        }catch (Exception e) {
            log.error("Error while deleting deleteRole association at testAddUserOperation.");
        }
        try {
            usmClient.deleteRole(roleName1);
            Assert.assertFalse(usmClient.isExistingRole(roleName1), "Role should have deleted since association is " +
                    "removed");
        } catch (Exception e) {
            Assert.fail ("Error while deleting role where no associations of DELETE_ROLE exist.");
        }

    }

    @Test(alwaysRun = true, description = "Testing delete user operation when workflows associated with it.",
            dependsOnMethods = "testAddUserOperation")
    public void testDeleteUserOperation() {
        String userName1 = "TestUser1ForDeleteUserWorkflow";
        String userName2 = "TestUser2ForDeleteUserWorkflow";
        String userName3 = "TestUser3ForDeleteUserWorkflow";
        String userName4 = "TestUser4ForDeleteUserWorkflow";
        String userName5 = "TestUser5ForDeleteUserWorkflow";
        String roleName1 = "TestRole1ForDeleteUserWorkflow";
        String roleName2 = "TestRole2ForDeleteUserWorkflow";

        try {
            client.addAssociation(workflowId, "TestDeleteUserAssociation", WorkflowConstants.DELETE_USER_EVENT,
                    "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add deleteUser workflow.");
        }
        try {
            usmClient.addUser(userName1, "test12345", new String[]{""}, new ClaimValue[0], null, false);
            usmClient.addUser(userName2, "test12345", new String[]{""}, new ClaimValue[0], null, false);
            usmClient.addUser(userName3, "test12345", new String[]{""}, new ClaimValue[0], null, false);
            usmClient.addUser(userName4, "test12345", new String[]{""}, new ClaimValue[0], null, false);
            usmClient.addUser(userName5, "test12345", new String[]{""}, new ClaimValue[0], null, false);

        } catch (Exception e) {
            log.error("Error occurred when adding test user, therefore ignoring testAssociation.", e);
        }
        try {
            usmClient.deleteUser(userName1);
            Assert.assertTrue(usmClient.isExistingUser(userName1), "User should be still in the user store since " +
                    "workflow has not approved yet.");
        } catch (Exception e) {
            log.error("Error occurred when deleting test user, therefore ignoring testAssociation.", e);
        }
        try {
            usmClient.deleteUser(userName1);
            Assert.fail("Should throw an exception since there is already a pending workflow for deleting this user.");
        } catch (Exception e) {
            //test pass
        }

        try {
            client.addAssociation(workflowId, "TestAddRoleAssociation", WorkflowConstants.ADD_ROLE_EVENT,
                    "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add addRole workflow.");
        }
        try {
            usmClient.addRole(roleName1, new String[]{userName2}, new PermissionDTO[]{});
        } catch (Exception e) {
            Assert.fail("Failed to add role with user.");
        }
        try {
            usmClient.deleteUser(userName2);
            Assert.fail("Should throw an exception since there is a pending addRole workflow with this user.");
        } catch (Exception e) {
            //test pass
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestAddRoleAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error while deleting addRole association at testAddUserOperation.");
        }

        try {
            usmClient.addRole(roleName2, new String[]{userName2}, new PermissionDTO[]{});
            Assert.assertTrue(usmClient.isExistingRole(roleName2), "Role should be available in the user store.");
        } catch (Exception e) {
            Assert.fail("Failed to add role.");
        }
        try {
            client.addAssociation(workflowId, "TestUpdateUserListOfRoleAssociation", WorkflowConstants
                    .UPDATE_ROLE_USERS_EVENT, "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add updateUserListOfRole workflow.");
        }
        try {
            usmClient.updateUserListOfRole(roleName2, new String[]{userName3}, new String[]{});
        } catch (Exception e) {
            Assert.fail("Failed to update user list of role.");
        }
        try {
            usmClient.deleteUser(userName3);
            Assert.fail("Should throw an exception since there is a pending updateUserListOfRole workflow with this " +
                    "user.");
        } catch (Exception e) {
            //test pass
        }

        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestUpdateUserListOfRoleAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error while deleting updateUserListOfRole association at testAddUserOperation.");
        }

        try {
            client.addAssociation(workflowId, "TestUpdateRoleListOfUserAssociation", WorkflowConstants
                    .UPDATE_USER_ROLES_EVENT, "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add updateRoleListOfUser workflow.");
        }
        try {
            usmClient.updateRoleListOfUser(userName4, new String[]{roleName2}, new String[]{});
        } catch (Exception e) {
            Assert.fail("Failed to update user list of role.");
        }
        try {
            usmClient.deleteUser(userName4);
            Assert.fail("Should throw an exception since there is a pending updateRoleListOfUser workflow with this " +
                    "user.");
        } catch (Exception e) {
            //test pass
        }

        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestUpdateRoleListOfUserAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error while deleting updateRoleListOfUser association at testAddUserOperation.");
        }

        try {
            client.addAssociation(workflowId, "TestDeleteClaimOfUserAssociation", WorkflowConstants
                    .DELETE_USER_CLAIM_EVENT, "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add deleteClaimOfUser workflow.");
        }
        try {
            usmClient.deleteUserClaimValue(userName5, "wso2.org/testClaim", null);
        } catch (Exception e) {
            Assert.fail("Failed to delete claim of user");
        }
        try {
            usmClient.deleteUser(userName5);
            Assert.fail("Should throw an exception since there is a pending deleteClaimOfUser workflow with this user" +
                    ".");
        } catch (Exception e) {
            //test pass
        }

        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestDeleteUserAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                } else if ("TestDeleteClaimOfUserAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                }
            }
            usmClient.deleteUser(userName1);
            usmClient.deleteUser(userName2);
            usmClient.deleteUser(userName3);
            usmClient.deleteUser(userName4);
            usmClient.deleteUser(userName5);
        } catch (Exception e) {
            log.error("Error while clean up testDeleteUserOperation");
        }

    }

    @Test(alwaysRun = true, description = "Testing add role operation when workflows associated with it.",
            dependsOnMethods = "testDeleteUserOperation")
    public void testAddRoleOperation() {

        String roleName1 = "TestRole1ForAddRoleWorkflow";

        try {
            client.addAssociation(workflowId, "TestAddRoleAssociation", WorkflowConstants.ADD_ROLE_EVENT,
                    "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add addRole workflow.");
        }
        try {

            usmClient.addRole(roleName1, new String[]{}, new PermissionDTO[]{});
            Assert.assertFalse(usmClient.isExistingRole(roleName1), "Role should not exist in user store since " +
                    "workflow has not approved yet.");
        } catch (Exception e) {
            log.error("Error occurred when deleting test user, therefore ignoring testAssociation.", e);
        }
        try {
            usmClient.addRole(roleName1, new String[]{}, new PermissionDTO[]{});
            Assert.fail("Should throw an exception since there is already a pending workflow for adding this role.");
        } catch (Exception e) {
            //test pass
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestAddRoleAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error while clean up testDeleteUserOperation");
        }
    }

    @Test(alwaysRun = true, description = "Testing delete role operation when workflows associated with it.",
            dependsOnMethods = "testAddRoleOperation")
    public void testDeleteRoleOperation() {

        String roleName1 = "TestRole1ForDeleteRoleWorkflow";
        try {

            usmClient.addRole(roleName1, new String[]{}, new PermissionDTO[]{});
        } catch (Exception e) {
            log.error("Error occurred when adding test role, therefore ignoring testAssociation.", e);
        }
        try {
            client.addAssociation(workflowId, "TestDeleteRoleAssociation", WorkflowConstants.DELETE_ROLE_EVENT,
                    "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add deleteRole workflow.");
        }
        try {
            usmClient.deleteRole(roleName1);
            Assert.assertTrue(usmClient.isExistingRole(roleName1), "Role exist in user store since " +
                    "workflow has not approved yet.");
        } catch (Exception e) {
            log.error("Error occurred when deleting test user, therefore ignoring testAssociation.", e);
        }
        try {
            usmClient.deleteRole(roleName1);
            Assert.fail("Should throw an exception since there is already a pending workflow for deleting this role.");
        } catch (Exception e) {
            //test pass
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestDeleteRoleAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
            usmClient.deleteRole(roleName1);
        } catch (Exception e) {
            log.error("Error while clean up testDeleteUserOperation");
        }
    }

    @Test(alwaysRun = true, description = "Testing update user list of role operation when workflows associated with " +
            " it.", dependsOnMethods = "testDeleteRoleOperation")
    public void testUpdateUserListOfRoleOperation() {

        String userName1 = "TestUser1ForUpdateUserListOfRoleWorkflow";
        String roleName1 = "TestRole1ForUpdateUserListOfRoleWorkflow";
        try {

            usmClient.addRole(roleName1, new String[]{}, new PermissionDTO[]{});
            usmClient.addUser(userName1, "test12345", new String[]{""}, new ClaimValue[0], null, false);
        } catch (Exception e) {
            log.error("Error occurred when adding test user and role, therefore ignoring testAssociation.", e);
        }
        try {
            client.addAssociation(workflowId, "TestUpdateUserListOfRoleAssociation", WorkflowConstants
                    .UPDATE_ROLE_USERS_EVENT, "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add updateUserListOfRole workflow.");
        }
        try {
            client.addAssociation(workflowId, "TestDeleteUserAssociation", WorkflowConstants.DELETE_USER_EVENT,
                    "boolean(1)");
            usmClient.deleteUser(userName1);
        } catch (Exception e) {
            Assert.fail("failed to add deleteUser workflow.");
        }
        try {
            usmClient.updateUserListOfRole(roleName1, new String[]{userName1}, new String[]{});
            Assert.fail("Should throw an exception since there is a pending workflow for deleting username1.");
        } catch (Exception e) {
            //test pass
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestDeleteUserAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error while clean up testDeleteUserOperation");
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestUpdateUserListOfRoleAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
            usmClient.deleteRole(roleName1);
            usmClient.deleteUser(userName1);
        } catch (Exception e) {
            log.error("Error while clean up testUpdateUserListOfRole");
        }
    }

    @Test(alwaysRun = true, description = "Testing update role list of user operation when workflows associated with " +
            "it.", dependsOnMethods = "testUpdateUserListOfRoleOperation")
    public void testUpdateRoleListOfUserOperation() {

        String userName1 = "TestUser1ForUpdateRoleListOfUserWorkflow";
        String roleName1 = "TestRole1ForUpdateRoleListOfUserWorkflow";
        try {

            usmClient.addRole(roleName1, new String[]{}, new PermissionDTO[]{});
            usmClient.addUser(userName1, "test12345", new String[]{""}, new ClaimValue[0], null, false);
        } catch (Exception e) {
            log.error("Error occurred when adding test user and role, therefore ignoring testAssociation.", e);
        }
        try {
            client.addAssociation(workflowId, "TestUpdateRoleListOfUserAssociation", WorkflowConstants
                    .UPDATE_USER_ROLES_EVENT, "boolean(1)");
        } catch (Exception e) {
            Assert.fail("failed to add updateUserListOfRole workflow.");
        }
        try {
            client.addAssociation(workflowId, "TestDeleteUserAssociation", WorkflowConstants.DELETE_USER_EVENT,
                    "boolean(1)");
            usmClient.deleteUser(userName1);
        } catch (Exception e) {
            Assert.fail("failed to add deleteUser workflow.");
        }
        try {
            usmClient.updateUserListOfRole(userName1, new String[]{roleName1}, new String[]{});
            Assert.fail("Should throw an exception since there is a pending workflow for deleting username1.");
        } catch (Exception e) {
            //test pass
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestDeleteUserAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error while clean up testDeleteUserOperation");
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestUpdateRoleListOfUserAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
            usmClient.deleteRole(roleName1);
            usmClient.deleteUser(userName1);
        } catch (Exception e) {
            log.error("Error while clean up testUpdateRoleListOfUser");
        }
    }

    @Test(alwaysRun = true, description = "Testing update role name operation when workflows associated with  it.",
            dependsOnMethods = "testUpdateRoleListOfUserOperation")
    public void testUpdateRoleNameOperation() {

        String userName1 = "TestUser1ForUpdateRoleNameWorkflow";
        String roleName1 = "TestRole1ForUpdateRoleNameWorkflow";
        String roleName2 = "TestRole2ForUpdateRoleNameWorkflow";
        try {
            usmClient.addRole(roleName1, new String[]{}, new PermissionDTO[]{});
        } catch (Exception e) {

        }
        try {
            client.addAssociation(workflowId, "TestUpdateRoleNameAssociation", WorkflowConstants
                    .UPDATE_ROLE_NAME_EVENT, "boolean(1)");
            client.addAssociation(workflowId, "TestAddUserAssociationForRoleRename", WorkflowConstants
                    .ADD_USER_EVENT, "boolean(1)");
            usmClient.addUser(userName1, "passwd123", new String[]{roleName1}, new ClaimValue[0], null,
                    false);
        } catch (Exception e) {
            Assert.fail("failed to add updateRoleName workflow.");
        }
        try {
            usmClient.updateRoleName(roleName1,roleName2);
            Assert.fail("Should throw an exception since there is a pending workflow for adding a user with rolename1");
        } catch (Exception e) {
            //test pass
        }

        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestAddUserAssociationForRoleRename".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
            usmClient.deleteRole(roleName1);
        } catch (Exception e) {
            log.error("Error while clean up testUpdateRoleListOfUser");
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestUpdateRoleNameAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
            usmClient.deleteRole(roleName1);
        } catch (Exception e) {
            log.error("Error while clean up testUpdateRoleName");
        }
    }

    @Test(alwaysRun = true, description = "Testing update user claim operation when workflows associated with  it.",
            dependsOnMethods = "testUpdateRoleNameOperation")
    public void testUpdateUserClaimOperation() {

        String userName1 = "TestUser1ForDeleteClaimWorkflow";
        try {
            usmClient.addUser(userName1, "passwd123", new String[0], new ClaimValue[0], null,
                    false);
        } catch (Exception e) {

        }
        try {
            client.addAssociation(workflowId, "TestDeleteUserClaimValueAssociation", WorkflowConstants
                    .DELETE_USER_CLAIM_EVENT, "boolean(1)");
            client.addAssociation(workflowId, "TestDeleteUserAssociation", WorkflowConstants
                    .DELETE_USER_EVENT, "boolean(1)");
            usmClient.deleteUser(userName1);
        } catch (Exception e) {
            Assert.fail("failed to add updateRoleName workflow.");
        }
        try {
            usmClient.deleteUserClaimValue(userName1,"wso2.org/testClaim",null);
            Assert.fail("Should throw an exception since there is a pending workflow for deleting user.");
        } catch (Exception e) {
            //test pass
        }

        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestDeleteUserAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Error while clean up testUpdateRoleListOfUser");
        }
        try {
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            for (AssociationDTO association : associations) {
                if ("TestDeleteUserClaimValueAssociation".equals(association.getAssociationName())) {
                    associationId = association.getAssociationId();
                    client.deleteAssociation(associationId);
                    break;
                }
            }
            usmClient.deleteUser(userName1);
        } catch (Exception e) {
            log.error("Error while clean up testUpdateUserClaimOperation");
        }
    }



    @Test(alwaysRun = true, description = "Testing removing an association", dependsOnMethods =
            {"testAssociationForMatch", "testAssociationForNonMatch", "testUpdateUserClaimOperation"})
    public void testRemoveAssociation() {

        if (associationId == null) {
            Assert.fail("Association has not been added in the previous test");
        }
        try {
            client.deleteAssociation(associationId);
            AssociationDTO[] associations = client.listAssociationsForWorkflow(workflowId);
            if (associations != null) {
                for (AssociationDTO association : associations) {
                    if (associationId.equals(association.getAssociationId())) {
                        Assert.fail("Association " + associationId + " is not deleted, It still exists");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Assert.fail("Error while deleting the association", e);
        }
    }

    @Test(alwaysRun = true, description = "Testing removing an association", dependsOnMethods =
            {"testRemoveAssociation", "testDeleteUserOperation"})
    public void testRemoveWorkflow() {

        if (workflowId == null) {
            Assert.fail("Workflow has not been added in the previous test");
        }
        try {
            client.deleteWorkflow(workflowId);
            WorkflowDTO[] workflows = client.listWorkflows();
            if (workflows != null) {
                for (WorkflowDTO workflow : workflows) {
                    if (workflowId.equals(workflow.getWorkflowId())) {
                        Assert.fail("Workflow " + workflowId + " is not deleted, It still exists");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Assert.fail("Error while deleting the workflow", e);
        }
    }

    @Test(alwaysRun = true, description = "Testing deleting a BPS Profile", dependsOnMethods =
            {"testAddDuplicateBPSProfile","testRemoveWorkflow"})
    public void testRemoveBPSProfile() {

        String profileName = "TestBPSProfile";
        try {
            client.deleteBPSProfile(profileName);
            BPSProfileDTO[] bpsProfiles = client.listBPSProfiles();
            if (bpsProfiles != null) {
                for (BPSProfileDTO bpsProfile : bpsProfiles) {
                    if (profileName.equals(bpsProfile.getProfileName())) {
                        Assert.fail("BPS Profile " + profileName + " is not deleted");
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Assert.fail("Error while deleting the BPS profile", e);
        }
    }

    private WorkflowDTO getWorkflowDTO(String workflowName, String workflowDescription) {

        WorkflowDTO workflowDTO = new WorkflowDTO();
        workflowDTO.setWorkflowName(addUserWorkflowName);
        workflowDTO.setWorkflowDescription(workflowDescription);
        workflowDTO.setTemplateName("SimpleApproval");
        workflowDTO.setImplementationName("BPEL");
        return workflowDTO;
    }

    private List<ParameterDTO> getTemplateImplParams() {

        List<ParameterDTO> templateImplParams = new ArrayList<>();
        ParameterDTO bpelProfile = new ParameterDTO();
        bpelProfile.setParamName("BPELEngineProfile");
        bpelProfile.setParamValue("TestBPSProfile");
        templateImplParams.add(bpelProfile);
        ParameterDTO HTSubject = new ParameterDTO();
        HTSubject.setParamName("HTSubject");
        HTSubject.setParamValue("");
        templateImplParams.add(HTSubject);
        ParameterDTO HTDescription = new ParameterDTO();
        HTDescription.setParamName("HTDescription");
        HTDescription.setParamValue("");
        templateImplParams.add(HTDescription);
        return templateImplParams;
    }

}