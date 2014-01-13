package com.griddynamics.jagger.xml;

import com.griddynamics.jagger.JaggerLauncher;
import com.griddynamics.jagger.engine.e1.scenario.*;
import com.griddynamics.jagger.user.ProcessingConfig;
import com.griddynamics.jagger.user.TestConfiguration;
import com.griddynamics.jagger.user.TestGroupConfiguration;
import junit.framework.Assert;
import org.springframework.context.ApplicationContext;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created with IntelliJ IDEA.
 * User: kgribov
 * Date: 12/14/12
 * Time: 12:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class JaggerLoadTest {

    private ApplicationContext context;

    @BeforeClass
    public void testContext() throws Exception{
        URL directory = new URL("file:" + "../configuration/");
        Properties environmentProperties = new Properties();
        JaggerLauncher.loadBootProperties(directory, "profiles/local/environment.properties", environmentProperties);
        environmentProperties.put("chassis.master.configuration.include",environmentProperties.get("chassis.master.configuration.include")+", ../spring.schema/src/test/resources/example-load.conf.xml1");
        context = JaggerLauncher.loadContext(directory,"chassis.master.configuration",environmentProperties);
    }

    @Test
    public void testNewTest(){
        TestConfiguration testConfiguration = (TestConfiguration) context.getBean("test1");

        IterationsOrDurationStrategyConfiguration termination = (IterationsOrDurationStrategyConfiguration)testConfiguration.getTerminateStrategyConfiguration();
        Assert.assertEquals(termination.getIterations(), 255);
        Assert.assertEquals(termination.getDuration(), "2h");

        TpsClockConfiguration tps = (TpsClockConfiguration)testConfiguration.getClockConfiguration();
        Assert.assertEquals(tps.getTps(), 100d);
    }

    @Test
    public void testRpsLoad(){
        TestConfiguration testConfiguration = (TestConfiguration) context.getBean("test-rps");

        IterationsOrDurationStrategyConfiguration termination = (IterationsOrDurationStrategyConfiguration)testConfiguration.getTerminateStrategyConfiguration();
        Assert.assertEquals(255, termination.getIterations());
        Assert.assertEquals("2h", termination.getDuration());

        Assert.assertEquals(0, testConfiguration.generate(new AtomicBoolean(false)).getStartDelay());

        RpsClockConfiguration tps = (RpsClockConfiguration) testConfiguration.getClockConfiguration();
        Assert.assertEquals(100d, tps.getTps());
        Assert.assertEquals(RpsClock.class, tps.getClock().getClass());
    }

    @Test
    public void testRumpUpLoad(){
        TestConfiguration testConfiguration = (TestConfiguration) context.getBean("test-rump-up");

        IterationsOrDurationStrategyConfiguration termination = (IterationsOrDurationStrategyConfiguration)testConfiguration.getTerminateStrategyConfiguration();
        Assert.assertEquals(255, termination.getIterations());
        Assert.assertEquals("2h", termination.getDuration());

        TpsClockConfiguration tps = (TpsClockConfiguration) testConfiguration.getClockConfiguration();
        Assert.assertEquals(100d, tps.getTps());
        Assert.assertEquals(TpsClock.class, tps.getClock().getClass());
        Assert.assertEquals(10000L, tps.getWarmUpTime());
    }

    @Test
    public void testNewTestGroup(){
        TestGroupConfiguration testGroup = (TestGroupConfiguration)context.getBean("gr1");

        List<TestConfiguration> tests = testGroup.getTests();
        Assert.assertEquals(tests.size(), 1);

        TestConfiguration test = tests.get(0);
        InfiniteTerminationStrategyConfiguration termination = (InfiniteTerminationStrategyConfiguration)test.getTerminateStrategyConfiguration();
        Assert.assertEquals(10, test.getStartDelay());
        Assert.assertNotNull(termination);

        UserGroupsClockConfiguration userGroup = (UserGroupsClockConfiguration)test.getClockConfiguration();
        Assert.assertEquals(userGroup.getUsers().size(), 1);

        ProcessingConfig.Test.Task.User user = userGroup.getUsers().get(0);
        Assert.assertEquals(user.getCount(), "5");
    }
}
