package com.base2.kagura.services.camel.kagura;

import com.base2.kagura.services.camel.model.Group;
import com.base2.kagura.services.camel.model.User;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import static org.hamcrest.Matchers.*;

/**
 * @author aubels
 *         Date: 29/08/13
 */
public class AuthBeanTests {

    public String getResourcePath(String path)
    {
        URL dir_url = ClassLoader.getSystemResource(path);
        try {
            return dir_url.toURI().toString();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Test
    public void getGroupsTest()
    {
        ServerBean serverBean = new ServerBean();
        serverBean.setConfigPath(getResourcePath("TestReports"));
        AuthBean authBean = new AuthBean();
        authBean.setServerBean(serverBean);
        List<Group> groups = authBean.getGroups();
        Assert.assertNotNull(groups);
        Assert.assertThat("Incorrect number of groups returned", groups, hasSize(1));
        Assert.assertEquals("test reports", groups.get(0).getGroupname());
        Assert.assertThat(groups.get(0).getReports(), contains("fake1"));
    }
    
    @Test
    public void getUsersTest()
    {
        ServerBean serverBean = new ServerBean();
        serverBean.setConfigPath(getResourcePath("TestReports"));
        AuthBean authBean = new AuthBean();
        authBean.setServerBean(serverBean);
        List<User> users = authBean.getUsers();
        Assert.assertNotNull(users);
        Assert.assertThat("Incorrect number of users returned", users, hasSize(2));
        Assert.assertThat(users, contains(
                allOf(hasProperty("username", equalTo("testuser")), hasProperty("password", equalTo("testuserpass")), hasProperty("groups", hasSize(1)), hasProperty("groups", contains("test reports"))),
                allOf(hasProperty("username", equalTo("testuser2")), hasProperty("password", equalTo("testuserpass2")), hasProperty("groups", hasSize(1)), hasProperty("groups", contains("test reports")))
            ));
    }
}
