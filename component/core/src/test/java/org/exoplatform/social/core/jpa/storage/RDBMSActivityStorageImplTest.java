/*
 * Copyright (C) 2003-2016 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.core.jpa.storage;

import java.util.*;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.social.core.BaseActivityProcessorPlugin;
import org.exoplatform.social.core.activity.model.*;
import org.exoplatform.social.core.application.RelationshipPublisher.TitleId;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.jpa.storage.dao.StreamItemDAO;
import org.exoplatform.social.core.jpa.storage.entity.StreamItemEntity;
import org.exoplatform.social.core.jpa.test.AbstractCoreTest;
import org.exoplatform.social.core.jpa.test.QueryNumberTest;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.RelationshipManager;
import org.exoplatform.social.core.relationship.model.Relationship;
import org.exoplatform.social.core.space.impl.DefaultSpaceApplicationHandler;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.ActivityStorageException;
import org.exoplatform.social.core.storage.api.ActivityStorage;
import org.exoplatform.social.core.storage.api.IdentityStorage;
import org.exoplatform.social.core.test.MaxQueryNumber;

@QueryNumberTest
public class RDBMSActivityStorageImplTest extends AbstractCoreTest {

  private IdentityStorage         identityStorage;

  private StreamItemDAO           streamItemDAO;

  private List<ExoSocialActivity> tearDownActivityList;

  private List<Space>             tearDownSpaceList;

  private Identity                rootIdentity;

  private Identity                johnIdentity;

  private Identity                maryIdentity;

  private Identity                demoIdentity;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    identityStorage = getService(IdentityStorage.class);
    streamItemDAO = getService(StreamItemDAO.class);

    assertNotNull(identityStorage);
    assertNotNull(activityStorage);

    rootIdentity = createOrUpdateIdentity("root");
    johnIdentity = createOrUpdateIdentity("john");
    maryIdentity = createOrUpdateIdentity("mary");
    demoIdentity = createOrUpdateIdentity("demo");

    tearDownActivityList = new ArrayList<ExoSocialActivity>();
  }

  @Override
  protected void tearDown() throws Exception {
    for (ExoSocialActivity activity : tearDownActivityList) {
      activityStorage.deleteActivity(activity.getId());
    }
    identityStorage.deleteIdentity(rootIdentity);
    identityStorage.deleteIdentity(johnIdentity);
    identityStorage.deleteIdentity(maryIdentity);
    identityStorage.deleteIdentity(demoIdentity);

    for (Space space : tearDownSpaceList) {
      Identity spaceIdentity = identityStorage.findIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());
      if (spaceIdentity != null) {
        identityStorage.deleteIdentity(spaceIdentity);
      }
      spaceService.deleteSpace(space);
    }
    super.tearDown();
  }

  /**
   * SOC-4525
   * 
   * @throws Exception
   */
  public void testConnectionActivities() throws Exception {
    relationshipManager.inviteToConnect(johnIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, johnIdentity);

    List<ExoSocialActivity> activities = activityStorage.getActivityFeed(johnIdentity, 0, 10);
    assertEquals(0, activities.size());

    // demo posts 2 activities, john must have these 2 activities on his AS
    createActivities(2, demoIdentity);
    activities = activityStorage.getActivityFeed(johnIdentity, 0, 10);
    assertEquals(2, activities.size());

    // demo creates a space and posts 2 other activities, john must not see
    // these 2 new activities
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
    for (int i = 0; i < 2; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
    }
    activities = activityStorage.getActivityFeed(johnIdentity, 0, 10);
    assertEquals(2, activities.size());
    activities = activityStorage.getUserSpacesActivities(johnIdentity, 0, 10);
    assertEquals(0, activities.size());
  }

  @MaxQueryNumber(522)
  public void testGetActivitiesByPoster() {
    ExoSocialActivity activity1 = createActivity(1);
    activity1.setType("TYPE1");
    activityStorage.saveActivity(demoIdentity, activity1);
    tearDownActivityList.add(activity1);

    ExoSocialActivity activity2 = createActivity(2);
    activity2.setType("TYPE2");
    activityStorage.saveActivity(demoIdentity, activity2);
    tearDownActivityList.add(activity2);

    //
    List<ExoSocialActivity> activities = activityStorage.getActivitiesByPoster(demoIdentity, 0, 10);
    assertEquals(2, activities.size());
    assertEquals(2, activityStorage.getNumberOfActivitiesByPoster(demoIdentity));
    activities = activityStorage.getActivitiesByPoster(demoIdentity, 0, 10, new String[] { "TYPE1" });
    assertEquals(1, activities.size());
  }

  @MaxQueryNumber(516)
  public void testUpdateActivity() {
    ExoSocialActivity activity = createActivity(1);
    //
    ExoSocialActivity activityCreated = activityStorage.saveActivity(demoIdentity, activity);
    List<StreamItemEntity> streamItems = streamItemDAO.findStreamItemByActivityId(Long.parseLong(activityCreated.getId()));

    activityCreated.setTitle("Title after updated");

    // update

    activityStorage.updateActivity(activityCreated);

    ExoSocialActivity res = activityStorage.getActivity(activity.getId());
    // Check that stream Item update date is not modified

    List<StreamItemEntity> streamItemsRes = streamItemDAO.findStreamItemByActivityId(Long.parseLong(activityCreated.getId()));

    assertEquals(streamItemsRes.get(0).getUpdatedDate(), streamItems.get(0).getUpdatedDate());

    assertEquals("Title after updated", res.getTitle());
    //
    tearDownActivityList.add(activity);
  }

  @MaxQueryNumber(60)
  public void testUpdateComment() {
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("Initial Activity");
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    //
    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("comment");
    comment.setUserId(johnIdentity.getId());
    comment.setPosterId(johnIdentity.getId());
    activityStorage.saveComment(activity, comment);

    assertTrue(comment.getPostedTime() == comment.getUpdated().getTime());

    comment.setTitle("comment updated");
    comment.setUpdated(new Date().getTime());
    activityStorage.saveComment(activity, comment);
    comment = activityStorage.getActivity(comment.getId());
    assertEquals("comment updated", comment.getTitle());
    assertTrue(comment.getPostedTime() < comment.getUpdated().getTime());
  }

  @MaxQueryNumber(123)
  public void testUpdateActivityMention() {
    ExoSocialActivity activity = createActivity(1);
    activityStorage.saveActivity(demoIdentity, activity);
    //
    activity = activityStorage.getActivity(activity.getId());
    assertEquals(0, activity.getMentionedIds().length);

    // update
    String processedTitle = "test <a href=\"/portal/classic/profile/root\" rel=\"nofollow\" target=\"_blank\">"
        + rootIdentity.getProfile().getFullName() + "</a> " +
        "<a href=\"/portal/classic/profile/john\" rel=\"nofollow\" target=\"_blank\">" + johnIdentity.getProfile().getFullName()
        + "</a>";
    activity.setTitle("test @root @john");
    activityStorage.updateActivity(activity);
    //
    activity = activityStorage.getActivity(activity.getId());
    assertEquals(processedTitle, activity.getTitle());
    assertEquals(2, activity.getMentionedIds().length);
    List<ExoSocialActivity> list = activityStorage.getActivities(rootIdentity, rootIdentity, 0, 10);
    assertEquals(1, list.size());
    list = activityStorage.getActivities(johnIdentity, johnIdentity, 0, 10);
    assertEquals(1, list.size());

    // update remove mention
    activity.setTitle("test @root");
    activityStorage.updateActivity(activity);
    //
    activity = activityStorage.getActivity(activity.getId());
    assertEquals(1, activity.getMentionedIds().length);
    list = activityStorage.getActivities(johnIdentity, johnIdentity, 0, 10);
    assertEquals(0, list.size());

    // add comment
    ExoSocialActivity comment1 = new ExoSocialActivityImpl();
    comment1.setTitle("comment @root @john");
    comment1.setUserId(johnIdentity.getId());
    comment1.setPosterId(johnIdentity.getId());
    activityStorage.saveComment(activity, comment1);
    //
    activity = activityStorage.getActivity(activity.getId());
    assertEquals(2, activity.getMentionedIds().length);
    list = activityStorage.getActivities(rootIdentity, rootIdentity, 0, 10);
    assertEquals(1, list.size());
    list = activityStorage.getActivities(johnIdentity, johnIdentity, 0, 10);
    assertEquals(1, list.size());

    //
    tearDownActivityList.add(activity);
  }

  @MaxQueryNumber(520)
  public void testGetUserActivities() {
    ExoSocialActivity activity = createActivity(1);
    //
    activityStorage.saveActivity(demoIdentity, activity);
    List<ExoSocialActivity> got = activityStorage.getUserActivities(demoIdentity, 0, 20);
    assertEquals(1, got.size());
    tearDownActivityList.addAll(got);
  }

  @MaxQueryNumber(520)
  public void testGetUserIdsActivities() {
    ExoSocialActivity activity = createActivity(1);
    //
    activityStorage.saveActivity(demoIdentity, activity);
    List<String> got = activityStorage.getUserIdsActivities(demoIdentity, 0, 20);
    assertEquals(1, got.size());
    tearDownActivityList.add(activityStorage.getActivity(got.get(0)));
  }

  @MaxQueryNumber(520)
  public void testGetActivitiesByIDs() {
    ExoSocialActivity activity1 = createActivity(10);
    activityStorage.saveActivity(demoIdentity, activity1);
    tearDownActivityList.add(activity1);
    ExoSocialActivity activity2 = createActivity(20);
    activityStorage.saveActivity(demoIdentity, activity2);
    tearDownActivityList.add(activity2);
    ExoSocialActivity activity3 = createActivity(30);
    activityStorage.saveActivity(demoIdentity, activity3);
    tearDownActivityList.add(activity3);
    //
    List<ExoSocialActivity> got = activityStorage.getActivities(Arrays.asList(new String[] { activity1.getId(), activity2.getId(),
        activity3.getId() }));
    assertEquals(3, got.size());
    assertEquals(activity1.getId(), got.get(0).getId());
    assertEquals(activity2.getId(), got.get(1).getId());
    assertEquals(activity3.getId(), got.get(2).getId());
  }

  @MaxQueryNumber(530)
  public void testGetActivityIdsFeed() {
    createActivities(3, demoIdentity);
    List<String> got = activityStorage.getActivityIdsFeed(demoIdentity, 0, 10);
    assertEquals(3, got.size());
  }

  @MaxQueryNumber(650)
  public void testGetSpaceActivityIds() throws Exception {
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 5;

    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityManager.saveActivityNoReturn(spaceIdentity, activity);
      tearDownActivityList.add(activity);
    }

    List<String> got = activityStorage.getSpaceActivityIds(spaceIdentity, 0, 10);
    assertEquals(5, got.size());
  }

  /**
   * Unit Test for:
   * <p>
   * {@link ActivityManager#getActivitiesOfConnections(Identity)}
   * 
   * @throws Exception
   */
  @MaxQueryNumber(540)
  public void testGetActivityIdsOfConnections() throws Exception {
    createActivities(5, johnIdentity);

    ListAccess<ExoSocialActivity> demoConnectionActivities =
                                                           activityManager.getActivitiesOfConnectionsWithListAccess(demoIdentity);
    assertEquals(0, demoConnectionActivities.load(0, 10).length);
    assertEquals(0, demoConnectionActivities.getSize());

    Relationship demoJohnRelationship = relationshipManager.inviteToConnect(demoIdentity, johnIdentity);
    relationshipManager.confirm(johnIdentity, demoIdentity);

    createActivities(1, johnIdentity);

    ListAccess<ExoSocialActivity> got = activityManager.getActivitiesOfConnectionsWithListAccess(demoIdentity);
    assertEquals(6, got.load(0, 10).length);
    assertEquals(6, got.getSize());

    List<Relationship> relationships = new ArrayList<Relationship>();

    this.createActivities(2, rootIdentity);
    this.createActivities(1, demoIdentity);

    List<String> activities = activityStorage.getActivityIdsOfConnections(demoIdentity, 0, 10);
    assertEquals(0, activities.size());

    RelationshipManager relationshipManager = this.getRelationshipManager();

    Relationship rootDemoRelationship = relationshipManager.invite(rootIdentity, demoIdentity);
    relationshipManager.confirm(rootDemoRelationship);
    relationships.add(rootDemoRelationship);

    activities = activityStorage.getActivityIdsOfConnections(rootIdentity, 0, 10);
    assertEquals("activities.size() must return: 1", 1, activities.size());

    activities = activityStorage.getActivityIdsOfConnections(demoIdentity, 0, 10);
    assertEquals(2, activities.size());

    for (Relationship rel : relationships) {
      relationshipManager.remove(rel);
    }
  }

  @MaxQueryNumber(530)
  public void testGetNewerOnUserActivities() {
    createActivities(2, demoIdentity);
    ExoSocialActivity firstActivity = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0);
    assertEquals(0, activityStorage.getNewerOnUserActivities(demoIdentity, firstActivity, 10).size());
    assertEquals(0, activityStorage.getNumberOfNewerOnUserActivities(demoIdentity, firstActivity));
    //
    createActivities(2, maryIdentity);
    assertEquals(0, activityStorage.getNewerOnUserActivities(demoIdentity, firstActivity, 10).size());
    assertEquals(0, activityStorage.getNumberOfNewerOnUserActivities(demoIdentity, firstActivity));
    //
    createActivities(2, demoIdentity);
    assertEquals(2, activityStorage.getNewerOnUserActivities(demoIdentity, firstActivity, 10).size());
    assertEquals(2, activityStorage.getNumberOfNewerOnUserActivities(demoIdentity, firstActivity));
  }

  @MaxQueryNumber(695)
  public void testGetNewerOnActivityFeed() {
    createActivities(3, demoIdentity);
    ExoSocialActivity demoBaseActivity = activityStorage.getActivityFeed(demoIdentity, 0, 10).get(0);
    assertEquals(0, activityStorage.getNewerOnActivityFeed(demoIdentity, demoBaseActivity, 10).size());
    assertEquals(0, activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, demoBaseActivity));
    //
    createActivities(1, demoIdentity);
    assertEquals(1, activityStorage.getNewerOnActivityFeed(demoIdentity, demoBaseActivity, 10).size());
    assertEquals(1, activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, demoBaseActivity));
    //
    createActivities(2, maryIdentity);
    Relationship demoMaryConnection = relationshipManager.inviteToConnect(demoIdentity, maryIdentity);
    relationshipManager.confirm(maryIdentity, demoIdentity);
    createActivities(2, maryIdentity);
    assertEquals(5, activityStorage.getNewerOnActivityFeed(demoIdentity, demoBaseActivity, 10).size());
    assertEquals(5, activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, demoBaseActivity));
  }

  @MaxQueryNumber(695)
  public void testGetOlderOnActivityFeed() throws Exception {
    createActivities(3, demoIdentity);
    createActivities(2, maryIdentity);
    Relationship maryDemoConnection = relationshipManager.inviteToConnect(maryIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, maryIdentity);

    List<ExoSocialActivity> demoActivityFeed = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    ExoSocialActivity baseActivity = demoActivityFeed.get(4);
    assertEquals(0, activityStorage.getNumberOfOlderOnActivityFeed(demoIdentity, baseActivity));
    assertEquals(0, activityStorage.getOlderOnActivityFeed(demoIdentity, baseActivity, 10).size());
    //
    createActivities(1, johnIdentity);
    assertEquals(0, activityStorage.getNumberOfOlderOnActivityFeed(demoIdentity, baseActivity));
    assertEquals(0, activityStorage.getOlderOnActivityFeed(demoIdentity, baseActivity, 10).size());
    //
    baseActivity = demoActivityFeed.get(2);
    assertEquals(2, activityStorage.getNumberOfOlderOnActivityFeed(demoIdentity, baseActivity));
    assertEquals(2, activityStorage.getOlderOnActivityFeed(demoIdentity, baseActivity, 10).size());
  }

  @MaxQueryNumber(1129)
  public void testGetNewerOnActivitiesOfConnections() throws Exception {
    List<Relationship> relationships = new ArrayList<Relationship>();
    createActivities(3, maryIdentity);
    createActivities(1, demoIdentity);
    createActivities(2, johnIdentity);
    createActivities(2, rootIdentity);

    List<ExoSocialActivity> maryActivities = activityStorage.getActivitiesOfIdentity(maryIdentity, 0, 10);
    assertEquals(3, maryActivities.size());

    // base activity is the second activity created by mary
    ExoSocialActivity baseActivity = maryActivities.get(1);

    // As mary has no connections, there are any activity on her connection
    // stream
    assertEquals(0, activityStorage.getNewerOnActivitiesOfConnections(maryIdentity, baseActivity, 10).size());
    assertEquals(0, activityStorage.getNumberOfNewerOnActivitiesOfConnections(maryIdentity, baseActivity));

    // demo connect with mary
    Relationship maryDemoRelationship = relationshipManager.inviteToConnect(maryIdentity, demoIdentity);
    relationshipManager.confirm(maryIdentity, demoIdentity);
    relationships.add(maryDemoRelationship);

    // add 1 activity to make sure cache is updated
    createActivities(1, demoIdentity);

    // mary has 2 activities created by demo (1 at the beginning + 1 after the
    // connection confirmation) newer than the base activity
    assertEquals(2, activityStorage.getNewerOnActivitiesOfConnections(maryIdentity, baseActivity, 10).size());
    assertEquals(2, activityStorage.getNumberOfNewerOnActivitiesOfConnections(maryIdentity, baseActivity));

    // demo has activity created by mary newer than the base activity
    assertEquals(1, activityStorage.getNewerOnActivitiesOfConnections(demoIdentity, baseActivity, 10).size());
    assertEquals(1, activityStorage.getNumberOfNewerOnActivitiesOfConnections(demoIdentity, baseActivity));

    // john connects with mary
    Relationship maryJohnRelationship = relationshipManager.inviteToConnect(maryIdentity, johnIdentity);
    relationshipManager.confirm(maryIdentity, johnIdentity);
    relationships.add(maryJohnRelationship);

    // add 1 activity to make sure cache is updated
    createActivities(1, johnIdentity);

    // mary has 2 activities created by demo and 3 activities created by john (2
    // at the beginning + 1 after the connection confirmation) newer than the
    // base activity
    assertEquals(5, activityStorage.getNewerOnActivitiesOfConnections(maryIdentity, baseActivity, 10).size());
    assertEquals(5, activityStorage.getNumberOfNewerOnActivitiesOfConnections(maryIdentity, baseActivity));

    // john has 1 activity created by mary newer than the base activity
    assertEquals(1, activityStorage.getNewerOnActivitiesOfConnections(johnIdentity, baseActivity, 10).size());
    assertEquals(1, activityStorage.getNumberOfNewerOnActivitiesOfConnections(johnIdentity, baseActivity));

    // mary connects with root
    Relationship maryRootRelationship = relationshipManager.inviteToConnect(maryIdentity, rootIdentity);
    relationshipManager.confirm(maryIdentity, rootIdentity);
    relationships.add(maryRootRelationship);

    // add 1 activity to make sure cache is updated
    createActivities(1, rootIdentity);

    // mary has 2 activities created by demo, 3 activities created by john (2 at
    // the beginning + 1 after the connection confirmation)
    // and 3 activities created by root (2 at the beginning + 1 after the
    // connection confirmation) newer than the base activity
    assertEquals(8, activityStorage.getNewerOnActivitiesOfConnections(maryIdentity, baseActivity, 10).size());
    assertEquals(8, activityStorage.getNumberOfNewerOnActivitiesOfConnections(maryIdentity, baseActivity));
  }

  @MaxQueryNumber(1135)
  public void testGetOlderOnActivitiesOfConnections() throws Exception {
    int initialSize = activityStorage.getOlderFeedActivities(maryIdentity, System.currentTimeMillis(), 100).size();

    List<Relationship> relationships = new ArrayList<Relationship>();
    createActivities(3, maryIdentity);
    createActivities(1, demoIdentity);
    createActivities(2, johnIdentity);
    createActivities(2, rootIdentity);

    List<ExoSocialActivity> maryActivities = activityStorage.getActivitiesOfIdentity(maryIdentity, 0, 10);
    assertEquals(3, maryActivities.size());

    // base activity is the first activity created by mary
    ExoSocialActivity baseActivity = maryActivities.get(2);

    // As mary has no connections, there are any activity on her connection
    // stream
    assertEquals(0, activityStorage.getOlderOnActivitiesOfConnections(maryIdentity, baseActivity, 10).size());
    assertEquals(0, activityStorage.getNumberOfOlderOnActivitiesOfConnections(maryIdentity, baseActivity));

    // demo connect with mary
    Relationship maryDemoRelationship = relationshipManager.inviteToConnect(maryIdentity, demoIdentity);
    relationshipManager.confirm(maryIdentity, demoIdentity);
    relationships.add(maryDemoRelationship);

    baseActivity = activityStorage.getActivitiesOfIdentity(demoIdentity, 0, 10).get(0);
    LOG.info("demo::sinceTime = " + baseActivity.getPostedTime());
    assertEquals(0, activityStorage.getOlderOnActivitiesOfConnections(maryIdentity, baseActivity, 10).size());
    assertEquals(0, activityStorage.getNumberOfOlderOnActivitiesOfConnections(maryIdentity, baseActivity));

    assertEquals(3, activityStorage.getOlderOnActivitiesOfConnections(demoIdentity, baseActivity, 10).size());
    assertEquals(3, activityStorage.getNumberOfOlderOnActivitiesOfConnections(demoIdentity, baseActivity));

    // john connects with mary
    Relationship maryJohnRelationship = relationshipManager.inviteToConnect(maryIdentity, johnIdentity);
    relationshipManager.confirm(maryIdentity, johnIdentity);
    relationships.add(maryJohnRelationship);

    baseActivity = activityStorage.getActivitiesOfIdentity(johnIdentity, 0, 10).get(0);
    LOG.info("john::sinceTime = " + baseActivity.getPostedTime());
    restartTransaction();

    assertEquals(2, activityStorage.getOlderOnActivitiesOfConnections(maryIdentity, baseActivity, 10).size());
    assertEquals(2, activityStorage.getNumberOfOlderOnActivitiesOfConnections(maryIdentity, baseActivity));

    assertEquals(3, activityStorage.getOlderOnActivitiesOfConnections(johnIdentity, baseActivity, 10).size());
    assertEquals(3, activityStorage.getNumberOfOlderOnActivitiesOfConnections(johnIdentity, baseActivity));

    // mary connects with root
    Relationship maryRootRelationship = relationshipManager.inviteToConnect(maryIdentity, rootIdentity);
    relationshipManager.confirm(maryIdentity, rootIdentity);
    relationships.add(maryRootRelationship);

    baseActivity = activityStorage.getActivitiesOfIdentity(rootIdentity, 0, 10).get(0);
    LOG.info("root::sinceTime = " + baseActivity.getPostedTime());
    assertEquals(initialSize + 4, activityStorage.getOlderOnActivitiesOfConnections(maryIdentity, baseActivity, 10).size());
    assertEquals(initialSize + 4, activityStorage.getNumberOfOlderOnActivitiesOfConnections(maryIdentity, baseActivity));
  }

  @MaxQueryNumber(835)
  public void testGetNewerOnUserSpacesActivities() throws Exception {
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 10;
    ExoSocialActivity baseActivity = null;
    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
      if (i == 0) {
        baseActivity = activity;
      }
    }

    assertEquals(9, activityStorage.getNewerOnUserSpacesActivities(demoIdentity, baseActivity, 10).size());
    assertEquals(9, activityStorage.getNumberOfNewerOnUserSpacesActivities(demoIdentity, baseActivity));
    //
    assertEquals(9, activityStorage.getNewerOnSpaceActivities(spaceIdentity, baseActivity, 10).size());
    assertEquals(9, activityStorage.getNumberOfNewerOnSpaceActivities(spaceIdentity, baseActivity));

    Space space2 = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space2.getPrettyName(), false);
    // demo posts activities to space2
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      tearDownActivityList.add(activity);
    }

    assertEquals(19, activityStorage.getNewerOnUserSpacesActivities(demoIdentity, baseActivity, 20).size());
    assertEquals(19, activityStorage.getNumberOfNewerOnUserSpacesActivities(demoIdentity, baseActivity));
  }

  @MaxQueryNumber(820)
  public void testGetOlderOnUserSpacesActivities() throws Exception {
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 5;
    ExoSocialActivity baseActivity = null;
    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      LOG.info("user = " + demoIdentity.getRemoteId() + " activity's postedTime = " + activity.getPostedTime());
      restartTransaction();
      tearDownActivityList.add(activity);
      if (i == 4) {
        baseActivity = activity;
      }
    }

    LOG.info("user = " + demoIdentity.getRemoteId() + " sinceTime = " + baseActivity.getPostedTime());

    assertEquals(4, activityStorage.getOlderOnUserSpacesActivities(demoIdentity, baseActivity, 10).size());
    assertEquals(4, activityStorage.getNumberOfOlderOnUserSpacesActivities(demoIdentity, baseActivity));
    //
    assertEquals(4, activityStorage.getOlderOnSpaceActivities(spaceIdentity, baseActivity, 10).size());
    assertEquals(4, activityStorage.getNumberOfOlderOnSpaceActivities(spaceIdentity, baseActivity));

    Space space2 = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space2.getPrettyName(), false);
    // demo posts activities to space2
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      restartTransaction();
      tearDownActivityList.add(activity);
    }
    assertEquals(4, activityStorage.getOlderOnUserSpacesActivities(demoIdentity, baseActivity, 10).size());
    assertEquals(4, activityStorage.getNumberOfOlderOnUserSpacesActivities(demoIdentity, baseActivity));
  }

  @MaxQueryNumber(213)
  public void testGetNewerComments() {
    int totalNumber = 10;

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("activity title");
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("john comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("demo comment " + i);
      comment.setUserId(demoIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    List<ExoSocialActivity> comments = activityStorage.getComments(activity, false, 0, 20);
    assertEquals(20, comments.size());

    ExoSocialActivity baseComment = comments.get(0);

    assertEquals(19, activityStorage.getNewerComments(activity, baseComment, 20).size());
    assertEquals(19, activityStorage.getNumberOfNewerComments(activity, baseComment));

    baseComment = comments.get(9);
    assertEquals(10, activityStorage.getNewerComments(activity, baseComment, 20).size());
    assertEquals(10, activityStorage.getNumberOfNewerComments(activity, baseComment));

    baseComment = comments.get(19);
    assertEquals(0, activityStorage.getNewerComments(activity, baseComment, 20).size());
    assertEquals(0, activityStorage.getNumberOfNewerComments(activity, baseComment));
  }

  @MaxQueryNumber(690)
  public void testGetOlderComments() {
    int totalNumber = 10;

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("activity title");
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("john comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("demo comment " + i);
      comment.setUserId(demoIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    List<ExoSocialActivity> comments = activityStorage.getComments(activity, false, 0, 20);
    assertEquals(20, comments.size());

    ExoSocialActivity baseComment = comments.get(19);

    assertEquals(19, activityStorage.getOlderComments(activity, baseComment, 20).size());
    assertEquals(19, activityStorage.getNumberOfOlderComments(activity, baseComment));

    baseComment = comments.get(10);
    assertEquals(10, activityStorage.getOlderComments(activity, baseComment, 20).size());
    assertEquals(10, activityStorage.getNumberOfOlderComments(activity, baseComment));

    baseComment = comments.get(0);
    assertEquals(0, activityStorage.getOlderComments(activity, baseComment, 20).size());
    assertEquals(0, activityStorage.getNumberOfOlderComments(activity, baseComment));
  }

  @MaxQueryNumber(1281)
  public void testMentionersAndCommenters() throws Exception {
    ExoSocialActivity activity = createActivity(1);
    activity.setTitle("hello @demo @john");
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    ExoSocialActivity got = activityStorage.getActivity(activity.getId());
    assertNotNull(got);
    assertEquals(2, got.getMentionedIds().length);

    ExoSocialActivity comment1 = new ExoSocialActivityImpl();
    comment1.setTitle("comment 1");
    comment1.setUserId(demoIdentity.getId());
    activityStorage.saveComment(activity, comment1);
    ExoSocialActivity comment2 = new ExoSocialActivityImpl();
    comment2.setTitle("comment 2");
    comment2.setUserId(johnIdentity.getId());
    activityStorage.saveComment(activity, comment2);

    got = activityStorage.getActivity(activity.getId());
    assertEquals(2, got.getReplyToId().length);
    assertEquals(2, got.getCommentedIds().length);

    ExoSocialActivity comment3 = new ExoSocialActivityImpl();
    comment3.setTitle("hello @mary");
    comment3.setUserId(johnIdentity.getId());
    activityStorage.saveComment(activity, comment3);

    got = activityStorage.getActivity(activity.getId());
    assertEquals(3, got.getReplyToId().length);
    assertEquals(2, got.getCommentedIds().length);
    assertEquals(3, got.getMentionedIds().length);

    activityStorage.deleteComment(activity.getId(), comment3.getId());

    got = activityStorage.getActivity(activity.getId());
    assertEquals(2, got.getReplyToId().length);
    assertEquals(2, got.getCommentedIds().length);
    assertEquals(2, got.getMentionedIds().length);
  }

  @MaxQueryNumber(57)
  public void testShouldMoveActivityUpWhenMentionedInAPost() {
    // Given
    ExoSocialActivity activity1 = createActivity(1);
    activity1.setTitle("hello world");
    activityStorage.saveActivity(demoIdentity, activity1);
    tearDownActivityList.add(activity1);

    restartTransaction();

    ExoSocialActivity activity2 = createActivity(1);
    activity2.setTitle("hello mention @demo");
    activityStorage.saveActivity(rootIdentity, activity2);
    tearDownActivityList.add(activity2);

    restartTransaction();

    ExoSocialActivity activity3 = createActivity(1);
    activity3.setTitle("bye world");
    activityStorage.saveActivity(demoIdentity, activity3);
    tearDownActivityList.add(activity3);

    restartTransaction();

    // When
    List<ExoSocialActivity> activities = activityStorage.getActivities(demoIdentity, demoIdentity, 0, 10);

    // Then
    assertNotNull(activities);
    assertEquals(3, activities.size());
    assertEquals("bye world", activities.get(0).getTitle());
    assertTrue("Title '" + activities.get(1).getTitle() + "' doesn't start with 'hello mention'",
               activities.get(1).getTitle().startsWith("hello mention"));
    assertEquals("hello world", activities.get(2).getTitle());
  }

  @MaxQueryNumber(1293)
  public void testViewerOwnerPosterActivities() throws Exception {
    ExoSocialActivity activity1 = new ExoSocialActivityImpl();
    // Demo mentionned here
    activity1.setTitle("title @demo hi");
    activityStorage.saveActivity(rootIdentity, activity1);
    tearDownActivityList.add(activity1);

    // owner poster comment
    ExoSocialActivity activity2 = new ExoSocialActivityImpl();
    activity2.setTitle("root title");
    activityStorage.saveActivity(rootIdentity, activity2);
    tearDownActivityList.add(activity2);

    Space space = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
    // demo posts activities to space2
    for (int i = 0; i < 5; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      tearDownActivityList.add(activity);
    }

    createComment(activity2, demoIdentity, null, 5, 0);

    List<ExoSocialActivity> list = activityStorage.getActivities(demoIdentity, johnIdentity, 0, 10);

    // Return the activity that demo has commented on it and where he's
    // mentionned
    assertEquals(2, list.size());

    list = activityStorage.getActivities(johnIdentity, demoIdentity, 0, 10);

    // John hasn't added, commeted or was menionned in an activity
    assertEquals(0, list.size());

    list = activityStorage.getActivities(demoIdentity, demoIdentity, 0, 10);

    // Demo can see his activities 'Space' & 'User activities'
    assertEquals(7, list.size());
  }

  @MaxQueryNumber(1293)
  public void testViewerOwnerPosterInSpaceSubComments() throws Exception {
    ExoSocialActivity activity1 = new ExoSocialActivityImpl();
    // Demo mentionned here
    activity1.setTitle("title @mary hi");
    activityStorage.saveActivity(rootIdentity, activity1);
    tearDownActivityList.add(activity1);

    // owner poster comment
    ExoSocialActivity activity2 = new ExoSocialActivityImpl();
    activity2.setTitle("root title");
    activityStorage.saveActivity(rootIdentity, activity2);
    tearDownActivityList.add(activity2);

    Space space = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
    // demo posts activities to space2
    for (int i = 0; i < 5; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      tearDownActivityList.add(activity);
    }

    createComment(activity2, demoIdentity, maryIdentity, 5, 5);

    List<ExoSocialActivity> list = activityStorage.getActivities(maryIdentity, johnIdentity, 0, 10);

    // Return the activity that mary has commented on it and where he's
    // mentionned
    assertEquals(2, list.size());

    list = activityStorage.getActivities(johnIdentity, maryIdentity, 0, 10);

    // John hasn't added, commeted or was menionned in an activity
    assertEquals(0, list.size());

    list = activityStorage.getActivities(maryIdentity, maryIdentity, 0, 10);

    // Demo can see his activities 'Space' & 'User activities'
    assertEquals(2, list.size());
  }

  @MaxQueryNumber(1329)
  public void testViewerOwnerPosterSubComments() throws Exception {
    ExoSocialActivity activity1 = new ExoSocialActivityImpl();
    // Demo mentionned here
    activity1.setTitle("title @mary hi");
    activityStorage.saveActivity(rootIdentity, activity1);
    tearDownActivityList.add(activity1);

    // owner poster comment
    ExoSocialActivity activity2 = new ExoSocialActivityImpl();
    activity2.setTitle("root title");
    activityStorage.saveActivity(rootIdentity, activity2);
    activity2.setLikeIdentityIds(new String[] { maryIdentity.getId() });
    activityStorage.updateActivity(activity2);
    tearDownActivityList.add(activity2);

    // demo posts activities to his stream
    for (int i = 0; i < 5; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(rootIdentity.getId());
      activityStorage.saveActivity(rootIdentity, activity);

      createComment(activity, demoIdentity, maryIdentity, 5, 5);
      tearDownActivityList.add(activity);
    }

    // Demo post a comment
    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("comment demo");
    comment.setUserId(demoIdentity.getId());
    comment.setPosterId(demoIdentity.getId());
    activityStorage.saveComment(activity1, comment);

    // Demo post a comment reply by mentioning mary
    ExoSocialActivity commentReply = new ExoSocialActivityImpl();
    commentReply.setTitle("comment reply @mary");
    commentReply.setUserId(demoIdentity.getId());
    commentReply.setPosterId(demoIdentity.getId());
    commentReply.setParentCommentId(comment.getId());
    activityStorage.saveComment(activity1, commentReply);

    List<ExoSocialActivity> list = activityStorage.getActivities(maryIdentity, maryIdentity, 0, 10);

    // Demo can see his activities 'User activities'
    assertEquals(7, list.size());

    list = activityStorage.getActivities(johnIdentity, maryIdentity, 0, 10);

    // John hasn't added, commeted or was menionned in an activity
    assertEquals(0, list.size());

    list = activityStorage.getActivities(maryIdentity, johnIdentity, 0, 10);

    // Return the activity that demo has commented on it and where he's
    // mentionned
    assertEquals(7, list.size());
  }

  @MaxQueryNumber(129)
  public void testSaveCommentWithAlreadyMentionedUsers() throws Exception {
    ExoSocialActivity activity1 = new ExoSocialActivityImpl();
    activity1.setTitle("Initial Activity");
    activityStorage.saveActivity(rootIdentity, activity1);
    tearDownActivityList.add(activity1);

    // mention root
    ExoSocialActivity comment1 = new ExoSocialActivityImpl();
    comment1.setTitle("comment @root");
    comment1.setUserId(johnIdentity.getId());
    comment1.setPosterId(johnIdentity.getId());
    activityStorage.saveComment(activity1, comment1);

    // mention john
    ExoSocialActivity comment2 = new ExoSocialActivityImpl();
    comment2.setTitle("comment @john");
    comment2.setUserId(rootIdentity.getId());
    comment2.setPosterId(rootIdentity.getId());
    activityStorage.saveComment(activity1, comment2);

    // mention root
    ExoSocialActivity comment3 = new ExoSocialActivityImpl();
    comment3.setTitle("comment @root");
    comment3.setUserId(johnIdentity.getId());
    comment3.setPosterId(johnIdentity.getId());
    activityStorage.saveComment(activity1, comment3);

    // mention john
    ExoSocialActivity comment4 = new ExoSocialActivityImpl();
    comment4.setTitle("comment @john");
    comment4.setUserId(rootIdentity.getId());
    comment4.setPosterId(rootIdentity.getId());
    activityStorage.saveComment(activity1, comment4);

    List<ExoSocialActivity> list = activityStorage.getActivities(rootIdentity, rootIdentity, 0, 10);
    assertEquals(1, list.size());
    assertEquals(2, list.get(0).getMentionedIds().length);

    List<ExoSocialActivity> comments = activityStorage.getComments(list.get(0), true, 0, 10);
    assertEquals(4, comments.size());

    tearDownActivityList.add(activity1);
  }

  /**
   * SOC-4525 demo creates a space and posts 5 activities to that space demo and
   * john connect together
   * 
   * @throws Exception
   */
  public void testConnectionActivities2() throws Exception {
    // demo creates a space "space0" and posts 5 activities
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);

    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
    for (int i = 0; i < 5; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("space activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
    }

    List<ExoSocialActivity> activities = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals(5, activities.size());

    // john (in pending list of space0) must not see these 5 activities
    activities = activityStorage.getActivityFeed(johnIdentity, 0, 10);
    assertEquals(0, activities.size());

    activities = activityStorage.getUserSpacesActivities(johnIdentity, 0, 10);
    assertEquals(0, activities.size());

    // demo and john are now connected
    relationshipManager.inviteToConnect(johnIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, johnIdentity);

    // demo posts 2 activities on his stream, john must have these 2 activities
    // on his AS
    createActivities(2, demoIdentity);
    activities = activityStorage.getActivityFeed(johnIdentity, 0, 10);
    assertEquals(2, activities.size());

    activities = activityStorage.getUserSpacesActivities(johnIdentity, 0, 10);
    assertEquals(0, activities.size());
  }

  /**
   * Test
   * {@link org.exoplatform.social.core.storage.ActivityStorage#saveActivity(org.exoplatform.social.core.identity.model.Identity, org.exoplatform.social.core.activity.model.ExoSocialActivity)}
   */
  @MaxQueryNumber(366)
  public void testSaveActivity() throws ActivityStorageException {
    final String activityTitle = "activity Title";
    // test wrong
    {
      ExoSocialActivity wrongActivity = new ExoSocialActivityImpl();
      try {
        activityStorage.saveActivity(demoIdentity, null);
        activityStorage.saveActivity(null, wrongActivity);
      } catch (ActivityStorageException e) {
        LOG.info("wrong argument tests passed.");
      }
    }
    // test with only mandatory fields
    {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle(activityTitle);
      activityStorage.saveActivity(rootIdentity, activity);

      assertNotNull("activity.getId() must not be null", activity.getId());

      tearDownActivityList.addAll(activityStorage.getUserActivities(rootIdentity, 0, 1));

      ExoSocialActivity johnActivity = new ExoSocialActivityImpl();
      johnActivity.setTitle(activityTitle);
      activityStorage.saveActivity(johnIdentity, johnActivity);
      assertNotNull("johnActivity.getId() must not be null", johnActivity.getId());

      tearDownActivityList.addAll(activityStorage.getUserActivities(johnIdentity, 0, 1));
    }
    // Test with full fields.
    {

    }

    // Test mail-formed activityId
    {

    }

  }

  /**
   * Test
   * {@link org.exoplatform.social.core.storage.ActivityStorage#deleteActivity(String)}
   */
  @MaxQueryNumber(810)
  public void testDeleteActivity() throws ActivityStorageException {
    final String activityTitle = "activity Title";

    // Test deleteActivity(String)
    {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle(activityTitle);
      activityStorage.saveActivity(maryIdentity, activity);

      assertNotNull("activity.getId() must not be null", activity.getId());

      activityStorage.deleteActivity(activity.getId());
      try {
        assertEquals(null, activityStorage.getActivity(activity.getId()));
      } catch (Exception ase) {
        // ok
      }
    }
    // Test deleteActivity(Activity)
    {
      ExoSocialActivity activity2 = new ExoSocialActivityImpl();
      activity2.setTitle(activityTitle);
      activityStorage.saveActivity(demoIdentity, activity2);

      assertNotNull("activity2.getId() must not be null", activity2.getId());
      activityStorage.deleteActivity(activity2.getId());
    }

  }

  /**
   * Test
   * {@link org.exoplatform.social.core.storage.ActivityStorage#saveComment(org.exoplatform.social.core.activity.model.ExoSocialActivity , org.exoplatform.social.core.activity.model.ExoSocialActivity)}
   */
  @MaxQueryNumber(366)
  public void testSaveComment() throws ActivityStorageException {

    // comment on his own activity
    {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("blah blah");
      activityStorage.saveActivity(rootIdentity, activity);

      assertNotNull(activity.getReplyToId());

      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("comment blah");
      comment.setUserId(rootIdentity.getId());

      activityStorage.saveComment(activity, comment);

      assertNotNull(activity.getReplyToId());
      assertEquals(1, activity.getReplyToId().length);

      comment = activityStorage.getActivity(comment.getId());
      assertTrue(comment.isComment());

      tearDownActivityList.add(activity);
    }

    // comment on other users' activity
    {

    }

  }

  /**
   * Test
   * {@link org.exoplatform.social.core.storage.ActivityStorage#deleteComment(String, String)}
   */

  @MaxQueryNumber(462)
  public void testDeleteComment() throws ActivityStorageException {

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("blah blah");
    activityStorage.saveActivity(rootIdentity, activity);

    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("coment blah blah");
    comment.setUserId(rootIdentity.getId());

    activityStorage.saveComment(activity, comment);

    assertNotNull("comment.getId() must not be null", comment.getId());

    activityStorage.deleteComment(activity.getId(), comment.getId());

    tearDownActivityList.add(activity);
  }

  /**
   * Test
   * {@link org.exoplatform.social.core.storage.ActivityStorage#getActivity(String)}
   */
  @MaxQueryNumber(159)
  public void testGetActivity() throws ActivityStorageException {
    final String activityTitle = "activity title";
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle(activityTitle);
    activityStorage.saveActivity(demoIdentity, activity);
    tearDownActivityList.add(activity);

    assertNotNull("activity.getId() must not be null", activity.getId());

    assertEquals("demoIdentity.getRemoteId() must return: " + demoIdentity.getRemoteId(),
                 demoIdentity.getRemoteId(),
                 activity.getStreamOwner());

    ExoSocialActivity gotActivity = activityStorage.getActivity(activity.getId());

    assertNotNull("gotActivity.getId() must not be null", gotActivity.getId());

    assertEquals("activity.getId() must return: " + activity.getId(), activity.getId(), gotActivity.getId());

    assertEquals("gotActivity.getTitle() must return: " + gotActivity.getTitle(), activityTitle, gotActivity.getTitle());

    ActivityStream activityStream = activity.getActivityStream();
    assertNotNull("activityStream.getId() must not be null", activityStream.getId());
    assertEquals("activityStream.getPrettyId() must return: " + demoIdentity.getRemoteId(),
                 demoIdentity.getRemoteId(),
                 activityStream.getPrettyId());
    assertEquals(ActivityStream.Type.USER, activityStream.getType());
    assertNotNull("activityStream.getPermaLink() must not be null", activityStream.getPermaLink());

  }

  /**
   * Test
   * {@link org.exoplatform.social.core.storage.ActivityStorage#getUserActivities(org.exoplatform.social.core.identity.model.Identity, long, long)}
   * and
   * {@link org.exoplatform.social.core.storage.ActivityStorage#getUserActivities(org.exoplatform.social.core.identity.model.Identity)}
   */
  @MaxQueryNumber(12675)
  public void testGetActivities() throws ActivityStorageException {
    final int totalNumber = 20;
    final String activityTitle = "activity title";
    // John posts activity to root's activity stream
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle(activityTitle + i);

      activityStorage.saveActivity(rootIdentity, activity);
      tearDownActivityList.add(activity);
    }

    // Till now Root's activity stream has 10 activities posted by John
    assertEquals("John must have zero activity", 0, activityStorage.getUserActivities(johnIdentity, 0, 100).size());
    assertEquals("Root must have " + totalNumber + " activities",
                 totalNumber,
                 activityStorage.getUserActivities(rootIdentity, 0, 100).size());

    // Root posts activities to his stream
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle(activityTitle + i);
      activity.setUserId(rootIdentity.getId());
      activityStorage.saveActivity(rootIdentity, activity);

      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("Comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
      tearDownActivityList.add(activity);
    }
    // Till now Root's activity stream has 40 activities: 20 posted by John and
    // 20 posted by Root
    // , each of those activities posted by Root has 1 comment by John.
    assertEquals("John must have zero activity", 20, activityStorage.getUserActivities(johnIdentity).size());
    assertEquals("Root must have " + totalNumber * 2 + " activities",
                 totalNumber * 2,
                 activityStorage.getUserActivities(rootIdentity).size());

    // Test ActivityStorage#getActivities(Identity, long, long)
    {
      final int limit = 34;
      assertTrue("root's activities should be greater than " + limit + " for passing test below",
                 activityStorage.getUserActivities(rootIdentity).size() > limit);
      List<ExoSocialActivity> gotRootActivityList = activityStorage.getUserActivities(rootIdentity, 0, limit);
      assertEquals("gotRootActivityList.size() must return " + limit, limit, gotRootActivityList.size());
    }

  }

  public void testGetUserActivityIds() throws ActivityStorageException {
    final int totalNumber = 10;
    final String activityTitle = "activity title";
    // John posts activity to root's activity stream
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle(activityTitle + i);
      activityStorage.saveActivity(rootIdentity, activity);
      tearDownActivityList.add(activity);
    }

    assertEquals(10, activityStorage.getUserIdsActivities(rootIdentity, 0, 20).size());
  }

  /**
   * Test
   * {@link org.exoplatform.social.core.storage.ActivityStorage#getNumberOfUserActivities(org.exoplatform.social.core.identity.model.Identity)}
   */

  @MaxQueryNumber(18207)
  public void testGetActivitiesCount() throws ActivityStorageException {

    final int totalNumber = 20;
    // create 20 activities each for root, john, mary, demo.
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity rootActivity = new ExoSocialActivityImpl();
      rootActivity.setTitle("Root activity" + i);
      activityStorage.saveActivity(rootIdentity, rootActivity);

      tearDownActivityList.add(rootActivity);

      ExoSocialActivity johnActivity = new ExoSocialActivityImpl();
      johnActivity.setTitle("John activity" + i);
      activityStorage.saveActivity(johnIdentity, johnActivity);

      tearDownActivityList.add(johnActivity);

      ExoSocialActivity maryActivity = new ExoSocialActivityImpl();
      maryActivity.setTitle("Mary activity" + i);
      activityStorage.saveActivity(maryIdentity, maryActivity);

      tearDownActivityList.add(maryActivity);

      ExoSocialActivity demoActivity = new ExoSocialActivityImpl();
      demoActivity.setTitle("Demo activity" + i);
      activityStorage.saveActivity(demoIdentity, demoActivity);

      tearDownActivityList.add(demoActivity);

      // John comments demo's activities
      ExoSocialActivity johnComment = new ExoSocialActivityImpl();
      johnComment.setTitle("John's comment " + i);
      johnComment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(demoActivity, johnComment);
    }

    assertEquals("activityStorage.getNumberOfUserActivities(rootIdentity) must return " + totalNumber,
                 totalNumber,
                 activityStorage.getNumberOfUserActivities(rootIdentity));
    assertEquals("activityStorage.getNumberOfUserActivities(johnIdentity) must return " + totalNumber * 2,
                 totalNumber * 2,
                 activityStorage.getNumberOfUserActivities(johnIdentity));
    assertEquals("activityStorage.getNumberOfUserActivities(maryIdentity) must return " + totalNumber,
                 totalNumber,
                 activityStorage.getNumberOfUserActivities(maryIdentity));
    assertEquals("activityStorage.getNumberOfUserActivities(demoIdentity) must return " + totalNumber,
                 totalNumber,
                 activityStorage.getNumberOfUserActivities(demoIdentity));

  }

  /**
   * Tests
   * {@link ActivityStorage#getNumberOfNewerOnUserActivities(Identity, ExoSocialActivity)}.
   */
  @MaxQueryNumber(615)
  public void testGetNumberOfNewerOnUserActivities() {
    checkCleanData();
    createActivities(2, demoIdentity);
    ExoSocialActivity firstActivity = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0);
    assertEquals(0, activityStorage.getNumberOfNewerOnUserActivities(demoIdentity, firstActivity));

    createActivities(1, johnIdentity);

    createActivities(1, demoIdentity);

    assertEquals(1, activityStorage.getNumberOfNewerOnUserActivities(demoIdentity, firstActivity));

  }

  /**
   * Tests
   * {@link ActivityStorage#getNumberOfOlderOnUserActivities(Identity, ExoSocialActivity)}.
   */
  @MaxQueryNumber(774)
  public void testGetNumberOfOlderOnUserActivities() {
    checkCleanData();
    createActivities(3, demoIdentity);
    List<ExoSocialActivity> userActivities = activityStorage.getUserActivities(demoIdentity, 0, 10);
    ExoSocialActivity secondActivity = userActivities.get(1);
    assertEquals(1, activityStorage.getNumberOfOlderOnUserActivities(demoIdentity, secondActivity));
    createActivities(2, demoIdentity);
    assertEquals(1, activityStorage.getNumberOfOlderOnUserActivities(demoIdentity, secondActivity));
    ExoSocialActivity newFirstActivity = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0);
    assertEquals(4, activityStorage.getNumberOfOlderOnUserActivities(demoIdentity, newFirstActivity));
  }

  /**
   * Tests
   * {@link ActivityStorage#getOlderOnUserActivities(Identity, ExoSocialActivity, int)}.
   */
  @MaxQueryNumber(912)
  public void testGetOlderOnUserActivities() {
    checkCleanData();
    createActivities(2, demoIdentity);
    ExoSocialActivity firstActivity = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0);
    assertEquals(1, activityStorage.getOlderOnUserActivities(demoIdentity, firstActivity, 10).size());
    createActivities(2, maryIdentity);
    assertEquals(1, activityStorage.getOlderOnUserActivities(demoIdentity, firstActivity, 10).size());
    createActivities(2, demoIdentity);
    assertEquals(1, activityStorage.getOlderOnUserActivities(demoIdentity, firstActivity, 10).size());
    firstActivity = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0);
    assertEquals(3, activityStorage.getOlderOnUserActivities(demoIdentity, firstActivity, 10).size());
  }

  /**
   * Tests {@link ActivityStorage#getActivityFeed(Identity, int, int)}.
   */
  @MaxQueryNumber(1608)
  public void testGetActivityFeed() throws Exception {
    createActivities(3, demoIdentity);
    createActivities(3, maryIdentity);
    createActivities(2, johnIdentity);

    List<ExoSocialActivity> demoActivityFeed = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals("demoActivityFeed.size() must be 3", 3, demoActivityFeed.size());

    Relationship demoMaryConnection = relationshipManager.invite(demoIdentity, maryIdentity);
    assertEquals(3, activityStorage.getActivityFeed(demoIdentity, 0, 10).size());

    relationshipManager.confirm(demoMaryConnection);

    List<ExoSocialActivity> demoActivityFeed2 = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals("demoActivityFeed2.size() must return 6", 6, demoActivityFeed2.size());
    List<ExoSocialActivity> maryActivityFeed = activityStorage.getActivityFeed(maryIdentity, 0, 10);
    assertEquals("maryActivityFeed.size() must return 6", 6, maryActivityFeed.size());
  }

  /**
   * Tests {@link ActivityStorage#getNumberOfActivitesOnActivityFeed(Identity)}.
   */
  @MaxQueryNumber(1293)
  public void testGetNumberOfActivitesOnActivityFeed() throws Exception {
    createActivities(3, demoIdentity);
    createActivities(2, maryIdentity);
    createActivities(1, johnIdentity);
    int demoActivityCount = activityStorage.getNumberOfActivitesOnActivityFeed(demoIdentity);
    assertEquals("demoActivityCount must be 3", 3, demoActivityCount);
    int maryActivityCount = activityStorage.getNumberOfActivitesOnActivityFeed(maryIdentity);
    assertEquals("maryActivityCount must be 2", 2, maryActivityCount);
    Relationship demoMaryConnection = relationshipManager.invite(demoIdentity, maryIdentity);
    int demoActivityCount2 = activityStorage.getNumberOfActivitesOnActivityFeed(demoIdentity);
    assertEquals("demoActivityCount2 must be 3", 3, demoActivityCount2);
    relationshipManager.confirm(demoMaryConnection);

    int demoActivityCount3 = activityStorage.getNumberOfActivitesOnActivityFeed(demoIdentity);
    assertEquals("demoActivityCount3 must be 5", 5, demoActivityCount3);
    int maryActivityCount2 = activityStorage.getNumberOfActivitesOnActivityFeed(maryIdentity);
    assertEquals("maryActivityCount2 must be 5", 5, maryActivityCount2);
  }

  /**
   * Tests
   * {@link ActivityStorage#getNumberOfActivitesOnActivityFeed(Identity, ExoSocialActivity)}.
   */
  @MaxQueryNumber(1908)
  public void testGetNumberOfNewerOnActivityFeed() throws Exception {
    createActivities(3, demoIdentity);
    createActivities(2, maryIdentity);
    Relationship maryDemoConnection = relationshipManager.invite(maryIdentity, demoIdentity);
    relationshipManager.confirm(maryDemoConnection);

    List<ExoSocialActivity> demoActivityFeed = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    ExoSocialActivity firstActivity = demoActivityFeed.get(0);
    int newDemoActivityFeed = activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, firstActivity);
    assertEquals("newDemoActivityFeed must be 0", 0, newDemoActivityFeed);
    createActivities(1, johnIdentity);
    int newDemoActivityFeed2 = activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, firstActivity);
    assertEquals("newDemoActivityFeed2 must be 0", 0, newDemoActivityFeed2);
    createActivities(1, demoIdentity);
    int newDemoActivityFeed3 = activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, firstActivity);
    assertEquals("newDemoActivityFeed3 must be 1", 1, newDemoActivityFeed3);
    createActivities(2, maryIdentity);
    int newDemoActivityFeed4 = activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, firstActivity);
    assertEquals("newDemoActivityFeed must be 3", 3, newDemoActivityFeed4);
  }

  /**
   * Tests
   * {@link ActivityStorage#getNumberOfOlderOnActivityFeed(Identity, ExoSocialActivity)}.
   */
  @MaxQueryNumber(1299)
  public void testGetNumberOfOlderOnActivityFeed() throws Exception {
    createActivities(3, demoIdentity);
    createActivities(2, maryIdentity);
    Relationship maryDemoConnection = relationshipManager.invite(maryIdentity, demoIdentity);
    relationshipManager.confirm(maryDemoConnection);

    List<ExoSocialActivity> demoActivityFeed = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    ExoSocialActivity lastDemoActivity = demoActivityFeed.get(4);
    int oldDemoActivityFeed = activityStorage.getNumberOfOlderOnActivityFeed(demoIdentity, lastDemoActivity);
    assertEquals("oldDemoActivityFeed must be 0", 0, oldDemoActivityFeed);
    createActivities(1, johnIdentity);

    int oldDemoActivityFeed2 = activityStorage.getNumberOfOlderOnActivityFeed(demoIdentity, lastDemoActivity);
    assertEquals("oldDemoActivityFeed2 must be 0", 0, oldDemoActivityFeed2);
    ExoSocialActivity nextLastDemoActivity = demoActivityFeed.get(3);
    int oldDemoActivityFeed3 = activityStorage.getNumberOfOlderOnActivityFeed(demoIdentity, nextLastDemoActivity);
    assertEquals("oldDemoActivityFeed3 must be 1", 1, oldDemoActivityFeed3);
  }

  /**
   * Test {@link ActivityStorage#getActivitiesOfConnections(Identity, int, int)}
   */
  @MaxQueryNumber(3195)
  public void testGetActivitiesOfConnections() throws Exception {
    List<Relationship> relationships = new ArrayList<Relationship>();

    this.createActivities(2, rootIdentity);
    this.createActivities(1, demoIdentity);
    this.createActivities(2, johnIdentity);
    this.createActivities(3, maryIdentity);

    List<ExoSocialActivity> activities = activityStorage.getActivitiesOfConnections(demoIdentity, 0, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals(0, activities.size());

    RelationshipManager relationshipManager = this.getRelationshipManager();

    Relationship rootDemoRelationship = relationshipManager.invite(rootIdentity, demoIdentity);
    relationshipManager.confirm(rootDemoRelationship);
    relationships.add(rootDemoRelationship);

    activities = activityStorage.getActivitiesOfConnections(rootIdentity, 0, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 1", 1, activities.size());

    Relationship rootMaryRelationship = relationshipManager.invite(rootIdentity, maryIdentity);
    relationshipManager.confirm(rootMaryRelationship);
    relationships.add(rootMaryRelationship);

    activities = activityStorage.getActivitiesOfConnections(rootIdentity, 0, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 4", 4, activities.size());

    Relationship rootJohnRelationship = relationshipManager.invite(rootIdentity, johnIdentity);
    relationshipManager.confirm(rootJohnRelationship);
    relationships.add(rootJohnRelationship);
    activities = activityStorage.getActivitiesOfConnections(rootIdentity, 0, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 6", 6, activities.size());

    for (Relationship rel : relationships) {
      relationshipManager.remove(rel);
    }
  }

  /**
   * Test
   * {@link ActivityStorage#testGetActivitiesRelationshipByFeed(Identity, int, int)}
   */
  @MaxQueryNumber(1131)
  public void testGetActivitiesRelationshipByFeed() throws Exception {
    RelationshipManager relationshipManager = this.getRelationshipManager();

    Relationship rootDemoRelationship = relationshipManager.inviteToConnect(rootIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, rootIdentity);

    Relationship johnDemoRelationship = relationshipManager.inviteToConnect(demoIdentity, johnIdentity);
    relationshipManager.confirm(demoIdentity, johnIdentity);

    Relationship johnMaryRelationship = relationshipManager.inviteToConnect(johnIdentity, maryIdentity);
    relationshipManager.confirm(johnIdentity, maryIdentity);

    List<ExoSocialActivity> activities = activityStorage.getActivityFeed(maryIdentity, 0, 10);
    assertEquals(0, activities.size());

    //
    ExoSocialActivity demoActivity = new ExoSocialActivityImpl();
    demoActivity.setTitle("Activity of root.");
    demoActivity.setUserId(demoIdentity.getId());
    activityStorage.saveActivity(johnIdentity, demoActivity);

    activities = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals(1, activities.size());

    activities = activityStorage.getActivityFeed(rootIdentity, 0, 10);
    assertEquals(1, activities.size());

    activities = activityStorage.getActivityFeed(johnIdentity, 0, 10);
    assertEquals(1, activities.size());

    activities = activityStorage.getActivityFeed(maryIdentity, 0, 10);
    assertEquals(0, activities.size());

    //
    tearDownActivityList.add(demoActivity);
    relationshipManager.delete(rootDemoRelationship);
    relationshipManager.delete(johnMaryRelationship);
    relationshipManager.delete(johnDemoRelationship);
  }

  /**
   * Test {@link ActivityStorage#getActivitiesOfConnections(Identity, int, int)}
   * for issue SOC-1995
   * 
   * @throws Exception
   * @since 1.2.2
   */
  @MaxQueryNumber(1062)
  public void testGetActivitiesOfConnectionsWithPosterIdentity() throws Exception {
    RelationshipManager relationshipManager = this.getRelationshipManager();
    List<Relationship> relationships = new ArrayList<Relationship>();

    Relationship johnDemoIdentity = relationshipManager.inviteToConnect(johnIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, johnIdentity);
    johnDemoIdentity = relationshipManager.get(johnDemoIdentity.getId());
    relationships.add(johnDemoIdentity);

    Relationship demoMaryIdentity = relationshipManager.inviteToConnect(demoIdentity, maryIdentity);
    relationshipManager.confirm(maryIdentity, demoIdentity);
    demoMaryIdentity = relationshipManager.get(demoMaryIdentity.getId());
    relationships.add(demoMaryIdentity);

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("Hello Demo from Mary");
    activity.setPosterId(maryIdentity.getId());
    activityStorage.saveActivity(demoIdentity, activity);
    tearDownActivityList.add(activity);

    activity = activityStorage.getActivity(activity.getId());
    assertNotNull("activity must not be null", activity);
    assertEquals("activity.getStreamOwner() must return: demo", "demo", activity.getStreamOwner());
    assertEquals("activity.getUserId() must return: " + maryIdentity.getId(), maryIdentity.getId(), activity.getUserId());

    List<ExoSocialActivity> johnConnectionActivities = activityStorage.getActivitiesOfConnections(johnIdentity, 0, 10);
    assertNotNull("johnConnectionActivities must not be null", johnConnectionActivities);
    assertEquals("johnConnectionActivities.size() must return: 0", 0, johnConnectionActivities.size());

    List<ExoSocialActivity> demoConnectionActivities = activityStorage.getActivitiesOfConnections(demoIdentity, 0, 10);
    assertNotNull("demoConnectionActivities must not be null", demoConnectionActivities);
    assertEquals(1, demoConnectionActivities.size());

    List<ExoSocialActivity> maryConnectionActivities = activityStorage.getActivitiesOfConnections(maryIdentity, 0, 10);
    assertNotNull("maryConnectionActivities must not be null", maryConnectionActivities);
    assertEquals("maryConnectionActivities.size() must return: 0", 0, maryConnectionActivities.size());

    for (Relationship rel : relationships) {
      relationshipManager.delete(rel);
    }
  }

  /**
   * Test {@link ActivityStorage#getNumberOfActivitiesOfConnections(Identity)}
   * 
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(3189)
  public void testGetNumberOfActivitiesOfConnections() throws Exception {
    List<Relationship> relationships = new ArrayList<Relationship>();

    this.createActivities(2, rootIdentity);
    this.createActivities(1, demoIdentity);
    this.createActivities(2, johnIdentity);
    this.createActivities(3, maryIdentity);

    int count = activityStorage.getNumberOfActivitiesOfConnections(demoIdentity);
    assertEquals(0, count);

    RelationshipManager relationshipManager = this.getRelationshipManager();

    Relationship rootDemoRelationship = relationshipManager.invite(rootIdentity, demoIdentity);
    relationshipManager.confirm(rootDemoRelationship);
    relationships.add(rootDemoRelationship);

    count = activityStorage.getNumberOfActivitiesOfConnections(rootIdentity);
    assertEquals(1, count);

    Relationship rootMaryRelationship = relationshipManager.invite(rootIdentity, maryIdentity);
    relationshipManager.confirm(rootMaryRelationship);
    relationships.add(rootMaryRelationship);

    count = activityStorage.getNumberOfActivitiesOfConnections(rootIdentity);
    assertEquals("count must be: 4", 4, count);

    Relationship rootJohnRelationship = relationshipManager.invite(rootIdentity, johnIdentity);
    relationshipManager.confirm(rootJohnRelationship);
    relationships.add(rootJohnRelationship);

    count = activityStorage.getNumberOfActivitiesOfConnections(rootIdentity);
    assertEquals("count must be: 6", 6, count);

    for (Relationship rel : relationships) {
      relationshipManager.remove(rel);
    }
  }

  /**
   * Test
   * {@link ActivityStorage#getNumberOfNewerOnActivitiesOfConnections(Identity, ExoSocialActivity)}
   * 
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(2985)
  public void testGetNumberOfNewerOnActivitiesOfConnections() {
    List<Relationship> relationships = new ArrayList<Relationship>();

    this.createActivities(3, maryIdentity);
    this.createActivities(1, demoIdentity);
    this.createActivities(2, johnIdentity);
    this.createActivities(2, rootIdentity);

    List<ExoSocialActivity> demoActivities = activityStorage.getActivitiesOfIdentity(demoIdentity, 0, 10);
    assertNotNull("demoActivities must not be null", demoActivities);
    assertEquals("demoActivities.size() must return: 1", 1, demoActivities.size());

    ExoSocialActivity baseActivity = demoActivities.get(0);

    int count = activityStorage.getNumberOfNewerOnActivitiesOfConnections(johnIdentity, baseActivity);
    assertEquals("count must be: 2", 2, count);

    count = activityStorage.getNumberOfNewerOnActivitiesOfConnections(demoIdentity, baseActivity);
    assertEquals("count must be: 0", 0, count);

    RelationshipManager relationshipManager = this.getRelationshipManager();

    Relationship demoJohnRelationship = relationshipManager.invite(demoIdentity, johnIdentity);
    relationshipManager.confirm(demoJohnRelationship);
    relationships.add(demoJohnRelationship);

    count = activityStorage.getNumberOfNewerOnActivitiesOfConnections(demoIdentity, baseActivity);
    assertEquals("count must be: 2", 2, count);

    Relationship demoMaryRelationship = relationshipManager.invite(demoIdentity, maryIdentity);
    relationshipManager.confirm(demoMaryRelationship);
    relationships.add(demoMaryRelationship);

    count = activityStorage.getNumberOfNewerOnActivitiesOfConnections(demoIdentity, baseActivity);
    assertEquals("count must be: 2", 2, count);

    Relationship demoRootRelationship = relationshipManager.invite(demoIdentity, rootIdentity);
    relationshipManager.confirm(demoRootRelationship);
    relationships.add(demoRootRelationship);

    count = activityStorage.getNumberOfNewerOnActivitiesOfConnections(demoIdentity, baseActivity);
    assertEquals("count must be: 4", 4, count);

    for (Relationship rel : relationships) {
      relationshipManager.remove(rel);
    }
  }

  /**
   * Test
   * {@link ActivityStorage#getNumberOfOlderOnActivitiesOfConnections(Identity, ExoSocialActivity)}
   * 
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(6970)
  public void testGetNumberOfOlderOnActivitiesOfConnections() {
    List<Relationship> relationships = new ArrayList<Relationship>();

    this.createActivities(3, maryIdentity);
    this.createActivities(1, demoIdentity);
    this.createActivities(2, johnIdentity);
    this.createActivities(2, rootIdentity);

    List<ExoSocialActivity> rootActivities = activityStorage.getActivitiesOfIdentity(rootIdentity, 0, 10);
    assertNotNull("rootActivities must not be null", rootActivities);
    assertEquals("rootActivities.size() must return: 2", 2, rootActivities.size());

    ExoSocialActivity baseActivity = rootActivities.get(1);

    int count = activityStorage.getNumberOfOlderOnActivitiesOfConnections(rootIdentity, baseActivity);
    assertEquals("count must be: 0", 0, count);

    count = activityStorage.getNumberOfOlderOnActivitiesOfConnections(johnIdentity, baseActivity);
    assertEquals("count must be: 2", 2, count);

    RelationshipManager relationshipManager = this.getRelationshipManager();

    Relationship rootJohnRelationship = relationshipManager.invite(rootIdentity, johnIdentity);
    relationshipManager.confirm(rootJohnRelationship);
    relationships.add(rootJohnRelationship);

    count = activityStorage.getNumberOfOlderOnActivitiesOfConnections(rootIdentity, baseActivity);
    assertEquals("count must be: 2", 2, count);

    Relationship rootDemoRelationship = relationshipManager.invite(rootIdentity, demoIdentity);
    relationshipManager.confirm(rootDemoRelationship);
    relationships.add(rootDemoRelationship);

    count = activityStorage.getNumberOfOlderOnActivitiesOfConnections(rootIdentity, baseActivity);
    assertEquals("count must be: 3", 3, count);

    Relationship rootMaryRelationship = relationshipManager.invite(rootIdentity, maryIdentity);
    relationshipManager.confirm(rootMaryRelationship);
    relationships.add(rootMaryRelationship);

    count = activityStorage.getNumberOfOlderOnActivitiesOfConnections(rootIdentity, baseActivity);
    assertEquals("count must be: 6", 6, count);

    for (Relationship rel : relationships) {
      relationshipManager.remove(rel);
    }
  }

  /**
   * Test {@link ActivityStorage#getUserSpacesActivities(Identity, int, int)}
   * 
   * @throws Exception
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(5021)
  public void testGetUserSpacesActivities() throws Exception {
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 10;

    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
    }

    space = spaceService.getSpaceByDisplayName(space.getDisplayName());
    assertNotNull("space must not be null", space);
    assertEquals("space.getDisplayName() must return: my space 0", "my space 0", space.getDisplayName());
    assertEquals("space.getDescription() must return: add new space 0", "add new space 0", space.getDescription());

    List<ExoSocialActivity> demoActivities = activityStorage.getUserSpacesActivities(demoIdentity, 0, 10);
    assertNotNull("demoActivities must not be null", demoActivities);
    assertEquals("demoActivities.size() must return: 10", 10, demoActivities.size());

    Space space2 = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space2.getPrettyName(), false);

    // demo posts activities to space2
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      tearDownActivityList.add(activity);
    }

    space2 = spaceService.getSpaceByDisplayName(space2.getDisplayName());
    assertNotNull("space2 must not be null", space2);
    assertEquals("space2.getDisplayName() must return: my space 1", "my space 1", space2.getDisplayName());
    assertEquals("space2.getDescription() must return: add new space 1", "add new space 1", space2.getDescription());

    demoActivities = activityStorage.getUserSpacesActivities(demoIdentity, 0, 20);
    assertNotNull("demoActivities must not be null", demoActivities);
    assertEquals("demoActivities.size() must return: 20", 20, demoActivities.size());

    demoActivities = activityStorage.getUserSpacesActivities(demoIdentity, 0, 10);
    assertNotNull("demoActivities must not be null", demoActivities);
    assertEquals("demoActivities.size() must return: 10", 10, demoActivities.size());

    demoActivities = activityStorage.getUserSpacesActivities(johnIdentity, 0, 10);
    assertNotNull("demoActivities must not be null", demoActivities);
    assertEquals("demoActivities.size() must return: 0", 0, demoActivities.size());

  }

  public void testGetUserSpacesActivityIds() throws Exception {
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);
    int totalNumber = 10;

    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
    }

    List<String> demoActivities = activityStorage.getUserSpacesActivityIds(demoIdentity, 0, 10);
    assertNotNull("demoActivities must not be null", demoActivities);
    assertEquals("demoActivities.size() must return: 10", 10, demoActivities.size());
  }

  public void testGetActivitiesAfterRemoveSpace() throws Exception {
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 10;

    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
    }

    List<ExoSocialActivity> spacesActivities = activityStorage.getUserSpacesActivities(demoIdentity, 0, 10);
    assertEquals("spacesActivities.size() must return: 10", 10, spacesActivities.size());
    List<ExoSocialActivity> myActivities = activityStorage.getUserActivities(demoIdentity, 0, 10);
    assertEquals("myActivities.size() must return: 10", 10, myActivities.size());
    List<ExoSocialActivity> feedActivities = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals("feedActivities.size() must return: 10", 10, feedActivities.size());

    // delete space
    spaceService.deleteSpace(space);

    spacesActivities = activityStorage.getUserSpacesActivities(demoIdentity, 0, 10);
    assertEquals(10, spacesActivities.size());
    feedActivities = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals(10, feedActivities.size());
    myActivities = activityStorage.getUserActivities(demoIdentity, 0, 10);
    assertEquals(10, myActivities.size());

  }

  /**
   * Test {@link ActivityStorage#getNumberOfUserSpacesActivities(Identity)}
   * 
   * @throws Exception
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(5012)
  public void testGetNumberOfUserSpacesActivities() throws Exception {
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 10;

    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
    }

    space = spaceService.getSpaceByDisplayName(space.getDisplayName());
    assertNotNull("space must not be null", space);
    assertEquals("space.getDisplayName() must return: my space 0", "my space 0", space.getDisplayName());
    assertEquals("space.getDescription() must return: add new space 0", "add new space 0", space.getDescription());

    int number = activityStorage.getNumberOfUserSpacesActivities(demoIdentity);
    assertEquals("number must be: 10", 10, number);

    Space space2 = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space2.getPrettyName(), false);

    // demo posts activities to space2
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      tearDownActivityList.add(activity);
    }

    space2 = spaceService.getSpaceByDisplayName(space2.getDisplayName());
    assertNotNull("space2 must not be null", space2);
    assertEquals("space2.getDisplayName() must return: my space 1", "my space 1", space2.getDisplayName());
    assertEquals("space2.getDescription() must return: add new space 1", "add new space 1", space2.getDescription());

    number = activityStorage.getNumberOfUserSpacesActivities(demoIdentity);
    assertEquals("number must be: 20", 20, number);

  }

  /**
   * Test
   * {@link ActivityStorage#getNumberOfNewerOnUserSpacesActivities(Identity, ExoSocialActivity)}
   * 
   * @throws Exception
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(5012)
  public void testGetNumberOfNewerOnUserSpacesActivities() throws Exception {
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 10;

    ExoSocialActivity baseActivity = null;

    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
      if (i == 0) {
        baseActivity = activity;
      }
    }

    space = spaceService.getSpaceByDisplayName(space.getDisplayName());
    assertNotNull("space must not be null", space);
    assertEquals("space.getDisplayName() must return: my space 0", "my space 0", space.getDisplayName());
    assertEquals("space.getDescription() must return: add new space 0", "add new space 0", space.getDescription());

    int number = activityStorage.getNumberOfNewerOnUserSpacesActivities(demoIdentity, baseActivity);
    assertEquals("number must be: 9", 9, number);

    Space space2 = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space2.getPrettyName(), false);

    // demo posts activities to space2
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      tearDownActivityList.add(activity);
    }

    space2 = spaceService.getSpaceByDisplayName(space2.getDisplayName());
    assertNotNull("space2 must not be null", space2);
    assertEquals("space2.getDisplayName() must return: my space 1", "my space 1", space2.getDisplayName());
    assertEquals("space2.getDescription() must return: add new space 1", "add new space 1", space2.getDescription());

    number = activityStorage.getNumberOfNewerOnUserSpacesActivities(demoIdentity, baseActivity);
    assertEquals("number must be: 19", 19, number);

  }

  /**
   * Tests
   * {@link ActivityStorage#getNumberOfNewerOnUserActivities(Identity, Long)}
   */
  @MaxQueryNumber(1155)
  public void testGetNumberOfNewerOnUserActivitiesByTimestamp() {
    checkCleanData();
    createActivities(2, demoIdentity);
    Long sinceTime = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0).getPostedTime();
    assertEquals(0, activityStorage.getNumberOfNewerOnUserActivities(demoIdentity, sinceTime));
    createActivities(2, maryIdentity);
    assertEquals(0, activityStorage.getNumberOfNewerOnUserActivities(demoIdentity, sinceTime));
    createActivities(2, demoIdentity);
    assertEquals(2, activityStorage.getNumberOfNewerOnUserActivities(demoIdentity, sinceTime));

    // Delete the activity at this sinceTime will don't change the result
    // We just add 2 more activities of demoIdentity so the position of the
    // activity that we get the sinceTime has
    // changed from 0 to 2
    String id = activityStorage.getUserActivities(demoIdentity, 0, 10).get(2).getId();
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }
    activityStorage.deleteActivity(id);
    assertEquals(2, activityStorage.getNumberOfNewerOnUserActivities(demoIdentity, sinceTime));
  }

  /**
   * Tests {@link ActivityStorage#getNewerOnActivityFeed(Identity, Long, int)}.
   */
  @MaxQueryNumber(2007)
  public void testGetNumberOfNewerOnActivityFeedByTimestamp() {
    createActivities(3, demoIdentity);
    Long sinceTime = activityStorage.getActivityFeed(demoIdentity, 0, 10).get(0).getPostedTime();
    assertEquals(0, activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, sinceTime));
    createActivities(1, demoIdentity);
    assertEquals(1, activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, sinceTime));
    createActivities(2, maryIdentity);
    relationshipManager.inviteToConnect(demoIdentity, maryIdentity);
    assertEquals(1, activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, sinceTime));
    relationshipManager.confirm(demoIdentity, maryIdentity);
    createActivities(2, maryIdentity);
    assertEquals(5, activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, sinceTime));

    // Delete the activity at this sinceTime will don't change the result
    String id = activityStorage.getUserActivities(demoIdentity, 0, 10).get(1).getId();
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }
    activityStorage.deleteActivity(id);
    assertEquals(5, activityStorage.getNumberOfNewerOnActivityFeed(demoIdentity, sinceTime));
  }

  /**
   * Test
   * {@link ActivityStorage#getNewerOnActivitiesOfConnections(Identity, Long, int)}
   * 
   * @since 1.2.12
   */
  @MaxQueryNumber(3558)
  public void testGetNumberOfNewerOnActivitiesOfConnectionsByTimestamp() throws Exception {
    List<Relationship> relationships = new ArrayList<Relationship>();
    this.createActivities(3, maryIdentity);
    this.createActivities(1, demoIdentity);
    this.createActivities(2, johnIdentity);
    this.createActivities(2, rootIdentity);

    List<ExoSocialActivity> maryActivities = activityStorage.getActivitiesOfIdentity(maryIdentity, 0, 10);
    assertNotNull("maryActivities must not be null", maryActivities);
    assertEquals(3, maryActivities.size());

    Long sinceTime = maryActivities.get(2).getPostedTime();

    assertEquals(2, activityStorage.getNumberOfNewerOnActivitiesOfConnections(johnIdentity, sinceTime));

    assertEquals(1, activityStorage.getNumberOfNewerOnActivitiesOfConnections(demoIdentity, sinceTime));

    assertEquals(2, activityStorage.getNumberOfNewerOnActivitiesOfConnections(maryIdentity, sinceTime));

    RelationshipManager relationshipManager = this.getRelationshipManager();
    Relationship maryDemoRelationship = relationshipManager.inviteToConnect(maryIdentity, demoIdentity);
    relationshipManager.confirm(maryIdentity, demoIdentity);
    relationships.add(maryDemoRelationship);

    assertEquals(3, activityStorage.getNumberOfNewerOnActivitiesOfConnections(maryIdentity, sinceTime));

    assertEquals(3, activityStorage.getNumberOfNewerOnActivitiesOfConnections(demoIdentity, sinceTime));

    // Delete the activity at this sinceTime will don't change the result
    String id = activityStorage.getUserActivities(maryIdentity, 0, 10).get(2).getId();
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }
    activityStorage.deleteActivity(id);
    assertEquals(3, activityStorage.getNumberOfNewerOnActivitiesOfConnections(demoIdentity, sinceTime));

    Relationship maryJohnRelationship = relationshipManager.inviteToConnect(maryIdentity, johnIdentity);
    relationshipManager.confirm(maryIdentity, johnIdentity);
    relationships.add(maryJohnRelationship);

    assertEquals(5, activityStorage.getNumberOfNewerOnActivitiesOfConnections(maryIdentity, sinceTime));

    Relationship maryRootRelationship = relationshipManager.inviteToConnect(maryIdentity, rootIdentity);
    relationshipManager.confirm(maryIdentity, rootIdentity);
    relationships.add(maryRootRelationship);

    assertEquals(7, activityStorage.getNumberOfNewerOnActivitiesOfConnections(maryIdentity, sinceTime));

    for (Relationship rel : relationships) {
      relationshipManager.delete(rel);
    }
  }

  /**
   * Test
   * {@link ActivityStorage#getNewerOnUserSpacesActivities(Identity, Long, int)}
   * 
   * @throws Exception
   * @since 1.2.12
   */
  @MaxQueryNumber(2503)
  public void testGetNumberOfNewerOnUserSpacesActivitiesByTimestamp() throws Exception {
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 10;

    long sinceTime = 0;

    String id = "";
    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
      if (i == 0) {
        sinceTime = activity.getPostedTime();
        id = activity.getId();
      }
    }

    space = spaceService.getSpaceByDisplayName(space.getDisplayName());
    assertNotNull("space must not be null", space);
    assertEquals("my space 0", space.getDisplayName());
    assertEquals("add new space 0", space.getDescription());

    assertEquals(9, activityStorage.getNumberOfNewerOnUserSpacesActivities(demoIdentity, sinceTime));

  }

  /**
   * Test
   * {@link ActivityStorage#getNumberOfOlderOnUserSpacesActivities(Identity, ExoSocialActivity)}
   * 
   * @throws Exception
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(5012)
  public void testGetNumberOfOlderOnUserSpacesActivities() throws Exception {
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 10;

    ExoSocialActivity baseActivity = null;

    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
      if (i == totalNumber - 1) {
        baseActivity = activity;
      }
    }

    space = spaceService.getSpaceByDisplayName(space.getDisplayName());
    assertNotNull("space must not be null", space);
    assertEquals("space.getDisplayName() must return: my space 0", "my space 0", space.getDisplayName());
    assertEquals("space.getDescription() must return: add new space 0", "add new space 0", space.getDescription());

    int number = activityStorage.getNumberOfOlderOnUserSpacesActivities(demoIdentity, baseActivity);
    assertEquals("number must be: 9", 9, number);

    Space space2 = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space2.getPrettyName(), false);

    // demo posts activities to space2
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      tearDownActivityList.add(activity);
      if (i == totalNumber - 1) {
        baseActivity = activity;
      }
    }

    space2 = spaceService.getSpaceByDisplayName(space2.getDisplayName());
    assertNotNull("space2 must not be null", space2);
    assertEquals("space2.getDisplayName() must return: my space 1", "my space 1", space2.getDisplayName());
    assertEquals("space2.getDescription() must return: add new space 1", "add new space 1", space2.getDescription());

    number = activityStorage.getNumberOfOlderOnUserSpacesActivities(demoIdentity, baseActivity);
    assertEquals("number must be: 19", 19, number);

  }

  /**
   * Test {@link ActivityStorage#getComments(ExoSocialActivity, int, int)}
   * 
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(23019)
  public void testGetComments() {
    int totalNumber = 40;
    String activityTitle = "activity title";

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle(activityTitle);
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("Comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    List<ExoSocialActivity> comments = activityStorage.getComments(activity, false, 0, 40);
    assertNotNull("comments must not be null", comments);
    assertEquals("comments.size() must return: 40", 40, comments.size());
  }

  /**
   * Test {@link ActivityStorage#getNumberOfComments(ExoSocialActivity)}
   * 
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(23019)
  public void testGetNumberOfComments() {
    int totalNumber = 40;
    String activityTitle = "activity title";

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle(activityTitle);
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("Comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    List<ExoSocialActivity> comments = activityStorage.getComments(activity, false, 0, 40);
    assertNotNull("comments must not be null", comments);
    assertEquals("comments.size() must return: 40", 40, comments.size());

    int number = activityStorage.getNumberOfComments(activity);
    assertEquals("number must be: 40", 40, number);
  }

  /**
   * Test
   * {@link ActivityStorage#getNumberOfNewerComments(ExoSocialActivity, ExoSocialActivity)}
   * 
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(13707)
  public void testGetNumberOfNewerComments() {
    int totalNumber = 10;
    String activityTitle = "activity title";

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle(activityTitle);
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("john comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("demo comment " + i);
      comment.setUserId(demoIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    List<ExoSocialActivity> comments = activityStorage.getComments(activity, false, 0, 10);
    assertNotNull("comments must not be null", comments);
    assertEquals("comments.size() must return: 10", 10, comments.size());

    ExoSocialActivity latestComment = comments.get(0);

    int number = activityStorage.getNumberOfNewerComments(activity, latestComment);
    assertEquals("number must be: 0", 0, number);

    ExoSocialActivity baseComment = activityStorage.getComments(activity, false, 0, 20).get(10);
    number = activityStorage.getNumberOfNewerComments(activity, baseComment);
    assertEquals("number must be: 10", 10, number);

    baseComment = activityStorage.getComments(activity, false, 0, 20).get(19);
    number = activityStorage.getNumberOfNewerComments(activity, baseComment);
    assertEquals("number must be: 19", 19, number);
  }

  /**
   * Test
   * {@link ActivityStorage#getNumberOfOlderComments(ExoSocialActivity, ExoSocialActivity)}
   * 
   * @since 1.2.0-Beta3
   */
  @MaxQueryNumber(5559)
  public void testGetNumberOfOlderComments() {
    int totalNumber = 10;
    String activityTitle = "activity title";

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle(activityTitle);
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("john comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    List<ExoSocialActivity> comments = activityStorage.getComments(activity, false, 0, 10);
    assertNotNull("comments must not be null", comments);
    assertEquals("comments.size() must return: 10", 10, comments.size());

    ExoSocialActivity baseComment = comments.get(0);

    int number = activityStorage.getNumberOfOlderComments(activity, baseComment);
    assertEquals("number must be: 9", 9, number);

    baseComment = comments.get(9);

    number = activityStorage.getNumberOfOlderComments(activity, baseComment);
    assertEquals("number must be: 0", 0, number);

    baseComment = comments.get(5);

    number = activityStorage.getNumberOfOlderComments(activity, baseComment);
    assertEquals("number must be: 4", 4, number);
  }

  /**
   * @throws ActivityStorageException
   */
  @MaxQueryNumber(177)
  public void testGetStreamInfo() throws ActivityStorageException {
    checkCleanData();
    // root save on root's stream
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("blabla");
    activity.setUpdated(new Date().getTime());
    activity.setUserId(demoIdentity.getId());
    activityStorage.saveActivity(demoIdentity, activity);

    String streamId = activity.getStreamId();
    assertNotNull("streamId must not be null", streamId);
    assertEquals(activity.getStreamOwner(), demoIdentity.getRemoteId());

    ActivityStream activityStream = activity.getActivityStream();

    assertEquals("activityStream.getId() must return: " + streamId, streamId, activityStream.getId());

    assertEquals("activityStream.getPrettyId() must return: " + demoIdentity.getRemoteId(),
                 demoIdentity.getRemoteId(),
                 activityStream.getPrettyId());

    assertNotNull(activityStream.getPermaLink());

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(demoIdentity, 0, 100);
    assertEquals(1, activities.size());
    assertEquals(demoIdentity.getRemoteId(), activities.get(0).getStreamOwner());
    assertEquals(streamId, activities.get(0).getStreamId());

    ExoSocialActivity loaded = activityStorage.getActivity(activity.getId());
    assertEquals(demoIdentity.getRemoteId(), loaded.getStreamOwner());
    assertEquals(streamId, loaded.getStreamId());

    tearDownActivityList.add(activity);
  }

  /**
   * Test {@link ActivityStorage#getUserActivities(Identity, long, long)}
   * 
   * @throws ActivityStorageException
   */
  @MaxQueryNumber(1371)
  public void testGetActivitiesByPagingWithoutCreatingComments() throws ActivityStorageException {
    checkCleanData();
    final int totalActivityCount = 9;
    final int retrievedCount = 7;

    for (int i = 0; i < totalActivityCount; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("blabla");
      activityStorage.saveActivity(demoIdentity, activity);
      tearDownActivityList.add(activity);
    }

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(demoIdentity, 0, retrievedCount);
    assertEquals(retrievedCount, activities.size());
  }

  /**
   * Test {@link ActivityStorage#getUserActivities(Identity, long, long) and
   * ActivityStorage#saveComment(ExoSocialActivity, ExoSocialActivity)}
   * 
   * @throws ActivityStorageException
   */
  @MaxQueryNumber(663)
  public void testGetActivitiesByPagingWithCreatingComments() throws ActivityStorageException {
    checkCleanData();

    final int totalActivityCount = 2;
    final int retrievedCount = 1;

    for (int i = 0; i < totalActivityCount; i++) {
      // root save on john's stream
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("blabla");
      activity.setUserId(johnIdentity.getId());

      activityStorage.saveActivity(johnIdentity, activity);
      activity = activityStorage.getUserActivities(johnIdentity, 0, 1).get(0);

      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("this is comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);

    }

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(johnIdentity, 0, retrievedCount);
    assertEquals(retrievedCount, activities.size());

    ;
    // for teardown cleanup
    tearDownActivityList.addAll(activityStorage.getUserActivities(johnIdentity, 0, 10));
  }

  /**
   * @throws ActivityStorageException
   */
  @MaxQueryNumber(183)
  public void testTemplateParams() throws ActivityStorageException {
    checkCleanData();
    final String URL_PARAMS = "URL";
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("blabla");
    activity.setUserId(rootIdentity.getId());
    activity.setUpdated(new Date().getTime());

    Map<String, String> templateParams = new HashMap<String, String>();
    templateParams.put(URL_PARAMS, "http://xxxxxxxxxxxxxxxx/xxxx=xxxxx");
    activity.setTemplateParams(templateParams);

    activityStorage.saveActivity(rootIdentity, activity);

    tearDownActivityList.add(activity);

    activity = activityStorage.getUserActivities(rootIdentity, 0, 100).get(0);
    assertNotNull("activity must not be null", activity);
    assertNotNull("activity.getTemplateParams() must not be null", activity.getTemplateParams());
    assertEquals("http://xxxxxxxxxxxxxxxx/xxxx=xxxxx", activity.getTemplateParams().get(URL_PARAMS));
  }

  /**
   * Test {@link ActivityStorage#getComments(ExoSocialActivity, int, int)}
   * 
   * @since 4.0
   */
  @MaxQueryNumber(2904)
  public void testGetHiddenComments() {
    int totalNumber = 5;
    String activityTitle = "activity title";

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle(activityTitle);
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("Comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    List<ExoSocialActivity> comments = activityStorage.getComments(activity, false, 0, 5);
    assertEquals("comments.size() must return: 5", 5, comments.size());

    ExoSocialActivity hiddenComment = comments.get(3);
    hiddenComment.isHidden(true);
    activityStorage.updateActivity(hiddenComment);
    List<ExoSocialActivity> newCommentsList = activityStorage.getComments(activity, false, 0, 5);
    assertEquals("newCommentsList.size() must return: 4", 4, newCommentsList.size());

    // get 2 lastest comments
    newCommentsList = activityStorage.getComments(activity, false, 2, 2);
    assertEquals("newCommentsList.size() must return: 2", 2, newCommentsList.size());
    assertEquals("Comment 2", newCommentsList.get(0).getTitle());
    assertEquals("Comment 4", newCommentsList.get(1).getTitle());
  }

  /**
   * Tests {@link ActivityStorage#getNewerOnUserActivities(Identity, Long, int)}
   */
  @MaxQueryNumber(1155)
  public void testGetNewerOnUserActivitiesWithTimestamp() {
    checkCleanData();
    createActivities(2, demoIdentity);
    Long sinceTime = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0).getUpdated().getTime();
    assertEquals(0, activityStorage.getNewerUserActivities(demoIdentity, sinceTime, 10).size());
    createActivities(2, maryIdentity);
    assertEquals(0, activityStorage.getNewerUserActivities(demoIdentity, sinceTime, 10).size());
    createActivities(2, demoIdentity);
    assertEquals(2, activityStorage.getNewerUserActivities(demoIdentity, sinceTime, 10).size());

    // Delete the activity at this sinceTime will don't change the result
    // We just add 2 more activities of demoIdentity so the position of the
    // activity that we get the sinceTime has
    // changed from 0 to 2
    String id = activityStorage.getUserActivities(demoIdentity, 0, 10).get(2).getId();
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }
    activityStorage.deleteActivity(id);
    assertEquals(2, activityStorage.getNewerUserActivities(demoIdentity, sinceTime, 10).size());
  }

  /**
   * Tests {@link ActivityStorage#getOlderOnUserActivities(Identity, Long, int)}
   */
  @MaxQueryNumber(1155)
  public void testGetOlderOnUserActivitiesWithTimestamp() {
    checkCleanData();
    createActivities(2, demoIdentity);
    Long maxTime = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0).getUpdated().getTime();
    assertEquals(1, activityStorage.getOlderUserActivities(demoIdentity, maxTime, 10).size());
    createActivities(2, maryIdentity);
    assertEquals(1, activityStorage.getOlderUserActivities(demoIdentity, maxTime, 10).size());
    createActivities(2, demoIdentity);
    assertEquals(1, activityStorage.getOlderUserActivities(demoIdentity, maxTime, 10).size());
    maxTime = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0).getUpdated().getTime();
    assertEquals(3, activityStorage.getOlderUserActivities(demoIdentity, maxTime, 10).size());

    // Delete the activity at this maxTime will don't change the result
    String id = activityStorage.getUserActivities(demoIdentity, 0, 10).get(0).getId();
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }
    activityStorage.deleteActivity(id);
    assertEquals(3, activityStorage.getOlderUserActivities(demoIdentity, maxTime, 10).size());
  }

  /**
   * Tests {@link ActivityStorage#getNewerOnActivityFeed(Identity, Long, int)}.
   */
  @MaxQueryNumber(2037)
  public void testGetNewerOnActivityFeedWithTimestamp() throws Exception {
    checkCleanData();
    createActivities(3, demoIdentity);
    Long sinceTime = activityStorage.getActivityFeed(demoIdentity, 0, 10).get(0).getUpdated().getTime();
    assertEquals(0, activityStorage.getNewerFeedActivities(demoIdentity, sinceTime, 10).size());
    createActivities(1, demoIdentity);
    assertEquals(1, activityStorage.getNewerFeedActivities(demoIdentity, sinceTime, 10).size());
    createActivities(2, maryIdentity);
    relationshipManager.inviteToConnect(demoIdentity, maryIdentity);
    assertEquals(1, activityStorage.getNewerFeedActivities(demoIdentity, sinceTime, 10).size());
    relationshipManager.confirm(demoIdentity, maryIdentity);
    createActivities(2, maryIdentity);
    assertEquals(5, activityStorage.getNewerFeedActivities(demoIdentity, sinceTime, 10).size());

    // Delete the activity at this sinceTime will don't change the result
    String id = activityStorage.getUserActivities(demoIdentity, 0, 10).get(1).getId();
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }
    activityStorage.deleteActivity(id);
    assertEquals(5, activityStorage.getNewerFeedActivities(demoIdentity, sinceTime, 10).size());
  }

  /**
   * Tests {@link ActivityStorage#getOlderOnActivityFeed(Identity, Long, int)}.
   */
  @MaxQueryNumber(1173)
  public void testGetOlderOnActivityFeedWithTimestamp() throws Exception {
    checkCleanData();
    createActivities(5, demoIdentity);
    Long maxTime = activityStorage.getActivityFeed(demoIdentity, 0, 10).get(2).getUpdated().getTime();
    assertEquals(2, activityStorage.getOlderFeedActivities(demoIdentity, maxTime, 10).size());

    // Update an older activity, this activity must be newer than maxTime
    ExoSocialActivity act = activityStorage.getActivityFeed(demoIdentity, 0, 10).get(3);
    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("demo comment ");
    comment.setUserId(demoIdentity.getId());
    activityStorage.saveComment(act, comment);
    assertEquals(1, activityStorage.getOlderFeedActivities(demoIdentity, maxTime, 10).size());

    // Delete the activity at this maxTime will don't change the result
    String id = activityStorage.getUserActivities(demoIdentity, 0, 10).get(2).getId();
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }
    activityStorage.deleteActivity(id);
    assertEquals(1, activityStorage.getOlderFeedActivities(demoIdentity, maxTime, 10).size());
  }

  /**
   * Test
   * {@link ActivityStorage#getNewerOnActivitiesOfConnections(Identity, Long, int)}
   */
  @MaxQueryNumber(3558)
  public void testGetNewerOnActivitiesOfConnectionsWithTimestamp() throws Exception {
    checkCleanData();
    List<Relationship> relationships = new ArrayList<Relationship>();
    this.createActivities(3, maryIdentity);
    this.createActivities(1, demoIdentity);
    this.createActivities(2, johnIdentity);
    this.createActivities(2, rootIdentity);

    List<ExoSocialActivity> maryActivities = activityStorage.getActivitiesOfIdentity(maryIdentity, 0, 10);
    assertNotNull("maryActivities must not be null", maryActivities);
    assertEquals("maryActivities.size() must return: 3", 3, maryActivities.size());

    Long sinceTime = maryActivities.get(2).getUpdated().getTime();

    List<ExoSocialActivity> activities = activityStorage.getNewerActivitiesOfConnections(johnIdentity, sinceTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 0", 0, activities.size());

    activities = activityStorage.getNewerActivitiesOfConnections(demoIdentity, sinceTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 0", 0, activities.size());

    activities = activityStorage.getNewerActivitiesOfConnections(maryIdentity, sinceTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 0", 0, activities.size());

    RelationshipManager relationshipManager = this.getRelationshipManager();
    Relationship maryDemoRelationship = relationshipManager.inviteToConnect(maryIdentity, demoIdentity);
    relationshipManager.confirm(maryIdentity, demoIdentity);
    relationships.add(maryDemoRelationship);

    activities = activityStorage.getNewerActivitiesOfConnections(maryIdentity, sinceTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 1", 1, activities.size());

    activities = activityStorage.getNewerActivitiesOfConnections(demoIdentity, sinceTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 2", 2, activities.size());

    // Delete the activity at this sinceTime will don't change the result
    String id = activityStorage.getUserActivities(maryIdentity, 0, 10).get(2).getId();
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }

    activityStorage.deleteActivity(id);

    assertEquals("activities.size() must return: 2",
                 2,
                 activityStorage.getNewerActivitiesOfConnections(demoIdentity, sinceTime, 10).size());

    Relationship maryJohnRelationship = relationshipManager.inviteToConnect(maryIdentity, johnIdentity);
    relationshipManager.confirm(maryIdentity, johnIdentity);
    relationships.add(maryJohnRelationship);

    activities = activityStorage.getNewerActivitiesOfConnections(maryIdentity, sinceTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 3", 3, activities.size());

    Relationship maryRootRelationship = relationshipManager.inviteToConnect(maryIdentity, rootIdentity);
    relationshipManager.confirm(maryIdentity, rootIdentity);
    relationships.add(maryRootRelationship);

    activities = activityStorage.getNewerActivitiesOfConnections(maryIdentity, sinceTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 5", 5, activities.size());

    for (Relationship rel : relationships) {
      relationshipManager.delete(rel);
    }
  }

  /**
   * Test
   * {@link ActivityStorage#getOlderOnActivitiesOfConnections(Identity, Long, int)}
   */
  @MaxQueryNumber(7034)
  public void testGetOlderOnActivitiesOfConnectionsWithTimestamp() throws Exception {
    checkCleanData();
    List<Relationship> relationships = new ArrayList<Relationship>();

    this.createActivities(3, maryIdentity);
    this.createActivities(1, demoIdentity);
    this.createActivities(2, johnIdentity);
    this.createActivities(2, rootIdentity);

    List<ExoSocialActivity> rootActivities = activityStorage.getActivitiesOfIdentity(rootIdentity, 0, 10);
    assertNotNull("rootActivities must not be null", rootActivities);
    assertEquals("rootActivities.size() must return: 2", 2, rootActivities.size());

    Long maxTime = rootActivities.get(1).getUpdated().getTime();

    List<ExoSocialActivity> activities;

    activities = activityStorage.getOlderActivitiesOfConnections(rootIdentity, maxTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 0", 0, activities.size());

    activities = activityStorage.getOlderActivitiesOfConnections(johnIdentity, maxTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 0", 0, activities.size());

    RelationshipManager relationshipManager = this.getRelationshipManager();

    Relationship rootJohnRelationship = relationshipManager.inviteToConnect(rootIdentity, johnIdentity);
    relationshipManager.confirm(rootIdentity, johnIdentity);
    relationships.add(rootJohnRelationship);

    activities = activityStorage.getOlderActivitiesOfConnections(rootIdentity, maxTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 2", 2, activities.size());

    // Delete the activity at this maxTime will don't change the result
    String id = activityStorage.getUserActivities(rootIdentity, 0, 10).get(1).getId();
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }
    activityStorage.deleteActivity(id);
    assertEquals("activities.size() must return: 2",
                 2,
                 activityStorage.getOlderActivitiesOfConnections(rootIdentity, maxTime, 10).size());

    Relationship rootDemoRelationship = relationshipManager.inviteToConnect(rootIdentity, demoIdentity);
    relationshipManager.confirm(rootIdentity, demoIdentity);
    relationships.add(rootDemoRelationship);

    activities = activityStorage.getOlderActivitiesOfConnections(rootIdentity, maxTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 3", 3, activities.size());

    Relationship rootMaryRelationship = relationshipManager.inviteToConnect(rootIdentity, maryIdentity);
    relationshipManager.confirm(rootIdentity, maryIdentity);
    relationships.add(rootMaryRelationship);

    activities = activityStorage.getOlderActivitiesOfConnections(rootIdentity, maxTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 6", 6, activities.size());

    for (Relationship rel : relationships) {
      relationshipManager.delete(rel);
    }
  }

  /**
   * Test
   * {@link ActivityStorage#getNewerOnUserSpacesActivities(Identity, Long, int)}
   * 
   * @throws Exception
   */
  @MaxQueryNumber(5291)
  public void testGetNewerOnUserSpacesActivitiesWithTimestamp() throws Exception {
    checkCleanData();
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 10;

    long sinceTime = 0;

    String id = "";
    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
      if (i == 0) {
        sinceTime = activity.getUpdated().getTime();
        id = activity.getId();
      }
    }

    space = spaceService.getSpaceByDisplayName(space.getDisplayName());
    assertNotNull("space must not be null", space);
    assertEquals("space.getDisplayName() must return: my space 0", "my space 0", space.getDisplayName());
    assertEquals("space.getDescription() must return: add new space 0", "add new space 0", space.getDescription());

    List<ExoSocialActivity> activities = activityStorage.getNewerUserSpacesActivities(demoIdentity, sinceTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 9", 9, activities.size());

    Space space2 = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space2.getPrettyName(), false);

    // demo posts activities to space2
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      tearDownActivityList.add(activity);
    }

    space2 = spaceService.getSpaceByDisplayName(space2.getDisplayName());
    assertNotNull("space2 must not be null", space2);
    assertEquals("space2.getDisplayName() must return: my space 1", "my space 1", space2.getDisplayName());
    assertEquals("space2.getDescription() must return: add new space 1", "add new space 1", space2.getDescription());

    activities = activityStorage.getNewerUserSpacesActivities(demoIdentity, sinceTime, 20);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 19", 19, activities.size());

    // Delete the activity at this sinceTime will don't change the result
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        break;
      }
    }
    assertEquals("activities.size() must return: 19",
                 19,
                 activityStorage.getNewerUserSpacesActivities(demoIdentity, sinceTime, 20).size());

  }

  /**
   * Test
   * {@link ActivityStorage#getOlderOnUserSpacesActivities(Identity, Long, int)}
   * 
   * @throws Exception
   */
  @MaxQueryNumber(5285)
  public void testGetOlderOnUserSpacesActivitiesWithTimestamp() throws Exception {
    checkCleanData();
    SpaceService spaceService = this.getSpaceService();
    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    int totalNumber = 10;

    long maxTime = 0;

    String id = "";

    // demo posts activities to space
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity, activity);
      tearDownActivityList.add(activity);
      if (i == totalNumber - 1) {
        maxTime = activity.getUpdated().getTime();
        id = activity.getId();
      }
    }

    space = spaceService.getSpaceByDisplayName(space.getDisplayName());
    assertNotNull("space must not be null", space);
    assertEquals("space.getDisplayName() must return: my space 0", "my space 0", space.getDisplayName());
    assertEquals("space.getDescription() must return: add new space 0", "add new space 0", space.getDescription());

    List<ExoSocialActivity> activities = activityStorage.getOlderUserSpacesActivities(demoIdentity, maxTime, 10);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 9", 9, activities.size());

    Space space2 = this.getSpaceInstance(spaceService, 1);
    Identity spaceIdentity2 = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space2.getPrettyName(), false);

    // demo posts activities to space2
    for (int i = 0; i < totalNumber; i++) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(demoIdentity.getId());
      activityStorage.saveActivity(spaceIdentity2, activity);
      tearDownActivityList.add(activity);
      if (i == totalNumber - 1) {
        maxTime = activity.getUpdated().getTime();
      }
    }

    space2 = spaceService.getSpaceByDisplayName(space2.getDisplayName());
    assertNotNull("space2 must not be null", space2);
    assertEquals("space2.getDisplayName() must return: my space 1", "my space 1", space2.getDisplayName());
    assertEquals("space2.getDescription() must return: add new space 1", "add new space 1", space2.getDescription());

    activities = activityStorage.getOlderUserSpacesActivities(demoIdentity, maxTime, 20);
    assertNotNull("activities must not be null", activities);
    assertEquals("activities.size() must return: 19", 19, activities.size());

    // Delete the activity at this maxTime will don't change the result
    for (ExoSocialActivity activity : tearDownActivityList) {
      if (id == activity.getId()) {
        tearDownActivityList.remove(activity);
        activityStorage.deleteActivity(activity.getId());
        break;
      }
    }
    assertEquals("activities.size() must return: 18",
                 18,
                 activityStorage.getOlderUserSpacesActivities(demoIdentity, maxTime, 20).size());

  }

  /**
   * Test {@link ActivityStorage#getNewerComments(ExoSocialActivity, Long, int)}
   */
  @MaxQueryNumber(13707)
  public void testGetNewerCommentsWithTimestamp() throws Exception {
    checkCleanData();
    int totalNumber = 10;
    String activityTitle = "activity title";

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle(activityTitle);
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("john comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("demo comment " + i);
      comment.setUserId(demoIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    List<ExoSocialActivity> comments = activityStorage.getComments(activity, false, 0, 10);
    assertNotNull("comments must not be null", comments);
    assertEquals("comments.size() must return: 10", 10, comments.size());

    Long sinceTime = comments.get(0).getUpdated().getTime();
    List<ExoSocialActivity> newerComments = activityStorage.getNewerComments(activity, sinceTime, 10);
    assertNotNull("newerComments must not be null", newerComments);
    assertEquals("newerComments.size() must return: 10", 10, newerComments.size());

    sinceTime = activityStorage.getComments(activity, false, 0, 20).get(10).getUpdated().getTime();
    newerComments = activityStorage.getNewerComments(activity, sinceTime, 20);
    assertNotNull("newerComments must not be null", newerComments);
    assertEquals("newerComments.size() must return: 9", 9, newerComments.size());

    sinceTime = activityStorage.getComments(activity, false, 0, 20).get(19).getUpdated().getTime();
    newerComments = activityStorage.getNewerComments(activity, sinceTime, 20);
    assertNotNull("newerComments must not be null", newerComments);
    assertEquals("newerComments.size() must return: 0", 0, newerComments.size());
  }

  /**
   * Test {@link ActivityStorage#getOlderComments(ExoSocialActivity, Long, int)}
   */
  @MaxQueryNumber(5565)
  public void testGetOlderCommentsWithTimestamp() throws Exception {
    checkCleanData();
    int totalNumber = 10;
    String activityTitle = "activity title";

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle(activityTitle);
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    for (int i = 0; i < totalNumber; i++) {
      // John comments on Root's activity
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("john comment " + i);
      comment.setUserId(johnIdentity.getId());
      activityStorage.saveComment(activity, comment);
    }

    List<ExoSocialActivity> comments = activityStorage.getComments(activity, false, 0, 10);
    assertNotNull("comments must not be null", comments);
    assertEquals("comments.size() must return: 10", 10, comments.size());

    Long maxTime = comments.get(0).getUpdated().getTime();

    List<ExoSocialActivity> olderComments = activityStorage.getOlderComments(activity, maxTime, 10);
    assertNotNull("olderComments must not be null", olderComments);
    assertEquals("olderComments.size() must return: 0", 0, olderComments.size());

    maxTime = comments.get(9).getUpdated().getTime();

    olderComments = activityStorage.getOlderComments(activity, maxTime, 10);
    assertNotNull("olderComments must not be null", olderComments);
    assertEquals("olderComments.size() must return: 9", 9, olderComments.size());

    maxTime = comments.get(5).getUpdated().getTime();

    olderComments = activityStorage.getOlderComments(activity, maxTime, 10);
    assertNotNull("olderComments must not be null", olderComments);
    assertEquals("olderComments.size() must return: 5", 5, olderComments.size());
  }

  @MaxQueryNumber(685)
  public void testMoveActivity() throws Exception {
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("activity title");
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    activity = activityStorage.getActivity(activity.getId());
    assertEquals(rootIdentity.getId(), activity.getStreamId());

    Space space = this.getSpaceInstance(spaceService, 0);
    Identity spaceIdentity = this.identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName(), false);

    activity.setStreamOwner(spaceIdentity.getRemoteId());
    activity.setStreamId(spaceIdentity.getId());
    activityStorage.updateActivity(activity);

    activity = activityStorage.getActivity(activity.getId());
    assertEquals(spaceIdentity.getId(), activity.getStreamId());

  }

  @MaxQueryNumber(696)
  public void testCommentedActivity() throws Exception {

    // root creates an activity
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("activity title");
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity);
    tearDownActivityList.add(activity);

    List<ExoSocialActivity> rootActivities = activityStorage.getActivityFeed(rootIdentity, 0, 10);
    assertEquals(1, rootActivities.size());
    List<ExoSocialActivity> demoActivities = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals(0, demoActivities.size());

    // demo comments on root's activity
    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("demo comment");
    comment.setUserId(demoIdentity.getId());
    activityStorage.saveComment(activity, comment);

    demoActivities = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals(1, demoActivities.size());

    // root creates another activity
    ExoSocialActivity newActivity = new ExoSocialActivityImpl();
    newActivity.setTitle("new activity title");
    newActivity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, newActivity);
    tearDownActivityList.add(newActivity);

    demoActivities = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals(1, demoActivities.size());

    // demo likes root's new activity
    newActivity = activityStorage.getActivity(newActivity.getId());
    newActivity.setLikeIdentityIds(new String[] { demoIdentity.getId() });
    activityStorage.updateActivity(newActivity);

    demoActivities = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals(2, demoActivities.size());

    // demo creates an activity on root's stream
    /*
     * ExoSocialActivity demoActivity = new ExoSocialActivityImpl();
     * demoActivity.setTitle("new activity title");
     * demoActivity.setUserId(demoIdentity.getId());
     * activityStorage.saveActivity(rootIdentity, demoActivity);
     * tearDownActivityList.add(demoActivity); demoActivities =
     * activityStorage.getActivityFeed(demoIdentity, 0, 10); assertEquals(3,
     * demoActivities.size());
     */
  }

  @MaxQueryNumber(1158)
  public void testPostActivityOnConnectionStream() throws Exception {
    Relationship rootDemoRelationship = relationshipManager.inviteToConnect(rootIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, rootIdentity);
    Relationship demoJohnRelationship = relationshipManager.inviteToConnect(demoIdentity, johnIdentity);
    relationshipManager.confirm(johnIdentity, demoIdentity);

    // root posts an activity on demo's stream
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("activity title");
    activity.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(demoIdentity, activity);
    tearDownActivityList.add(activity);

    List<ExoSocialActivity> demoActivities = activityStorage.getActivityFeed(demoIdentity, 0, 10);
    assertEquals(1, demoActivities.size());
    List<ExoSocialActivity> johnActivities = activityStorage.getActivityFeed(johnIdentity, 0, 10);
    assertEquals(0, johnActivities.size());

    Relationship maryDemoRelationship = relationshipManager.inviteToConnect(maryIdentity, demoIdentity);
    relationshipManager.confirm(demoIdentity, maryIdentity);

    List<ExoSocialActivity> maryActivities = activityStorage.getActivityFeed(maryIdentity, 0, 10);
    // Mary can see the demo's activity in her stream
    assertEquals(1, maryActivities.size());

    relationshipManager.delete(rootDemoRelationship);
    relationshipManager.delete(demoJohnRelationship);
    relationshipManager.delete(maryDemoRelationship);
  }

  @MaxQueryNumber(450)
  public void testSaveHiddenComment() throws Exception {
    // root posts 2 activities
    ExoSocialActivity activity1 = new ExoSocialActivityImpl();
    activity1.setTitle("activity 1");
    activity1.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity1);
    tearDownActivityList.add(activity1);

    ExoSocialActivity activity2 = new ExoSocialActivityImpl();
    activity2.setTitle("activity 2");
    activity2.setUserId(rootIdentity.getId());
    activityStorage.saveActivity(rootIdentity, activity2);
    tearDownActivityList.add(activity2);

    // demo add a hidden comment on activity 1 of root
    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setTitle("comment is hidden");
    comment.setUserId(demoIdentity.getId());
    comment.isHidden(true);
    activityStorage.saveComment(activity1, comment);

    List<ExoSocialActivity> activities = activityStorage.getActivityFeed(rootIdentity, 0, 10);
    assertEquals("activity 2", activities.get(0).getTitle());
    assertEquals("activity 1", activities.get(1).getTitle());

    comment = activityStorage.getActivity(comment.getId());
    comment.isHidden(false);
    activityStorage.updateActivity(comment);

    activities = activityStorage.getActivityFeed(rootIdentity, 0, 10);
    assertEquals("activity 1", activities.get(0).getTitle());
    assertEquals("activity 2", activities.get(1).getTitle());
  }

  @MaxQueryNumber(428)
  public void testUpdateActivityForLike() throws Exception {
    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("a");
    activity.setBody("test");
    activityStorage.saveActivity(rootIdentity, activity);
    assertNotNull(activity.getId());

    //
    ExoSocialActivity got = activityStorage.getActivity(activity.getId());

    got.setBody(null);
    got.setTitle(null);
    got.setLikeIdentityIds(new String[] { maryIdentity.getId() });
    activityStorage.updateActivity(got);

    ExoSocialActivity updatedActivity = activityStorage.getActivity(activity.getId());

    assertEquals(got.getId(), updatedActivity.getId());
    assertEquals(got.getTitle(), updatedActivity.getTitle());
    assertEquals(got.getBody(), updatedActivity.getBody());
  }

  /**
   * Wrong due to not set: got.setBody(null); got.setTitle(null); before
   * invokes: activityStorage.updateActivity(got);
   * 
   * @throws Exception
   */
  @MaxQueryNumber(428)
  public void testUpdateActivityForWrong() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("&");
    activity.setBody("test&amp;");
    activityStorage.saveActivity(rootIdentity, activity);
    assertNotNull(activity.getId());

    //
    ExoSocialActivity got = activityStorage.getActivity(activity.getId());

    got.setLikeIdentityIds(new String[] { maryIdentity.getId() });
    activityStorage.updateActivity(got);

    ExoSocialActivity updatedActivity = activityStorage.getActivity(activity.getId());

    assertEquals(got.getId(), updatedActivity.getId());
    assertNotSame(got.getTitle(), updatedActivity.getTitle());
    assertNotSame(got.getBody(), updatedActivity.getBody());

  }

  @MaxQueryNumber(428)
  public void testUpdateActivityForUnLike() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("title ");
    activity.setLikeIdentityIds(new String[] { maryIdentity.getId() });
    activityStorage.saveActivity(rootIdentity, activity);
    assertNotNull(activity.getId());

    //
    ExoSocialActivity got = activityStorage.getActivity(activity.getId());

    got.setBody(null);
    got.setTitle(null);
    got.setLikeIdentityIds(new String[] {});
    activityStorage.updateActivity(got);

    ExoSocialActivity updatedActivity = activityStorage.getActivity(activity.getId());

    assertEquals(got.getId(), updatedActivity.getId());
    assertEquals(got.getTitle(), updatedActivity.getTitle());
    assertEquals(got.getBody(), updatedActivity.getBody());

  }

  /**
   * Wrong due to not set: got.setBody(null); got.setTitle(null); before
   * invokes: activityStorage.updateActivity(got);
   * 
   * @throws Exception
   */
  @MaxQueryNumber(428)
  public void testUpdateActivityForUnLikeWrong() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("&");
    activity.setBody("test&amp;");
    activity.setLikeIdentityIds(new String[] { maryIdentity.getId() });
    activityStorage.saveActivity(rootIdentity, activity);
    assertNotNull(activity.getId());

    //
    ExoSocialActivity got = activityStorage.getActivity(activity.getId());

    got.setLikeIdentityIds(new String[] {});
    activityStorage.updateActivity(got);

    ExoSocialActivity updatedActivity = activityStorage.getActivity(activity.getId());

    assertEquals(got.getId(), updatedActivity.getId());
    assertNotSame(got.getTitle(), updatedActivity.getTitle());
    assertNotSame(got.getBody(), updatedActivity.getBody());

  }

  @MaxQueryNumber(2685)
  public void testActivityCount() throws Exception {

    // fill 10 activities
    for (int i = 0; i < 10; ++i) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("title " + i);
      activityStorage.saveActivity(rootIdentity, activity);
    }

    //
    assertEquals(10, activityStorage.getNumberOfUserActivities(rootIdentity));

    // remove 5 activities
    Iterator<ExoSocialActivity> it = activityStorage.getUserActivities(rootIdentity, 0, 100).iterator();

    for (int i = 0; i < 5; ++i) {
      activityStorage.deleteActivity(it.next().getId());
    }

    it = activityStorage.getUserActivities(rootIdentity, 0, 100).iterator();

    //
    assertEquals(5, activityStorage.getNumberOfUserActivities(rootIdentity));

    while (it.hasNext()) {
      tearDownActivityList.add(it.next());
    }

  }

  /**
   * Test
   * {@link org.exoplatform.social.core.storage.impl.ActivityStorageImpl#getActivity(String)}
   */
  @MaxQueryNumber(888)
  public void testUserPostActivityToSpace() throws ActivityStorageException {
    // Create new Space and its Identity
    Space space = getSpaceInstance();
    SpaceIdentityProvider spaceIdentityProvider =
                                                (SpaceIdentityProvider) getContainer().getComponentInstanceOfType(SpaceIdentityProvider.class);
    Identity spaceIdentity = spaceIdentityProvider.createIdentity(space);
    identityStorage.saveIdentity(spaceIdentity);

    // john posted activity on created Space
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("Space's Activity");
    activity.setUserId(johnIdentity.getId());

    activityStorage.saveActivity(spaceIdentity, activity);

    // Get posted Activity and check
    ExoSocialActivity gotActivity = activityStorage.getActivity(activity.getId());

    assertEquals("userId must be " + johnIdentity.getId(), johnIdentity.getId(), gotActivity.getUserId());

    //
    List<ExoSocialActivity> gotActivities = activityStorage.getUserActivities(johnIdentity, 0, 20);
    assertEquals(1, gotActivities.size());
    assertEquals(johnIdentity.getId(), gotActivities.get(0).getUserId());

    identityStorage.deleteIdentity(spaceIdentity);
  }

  @MaxQueryNumber(1527)
  public void testActivityOrder() throws Exception {
    // fill 10 activities
    for (int i = 0; i < 10; ++i) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("title " + i);
      activityStorage.saveActivity(rootIdentity, activity);
      tearDownActivityList.add(activity);
    }

    List<ExoSocialActivity> userActivities = activityStorage.getUserActivities(rootIdentity);
    assertTrue(userActivities.size() >= 10);
    Iterator<ExoSocialActivity> userActivitiesIterator = userActivities.iterator();
    int i = 0;
    while (userActivitiesIterator.hasNext() && i < 10) {
      ExoSocialActivity activity = userActivitiesIterator.next();
      if (i == 0 && !StringUtils.equals(activity.getTitle(), "title0")) {
        continue;
      }
      assertEquals("title " + i, activity.getTitle());
      i++;
    }
  }

  @MaxQueryNumber(1500)
  public void testActivityOrderByPostedTime() throws Exception {
    // fill 10 activities
    Calendar cal = Calendar.getInstance();
    long today = cal.getTime().getTime();
    cal.add(Calendar.DATE, -1);
    long yesterday = cal.getTime().getTime();
    // i > 5 PostedTime = currentDate + i;
    // else yesterdayDate = currentDate + i;
    for (int i = 0; i < 10; ++i) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("title " + i);
      activity.setPostedTime(i > 5 ? today + i : yesterday + i);
      activityStorage.saveActivity(rootIdentity, activity);
    }

    // fill 10 activities
    for (int i = 0; i < 10; ++i) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("title " + i);
      activityStorage.saveActivity(rootIdentity, activity);
      tearDownActivityList.add(activity);
    }

    List<ExoSocialActivity> userActivities = activityStorage.getUserActivities(rootIdentity);
    assertTrue(userActivities.size() >= 10);
    Iterator<ExoSocialActivity> userActivitiesIterator = userActivities.iterator();
    int i = 0;
    while (userActivitiesIterator.hasNext() && i < 10) {
      ExoSocialActivity activity = userActivitiesIterator.next();
      if (i == 0 && !StringUtils.equals(activity.getTitle(), "title0")) {
        continue;
      }
      assertEquals("title " + i, activity.getTitle());

      if (i > 5) {
        assertEquals(today + i, activity.getPostedTime().longValue());
      } else {
        assertEquals(yesterday + i, activity.getPostedTime().longValue());
      }
      i++;
    }
  }

  /*
   * Today: the 1st of this month Yesterday: the last day of last month
   */
  @MaxQueryNumber(1494)
  public void testActivityOrderByPostedTime2() throws Exception {
    // fill 10 activities
    Calendar cal = Calendar.getInstance();
    cal.set(Calendar.DATE, 1);
    long today = cal.getTime().getTime();
    cal.add(Calendar.DATE, -1);
    long yesterday = cal.getTime().getTime();
    // i >= 5 PostedTime = currentDate + i;
    // else PostedTime = yesterdayDate + i;
    for (int i = 0; i < 10; ++i) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("title " + i);
      activity.setPostedTime(i >= 5 ? today + i : yesterday + i);
      activityStorage.saveActivity(rootIdentity, activity);
    }

    List<ExoSocialActivity> userActivities = activityStorage.getUserActivities(rootIdentity);
    assertTrue(userActivities.size() >= 10);
    Iterator<ExoSocialActivity> userActivitiesIterator = userActivities.iterator();
    int i = 0;
    while (userActivitiesIterator.hasNext() && i < 10) {
      ExoSocialActivity activity = userActivitiesIterator.next();
      if (i == 0 && !StringUtils.equals(activity.getTitle(), "title0")) {
        continue;
      }
      assertEquals("title " + i, activity.getTitle());

      if (i >= 5) {
        assertEquals(today + i, activity.getPostedTime().longValue());
      } else {
        assertEquals(yesterday + i, activity.getPostedTime().longValue());
      }
      i++;
    }
  }

  @MaxQueryNumber(4155)
  public void testActivityOrder2() throws Exception {
    // fill 10 activities
    for (int i = 0; i < 10; ++i) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("title " + i);
      activityStorage.saveActivity(rootIdentity, activity);
    }

    // remove 5 activities
    Iterator<ExoSocialActivity> it = activityStorage.getUserActivities(rootIdentity).iterator();
    for (int i = 0; i < 5; ++i) {
      activityStorage.deleteActivity(it.next().getId());
    }

    while (it.hasNext()) {
      tearDownActivityList.add(it.next());
    }

    // fill 10 others
    for (int i = 0; i < 10; ++i) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("title " + i);
      activityStorage.saveActivity(rootIdentity, activity);
      tearDownActivityList.add(activity);
    }

    List<ExoSocialActivity> userActivities = activityStorage.getUserActivities(rootIdentity);
    assertTrue(userActivities.size() >= 15);
    int[] values = { 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, 4, 3, 2, 1, 0 };

    Iterator<ExoSocialActivity> userActivitiesIterator = userActivities.iterator();
    int i = 0;
    while (userActivitiesIterator.hasNext() && i < values.length) {
      ExoSocialActivity activity = userActivitiesIterator.next();
      if (i == 0 && !StringUtils.equals(activity.getTitle(), "title0")) {
        continue;
      }
      assertEquals("title " + values[i], activity.getTitle());
      i++;
    }
  }

  @MaxQueryNumber(318)
  public void testActivityHidden() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("title");
    activity.setBody("body");
    activity.isHidden(false);
    activityStorage.saveActivity(rootIdentity, activity);
    assertNotNull(activity.getId());

    //
    ExoSocialActivity got = activityStorage.getActivity(activity.getId());
    got.isHidden(true);

    activityStorage.updateActivity(got);

    ExoSocialActivity updatedActivity = activityStorage.getActivity(activity.getId());
    assertEquals(true, updatedActivity.isHidden());
  }

  @MaxQueryNumber(366)
  public void testActivityUnHidden() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("title");
    activity.setBody("body");
    activity.isHidden(true);
    activityStorage.saveActivity(rootIdentity, activity);
    assertNotNull(activity.getId());

    //
    ExoSocialActivity got = activityStorage.getActivity(activity.getId());
    got.isHidden(false);

    activityStorage.updateActivity(got);

    ExoSocialActivity updatedActivity = activityStorage.getActivity(activity.getId());
    assertEquals(false, updatedActivity.isHidden());
  }

  @MaxQueryNumber(248)
  public void testActivityLock() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("title");
    activity.setBody("body");
    activity.isLocked(false);
    activityStorage.saveActivity(rootIdentity, activity);
    assertNotNull(activity.getId());

    //
    ExoSocialActivity got = activityStorage.getActivity(activity.getId());
    got.isLocked(true);

    activityStorage.updateActivity(got);

    ExoSocialActivity updatedActivity = activityStorage.getActivity(activity.getId());
    assertEquals(true, updatedActivity.isLocked());
  }

  @MaxQueryNumber(248)
  public void testActivityUnLock() throws Exception {

    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("title");
    activity.setBody("body");
    activity.isLocked(true);
    activityStorage.saveActivity(rootIdentity, activity);
    assertNotNull(activity.getId());

    //
    ExoSocialActivity got = activityStorage.getActivity(activity.getId());
    got.isLocked(false);

    activityStorage.updateActivity(got);

    ExoSocialActivity updatedActivity = activityStorage.getActivity(activity.getId());
    assertEquals(false, updatedActivity.isLocked());
  }

  @MaxQueryNumber(32430)
  public void testCommentOrder() throws Exception {
    for (int i = 0; i < 10; ++i) {
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("title " + i);
      activityStorage.saveActivity(rootIdentity, activity);
      tearDownActivityList.add(activity);

      // fill 10 comments for each activity
      for (int j = 0; j < 10; ++j) {
        ExoSocialActivity comment = new ExoSocialActivityImpl();
        comment.setTitle("title " + i + j);
        comment.setUserId(rootIdentity.getId());
        activityStorage.saveComment(activity, comment);
      }
    }
    restartTransaction();

    int i = 9;
    for (ExoSocialActivity activity : activityStorage.getUserActivities(rootIdentity)) {
      int j = 0;
      for (String commentId : activity.getReplyToId()) {
        if (!"".equals(commentId)) {
          assertEquals("title " + i + j, activityStorage.getActivity(commentId).getTitle());
          ++j;
        }
      }
      --i;
    }
  }

  @MaxQueryNumber(630)
  public void testLike() throws Exception {
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("activity title");

    activityStorage.saveActivity(rootIdentity, activity);

    activity.setLikeIdentityIds(new String[] { rootIdentity.getId(), johnIdentity.getId(), demoIdentity.getId() });

    activityStorage.saveActivity(rootIdentity, activity);

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(rootIdentity);

    assertEquals(1, activities.size());
    assertEquals(3, activities.get(0).getLikeIdentityIds().length);

    List<String> ids = Arrays.asList(activities.get(0).getLikeIdentityIds());

    assertTrue(ids.contains(rootIdentity.getId()));
    assertTrue(ids.contains(johnIdentity.getId()));
    assertTrue(ids.contains(demoIdentity.getId()));
    assertTrue(!ids.contains(maryIdentity.getId()));
  }

  @MaxQueryNumber(372)
  public void testTimeStamp() throws Exception {
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("activity title");
    activityStorage.saveActivity(rootIdentity, activity);

    ExoSocialActivity comment = new ExoSocialActivityImpl();
    comment.setUserId(rootIdentity.getId());
    comment.setTitle("comment title");

    activityStorage.saveComment(activity, comment);

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(rootIdentity);

    assertEquals(1, activities.size());
    assertFalse(activities.get(0).getPostedTime() == 0);
    assertEquals(1, activities.get(0).getReplyToId().length);

    ExoSocialActivity gotComment = activityStorage.getActivity(activities.get(0).getReplyToId()[0]);
    assertFalse(gotComment.getPostedTime() == 0);

  }

  @MaxQueryNumber(1347)
  public void testManyDays() throws Exception {

    long timestamp111 = timestamp(2001, 1, 1);
    long timestamp112 = timestamp(2001, 1, 2);
    long timestamp121 = timestamp(2001, 2, 1);
    long timestamp122 = timestamp(2001, 2, 2);
    long timestamp211 = timestamp(2002, 1, 1);
    long timestamp212 = timestamp(2002, 1, 2);
    long timestamp221 = timestamp(2002, 2, 1);
    long timestamp222 = timestamp(2002, 2, 2);

    addActivity(rootIdentity, timestamp111);
    addActivity(rootIdentity, timestamp112);
    addActivity(rootIdentity, timestamp121);
    addActivity(rootIdentity, timestamp122);
    addActivity(rootIdentity, timestamp211);
    addActivity(rootIdentity, timestamp212);
    addActivity(rootIdentity, timestamp221);
    addActivity(rootIdentity, timestamp222);

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(rootIdentity);
    assertEquals(8, activities.size());
    assertEquals(timestamp111, activities.get(7).getPostedTime().longValue());
    assertEquals(timestamp112, activities.get(6).getPostedTime().longValue());
    assertEquals(timestamp121, activities.get(5).getPostedTime().longValue());
    assertEquals(timestamp122, activities.get(4).getPostedTime().longValue());
    assertEquals(timestamp211, activities.get(3).getPostedTime().longValue());
    assertEquals(timestamp212, activities.get(2).getPostedTime().longValue());
    assertEquals(timestamp221, activities.get(1).getPostedTime().longValue());
    assertEquals(timestamp222, activities.get(0).getPostedTime().longValue());

  }

  @MaxQueryNumber(561)
  public void testManyDaysNoActivityOnDay() throws Exception {

    long timestamp1 = timestamp(2001, 1, 1);
    long timestamp2 = timestamp(2001, 1, 2);

    addActivity(rootIdentity, timestamp1);
    ExoSocialActivity activity2 = addActivity(rootIdentity, timestamp2);

    activityStorage.deleteActivity(activity2.getId());

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(rootIdentity);
    assertEquals(1, activities.size());
    assertEquals(timestamp1, activities.get(0).getPostedTime().longValue());

  }

  @MaxQueryNumber(1131)
  public void testManyDaysNoActivityOnMonth() throws Exception {

    long timestamp11 = timestamp(2001, 1, 1);
    long timestamp12 = timestamp(2001, 1, 2);
    long timestamp21 = timestamp(2001, 2, 1);
    long timestamp22 = timestamp(2001, 2, 2);

    addActivity(rootIdentity, timestamp11);
    addActivity(rootIdentity, timestamp12);
    ExoSocialActivity activity21 = addActivity(rootIdentity, timestamp21);
    ExoSocialActivity activity22 = addActivity(rootIdentity, timestamp22);

    activityStorage.deleteActivity(activity21.getId());
    activityStorage.deleteActivity(activity22.getId());

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(rootIdentity);
    assertEquals(2, activities.size());
    assertEquals(timestamp11, activities.get(1).getPostedTime().longValue());
    assertEquals(timestamp12, activities.get(0).getPostedTime().longValue());

  }

  @MaxQueryNumber(2271)
  public void testManyDaysNoActivityOnYear() throws Exception {

    long timestamp111 = timestamp(2001, 1, 1);
    long timestamp112 = timestamp(2001, 1, 2);
    long timestamp121 = timestamp(2001, 2, 1);
    long timestamp122 = timestamp(2001, 2, 2);
    long timestamp211 = timestamp(2002, 1, 1);
    long timestamp212 = timestamp(2002, 1, 2);
    long timestamp221 = timestamp(2002, 2, 1);
    long timestamp222 = timestamp(2002, 2, 2);

    addActivity(rootIdentity, timestamp111);
    addActivity(rootIdentity, timestamp112);
    addActivity(rootIdentity, timestamp121);
    addActivity(rootIdentity, timestamp122);
    ExoSocialActivity activity211 = addActivity(rootIdentity, timestamp211);
    ExoSocialActivity activity212 = addActivity(rootIdentity, timestamp212);
    ExoSocialActivity activity221 = addActivity(rootIdentity, timestamp221);
    ExoSocialActivity activity222 = addActivity(rootIdentity, timestamp222);

    activityStorage.deleteActivity(activity211.getId());
    activityStorage.deleteActivity(activity212.getId());
    activityStorage.deleteActivity(activity221.getId());
    activityStorage.deleteActivity(activity222.getId());

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(rootIdentity);
    assertEquals(4, activities.size());
    assertEquals(timestamp111, activities.get(3).getPostedTime().longValue());
    assertEquals(timestamp112, activities.get(2).getPostedTime().longValue());
    assertEquals(timestamp121, activities.get(1).getPostedTime().longValue());
    assertEquals(timestamp122, activities.get(0).getPostedTime().longValue());

  }

  @MaxQueryNumber(3207)
  public void testManyDaysNoActivityOnAll() throws Exception {

    long timestamp111 = timestamp(2001, 1, 1);
    long timestamp112 = timestamp(2001, 1, 2);
    long timestamp121 = timestamp(2001, 2, 1);
    long timestamp122 = timestamp(2001, 2, 2);
    long timestamp211 = timestamp(2002, 1, 1);
    long timestamp212 = timestamp(2002, 1, 2);
    long timestamp221 = timestamp(2002, 2, 1);
    long timestamp222 = timestamp(2002, 2, 2);

    ExoSocialActivity activity111 = addActivity(rootIdentity, timestamp111);
    ExoSocialActivity activity112 = addActivity(rootIdentity, timestamp112);
    ExoSocialActivity activity121 = addActivity(rootIdentity, timestamp121);
    ExoSocialActivity activity122 = addActivity(rootIdentity, timestamp122);
    ExoSocialActivity activity211 = addActivity(rootIdentity, timestamp211);
    ExoSocialActivity activity212 = addActivity(rootIdentity, timestamp212);
    ExoSocialActivity activity221 = addActivity(rootIdentity, timestamp221);
    ExoSocialActivity activity222 = addActivity(rootIdentity, timestamp222);

    activityStorage.deleteActivity(activity111.getId());
    activityStorage.deleteActivity(activity112.getId());
    activityStorage.deleteActivity(activity121.getId());
    activityStorage.deleteActivity(activity122.getId());
    activityStorage.deleteActivity(activity211.getId());
    activityStorage.deleteActivity(activity212.getId());
    activityStorage.deleteActivity(activity221.getId());
    activityStorage.deleteActivity(activity222.getId());

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(rootIdentity);
    assertEquals(0, activities.size());

  }

  @MaxQueryNumber(165)
  public void testRelationshipActivity() throws Exception {
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("I am now connected with @receiverRemoteId");
    activity.setType("exosocial:relationship");
    // Shindig's Activity's fields
    activity.setAppId("appId");
    activity.setBody("body");
    activity.setBodyId("bodyId");
    activity.setTitleId(TitleId.CONNECTION_REQUESTED.toString());
    activity.setExternalId("externalId");
    // activity.setId("id");
    activity.setUrl("http://www.exoplatform.org");

    Map<String, String> params = new HashMap<String, String>();
    params.put("SENDER", "senderRemoteId");
    params.put("RECEIVER", "receiverRemoteId");
    params.put("RELATIONSHIP_UUID", "relationship_id");
    activity.setTemplateParams(params);

    activityStorage.saveActivity(rootIdentity, activity);

    List<ExoSocialActivity> activities = activityStorage.getUserActivities(rootIdentity);
    assertNotNull(activities);
    assertEquals(1, activities.size());

    for (ExoSocialActivity element : activities) {

      // title
      assertNotNull(element.getTitle());
      // type
      assertNotNull(element.getType());
      // appId
      assertNotNull(element.getAppId());
      // body
      assertNotNull(element.getBody());
      // bodyId
      assertNotNull(element.getBodyId());
      // titleId
      assertEquals(TitleId.CONNECTION_REQUESTED.toString(), element.getTitleId());
      // externalId
      assertNotNull(element.getExternalId());
      // id
      // assertNotNull(element.getId());
      // url
      assertEquals("http://www.exoplatform.org", element.getUrl());
      // id
      assertNotNull(element.getUserId());
      // templateParams
      assertNotNull(element.getTemplateParams());

    }

  }

  @MaxQueryNumber(366)
  public void testActivityProcessing() throws Exception {

    //
    BaseActivityProcessorPlugin processor = new DummyProcessor(null);
    activityStorage.getActivityProcessors().add(processor);
    try {
      //
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity");
      activityStorage.saveActivity(rootIdentity, activity);
      assertNotNull(activity.getId());

      //
      ExoSocialActivity got = activityStorage.getActivity(activity.getId());
      assertEquals(activity.getId(), got.getId());
      assertEquals("edited", got.getTitle());

      //
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("comment");
      comment.setUserId(rootIdentity.getId());
      activityStorage.saveComment(activity, comment);
      assertNotNull(comment.getId());

      //
      ExoSocialActivity gotComment = activityStorage.getActivity(comment.getId());
      assertEquals(comment.getId(), gotComment.getId());
      assertEquals("edited", gotComment.getTitle());

      //
      ExoSocialActivity gotParentActivity = activityStorage.getParentActivity(comment);
      assertEquals(activity.getId(), gotParentActivity.getId());
      assertEquals("edited", gotParentActivity.getTitle());
      assertEquals(1, activity.getReplyToId().length);
      assertEquals(comment.getId(), activity.getReplyToId()[0]);
    } finally {
      //
      activityStorage.getActivityProcessors().remove(processor);
    }
  }

  /**
   * Gets an instance of Space.
   *
   * @return an instance of space
   */
  private Space getSpaceInstance() {
    Space space = new Space();
    space.setDisplayName("myspace");
    space.setPrettyName(space.getDisplayName());
    space.setRegistration(Space.OPEN);
    space.setDescription("add new space");
    space.setType(DefaultSpaceApplicationHandler.NAME);
    space.setVisibility(Space.PUBLIC);
    space.setPriority(Space.INTERMEDIATE_PRIORITY);
    space.setGroupId("/space/space");
    space.setUrl(space.getPrettyName());
    String[] managers = new String[] { "john", "demo" };
    space.setManagers(managers);
    return space;
  }

  private ExoSocialActivity addActivity(Identity identity, long timestamp) {

    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("activity title");
    activity.setPostedTime(timestamp);
    activityStorage.saveActivity(identity, activity);

    return activity;

  }

  private long timestamp(int year, int month, int day) {

    Calendar cal = Calendar.getInstance();
    cal.set(year, month, day, 0, 0, 0);
    return cal.getTime().getTime();

  }

  class DummyProcessor extends BaseActivityProcessorPlugin {

    DummyProcessor(final InitParams params) {
      super(params);
    }

    @Override
    public void processActivity(final ExoSocialActivity activity) {
      activity.setTitle("edited");
    }
  }

  private Space getSpaceInstance(int number) {
    Space space = new Space();
    space.setApp("app");
    space.setDisplayName("myspace " + number);
    space.setPrettyName(space.getDisplayName());
    space.setRegistration(Space.OPEN);
    space.setDescription("add new space " + number);
    space.setType(DefaultSpaceApplicationHandler.NAME);
    space.setVisibility(Space.PUBLIC);
    space.setPriority(Space.INTERMEDIATE_PRIORITY);
    space.setGroupId("/spaces/space" + number);
    String[] managers = new String[] { "demo", "tom" };
    String[] members = new String[] { "raul", "ghost", "dragon" };
    String[] invitedUsers = new String[] { "register1", "mary" };
    String[] pendingUsers = new String[] { "jame", "paul", "hacker" };
    space.setInvitedUsers(invitedUsers);
    space.setPendingUsers(pendingUsers);
    space.setManagers(managers);
    space.setMembers(members);
    space.setUrl(space.getPrettyName());
    return space;
  }

  private long getSinceTime() {
    Calendar cal = Calendar.getInstance();
    return cal.getTimeInMillis();
  }

  /**
   * Checks clean data.
   * 
   * @since 1.2.0-Beta3
   */
  private void checkCleanData() {
    assertEquals("assertEquals(activityStorage.getActivities(rootIdentity).size() must be 0",
                 0,
                 activityStorage.getUserActivities(rootIdentity, 0, activityStorage.getNumberOfUserActivities(rootIdentity))
                                .size());
    assertEquals("assertEquals(activityStorage.getActivities(johnIdentity).size() must be 0",
                 0,
                 activityStorage.getUserActivities(johnIdentity, 0, activityStorage.getNumberOfUserActivities(johnIdentity))
                                .size());
    assertEquals("assertEquals(activityStorage.getActivities(maryIdentity).size() must be 0",
                 0,
                 activityStorage.getUserActivities(maryIdentity, 0, activityStorage.getNumberOfUserActivities(maryIdentity))
                                .size());
    assertEquals("assertEquals(activityStorage.getActivities(demoIdentity).size() must be 0",
                 0,
                 activityStorage.getUserActivities(demoIdentity, 0, activityStorage.getNumberOfUserActivities(demoIdentity))
                                .size());
  }

  /**
   * Deletes connections of identity.
   * 
   * @param existingIdentity
   * @since 1.2.0-Beta3
   */
  private void deleteConnections(Identity existingIdentity) {
    List<Relationship> allConnections = relationshipManager.getAll(existingIdentity);
    for (Relationship relationship : allConnections) {
      relationshipManager.remove(relationship);
    }
  }

  /**
   * Gets the relationship manager.
   * 
   * @return
   * @since 1.2.0-Beta3
   */
  private RelationshipManager getRelationshipManager() {
    return (RelationshipManager) getContainer().getComponentInstanceOfType(RelationshipManager.class);
  }

  /**
   * Gets the space service.
   * 
   * @return the space service
   */
  private SpaceService getSpaceService() {
    return (SpaceService) getContainer().getComponentInstanceOfType(SpaceService.class);
  }

  /**
   * Gets an instance of the space.
   * 
   * @param spaceService
   * @param number
   * @return
   * @throws Exception
   * @since 1.2.0-GA
   */
  private Space getSpaceInstance(SpaceService spaceService, int number) throws Exception {
    Space space = new Space();
    space.setDisplayName("my space " + number);
    space.setPrettyName(space.getDisplayName());
    space.setRegistration(Space.OPEN);
    space.setDescription("add new space " + number);
    space.setType(DefaultSpaceApplicationHandler.NAME);
    space.setVisibility(Space.PUBLIC);
    space.setRegistration(Space.VALIDATION);
    space.setPriority(Space.INTERMEDIATE_PRIORITY);
    space.setGroupId("/space/space" + number);
    space.setUrl(space.getPrettyName());
    String[] managers = new String[] { "demo" };
    String[] members = new String[] { "demo" };
    String[] invitedUsers = new String[] { "mary" };
    String[] pendingUsers = new String[] { "john", };
    space.setInvitedUsers(invitedUsers);
    space.setPendingUsers(pendingUsers);
    space.setManagers(managers);
    space.setMembers(members);
    spaceService.saveSpace(space, true);
    tearDownSpaceList.add(space);
    return space;
  }

  private void createActivities(int number, Identity owner) {
    for (int i = 0; i < number; i++) {
      restartTransaction();
      ExoSocialActivity activity = new ExoSocialActivityImpl();
      activity.setTitle("activity title " + i);
      activity.setUserId(owner.getId());
      activityStorage.saveActivity(owner, activity);
      tearDownActivityList.add(activity);
      LOG.info("owner = " + owner.getRemoteId() + " PostedTime = " + activity.getPostedTime());
    }
    restartTransaction();
  }

  private ExoSocialActivity createActivity(int num) {
    //
    ExoSocialActivity activity = new ExoSocialActivityImpl();
    activity.setTitle("Activity " + num);
    activity.setTitleId("TitleID: " + activity.getTitle());
    activity.setType("UserActivity");
    activity.setBody("Body of " + activity.getTitle());
    activity.setBodyId("BodyId of " + activity.getTitle());
    activity.setLikeIdentityIds(new String[] { "demo", "mary" });
    activity.setMentionedIds(new String[] { "demo", "john" });
    activity.setCommentedIds(new String[] {});
    activity.setReplyToId(new String[] {});
    activity.setAppId("AppID");
    activity.setExternalId("External ID");

    return activity;
  }

  /**
   * Creates a comment to an existing activity.
   *
   * @param existingActivity the existing activity
   * @param posterIdentity the identity who comments
   * @param commentReplyIdentity
   * @param number the number of comments
   */
  private void createComment(ExoSocialActivity existingActivity,
                             Identity posterIdentity,
                             Identity commentReplyIdentity,
                             int number,
                             int numberOfSubComments) {
    for (int i = 0; i < number; i++) {
      ExoSocialActivity comment = new ExoSocialActivityImpl();
      comment.setTitle("comment " + i);
      comment.setUserId(posterIdentity.getId());
      comment.setPosterId(posterIdentity.getId());
      activityStorage.saveComment(existingActivity, comment);

      for (int j = 0; j < numberOfSubComments; j++) {
        ExoSocialActivity commentReply = new ExoSocialActivityImpl();
        commentReply.setTitle("comment reply " + i + " " + j);
        commentReply.setUserId(commentReplyIdentity.getId());
        commentReply.setPosterId(commentReplyIdentity.getId());
        commentReply.setParentCommentId(comment.getId());
        activityStorage.saveComment(existingActivity, commentReply);
      }
    }
  }

  /**
   * Gets an instance of Space.
   *
   * @param number
   * @return an instance of space
   */
  private Space getSpaceInstance(int number, String visible, String registration, String manager, String... members) {
    Space space = new Space();
    space.setApp("app");
    space.setDisplayName("my space " + number);
    space.setPrettyName(space.getDisplayName());
    space.setRegistration(registration);
    space.setDescription("add new space " + number);
    space.setType(DefaultSpaceApplicationHandler.NAME);
    space.setVisibility(visible);
    space.setPriority(Space.INTERMEDIATE_PRIORITY);
    space.setGroupId("/spaces/space" + number);
    String[] managers = new String[] { manager };
    String[] invitedUsers = new String[] {};
    String[] pendingUsers = new String[] {};
    space.setInvitedUsers(invitedUsers);
    space.setPendingUsers(pendingUsers);
    space.setManagers(managers);
    space.setMembers(members);
    space.setUrl(space.getPrettyName());
    tearDownSpaceList.add(space);
    return space;
  }

  /**
   * Gets an instance of Space.
   *
   * @param number
   * @return an instance of space
   */
  private Space getSpaceInstanceInvitedMember(int number,
                                              String visible,
                                              String registration,
                                              String[] invitedMember,
                                              String manager,
                                              String... members) {
    Space space = new Space();
    space.setApp("app");
    space.setDisplayName("my space " + number);
    space.setPrettyName(space.getDisplayName());
    space.setRegistration(registration);
    space.setDescription("add new space " + number);
    space.setType(DefaultSpaceApplicationHandler.NAME);
    space.setVisibility(visible);
    space.setPriority(Space.INTERMEDIATE_PRIORITY);
    space.setGroupId("/spaces/space" + number);
    space.setUrl(space.getPrettyName());
    String[] managers = new String[] { manager };
    // String[] invitedUsers = new String[] {invitedMember};
    String[] pendingUsers = new String[] {};
    space.setInvitedUsers(invitedMember);
    space.setPendingUsers(pendingUsers);
    space.setManagers(managers);
    space.setMembers(members);
    tearDownSpaceList.add(space);
    return space;
  }

}
