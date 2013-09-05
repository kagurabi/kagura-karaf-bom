package com.base2.kagura.services.camel.kagura;

import com.base2.kagura.services.camel.model.Group;
import com.base2.kagura.services.camel.model.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * @author aubels
 *         Date: 29/08/13
 */
@Service
public class AuthBean {
    private static final Logger LOG = LoggerFactory.getLogger(AuthBean.class);

    private Map<String, AuthDetails> tokens = new HashMap<String, AuthDetails>();

    ServerBean serverBean;
    private ReportBean reportsBean;

    public void isLoggedIn(@Header("authToken") String authToken, Exchange exchange) throws AuthenticationException {
        if (tokens.containsKey(authToken))
        {
            AuthDetails authDetails = tokens.get(authToken);
            if( authDetails.getLoggedIn() && !authDetails.expired())
            {
                authDetails.updateLastAccessed();
                return;
            }
            LOG.info("Ticket was expired or logged out.");
        }
        throw new AuthenticationException("User is not logged in.");
    }

    public void authenticate(@Header("user") String user, @Body String pass, Exchange exchange) throws AuthenticationException {
        if (StringUtils.isBlank(user) || StringUtils.isBlank(pass))
        {
            LOG.info("User '{}' attempted to login with a blank username or password.", user);
            throw new AuthenticationException("User was not logged in.");
        }
        Map<String, User> userMap = getStringUserMap();
        User matchUser = userMap.get(user);
        if (matchUser == null)
        {
            LOG.info("User '{}' does not exist.", user);
            throw new AuthenticationException("User was not logged in.");
        }
        if (!matchUser.getPassword().equals(pass))
        {
            LOG.info("User '{}' bad password entered.", user);
            throw new AuthenticationException("User was not logged in.");
        }
        AuthDetails authDetails = new AuthDetails(user);
        tokens.put(authDetails.getToken().toString(), authDetails);
        Map<String, String> response = new HashMap<String, String>();
        response.put("error","");
        response.put("token",authDetails.getToken().toString());
        exchange.getOut().setBody(response);
    }

    private Map<String, User> getStringUserMap() {
        List<User> users = getUsers();
        Map<String, User> userMap = new HashMap<String, User>();
        for (User each : users)
        {
            userMap.put(each.getUsername(), each);
        }
        return userMap;
    }

    private Map<String, Group> getStringGroupMap() {
        List<Group> users = getGroups();
        Map<String, Group> userMap = new HashMap<String, Group>();
        for (Group each : users)
        {
            userMap.put(each.getGroupname(), each);
        }
        return userMap;
    }

    public void buildAuthFail(Exchange exchange)
    {
        Map<String, String> response = new HashMap<String, String>();
        response.put("error","Authentication failure");
        response.put("token","");
        exchange.getOut().setBody(response);
    }

    public void cleanAuthTickets(Exchange exchange)
    {
        List<String> removeThese = new ArrayList<String>();
        for (AuthDetails authDetails : tokens.values())
        {
            if (authDetails.expired())
                removeThese.add(authDetails.getToken().toString());
        }
        for (String each: removeThese)
        {
            LOG.info("Removing token: " + each);
            tokens.remove(each);
        }
    }

    public void logout(@Header("authToken") String authToken, Exchange exchange) throws AuthenticationException {
        if (tokens.containsKey(authToken) && tokens.get(authToken).getLoggedIn())
        {
            tokens.remove(authToken);
            return;
        }
        throw new AuthenticationException("User is not logged in.");
    }

    public void canAccessReport(@Header("authToken") String authToken, @Header("reportId") String reportId, Exchange exchange) throws AuthorizationException, AuthenticationException {
        if (tokens.containsKey(authToken) && tokens.get(authToken).getLoggedIn())
        {
            if (getUserReports(tokens.get(authToken).getUsername()).contains(reportId))
                return;
            throw new AuthorizationException("User is not logged in.");
        }
        throw new AuthenticationException("User is not logged in.");
    }

