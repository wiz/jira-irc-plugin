package ut.biz.wiz.jira.plugins.irc;

import org.junit.Test;
import biz.wiz.jira.plugins.irc.MyPluginComponent;
import biz.wiz.jira.plugins.irc.MyPluginComponentImpl;

import static org.junit.Assert.assertEquals;

public class MyComponentUnitTest
{
    @Test
    public void testMyName()
    {
        MyPluginComponent component = new MyPluginComponentImpl(null);
        assertEquals("names do not match!", "myComponent",component.getName());
    }
}