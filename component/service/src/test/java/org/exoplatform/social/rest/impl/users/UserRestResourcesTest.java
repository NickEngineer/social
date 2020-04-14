package org.exoplatform.social.rest.impl.users;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.rest.impl.ContainerResponse;
import org.exoplatform.services.user.UserStateModel;
import org.exoplatform.services.user.UserStateService;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.activity.model.ExoSocialActivityImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.manager.RelationshipManager;
import org.exoplatform.social.core.space.impl.DefaultSpaceApplicationHandler;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.rest.api.ErrorResource;
import org.exoplatform.social.rest.entity.ActivityEntity;
import org.exoplatform.social.rest.entity.CollectionEntity;
import org.exoplatform.social.rest.entity.DataEntity;
import org.exoplatform.social.rest.entity.ProfileEntity;
import org.exoplatform.social.rest.impl.user.UserRestResourcesV1;
import org.exoplatform.social.service.test.AbstractResourceTest;

import java.util.Date;
import java.util.List;

public class UserRestResourcesTest extends AbstractResourceTest {
  
  private ActivityManager activityManager;
  private IdentityManager identityManager;
  private UserACL userACL;
  private RelationshipManager relationshipManager;
  private SpaceService spaceService;
  private UserStateService userStateService;

  private Identity rootIdentity;
  private Identity johnIdentity;
  private Identity maryIdentity;
  private Identity demoIdentity;

  public void setUp() throws Exception {
    super.setUp();
    
    System.setProperty("gatein.email.domain.url", "localhost:8080");

    activityManager = getContainer().getComponentInstanceOfType(ActivityManager.class);
    identityManager = getContainer().getComponentInstanceOfType(IdentityManager.class);
    userACL = getContainer().getComponentInstanceOfType(UserACL.class);
    relationshipManager = getContainer().getComponentInstanceOfType(RelationshipManager.class);
    spaceService = getContainer().getComponentInstanceOfType(SpaceService.class);
    userStateService = getContainer().getComponentInstanceOfType(UserStateService.class);

    rootIdentity = new Identity(OrganizationIdentityProvider.NAME, "root");
    johnIdentity = new Identity(OrganizationIdentityProvider.NAME, "john");
    maryIdentity = new Identity(OrganizationIdentityProvider.NAME, "mary");
    demoIdentity = new Identity(OrganizationIdentityProvider.NAME, "demo");

    identityManager.saveIdentity(rootIdentity);
    identityManager.saveIdentity(johnIdentity);
    identityManager.saveIdentity(maryIdentity);
    identityManager.saveIdentity(demoIdentity);

    addResource(UserRestResourcesV1.class, null);
  }

  public void tearDown() throws Exception {
    super.tearDown();
    removeResource(UserRestResourcesV1.class);
  }

  public void testGetAllUsers() throws Exception {
    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(4, collections.getEntities().size());
  }

  public void testGetAllUsersWithExtraFields() throws Exception {
    Space spaceTest = getSpaceInstance(700, "root");
    spaceTest.getId();
    spaceTest.setMembers(new String[] {"mary", "john", "demo"});
    spaceService.updateSpace(spaceTest);

    relationshipManager.inviteToConnect(rootIdentity, maryIdentity);

    relationshipManager.inviteToConnect(demoIdentity, rootIdentity);

    relationshipManager.inviteToConnect(rootIdentity, johnIdentity);
    relationshipManager.confirm(johnIdentity, rootIdentity);

    relationshipManager.inviteToConnect(maryIdentity, johnIdentity);
    relationshipManager.confirm(johnIdentity, maryIdentity);

    relationshipManager.inviteToConnect(demoIdentity, johnIdentity);
    relationshipManager.confirm(johnIdentity, demoIdentity);

    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users?limit=5&offset=0&expand=all,connectionsCount,spacesCount,connectionsInCommonCount,relationshipStatus"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(4, collections.getEntities().size());

    List<? extends DataEntity> entities = collections.getEntities();
    for (DataEntity dataEntity : entities) {
      String username = dataEntity.get("username").toString();
      if (StringUtils.equals(username, "root")) {
        continue;
      }
      String connectionsCount = dataEntity.get("connectionsCount").toString();
      String spacesCount = dataEntity.get("spacesCount").toString();
      String connectionsInCommonCount = dataEntity.get("connectionsInCommonCount").toString();
      String relationshipStatus = dataEntity.get("relationshipStatus").toString();
      if (StringUtils.equals(username, "john")) {
        assertEquals("3", connectionsCount);
      } else {
        assertEquals("1", connectionsCount);
      }
      assertEquals("1", spacesCount);
      if (StringUtils.equals(username, "john")) {
        assertEquals("CONFIRMED", relationshipStatus);
      } else if (StringUtils.equals(username, "mary")) {
        assertEquals("OUTGOING", relationshipStatus);
      } else if (StringUtils.equals(username, "demo")) {
        assertEquals("INCOMING", relationshipStatus);
      }
      if (StringUtils.equals(username, "john")) {
        assertEquals("0", connectionsInCommonCount);
      } else {
        assertEquals("1", connectionsInCommonCount);
      }
    }
  }