    public List<Group> getGroups()
    {
        String filename = FilenameUtils.concat(serverBean.getConfigPath(), "groups.yaml");
        InputStream selectedYaml = serverBean.openFile(filename);
        if (selectedYaml == null)
        {
            LOG.error("Can not find: {}", filename);
            return null;
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        List<Group> groups = null;
        try {
            groups = mapper.readValue(selectedYaml, new TypeReference<List<Group>>(){});
        } catch (IOException e) {
            LOG.warn("Error parsing {}", filename);
            e.printStackTrace();
        }
        return groups;
    }

    public List<User> getUsers()
    {
        String filename = FilenameUtils.concat(serverBean.getConfigPath(), "users.yaml");
        InputStream selectedYaml = serverBean.openFile(filename);
        if (selectedYaml == null)
        {
            LOG.error("Can not find: {}", filename);
            return null;
        }
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        List<User> users = null;
        try {
            users = mapper.readValue(selectedYaml, new TypeReference<List<User>>(){});
        } catch (IOException e) {
            LOG.warn("Error parsing {}", filename);
            e.printStackTrace();
        }
        return users;
    }
    
    public Collection<String> getReports(@Header("authToken") String authToken, Exchange exchange) throws AuthenticationException {
        if (!tokens.containsKey(authToken) || !tokens.get(authToken).getLoggedIn())
            throw new AuthenticationException("User is not logged in.");
        AuthDetails authDetails = tokens.get(authToken);
        String username = authDetails.getUsername();
        Set<String> result = getUserReports(username);
        return result;
    }

    public Map<String, Object> getReportsDetailed(@Header("authToken") String authToken, Exchange exchange) throws AuthenticationException {
        if (!tokens.containsKey(authToken) || !tokens.get(authToken).getLoggedIn())
            throw new AuthenticationException("User is not logged in.");
        AuthDetails authDetails = tokens.get(authToken);
        String username = authDetails.getUsername();
        Set<String> reports = getUserReports(username);
        Map<String, Object> result = new HashMap<String, Object>();
        for (String reportName : reports)
        {
            result.put(reportName, reportsBean.getReportDetails(reportName, false));
        }
        return result;
    }

    private Set<String> getUserReports(String username) {
        Set<String> result = new HashSet<String>();
        Map<String, User> userMap = getStringUserMap();
        Map<String, Group> groupMap = getStringGroupMap();
        User user = userMap.get(username);
        for (String group : user.getGroups())
        {
            if (groupMap.containsKey(group))
            {
                result.addAll(groupMap.get(group).getReports());
            }
            else
                LOG.warn("Group '{}' does nto exist.", group);
        }
        return result;
    }

    public static class AuthDetails {
        private Boolean loggedIn;
        private String username;
        private UUID token;
        private Date lastAccessed;

        private AuthDetails(String username) {
            this.username = username;
            this.loggedIn = true;
            this.token = UUID.randomUUID();
            this.lastAccessed = Calendar.getInstance().getTime();
        }

        public Boolean expired()
        {
            Date curDate = Calendar.getInstance().getTime();
            return curDate.getTime() - this.lastAccessed.getTime() > 2 * 24 * 60 * 60 * 1000;
        }

        public void updateLastAccessed()
        {
            this.lastAccessed = Calendar.getInstance().getTime();
        }

        private Boolean getLoggedIn() {
            return loggedIn;
        }

        private void setLoggedIn(Boolean loggedIn) {
            this.loggedIn = loggedIn;
        }

        private String getUsername() {
            return username;
        }

        private void setUsername(String username) {
            this.username = username;
        }

        public UUID getToken() {
            return token;
        }

        public Date getLastAccessed() {
            return lastAccessed;
        }

        public void setLastAccessed(Date lastAccessed) {
            this.lastAccessed = lastAccessed;
        }
    }

    public Map<String, AuthDetails> getTokens() {
        return tokens;
    }

    public ServerBean getServerBean() {
        return serverBean;
    }

    @Autowired
    public void setServerBean(ServerBean serverBean) {
        this.serverBean = serverBean;
    }

    @Autowired
    public void setReportsBean(ReportBean reportsBean) {
        this.reportsBean = reportsBean;
    }

}
