/*
 * Copyright (c)  2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.extension.siddhi.map.text.sinkmapper;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.testng.Assert;
import org.testng.AssertJUnit;
import org.testng.TestException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.wso2.extension.siddhi.map.text.sinkmapper.util.HttpServerListenerHandler;
import org.wso2.siddhi.core.SiddhiAppRuntime;
import org.wso2.siddhi.core.SiddhiManager;
import org.wso2.siddhi.core.exception.NoSuchAttributeException;
import org.wso2.siddhi.core.stream.input.InputHandler;
import org.wso2.siddhi.core.stream.output.sink.InMemorySink;
import org.wso2.siddhi.core.util.SiddhiTestHelper;
import org.wso2.siddhi.core.util.transport.InMemoryBroker;
import org.wso2.siddhi.query.api.SiddhiApp;
import org.wso2.siddhi.query.api.annotation.Annotation;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.definition.StreamDefinition;
import org.wso2.siddhi.query.api.execution.query.Query;
import org.wso2.siddhi.query.api.execution.query.input.stream.InputStream;
import org.wso2.siddhi.query.api.execution.query.selection.Selector;
import org.wso2.siddhi.query.api.expression.Variable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test Case for QLT type output text mapper.
 */
public class TextCustomSinkMapperTestCase {
    private static final Logger log = Logger.getLogger(TextCustomSinkMapperTestCase.class);
    private AtomicInteger wso2Count = new AtomicInteger(0);
    private AtomicInteger ibmCount = new AtomicInteger(0);
    private int waitTime = 50;
    private int timeout = 30000;

    @BeforeMethod
    public void init() {
        wso2Count.set(0);
        ibmCount.set(0);
    }