  public void testGetOnlineUsers() throws Exception {
    startSessionAs("root");
    long date = new Date().getTime();
    UserStateModel userModel =
            new UserStateModel("john",
                    date,
                    UserStateService.DEFAULT_STATUS);
    UserStateModel userModel2 =
            new UserStateModel("mary",
                    date,
                    UserStateService.DEFAULT_STATUS);
    userStateService.save(userModel);
    userStateService.save(userModel2);
    userStateService.ping(userModel.getUserId());
    userStateService.ping(userModel2.getUserId());
    ContainerResponse response = service("GET", getURLResource("users?status=online&limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(2, collections.getEntities().size()); // john and mary
  }

  public void testGetOnlineUsersOfSpace() throws Exception {
    startSessionAs("root");
    long date = new Date().getTime();
    UserStateModel userModel =
            new UserStateModel("john",
                    date,
                    UserStateService.DEFAULT_STATUS);
    UserStateModel userModel2 =
            new UserStateModel("mary",
                    date,
                    UserStateService.DEFAULT_STATUS);
    userStateService.save(userModel);
    userStateService.save(userModel2);
    userStateService.ping(userModel.getUserId());
    userStateService.ping(userModel2.getUserId());
    Space spaceTest = getSpaceInstance(0, "root");
    String spaceId = spaceTest.getId();
    spaceTest.setMembers(new String[] {"john"});
    spaceService.updateSpace(spaceTest);
    ContainerResponse response = service("GET", getURLResource("users?status=online&spaceId="+spaceId), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size()); // only john
    // non existing space
    response = service("GET", getURLResource("users?status=online&spaceId=600"), "", null, null);
    assertNotNull(response);
    assertEquals(404, response.getStatus());
    ErrorResource errorResource = (ErrorResource) response.getEntity();
    assertEquals("space not found", errorResource.getDeveloperMessage());
    assertEquals("space 600 does not exist", errorResource.getMessage());
  }

  public void testGetUserById() throws Exception {
    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users/john"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    ProfileEntity userEntity = getBaseEntity(response.getEntity(), ProfileEntity.class);
    assertEquals("john", userEntity.getUsername());
  }

  public void testGetConnectionsOfUser() throws Exception {
    startSessionAs("root");
    relationshipManager.inviteToConnect(rootIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, rootIdentity);

    ContainerResponse response = service("GET", getURLResource("users/root/connections?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());
    ProfileEntity userEntity = getBaseEntity(collections.getEntities().get(0), ProfileEntity.class);
    assertEquals("demo", userEntity.getUsername());
  }

  public void testGetInvitationsOfUser() throws Exception {
    relationshipManager.inviteToConnect(demoIdentity, rootIdentity);

    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users/connections/invitations?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());

    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());
    ProfileEntity userEntity = getBaseEntity(collections.getEntities().get(0), ProfileEntity.class);
    assertEquals("demo", userEntity.getUsername());
  }

  public void testGetPendingOfUser() throws Exception {
    relationshipManager.inviteToConnect(rootIdentity, demoIdentity);

    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users/connections/pending?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());
    ProfileEntity userEntity = getBaseEntity(collections.getEntities().get(0), ProfileEntity.class);
    assertEquals("demo", userEntity.getUsername());
  }

  public void testGetActivitiesOfUser() throws Exception {
    startSessionAs("root");
    relationshipManager.inviteToConnect(rootIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, rootIdentity);

    ExoSocialActivity rootActivity = new ExoSocialActivityImpl();
    rootActivity.setTitle("root activity");
    activityManager.saveActivityNoReturn(rootIdentity, rootActivity);

    //wait to make sure the order of activities
    Thread.sleep(10);
    ExoSocialActivity demoActivity = new ExoSocialActivityImpl();
    demoActivity.setTitle("demo activity");
    activityManager.saveActivityNoReturn(demoIdentity, demoActivity);
    
    ExoSocialActivity maryActivity = new ExoSocialActivityImpl();
    maryActivity.setTitle("mary activity");
    activityManager.saveActivityNoReturn(maryIdentity, maryActivity);

    end();
    begin();

    ContainerResponse response = service("GET", getURLResource("users/root/activities?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    //must return one activity of root and one of demo
    assertEquals(2, collections.getEntities().size());
    ActivityEntity activityEntity = getBaseEntity(collections.getEntities().get(0), ActivityEntity.class);
    assertEquals("demo activity", activityEntity.getTitle());
    activityEntity = getBaseEntity(collections.getEntities().get(1), ActivityEntity.class);
    assertEquals("root activity", activityEntity.getTitle());
    
    activityManager.deleteActivity(maryActivity);
    activityManager.deleteActivity(demoActivity);
    activityManager.deleteActivity(rootActivity);
  }

  public void testGetSpacesOfUser() throws Exception {
    getSpaceInstance(0, "root");
    getSpaceInstance(1, "john");
    getSpaceInstance(2, "demo");
    getSpaceInstance(3, "demo");
    
    startSessionAs("root");
    ContainerResponse response = service("GET", getURLResource("users/root/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    CollectionEntity collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());

    startSessionAs("john");
    response = service("GET", getURLResource("users/john/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    collections = (CollectionEntity) response.getEntity();
    assertEquals(1, collections.getEntities().size());

    startSessionAs("demo");
    response = service("GET", getURLResource("users/demo/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    collections = (CollectionEntity) response.getEntity();
    assertEquals(2, collections.getEntities().size());

    startSessionAs("john");
    response = service("GET", getURLResource("users/demo/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(403, response.getStatus());

    startSessionAs("root");
    response = service("GET", getURLResource("users/demo/spaces?limit=5&offset=0"), "", null, null);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    collections = (CollectionEntity) response.getEntity();
    assertEquals(2, collections.getEntities().size());
  }

  public void testAddActivityByUser() throws Exception {
    //root posts another activity
    String input = "{\"title\":titleOfActivity,\"templateParams\":{\"param1\": value1,\"param2\":value2}}";
    startSessionAs("john");
    ContainerResponse response = getResponse("POST", getURLResource("users/john/activities"), input);
    assertNotNull(response);
    assertEquals(200, response.getStatus());
    DataEntity responseEntity = (DataEntity) response.getEntity();
    String title = (String) responseEntity.get("title");
    assertNotNull(title);
    assertEquals("titleOfActivity", title);
    DataEntity templateParams = (DataEntity) responseEntity.get("templateParams");
    assertNotNull(templateParams);
    assertEquals(2, templateParams.size());
  }

  private Space getSpaceInstance(int number, String creator) throws Exception {
    Space space = new Space();
    space.setDisplayName("my space " + number);
    space.setPrettyName(space.getDisplayName());
    space.setRegistration(Space.OPEN);
    space.setDescription("add new space " + number);
    space.setType(DefaultSpaceApplicationHandler.NAME);
    space.setVisibility(Space.PUBLIC);
    space.setRegistration(Space.VALIDATION);
    space.setPriority(Space.INTERMEDIATE_PRIORITY);
    space.setGroupId("/spaces/space" + number);
    String[] managers = new String[] {creator};
    String[] members = new String[] {creator};
    space.setManagers(managers);
    space.setMembers(members);
    space.setUrl(space.getPrettyName());
    this.spaceService.createSpace(space, creator);
    return space;
  }
}
