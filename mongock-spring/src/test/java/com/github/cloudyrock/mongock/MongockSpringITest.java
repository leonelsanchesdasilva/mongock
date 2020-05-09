package com.github.cloudyrock.mongock;

import com.github.cloudyrock.mongock.driver.mongodb.springdata.v3.SpringDataMongo3Driver;
import com.github.cloudyrock.mongock.test.changelogs.MongockTestResource;
import com.github.cloudyrock.mongock.utils.IndependentDbIntegrationTestBase;
import org.bson.Document;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class MongockSpringITest extends IndependentDbIntegrationTestBase {

  private static final String CHANGELOG_COLLECTION_NAME = "mongockChangeLog";
  private ApplicationContext springContextMock;

  @Before
  public void before() {
    setSpringContext();
  }

  @Test
  public void shouldExecuteAllChangeSets() {
    // given
    setSpringContext();
    MongockInitializingBeanRunner runner = new SpringMongockBuilder(MongockTestResource.class.getPackage().getName())
        .setDriver(buildDriver())
        .setLockQuickConfig()
        .setApplicationContext(springContextMock)
        .buildInitializingBeanRunner();

    // when
    runner.execute();

    // then

    // dbchangelog collection checking
    long change1 = mongoTemplate.getCollection(CHANGELOG_COLLECTION_NAME).countDocuments(new Document()
        .append("changeId", "test1")
        .append("author", "testuser"));
    assertEquals(1, change1);
  }

  @Test
  public void shouldStoreMetadata_WhenChangeSetIsTrack_IfAddedInBuilder() {
    // given
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("string_key", "string_value");
    metadata.put("integer_key", 10);
    metadata.put("float_key", 11.11F);
    metadata.put("double_key", 12.12D);
    metadata.put("long_key", 13L);
    metadata.put("boolean_key", true);
    setSpringContext();
    MongockInitializingBeanRunner runner = new SpringMongockBuilder(MongockTestResource.class.getPackage().getName())
        .setDriver(buildDriver())
        .setLockQuickConfig()
        .setApplicationContext(springContextMock)
        .withMetadata(metadata)
        .buildInitializingBeanRunner();

    // when
    runner.execute();

    // then
    Map metadataResult = mongoTemplate.getDb().getCollection(CHANGELOG_COLLECTION_NAME).find().first().get("metadata", Map.class);
    assertEquals("string_value", metadataResult.get("string_key"));
    assertEquals(10, metadataResult.get("integer_key"));
    assertEquals(11.11F, (Double) metadataResult.get("float_key"), 0.01);
    assertEquals(12.12D, (Double) metadataResult.get("double_key"), 0.01);
    assertEquals(13L, metadataResult.get("long_key"));
    assertEquals(true, metadataResult.get("boolean_key"));

  }


  private void setSpringContext() {
    Environment springEnvironmentMock = mock(Environment.class);
    springContextMock = mock(ApplicationContext.class);
    when(springContextMock.getEnvironment()).thenReturn(springEnvironmentMock);
    when(springContextMock.getBean(Environment.class)).thenReturn(springEnvironmentMock);
  }

  @Test
  public void shouldTwoExecutedChangeSet_whenRunningTwice_ifRunAlways() {
    // given
    MongockInitializingBeanRunner runner = new SpringMongockBuilder(MongockTestResource.class.getPackage().getName())
        .setDriver(buildDriver())
        .setLockQuickConfig()
        .setApplicationContext(springContextMock)
        .buildInitializingBeanRunner();


    // when
    runner.execute();
    runner.execute();

    // then
    List<Document> documentList = new ArrayList<>();

    mongoTemplate.getDb().getCollection(CHANGELOG_COLLECTION_NAME)
        .find(new Document().append("changeSetMethod", "testChangeSetWithAlways").append("state", "EXECUTED"))
        .forEach(documentList::add);
    Assert.assertEquals(2, documentList.size());

  }

  @Test
  public void shouldOneExecutedAndOneIgnoredChangeSet_whenRunningTwice_ifNotRunAlways() {
    // given
    MongockInitializingBeanRunner runner = new SpringMongockBuilder(MongockTestResource.class.getPackage().getName())
        .setDriver(buildDriver())
        .setLockQuickConfig()
        .setApplicationContext(springContextMock)
        .buildInitializingBeanRunner();


    // when
    runner.execute();
    runner.execute();

    // then
    List<String> stateList = new ArrayList<>();

    mongoTemplate.getDb().getCollection(CHANGELOG_COLLECTION_NAME)
        .find(new Document()
            .append("changeLogClass", "com.github.cloudyrock.mongock.test.changelogs.AnotherMongockTestResource")
            .append("changeSetMethod", "testChangeSet"))
        .map(document-> document.getString("state"))
        .forEach(stateList::add);
    Assert.assertEquals(2, stateList.size());
    Assert.assertTrue(stateList.contains("EXECUTED"));
    Assert.assertTrue(stateList.contains("IGNORED"));
  }


  private SpringDataMongo3Driver buildDriver() {
    SpringDataMongo3Driver driver = new SpringDataMongo3Driver(mongoTemplate);
    driver.setChangeLogCollectionName(CHANGELOG_COLLECTION_NAME);
    return driver;
  }
}
