/**
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file
 * except in compliance with the License. A copy of the License is located at
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "LICENSE.TXT" file accompanying this file. This file is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under the License.
 */

package org.apache.hadoop.dynamodb;

import static org.apache.hadoop.dynamodb.DynamoDBConstants.DEFAULT_MAX_ITEM_SIZE;
import static org.mockito.Mockito.mock;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemRequest;
import com.amazonaws.services.dynamodbv2.model.BatchWriteItemResult;
import com.amazonaws.services.dynamodbv2.model.Capacity;
import com.amazonaws.services.dynamodbv2.model.ConsumedCapacity;
import com.amazonaws.services.dynamodbv2.model.WriteRequest;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.hamcrest.core.Is;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DynamoDBClientTest {

  private static final String TEST_PROXY_HOST = "test.proxy.host";
  private static final int TEST_PROXY_PORT = 5555;
  private static final String TEST_USERNAME = "username";
  private static final String TEST_PASSWORD = "password";

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  AmazonDynamoDBClient mockClient = mock(AmazonDynamoDBClient.class);
  Configuration conf = new Configuration();
  ClientConfiguration clientConf;
  DynamoDBClient client;

  @Before
  public void setup() {
    conf.clear();
    clientConf = new ClientConfiguration();
    client = new DynamoDBClient(mockClient, conf);
  }

  @Test
  public void testDynamoDBCredentials() {
    final String DYNAMODB_ACCESS_KEY = "abc";
    final String DYNAMODB_SECRET_KEY = "xyz";
    Configuration conf = new Configuration();
    conf.set(DynamoDBConstants.DYNAMODB_ACCESS_KEY_CONF, DYNAMODB_ACCESS_KEY);
    conf.set(DynamoDBConstants.DYNAMODB_SECRET_KEY_CONF, DYNAMODB_SECRET_KEY);

    DynamoDBClient dynamoDBClient = new DynamoDBClient();
    AWSCredentialsProvider provider = dynamoDBClient.getAWSCredentialsProvider(conf);
    Assert.assertEquals(DYNAMODB_ACCESS_KEY, provider.getCredentials().getAWSAccessKeyId());
    Assert.assertEquals(DYNAMODB_SECRET_KEY, provider.getCredentials().getAWSSecretKey());
  }

  @Test
  public void testDefaultCredentials() {
    final String DEFAULT_ACCESS_KEY = "abc";
    final String DEFAULT_SECRET_KEY = "xyz";
    Configuration conf = new Configuration();
    conf.set(DynamoDBConstants.DEFAULT_ACCESS_KEY_CONF, DEFAULT_ACCESS_KEY);
    conf.set(DynamoDBConstants.DEFAULT_SECRET_KEY_CONF, DEFAULT_SECRET_KEY);

    DynamoDBClient dynamoDBClient = new DynamoDBClient();
    AWSCredentialsProvider provider = dynamoDBClient.getAWSCredentialsProvider(conf);
    Assert.assertEquals(DEFAULT_ACCESS_KEY, provider.getCredentials().getAWSAccessKeyId());
    Assert.assertEquals(DEFAULT_SECRET_KEY, provider.getCredentials().getAWSSecretKey());
  }

  @Test
  public void testCustomCredentialsProvider() {
    final String MY_ACCESS_KEY = "abc";
    final String MY_SECRET_KEY = "xyz";
    Configuration conf = new Configuration();
    conf.set("my.accessKey", MY_ACCESS_KEY);
    conf.set("my.secretKey", MY_SECRET_KEY);
    conf.set(DynamoDBConstants.CUSTOM_CREDENTIALS_PROVIDER_CONF, MyAWSCredentialsProvider.class
        .getName());

    DynamoDBClient dynamoDBClient = new DynamoDBClient();
    AWSCredentialsProvider provider = dynamoDBClient.getAWSCredentialsProvider(conf);
    Assert.assertEquals(MY_ACCESS_KEY, provider.getCredentials().getAWSAccessKeyId());
    Assert.assertEquals(MY_SECRET_KEY, provider.getCredentials().getAWSSecretKey());
  }

  @Test
  public void testCustomProviderNotFound() {
    Configuration conf = new Configuration();
    conf.set(DynamoDBConstants.CUSTOM_CREDENTIALS_PROVIDER_CONF, "org.apache.hadoop.dynamodb" +
        ".NonExistentCredentialsProvider");
    DynamoDBClient dynamoDBClient = new DynamoDBClient();
    expectedException.expectCause(Is.isA(ClassNotFoundException.class));
    dynamoDBClient.getAWSCredentialsProvider(conf);
  }

  @Test
  public void testCustomProviderCannotCast() {
    Configuration conf = new Configuration();
    conf.set(DynamoDBConstants.CUSTOM_CREDENTIALS_PROVIDER_CONF, Object.class.getName());
    DynamoDBClient dynamoDBClient = new DynamoDBClient();
    expectedException.expect(ClassCastException.class);
    dynamoDBClient.getAWSCredentialsProvider(conf);
  }

  @Test
  public void testBasicSessionCredentials(){
    final String DYNAMODB_ACCESS_KEY = "abc";
    final String DYNAMODB_SECRET_KEY = "xyz";
    final String DYNAMODB_SESSION_KEY = "007";
    Configuration conf = new Configuration();
    conf.set(DynamoDBConstants.DYNAMODB_ACCESS_KEY_CONF, DYNAMODB_ACCESS_KEY);
    conf.set(DynamoDBConstants.DYNAMODB_SECRET_KEY_CONF, DYNAMODB_SECRET_KEY);
    conf.set(DynamoDBConstants.DYNAMODB_SESSION_TOKEN_CONF, DYNAMODB_SESSION_KEY);

    DynamoDBClient dynamoDBClient = new DynamoDBClient();
    AWSCredentialsProvider provider = dynamoDBClient.getAWSCredentialsProvider(conf);
    AWSSessionCredentials sessionCredentials = (AWSSessionCredentials) provider.getCredentials();
    Assert.assertEquals(DYNAMODB_ACCESS_KEY, sessionCredentials.getAWSAccessKeyId());
    Assert.assertEquals(DYNAMODB_SECRET_KEY, sessionCredentials.getAWSSecretKey());
    Assert.assertEquals(DYNAMODB_SESSION_KEY, sessionCredentials.getSessionToken());

  }

  @Test
  public void setsClientConfigurationProxyHostAndPortWhenBothAreSupplied() {
    setTestProxyHostAndPort(conf);
    client.applyProxyConfiguration(clientConf, conf);
    Assert.assertEquals(TEST_PROXY_HOST, clientConf.getProxyHost());
    Assert.assertEquals(TEST_PROXY_PORT, clientConf.getProxyPort());
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenProxyPortIsMissing() {
    setProxyHostAndPort(conf, "test.proxy.host", 0);
    client.applyProxyConfiguration(clientConf, conf);
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenProxyHostIsMissing() {
    setProxyHostAndPort(conf, null, 5555);
    client.applyProxyConfiguration(clientConf, conf);
  }

  @Test
  public void
  setsClientConfigurationProxyUsernameAndPasswordWhenBothAreSuppliedWithProxyHostAndPort() {
    setTestProxyHostAndPort(conf);
    setProxyUsernameAndPassword(conf, "username", "password");
    client.applyProxyConfiguration(clientConf, conf);
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenProxyUsernameIsMissing() {
    setTestProxyHostAndPort(conf);
    setProxyUsernameAndPassword(conf, null, "password");
    client.applyProxyConfiguration(clientConf, conf);
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenProxyPasswordIsMissing() {
    setTestProxyHostAndPort(conf);
    conf.set(DynamoDBConstants.PROXY_USERNAME, "username");
    client.applyProxyConfiguration(clientConf, conf);
  }

  @Test(expected = RuntimeException.class)
  public void throwsWhenGivenProxyUsernameAndPasswordWithoutProxyHostAndPortAreNotSupplied() {
    setProxyUsernameAndPassword(conf, TEST_USERNAME, TEST_PASSWORD);
    client.applyProxyConfiguration(clientConf, conf);
  }

  @Test(expected = RuntimeException.class)
  public void testPutBatchThrowsWhenItemIsTooLarge() throws Exception {
    Map<String, AttributeValue> item = ImmutableMap.of("",
        new AttributeValue(Strings.repeat("a", (int) (DEFAULT_MAX_ITEM_SIZE + 1))));
    client.putBatch("dummyTable", item, 1, null, false);
  }

  @Test
  public void testPutBatchDoesNotThrowWhenItemIsNotTooLarge() throws Exception {
    Map<String, AttributeValue> item = ImmutableMap.of("",
        new AttributeValue(Strings.repeat("a", (int) DEFAULT_MAX_ITEM_SIZE)));
    client.putBatch("dummyTable", item, 1, null, false);
  }

  @Test
  public void testPutBatchDeletionModeSuccessful() throws Exception {
    Map<String, AttributeValue> item = ImmutableMap.of("",
            new AttributeValue(Strings.repeat("a", (int) DEFAULT_MAX_ITEM_SIZE)));

    client.putBatch("dummyTable", item, 1, null, true);

    for (Map.Entry<String, List<WriteRequest>> entry: client.getWriteBatchMap().entrySet()) {
      for (WriteRequest req: entry.getValue()) {
        Assert.assertNotNull(req.getDeleteRequest());
        Assert.assertNull(req.getPutRequest());
      }
    }
  }

  @Test
  public void testPutMultipleBatchDeletionModeSuccessful() throws Exception {
    Map<String, AttributeValue> item = ImmutableMap.of("",
            new AttributeValue(Strings.repeat("a", (int) DEFAULT_MAX_ITEM_SIZE)));

    Mockito.when(mockClient.batchWriteItem(Mockito.<BatchWriteItemRequest>any())).thenAnswer(i -> {
      BatchWriteItemResult result = new BatchWriteItemResult();
      BatchWriteItemRequest request = (BatchWriteItemRequest) i.getArguments()[0];
      result.setUnprocessedItems(request.getRequestItems());

      ConsumedCapacity consumedCapacity = new ConsumedCapacity();
      Capacity capacity  = new Capacity();
      capacity.setCapacityUnits(100.0);
      consumedCapacity.setTable(capacity);

      result.setConsumedCapacity(ImmutableList.of(consumedCapacity));
      return result;
    });
    for (int i = 0; i < 5; ++i) {
      client.putBatch("dummyTable", item, 2, null, true);
    }

    for (Map.Entry<String, List<WriteRequest>> entry: client.getWriteBatchMap().entrySet()) {
      for (WriteRequest req: entry.getValue()) {
        Assert.assertNotNull(req.getDeleteRequest());
        Assert.assertNull(req.getPutRequest());
      }
    }
  }

  @Test
  public void testPutBatchDeletionModeSuccessfulWithAdditionalKeysInItem() throws Exception {
    Map<String, AttributeValue> item = ImmutableMap.of(
        "a", new AttributeValue().withS("a"),
        "b", new AttributeValue().withS("b")
    );

    conf.set(DynamoDBConstants.DYNAMODB_TABLE_KEY_NAMES, "a");

    client.putBatch("dummyTable", item, 1, null, true);

    for (Map.Entry<String, List<WriteRequest>> entry: client.getWriteBatchMap().entrySet()) {
      for (WriteRequest req: entry.getValue()) {
        Assert.assertNotNull(req.getDeleteRequest());
        Assert.assertEquals(1, req.getDeleteRequest().getKey().size());
        Assert.assertTrue(req.getDeleteRequest().getKey().containsKey("a"));
        Assert.assertNull(req.getPutRequest());
      }
    }
  }

  @Test
  public void testPutBatchDeletionFailsAsGivenItemDoesNotContainAnyKey() throws Exception {
    Map<String, AttributeValue> item = ImmutableMap.of(
        "c", new AttributeValue().withS("a"),
        "d", new AttributeValue().withS("b")
    );

    conf.set(DynamoDBConstants.DYNAMODB_TABLE_KEY_NAMES, "a,b");

    Assert.assertThrows(IllegalArgumentException.class, () ->
        client.putBatch("dummyTable", item, 1, null, true));
  }

  private void setTestProxyHostAndPort(Configuration conf) {
    setProxyHostAndPort(conf, TEST_PROXY_HOST, TEST_PROXY_PORT);
  }

  private void setProxyHostAndPort(Configuration conf, String host, int port) {
    if (!Strings.isNullOrEmpty(host)) {
      conf.set(DynamoDBConstants.PROXY_HOST, host);
    }
    if (port > 0) {
      conf.setInt(DynamoDBConstants.PROXY_PORT, port);
    }
  }

  private void setProxyUsernameAndPassword(Configuration conf, String username, String password) {
    if (!Strings.isNullOrEmpty(username)) {
      conf.set(DynamoDBConstants.PROXY_USERNAME, username);
    }
    if (!Strings.isNullOrEmpty(password)) {
      conf.set(DynamoDBConstants.PROXY_PASSWORD, password);
    }
  }

  private static class MyAWSCredentialsProvider implements AWSCredentialsProvider, Configurable {
    private Configuration conf;
    private String accessKey;
    private String secretKey;

    private void init() {
      accessKey = conf.get("my.accessKey");
      secretKey = conf.get("my.secretKey");
    }

    @Override
    public AWSCredentials getCredentials() {
      return new BasicAWSCredentials(accessKey, secretKey);
    }

    @Override
    public void refresh() {

    }

    @Override
    public Configuration getConf() {
      return this.conf;
    }

    @Override
    public void setConf(Configuration configuration) {
      this.conf = configuration;
      init();
    }
  }

}
