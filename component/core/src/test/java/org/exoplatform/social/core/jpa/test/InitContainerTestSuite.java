/*
 * Copyright (C) 2003-2012 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.social.core.jpa.test;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runners.Suite.SuiteClasses;

import org.exoplatform.commons.testing.BaseExoContainerTestSuite;
import org.exoplatform.commons.testing.ConfigTestCase;
import org.exoplatform.social.core.application.*;
import org.exoplatform.social.core.feature.SpaceLastVisitedTest;
import org.exoplatform.social.core.feature.WhatsHotTest;
import org.exoplatform.social.core.jpa.storage.*;
import org.exoplatform.social.core.jpa.storage.dao.*;
import org.exoplatform.social.core.listeners.SocialUserProfileEventListenerImplTest;
import org.exoplatform.social.core.manager.IdentityManagerTest;
import org.exoplatform.social.core.processor.*;
import org.exoplatform.social.core.search.SortingTest;
import org.exoplatform.social.core.service.LinkProviderTest;
import org.exoplatform.social.core.space.*;
import org.exoplatform.social.core.space.spi.SpaceServiceTest;
import org.exoplatform.social.core.space.spi.SpaceTemplateServiceTest;

@SuiteClasses({
    WhatsHotTest.class,
    SortingTest.class,
    SpaceUtilsWildCardMembershipTest.class,
    ActivityDAOTest.class,
    RDBMSActivityStorageImplTest.class,
    ActivityManagerRDBMSTest.class,
    IdentityDAOTest.class,
    IdentityStorageTest.class,
    IdentityManagerTest.class,
    SpaceActivityRDBMSPublisherTest.class,
    SpaceDAOTest.class,
    SpaceMemberDAOTest.class,
    SpaceStorageTest.class,
    RDBMSSpaceStorageTest.class,
    SpaceServiceTest.class,
    SpaceTemplateServiceTest.class,
    SpaceUtilsRestTest.class,
    SpaceUtilsTest.class,
    SpaceActivityPublisherTest.class,
    SpaceLastVisitedTest.class,
    SpaceLifeCycleTest.class,
    RDBMSRelationshipManagerTest.class,
    RelationshipPublisherTest.class,
    StreamItemDAOTest.class,
    SocialUserProfileEventListenerImplTest.class,
    OSHtmlSanitizerProcessorTest.class,
    TemplateParamsProcessorTest.class,
    ProfileUpdatesPublisherTest.class,
    MentionsProcessorTest.class,
    LinkProviderTest.class,
})
@ConfigTestCase(AbstractCoreTest.class)
public class InitContainerTestSuite extends BaseExoContainerTestSuite {

  @BeforeClass
  public static void setUp() throws Exception {
    initConfiguration(InitContainerTestSuite.class);
    beforeSetup();
  }

  @AfterClass
  public static void tearDown() {
    afterTearDown();
  }
}