    @Test
    public void testTextSinkCustomMapping() throws InterruptedException {
        log.info("Test custom text mapping.");
        List<Object> onMessageList = new ArrayList<Object>();

        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='inMemory', topic='{{symbol}}', @map(type='text', @payload(\"Stock price of {{symbol}} is" +
                " {{price}}\"))) " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();
        stockStream.send(new Object[]{"WSO2", 55.6f, 100L});
        stockStream.send(new Object[]{"IBM", 75.6f, 100L});
        stockStream.send(new Object[]{"WSO2", 57.6f, 100L});
        SiddhiTestHelper.waitForEvents(waitTime, 2, wso2Count, timeout);
        SiddhiTestHelper.waitForEvents(waitTime, 1, ibmCount, timeout);

        //assert event count
        Assert.assertEquals(wso2Count.get(), 2);
        Assert.assertEquals(ibmCount.get(), 1);
        //assert custom text
        Assert.assertEquals(onMessageList.get(0).toString(), "Stock price of WSO2 is 55.6");
        Assert.assertEquals(onMessageList.get(1).toString(), "Stock price of IBM is 75.6");
        Assert.assertEquals(onMessageList.get(2).toString(), "Stock price of WSO2 is 57.6");
        siddhiAppRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }


    @Test
    public void testTextSinkMapperEventGroupDefaultDelimiter() throws InterruptedException {
        log.info("Test for default delimiter");
        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='inMemory', topic='{{symbol}}', @map(type='text' , event.grouping.enabled='true' , " +
                "@payload(\"Stock price of {{symbol}} is {{price}}\"))) " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();
        ArrayList<org.wso2.siddhi.core.event.Event> arrayList = new ArrayList<>(10);
        for (int j = 0; j < 5; j++) {
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"WSO2", 55.6f, 10}));
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"IBM", 75.6f, 10}));
        }
        stockStream.send(arrayList.toArray(new org.wso2.siddhi.core.event.Event[10]));
        SiddhiTestHelper.waitForEvents(waitTime, 1, wso2Count, timeout);
        SiddhiTestHelper.waitForEvents(waitTime, 1, ibmCount, timeout);

        //assert event count
        Assert.assertEquals(wso2Count.get(), 1);
        Assert.assertEquals(ibmCount.get(), 1);
        siddhiAppRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void testTextSinkMapperEventGroupCustomDelimiter() throws InterruptedException {
        log.info("Test for custom delimiter.");
        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='inMemory', topic='{{symbol}}', @map(type='text' , event.grouping.enabled='true'," +
                "delimiter='#######' , @payload(\"Stock price of {{symbol}} is" +
                " {{price}}\"))) " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();
        ArrayList<org.wso2.siddhi.core.event.Event> arrayList = new ArrayList<>(100);
        for (int j = 0; j < 5; j++) {
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"WSO2", 55.6f, 10}));
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"IBM", 75.6f, 10}));
        }
        stockStream.send(arrayList.toArray(new org.wso2.siddhi.core.event.Event[10]));
        SiddhiTestHelper.waitForEvents(waitTime, 1, wso2Count, timeout);
        SiddhiTestHelper.waitForEvents(waitTime, 1, ibmCount, timeout);

        //assert event count
        Assert.assertEquals(wso2Count.get(), 1);
        Assert.assertEquals(ibmCount.get(), 1);
        siddhiAppRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void testTextSinkMapperWithoutPayload() throws InterruptedException {
        log.info("Test custom mapping without payload.");

        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        StreamDefinition streamDefinition = StreamDefinition.id("FooStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT);

        StreamDefinition outputDefinition = StreamDefinition.id("BarStream")
                .attribute("symbol", Attribute.Type.STRING)
                .attribute("price", Attribute.Type.FLOAT)
                .attribute("volume", Attribute.Type.INT)
                .annotation(Annotation.annotation("sink")
                        .element("type", "inMemory")
                        .element("topic", "{{symbol}}")
                        .annotation(Annotation.annotation("map")
                                .element("type", "text")));

        Query query = Query.query();
        query.from(
                InputStream.stream("FooStream")
        );
        query.select(
                Selector.selector().select(new Variable("symbol"))
                        .select(new Variable("price")).select(new Variable
                        ("volume"))
        );
        query.insertInto("BarStream");

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiApp siddhiApp = new SiddhiApp("ep1");
        siddhiApp.defineStream(streamDefinition);
        siddhiApp.defineStream(outputDefinition);
        siddhiApp.addQuery(query);
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(siddhiApp);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();
        stockStream.send(new Object[]{"WSO2", 55.6f, 100L});
        stockStream.send(new Object[]{"IBM", 75.6f, 100L});
        stockStream.send(new Object[]{"WSO2", 57.6f, 100L});
        SiddhiTestHelper.waitForEvents(waitTime, 2, wso2Count, timeout);
        SiddhiTestHelper.waitForEvents(waitTime, 1, ibmCount, timeout);

        //assert event count
        Assert.assertEquals(wso2Count.get(), 2);
        Assert.assertEquals(ibmCount.get(), 1);
        siddhiAppRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void testTextSinkMapperEventGroupSingleEvent() throws InterruptedException {
        log.info("Test for event group with single event.");
        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='inMemory', topic='{{symbol}}', @map(type='text' , event.grouping.enabled='true' , " +
                "@payload(\"Stock price of {{symbol}} is {{price}}\"))) " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();
        stockStream.send(new Object[]{"WSO2", 55.6f, 10});
        stockStream.send(new Object[]{"IBM", 75.6f, 10});
        SiddhiTestHelper.waitForEvents(waitTime, 1, wso2Count, timeout);
        SiddhiTestHelper.waitForEvents(waitTime, 1, ibmCount, timeout);

        //assert event count
        Assert.assertEquals(wso2Count.get(), 1);
        Assert.assertEquals(ibmCount.get(), 1);
        siddhiAppRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test(expectedExceptions = NoSuchAttributeException.class)
    public void testTextSinkWrongMapping() throws InterruptedException {
        log.info("Test custom for wrong mapping.");
        List<Object> onMessageList = new ArrayList<Object>();

        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
                onMessageList.add(msg);
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='inMemory', topic='{{symbol}}', @map(type='text', @payload(\"Stock price of {{id}} is" +
                " {{price}}\"))) " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();
        stockStream.send(new Object[]{"WSO2", 55.6f, 100L});
        stockStream.send(new Object[]{"IBM", 75.6f, 100L});
        stockStream.send(new Object[]{"WSO2", 57.6f, 100L});
        SiddhiTestHelper.waitForEvents(waitTime, 0, wso2Count, timeout);
        SiddhiTestHelper.waitForEvents(waitTime, 0, ibmCount, timeout);

        //assert event count
        Assert.assertEquals(wso2Count.get(), 0);
        Assert.assertEquals(ibmCount.get(), 0);

        siddhiAppRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void testTextSinkMapperNotEventGroupWithCustomDelimiter() throws InterruptedException {
        log.info("Test extra delimiter.");
        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='inMemory', topic='{{symbol}}', @map(type='text' , event.grouping.enabled='false'," +
                "delimiter='#######' , @payload(\"Stock price of {{symbol}} is" +
                " {{price}}\"))) " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();
        ArrayList<org.wso2.siddhi.core.event.Event> arrayList = new ArrayList<>(100);
        for (int j = 0; j < 5; j++) {
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"WSO2", 55.6f, 10}));
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"IBM", 75.6f, 10}));
        }
        stockStream.send(arrayList.toArray(new org.wso2.siddhi.core.event.Event[10]));
        SiddhiTestHelper.waitForEvents(waitTime, 5, wso2Count, timeout);
        SiddhiTestHelper.waitForEvents(waitTime, 5, ibmCount, timeout);

        //assert event count
        Assert.assertEquals(wso2Count.get(), 5);
        Assert.assertEquals(ibmCount.get(), 5);
        siddhiAppRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void testTextSinkMapperNewLineCharacter() throws InterruptedException {
        log.info("Test with custom new lie charater.");
        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='inMemory', topic='{{symbol}}', @map(type='text' , event.grouping.enabled='false'," +
                "new.line.character='\\n' , @payload(\"Stock price of {{symbol}} is" +
                " {{price}}\"))) " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();
        ArrayList<org.wso2.siddhi.core.event.Event> arrayList = new ArrayList<>(100);
        for (int j = 0; j < 5; j++) {
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"WSO2", 55.6f, 10}));
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"IBM", 75.6f, 10}));
        }
        stockStream.send(arrayList.toArray(new org.wso2.siddhi.core.event.Event[10]));
        SiddhiTestHelper.waitForEvents(waitTime, 5, wso2Count, timeout);
        SiddhiTestHelper.waitForEvents(waitTime, 5, ibmCount, timeout);

        //assert event count
        Assert.assertEquals(wso2Count.get(), 5);
        Assert.assertEquals(ibmCount.get(), 5);
        siddhiAppRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void testTextSinkMapperNewLineCharacterWrong() throws InterruptedException {
        log.info("Test with custom new.line.character.");
        InMemoryBroker.Subscriber subscriberWSO2 = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                wso2Count.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "WSO2";
            }
        };

        InMemoryBroker.Subscriber subscriberIBM = new InMemoryBroker.Subscriber() {
            @Override
            public void onMessage(Object msg) {
                ibmCount.incrementAndGet();
            }

            @Override
            public String getTopic() {
                return "IBM";
            }
        };

        //subscribe to "inMemory" broker per topic
        InMemoryBroker.subscribe(subscriberWSO2);
        InMemoryBroker.subscribe(subscriberIBM);

        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='inMemory', topic='{{symbol}}', @map(type='text' , event.grouping.enabled='false'," +
                "new.line.character='\\r' , @payload(\"Stock price of {{symbol}} is" +
                " {{price}}\"))) " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("sink:inMemory", InMemorySink.class);
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();
        ArrayList<org.wso2.siddhi.core.event.Event> arrayList = new ArrayList<>(100);
        for (int j = 0; j < 5; j++) {
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"WSO2", 55.6f, 10}));
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"IBM", 75.6f, 10}));
        }
        stockStream.send(arrayList.toArray(new org.wso2.siddhi.core.event.Event[10]));
        SiddhiTestHelper.waitForEvents(waitTime, 5, wso2Count, timeout);
        SiddhiTestHelper.waitForEvents(waitTime, 5, ibmCount, timeout);

        //assert event count
        Assert.assertEquals(wso2Count.get(), 5);
        Assert.assertEquals(ibmCount.get(), 5);
        siddhiAppRuntime.shutdown();

        //unsubscribe from "inMemory" broker per topic
        InMemoryBroker.unsubscribe(subscriberWSO2);
        InMemoryBroker.unsubscribe(subscriberIBM);
    }

    @Test
    public void fileSinkTest() throws InterruptedException {
        log.info("test text custom map with file io");
        AtomicInteger count = new AtomicInteger();
        ClassLoader classLoader = TextDefaultSinkMapperTestCase.class.getClassLoader();
        String rootPath = classLoader.getResource("files").getFile();
        String sinkUri = rootPath + "/sink";
        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='file', @map(type='text' , @payload('Stock price of {{symbol}} is {{price}}')), " +
                "append='false', " +
                "file.uri='" + sinkUri + "/{{symbol}}.txt') " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();

        stockStream.send(new Object[]{"WSO2", 55.6f, 100L});
        stockStream.send(new Object[]{"IBM", 57.678f, 100L});
        stockStream.send(new Object[]{"GOOGLE", 50f, 100L});
        stockStream.send(new Object[]{"REDHAT", 50f, 100L});
        Thread.sleep(100);

        ArrayList<String> symbolNames = new ArrayList<>();
        symbolNames.add("WSO2.txt");
        symbolNames.add("IBM.txt");
        symbolNames.add("GOOGLE.txt");
        symbolNames.add("REDHAT.txt");

        File sink = new File(sinkUri);
        if (sink.isDirectory()) {
            for (File file : sink.listFiles()) {
                if (symbolNames.contains(file.getName())) {
                    count.incrementAndGet();
                }
            }
            AssertJUnit.assertEquals(4, count.intValue());
        } else {
            AssertJUnit.fail(sinkUri + " is not a directory.");
        }

        Thread.sleep(1000);
        siddhiAppRuntime.shutdown();
        File sinkRoot = new File(sinkUri);
        try {
            FileUtils.deleteDirectory(sinkRoot);
        } catch (IOException e) {
            throw new TestException("Failed to delete files in due to " + e.getMessage(), e);
        }
    }

    @Test
    public void fileSinkTestGroup() throws InterruptedException {
        log.info("test text default map with file io");
        AtomicInteger count = new AtomicInteger();
        ClassLoader classLoader = TextDefaultSinkMapperTestCase.class.getClassLoader();
        String rootPath = classLoader.getResource("files").getFile();
        String sinkUri = rootPath + "/sink";
        String streams = "" +
                "@App:name('TestSiddhiApp')" +
                "define stream FooStream (symbol string, price float, volume long); " +
                "@sink(type='file', @map(type='text',event.grouping.enabled='true', @payload('Stock price of " +
                "{{symbol}} is {{price}}')" +
                "), append='false', " +
                "file.uri='" + sinkUri + "/{{symbol}}.txt') " +
                "define stream BarStream (symbol string, price float, volume long); ";

        String query = "" +
                "from FooStream " +
                "select * " +
                "insert into BarStream; ";

        SiddhiManager siddhiManager = new SiddhiManager();
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(streams + query);
        InputHandler stockStream = siddhiAppRuntime.getInputHandler("FooStream");

        siddhiAppRuntime.start();

        ArrayList<org.wso2.siddhi.core.event.Event> arrayListWSO2 = new ArrayList<>(100);
        for (int j = 0; j < 5; j++) {
            arrayListWSO2.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"WSO2", 55.6f, 10}));
        }
        ArrayList<org.wso2.siddhi.core.event.Event> arrayListIBM = new ArrayList<>(100);
        for (int j = 0; j < 5; j++) {
            arrayListIBM.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"IBM", 75.6f, 10}));
        }
        stockStream.send(arrayListWSO2.toArray(new org.wso2.siddhi.core.event.Event[5]));
        stockStream.send(arrayListIBM.toArray(new org.wso2.siddhi.core.event.Event[5]));
        Thread.sleep(100);

        ArrayList<String> symbolNames = new ArrayList<>();
        symbolNames.add("WSO2.txt");
        symbolNames.add("IBM.txt");
        symbolNames.add("GOOGLE.txt");
        symbolNames.add("REDHAT.txt");

        File sink = new File(sinkUri);
        if (sink.isDirectory()) {
            for (File file : sink.listFiles()) {
                if (symbolNames.contains(file.getName())) {
                    count.incrementAndGet();
                }
            }
            AssertJUnit.assertEquals(2, count.intValue());
        } else {
            AssertJUnit.fail(sinkUri + " is not a directory.");
        }

        Thread.sleep(1000);
        siddhiAppRuntime.shutdown();
        File sinkRoot = new File(sinkUri);
        try {
            FileUtils.deleteDirectory(sinkRoot);
        } catch (IOException e) {
            throw new TestException("Failed to delete files in due to " + e.getMessage(), e);
        }
    }

    /**
     * Creating test for publishing events with TEXT mapping.
     *
     * @throws Exception Interrupted exception
     */
    @Test
    public void testHTTPTextMappingText() throws Exception {

        log.info("Creating test for publishing events with TEXT mapping.");
        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("text-output-mapper", TextSinkMapper.class);
        String inStreamDefinition = "Define stream FooStream (message String,method String,headers String);"
                + "@sink(type='http',publisher.url='http://localhost:8005/abc',method='{{method}}',"
                + "headers='{{headers}}',"
                + "@map(type='text', @payload('{{message}}'))) "
                + "Define stream BarStream (message String,method String,headers String);";
        String query = ("@info(name = 'query1') " +
                "from FooStream select message,method,headers insert into BarStream;");
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(inStreamDefinition +
                query);
        InputHandler fooStream = siddhiAppRuntime.getInputHandler("FooStream");
        siddhiAppRuntime.start();
        HttpServerListenerHandler lst = new HttpServerListenerHandler(8005);
        lst.run();
        fooStream.send(new Object[]{"WSO2,55.6,100", "POST", "'Name:John','Age:23'"});
        while (!lst.getServerListener().isMessageArrive()) {
            Thread.sleep(10);
        }
        String eventData = lst.getServerListener().getData();
        Assert.assertEquals(eventData, "WSO2,55.6,100\n");
        lst.shutdown();
        siddhiAppRuntime.shutdown();
    }

    @Test
    public void testHTTPTextMappingTextMultiple() throws Exception {

        log.info("Creating test for publishing events with TEXT mapping.");
        SiddhiManager siddhiManager = new SiddhiManager();
        siddhiManager.setExtension("text-output-mapper", TextSinkMapper.class);
        String inStreamDefinition = "Define stream FooStream (message String,method String,headers String);"
                + "@sink(type='http',publisher.url='http://localhost:8005/abc',method='{{method}}',"
                + "headers='{{headers}}',"
                + "@map(type='text', event.grouping" +
                ".enabled='true', @payload('{{message}}'))) "
                + "Define stream BarStream (message String,method String,headers String);";
        String query = ("@info(name = 'query1') " +
                "from FooStream select message,method,headers insert into BarStream;");
        SiddhiAppRuntime siddhiAppRuntime = siddhiManager.createSiddhiAppRuntime(inStreamDefinition +
                query);
        InputHandler fooStream = siddhiAppRuntime.getInputHandler("FooStream");
        siddhiAppRuntime.start();
        HttpServerListenerHandler lst = new HttpServerListenerHandler(8005);
        lst.run();
        ArrayList<org.wso2.siddhi.core.event.Event> arrayList = new ArrayList<>(5);
        for (int j = 0; j < 5; j++) {
            arrayList.add(new org.wso2.siddhi.core.event
                    .Event(System.currentTimeMillis(), new Object[]{"WSO2", "POST", "'place:office'"}));
        }
        fooStream.send(arrayList.toArray(new org.wso2.siddhi.core.event.Event[5]));
        while (!lst.getServerListener().isMessageArrive()) {
            Thread.sleep(10);
        }
        String eventData = lst.getServerListener().getData();
        Assert.assertEquals(eventData, "WSO2\n" +
                "~~~~~~~~~~\n" +
                "WSO2\n" +
                "~~~~~~~~~~\n" +
                "WSO2\n" +
                "~~~~~~~~~~\n" +
                "WSO2\n" +
                "~~~~~~~~~~\n" +
                "WSO2\n");
        lst.shutdown();
        siddhiAppRuntime.shutdown();
    }

}
